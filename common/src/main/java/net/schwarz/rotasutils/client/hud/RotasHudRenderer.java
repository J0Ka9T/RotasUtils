package net.schwarz.rotasutils.client.hud;

import com.schwarz.lenlorui.ui.UiCanvas;
import com.schwarz.lenlorui.ui.UiColor;
import com.schwarz.lenlorui.ui.UiFormat;
import com.schwarz.lenlorui.ui.UiRect;
import com.schwarz.lenlorui.ui.UiSlotState;
import com.schwarz.lenlorui.ui.UiText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;

import java.util.UUID;

/**
 * Modern RPG combat HUD: centred hotbar, vitals panel in the top-left corner.
 *
 * <p>Hotbar sits centred on the bottom edge; the vitals (level, clearance, XP rail, then blood,
 * hunger, armor + situational air/mount/jump rows) are pinned to the <b>top-left</b> corner and
 * grow downward with no background of their own, keeping the bottom-left lane open for vanilla
 * chat. The item-name pill sits centred above the hotbar. Rounded bars, damage ghosts, notches and
 * low-HP pulse give the modern RPG read at a glance. Vanilla survival overlays are cancelled by
 * {@code GuiHudMixin} and {@code RotasForgeHudEvents}.</p>
 *
 * <p>The block has no panel, so the thing that makes level, rank, blood and hunger read as one
 * unit is the clearance rank: its colour runs down a two-pixel spine on the leading edge and fills
 * the badge that carries the rank letter, and every bar - the XP rail included - is drawn by the
 * same {@link #bar(UiCanvas, int, int, int, int, int, float, float, int, boolean)} helper so the
 * rows share one shape and one sheen. Resource hues are held at one saturation and value so the
 * set reads as designed rather than assembled.</p>
 */
@Environment(EnvType.CLIENT)
public final class RotasHudRenderer {
    /*
     * Resource hues, all held at one saturation and value so the rows read as one instrument
     * instead of a grab bag: terracotta blood, honey hunger, muted steel armour, pale breath.
     * They are the theme's own ink weights (BAD/WARN) rather than neon, so the HUD and the
     * parchment screens still look like the same mod.
     */
    private static final int HEALTH = 0xFFE2695C;
    private static final int HEALTH_LOW = 0xFFFF5A4A;
    private static final int HEALTH_GHOST = 0xFFF5E6C8;
    private static final int ABSORPTION = 0xFFE8B45C;
    private static final int FOOD = 0xFFE0AC4C;
    private static final int SATURATION = 0xFFF2D48A;
    private static final int ARMOR = 0xFF8FA6B8;
    private static final int AIR = 0xFF86C4C0;
    private static final int MOUNT = 0xFF9CC08A;

    private static final int PANEL_RADIUS = 8;
    /** Corner radius of a vitals bar; the XP rail is thinner and rounds to half its height. */
    private static final int BAR_RADIUS = 3;
    private static final int XP_BAR_RADIUS = 2;
    private static final int PANEL_FILL = RotasTheme.HUD_PANEL;
    private static final int PANEL_EDGE = RotasTheme.HUD_PANEL_EDGE;
    private static final int PANEL_SHADOW = 0x802A2015;
    private static final int BAR_TRACK = RotasTheme.HUD_TRACK;
    private static final int BAR_NOTCH = 0x55000000;
    /**
     * Thin ink edge around a vitals bar. This is the one job the removed panel used to do for
     * the bars - keeping them legible over bright terrain - done per bar instead of with a slab.
     */
    private static final int BAR_INK_EDGE = 0x66000000;
    /** Displayed (smoothed) bar fractions; -1 forces a snap on the first frame. */
    private static UUID smoothOwner;
    private static float hpShown = -1f;
    private static float hpGhost = -1f;
    private static float foodShown = -1f;
    private static float airShown = -1f;
    private static float xpShown = -1f;
    private static float armorShown = -1f;

    /** Selected-slot name toast state; mirrors vanilla highlight timing without its fields. */
    private static ItemStack toastStack = ItemStack.EMPTY;
    private static int toastSlot = -1;
    private static float toastTicks;
    private static long toastLastGameTime = Long.MIN_VALUE;

