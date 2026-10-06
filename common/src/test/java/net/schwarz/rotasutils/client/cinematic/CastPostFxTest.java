package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CastPostFxTest {
    private final AtomicReference<CastPostFx.Look> submitted = new AtomicReference<>();
    private final Matrix4f identity = new Matrix4f();

    @BeforeEach
    void captureBackend() {
        CastPostFx.backend = look -> { submitted.set(look); return true; };
        CastPostFx.reset();
    }

    @AfterEach
    void clearBackend() {
        CastPostFx.reset();
        CastPostFx.backend = null;
    }

    private void lens() {
        CastPostFx.request(new Vec3(0, 0, -20), Vec3.ZERO, identity, identity, 0.4f, 0.3f, 0.2f, 0.7f, 8);
        CastPostFx.glow(0.8f, 0.5f, 0.6f);
    }

    @Test
    void terrainPreservesAnotherAbilitysLensInEitherSubmissionOrder() {
        for (boolean terrainFirst : new boolean[]{true, false}) {
            CastPostFx.reset();
            if (!terrainFirst) lens();
            CastPostFx.afterglow(new Vec3(0, 0, -80), Vec3.ZERO, identity, identity, 1, 160);
            if (terrainFirst) lens();
            CastPostFx.process(0);
            CastPostFx.Look look = submitted.get();
            assertEquals(0.4f, look.strength());
            assertEquals(0.7f, look.flash());
            assertEquals(0.8f, look.bloom());
            assertEquals(0.5f, look.rays());
            assertEquals(1, look.terrains().size());
            CastPostFx.process(0);
            assertNull(submitted.get(), "next frame must not retain any field or lens request");
        }
    }

    @Test
    void standaloneAftermathNeedsNoBloomAndSelectsFourNearestFieldsDeterministically() {
        for (boolean reverse : new boolean[]{true, false}) {
            CastPostFx.reset();
            for (int i = 0; i < 6; i++) {
                int distance = reverse ? 6 - i : i + 1;
                CastPostFx.afterglow(new Vec3(distance * 30, 0, 0), Vec3.ZERO, identity, identity, 1, 160);
            }
            CastPostFx.process(0);
            CastPostFx.Look look = submitted.get();
            assertEquals(0, look.bloom());
            assertEquals(0, look.rays());
            assertEquals(0, look.streak());
            assertEquals(4, look.terrains().size());
            for (int i = 0; i < 4; i++) assertEquals((i + 1) * 30, look.terrains().get(i).centre().x);
        }
    }

    @Test
    void rotatedPerspectiveReconstructsCameraRelativeSurfacesFarFromWorldOrigin() {
        Vec3 camera = new Vec3(10_000_000, 120, -10_000_000);
        Vec3 centre = camera.add(20, -10, -80);
        Matrix4f view = new Matrix4f().rotateX(0.15f).rotateY(0.30f);
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 1024);
        CastPostFx.afterglow(centre, camera, view, projection, 1, 160);
        CastPostFx.process(0);
        CastPostFx.Terrain terrain = submitted.get().terrains().get(0);
        Vec3 relative = centre.subtract(camera);
        assertEquals(relative, terrain.centre());
        Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1).mul(view).mul(projection);
        clip.div(clip.w);
        clip.mul(terrain.inverseViewProjection());
        clip.div(clip.w);
        assertEquals(relative.x, clip.x, 0.02);
        assertEquals(relative.y, clip.y, 0.02);
        assertEquals(relative.z, clip.z, 0.02);
    }
}
