package net.schwarz.rotasutils.server;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.data.ServerSettings;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.skill.SkillCategory;
import java.util.*;

public final class ConfigService {
    private ConfigService() { }

    public static String domain(String action, CompoundTag payload) {
        return switch (action) {
            case "save_server_settings" -> "settings";
            case "save_level_config" -> "progression";
            case "save_quest", "publish_quest" -> "quest/" + payload.getCompound("quest").getString("id");
            case "save_board" -> "board/" + payload.getCompound("board").getString("id");
            case "save_npc" -> "npc/" + payload.getCompound("npc").getString("id");
            case "save_category" -> "category/" + payload.getCompound("category").getString("id");
            case "save_job" -> "job/" + payload.getCompound("job").getString("id");
            default -> throw new IllegalArgumentException("Unsupported configuration action");
        };
    }

    public static String payloadKey(String action) {
        return switch (action) {
            case "save_server_settings" -> "server_settings";
            case "save_level_config" -> "level_config";
            case "save_quest", "publish_quest" -> "quest";
            case "save_board" -> "board";
            case "save_npc" -> "npc";
            case "save_category" -> "category";
            case "save_job" -> "job";
            default -> throw new IllegalArgumentException("Unsupported configuration action");
        };
    }

    public static void requireDomain(String domain) {
        if (domain.equals("settings") || domain.equals("progression")) { return; }
        if (domain.length() > 256 || !domain.matches("(quest|board|category|npc|job)/[a-zA-Z0-9_:./-]+") || domain.contains("..")) {
            throw new IllegalArgumentException("Unknown configuration domain: " + domain);
        }
    }

    public static CompoundTag snapshot(RotasData data, String domain) {
        requireDomain(domain);
        CompoundTag live;
        if (domain.equals("settings")) { live = data.serverSettings().save(); }
        else if (domain.equals("progression")) { live = data.levelConfig().save(); }
        else {
            String id = domain.substring(domain.indexOf('/') + 1);
            live = switch (domain.substring(0, domain.indexOf('/'))) {
                case "quest" -> data.quest(id) == null ? new CompoundTag() : data.quest(id).save();
                case "board" -> data.board(id) == null ? new CompoundTag() : data.board(id).save();
                case "npc" -> data.npc(id) == null ? new CompoundTag() : data.npc(id).save();
                case "category" -> data.category(id) == null ? new CompoundTag() : data.category(id).save();
                case "job" -> data.job(id) == null ? new CompoundTag() : data.job(id).save();
                default -> throw new IllegalArgumentException("Unknown domain");
            };
        }
        if (domain.startsWith("quest/")) { return live; }
        return merge(data.configHistory().retained(domain), live);
    }

    public static Map<String, CompoundTag> snapshots(RotasData data) {
        Map<String, CompoundTag> result = new TreeMap<>();
        result.put("settings", snapshot(data, "settings")); result.put("progression", snapshot(data, "progression"));
        data.quests().keySet().forEach(id -> result.put("quest/" + id, snapshot(data, "quest/" + id)));
        data.boards().keySet().forEach(id -> result.put("board/" + id, snapshot(data, "board/" + id)));
        data.npcs().keySet().forEach(id -> result.put("npc/" + id, snapshot(data, "npc/" + id)));
        data.categories().keySet().forEach(id -> result.put("category/" + id, snapshot(data, "category/" + id)));
        data.jobs().keySet().forEach(id -> result.put("job/" + id, snapshot(data, "job/" + id)));
        return result;
    }

    public static List<String> importFiles(RotasData data, String actor, java.nio.file.Path root) throws java.io.IOException {
        var incoming = net.schwarz.rotasutils.core.ConfigFiles.readLegacy(root);
        List<String> results = new ArrayList<>();
        for (var entry : new TreeMap<>(incoming).entrySet()) {
            try {
                requireDomain(entry.getKey());
                if (data.configHistory().draft(actor, entry.getKey()) != null) { throw new IllegalStateException("existing draft retained; apply or discard it first"); }
                var value = merge(snapshot(data, entry.getKey()), entry.getValue());
                data.configHistory().stage(actor, entry.getKey(), snapshot(data, entry.getKey()), value, -1);
                long errors = validate(data, entry.getKey(), value).stream().filter(i -> i.severity() == Validation.Severity.ERROR).count();
                results.add(entry.getKey() + ": imported to draft" + (errors > 0 ? " (" + errors + " validation errors; fix files and reimport)" : "; ready to review"));
            } catch (IllegalArgumentException | IllegalStateException error) { results.add(entry.getKey() + ": rejected - " + error.getMessage()); }
        }
        if (results.isEmpty()) { results.add("No files found in config/rotasutils/configuration"); }
        return List.copyOf(results);
    }

