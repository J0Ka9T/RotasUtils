package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class SkyShaderOverlay {
    private static boolean eldritch;
    private static boolean clash;
    private static boolean sunder;
    private static float partialTick;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f SAVED_PROJECTION = new Matrix4f();

    private SkyShaderOverlay() {
    }

    static void defer(PoseStack pose, float partialTick) {
        capture(pose, partialTick);
        eldritch = true;
    }

    static void deferClash(PoseStack pose, float partialTick) {
        capture(pose, partialTick);
        clash = true;
    }

    static void deferSunder(PoseStack pose, float partialTick) {
        capture(pose, partialTick);
        sunder = true;
    }

    private static void capture(PoseStack pose, float partialTick) {
        VIEW.set(pose.last().pose());
        PROJECTION.set(RenderSystem.getProjectionMatrix());
        SkyShaderOverlay.partialTick = partialTick;
    }

    public static void render() {
        if (!eldritch && !clash && !sunder) return;
        boolean drawEldritch = eldritch;
        boolean drawClash = clash;
        boolean drawSunder = sunder;
        eldritch = false;
        clash = false;
        sunder = false;
        SAVED_PROJECTION.set(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        ShaderPackCompat.overlay(true);
        try {
            if (drawEldritch) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(VIEW);
                EldritchSkyRenderer.render(pose, partialTick, false, false);
            }
            if (drawClash) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(VIEW);
                pose.scale(stretch(), stretch(), stretch());
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                SkyClashRenderer.render(pose, partialTick, false);
            }
            if (drawSunder) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(VIEW);
                pose.scale(stretch(), stretch(), stretch());
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                SkySunderRenderer.render(pose, partialTick, false);
            }
        } finally {
            ShaderPackCompat.overlay(false);
            RenderSystem.setProjectionMatrix(SAVED_PROJECTION, sorting);
            RenderSystem.enableDepthTest();
        }
    }

    static float stretch() {
        float blocks = net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
        return Math.max(1f, blocks * 1.6f / 100f);
    }
}
