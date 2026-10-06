package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;
import net.schwarz.rotasutils.core.DharmakayaRules;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.utils.EntitySnapshot;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

final class DharmakayaRenderer {
    static final ResourceLocation SEAL =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/dharmakaya/seal.png");
    static final ResourceLocation EMBLEM =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/dharmakaya/emblem.png");
    static final ResourceLocation ENSO =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/dharmakaya/enso.png");
    static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/dharmakaya/halo.png");
    static final ResourceLocation TALISMAN =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/dharmakaya/talisman.png");
    static final ResourceLocation STRIKE =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/dharmakaya/strike.png");
    static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/celestial/glow.png");

    private static final int GOLD = 0xE0B25C;
    private static final int GOLD_DIM = 0xB98F49;
    private static final int BONE = 0xF2EAD8;
    private static final int INK = 0x0B0F14;

    private static final int AVATAR_DELAY = DharmakayaRules.AVATAR_DELAY_TICKS;
    private static final int TALISMANS = 10;
    private static final int SWORDS = 16;
    private static final Map<Integer, Deque<EntitySnapshot<?>>> FRAMES = new HashMap<>();

    private DharmakayaRenderer() {
    }

    static void tick(AbstractClientPlayer player) {
        var patch = EpicFightCapabilities.getEntityPatch(player, AbstractClientPlayerPatch.class);
        if (patch == null) return;
        Deque<EntitySnapshot<?>> frames = FRAMES.computeIfAbsent(player.getId(), key -> new ArrayDeque<>());
        try {
            frames.addLast(patch.captureEntitySnapshot());
        } catch (RuntimeException ignored) {
            return;
        }
        while (frames.size() > AVATAR_DELAY + 1) frames.removeFirst();
    }

    static void clear(int sourceId) {
        FRAMES.remove(sourceId);
    }

    static void draw(PoseStack pose, MultiBufferSource output, CombatSkillEffects.Cue cue, float age,
                     AbstractClientPlayer source) {
        if (cue.stage() > 0) {
            strike(pose, output, cue, age);
            return;
        }
        var light = output.getBuffer(VfxRenderTypes.ADDITIVE);
        var solid = output.getBuffer(VfxRenderTypes.TRANSLUCENT);
        var seal = output.getBuffer(VfxRenderTypes.lightTextured(SEAL));
        var emblem = output.getBuffer(VfxRenderTypes.lightTextured(EMBLEM));
        var enso = output.getBuffer(VfxRenderTypes.lightTextured(ENSO));
        var halo = output.getBuffer(VfxRenderTypes.lightTextured(HALO));
        var talisman = output.getBuffer(VfxRenderTypes.sprite(TALISMAN));
        var glow = output.getBuffer(VfxRenderTypes.lightTextured(GLOW));

        float fade = fade(age, cue.life());
        if (fade <= 0.01f) return;
        int radius = cue.radius() > 0 ? Math.round(cue.radius()) : DharmakayaRules.DOMAIN_RADIUS;
        var mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 anchor = cue.source();
        Vec3 camLocal = camera.subtract(anchor);
        float open = Mth.clamp(age / 30f, 0f, 1f);

        inkWash(pose, glow, radius * (0.4 + 0.6 * open), fade);
        texDisc(pose, enso, 0, 0.06, 0, radius * (0.9 + 0.1 * open), age * 0.008, GOLD, fade * 0.75f);
        texDisc(pose, seal, 0, 0.09, 0, radius * 0.52 * open, -age * 0.02, GOLD, fade * 0.95f);
        texDisc(pose, emblem, 0, 44 + 4 * open, 0, radius * 0.5 * open, age * 0.006, GOLD, fade * 0.9f);
        talismanRing(pose, talisman, radius * 0.5, radius * 0.30, age * 0.12, fade);
        swordFormation(pose, output, radius, age, fade);
        avatar(pose, output, source, age, camLocal, glow, halo, fade);
    }

    private static float fade(float age, int life) {
        if (age < 0) return 0;
        return Mth.clamp(age / 12f, 0f, 1f) * Mth.clamp((life - age) / 16f, 0f, 1f);
    }

    private static void inkWash(PoseStack pose, VertexConsumer glow, double radius, float fade) {
        if (radius <= .1) return;
        for (int i = 0; i < 3; i++) {
            double r = radius * (1.0 - i * 0.16);
            texDisc(pose, glow, 0, 0.045 + i * 0.002, 0, r, 0, INK, fade * (0.34f + i * 0.10f));
        }
    }

