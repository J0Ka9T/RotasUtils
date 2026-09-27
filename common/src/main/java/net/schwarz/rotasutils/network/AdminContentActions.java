package net.schwarz.rotasutils.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.server.CharacterStatService;
import net.schwarz.rotasutils.server.SeasonConfigFile;
import net.schwarz.rotasutils.server.SeasonService;
import net.schwarz.rotasutils.server.TitleService;
import net.schwarz.rotasutils.title.TitleDef;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Admin editors for content that used to be command- or raw-JSON-only: world event kinds, the
 * world event schedule, and titles. The caller has already checked admin permission.
 *
 * <p>Season edits go through the same path as the season editor: the live rules are written to
 * JSON, one block is patched, and {@link SeasonRules#fromJson} parses and sanitizes the result, so
 * a form can never store a value the season file itself would reject.</p>
 */
public final class AdminContentActions {
    public static final Set<String> ACTIONS = Set.of(
            "worldevent_type_save", "worldevent_type_delete", "worldevent_settings_save",
            "title_admin_save", "title_admin_delete", "title_admin_grant", "title_admin_revoke",
            "title_admin_release");

    private static final String ID_PATTERN = "[a-z0-9_:./-]{1,48}";
    private static final int MAX_JSON = 32_768;

    private AdminContentActions() {
    }

    public static void handle(ServerPlayer player, RotasData data, String action, CompoundTag payload) {
        try {
            switch (action) {
                case "worldevent_type_save" -> saveEventType(player, data, payload);
                case "worldevent_type_delete" -> deleteEventType(player, data, payload.getString("id"));
                case "worldevent_settings_save" -> saveEventSettings(player, data, payload);
                case "title_admin_save" -> saveTitle(player, data, payload);
                case "title_admin_delete" -> deleteTitle(player, data, payload.getString("id"));
                case "title_admin_grant", "title_admin_revoke" -> grantOrRevoke(player, data, payload,
                        action.equals("title_admin_grant"));
                case "title_admin_release" -> releaseUnique(player, data, payload.getString("id"));
                default -> {
                }
            }
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, "Not saved: " + failure.getMessage());
        }
    }

    // ---- World events ---------------------------------------------------------------------------

    private static void saveEventType(ServerPlayer player, RotasData data, CompoundTag payload) {
        String id = payload.getString("id").trim();
        String previous = payload.getString("previous").trim();
        if (!id.matches(ID_PATTERN)) {
            RotasNetwork.feedback(player, false, "Event id must be 1-48 of a-z 0-9 _ : . / -");
            return;
        }
        JsonObject def = parseObject(payload.getString("json"));
        var types = SeasonService.rules(data).worldEvents.types;
        boolean adding = !types.containsKey(id) && (previous.isEmpty() || !types.containsKey(previous));
        if (adding && types.size() >= SeasonRules.WorldEventRules.MAX_TYPES) {
            RotasNetwork.feedback(player, false, "At most " + SeasonRules.WorldEventRules.MAX_TYPES + " event types");
            return;
        }
        patchSeason(player, data, root -> {
            JsonObject typesJson = worldEvents(root).getAsJsonObject("types");
            if (!previous.isEmpty() && !previous.equals(id)) typesJson.remove(previous);
            typesJson.add(id, def);
        }, "saved world event type " + id);
    }

    private static void deleteEventType(ServerPlayer player, RotasData data, String id) {
        if (!SeasonService.rules(data).worldEvents.types.containsKey(id)) {
            RotasNetwork.feedback(player, false, "Unknown event type " + id);
            return;
        }
        // A running event of this kind ends on its own: WorldEvent reads its kind live.
        patchSeason(player, data, root -> worldEvents(root).getAsJsonObject("types").remove(id),
                "deleted world event type " + id);
    }

    private static void saveEventSettings(ServerPlayer player, RotasData data, CompoundTag payload) {
        JsonObject settings = parseObject(payload.getString("json"));
        settings.remove("types"); // kinds are edited one at a time, never replaced wholesale here
        patchSeason(player, data, root -> {
            JsonObject block = worldEvents(root);
            for (var entry : settings.entrySet()) block.add(entry.getKey(), entry.getValue());
        }, "saved world event settings");
    }

    private static JsonObject worldEvents(JsonObject root) {
        if (!root.has("worldEvents") || !root.get("worldEvents").isJsonObject()) root.add("worldEvents", new JsonObject());
        JsonObject block = root.getAsJsonObject("worldEvents");
        if (!block.has("types") || !block.get("types").isJsonObject()) block.add("types", new JsonObject());
        return block;
    }

    private static void patchSeason(ServerPlayer player, RotasData data, Consumer<JsonObject> patch, String audit) {
        JsonObject root = JsonParser.parseString(SeasonService.rules(data).toJson()).getAsJsonObject();
        patch.accept(root);
        SeasonRules rules = SeasonRules.fromJson(root.toString());
        data.levelConfig().setSeason(rules);
        try {
            SeasonConfigFile.write(player.server, data.levelConfig().season());
        } catch (java.io.IOException failure) {
            RotasNetwork.feedback(player, false, "Applied, but season.json could not be written: " + failure.getMessage());
        }
        data.setDirty();
        data.audit(player.getGameProfile().getName() + " " + audit);
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            CharacterStatService.apply(online, data);
            RotasNetwork.syncContent(online);
        }
        RotasNetwork.feedback(player, true, "Saved");
        RotasNetwork.openWorldEventAdmin(player);
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON) {
            throw new IllegalArgumentException("empty or oversized value");
        }
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) throw new IllegalArgumentException("expected a JSON object");
        return element.getAsJsonObject();
    }

    // ---- Titles ---------------------------------------------------------------------------------

    private static void saveTitle(ServerPlayer player, RotasData data, CompoundTag payload) {
        if (!payload.contains("title", Tag.TAG_COMPOUND)) return;
        TitleDef title = TitleDef.load(payload.getCompound("title"));
        String previous = payload.getString("previous");
        if (!title.id().matches(ID_PATTERN)) {
            RotasNetwork.feedback(player, false, "Title id must be 1-48 of a-z 0-9 _ : . / -");
            return;
        }
        if (title.name().length() > 64 || title.description().length() > 256) {
            RotasNetwork.feedback(player, false, "Name max 64, description max 256 characters");
            return;
        }
        if (!previous.isEmpty() && !previous.equals(title.id())) {
            if (data.title(title.id()) != null) {
                RotasNetwork.feedback(player, false, "A title with id " + title.id() + " already exists");
                return;
            }
            data.removeTitle(previous);
        }
        if (previous.isEmpty() && data.title(title.id()) == null) {
            title.setOrder(data.titles().values().stream().mapToInt(TitleDef::order).max().orElse(-1) + 1);
        }
        data.putTitle(title);
        data.audit(player.getGameProfile().getName() + " saved title " + title.id());
        RotasNetwork.syncContent(player.server);
        RotasNetwork.feedback(player, true, "Title saved: " + title.name());
    }

    private static void deleteTitle(ServerPlayer player, RotasData data, String id) {
        if (data.title(id) == null) {
            RotasNetwork.feedback(player, false, "Unknown title " + id);
            return;
        }
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            TitleService.revoke(online, data, id);
        }
        data.removeTitle(id);
        data.audit(player.getGameProfile().getName() + " deleted title " + id);
        RotasNetwork.syncContent(player.server);
        RotasNetwork.feedback(player, true, "Title deleted: " + id);
    }

    private static void grantOrRevoke(ServerPlayer player, RotasData data, CompoundTag payload, boolean grant) {
        String id = payload.getString("id");
        String name = payload.getString("player").trim();
        TitleDef title = data.title(id);
        if (title == null) {
            RotasNetwork.feedback(player, false, "Unknown title " + id);
            return;
        }
        java.util.UUID targetId = name.isEmpty() ? player.getUUID() : resolve(player, name);
        if (targetId == null) {
            RotasNetwork.feedback(player, false, "Unknown player: " + name);
            return;
        }
        String who = name.isEmpty() ? player.getGameProfile().getName() : name;
        boolean done;
        if (grant) {
            ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
            if (target == null) {
                RotasNetwork.feedback(player, false, "Player must be online to receive a title: " + who);
                return;
            }
            TitleService.setBlocked(data.progress(targetId), id, false);
            done = TitleService.award(target, data, title, true);
        } else {
            // Blocks automatic re-earning, or the checker would hand it straight back.
            done = TitleService.revokeById(player.server, data, targetId, id, true);
        }
        if (done) data.audit(player.getGameProfile().getName() + (grant ? " granted " : " revoked ") + id + " / " + who);
        RotasNetwork.feedback(player, done, done
                ? (grant ? "Granted " : "Revoked ") + title.name() + " - " + who
                : grant ? "Could not grant (already owned, or unique held by someone else - use Free unique)"
                        : "Not owned by " + who + " (blocked from auto-earning it anyway)");
    }

    /** Takes a unique title back from whoever holds it, online or not, so a real player can claim it. */
    private static void releaseUnique(ServerPlayer player, RotasData data, String id) {
        TitleDef title = data.title(id);
        java.util.UUID owner = data.uniqueTitleOwner(id);
        if (title == null || !title.unique() || owner == null) {
            RotasNetwork.feedback(player, false, "Nobody holds unique title " + id);
            return;
        }
        TitleService.revokeById(player.server, data, owner, id, true);
        String who = player.server.getProfileCache() == null ? owner.toString()
                : player.server.getProfileCache().get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString());
        data.audit(player.getGameProfile().getName() + " freed unique title " + id + " from " + who);
        RotasNetwork.feedback(player, true, "Freed " + title.name() + " from " + who + " - the next player to qualify gets it");
    }

    private static java.util.UUID resolve(ServerPlayer player, String name) {
        ServerPlayer online = player.server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        var cache = player.server.getProfileCache();
        return cache == null ? null : cache.get(name).map(com.mojang.authlib.GameProfile::getId).orElse(null);
    }
}