    public static CompoundTag merge(CompoundTag original, CompoundTag edited) {
        var result = original.copy();
        for (String key : edited.getAllKeys()) {
            if (edited.get(key) instanceof CompoundTag nested && result.get(key) instanceof CompoundTag old) {
                result.put(key, merge(old, nested));
            } else { result.put(key, Objects.requireNonNull(edited.get(key)).copy()); }
        }
        return result;
    }

    public static List<Validation.Issue> validate(RotasData data, String domain, CompoundTag value) {
        requireDomain(domain);
        List<Validation.Issue> issues = new ArrayList<>();
        if (value.toString().length() > 262144) { issues.add(issue(domain, "body", "Configuration exceeds 256 KiB")); return issues; }
        try {
            CompoundTag normalized;
            if (domain.equals("settings")) {
                normalized = ServerSettings.load(value).save();
                range(issues, domain, value, "admin_op_level", 0, 4);
                range(issues, domain, value, "max_active", 1, Integer.MAX_VALUE);
                range(issues, domain, value, "max_party", 1, 64);
                range(issues, domain, value, "party_radius", 4, Double.MAX_VALUE);
                range(issues, domain, value, "anti_farm_memory", 60, Integer.MAX_VALUE);
                range(issues, domain, value, "request_cooldown", 0, Integer.MAX_VALUE);
                range(issues, domain, value, "autosave", 10, Integer.MAX_VALUE);
                range(issues, domain, value, "job_cooldown", 0, Long.MAX_VALUE);
            } else if (domain.equals("progression")) {
                normalized = LevelConfig.load(value).save();
                range(issues, domain, value, "starting_level", 1, 10000);
                for (String field : List.of("monster_xp_scale", "boss_xp_mult", "modded_mob_xp_mult")) { range(issues, domain, value, field, 0, Double.MAX_VALUE); }
            } else {
                String id = domain.substring(domain.indexOf('/') + 1);
                if (!id.equals(value.getString("id"))) { issues.add(issue(domain, "id", "Document id must match its domain")); }
                if (domain.startsWith("quest/")) {
                    var quest = QuestDef.load(value); normalized = quest.save();
                    var questIssues = Validation.validateQuest(data, quest);
                    if (!quest.published()) {
                        questIssues = questIssues.stream().map(i -> i.severity() == Validation.Severity.ERROR
                                ? new Validation.Issue(Validation.Severity.WARNING, i.targetKind(), i.targetId(), i.field(), i.message()) : i).toList();
                    }
                    issues.addAll(questIssues);
                } else if (domain.startsWith("board/")) {
                    var board = BoardConfig.load(value); normalized = board.save(); issues.addAll(Validation.validateBoard(data, board));
                } else if (domain.startsWith("npc/")) {
                    var npc = net.schwarz.rotasutils.npc.NpcDef.load(value); normalized = npc.save();
                    issues.addAll(Validation.validateNpc(data, npc));
                } else if (domain.startsWith("category/")) {
                    var category = SkillCategory.load(value); normalized = category.save();
                    Set<String> ids = new HashSet<>();
                    data.categories().values().stream().filter(c -> !c.id().equals(id)).forEach(c -> c.nodes().values().forEach(n -> ids.add(n.id())));
                    issues.addAll(Validation.validateCategory(data, category, ids));
                } else {
                    var job=net.schwarz.rotasutils.job.JobDef.load(value); normalized=job.save();
                    if(!job.id().matches("[a-z0-9_]{1,32}")) issues.add(issue(domain,"id","Job ids use 1-32 lowercase letters, digits or _"));
                    if(!job.mainAllowed()&&!job.subAllowed()) issues.add(issue(domain,"slots","Job must be available in at least one slot"));
                    for(String npc:job.trainerNpcIds()) if(data.npc(npc)==null) issues.add(issue(domain,"trainers","Trainer NPC does not exist: "+npc));
                }
            }
            compareInput(domain, "", value, normalized, issues);
        } catch (RuntimeException error) { issues.add(issue(domain, "body", error.getMessage() == null ? "Invalid configuration" : error.getMessage())); }
        return List.copyOf(issues);
    }

