package net.schwarz.rotasutils.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.MobSetupForm;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.KernelUi;
import net.schwarz.rotasutils.network.RotasNetwork;

import java.util.function.Consumer;

/**
 * One-click saving for the Mob Setup screens.
 *
 * <p>Monster profiles are kernel content with a draft, validate and apply workflow. This service runs
 * that whole workflow on the server in one step, so the easy editor has a single Save button while
 * still getting the same validation, revision history and rollback as the Content Studio.</p>
 */
public final class MobSetupService {
    private MobSetupService() {
    }

    public static void save(ServerPlayer player, String id, String bodyJson) {
        run(player, "save:" + id, "Mob setup saved. New mobs of this kind use it now.", (kernel, actor) -> {
            ContentId contentId = new ContentId(id);
            JsonObject body = JsonParser.parseString(bodyJson).getAsJsonObject();
            // Parse locally first so a mistake gets a plain message before touching the draft.
            MonsterDefinitions.profile(contentId, body);
            kernel.history().begin(actor);
            for (String tier : new MobSetupForm(body).tierIds()) {
                JsonObject definition = MobSetupForm.tierDefinition(tier);
                if (definition != null && !kernel.content().definitions().containsKey(new ContentId(tier))) {
                    kernel.history().put(actor, definition);
                }
            }
            kernel.history().put(actor, MobSetupForm.document(contentId.value(), body));
        });
    }

    /**
     * Saves a category and moves its mobs into it: any other setup with the same scope (global, or the same
     * zones) that still lists one of these mobs loses it, and a setup left with no mobs is deleted. All of
     * it is one content change, so a move never leaves a mob in two places or in none.
     */
    public static void saveCategory(ServerPlayer player, String id, String bodyJson) {
        run(player, "Category saved.", (kernel, actor) -> {
            ContentId contentId = new ContentId(id);
            JsonObject body = JsonParser.parseString(bodyJson).getAsJsonObject();
            MonsterDefinitions.profile(contentId, body);
            MobSetupForm form = new MobSetupForm(body);
            java.util.Set<String> moving = new java.util.HashSet<>(form.entities());
            java.util.Set<String> scope = new java.util.HashSet<>(form.scopeZones());
            kernel.history().begin(actor);
            for (String tier : form.tierIds()) {
                JsonObject definition = MobSetupForm.tierDefinition(tier);
                if (definition != null && !kernel.content().definitions().containsKey(new ContentId(tier))) {
                    kernel.history().put(actor, definition);
                }
            }
            kernel.content().definitions().forEach((otherId, definition) -> {
                if (definition.kind() != net.schwarz.rotasutils.core.ContentRegistry.Kind.MONSTER
                        || otherId.equals(contentId)) {
                    return;
                }
                MobSetupForm other = new MobSetupForm(definition.source().document().getAsJsonObject("body"));
                if (!new java.util.HashSet<>(other.scopeZones()).equals(scope)
                        || other.entities().stream().noneMatch(moving::contains)) {
                    return;
                }
                java.util.List<String> kept = new java.util.ArrayList<>(other.entities());
                kept.removeAll(moving);
                if (kept.isEmpty() && !other.usesOtherSelectors()) {
                    kernel.history().remove(actor, otherId);
                } else {
                    other.setEntities(kept);
                    kernel.history().put(actor, MobSetupForm.document(otherId.value(), other.body()));
                }
            });
            kernel.history().put(actor, MobSetupForm.document(contentId.value(), body));
        });
    }

    /**
     * Puts setups in a folder ({@code folder} empty = no folder). Only the {@code category} label changes;
     * each setup keeps its own settings. Many setups are one content change, so renaming a folder is atomic.
     */
    public static void setFolder(ServerPlayer player, java.util.List<String> ids, String folder) {
        run(player, folder.isBlank() ? "Removed from folder." : "Moved to " + folder + ".", (kernel, actor) -> {
            kernel.history().begin(actor);
            for (String id : ids) {
                ContentId contentId = new ContentId(id);
                var definition = kernel.content().definitions().get(contentId);
                if (definition == null || definition.kind() != net.schwarz.rotasutils.core.ContentRegistry.Kind.MONSTER) {
                    continue;
                }
                MobSetupForm form = new MobSetupForm(definition.source().document().getAsJsonObject("body"));
                form.setCategory(folder);
                kernel.history().put(actor, MobSetupForm.document(contentId.value(), form.body()));
            }
        });
    }

