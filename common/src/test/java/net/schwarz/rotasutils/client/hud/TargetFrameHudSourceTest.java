package net.schwarz.rotasutils.client.hud;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetFrameHudSourceTest {
    @Test
    void targetFrameUsesIdentityPoseAndNativeGuiCoordinates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/hud/TargetFrameHud.java"));
        int push = source.indexOf("graphics.pose().pushPose()");
        int identity = source.indexOf("graphics.pose().setIdentity()", push);
        int nativeWidth = source.indexOf("minecraft.getWindow().getGuiScaledWidth()", push);
        int fractionalScale = source.indexOf("graphics.pose().scale(toGui, toGui, 1f)", push);
        int pop = source.indexOf("graphics.pose().popPose()", push);

        assertTrue(push >= 0 && identity > push && nativeWidth > identity && pop > nativeWidth,
                "the frame must discard inherited HUD transforms and use native GUI coordinates");
        assertTrue(fractionalScale < 0,
                "the frame must not minify text and pixel art through a fractional pose scale");
        assertTrue(source.contains("private static final float UV_INSET = 0.5f"),
                "the packed atlas needs a half-texel sampling inset");
        int helper = source.indexOf("private static void blitRegion");
        int rawBlit = source.indexOf("graphics.blit(ATLAS");
        assertTrue(helper >= 0 && rawBlit > helper,
                "all atlas draws must be centralized in the inset sampling helper");
        assertTrue(source.indexOf("graphics.flush()", push) > 0,
                "sprite and font batches must be flushed in deterministic draw order");
        assertTrue(source.contains("private static final float TEXT_Z = 200f")
                        && source.contains("graphics.pose().translate(0, 0, TEXT_Z)"),
                "target labels must render on a foreground layer above the panel");
        assertTrue(source.contains("new ResourceLocation(\"minecraft\", \"uniform\")")
                        && source.contains("style.withFont(HUD_FONT)"),
                "target labels must bypass resource-pack TTF atlas corruption");
    }

    @Test
    void targetFrameHasOneLoaderNeutralHudEntryPoint() throws Exception {
        String renderer = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/hud/RotasHudRenderer.java"));
        String client = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/RotasClient.java"));

        int renderVitals = renderer.indexOf("public static void renderVitals");
        int targetMethod = renderer.indexOf("public static void renderMonsterTarget");
        assertTrue(targetMethod > renderVitals);
        assertTrue(!renderer.substring(renderVitals, targetMethod).contains("TargetFrameHud.render"),
                "the Forge vitals paths must not also submit the target frame");
        assertTrue(client.contains("ClientGuiEvent.RENDER_HUD.register")
                        && client.contains("RotasHudRenderer.renderMonsterTarget(graphics, minecraft)"),
                "the target frame must render once from the loader-neutral end-of-HUD event");
    }
}
