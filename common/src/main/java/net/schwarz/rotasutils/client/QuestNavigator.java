package net.schwarz.rotasutils.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.server.ObjectiveEngine;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.server.RewardService;
import org.joml.Matrix4f;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class QuestNavigator {
    public record Target(Vec3 pos, String dimension, String label) {
    }

    public static final double ARRIVED = 4.0;
    private static final double BEAM_HEIGHT = 48.0;
    private static final double LABEL_MAX = 48.0;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static Target cached;
    private static long cachedTick = -1;

    private QuestNavigator() {
    }

    public static Target target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return null;
        long tick = minecraft.level.getGameTime();
        if (tick != cachedTick) {
            cachedTick = tick;
            cached = resolve(minecraft);
        }
        return cached;
    }

    private static Target resolve(Minecraft minecraft) {
        String questId = ClientQuestTracker.resolved();
        if (questId.isEmpty()) return null;
        QuestDef quest = ClientState.quest(questId);
        ActiveQuest active = ClientState.progress().active(questId);
        if (quest == null || active == null) return null;
        if (active.turnInReady()) {
            for (NpcDef npc : ClientState.npcs().values()) {
                if (npc.enabled() && npc.questIds().contains(questId)) {
                    Target target = npcTarget(minecraft, npc.entityUuid(), npc);
                    if (target != null) return target;
                }
            }
            return null;
        }
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (active.isComplete(i) || objective.hidden() || !QuestService.onPath(quest, active, i)
                    || !ObjectiveEngine.stepUnlocked(quest, active, objective, i)) continue;
            Target target = objectiveTarget(minecraft, objective);
            if (target != null) return target;
            if (!objective.optional()) return null;
        }
        return null;
    }

    private static Target objectiveTarget(Minecraft minecraft, Objective objective) {
        String label = objective.displayText();
        BlockPos pos = RewardService.parsePos(objective.params().getString("pos", ""));
        String dimension = objective.params().getString("dimension", "");
        String npcUuid = objective.params().getString("npc_uuid", "");
        if (pos != null) {
            return new Target(Vec3.atBottomCenterOf(pos), dimension.isEmpty() ? here(minecraft) : dimension, label);
        }
        if (!npcUuid.isEmpty()) {
            NpcDef npc = null;
            for (NpcDef candidate : ClientState.npcs().values()) {
                if (npcUuid.equals(candidate.entityUuid())) npc = candidate;
            }
            Target target = npcTarget(minecraft, npcUuid, npc);
            return target == null ? null : new Target(target.pos(), target.dimension(), label);
        }
        return null;
    }

    private static Target npcTarget(Minecraft minecraft, String uuid, NpcDef npc) {
        String name = npc == null ? "" : npc.name();
        try {
            UUID id = UUID.fromString(uuid);
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity.getUUID().equals(id)) return new Target(entity.position(), here(minecraft), name);
            }
        } catch (IllegalArgumentException notAUuid) {
        }
        if (npc == null || npc.home() == null) return null;
        return new Target(Vec3.atBottomCenterOf(npc.home()), npc.dimension().isEmpty() ? here(minecraft) : npc.dimension(), name);
    }

    private static String here(Minecraft minecraft) {
        return minecraft.level.dimension().location().toString();
    }

    public static boolean sameDimension(Target target) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && target.dimension().equals(here(minecraft));
    }

    public static String hudLine(Target target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!sameDimension(target)) {
            String dim = target.dimension();
            return "◆ " + dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
        }
        Vec3 eye = minecraft.player.position();
        double dx = target.pos().x - eye.x, dz = target.pos().z - eye.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < ARRIVED) return "◆ " + Math.round(target.pos().y - eye.y) + "m ↕";
        double bearing = Math.toDegrees(Math.atan2(-dx, dz)) - minecraft.player.getYRot();
        int sector = (int) Math.floorMod(Math.round(bearing / 45.0), 8);
        return ARROWS[sector] + " " + Math.round(distance) + "m";
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()
                || minecraft.level.getGameTime() % 10 != 0) return;
        Target target = target();
        if (target == null || !sameDimension(target)) return;
        Vec3 from = minecraft.player.position();
        Vec3 delta = target.pos().subtract(from);
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (flat < ARRIVED) return;
        double stepX = delta.x / flat, stepZ = delta.z / flat;
        for (double d = 2.0; d <= Math.min(12.0, flat); d += 1.5) {
            double x = from.x + stepX * d, z = from.z + stepZ * d;
            double y = from.y + 0.15 + (delta.y / flat) * Math.min(d, flat) * 0.25;
            minecraft.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.005, 0);
        }
    }

    public static void render(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) return;
        Target target = target();
        if (target == null || !sameDimension(target)) return;
        Vec3 cam = camera.getPosition();
        Vec3 at = target.pos();
        double distance = at.distanceTo(cam);
        if (distance < ARRIVED) return;
        float alpha = (float) Math.min(1.0, (distance - ARRIVED) / 12.0);

        Matrix4f pose = poseStack.last().pose();
        float x = (float) (at.x - cam.x), y = (float) (at.y - cam.y), z = (float) (at.z - cam.z);
        float w = 0.35f, top = y + (float) BEAM_HEIGHT;
        int a0 = (int) (150 * alpha), a1 = 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(pose, x - w, y, z).color(255, 210, 90, a0).endVertex();
            buffer.vertex(pose, x + w, y, z).color(255, 210, 90, a0).endVertex();
            buffer.vertex(pose, x + w, top, z).color(255, 210, 90, a1).endVertex();
            buffer.vertex(pose, x - w, top, z).color(255, 210, 90, a1).endVertex();
            buffer.vertex(pose, x, y, z - w).color(255, 210, 90, a0).endVertex();
            buffer.vertex(pose, x, y, z + w).color(255, 210, 90, a0).endVertex();
            buffer.vertex(pose, x, top, z + w).color(255, 210, 90, a1).endVertex();
            buffer.vertex(pose, x, top, z - w).color(255, 210, 90, a1).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }

        Vec3 labelAt = at.add(0, 2.5, 0);
        Vec3 toLabel = labelAt.subtract(cam);
        if (distance > LABEL_MAX) labelAt = cam.add(toLabel.scale(LABEL_MAX / distance));
        double shown = labelAt.distanceTo(cam);
        float scale = 0.025f * (float) Math.max(1.0, shown / 10.0);
        String text = "◆ " + target.label() + "  " + Math.round(distance) + "m";
        Font font = minecraft.font;
        poseStack.pushPose();
        try {
            poseStack.translate(labelAt.x - cam.x, labelAt.y - cam.y, labelAt.z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-scale, -scale, scale);
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            int background = (int) (minecraft.options.getBackgroundOpacity(0.35f) * 255.0f) << 24;
            font.drawInBatch(text, -font.width(text) / 2.0f, 0, 0xFFFFD25A, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        } finally {
            poseStack.popPose();
        }
    }
}
