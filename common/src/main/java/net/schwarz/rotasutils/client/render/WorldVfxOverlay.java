package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class WorldVfxOverlay {
    @FunctionalInterface
    public interface Draw {
        void draw(PoseStack pose, MultiBufferSource buffers);
    }

    private record Pending(Matrix4f pose, Matrix3f normal, Draw draw) {
    }

    private static final int MAX_PENDING = 256;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f SAVED_PROJECTION = new Matrix4f();

    private WorldVfxOverlay() {
    }

    public static boolean defer(PoseStack pose, Draw draw) {
        if (!ShaderPackCompat.active()) {
            return false;
        }
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.clear();
        }
        PROJECTION.set(RenderSystem.getProjectionMatrix());
        PENDING.add(new Pending(new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()), draw));
        return true;
    }

    public static void render() {
        if (PENDING.isEmpty()) {
            return;
        }
        SAVED_PROJECTION.set(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        ShaderPackCompat.overlay(true);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            for (Pending pending : PENDING) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(pending.pose());
                pose.last().normal().set(pending.normal());
                pending.draw().draw(pose, buffers);
            }
            buffers.endBatch();
        } finally {
            PENDING.clear();
            ShaderPackCompat.overlay(false);
            RenderSystem.setProjectionMatrix(SAVED_PROJECTION, sorting);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
        }
    }
}
