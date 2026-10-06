package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EldritchSkyRendererAnchorTest {
    @Test
    void rendererHasNoInverseCameraPoseHelpers() {
        assertThrows(NoSuchMethodException.class,
                () -> EldritchSkyRenderer.class.getDeclaredMethod("pushCelestial"));
        assertThrows(NoSuchMethodException.class,
                () -> EldritchSkyRenderer.class.getDeclaredMethod("popCelestial"));

        for (Method method : EldritchSkyRenderer.class.getDeclaredMethods()) {
            assertFalse(method.getName().toLowerCase().contains("celestial"),
                    "no celestial inverse-pose helper may remain: " + method.getName());
        }
        for (Field field : EldritchSkyRenderer.class.getDeclaredFields()) {
            assertFalse("org.joml.Matrix4f".equals(field.getType().getName()),
                    "the renderer must not cache a pose matrix: " + field.getName());
        }
    }

    @Test
    void rendererSourceNeverInvertsOrReplacesTheCameraPose() throws Exception {
        String source = readRendererSource();
        if (source == null) {
            return;
        }
        assertFalse(source.contains("invert("), "the renderer must not invert the camera pose");
        assertFalse(source.contains("pushCelestial"), "the renderer must not push an inverted celestial pose");
        assertFalse(source.contains("popCelestial"), "the renderer must not pop an inverted celestial pose");
        assertFalse(source.contains("getXRot") || source.contains("getYRot"),
                "the renderer must read celestial directions, never raw camera angles");
        assertFalse(source.contains(".translate("),
                "celestial sky geometry must never translate by player or camera XYZ");
        assertFalse(source.contains("new Random("),
                "the render loop must never allocate or sample Random");
    }

    private static String readRendererSource() throws Exception {
        String relative = "src/main/java/net/schwarz/rotasutils/client/render/EldritchSkyRenderer.java";
        Path[] candidates = {Path.of(relative), Path.of("common", relative)};
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return Files.readString(candidate);
            }
        }
        return null;
    }
}
