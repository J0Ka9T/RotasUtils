package net.schwarz.rotasutils.client.screen;

import com.schwarz.lenlorui.ui.UiTheme;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Dark leather, copper selection and cream text for the pixel RPG interface. */
@Environment(EnvType.CLIENT)
public final class RotasTheme {
    /** Polished copper. Reserved for selection and the primary action. */
    public static final int ACCENT = 0xFFC5A175;
    public static final int ACCENT_STRONG = 0xFFD2B184;
    public static final int ACCENT_WASH = 0xFFD6C09A;

    public static final int PANEL = 0xFFF5E7D0;
    public static final int SURFACE = 0xFFF2E3CA;
    public static final int SURFACE_HIGH = 0xFFF8EDD9;
    public static final int PANEL_BORDER = 0xFFD0B594;
    public static final int SEPARATOR = 0xFFD8C2A2;

    public static final int CONTROL = 0xFFE8D5B7;
    public static final int CONTROL_HOVER = 0xFFF0DFC3;
    public static final int CONTROL_PRESSED = 0xFFDCC29D;
    public static final int CONTROL_DISABLED = 0xFFE4D5BF;

    public static final int TEXT = 0xFF51351F;
    public static final int TEXT_MUTED = 0xFF76583A;
    public static final int TEXT_FAINT = 0xFF9B8062;
    public static final int TRACK = 0xFFEEDDC2;

    public static final int GOOD = 0xFF718A68;
    public static final int WARN = 0xFFB48A4C;
    public static final int BAD = 0xFFA35F52;

    /** Corner cuts for window, card and control frames. */
    public static final int RADIUS_WINDOW = 1;
    public static final int RADIUS_CARD = 1;
    public static final int RADIUS_CONTROL = 1;
    public static final int SHADOW = 0x66000000;

    /* ---- HUD tokens (dark modern RPG panel) ------------------------------------------
     * Dark slate shell with gold trim so vitals stay legible over any terrain.
     */
    public static final int HUD_TEXT = 0xFFF5E6C8;
    public static final int HUD_TEXT_MUTED = 0xFFC9B895;
    /** Bright gold for level, XP and selection accents over dark. */
    public static final int HUD_ACCENT = 0xFFFFC25E;
    public static final int HUD_ACCENT_DIM = 0xFFC08A3C;
    public static final int HUD_PANEL = 0xE6161D29;
    public static final int HUD_PANEL_EDGE = 0xFF8A6220;
    public static final int HUD_PANEL_EDGE_HI = 0xFFFFC25E;
    /** Dark recessed track behind HUD bars. */
    public static final int HUD_TRACK = 0xFF2A3340;
    public static final int HUD_TRACK_EDGE = 0xFF0D1117;

    public static final UiTheme MAIN = new UiTheme(
            PANEL,
            PANEL_BORDER,
            CONTROL,
            CONTROL_HOVER,
            CONTROL_PRESSED,
            TEXT,
            ACCENT,
            RADIUS_CARD
    );

    public static final UiTheme COMPACT = new UiTheme(
            SURFACE,
            PANEL_BORDER,
            CONTROL,
            CONTROL_HOVER,
            CONTROL_PRESSED,
            TEXT,
            ACCENT,
            RADIUS_CONTROL
    );

    public static final UiTheme HUD = new UiTheme(
            HUD_PANEL,
            HUD_PANEL_EDGE,
            0xFF2A3340,
            0xFF354052,
            0xFF1A2230,
            HUD_TEXT,
            HUD_ACCENT,
            8
    );

    private RotasTheme() {
    }

    public static UiTheme accent(int argb) {
        return MAIN.withAccent(argb);
    }

    public static UiTheme compactAccent(int argb) {
        return COMPACT.withAccent(argb);
    }
}
