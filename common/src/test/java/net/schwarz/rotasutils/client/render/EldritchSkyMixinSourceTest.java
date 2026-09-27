package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EldritchSkyMixinSourceTest {
    @Test
    void mappedDaylightHookIsNarrowAndRegisteredOnce() throws Exception {
        String mixin = read("src/main/java/net/schwarz/rotasutils/mixin/client/ClientLevelSkyMixin.java");
        String config = read("src/main/resources/rotasutils.mixins.json");
        assertTrue(mixin.contains("@Inject(method = \"getSkyDarken\", at = @At(\"RETURN\"), cancellable = true)"));
        assertTrue(mixin.contains("EldritchSkyClientTint.daylight(vanilla, partialTick)"));
        assertEquals(1, occurrences(config, "client.ClientLevelSkyMixin"));
        assertEquals(1, occurrences(config, "client.FogRendererEldritchMixin"));
        assertEquals(1, occurrences(config, "client.LevelRendererSkyMixin"));
    }

    @Test
    void fluidAndInactiveGuardsPrecedeDaylightMutation() throws Exception {
        String tint = read("src/main/java/net/schwarz/rotasutils/client/render/EldritchSkyClientTint.java");
        int daylight = tint.indexOf("public static float daylight");
        int fluidGuard = tint.indexOf("getFluidInCamera() != FogType.NONE", daylight);
        int openness = tint.indexOf("float openness = openness(partialTick)", daylight);
        int mutation = tint.indexOf("daylightBrightness(vanilla, openness)", daylight);
        assertTrue(daylight >= 0 && fluidGuard > daylight && openness > fluidGuard && mutation > openness);
        assertTrue(tint.contains("return vanilla"));
    }

    private static String read(String relative) throws Exception {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) return Files.readString(direct);
        return Files.readString(Path.of("common").resolve(relative));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