    private static void range(List<Validation.Issue> issues, String domain, CompoundTag value, String field, double min, double max) {
        if (value.contains(field) && (!(value.get(field) instanceof NumericTag number) || !Double.isFinite(number.getAsDouble())
                || number.getAsDouble() < min || number.getAsDouble() > max)) { issues.add(issue(domain, field, "Expected " + min + " to " + max)); }
    }

    private static void compareInput(String domain, String path, CompoundTag raw, CompoundTag canonical, List<Validation.Issue> issues) {
        for (String key : raw.getAllKeys()) {
            if (!canonical.contains(key)) { continue; }
            String field = path.isEmpty() ? key : path + "." + key;
            Tag a = raw.get(key), b = canonical.get(key);
            if (a instanceof CompoundTag ac && b instanceof CompoundTag bc) { compareInput(domain, field, ac, bc, issues); }
            else if (a instanceof NumericTag an && b instanceof NumericTag bn) {
                if (!Double.isFinite(an.getAsDouble()) || Double.compare(an.getAsDouble(), bn.getAsDouble()) != 0) { issues.add(issue(domain, field, "Value is outside its supported range")); }
            } else if (a != null && b != null && a.getId() != b.getId()) { issues.add(issue(domain, field, "Incorrect field type")); }
            else if (a instanceof StringTag && !a.equals(b)) { issues.add(issue(domain, field, "Unsupported value")); }
        }
    }

    private static Validation.Issue issue(String domain, String field, String message) {
        return new Validation.Issue(Validation.Severity.ERROR, "configuration", domain, field, message);
    }

    public static void apply(MinecraftServer server, String actor, String domain, long generation) {
        var data = RotasData.get(server); var history = data.configHistory();
        var draft = history.check(actor, domain, snapshot(data, domain), generation);
        var issues = validate(data, domain, draft.value());
        if (issues.stream().anyMatch(i -> i.severity() == Validation.Severity.ERROR)) { throw new IllegalArgumentException(issues.get(0).field() + ": " + issues.get(0).message()); }
        CompoundTag value = draft.value();
        Object parsed = domain.equals("settings") ? ServerSettings.load(value) : domain.equals("progression") ? LevelConfig.load(value)
                : domain.startsWith("quest/") ? QuestDef.load(value) : domain.startsWith("board/") ? BoardConfig.load(value)
                : domain.startsWith("npc/") ? net.schwarz.rotasutils.npc.NpcDef.load(value)
                : domain.startsWith("job/") ? net.schwarz.rotasutils.job.JobDef.load(value) : SkillCategory.load(value);
        history.applied(actor, domain, "apply");
        if (parsed instanceof ServerSettings settings) { data.setServerSettings(settings); }
        else if (parsed instanceof LevelConfig levels) {
            data.setLevelConfig(levels);
            try { SeasonConfigFile.write(server, levels.season()); }
            catch (java.io.IOException failure) { net.schwarz.rotasutils.Rotasutils.LOG.error("season.json could not be written", failure); }
        }
        else if (parsed instanceof QuestDef quest) {
            var old = data.quest(quest.id()); if (old != null) { quest.setVersion(old.version()); }
            quest.freezeObjectiveKeys();
            if (quest.published()) { quest.bumpVersion(); }
            data.putQuest(quest);
            data.boards().values().forEach(board -> {
                if (quest.boardIds().contains(board.id())) { if (!board.questIds().contains(quest.id())) { board.questIds().add(quest.id()); } }
                else { board.questIds().remove(quest.id()); }
            });
        } else if (parsed instanceof BoardConfig board) { data.putBoard(board); }
        else if (parsed instanceof net.schwarz.rotasutils.npc.NpcDef npc) {
            if (npc.bound()) {
                data.npcs().values().forEach(other -> {
                    if (!other.id().equals(npc.id()) && npc.entityUuid().equals(other.entityUuid())) {
                        other.setEntityUuid("");
                    }
                });
            }
            data.putNpc(npc);
        }
        else if (parsed instanceof SkillCategory category) {
            var previous = data.category(category.id());
            if (previous != null) { category.bumpVersion(); net.schwarz.rotasutils.network.ServerActions.preserveRemovedNodes(data, previous, category); }
            data.putCategory(category);
        }
        else if (parsed instanceof net.schwarz.rotasutils.job.JobDef job) { data.putJob(job); }
        data.setDirty(); data.audit(java.time.Instant.now() + " actor=" + actor + " configuration=" + domain);
        for (var player : server.getPlayerList().getPlayers()) {
            try {
                if (domain.equals("progression")) {
                    ProgressService.refreshClearance(player, data, data.progress(player.getUUID()));
                    CharacterStatService.grantLevelPoints(data.progress(player.getUUID()), data);
                    CharacterStatService.apply(player, data);
                }
                if (domain.startsWith("category/") || domain.startsWith("job/")) { SkillService.recalculate(player, data); }
                RotasNetwork.syncContent(player); RotasNetwork.syncProgress(player);
            } catch (RuntimeException error) { net.schwarz.rotasutils.Rotasutils.LOG.error("Configuration applied, client refresh failed", error); }
        }
    }

