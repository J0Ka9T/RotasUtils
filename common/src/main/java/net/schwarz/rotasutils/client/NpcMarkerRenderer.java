package net.schwarz.rotasutils.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.npc.NpcState;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import org.joml.Matrix4f;

/**
 * The "!" and "?" floating over a configured NPC.
 *
 * <p>This is a display-only mirror of the state the server resolves in {@code NpcService}:
 * the marker can be optimistic or stale for a frame, and nothing depends on it, because the
 * dialogue itself is built from the server's answer when the player actually clicks.</p>
 *
 * <p>Cost matters here - this runs for every rendered entity - so the uuid lookup is the
 * first thing that happens and returns immediately for the overwhelmingly common case of an
 * entity that is not an NPC at all.</p>
 */
@Environment(EnvType.CLIENT)
public final class NpcMarkerRenderer {
    /** Beyond this the marker is unreadable anyway, so it is not drawn. */
    private static final double MAX_DISTANCE = 32.0;
    private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;

    private NpcMarkerRenderer() {
    }

    public static void render(Entity entity, PoseStack poseStack, MultiBufferSource buffer,
                              int packedLight) {
        if (ClientState.npcs().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        NpcDef npc = byEntity(entity);
        if (npc == null || !npc.showMarker() || !npc.enabled()) {
            return;
        }
        // Artisans are meant to be found, not pointed at.
        if (npc.role() == NpcDef.Role.CRAFTER && ClientState.levelConfig().season().crafterHideMarker) {
            return;
        }
        if (minecraft.player.distanceToSqr(entity) > MAX_DISTANCE_SQ) {
            return;
        }
        String marker = stateFor(npc).marker();
        if (marker.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.85, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.03f, -0.03f, 0.03f);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(marker, -font.width(marker) / 2f, 0f, stateFor(npc).markerColor(), true,
                matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    private static NpcDef byEntity(Entity entity) {
        String uuid = entity.getUUID().toString();
        for (NpcDef npc : ClientState.npcs().values()) {
            if (uuid.equals(npc.entityUuid())) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Client-side estimate of what this NPC has to offer, from the progress the player
     * already has. Requirements the client cannot see are treated as met; the server
     * refuses those on accept, with its own message.
     */
    private static NpcState stateFor(NpcDef npc) {
        PlayerProgress progress = ClientState.progress();
        if (npc.requiredLevel() > 0 && progress.level() < npc.requiredLevel()) {
            return NpcState.REQUIREMENTS_NOT_MET;
        }
        NpcState best = NpcState.NO_QUEST;
        for (String questId : npc.questIds()) {
            QuestDef quest = ClientState.quest(questId);
            if (quest == null || !quest.published()) {
                continue;
            }
            ActiveQuest active = progress.active(questId);
            if (active != null) {
                best = best.best(active.turnInReady() ? NpcState.READY_TO_TURN_IN : NpcState.QUEST_ACTIVE);
                continue;
            }
            if (progress.cooldownUntil(questId) > System.currentTimeMillis() / 1000L) {
                best = best.best(NpcState.QUEST_ON_COOLDOWN);
                continue;
            }
            boolean allowed = progress.level() >= quest.requiredLevel()
                    && progress.hasClearance(quest.rank());
            best = best.best(allowed ? NpcState.QUEST_AVAILABLE : NpcState.REQUIREMENTS_NOT_MET);
        }
        return best;
    }
}
