package net.schwarz.rotasutils.client.hud;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobPlateRendererSourceTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void plateLabelsBypassResourcePackFontAtlas() throws Exception {
        String source = read("src/main/java/net/schwarz/rotasutils/client/hud/MobPlateRenderer.java");
        assertTrue(source.contains("new ResourceLocation(\"minecraft\", \"uniform\")")
                        && source.contains("style.withFont(PLATE_FONT)"),
                "plate labels must use the built-in unicode font, not the TTF pack atlas");
        assertTrue(source.contains("Component levelText = plateText(") && source.contains("Component name = plateText("),
                "plate labels must be drawn as font-styled components, not raw default-font strings");
    }

    @Test
    void plateLayersSitBehindTheTextPlane() throws Exception {
        String source = read("src/main/java/net/schwarz/rotasutils/client/hud/MobPlateRenderer.java");
        assertTrue(source.contains("private static final float PLATE_Z")
                        && source.contains("private static final float FILL_Z")
                        && source.contains("private static final float RIM_Z"),
                "each plate layer needs its own depth");
        assertTrue(source.contains("quads.vertex(matrix, x0, y0, z)"),
                "quads must be emitted at their layer depth instead of the text plane");
        assertTrue(!source.contains("quads.vertex(matrix, x0, y0, 0)"),
                "no plate quad may share depth 0 with the labels");
    }

    @Test
    void plateIsSubmittedOncePerFrame() throws Exception {
        String mixin = read("src/main/java/net/schwarz/rotasutils/mixin/client/LevelRendererOutlineMixin.java");
        assertEquals(1, mixin.split("MobPlateRenderer.render\\(", -1).length - 1,
                "the world plate must be drawn from a single hook");
    }
}
