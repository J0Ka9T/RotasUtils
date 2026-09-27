package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContentRegistry {
    public enum Layer { CORE, SERVER, SEASON, EVENT, HOTFIX }
    public enum Kind { CONDITION, ACTION, REWARD, RULE, STAT, MONSTER, TIER, AFFIX, BOSS, ITEM, RARITY, SET, LOOT, QUEST, MERCHANT }

    public record Source(String name, Layer layer, JsonObject document) {
        public Source {
            if (name == null || layer == null || document == null) {
                throw new IllegalArgumentException("Incomplete content source");
            }
            document = document.deepCopy();
        }

        @Override
        public JsonObject document() {
            return document.deepCopy();
        }
    }

    public record Definition(ContentId id, Kind kind, boolean enabled, Source source) {
    }

    public record Rule(ContentId id, ContentId event, boolean once, ConditionEngine.Condition condition,
                       RewardEngine.Reward reward) {
    }

    public record Snapshot(String hash, Map<ContentId, Definition> definitions,
                           Map<ContentId, ConditionEngine.Condition> conditions,
                           Map<ContentId, ActionEngine.Action> actions,
                           Map<ContentId, RewardEngine.Reward> rewards, Map<ContentId, List<Rule>> rules,
                           Map<ContentId, StatDefinition> stats, MonsterCatalog monsters, ItemCatalog items,
                           Map<ContentId, QuestDefinitions.Quest> quests,
                           Map<ContentId, MerchantDefinitions.Merchant> merchants) {
        public Snapshot {
            definitions = Map.copyOf(definitions);
            conditions = Map.copyOf(conditions);
            actions = Map.copyOf(actions);
            rewards = Map.copyOf(rewards);
            stats = Map.copyOf(stats);
            quests = Map.copyOf(quests);
            merchants = Map.copyOf(merchants);
            Map<ContentId, List<Rule>> frozen = new HashMap<>();
            rules.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
            rules = Map.copyOf(frozen);
        }

        public static Snapshot empty() {
            return new Snapshot("empty", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), MonsterCatalog.empty(), ItemCatalog.empty(), Map.of(), Map.of());
        }
    }

    public record Diagnostic(String source, String message) {
        @Override
        public String toString() {
            return source + ": " + message;
        }
    }

    public record Prepared(Snapshot snapshot, List<Diagnostic> issues) {
        public Prepared {
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return snapshot != null && issues.isEmpty();
        }
    }

    private final ConditionEngine conditions;
    private final ActionEngine actions;

    public ContentRegistry(ConditionEngine conditions, ActionEngine actions) {
        this.conditions = conditions;
        this.actions = actions;
    }

    public Prepared prepare(List<Source> sources) {
        List<Diagnostic> issues = new ArrayList<>();
        if (sources.size() > 4096) {
            return new Prepared(null, List.of(new Diagnostic("packs", "Definition count exceeds 4096")));
        }
        Map<ContentId, Definition> effective = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Source source : sources.stream().sorted(Comparator.comparing(Source::layer)
                .thenComparing(Source::name)).toList()) {
            try {
                JsonObject json = source.document();
                KernelJson.fields(json, "id", "schema", "kind", "enabled", "body", "requires");
                ContentId id = new ContentId(KernelJson.string(json, "id"));
                KernelJson.integer(json, "schema", 1, 1);
                Kind kind = Kind.valueOf(KernelJson.string(json, "kind").toUpperCase(java.util.Locale.ROOT));
                boolean enabled = true;
                if (json.has("enabled")) {
                    if (!json.get("enabled").isJsonPrimitive() || !json.getAsJsonPrimitive("enabled").isBoolean()) {
                        throw new IllegalArgumentException("enabled must be boolean");
                    }
                    enabled = json.get("enabled").getAsBoolean();
                }
                if (!seen.add(source.layer() + "|" + id)) {
                    throw new IllegalArgumentException("Duplicate ID at equal priority: " + id);
                }
                Definition old = effective.get(id);
                if (old != null && old.kind() != kind) {
                    throw new IllegalArgumentException("Override changes definition kind: " + id);
                }
                effective.put(id, new Definition(id, kind, enabled, source));
            } catch (IllegalArgumentException ex) {
                issues.add(new Diagnostic(source.name(), ex.getMessage()));
            }
        }
        Compiler compiler = new Compiler(effective);
        for (Definition definition : effective.values()) {
            if (!definition.enabled()) {
                continue;
            }
            try {
                compiler.resolve(definition.id(), definition.kind());
            } catch (RuntimeException ex) {
                issues.add(new Diagnostic(definition.source().name(), ex.getMessage()));
            }
        }
        if (!issues.isEmpty()) {
            return new Prepared(null, issues);
        }
        Map<ContentId, ConditionEngine.Condition> conditionMap = new HashMap<>();
        Map<ContentId, ActionEngine.Action> actionMap = new HashMap<>();
        Map<ContentId, RewardEngine.Reward> rewardMap = new HashMap<>();
        Map<ContentId, List<Rule>> ruleMap = new HashMap<>();
        Map<ContentId, StatDefinition> statMap = new HashMap<>();
        Map<ContentId, MonsterDefinitions.Profile> monsterMap = new HashMap<>();
        Map<ContentId, MonsterDefinitions.Tier> tierMap = new HashMap<>();
        Map<ContentId, MonsterDefinitions.Affix> affixMap = new HashMap<>();
        Map<ContentId, BossDefinitions.Boss> bossMap = new HashMap<>();
        Map<ContentId, ItemDefinitions.Profile> itemMap = new HashMap<>();
        Map<ContentId, ItemDefinitions.Rarity> rarityMap = new HashMap<>();
        Map<ContentId, ItemDefinitions.ItemSet> setMap = new HashMap<>();
        Map<ContentId, ItemDefinitions.LootTable> lootMap = new HashMap<>();
        Map<ContentId, QuestDefinitions.Quest> questMap = new HashMap<>();
        Map<ContentId, MerchantDefinitions.Merchant> merchantMap = new HashMap<>();
        for (Definition definition : effective.values()) {
            Object compiled = compiler.compiled.get(definition.id());
            if (compiled instanceof MerchantDefinitions.Merchant merchant) {
                merchantMap.put(definition.id(), merchant);
            } else if (compiled instanceof QuestDefinitions.Quest quest) {
                questMap.put(definition.id(), quest);
            } else if (compiled instanceof ItemDefinitions.Profile item) {
                itemMap.put(definition.id(), item);
            } else if (compiled instanceof ItemDefinitions.Rarity rarity) {
                rarityMap.put(definition.id(), rarity);
            } else if (compiled instanceof ItemDefinitions.ItemSet set) {
                setMap.put(definition.id(), set);
            } else if (compiled instanceof ItemDefinitions.LootTable table) {
                lootMap.put(definition.id(), table);
            } else if (compiled instanceof MonsterDefinitions.Profile monster) {
                monsterMap.put(definition.id(), monster);
            } else if (compiled instanceof MonsterDefinitions.Tier tier) {
                tierMap.put(definition.id(), tier);
            } else if (compiled instanceof MonsterDefinitions.Affix affix) {
                affixMap.put(definition.id(), affix);
            } else if (compiled instanceof BossDefinitions.Boss boss) {
                bossMap.put(definition.id(), boss);
            } else if (compiled instanceof StatDefinition stat) {
                statMap.put(definition.id(), stat);
            } else if (compiled instanceof ConditionEngine.Condition condition) {
                conditionMap.put(definition.id(), condition);
            } else if (compiled instanceof ActionEngine.Action action) {
                actionMap.put(definition.id(), action);
            } else if (compiled instanceof RewardEngine.Reward reward) {
                rewardMap.put(definition.id(), reward);
            } else if (compiled instanceof Rule rule) {
                List<Rule> group = ruleMap.computeIfAbsent(rule.event(), key -> new ArrayList<>());
                group.add(rule);
                if (group.size() > 256) {
                    issues.add(new Diagnostic(definition.source().name(), "More than 256 rules for one event"));
                }
            }
        }
        if (!issues.isEmpty()) {
            return new Prepared(null, issues);
        }
        if (questMap.size() > 1024) {
            return new Prepared(null, List.of(new Diagnostic("quests", "Quest count exceeds 1024")));
        }
        if (statMap.size() > 256) {
            return new Prepared(null, List.of(new Diagnostic("stats", "Stat count exceeds 256")));
        }
        try {
            return new Prepared(new Snapshot(hash(effective), effective, conditionMap, actionMap, rewardMap, ruleMap, statMap,
                    new MonsterCatalog(monsterMap, tierMap, affixMap, bossMap), new ItemCatalog(itemMap, rarityMap, setMap, lootMap), questMap, merchantMap), issues);
        } catch (IllegalArgumentException failure) { return new Prepared(null, List.of(new Diagnostic("content", failure.getMessage()))); }
    }

    private static String hash(Map<ContentId, Definition> definitions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            definitions.values().stream().sorted(Comparator.comparing(Definition::id)).forEach(definition -> {
                digest.update(definition.source().layer().name().getBytes(StandardCharsets.UTF_8));
                digest.update(definition.source().document().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java runtime lacks SHA-256", ex);
        }
    }

    private final class Compiler {
        private final Map<ContentId, Definition> definitions;
        private final Map<ContentId, Object> compiled = new HashMap<>();
        private final Set<ContentId> visiting = new HashSet<>();

        private Compiler(Map<ContentId, Definition> definitions) {
            this.definitions = definitions;
        }

        private Object resolve(ContentId id, Kind expected) {
            Definition definition = definitions.get(id);
            if (definition == null || !definition.enabled()) {
                throw new IllegalArgumentException("Missing or disabled reference: " + id);
            }
            if (definition.kind() != expected) {
                throw new IllegalArgumentException("Expected " + expected + " reference: " + id);
            }
            if (compiled.containsKey(id)) {
                return compiled.get(id);
            }
            if (visiting.size() >= 32 || !visiting.add(id)) {
                throw new IllegalArgumentException("Cycle or reference depth exceeded at " + id);
            }
            try {
                JsonObject json = definition.source().document();
                if (json.has("requires")) {
                    if (!json.get("requires").isJsonArray() || json.getAsJsonArray("requires").size() > 64) {
                        throw new IllegalArgumentException("requires must be an array of at most 64 IDs");
                    }
                    for (JsonElement element : json.getAsJsonArray("requires")) {
                        ContentId dependency = id(element);
                        Definition target = definitions.get(dependency);
                        if (target == null) {
                            throw new IllegalArgumentException("Missing dependency: " + dependency);
                        }
                        resolve(dependency, target.kind());
                    }
                }
                JsonObject body = KernelJson.object(json, "body");
                Object result = switch (expected) {
                    case MONSTER -> {
                        var monster = MonsterDefinitions.profile(id, body);
                        monster.tiers().keySet().forEach(tier -> resolve(tier, Kind.TIER));
                        monster.affixes().forEach(affix -> resolve(affix, Kind.AFFIX));
                        if (monster.reward() != null) { resolve(monster.reward(), Kind.REWARD); }
                        if (monster.loot() != null) { resolve(monster.loot(), Kind.LOOT); }
                        if (monster.boss() != null) { resolve(monster.boss(), Kind.BOSS); }
                        yield monster;
                    }
                    case TIER -> MonsterDefinitions.tier(id, body);
                    case BOSS -> {
                        var boss = BossDefinitions.boss(id, body, this::condition, action -> (ActionEngine.Action) resolve(action, Kind.ACTION));
                        boss.phases().forEach(phase -> {
                            if (phase.summon() != null) { resolve(phase.summon(), Kind.MONSTER); }
                        });
                        if (boss.reward() != null) { resolve(boss.reward(), Kind.REWARD); }
                        if (boss.loot() != null) { resolve(boss.loot(), Kind.LOOT); }
                        yield boss;
                    }
                    case MERCHANT -> {
                        var merchant = MerchantDefinitions.merchant(id, body, this::condition);
                        merchant.trades().forEach(trade -> {
                            if (trade.profile() != null) { resolve(trade.profile(), Kind.ITEM); }
                        });
                        yield merchant;
                    }
                    case QUEST -> {
                        var quest = QuestDefinitions.quest(id, body, this::condition);
                        if (quest.reward() != null) { resolve(quest.reward(), Kind.REWARD); }
                        quest.stages().forEach(stage -> {
                            if (stage.reward() != null) { resolve(stage.reward(), Kind.REWARD); }
                        });
                        yield quest;
                    }
                    case RARITY -> ItemDefinitions.rarity(id, body);
                    case ITEM -> {
                        var item = ItemDefinitions.profile(id, body);
                        item.rarities().keySet().forEach(rarity -> resolve(rarity, Kind.RARITY));
                        if (item.set() != null) { resolve(item.set(), Kind.SET); }
                        yield item;
                    }
                    case SET -> {
                        var set = ItemDefinitions.set(id, body);
                        set.pieces().forEach(piece -> {
                            Definition target = definitions.get(piece);
                            if (target == null || !target.enabled() || target.kind() != Kind.ITEM) {
                                throw new IllegalArgumentException("Set piece is not an enabled item: " + piece);
                            }
                        });
                        yield set;
                    }
                    case LOOT -> {
                        var table = ItemDefinitions.lootTable(id, body, this::condition);
                        table.entries().forEach(entry -> {
                            if (entry.profile() != null) { resolve(entry.profile(), Kind.ITEM); }
                        });
                        yield table;
                    }
                    case AFFIX -> MonsterDefinitions.affix(id, body, this::condition, action -> (ActionEngine.Action) resolve(action, Kind.ACTION));
                    case STAT -> StatDefinition.parse(id, body);
                    case CONDITION -> condition(body);
                    case ACTION -> actions.compile(body);
                    case REWARD -> {
                        KernelJson.fields(body, "condition", "actions");
                        ConditionEngine.Condition gate = body.has("condition")
                                ? condition(KernelJson.object(body, "condition")) : ConditionEngine.ALWAYS;
                        if (!body.has("actions") || !body.get("actions").isJsonArray()
                                || body.getAsJsonArray("actions").size() > 128) {
                            throw new IllegalArgumentException("Expected at most 128 action IDs");
                        }
                        List<ActionEngine.Action> steps = new ArrayList<>();
                        for (JsonElement element : body.getAsJsonArray("actions")) {
                            steps.add((ActionEngine.Action) resolve(id(element), Kind.ACTION));
                        }
                        yield new RewardEngine.Reward(id, gate, steps);
                    }
                    case RULE -> {
                        KernelJson.fields(body, "event", "scope", "condition", "reward");
                        ContentId event = new ContentId(KernelJson.string(body, "event"));
                        String scope = KernelJson.string(body, "scope");
                        if (!scope.equals("once") && !scope.equals("occurrence")) {
                            throw new IllegalArgumentException("Rule scope must be once or occurrence");
                        }
                        var gate = body.has("condition") ? condition(KernelJson.object(body, "condition"))
                                : ConditionEngine.ALWAYS;
                        var reward = (RewardEngine.Reward) resolve(new ContentId(KernelJson.string(body, "reward")), Kind.REWARD);
                        yield new Rule(id, event, scope.equals("once"), gate, reward);
                    }
                };
                compiled.put(id, result);
                return result;
            } finally {
                visiting.remove(id);
            }
        }

        private ConditionEngine.Condition condition(JsonObject body) {
            return conditions.compile(body, id -> (ConditionEngine.Condition) resolve(id, Kind.CONDITION));
        }

        private ContentId id(JsonElement element) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Reference must be a namespaced string");
            }
            return new ContentId(element.getAsString());
        }
    }
}