    private static final String HUD_HP = L.t("rotasutils.hud.hp");
    private static final String HUD_FOOD = L.t("rotasutils.hud.food");
    private static final String HUD_ARMOR = L.t("rotasutils.hud.armor");
    private static final String HUD_AIR = L.t("rotasutils.hud.air");
    private static final String HUD_MOUNT = L.t("rotasutils.hud.mount");
    private static final String HUD_JUMP = L.t("rotasutils.hud.jump");

    private RotasHudRenderer() {
    }

    /**
     * Pins the HUD to {@link HudLayout#LOCKED_GUI_SCALE}: whatever GUI scale the player chose in
     * Options, the bars, slots and text always render at the same on-screen size. Multiply the
     * pose by {@code LOCKED_GUI_SCALE / guiScale} and draw in the {@link #lockedWidth} /
     * {@link #lockedHeight} grid, and one of our units is always three framebuffer pixels.
     *
     * <p>Must be paired with {@link #popHudScale(GuiGraphics)} — the caller wraps its whole draw
     * in the push/pop so nothing else on the HUD stack is affected.</p>
     */
    private static void pushHudScale(GuiGraphics graphics, Minecraft minecraft) {
        graphics.pose().pushPose();
        float scale = HudLayout.poseScale(minecraft.getWindow().getGuiScale(), hudScale(minecraft));
        if (scale != 1f) {
            graphics.pose().scale(scale, scale, 1f);
        }
    }