    private static void talismanRing(PoseStack pose, VertexConsumer v, double radius, double height, double turn,
                                     float fade) {
        float a = fade * 0.95f;
        for (int i = 0; i < TALISMANS; i++) {
            double angle = i * (Math.PI * 2 / TALISMANS) + turn;
            double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
            double bob = Math.sin(turn * 4 + i) * 0.35;
            pose.pushPose();
            pose.translate(x, height + bob, z);
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(-angle)));
            pose.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin(turn * 2 + i) * 5)));
            float w = 0.62f, h = 1.24f;
            texQuad(pose, v, -w, 0, 0, w, 0, 0, w, h, 0, -w, h, 0, 0, 1, 1, 0, 0xFFFFFF, a);
            pose.popPose();
        }
    }

    private static void swordFormation(PoseStack pose, MultiBufferSource output, int radius,
                                       float age, float fade) {
        double ring = radius * 0.66;
        for (int i = 0; i < SWORDS; i++) {
            double angle = i * (Math.PI * 2 / SWORDS) + age * 0.006;
            double cycle = (age * 0.007 + i / (double) SWORDS) % 1.0;
            double y = 0.4 + cycle * 10.0;
            float a = fade * (float) Math.sin(cycle * Math.PI) * 0.9f;
            if (a <= 0.02f) continue;
            pose.pushPose();
            pose.translate(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(-angle) + 90));
            pose.mulPose(Axis.ZP.rotationDegrees(-18));
            pose.scale(1.4f, 1.4f, 1.4f);
            SwordSprite.draw(pose, output, 1.35, 0xFFFFFF, a, GOLD, .5f, true);
            pose.popPose();
        }
    }

    private static void avatar(PoseStack pose, MultiBufferSource output, AbstractClientPlayer source, float age,
                               Vec3 camLocal, VertexConsumer glow, VertexConsumer halo, float fade) {
        if (source == null) return;
        Deque<EntitySnapshot<?>> frames = FRAMES.get(source.getId());
        EntitySnapshot<?> snap = frames == null ? null : frames.peekFirst();
        if (snap == null) return;
        float alpha = fade * 0.62f;
        if (alpha <= 0.02f) return;
        double yaw = Math.toRadians(source.yBodyRot);
        double backX = -Math.sin(yaw), backZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        float scale = DharmakayaRules.AVATAR_SCALE * (0.6f + 0.4f * Mth.clamp(fade, 0, 1));
        double bx = backX * 2.2 + rightX * 3.6, bz = backZ * 2.2 + rightZ * 3.6;
        float headY = 1.32f * scale;
        billboard(pose, glow, camLocal, bx, 0.85 * scale, bz, scale * 0.85, 0, GOLD, fade * 0.22f);
        billboard(pose, halo, camLocal, bx, headY, bz, scale * 0.78 + Math.sin(age * .08) * .15, age * .02, BONE,
                fade * 0.85f);
        pose.pushPose();
        pose.translate(bx, 0.0, bz);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(source.yBodyRot + 180));
        pose.mulPoseMatrix(OpenMatrix4f.exportToMojangMatrix(snap.getModelMatrix()));
        snap.renderTextured(pose, output, RenderType::entityTranslucent, Mesh.DrawingFunction.NEW_ENTITY, 0xF000F0,
                .40f, .31f, .17f, alpha);
        pose.scale(1.035f, 1.035f, 1.035f);
        snap.renderTextured(pose, output, RenderType::entityTranslucentEmissive, Mesh.DrawingFunction.NEW_ENTITY,
                0xF000F0, 1.0f, .78f, .42f, fade * 0.5f);
        snap.renderItems(pose, output, RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
                Mesh.DrawingFunction.NEW_ENTITY, 0xF000F0, fade * 0.6f);
        pose.popPose();
    }

    private static void strike(PoseStack pose, MultiBufferSource output, CombatSkillEffects.Cue cue, float age) {
        var light = output.getBuffer(VfxRenderTypes.ADDITIVE);
        var slash = output.getBuffer(VfxRenderTypes.lightTextured(STRIKE));
        float k = Mth.clamp(1 - age / 13f, 0f, 1f);
        if (k <= 0.01f) return;
        var mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 camLocal = camera.subtract(cue.target());
        billboard(pose, slash, camLocal, 0, 1.4, 0, 7.5 + age * .35, Math.toRadians(38), GOLD, k);
        float late = Mth.clamp(1 - (age - 2) / 11f, 0f, 1f) * Mth.clamp((age - 1) / 2f, 0f, 1f);
        billboard(pose, slash, camLocal, 0, 1.4, 0, 6.0 + age * .3, Math.toRadians(-52), BONE, late * .7f);
        ring(pose, light, 1.6 + age * .9, .10 * k, .08, age * .06, GOLD_DIM, k * .7f, 72);
        float drop = Mth.clamp(1 - age / 6f, 0f, 1f);
        if (drop > .01f) {
            double w = .7 * drop + .15;
            for (int plane = 0; plane < 2; plane++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(plane * 90 + 45));
                quad(pose, light, -w, 0, 0, w, 0, 0, w * .2, 14, 0, -w * .2, 14, 0, drop > .6f ? BONE : GOLD,
                        drop * .55f);
                pose.popPose();
            }
        }
        ring(pose, light, .4 + age * .35, .5 * k, .06, -age * .05, BONE, k * k * .6f, 32);
    }

