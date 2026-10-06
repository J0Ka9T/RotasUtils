package net.schwarz.rotasutils.client.hud;

public record HudLayout(
        int hotbarX, int hotbarY, int hotbarWidth, int hotbarHeight, int slotSize, int slotGap,
        int offhandSize,
        int offhandX, int vitalsX, int vitalsY, int vitalsWidth, int vitalsHeight,
        int labelX, int labelWidth, int barX, int valueX, int valueWidth,
        int firstRowY, int rowsBottom, int toastY, int toastCenterX) {
    public static final int MARGIN = 8;
    public static final int SLOT = 18;
    public static final int GAP = 1;
    public static final int HOTBAR_WIDTH = 9 * SLOT + 8 * GAP + 8;
    public static final int HOTBAR_HEIGHT = 22;
    public static final int OFFHAND_GAP = 4;
    public static final int OFFHAND_SIZE = 22;
    public static final int VITALS_WIDTH = 172;
    public static final int VITALS_MIN_WIDTH = 132;
    public static final int LABEL_W = 46;
    public static final int BAR_H = 7;
    public static final int VALUE_W = 34;
    public static final int VALUE_GAP = 6;
    public static final int ROW_PITCH = 16;
    public static final int TOAST_H = 16;
    public static final int HEADER_Y = 6;
    public static final int HEADER_H = 12;
    public static final int RANK_BADGE_H = 11;
    public static final int RANK_BADGE_PAD = 4;
    public static final int RANK_BADGE_MIN = 12;
    public static final int SPINE_W = 2;
    public static final int SPINE_INSET = 1;
    public static final int HEADER_GAP = 6;
    public static final int XP_BAR_Y = 21;
    public static final int XP_BAR_H = 4;
    public static final int FIRST_ROW_Y = 33;
    public static final int BASE_ROWS = 2;
    public static final int VITALS_PAD = 8;
    public static final int LOCKED_GUI_SCALE = 3;

    public static float poseScale(double guiScale) {
        return poseScale(guiScale, LOCKED_GUI_SCALE);
    }

    public static final int REFERENCE_WIDTH = 1920;
    public static final int REFERENCE_HEIGHT = 1080;
    public static final int DESIGN_HUD_SCALE = 2;
    public static final int MIN_HUD_SCALE = 1;
    public static final int MAX_HUD_SCALE = 6;
    public static final int LEGIBLE_MIN_HEIGHT = 600;

    public static int hudScale(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return DESIGN_HUD_SCALE;
        }
        double share = Math.min(framebufferWidth / (double) REFERENCE_WIDTH,
                framebufferHeight / (double) REFERENCE_HEIGHT);
        int floor = framebufferHeight >= LEGIBLE_MIN_HEIGHT ? DESIGN_HUD_SCALE : MIN_HUD_SCALE;
        return Math.max(floor, Math.min(MAX_HUD_SCALE, (int) Math.round(DESIGN_HUD_SCALE * share)));
    }

    public static float poseScale(double guiScale, int hudScale) {
        if (guiScale <= 0.0 || hudScale <= 0) {
            return 1f;
        }
        return (float) (hudScale / guiScale);
    }

    public static int gridSize(int pixels, int hudScale) {
        int safe = Math.max(1, pixels);
        return Math.max(1, (int) Math.ceil(safe / (double) Math.max(1, hudScale)));
    }

    public static int lockedSize(int pixels) {
        return gridSize(pixels, LOCKED_GUI_SCALE);
    }

    public static HudLayout compute(int screenWidth, int screenHeight, int rows, boolean offhand) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int slotSize = Math.min(SLOT, Math.max(12, (safeWidth - MARGIN * 2 - 8) / 9));
        int slotGap = slotSize >= SLOT ? GAP : 0;
        int hotbarWidth = 9 * slotSize + 8 * slotGap + 8;
        int hotbarHeight = slotSize + 4;
        int offhandSize = slotSize + 4;
        int boundedRows = Math.max(BASE_ROWS, rows);
        int hotbarY = Math.max(0, safeHeight - MARGIN - hotbarHeight);
        int hotbarX = Math.max(0, (safeWidth - hotbarWidth) / 2);
        int offhandX = Math.max(0, hotbarX - OFFHAND_GAP - offhandSize);

        int vitalsWidth = Math.max(VITALS_MIN_WIDTH, Math.min(VITALS_WIDTH, safeWidth - MARGIN * 2));
        int labelWidth = Math.min(LABEL_W, Math.max(30, vitalsWidth / 3));
        int valueWidth = Math.min(VALUE_W, Math.max(26, vitalsWidth / 4));
        int vitalsX = MARGIN;
        int vitalsY = MARGIN;
        int fullHeight = FIRST_ROW_Y + ROW_PITCH * boundedRows + VITALS_PAD;
        int maxHeight = Math.max(FIRST_ROW_Y + ROW_PITCH * BASE_ROWS + VITALS_PAD,
                safeHeight - vitalsY - MARGIN);
        int vitalsHeight = Math.min(fullHeight, maxHeight);
        int rowsBottom = Math.min(
                vitalsY + FIRST_ROW_Y + (boundedRows - 1) * ROW_PITCH + BAR_H + 3,
                vitalsY + vitalsHeight - 1);

        int toastY = Math.max(0, hotbarY - 4 - TOAST_H);
        return new HudLayout(hotbarX, hotbarY, hotbarWidth, hotbarHeight, slotSize, slotGap, offhandSize,
                offhandX, vitalsX, vitalsY, vitalsWidth, vitalsHeight,
                vitalsX + VITALS_PAD, labelWidth, vitalsX + VITALS_PAD + labelWidth,
                vitalsX + vitalsWidth - VITALS_PAD, valueWidth,
                vitalsY + FIRST_ROW_Y, rowsBottom, toastY, hotbarX + hotbarWidth / 2);
    }

    public boolean headerFits(int levelWidth, int clearanceWidth) {
        return labelX + levelWidth + HEADER_GAP + clearanceWidth <= valueX;
    }

    public int rankBadgeY() {
        return vitalsY + HEADER_Y - 2;
    }

    public int spineX() {
        return vitalsX + SPINE_INSET;
    }

    public static int rankBadgeWidth(int letterWidth) {
        return Math.max(RANK_BADGE_MIN, letterWidth + RANK_BADGE_PAD * 2);
    }

    public int slotX(int index) {
        return hotbarX + 4 + index * (slotSize + slotGap);
    }

    public int barWidth() {
        return Math.max(24, valueX - VALUE_GAP - valueWidth - barX);
    }
}