    public static void delete(ServerPlayer player, String id) {
        run(player, "save:" + id, "Mob setup deleted.", (kernel, actor) -> {
            kernel.history().begin(actor);
            kernel.history().remove(actor, new ContentId(id));
        });
    }

    private interface Stage {
        void apply(RpgKernel kernel, String actor);
    }

    /** A save waiting for the content system to be free. */
    private record Pending(java.util.UUID player, String success, Stage stage) {
    }

    /**
     * Saves wait here instead of failing while another content change is validating. Keyed by what they
     * change, so pressing Save twice on one mob keeps only the newest version.
     */
    private static final java.util.LinkedHashMap<String, Pending> QUEUE = new java.util.LinkedHashMap<>();
    private static boolean running;

    private static void run(ServerPlayer player, String success, Stage stage) {
        run(player, "op:" + System.nanoTime(), success, stage);
    }

    private static void run(ServerPlayer player, String key, String success, Stage stage) {
        RpgKernel kernel = RotasData.get(player.server).kernel();
        if (kernel == null) {
            RotasNetwork.feedback(player, false, "The RPG content system is still loading.");
            return;
        }
        if (!allowed(player)) {
            RotasNetwork.feedback(player, false, "Saving mob setups needs content edit and apply permission.");
            return;
        }
        QUEUE.remove(key);
        QUEUE.put(key, new Pending(player.getUUID(), success, stage));
        if (running || kernel.busy()) {
            RotasNetwork.feedback(player, true, "Saving... (waiting for the previous save)");
        }
        drain(player.server);
    }

    /** Called every server tick: starts the next waiting save once the content system is free. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (!QUEUE.isEmpty()) {
            drain(server);
        }
    }

    private static void drain(net.minecraft.server.MinecraftServer server) {
        RpgKernel kernel = RotasData.get(server).kernel();
        if (running || kernel == null || kernel.busy() || QUEUE.isEmpty()) {
            return;
        }
        var first = QUEUE.entrySet().iterator().next();
        QUEUE.remove(first.getKey());
        Pending pending = first.getValue();
        ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
        if (player == null) {
            return;
        }
        String actor = player.getUUID().toString();
        if (kernel.history().draft(actor) != null) {
            // A leftover draft from this screen (a save that failed half-way) is ours to clear; a real
            // Content Studio draft is the admin's, so that one blocks with a clear message.
            RotasNetwork.feedback(player, false,
                    "You have an unfinished draft in the Content Studio. Apply or discard it there first.");
            return;
        }
        Consumer<String> fail = message -> {
            if (kernel.history().draft(actor) != null) {
                kernel.history().discard(actor);
            }
            running = false;
            RotasNetwork.feedback(player, false, "Not saved: " + message);
            KernelUi.sync(player);
        };
        try {
            kernel.checkEditable();
            pending.stage().apply(kernel, actor);
        } catch (RuntimeException error) {
            fail.accept(error.getMessage() == null ? "invalid settings" : error.getMessage());
            return;
        }
        running = true;
        boolean started = kernel.validateDraft(actor, true, () -> allowed(player), result -> {
            running = false;
            if (!result.valid()) {
                fail.accept(result.issues().isEmpty() ? "validation failed" : result.issues().get(0).message());
            } else {
                if (kernel.history().draft(actor) != null) {
                    kernel.history().discard(actor);
                }
                RotasNetwork.feedback(player, true, pending.success());
            }
            // Everyone with the editor open sees the saved version, not just the one who saved.
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                net.schwarz.rotasutils.network.SyncQueue.kernel(online);
            }
        });
        if (!started) {
            running = false;
            if (kernel.history().draft(actor) != null) {
                kernel.history().discard(actor);
            }
            // Busy after all: put it back at the front and try again next tick.
            java.util.LinkedHashMap<String, Pending> rest = new java.util.LinkedHashMap<>(QUEUE);
            QUEUE.clear();
            QUEUE.put(first.getKey(), pending);
            QUEUE.putAll(rest);
        }
    }

    private static boolean allowed(ServerPlayer player) {
        var source = player.createCommandSourceStack();
        return RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT)
                && RotasPermissions.allowed(source, RotasPermissions.Capability.APPLY);
    }
}