private static void billboard(PoseStack pose, VertexConsumer v, Vec3 camLocal, double cx, double cy, double cz,
                                  double size, double spin, int colour, float a) {
        if (a <= .015f || size <= .02) return;
        Vec3 view = camLocal.subtract(cx, cy, cz);
        if (view.lengthSqr() < 1e-6) view = new Vec3(0, 0, 1);
        view = view.normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(view);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = view.cross(right).normalize();
        double cos = Math.cos(spin), sin = Math.sin(spin);
        Vec3 rx = right.scale(cos).add(up.scale(sin)).scale(size);
        Vec3 uy = up.scale(cos).subtract(right.scale(sin)).scale(size);
        texQuad(pose, v, cx - rx.x - uy.x, cy - rx.y - uy.y, cz - rx.z - uy.z,
                cx + rx.x - uy.x, cy + rx.y - uy.y, cz + rx.z - uy.z,
                cx + rx.x + uy.x, cy + rx.y + uy.y, cz + rx.z + uy.z,
                cx - rx.x + uy.x, cy - rx.y + uy.y, cz - rx.z + uy.z, 0, 0, 1, 1, colour, a);
    }

    private static void ring(PoseStack p, VertexConsumer v, double r, double width, double y, double turn, int c,
                             float a, int segments) {
        if (a <= .01f || r <= .02) return;
        for (int i = 0; i < segments; i++) {
            double u = i * Math.PI * 2 / segments + turn, w = (i + 1) * Math.PI * 2 / segments + turn;
            double bright = .75 + .25 * Math.sin(i * .5 + turn * 3);
            quad(p, v, Math.cos(u) * (r - width), y, Math.sin(u) * (r - width), Math.cos(u) * (r + width), y,
                    Math.sin(u) * (r + width), Math.cos(w) * (r + width), y, Math.sin(w) * (r + width),
                    Math.cos(w) * (r - width), y, Math.sin(w) * (r - width), c, a * (float) bright);
        }
    }

    private static void texDisc(PoseStack p, VertexConsumer v, double x, double y, double z, double radius,
                                double turn, int colour, float a) {
        if (a <= .01f || radius <= .05) return;
        double cos = Math.cos(turn), sin = Math.sin(turn);
        double[][] corners = {{-radius, -radius, 0, 0}, {radius, -radius, 1, 0}, {radius, radius, 1, 1},
                {-radius, radius, 0, 1}};
        for (double[] c : corners) {
            double qx = c[0] * cos - c[1] * sin, qz = c[0] * sin + c[1] * cos;
            tex(p, v, x + qx, y, z + qz, colour, a, (float) c[2], (float) c[3]);
        }
    }

    private static void quad(PoseStack p, VertexConsumer v, double ax, double ay, double az, double bx, double by,
                             double bz, double cx, double cy, double cz, double dx, double dy, double dz, int c,
                             float a) {
        vertex(p, v, ax, ay, az, c, a);
        vertex(p, v, bx, by, bz, c, a);
        vertex(p, v, cx, cy, cz, c, a);
        vertex(p, v, dx, dy, dz, c, a);
    }

    private static void texQuad(PoseStack p, VertexConsumer v, double ax, double ay, double az, double bx, double by,
                                double bz, double cx, double cy, double cz, double dx, double dy, double dz,
                                float u0, float v0, float u1, float v1, int c, float a) {
        tex(p, v, ax, ay, az, c, a, u0, v0);
        tex(p, v, bx, by, bz, c, a, u1, v0);
        tex(p, v, cx, cy, cz, c, a, u1, v1);
        tex(p, v, dx, dy, dz, c, a, u0, v1);
    }

    private static void vertex(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    private static void tex(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a, float u,
                            float vv) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z).uv(u, vv)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }
}
