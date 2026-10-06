package net.schwarz.rotasutils.board;

public enum BoardStyle {
    WOODEN_VILLAGE("Wooden Village Board", 0xFF8B6A3F, 0xFFD9B679, false),
    MEDIEVAL_GUILD("Medieval Guild Board", 0xFF5C3A21, 0xFFE0C07A, false),
    MODERN_NOTICE("Modern Notice Board", 0xFF3A3A3A, 0xFFDDDDDD, false),
    MILITARY_TERMINAL("Military Mission Terminal", 0xFF23301F, 0xFF7FE07F, true),
    CYBERPUNK_TERMINAL("Cyberpunk Holographic Terminal", 0xFF10121F, 0xFF3BE8FF, true),
    MAGICAL_FLOATING("Magical Floating Board", 0xFF1A1030, 0xFFC08BFF, true),
    FACTION("Faction Board", 0xFF2A1A1A, 0xFFFF7755, false),
    CUSTOM("Custom Resource Pack Style", 0xFF202020, 0xFFFFFFFF, false);

    public static final BoardStyle[] VALUES = values();

    private final String display;
    private final int frameColor;
    private final int accentColor;
    private final boolean animated;

    BoardStyle(String display, int frameColor, int accentColor, boolean animated) {
        this.display = display;
        this.frameColor = frameColor;
        this.accentColor = accentColor;
        this.animated = animated;
    }

    public String display() {
        return net.schwarz.rotasutils.util.ThaiText.label("board_style", this, display);
    }

    public int frameColor() {
        return frameColor;
    }

    public int accentColor() {
        return accentColor;
    }

    public boolean animated() {
        return animated;
    }
}
