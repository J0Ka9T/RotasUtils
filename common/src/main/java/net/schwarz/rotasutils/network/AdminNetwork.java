package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.CharacterStatService;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.server.RotasPermissions.Capability;
import net.schwarz.rotasutils.stat.CoreStat;
import net.schwarz.rotasutils.util.ThaiText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AdminNetwork {
    public static final ResourceLocation REQUEST = Rotasutils.id("admin_request_v1");
    public static final ResourceLocation RESPONSE = Rotasutils.id("admin_response_v1");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final class Session {
        private final ProgressChunks chunks = new ProgressChunks(AdminProtocol.CHUNK, AdminProtocol.MAX);
        private long window, completed;
        private int packets;
        private boolean accept(long now) {
            if (now - window > 10000) { window = now; packets = 0; }
            return ++packets <= 48;
        }
    }
    private AdminNetwork() { }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST, (buffer, context) -> {
            AdminProtocol.Segment segment;
            try { segment = AdminProtocol.read(buffer); } catch (RuntimeException invalid) { return; }
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player) || player.hasDisconnected() || !allowed(player, Capability.VIEW)) { return; }
                if (!SESSIONS.containsKey(player.getUUID()) && SESSIONS.size() >= 32) { return; }
                Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
                if (!session.accept(System.currentTimeMillis()) || segment.id() <= session.completed) { return; }
                try {
                    byte[] bytes = session.chunks.accept(segment.id(), segment.total(), segment.index(), segment.bytes(), System.currentTimeMillis());
                    if (bytes == null) { return; }
                    session.completed = segment.id();
                    handle(player, segment.id(), AdminProtocol.decode(bytes));
                } catch (IllegalArgumentException | IllegalStateException error) {
                    Rotasutils.LOG.warn("Admin request rejected for {}: {}", player.getUUID(), error.getMessage());
                }
            });
        });
    }

    public static boolean allowed(ServerPlayer player, Capability capability) {
        return !player.hasDisconnected() && RotasPermissions.allowed(player.createCommandSourceStack(), capability);
    }

    public static void handle(ServerPlayer player, long requestId, CompoundTag request) {
        if (!allowed(player, Capability.VIEW)) { return; }
        String action = request.getString("action");
        if (action.startsWith("player_stats_")) {
            handlePlayerStats(player, requestId, request, action);
            return;
        }
        if (request.hasUUID("ui") && request.getString("action").startsWith("config_")) { ConfigNetwork.handle(player, requestId, request); return; }
        var kernel = RotasData.get(player.server).kernel();
        if (kernel == null || !request.hasUUID("ui")) { return; }
        String actor = player.getUUID().toString();
        try {
            var draft = kernel.history().draft(actor);
            if (!java.util.Set.of("browse", "select", "history", "preview", "inspect_remove", "export").contains(action)) {
                AdminProtocol.expected(request, kernel.revision(), draft == null ? -1 : draft.generation());
                kernel.checkEditable();
            }
            Capability capability = switch (action) {
                case "browse", "select", "inspect_remove" -> Capability.VIEW;
                case "export" -> Capability.AUDIT;
                case "history" -> Capability.AUDIT;
                case "apply" -> Capability.APPLY;
                case "rollback" -> Capability.ROLLBACK;
                default -> Capability.EDIT;
            };
            if (!allowed(player, capability)) { throw new IllegalStateException("Permission denied: " + capability.node()); }
            switch (action) {
                case "browse", "select", "history", "preview", "inspect_remove" -> { }
                case "import" -> {
                    boolean started = kernel.importDraft(actor, () -> allowed(player, Capability.EDIT), result ->
                            reply(player, requestId, request, result.valid() ? "Files imported into a private draft. Review before applying." : "Import rejected", result.issues(), "preview"));
                    if (!started) { throw new IllegalStateException("Content operation is busy"); }
                    return;
                }
                case "export" -> {
                    var path = net.schwarz.rotasutils.core.ConfigFiles.export(dev.architectury.platform.Platform.getConfigFolder().resolve("rotasutils"),
                            kernel.content().definitions().values().stream().map(ContentRegistry.Definition::source).toList(),
                            net.schwarz.rotasutils.server.ConfigService.snapshots(RotasData.get(player.server)));
                    reply(player, requestId, request, "Exported active configuration to " + path, java.util.List.of(), "browse"); return;
                }
                case "create" -> kernel.history().begin(actor);
                case "discard" -> kernel.history().discard(actor);
                case "put" -> {
                    byte[] json = request.getByteArray("json");
                    if (json.length > ContentPacks.MAX_FILE_BYTES) { throw new IllegalArgumentException("Definition exceeds 256 KiB"); }
                    kernel.history().put(actor, ContentPacks.parse(new String(json, StandardCharsets.UTF_8)));
                }
                case "remove" -> kernel.history().remove(actor, new ContentId(request.getString("id")));
                case "validate", "apply" -> {
                    boolean started = kernel.validateDraft(actor, action.equals("apply"), () -> allowed(player, capability),
                            result -> reply(player, requestId, request, result.valid() ? (action.equals("apply") ? "Draft applied" : "Validation succeeded") : "Validation rejected",
                                    result.issues(), "preview"));
                    if (!started) { throw new IllegalStateException("Content operation is busy"); }
                    return;
                }
                case "rollback" -> {
                    boolean started = kernel.rollback(request.getLong("target"), actor, () -> allowed(player, Capability.ROLLBACK),
                            result -> reply(player, requestId, request, result.valid() ? "Rollback applied" : "Rollback rejected", result.issues(), "history"));
                    if (!started) { throw new IllegalStateException("Content operation is busy"); }
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown admin operation");
            }
            if (java.util.Set.of("create", "discard", "put", "remove").contains(action)) {
                RotasData.get(player.server).audit(java.time.Instant.now() + " actor=" + actor + " action=admin_" + action);
            }
            reply(player, requestId, request, "Ready", java.util.List.of(), action);
        } catch (IllegalArgumentException | IllegalStateException | IOException error) {
            reply(player, requestId, request, error.getMessage(), java.util.List.of(), "error");
        }
    }

    private static void handlePlayerStats(ServerPlayer actor, long requestId, CompoundTag request, String action) {
        if (!request.hasUUID("ui")) { return; }
        boolean canEdit = allowed(actor, Capability.EDIT);
        String requestedName = request.getString("player");
        ServerPlayer target = requestedName.length() <= 16
                ? actor.server.getPlayerList().getPlayerByName(requestedName) : null;
        if (target == null) {
            replyPlayerStats(actor, requestId, request, null,
                    ThaiText.t("rotasutils.admin.players.offline"), false);
            return;
        }

        RotasData data = RotasData.get(actor.server);
        boolean success = false;
        String message;
        if (action.equals("player_stats_get")) {
            success = true;
            message = "";
        } else if (!canEdit) {
            message = "Permission denied: " + Capability.EDIT.node();
        } else if (!request.contains("expected_revision", net.minecraft.nbt.Tag.TAG_LONG)) {
            message = ThaiText.t("rotasutils.msg.stat.admin_invalid");
        } else if (action.equals("player_stats_add_points")
                && request.contains("amount", net.minecraft.nbt.Tag.TAG_INT)) {
            int amount = request.getInt("amount");
            CharacterStatService.Result result = CharacterStatService.adminGrantStatPoints(target, data, amount,
                    request.getLong("expected_revision"));
            success = result.success();
            message = result.message();
            if (success) {
                data.audit(actor.getGameProfile().getName() + " granted " + amount + " stat points to "
                        + target.getGameProfile().getName());
                RotasNetwork.syncProgress(target);
            }
        } else if (!action.equals("player_stats_set") || !hasStatValues(request)) {
            message = ThaiText.t("rotasutils.msg.stat.admin_invalid");
        } else {
            Map<CoreStat, Integer> desired = new java.util.EnumMap<>(CoreStat.class);
            desired.put(CoreStat.STR, request.getInt("stat_str"));
            desired.put(CoreStat.VIT, request.getInt("stat_vit"));
            desired.put(CoreStat.INT, request.getInt("stat_int"));
            desired.put(CoreStat.AGI, request.getInt("stat_agi"));
            CharacterStatService.Result result = CharacterStatService.adminSetAllocations(target, data, desired,
                    request.getLong("expected_revision"));
            success = result.success();
            message = result.message();
            if (success) {
                data.audit(actor.getGameProfile().getName() + " set core stats of "
                        + target.getGameProfile().getName() + " to " + desired);
                RotasNetwork.syncProgress(target);
            }
        }
        replyPlayerStats(actor, requestId, request, target, message, success);
    }

    private static boolean hasStatValues(CompoundTag request) {
        return request.contains("stat_str", net.minecraft.nbt.Tag.TAG_INT)
                && request.contains("stat_vit", net.minecraft.nbt.Tag.TAG_INT)
                && request.contains("stat_int", net.minecraft.nbt.Tag.TAG_INT)
                && request.contains("stat_agi", net.minecraft.nbt.Tag.TAG_INT);
    }

    private static void replyPlayerStats(ServerPlayer actor, long requestId, CompoundTag request,
                                         ServerPlayer target, String message, boolean success) {
        if (actor.hasDisconnected() || !allowed(actor, Capability.VIEW)) { return; }
        CompoundTag response = new CompoundTag();
        response.putUUID("ui", request.getUUID("ui"));
        response.putString("mode", "player_stats");
        response.putString("message", message == null ? "" : message.substring(0, Math.min(message.length(), 512)));
        response.putBoolean("success", success);
        response.putBoolean("can_edit", allowed(actor, Capability.EDIT));
        if (target != null && !target.hasDisconnected()) {
            RotasData data = RotasData.get(actor.server);
            PlayerProgress progress = data.progress(target.getUUID());
            var profile = progress.rpg();
            response.putString("target", target.getGameProfile().getName());
            response.putInt("level", progress.level());
            var curve = data.levelConfig().curve();
            response.putInt("max_level", curve.maxLevel());
            response.putLong("xp", progress.xp());
            long toNext = curve.xpToNext(progress.level());
            response.putLong("xp_to_next", toNext == Long.MAX_VALUE ? -1 : toNext);
            response.putInt("skill_points", progress.skillPoints());
            response.putInt("stat_points", profile.statPoints());
            response.putInt("stat_cap", CharacterStatService.rules(data).maxPerStat);
            response.putLong("stat_revision", profile.revision());
            response.putInt("str", CharacterStatService.allocated(progress, CoreStat.STR));
            response.putInt("vit", CharacterStatService.allocated(progress, CoreStat.VIT));
            response.putInt("int", CharacterStatService.allocated(progress, CoreStat.INT));
            response.putInt("agi", CharacterStatService.allocated(progress, CoreStat.AGI));
        }
        AdminProtocol.send(requestId, response, packet -> NetworkManager.sendToPlayer(actor, RESPONSE, packet));
    }

    private static void reply(ServerPlayer player, long requestId, CompoundTag request, String message,
                              java.util.List<ContentRegistry.Diagnostic> issues, String mode) {
        if (player.hasDisconnected() || !allowed(player, Capability.VIEW)) { return; }
        var kernel = RotasData.get(player.server).kernel(); var history = kernel.history();
        String actor = player.getUUID().toString(); var draft = history.draft(actor);
        CompoundTag response = new CompoundTag(); response.putUUID("ui", request.getUUID("ui"));
        response.putLong("revision", kernel.revision()); response.putLong("generation", draft == null ? -1 : draft.generation());
        response.putLong("base", draft == null ? -1 : draft.base()); response.putString("message", limit(message, 512));
        response.putString("mode", mode);
        for (Capability capability : Capability.values()) { response.putBoolean(capability.name(), allowed(player, capability)); }
        ListTag lines = new ListTag(), ids = new ListTag();
        issues.stream().limit(16).forEach(issue -> lines.add(StringTag.valueOf(limit(issue.toString(), 512))));
        ListTag diagnostics = new ListTag();
        issues.stream().limit(64).forEach(issue -> {
            var detail = new CompoundTag(); detail.putString("source", limit(issue.source(), 256));
            detail.putString("field", "body"); detail.putString("message", limit(issue.message(), 512)); diagnostics.add(detail);
        });
        response.put("diagnostics", diagnostics);
        var sources = draft == null ? kernel.content().definitions().values().stream().map(ContentRegistry.Definition::source).toList()
                : history.sources(draft.documents());
        ListTag references = new ListTag();
        sources.stream().limit(4096).forEach(source -> references.add(StringTag.valueOf(source.document().get("id").getAsString())));
        response.put("references", references);
        if (mode.equals("history") && allowed(player, Capability.AUDIT)) {
            var revisions = history.revisions();
            revisions.stream().skip(Math.max(0, revisions.size() - 32)).forEach(revision -> {
                lines.add(StringTag.valueOf("#" + revision.number() + " " + revision.reason() + " " + java.time.Instant.ofEpochMilli(revision.time())));
                ids.add(StringTag.valueOf(Long.toString(revision.number())));
            });
        } else if (mode.equals("inspect_remove")) {
            String target = new ContentId(request.getString("id")).value();
            ListTag dependents = new ListTag();
            sources.stream().filter(source -> !source.document().get("id").getAsString().equals(target)
                    && mentions(source.document(), target)).limit(128).forEach(source -> dependents.add(StringTag.valueOf(source.document().get("id").getAsString())));
            response.put("dependents", dependents);
        } else if (mode.equals("preview") && draft != null) {
            var preview = history.preview(actor);
            lines.add(StringTag.valueOf("Added " + preview.added().size() + ", changed " + preview.changed().size() + ", removed " + preview.removed().size()));
            preview.added().stream().limit(16).forEach(id -> lines.add(StringTag.valueOf("+ " + id)));
            preview.changed().stream().limit(16).forEach(id -> lines.add(StringTag.valueOf("~ " + id)));
            preview.removed().stream().limit(16).forEach(id -> lines.add(StringTag.valueOf("- " + id)));
        } else if (mode.equals("select")) {
            String id = new ContentId(request.getString("id")).value();
            var selected = sources.stream().filter(source -> id.equals(source.document().get("id").getAsString())).findFirst();
            selected.ifPresent(source -> {
                response.putString("selected", id);
                response.putByteArray("json", source.document().toString().getBytes(StandardCharsets.UTF_8));
            });
            if (selected.isEmpty()) { response.putString("message", "Unknown definition: " + id); }
        } else {
            String query = request.getString("query").toLowerCase(java.util.Locale.ROOT);
            String kind = request.getString("kind");
            sources = sources.stream().filter(source -> source.document().get("id").getAsString().toLowerCase(java.util.Locale.ROOT).contains(query)
                    && (kind.isBlank() || source.document().get("kind").getAsString().equalsIgnoreCase(kind))).toList();
            int page = Math.max(0, Math.min(request.getInt("page"), Math.max(0, (sources.size() - 1) / 32)));
            response.putInt("page", page); response.putInt("pages", Math.max(1, (sources.size() + 31) / 32));
            sources.stream().sorted(java.util.Comparator.comparing(source -> source.document().get("id").getAsString()))
                    .skip(page * 32L).limit(32).forEach(source -> {
                        String id = source.document().get("id").getAsString();
                        lines.add(StringTag.valueOf(id + "  " + source.document().get("kind").getAsString()));
                        ids.add(StringTag.valueOf(id));
                    });
        }
        response.put("lines", lines); response.put("ids", ids);
        AdminProtocol.send(requestId, response, packet -> NetworkManager.sendToPlayer(player, RESPONSE, packet));
    }

    private static String limit(String message, int length) {
        return message == null ? "Operation rejected" : message.substring(0, Math.min(message.length(), length));
    }
    private static boolean mentions(com.google.gson.JsonElement value, String id) {
        if (value.isJsonPrimitive()) { return value.getAsJsonPrimitive().isString() && value.getAsString().equals(id); }
        if (value.isJsonArray()) { for (var child : value.getAsJsonArray()) { if (mentions(child, id)) { return true; } } }
        if (value.isJsonObject()) { for (var entry : value.getAsJsonObject().entrySet()) { if (mentions(entry.getValue(), id)) { return true; } } }
        return false;
    }
    public static void forget(UUID id) { SESSIONS.remove(id); }
    public static void clear() { SESSIONS.clear(); }
}
