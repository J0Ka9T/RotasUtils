package net.schwarz.rotasutils.client.theme;

public final class SophisticatedTheme {
    public static final int DEFAULT_DARK_TEXT = 0x404040;
    public static final int ROTAS_TEXT = 0xEDDFC0;

    private SophisticatedTheme() {
    }

    public static int remapDefaultTextColor(int color) {
        return color == DEFAULT_DARK_TEXT ? ROTAS_TEXT : color;
    }
}
