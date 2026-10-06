package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ConfigService;
import net.schwarz.rotasutils.server.RotasPermissions.Capability;
import net.schwarz.rotasutils.server.Validation;
import java.util.*;

public final class ConfigNetwork {
    private ConfigNetwork() { }

    public static void handle(ServerPlayer player, long requestId, CompoundTag request) {
        var response = new CompoundTag(); response.putUUID("ui", request.getUUID("ui"));
        String actor = player.getUUID().toString(); var data = RotasData.get(player.server);
        String domain = ""; ListTag lines = new ListTag();
        try {
            String operation = request.getString("action");
            Capability permission = Set.of("config_status", "config_list").contains(operation) ? Capability.VIEW
                    : Set.of("config_apply", "config_commit").contains(operation) ? Capability.APPLY : Capability.EDIT;
            if (operation.equals("config_commit") && !AdminNetwork.allowed(player, Capability.EDIT)) { throw new IllegalStateException("Permission denied: " + Capability.EDIT); }
            if (!AdminNetwork.allowed(player, permission)) { throw new IllegalStateException("Permission denied: " + permission); }
            if (Set.of("config_list", "config_import_files", "config_export").contains(operation)) { workspace(player, requestId, request); return; }
            domain = request.contains("domain") ? request.getString("domain") : ConfigService.domain(request.getString("edit_action"), request.getCompound("payload"));
            ConfigService.requireDomain(domain); var history = data.configHistory();
            switch (operation) {
                case "config_status" -> response.putString("message", "Save a private draft, review differences, then apply.");
                case "config_stage" -> {
                    stage(data, actor, domain, request);
                    response.putString("message", "Draft saved on server. Live settings are unchanged.");
                }
                case "config_commit" -> {
                    long generation = stage(data, actor, domain, request);
                    var issues = ConfigService.validate(data, domain, history.draft(actor, domain).value());
                    issues.forEach(issue -> lines.add(StringTag.valueOf(issue.severity() + " " + issue.field() + ": " + issue.message())));
                    if (issues.stream().anyMatch(i -> i.severity() == Validation.Severity.ERROR)) {
                        response.putString("message", "Not saved live: fix the errors below, then press Back and save again.");
                    } else {
                        ConfigService.checkBoardPermission(player.createCommandSourceStack(), domain, history.draft(actor, domain).value());
                        ConfigService.checkSettingsPermission(player.createCommandSourceStack(), domain, history.draft(actor, domain).value());
                        ConfigService.checkCommandPermission(player.createCommandSourceStack(), domain, history.draft(actor, domain).value());
                        ConfigService.apply(player.server, actor, domain, generation);
                        response.putBoolean("applied", true);
                        response.putString("message", request.getString("edit_action").equals("publish_quest") ? "Published. Changes are live." : "Saved. Changes are live.");
                    }
                }
                case "config_review" -> {
                    var draft = history.check(actor, domain, ConfigService.snapshot(data, domain), request.getLong("generation"));
                    var issues = ConfigService.validate(data, domain, draft.value());
                    boolean valid = issues.stream().noneMatch(i -> i.severity() == Validation.Severity.ERROR);
                    response.putBoolean("reviewed", valid);
                    response.putString("message", valid ? "Review complete. Apply makes these changes live." : "Fix the listed errors before applying.");
                    ListTag diagnostics = new ListTag(); issues.forEach(issue -> { diagnostics.add(issue.save()); lines.add(StringTag.valueOf(issue.field() + ": " + issue.message())); });
                    response.put("diagnostics", diagnostics);
                }
                case "config_apply" -> {
                    var applying = history.draft(actor, domain); if (applying == null) { throw new IllegalStateException("Save a draft first"); }
                    ConfigService.checkBoardPermission(player.createCommandSourceStack(), domain, applying.value());
                    ConfigService.checkSettingsPermission(player.createCommandSourceStack(), domain, applying.value());
                    ConfigService.checkCommandPermission(player.createCommandSourceStack(), domain, applying.value());
                    ConfigService.apply(player.server, actor, domain, request.getLong("generation")); response.putBoolean("applied", true); response.putString("message", "Configuration applied. Changes are live.");
                }
                case "config_discard" -> { history.discard(actor, domain, request.getLong("generation")); response.putString("message", "Draft discarded."); }
                case "config_restore" -> {
                    if (!AdminNetwork.allowed(player, Capability.ROLLBACK)) { throw new IllegalStateException("Rollback permission required"); }
                    var revisions = history.revisions(domain);
                    if (revisions.isEmpty()) { throw new IllegalStateException("No retained revision for this configuration"); }
                    if (history.draft(actor, domain) != null) { throw new IllegalStateException("Discard or apply your draft before restoring a revision"); }
                    history.stage(actor, domain, ConfigService.snapshot(data, domain), revisions.get(revisions.size() - 1).getCompound("before"), -1);
                    response.putString("message", "Previous values staged for review. Apply to restore them.");
                }
                default -> throw new IllegalArgumentException("Unknown configuration operation");
            }
        } catch (RuntimeException error) { response.putString("message", error.getMessage() == null ? "Configuration rejected" : error.getMessage()); }
        if (!domain.isEmpty()) {
            try {
                var live = ConfigService.snapshot(data, domain); response.put("live", live);
                var draft = data.configHistory().draft(actor, domain);
                response.putLong("generation", draft == null ? -1 : draft.generation());
                if (draft != null) { differences("", live, draft.value(), lines); }
            } catch (RuntimeException ignored) { response.putLong("generation", -1); }
        }
        if (lines.isEmpty()) { lines.add(StringTag.valueOf("No staged differences.")); }
        response.put("lines", lines); response.putBoolean("APPLY", AdminNetwork.allowed(player, Capability.APPLY));
        response.putBoolean("ROLLBACK", AdminNetwork.allowed(player, Capability.ROLLBACK));
        AdminProtocol.send(requestId, response, packet -> NetworkManager.sendToPlayer(player, AdminNetwork.RESPONSE, packet));
    }