    private static void popHudScale(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    /** HUD scale picked from the window size, so the HUD keeps its share of any screen. */
    static int hudScale(Minecraft minecraft) {
        return HudLayout.hudScale(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }

    /** Screen width in the HUD grid, independent of the player's GUI scale. */
    private static int lockedWidth(Minecraft minecraft) {
        return HudLayout.gridSize(minecraft.getWindow().getWidth(), hudScale(minecraft));
    }

    /** Screen height in the HUD grid, independent of the player's GUI scale. */
    private static int lockedHeight(Minecraft minecraft) {
        return HudLayout.gridSize(minecraft.getWindow().getHeight(), hudScale(minecraft));
    }

    public static void renderHotbar(GuiGraphics graphics, Minecraft minecraft, float partialTick) {
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || minecraft.screen != null) {
            return;
        }

        // Layout in the locked grid (see pushHudScale) rather than the player's own GUI scale, so
        // the HUD keeps the same footprint on screen whatever scale is chosen.
        int screenWidth = lockedWidth(minecraft);
        int screenHeight = lockedHeight(minecraft);
        Font font = minecraft.font;
        boolean offhand = !player.getOffhandItem().isEmpty();
        HudLayout layout = HudLayout.compute(screenWidth, screenHeight,
                planVitals(player).rows(player), offhand);

        trackSelectedItemToast(minecraft, player);

        ItemStack offhandStack = player.getOffhandItem();
        int selected = player.getInventory().selected;
        float attack = clamp01(player.getAttackStrengthScale(partialTick));

        pushHudScale(graphics, minecraft);
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.HUD)) {
            hudPanel(ui, layout.hotbarX(), layout.hotbarY(), layout.hotbarWidth(), layout.hotbarHeight());
            if (offhand) {
                hudPanel(ui, layout.offhandX(), layout.hotbarY(),
                        layout.offhandSize(), layout.offhandSize());
            }

            for (int i = 0; i < 9; i++) {
                int sx = layout.slotX(i);
                boolean isSelected = i == selected;
                if (isSelected) {
                    ui.outerGlow(sx, layout.hotbarY() + 2, layout.slotSize(), layout.slotSize(),
                            4, UiColor.multiplyAlpha(RotasTheme.HUD_ACCENT, 0.45f), 2);
                }
                ui.slot(new UiRect(sx, layout.hotbarY() + 2, layout.slotSize(), layout.slotSize()),
                        isSelected ? UiSlotState.SELECTED
                                : player.getInventory().getItem(i).isEmpty()
                                        ? UiSlotState.EMPTY : UiSlotState.IDLE);
                if (isSelected) {
                    int w = Math.max(2, Math.round((layout.slotSize() - 4) * attack));
                    ui.roundedRect(sx + 2, layout.hotbarY() + layout.slotSize(), w, 2, 1,
                            attack >= 0.999f ? RotasTheme.HUD_ACCENT
                                    : UiColor.multiplyAlpha(RotasTheme.HUD_ACCENT, 0.55f));
                }
            }
            if (offhand) {
                ui.slot(new UiRect(layout.offhandX() + 2, layout.hotbarY() + 2,
                        layout.slotSize(), layout.slotSize()), offhandStack.isEmpty()
                        ? UiSlotState.EMPTY : UiSlotState.IDLE);
            }

            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    int sx = layout.slotX(i);
                    int itemInset = Math.max(0, (layout.slotSize() - 16) / 2);
                    ui.item(player, stack, sx + itemInset, layout.hotbarY() + 3, i + 1);
                    ui.itemDecorations(font, stack, sx + itemInset, layout.hotbarY() + 3);
                }
            }
            if (offhand) {
                int itemInset = Math.max(0, (layout.slotSize() - 16) / 2);
                ui.item(player, offhandStack, layout.offhandX() + 2 + itemInset,
                        layout.hotbarY() + 3, 31);
                ui.itemDecorations(font, offhandStack, layout.offhandX() + 2 + itemInset,
                        layout.hotbarY() + 3);
            }

            renderSelectedItemToast(ui, font, layout);
        }
        // The tracker hangs off the hotbar pass rather than the vitals pass on purpose:
        // vanilla only calls the health hook when the game mode can hurt the player, so a
        // creative-mode builder would otherwise lose the panel. It draws inside the locked
        // pose too, so the tracker scales with the rest of the HUD.
        QuestTrackerHud.render(graphics, minecraft);
        ZoneHud.render(graphics, minecraft, lockedWidth(minecraft), lockedHeight(minecraft));
        popHudScale(graphics);
    }

    public static void renderVitals(GuiGraphics graphics, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || minecraft.screen != null
                || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }

        int screenWidth = lockedWidth(minecraft);
        int screenHeight = lockedHeight(minecraft);
        Font font = minecraft.font;
        PlayerProgress progress = ClientState.progress();
        Vitals vitals = planVitals(player);
        HudLayout layout = HudLayout.compute(screenWidth, screenHeight, vitals.rows(player),
                !player.getOffhandItem().isEmpty());

        if (smoothOwner == null || !smoothOwner.equals(player.getUUID())) {
            smoothOwner = player.getUUID();
            hpShown = -1f;
            hpGhost = -1f;
            foodShown = -1f;
            airShown = -1f;
            xpShown = -1f;
            armorShown = -1f;
        }

        float maxHealth = Math.max(1f, player.getMaxHealth());
        float health = Math.max(0f, player.getHealth());
        float hpTarget = clamp01(health / maxHealth);
        hpShown = approach(hpShown, hpTarget, 0.45f);
        hpGhost = approach(hpGhost, hpTarget, 0.08f);
        if (hpGhost < hpShown) {
            hpGhost = hpShown;
        }
        float absorption = Math.max(0f, player.getAbsorptionAmount());
        int food = player.getFoodData().getFoodLevel();
        foodShown = approach(foodShown, clamp01(food / 20f), 0.45f);
        float saturation = clamp01(player.getFoodData().getSaturationLevel() / 20f);

        long needed = ClientState.xpForNextLevel();
        float xpTarget = needed <= 0 || needed == Long.MAX_VALUE
                ? 1f : clamp01((float) (progress.xp() / (double) needed));
        xpShown = approach(xpShown, xpTarget, 0.3f);

        pushHudScale(graphics, minecraft);
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.HUD)) {
            // No background: the vitals float straight over the world, so bright sky or a dark cave
            // never gets a slab of UI pasted over it. Every line carries its own drop shadow and
            // each bar carries its own thin track, which is all the framing needed. What groups
            // level, rank, blood and hunger into one block instead is the clearance colour: it
            // runs down the leading-edge spine and fills the badge on the header row.
            DangerRank rank = progress.highestClearance();
            rankSpine(ui, layout, rank);

            int headerY = layout.vitalsY() + HudLayout.HEADER_Y;
            String levelText = L.t("rotasutils.hud.level", progress.level());
            shadowText(ui, font, levelText, layout.labelX(), headerY, RotasTheme.HUD_ACCENT);
            // The rank letter sits in a plate tinted with the rank's own colour - the same colour
            // the quest tracker puts on its leading edge - and the spelled-out word follows it, so
            // the letter reads as a value rather than a stray glyph. On a narrow panel the word
            // gives way first and the bare plate only if even that would overlap the level.
            int badgeW = HudLayout.rankBadgeWidth(font.width(rank.display()));
            String rankWord = L.t("rotasutils.hud.rank");
            int wordW = rankWord.isEmpty() ? 0 : font.width(rankWord) + 4;
            int levelW = font.width(levelText);
            boolean withWord = wordW > 0 && layout.headerFits(levelW, badgeW + wordW);
            if (layout.headerFits(levelW, withWord ? badgeW + wordW : badgeW)) {
                // The word reads before the letter ("clearance F"), and the badge keeps the edge.
                int badgeX = layout.valueX() - badgeW;
                if (withWord) {
                    shadowText(ui, font, rankWord, badgeX - wordW, headerY, RotasTheme.HUD_TEXT);
                }
                rankBadge(ui, font, rank, badgeX, layout.rankBadgeY(), badgeW);
            }
            // XP rail under the header ends where the bars below end, and its value column shows how
            // far into the level the player is, so an empty rail no longer reads as a missing bar.
            int railW = Math.max(8, layout.barX() + layout.barWidth() - layout.labelX());
            bar(ui, layout.labelX(), layout.vitalsY() + HudLayout.XP_BAR_Y, railW,
                    HudLayout.XP_BAR_H, XP_BAR_RADIUS, clamp01(xpShown), -1f,
                    RotasTheme.HUD_ACCENT, false);
            String xpText = needed <= 0 || needed == Long.MAX_VALUE ? "MAX" : (int) (xpTarget * 100f) + "%";
            String fittedXp = HudValueText.fit(xpText, font::width, layout.valueWidth());
            shadowText(ui, font, fittedXp, layout.valueX() - font.width(fittedXp),
                    layout.vitalsY() + HudLayout.XP_BAR_Y - 2, RotasTheme.HUD_ACCENT);

            boolean low = hpTarget <= 0.25f;
            int pulse = low ? pulseAlpha() : 0xFF;
            int hpColor = low ? HEALTH_LOW : HEALTH;
            String hpValue = Math.round(health) + "/" + Math.round(maxHealth);
            int rowTop = layout.firstRowY();
            int barW = rpgRow(ui, font, layout, rowTop, HUD_HP, hpShown, hpGhost, hpValue,
                    UiColor.alpha(hpColor, pulse), RotasTheme.HUD_TEXT, true);
            if (absorption > 0f) {
                subLine(ui, layout.barX(), rowTop, barW, absorption / maxHealth, ABSORPTION);
            }

            rowTop += HudLayout.ROW_PITCH;
            barW = rpgRow(ui, font, layout, rowTop, HUD_FOOD, foodShown, -1f, food + "/20",
                    FOOD, RotasTheme.HUD_TEXT, true);
            if (saturation > 0.02f) {
                subLine(ui, layout.barX(), rowTop, barW, saturation, SATURATION);
            }

            int armor = player.getArmorValue();
            if (armor > 0) {
                rowTop += HudLayout.ROW_PITCH;
                armorShown = approach(armorShown, clamp01(armor / 20f), 0.45f);
                rpgRow(ui, font, layout, rowTop, HUD_ARMOR, armorShown, -1f,
                        armor + "/20", ARMOR, RotasTheme.HUD_TEXT, false);
            } else {
                armorShown = -1f;
            }

            if (vitals.air()) {
                rowTop += HudLayout.ROW_PITCH;
                int airSupply = Math.max(0, player.getAirSupply());
                int maxAir = Math.max(1, player.getMaxAirSupply());
                float airFraction = clamp01(airSupply / (float) maxAir);
                airShown = approach(airShown, airFraction, 0.45f);
                rpgRow(ui, font, layout, rowTop, HUD_AIR, airShown, -1f,
                        UiFormat.formatPercent(airFraction), AIR, RotasTheme.HUD_TEXT, false);
            }

            if (vitals.mount() != null) {
                rowTop += HudLayout.ROW_PITCH;
                LivingEntity mount = vitals.mount();
                float mountHealth = Math.max(0f, mount.getHealth());
                float mountMax = Math.max(1f, mount.getMaxHealth());
                rpgRow(ui, font, layout, rowTop, HUD_MOUNT, clamp01(mountHealth / mountMax), -1f,
                        Math.round(mountHealth) + "/" + Math.round(mountMax),
                        MOUNT, RotasTheme.HUD_TEXT, true);
            }

            if (vitals.jump() != null) {
                rowTop += HudLayout.ROW_PITCH;
                float jumpScale = clamp01(player.getJumpRidingScale());
                boolean cooling = vitals.jump().getJumpCooldown() > 0;
                rpgRow(ui, font, layout, rowTop, HUD_JUMP, jumpScale, -1f,
                        UiFormat.formatPercent(jumpScale),
                        cooling ? RotasTheme.HUD_TEXT_MUTED : RotasTheme.HUD_ACCENT,
                        RotasTheme.HUD_TEXT, false);
            }
        }
        popHudScale(graphics);
    }

    /**
     * Draws once from Architectury's end-of-HUD event. It must not hang off the vitals renderer:
     * Forge can reach that renderer through both its overlay fallback and the vanilla GUI mixin.
     */
    public static void renderMonsterTarget(GuiGraphics graphics, Minecraft minecraft) {
        TargetFrameHud.render(graphics, minecraft, ClientState.progress().level());
    }

    /** Which situational rows the vitals panel needs right now. */
    private record Vitals(boolean air, LivingEntity mount, PlayerRideableJumping jump) {
        int rows(LocalPlayer player) {
            return HudLayout.BASE_ROWS + (player.getArmorValue() > 0 ? 1 : 0)
                    + (air ? 1 : 0) + (mount != null ? 1 : 0) + (jump != null ? 1 : 0);
        }
    }

    private static Vitals planVitals(LocalPlayer player) {
        boolean air = player.getAirSupply() < player.getMaxAirSupply();
        LivingEntity mount = player.getVehicle() instanceof LivingEntity living ? living : null;
        return new Vitals(air, mount, player.jumpableVehicle());
    }

    private static void hudPanel(UiCanvas ui, int x, int y, int width, int height) {
        ui.shadow(x, y, width, height, PANEL_RADIUS, PANEL_SHADOW);
        ui.borderedRoundedRect(x, y, width, height, PANEL_RADIUS, PANEL_EDGE, PANEL_FILL);
        // Inner top highlight for a clean modern edge.
        ui.roundedRect(x + 6, y + 1, Math.max(1, width - 12), 1, 1, 0x22FFFFFF);
    }

    /**
     * One rounded resource bar: recessed track, ink edge, damage ghost, gradient fill, gloss and
     * notches. Every bar in the vitals block goes through here - the XP rail as well as the blood,
     * hunger and situational rows - so they share one shape, one edge and one sheen and read as
     * parts of a single instrument rather than a line plus some bars.
     */
    private static void bar(UiCanvas ui, int x, int y, int width, int height, int radius,
                            float shown, float ghost, int fillColor, boolean notches) {
        // Ink edge for contrast on bright terrain, then a recessed track so how much is
        // missing stays visible without a background slab.
        ui.roundedRect(x - 1, y - 1, width + 2, height + 2, radius + 1, BAR_INK_EDGE);
        ui.roundedRect(x, y, width, height, radius, UiColor.multiplyAlpha(BAR_TRACK, 0.55f));
        // Damage ghost (white trailing bar) then live fill.
        if (ghost >= 0f && ghost > shown + 0.005f) {
            int ghostW = Math.round(width * clamp01(ghost));
            if (ghostW > 0) {
                ui.roundedRect(x, y, ghostW, height, radius,
                        UiColor.multiplyAlpha(HEALTH_GHOST, 0.55f));
            }
        }
        int filled = Math.round(width * clamp01(shown));
        if (filled > 0) {
            ui.gradientRoundedRect(x, y, filled, height, radius,
                    UiColor.lighten(fillColor, 0.14f), UiColor.darken(fillColor, 0.06f));
            // Gloss line.
            ui.roundedRect(x + 1, y + 1, Math.max(1, filled - 2), 1, 1, 0x55FFFFFF);
        }
        if (notches) {
            for (int i = 1; i <= 4; i++) {
                int notchX = x + Math.round(width * i / 5f);
                if (notchX > x + 3 && notchX < x + width - 3) {
                    ui.rect(notchX, y + 1, 1, height - 2, BAR_NOTCH);
                }
            }
        }
    }

    /**
     * The clearance colour as a two-pixel spine down the leading edge of the vitals block. It is
     * the one thing that groups level, rank, blood and hunger without a panel behind them, and it
     * repeats the rank stripe the quest tracker already puts on its own leading edge.
     */
    private static void rankSpine(UiCanvas ui, HudLayout layout, DangerRank rank) {
        int top = layout.rankBadgeY();
        int height = Math.max(HudLayout.RANK_BADGE_H, layout.rowsBottom() - top);
        ui.roundedRect(layout.spineX(), top, HudLayout.SPINE_W, height, 1, BAR_INK_EDGE);
        ui.roundedRect(layout.spineX(), top, HudLayout.SPINE_W, height, 1,
                UiColor.multiplyAlpha(rank.argb(), 0.6f));
    }

    /**
     * The rank letter in a plate tinted with the rank's own colour. A bare letter floating on the
     * header meant nothing; the plate makes it a value and the tint ties it to the spine, the
     * quest tracker and the board, which all use the same rank colour.
     */
    private static void rankBadge(UiCanvas ui, Font font, DangerRank rank, int x, int y, int width) {
        int color = rank.argb();
        ui.roundedRect(x, y, width, HudLayout.RANK_BADGE_H, 2, BAR_INK_EDGE);
        ui.roundedRect(x, y, width, HudLayout.RANK_BADGE_H, 2,
                UiColor.multiplyAlpha(color, 0.32f));
        // Dark ranks (SSS purple) would vanish against dark terrain, so the letter is lifted
        // toward the HUD's cream before it is drawn.
        int letter = UiColor.isLight(color) ? color : UiColor.mix(color, RotasTheme.HUD_TEXT, 0.5f);
        String text = rank.display();
        shadowText(ui, font, text, x + (width - font.width(text)) / 2,
                y + 2, letter);
    }

    /**
     * One modern RPG row: icon dot + label, rounded bar with damage ghost, right-aligned value.
     * Returns the bar width for sub-lines.
     */
    private static int rpgRow(UiCanvas ui, Font font, HudLayout layout, int rowTop,
                              String label, float shown, float ghost, String value,
                              int fillColor, int valueColor, boolean notches) {
        // Icon dot: no backing plate, so the row reads as floating text plus a bar. The dot gets
        // the same ink ring the bars carry, which is what keeps it visible over bright terrain.
        ui.flush();
        int dotCx = layout.labelX() + 4;
        int dotCy = rowTop + 4;
        ui.circle(dotCx, dotCy, 3f, 12, BAR_INK_EDGE);
        ui.circle(dotCx, dotCy, 2f, 12, fillColor);
        // Measured against the column so a long localised label can never run under its own bar.
        shadowText(ui, font, HudValueText.fit(label, font::width, layout.labelWidth() - 9),
                layout.labelX() + 9, rowTop, fillColor);
        int barW = layout.barWidth();
        bar(ui, layout.barX(), rowTop + 1, barW, HudLayout.BAR_H, BAR_RADIUS,
                shown, ghost, fillColor, notches);
        // Kept inside its reserved column so every row's bar can stay the same width.
        String fitted = HudValueText.fit(value, font::width, layout.valueWidth());
        shadowText(ui, font, fitted, layout.valueX() - font.width(fitted), rowTop, valueColor);
        return barW;
    }

    /**
     * Secondary resource (absorption, saturation) as a one-pixel line just under its bar. Drawn inside
     * the bar it looked like a rendering glitch in the fill.
     */
    private static void subLine(UiCanvas ui, int barX, int rowTop, int barW, float fraction, int color) {
        int w = Math.min(barW, Math.round(barW * clamp01(fraction)));
        if (w >= 2) {
            ui.rect(barX, rowTop + HudLayout.BAR_H + 3, w, 1, UiColor.multiplyAlpha(color, 0.9f));
        }
    }

    /** Text with a drop shadow so it survives bright terrain. */
    private static void shadowText(UiCanvas ui, Font font, String text, int x, int y, int color) {
        ui.flush();
        ui.graphics().drawString(font, text, x, y, color, true);
    }

    /** Fading dark RPG pill above the stack that names the freshly selected item. */
    private static void renderSelectedItemToast(UiCanvas ui, Font font, HudLayout layout) {
        if (toastTicks <= 0f || toastStack.isEmpty()) {
            return;
        }
        int fade = Math.min(255, (int) (toastTicks * 256f / 10f));
        if (fade <= 8) {
            return;
        }
        String count = toastStack.getCount() > 1 ? "x" + UiFormat.formatCount(toastStack.getCount()) : "";
        int countW = count.isEmpty() ? 0 : font.width(count) + 4;
        int maxNameW = Math.max(24, layout.hotbarWidth() - 16 - countW);
        String name = UiText.ellipsize(font, toastStack.getHoverName().getString(), maxNameW);
        int nameW = font.width(name);
        int pillW = 16 + nameW + countW;
        int pillX = Math.max(2, layout.toastCenterX() - pillW / 2);
        int pillY = layout.toastY();
        ui.borderedRoundedRect(pillX, pillY, pillW, HudLayout.TOAST_H, 8,
                UiColor.multiplyAlpha(PANEL_EDGE, fade / 255f),
                UiColor.multiplyAlpha(PANEL_FILL, fade / 255f));
        ui.flush();
        GuiGraphics graphics = ui.graphics();
        int nameColor = 0xFFF5E6C8;
        try {
            Integer rarity = toastStack.getRarity().color.getColor();
            if (rarity != null) {
                nameColor = 0xFF000000 | rarity;
                if (UiColor.isLight(nameColor)) {
                    nameColor = UiColor.mix(nameColor, 0xFFFFC25E, 0.35f);
                }
            }
        } catch (Exception ignored) {
            // Rarity lookup must never break the HUD.
        }
        graphics.drawString(font, name, pillX + 8, pillY + 4,
                (fade << 24) | (nameColor & 0xFFFFFF), true);
        if (!count.isEmpty()) {
            graphics.drawString(font, count, pillX + 8 + nameW + 4, pillY + 4,
                    (fade << 24) | (RotasTheme.HUD_TEXT_MUTED & 0xFFFFFF), true);
        }
    }

    /** Restarts the fade timer whenever the selected slot or its stack changes. */
    private static void trackSelectedItemToast(Minecraft minecraft, LocalPlayer player) {
        int slot = player.getInventory().selected;
        ItemStack stack = player.getInventory().getSelected();
        if (slot != toastSlot || !ItemStack.matches(stack, toastStack)) {
            toastSlot = slot;
            toastStack = stack.copy();
            toastTicks = 40f * minecraft.options.notificationDisplayTime().get().floatValue();
        }
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        if (toastLastGameTime != Long.MIN_VALUE) {
            toastTicks = Math.max(0f, toastTicks - Math.max(0L, now - toastLastGameTime));
        }
        toastLastGameTime = now;
    }

    /** Exponential smoothing with a snap threshold so big hits still land instantly. */
    private static float approach(float shown, float target, float speed) {
        if (shown < 0f || Math.abs(target - shown) > 0.5f) {
            return target;
        }
        return shown + (target - shown) * speed;
    }

    private static int pulseAlpha() {
        float phase = (System.currentTimeMillis() % 1200L) / 1200f;
        float wave = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
        return Math.round(255f * (0.72f + 0.28f * wave));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