    public static void checkBoardPermission(net.minecraft.commands.CommandSourceStack source, String domain, CompoundTag value) {
        if (!domain.startsWith("board/") || source.hasPermission(RotasData.get(source.getServer()).serverSettings().adminOpLevel())) { return; }
        BoardConfig proposed = BoardConfig.load(value);
        BoardConfig live = RotasData.get(source.getServer()).board(proposed.id());
        if (live == null) { throw new IllegalStateException("Creating a board configuration requires operator level 4"); }
        BoardConfig restored = BoardConfig.load(value);
        net.schwarz.rotasutils.network.ServerActions.restoreOperatorOnlyFields(restored, live);
        if (!restored.save().equals(proposed.save())) { throw new IllegalStateException("Board access and rotation rules require operator level 4"); }
    }

    public static void checkCommandPermission(net.minecraft.commands.CommandSourceStack source, String domain, CompoundTag value) {
        if (source.hasPermission(4)) { return; }
        RotasData data = RotasData.get(source.getServer());
        boolean changed = false;
        if (domain.startsWith("quest/")) {
            QuestDef proposed = QuestDef.load(value);
            changed = !commands(proposed).equals(commands(data.quest(proposed.id())));
        } else if (domain.startsWith("category/")) {
            SkillCategory proposed = SkillCategory.load(value);
            changed = !commands(proposed).equals(commands(data.category(proposed.id())));
        }
        if (changed) { throw new IllegalStateException("Adding or changing a Run Command reward or effect requires operator level 4"); }
    }

    public static List<String> commands(QuestDef quest) {
        List<String> lines = new ArrayList<>();
        if (quest == null) { return lines; }
        for (var reward : quest.rewards()) {
            if (reward.type() == net.schwarz.rotasutils.quest.reward.RewardType.COMMAND) {
                String line = reward.params().getString("command", "").trim();
                if (!line.isEmpty()) { lines.add(line); }
            }
        }
        lines.sort(null);
        return lines;
    }

    public static List<String> commands(SkillCategory category) {
        List<String> lines = new ArrayList<>();
        if (category == null) { return lines; }
        for (var node : category.nodes().values()) {
            for (var effect : node.effects()) {
                if (effect.type() == net.schwarz.rotasutils.skill.EffectType.COMMAND) {
                    String line = effect.params().getString("command", "").trim();
                    if (!line.isEmpty()) { lines.add(node.id() + "=" + line); }
                }
            }
        }
        lines.sort(null);
        return lines;
    }

    public static void checkSettingsPermission(net.minecraft.commands.CommandSourceStack source, String domain, CompoundTag value) {
        if (!domain.equals("settings") || source.hasPermission(4)) { return; }
        if (ServerSettings.load(value).adminOpLevel() != RotasData.get(source.getServer()).serverSettings().adminOpLevel()) {
            throw new IllegalStateException("Changing the administrator permission level requires operator level 4");
        }
    }
}