    private static long stage(RotasData data, String actor, String domain, CompoundTag request) {
        var live = ConfigService.snapshot(data, domain);
        String action = request.getString("edit_action");
        var edited = request.getCompound("payload").getCompound(ConfigService.payloadKey(action));
        var value = domain.startsWith("quest/") ? edited.copy() : ConfigService.merge(live, edited);
        if (action.equals("publish_quest")) { value.putBoolean("published", true); }
        var previous = data.configHistory().draft(actor, domain);
        long expected = previous == null ? -1 : previous.generation();
        data.configHistory().stage(actor, domain, live, value, expected);
        return expected + 1;
    }

    private static void workspace(ServerPlayer player, long requestId, CompoundTag request) {
        var data = RotasData.get(player.server); String actor = player.getUUID().toString();
        CompoundTag response = new CompoundTag(); response.putUUID("ui", request.getUUID("ui"));
        ListTag lines = new ListTag(), domains = new ListTag();
        try {
            switch (request.getString("action")) {
                case "config_list" -> { }
                case "config_import_files" -> ConfigService.importFiles(data, actor, dev.architectury.platform.Platform.getConfigFolder().resolve("rotasutils"))
                        .forEach(line -> lines.add(StringTag.valueOf(line)));
                case "config_export" -> {
                    if (!AdminNetwork.allowed(player, Capability.AUDIT)) { throw new IllegalStateException("Audit permission required to export"); }
                    var sources = data.kernel().content().definitions().values().stream().map(net.schwarz.rotasutils.core.ContentRegistry.Definition::source).toList();
                    var path = net.schwarz.rotasutils.core.ConfigFiles.export(dev.architectury.platform.Platform.getConfigFolder().resolve("rotasutils"), sources, ConfigService.snapshots(data));
                    lines.add(StringTag.valueOf("Exported to " + path));
                }
            }
        } catch (java.io.IOException | RuntimeException error) { lines.add(StringTag.valueOf("Rejected: " + error.getMessage())); }
        Set<String> all = new TreeSet<>(ConfigService.snapshots(data).keySet()); all.addAll(data.configHistory().domains(actor));
        all.stream().limit(1024).forEach(domain -> domains.add(StringTag.valueOf(domain)));
        response.put("domains", domains); response.put("lines", lines);
        response.putBoolean("EDIT", AdminNetwork.allowed(player, Capability.EDIT)); response.putBoolean("AUDIT", AdminNetwork.allowed(player, Capability.AUDIT));
        AdminProtocol.send(requestId, response, packet -> NetworkManager.sendToPlayer(player, AdminNetwork.RESPONSE, packet));
    }

    public static void differences(String path, CompoundTag before, CompoundTag after, ListTag lines) {
        Set<String> keys = new TreeSet<>(before.getAllKeys()); keys.addAll(after.getAllKeys());
        for (String key : keys) {
            if (lines.size() >= 128) { return; }
            Tag a = before.get(key), b = after.get(key); String field = path.isEmpty() ? key : path + "." + key;
            if (Objects.equals(a, b)) { continue; }
            if (a instanceof CompoundTag ac && b instanceof CompoundTag bc) { differences(field, ac, bc, lines); }
            else { lines.add(StringTag.valueOf(field + ": " + shortValue(a) + " -> " + shortValue(b))); }
        }
    }
    private static String shortValue(Tag tag) { String text = tag == null ? "(absent)" : tag.toString(); return text.length() > 120 ? text.substring(0, 120) + "..." : text; }
}
