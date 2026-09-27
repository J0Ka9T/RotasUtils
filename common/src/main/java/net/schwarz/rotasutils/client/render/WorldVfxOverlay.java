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

/**
 * The in-world counterpart of {@link SkyShaderOverlay}. A shader pack re-routes the glow, additive and
 * translucent entity passes through its own programs - fogging them, cutting them at the horizon,
 * dropping their blending - so with a pack on, effects hand their drawing over here instead. The pose
 * and projection are captured as the entity is drawn, and the draw is replayed right after the pack has
 * composited the frame, with the vanilla shaders back and depth-tested against the world so blocks
 * still stand in front. Captures live for one frame only.
 */
@Environment(EnvType.CLIENT)
public final class WorldVfxOverlay {
    /** One effect's drawing, replayed with a pose equal to the one it was given. */
    @FunctionalInterface
    public interface Draw {
        void draw(PoseStack pose, MultiBufferSource buffers);
    }

    private record Pending(Matrix4f pose, Matrix3f normal, Draw draw) {
    }

    /** More than any frame should need; guards against a frame whose overlay hook never ran. */
    private static final int MAX_PENDING = 256;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f SAVED_PROJECTION = new Matrix4f();

    private WorldVfxOverlay() {
    }

    /**
     * Takes over {@code draw} when a shader pack is drawing the world, and returns true; otherwise
     * returns false and the caller draws as usual.
     */
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

    /** Called right after the world (and any shader pack's composite) has been drawn, before the hand. */
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
