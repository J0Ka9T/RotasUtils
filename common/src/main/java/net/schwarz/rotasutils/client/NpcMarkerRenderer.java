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

@Environment(EnvType.CLIENT)
public final class NpcMarkerRenderer {
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
        if (npc == null || !npc.enabled()) {
            return;
        }
        if (minecraft.player.distanceToSqr(entity) > MAX_DISTANCE_SQ) {
            return;
        }
        if (npc.showName() && !npc.name().isBlank() && !npc.title().isBlank()) {
            drawLine(minecraft.font, npc.title(), 0xFFE8C872, 0.27, 0.02f, entity, poseStack, buffer, packedLight);
        }
        if (!npc.showMarker()) {
            return;
        }
        if (npc.role() == NpcDef.Role.CRAFTER && ClientState.levelConfig().season().crafterHideMarker) {
            return;
        }
        String marker = stateFor(npc).marker();
        if (marker.isEmpty()) {
            return;
        }

        drawLine(minecraft.font, marker, stateFor(npc).markerColor(), 0.85, 0.03f, entity, poseStack, buffer, packedLight);
    }

    private static void drawLine(Font font, String text, int color, double above, float scale, Entity entity,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + above, 0.0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(text, -font.width(text) / 2f, 0f, color, true,
                matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    private static NpcDef byEntity(Entity entity) {
        return ClientState.npcByEntity(entity.getUUID());
    }

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
