package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;

@Environment(EnvType.CLIENT)
public final class EldritchSkyLetterbox {
    private static final float SPLIT = 0.42f;
    private static final float CLOUDS_GONE = 0.3f;

    private EldritchSkyLetterbox() {
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        if (snapshot == null || snapshot.state != EldritchSkyTransition.State.OPENING) {
            return;
        }
        float openness = EldritchSkyClientState.opennessNow(snapshot, partialTick);
        float flash = (float) Math.exp(-Math.pow((openness - SPLIT) / 0.018f, 2));
        int alpha = Math.round(200 * flash);
        if (alpha > 2) {
            int tint = switch (EldritchSkyPalette.current()) {
                case net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_RED -> 0xFFD8C4;
                case net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_GOLD -> 0xFFF2C8;
                case net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_VOID -> 0xC8E6D0;
                default -> 0xE6DCFF;
            };
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | tint);
        }
    }

    public static boolean hidesClouds(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        if (snapshot == null || snapshot.state == EldritchSkyTransition.State.OFF) {
            return false;
        }
        return EldritchSkyClientState.opennessNow(snapshot, partialTick) >= CLOUDS_GONE;
    }
}
