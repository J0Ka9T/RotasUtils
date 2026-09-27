package net.schwarz.rotasutils.client.hud;

/**
 * Pure geometry for the Rotas modern RPG HUD, kept free of Minecraft types so the arithmetic can
 * be unit-tested without a client. All values are GUI-scaled pixels.
 *
 * <p>Classic MMO split: the vitals RPG panel (level, clearance, XP rail, HP, food, armor +
 * situational air/mount/jump rows) is pinned to the <b>top-left</b> corner and grows downward, the
 * hotbar is centred on the bottom edge where muscle memory expects it, and the item-name toast sits
 * centred above the hotbar. The quest tracker is painted on the top-right edge by
 * {@link QuestTrackerHud}. By moving the vitals to the top, the bottom-left lane stays free for
 * vanilla chat and the panel can never be obscured by it. The whole HUD is locked to
 * {@link #LOCKED_GUI_SCALE} so one HUD unit always renders the same size on screen. The vitals draw
 * without any background of their own; only the hotbar keeps its panel. What groups the block
 * instead is the clearance rank: its colour runs down a two-pixel spine on the leading edge and
 * fills the badge that carries the rank letter on the header row.</p>
 */
public record HudLayout(
        int hotbarX, int hotbarY, int hotbarWidth, int hotbarHeight, int slotSize, int slotGap,
        int offhandSize,
        int offhandX, int vitalsX, int vitalsY, int vitalsWidth, int vitalsHeight,
        int labelX, int labelWidth, int barX, int valueX, int valueWidth,
        int firstRowY, int rowsBottom, int toastY, int toastCenterX) {

    /** Screen-edge margin shared by the vitals panel, the hotbar's bottom gap and the toast. */
    public static final int MARGIN = 8;
    public static final int SLOT = 18;
    public static final int GAP = 1;
    /** 4px shell padding around the 9 slots: 9*18 + 8*1 + 8. */
    public static final int HOTBAR_WIDTH = 9 * SLOT + 8 * GAP + 8;
    /** 2px shell padding above and below the 18px slots. */
    public static final int HOTBAR_HEIGHT = 22;
    public static final int OFFHAND_GAP = 4;
    public static final int OFFHAND_SIZE = 22;
    /** Fixed vitals width so the top-left panel reads as one stable RPG unit. */
    public static final int VITALS_WIDTH = 172;
    /** Minimum vitals width so labels never crush on tiny GUIs. */
    public static final int VITALS_MIN_WIDTH = 132;
    /**
     * Label column wide enough for the longest label ("MOUNT" / "HUNGER") once the 9px inset
     * that clears the icon dot is counted. The old 32px value let "FOOD" run under its own bar.
     */
    public static final int LABEL_W = 46;
    public static final int BAR_H = 7;
    /** Reserved right column for the value text, wide enough for "100/100". */
    public static final int VALUE_W = 34;
    /** Gutter between a bar's right edge and the value column. */
    public static final int VALUE_GAP = 6;
    /** Vertical distance between two bar-row tops. */
    public static final int ROW_PITCH = 16;
    public static final int TOAST_H = 16;
    /** Header row: the level text on the left and the clearance badge on the right. */
    public static final int HEADER_Y = 6;
    public static final int HEADER_H = 12;
    /**
     * Clearance badge: the rank letter in a small plate on the header row. Padding is per side,
     * and the floor width keeps a one-letter rank from reading as a sliver.
     */
    public static final int RANK_BADGE_H = 11;
    public static final int RANK_BADGE_PAD = 4;
    public static final int RANK_BADGE_MIN = 12;
    /**
     * Identity spine on the leading edge of the vitals block, tinted with the clearance rank's
     * own colour. It is what groups level, rank, blood and hunger into one instrument without
     * putting a background slab behind them.
     */
    public static final int SPINE_W = 2;
    public static final int SPINE_INSET = 1;
    /** Smallest gap kept between the level text and the clearance text. */
    public static final int HEADER_GAP = 6;
    /** Rounded XP rail under the header. */
    public static final int XP_BAR_Y = 21;
    public static final int XP_BAR_H = 4;
    /** Top of the first bar row, below the header and the XP rail. */
    public static final int FIRST_ROW_Y = 33;
    /** HP and FOOD are always present; armor/air/mount/jump are situational additions. */
    public static final int BASE_ROWS = 2;
    /** Inner padding inside the vitals panel. */
    public static final int VITALS_PAD = 8;
    /**
     * The HUD is locked to this GUI scale: whatever the player picks in Options, the bars, slots
     * and text always come out the same size on screen. Three is the vanilla default-sized read
     * that still fits the top-left vitals block plus a centred hotbar on a 1080p window.
     */
    public static final int LOCKED_GUI_SCALE = 3;

    /**
     * Pose multiplier that renders the HUD as if the GUI scale were {@link #LOCKED_GUI_SCALE}.
     *
     * <p>Vanilla draws one GUI unit as {@code guiScale} framebuffer pixels, so scaling the pose by
     * {@code LOCKED_GUI_SCALE / guiScale} pins one of our units to {@code LOCKED_GUI_SCALE} pixels
     * regardless of the player's setting. Callers must draw in the {@link #lockedSize(int)} grid
     * for this to line up.</p>
     */
    public static float poseScale(double guiScale) {
        return poseScale(guiScale, LOCKED_GUI_SCALE);
    }

    /** Window the HUD was designed on: at 1920x1080 it renders at {@link #DESIGN_HUD_SCALE}. */
    public static final int REFERENCE_WIDTH = 1920;
    public static final int REFERENCE_HEIGHT = 1080;
    /** HUD scale on the reference window. Scale 3 read as oversized at 1080p. */
    public static final int DESIGN_HUD_SCALE = 2;
    public static final int MIN_HUD_SCALE = 1;
    public static final int MAX_HUD_SCALE = 6;
    /** Windows at least this tall never drop below scale 2, so laptop screens stay legible. */
    public static final int LEGIBLE_MIN_HEIGHT = 600;

    /**
     * HUD scale for a window of this framebuffer size, so the HUD takes a similar share of a laptop,
     * a 1080p monitor or a 4K screen: 1080p gets 2, 1440p 3 and 4K 4. Scale 2 is the floor for any
     * window at least {@link #LEGIBLE_MIN_HEIGHT} tall, so 720p and 768p laptops keep readable text.
     * The smaller of the two ratios wins, so an ultrawide window is sized by its height.
     */
    public static int hudScale(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return DESIGN_HUD_SCALE;
        }
        double share = Math.min(framebufferWidth / (double) REFERENCE_WIDTH,
                framebufferHeight / (double) REFERENCE_HEIGHT);
        int floor = framebufferHeight >= LEGIBLE_MIN_HEIGHT ? DESIGN_HUD_SCALE : MIN_HUD_SCALE;
        return Math.max(floor, Math.min(MAX_HUD_SCALE, (int) Math.round(DESIGN_HUD_SCALE * share)));
    }

    /** Pose multiplier that renders the HUD at {@code hudScale} whatever GUI scale the player chose. */
    public static float poseScale(double guiScale, int hudScale) {
        if (guiScale <= 0.0 || hudScale <= 0) {
            return 1f;
        }
        return (float) (hudScale / guiScale);
    }

    /** Screen size in the HUD grid for {@code hudScale}: {@code ceil(pixels / hudScale)}, like vanilla. */
    public static int gridSize(int pixels, int hudScale) {
        int safe = Math.max(1, pixels);
        return Math.max(1, (int) Math.ceil(safe / (double) Math.max(1, hudScale)));
    }

    /**
     * Screen size in the HUD's fixed pixel grid: framebuffer pixels divided by the locked scale.
     * Replaces {@code Window#getGuiScaledWidth/Height} so a player's own GUI scale never changes
     * where the HUD anchors. Rounded up to mirror vanilla's own {@code Window#setGuiScale}, which
     * computes {@code ceil(framebufferSize / guiScale)}.
     */
    public static int lockedSize(int pixels) {
        return gridSize(pixels, LOCKED_GUI_SCALE);
    }

    /**
     * Layout in the fixed HUD grid for the given screen dimensions, situation-row count and
     * offhand presence.
     *
     * <p>The vitals panel is pinned to the top-left corner ({@code vitalsX = vitalsY = MARGIN})
     * and rows grow downward. The panel height is clamped so no situation-row count can push the
     * panel off the bottom edge of the screen. The toast always sits above the centred hotbar.</p>
     */
    public static HudLayout compute(int screenWidth, int screenHeight, int rows, boolean offhand) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int slotSize = Math.min(SLOT, Math.max(12, (safeWidth - MARGIN * 2 - 8) / 9));
        int slotGap = slotSize >= SLOT ? GAP : 0;
        int hotbarWidth = 9 * slotSize + 8 * slotGap + 8;
        int hotbarHeight = slotSize + 4;
        int offhandSize = slotSize + 4;
        int boundedRows = Math.max(BASE_ROWS, rows);
        // Hotbar centred on the bottom edge where muscle memory expects it.
        int hotbarY = Math.max(0, safeHeight - MARGIN - hotbarHeight);
        int hotbarX = Math.max(0, (safeWidth - hotbarWidth) / 2);
        int offhandX = Math.max(0, hotbarX - OFFHAND_GAP - offhandSize);

        // Vitals pinned to the top-left corner: level, rank, XP, blood, hunger + situational rows.
        // Rows grow downward from the header, so live additions never shift what the player is
        // already reading; the panel is clamped so no row count can run it off the bottom edge.
        int vitalsWidth = Math.max(VITALS_MIN_WIDTH, Math.min(VITALS_WIDTH, safeWidth - MARGIN * 2));
        int labelWidth = Math.min(LABEL_W, Math.max(30, vitalsWidth / 3));
        int valueWidth = Math.min(VALUE_W, Math.max(26, vitalsWidth / 4));
        int vitalsX = MARGIN;
        int vitalsY = MARGIN;
        int fullHeight = FIRST_ROW_Y + ROW_PITCH * boundedRows + VITALS_PAD;
        int maxHeight = Math.max(FIRST_ROW_Y + ROW_PITCH * BASE_ROWS + VITALS_PAD,
                safeHeight - vitalsY - MARGIN);
        int vitalsHeight = Math.min(fullHeight, maxHeight);
        // Bottom of the last drawn row, so the rank spine ends with the bars instead of dangling
        // into the space reserved for rows that are not currently live.
        int rowsBottom = Math.min(
                vitalsY + FIRST_ROW_Y + (boundedRows - 1) * ROW_PITCH + BAR_H + 3,
                vitalsY + vitalsHeight - 1);

        // Toast sits centred above the hotbar; the vitals are at the top now, so it only has to
        // clear the hotbar's own block.
        int toastY = Math.max(0, hotbarY - 4 - TOAST_H);
        return new HudLayout(hotbarX, hotbarY, hotbarWidth, hotbarHeight, slotSize, slotGap, offhandSize,
                offhandX, vitalsX, vitalsY, vitalsWidth, vitalsHeight,
                vitalsX + VITALS_PAD, labelWidth, vitalsX + VITALS_PAD + labelWidth,
                vitalsX + vitalsWidth - VITALS_PAD, valueWidth,
                vitalsY + FIRST_ROW_Y, rowsBottom, toastY, hotbarX + hotbarWidth / 2);
    }

    /**
     * True when clearance text of {@code clearanceWidth} still fits beside level text of
     * {@code levelWidth}. On a narrow panel the clearance is dropped instead of overlapping the
     * level, which is the one value the player always needs to read.
     */
    public boolean headerFits(int levelWidth, int clearanceWidth) {
        return labelX + levelWidth + HEADER_GAP + clearanceWidth <= valueX;
    }

    /** Top of the clearance badge: the plate straddles the header text row. */
    public int rankBadgeY() {
        return vitalsY + HEADER_Y - 2;
    }

    /** Leading-edge x of the rank spine. */
    public int spineX() {
        return vitalsX + SPINE_INSET;
    }

    /** Width of the clearance badge plate for a rank glyph {@code letterWidth} wide. */
    public static int rankBadgeWidth(int letterWidth) {
        return Math.max(RANK_BADGE_MIN, letterWidth + RANK_BADGE_PAD * 2);
    }

    /** Left edge of hotbar slot {@code index} (0-8). */
    public int slotX(int index) {
        return hotbarX + 4 + index * (slotSize + slotGap);
    }

    /**
     * Fixed bar width shared by every vitals row. The value column is reserved up front,
     * so all bars start and end on the same pixels no matter how wide the current value
     * text is — a hurt player's "7/20" never shrinks the bar under a full "20/20" row.
     */
    public int barWidth() {
        return Math.max(24, valueX - VALUE_GAP - valueWidth - barX);
    }
}
