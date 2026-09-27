package net.schwarz.rotasutils.server;

import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.ActionEngine;
import net.schwarz.rotasutils.core.ConditionEngine;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.KernelEventBus;
import net.schwarz.rotasutils.core.ReloadState;
import net.schwarz.rotasutils.core.RewardEngine;
import net.schwarz.rotasutils.data.RotasData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class RpgKernel implements AutoCloseable {
    private final MinecraftServer server;
    private final Path packs;
    private final ActionEngine actions;
    private final ContentRegistry registry;
    private final RewardEngine rewards;
    private final ReloadState reload = new ReloadState();
    private final KernelEventBus events;
    private final List<AutoCloseable> ruleSubscriptions = new ArrayList<>();
    private final Set<String> runtimeErrors = new LinkedHashSet<>();
    private final StatsService stats = new StatsService();
    private final EquipmentService equipment = new EquipmentService();
    private final MonsterService monsters;
    private final BossService bosses;
    private final ZoneEncounterService encounters;
    private final QuestKernelService quests;
    private final MerchantService merchants;
    private final net.schwarz.rotasutils.core.ContentHistory history;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Rotas-content-loader");
        thread.setDaemon(true);
        return thread;
    });
    private boolean busy;
    private boolean closed;

    public RpgKernel(MinecraftServer server) {
        this.server = server;
        var adapters = net.schwarz.rotasutils.api.KernelAdapters.freeze();
        var conditionAdapters = new java.util.HashMap<>(KernelPlayerContext.requirementAdapters());
        conditionAdapters.putAll(adapters.conditions());
        actions = new ActionEngine(adapters.actions());
        registry = new ContentRegistry(new ConditionEngine(conditionAdapters), actions);
        rewards = new RewardEngine(actions);
        packs = Platform.getConfigFolder().resolve("rotasutils/packs");
        events = new KernelEventBus((event, error) -> error(event.type().toString(), error));
        history = RotasData.get(server).contentHistory();
        monsters = new MonsterService(server, this);
        bosses = new BossService(server, this);
        encounters = new ZoneEncounterService(server, this);
        quests = new QuestKernelService(server, this);
        merchants = new MerchantService(server, this);
        ContentRegistry.Prepared startup;
        if (history.current() == null) {
            startup = StatsService.validate(ContentPacks.read(packs, registry));
            if (startup.valid()) { history.commit(startup, 0, "server", "bootstrap", System.currentTimeMillis()); }
        } else {
            startup = StatsService.validate(registry.prepare(history.sources(history.current().documents())));
            if (startup.valid() && !startup.snapshot().hash().equals(history.current().hash())) {
                startup = rejected("Stored active content hash mismatch");
            }
        }
        apply(startup);
    }

    public ContentRegistry.Snapshot content() {
        return reload.active();
    }

    public KernelEventBus events() {
        return events;
    }

    public MonsterService monsters() { return monsters; }

    public BossService bosses() { return bosses; }

    public ZoneEncounterService encounters() { return encounters; }

    public QuestKernelService quests() { return quests; }

    public MerchantService merchants() { return merchants; }

    public EquipmentService equipment() { return equipment; }

    public Map<String, Double> stats(ServerPlayer player) {
        return stats.refresh(player, RotasData.get(server), content());
    }

    public void forgetPlayer(UUID id) { checkThread(); stats.forget(id); equipment.forget(id); }

    public long revision() {
        return history.revision();
    }

    public net.schwarz.rotasutils.core.ContentHistory history() { checkThread(); return history; }

    /** True while a content change is being validated or applied. */
    public boolean busy() {
        return busy || closed;
    }

    public void checkEditable() {
        checkThread();
        if (busy || closed) { throw new IllegalStateException("A content operation is already running"); }
    }

    public List<String> diagnostics() {
        List<String> result = new ArrayList<>();
        result.add("RPG revision=" + revision() + " hash=" + content().hash() + " definitions="
                + content().definitions().size() + " loading=" + busy);
        reload.issues().stream().limit(32).forEach(issue -> result.add(issue.toString()));
        result.addAll(runtimeErrors);
        result.add(monsters.diagnostics());
        result.add(bosses.diagnostics());
        result.add(quests.diagnostics());
        result.add(merchants.diagnostics());
        return List.copyOf(result);
    }

    public boolean reload(boolean validateOnly, Consumer<ContentRegistry.Prepared> completed) {
        return reload(validateOnly, "server", () -> true, completed);
    }

    public boolean reload(boolean validateOnly, String actor, java.util.function.BooleanSupplier permitted,
                          Consumer<ContentRegistry.Prepared> completed) {
        long expected = revision();
        return prepare(() -> StatsService.validate(ContentPacks.read(packs, registry)), result -> {
            if (!validateOnly) {
                if (!permitted.getAsBoolean()) { throw new IllegalStateException("Permission was revoked before apply"); }
                history.commit(result, expected, actor, "reload", System.currentTimeMillis());
                apply(result);
                audit(actor, "reload");
            }
        }, completed);
    }

    public boolean validateDraft(String actor, boolean publish, java.util.function.BooleanSupplier permitted,
                                 Consumer<ContentRegistry.Prepared> completed) {
        checkThread();
        var draft = history.requireDraft(actor);
        var sources = history.sources(draft.documents());
        return prepare(() -> StatsService.validate(registry.prepare(sources)), result -> {
            if (publish) {
                if (!permitted.getAsBoolean()) { throw new IllegalStateException("Permission was revoked before apply"); }
                history.applyDraft(actor, draft.generation(), result, System.currentTimeMillis());
                apply(result); audit(actor, "draft_apply");
            }
        }, completed);
    }

    public boolean importDraft(String actor, java.util.function.BooleanSupplier permitted,
                               Consumer<ContentRegistry.Prepared> completed) {
        checkEditable();
        if (history.draft(actor) != null) { throw new IllegalStateException("Apply or discard your existing draft before importing files"); }
        long expected = revision();
        return prepare(() -> StatsService.validate(ContentPacks.read(packs, registry)), result -> {
            if (!permitted.getAsBoolean()) { throw new IllegalStateException("Permission revoked before import"); }
            history.importDraft(actor, result, expected); audit(actor, "draft_import");
        }, completed);
    }

    public boolean rollback(long target, String actor, java.util.function.BooleanSupplier permitted,
                            Consumer<ContentRegistry.Prepared> completed) {
        checkThread();
        long expected = revision();
        var sources = history.sources(history.revision(target).documents());
        return prepare(() -> StatsService.validate(registry.prepare(sources)), result -> {
            if (!permitted.getAsBoolean()) { throw new IllegalStateException("Permission was revoked before rollback"); }
            history.commit(result, expected, actor, "rollback:" + target, System.currentTimeMillis());
            apply(result); audit(actor, "rollback:" + target);
        }, completed);
    }

    private boolean prepare(java.util.function.Supplier<ContentRegistry.Prepared> operation,
                            Consumer<ContentRegistry.Prepared> publish, Consumer<ContentRegistry.Prepared> completed) {
        checkThread();
        if (busy || closed) {
            return false;
        }
        busy = true;
        CompletableFuture.supplyAsync(operation, loader)
                .whenComplete((prepared, failure) -> server.execute(() -> {
                    busy = false;
                    if (closed) {
                        return;
                    }
                    ContentRegistry.Prepared result = prepared;
                    if (failure != null) {
                        Rotasutils.LOG.error("Rotas content preparation failed", failure);
                        result = new ContentRegistry.Prepared(null, List.of(
                                new ContentRegistry.Diagnostic("packs", "Preparation failed; see server log")));
                    }
                    if (result.valid()) {
                        try { publish.accept(result); }
                        catch (IllegalArgumentException | IllegalStateException ex) { result = rejected(ex.getMessage()); }
                    }
                    if (!result.valid()) { reload.apply(result); }
                    completed.accept(result);
                }));
        return true;
    }

    private void apply(ContentRegistry.Prepared prepared) {
        checkThread();
        long before = reload.revision();
        if (!prepared.valid()) {
            reload.apply(prepared);
            prepared.issues().stream().limit(32).forEach(issue -> Rotasutils.LOG.error("RPG content: {}", issue));
            return;
        }
        reload.restore(prepared, history.revision());
        if (before == reload.revision()) {
            return;
        }
        clearRuleSubscriptions();
        runtimeErrors.clear();
        monsters.contentReloaded();
        equipment.contentReloaded();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                stats(player);
                net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
            } catch (RuntimeException failure) { error("reload-player:" + player.getUUID(), failure); }
        }
        for (ContentId event : content().rules().keySet()) {
            ruleSubscriptions.add(events.subscribe(event, this::applyRules));
        }
        Rotasutils.LOG.info("Rotas RPG kernel revision {}: {} definitions, hash {}",
                revision(), content().definitions().size(), content().hash());
    }

    private static ContentRegistry.Prepared rejected(String message) {
        return new ContentRegistry.Prepared(null, List.of(new ContentRegistry.Diagnostic("admin", message)));
    }

    private void audit(String actor, String operation) {
        RotasData.get(server).audit(java.time.Instant.now() + " actor=" + actor + " action=" + operation
                + " revision=" + revision() + " hash=" + content().hash());
    }

    private void applyRules(KernelEventBus.Event event) {
        for (ContentRegistry.Rule rule : content().rules().getOrDefault(event.type(), List.of())) {
            try {
                if (rule.condition().test(event.context())) {
                    String occurrence = rule.id() + "|" + (rule.once() ? "once" : event.occurrence());
                    String key = "rule:" + UUID.nameUUIDFromBytes(occurrence.getBytes(StandardCharsets.UTF_8));
                    rewards.grant(rule.reward(), key, event.context());
                }
            } catch (RuntimeException ex) {
                error(rule.id().toString(), ex);
            }
        }
    }

    public RewardEngine.Result grant(ServerPlayer player, ContentId id, String occurrence) {
        checkThread();
        RewardEngine.Reward reward = content().rewards().get(id);
        if (reward == null) {
            throw new IllegalArgumentException("Unknown or disabled reward: " + id);
        }
        return rewards.grant(reward, occurrence, new KernelPlayerContext(player, Map.of()));
    }

    public static void emit(ServerPlayer player, String type, String occurrence, Map<String, String> facts) {
        RpgKernel kernel = RotasData.get(player.server).kernel();
        if (kernel == null || kernel.closed) {
            return;
        }
        ContentId id = new ContentId(type);
        if (kernel.events.hasListeners(id)) {
            kernel.events.emit(new KernelEventBus.Event(id, occurrence, new KernelPlayerContext(player, facts)));
        }
        // Quest objectives observe the same events as content rules, without a second event pipeline.
        if (!kernel.content().quests().isEmpty()) {
            kernel.quests().handle(player, id, occurrence, facts);
        }
    }

    public static void objectiveEvent(ServerPlayer player, QuestEvent event) {
        String type = switch (event.kind()) {
            case KILL_ENTITY, KILL_PLAYER -> "entity_killed";
            case COLLECT_ITEM -> "item_obtained";
            case CRAFT_ITEM -> "item_crafted";
            case USE_ITEM -> "item_used";
            case QUEST_COMPLETE -> "quest_completed";
            default -> event.kind().name().toLowerCase(java.util.Locale.ROOT);
        };
        RpgKernel kernel = RotasData.get(player.server).kernel();
        if (kernel == null || kernel.closed || !kernel.events.hasListeners(new ContentId("rotas:" + type))) {
            return;
        }
        String occurrence = event.entityUuid() == null ? UUID.randomUUID().toString() : event.entityUuid().toString();
        Map<String, String> facts = Map.ofEntries(Map.entry("event.amount", Integer.toString(event.amount())),
                Map.entry("event.item", event.stack().isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.stack().getItem()).toString()),
                Map.entry("event.block", event.blockId() == null ? "" : event.blockId().toString()),
                Map.entry("event.dimension", event.dimension()), Map.entry("event.biome", event.biome()),
                Map.entry("event.dialogue", event.dialogueId()), Map.entry("event.choice", event.choiceId()),
                Map.entry("event.dialogue_complete", Boolean.toString(event.dialogueComplete())),
                Map.entry("event.entity_type", event.entityType() == null ? "" : event.entityType().toString()),
                Map.entry("event.custom_id", event.customId()), Map.entry("event.boss", Boolean.toString(event.boss())));
        emit(player, "rotas:" + type, occurrence, facts);
    }

    private void error(String source, RuntimeException exception) {
        String message = source + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (runtimeErrors.size() < 64 && runtimeErrors.add(message)) {
            Rotasutils.LOG.error("RPG runtime failure: {}", message, exception);
        }
    }

    private void checkThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("RPG mutation requires the server thread");
        }
    }

    private void clearRuleSubscriptions() {
        for (AutoCloseable subscription : ruleSubscriptions) {
            try {
                subscription.close();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to detach RPG rule", ex);
            }
        }
        ruleSubscriptions.clear();
    }

    @Override
    public void close() {
        checkThread();
        closed = true;
        clearRuleSubscriptions();
        loader.shutdownNow();
        stats.clear();
        equipment.clear();
        encounters.close();
        monsters.close();
    }
}
