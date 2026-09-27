package net.schwarz.rotasutils.client.screen;

/**
 * Pure GUI-scale arithmetic for the Rotas screens and the Character Hub, kept free of Minecraft
 * types so it can be unit-tested without a client.
 *
 * <p>Those screens are laid out for a GUI-scaled canvas of roughly {@link #MIN_WIDTH} x
 * {@link #MIN_HEIGHT}. A player's own GUI scale decides the canvas, so the same scale looks fine on
 * a 1440p monitor (scale 3 = 854x480) but squeezes everything on a 1080p one (scale 3 = 640x360,
 * auto scale 4 = 480x270). While a managed screen is open the scale is lowered just far enough for
 * the canvas to reach the design size; it is never raised, so a player who picked a smaller scale
 * keeps it.</p>
 */
public final class ScreenScale {
    /** Smallest GUI-scaled canvas the console windows and the Character Hub are designed for. */
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 400;
    /** Windows at least this tall never drop below scale 2, so laptop screens keep readable text. */
    public static final int LEGIBLE_MIN_HEIGHT = 720;
    /**
     * Smallest canvas the Character Hub still lays out well (its card and medallion adapt down to
     * this). Used only to avoid scale 1 on small or windowed monitors.
     */
    public static final int HUB_MIN_WIDTH = 500;
    public static final int HUB_MIN_HEIGHT = 320;

    private ScreenScale() {
    }

    /**
     * GUI scale to use for a managed screen: the player's scale, lowered until the canvas
     * {@code ceil(framebuffer / scale)} is at least {@link #MIN_WIDTH} x {@link #MIN_HEIGHT}.
     * A window of {@link #LEGIBLE_MIN_HEIGHT} or taller keeps at least scale 2.
     */
    public static int effectiveScale(int playerScale, int framebufferWidth, int framebufferHeight) {
        int scale = Math.max(1, playerScale);
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return scale;
        }
        int floor = Math.min(scale, framebufferHeight >= LEGIBLE_MIN_HEIGHT ? 2 : 1);
        while (scale > floor && !fits(scale, framebufferWidth, framebufferHeight)) {
            scale--;
        }
        return scale;
    }

    /**
     * GUI scale for the Character Hub. Same as {@link #effectiveScale}, except that where that policy
     * would fall to scale 1 (a windowed 1366x768 or 1280x720 laptop, a 1024x768 monitor) the hub keeps
     * scale 2 as long as its smaller canvas still fits, so it is not rendered at half size.
     */
    public static int hubScale(int playerScale, int framebufferWidth, int framebufferHeight) {
        int preferred = effectiveScale(playerScale, framebufferWidth, framebufferHeight);
        if (preferred >= 2 || playerScale < 2) {
            return preferred;
        }
        return fits(2, framebufferWidth, framebufferHeight, HUB_MIN_WIDTH, HUB_MIN_HEIGHT) ? 2 : preferred;
    }

    /** True when the canvas at {@code scale} reaches the design size, using vanilla's ceiling division. */
    static boolean fits(int scale, int framebufferWidth, int framebufferHeight) {
        return fits(scale, framebufferWidth, framebufferHeight, MIN_WIDTH, MIN_HEIGHT);
    }

    static boolean fits(int scale, int framebufferWidth, int framebufferHeight, int minWidth, int minHeight) {
        return canvas(framebufferWidth, scale) >= minWidth && canvas(framebufferHeight, scale) >= minHeight;
    }

    /** GUI-scaled size for {@code pixels} at {@code scale}, like {@code Window#setGuiScale}. */
    public static int canvas(int pixels, int scale) {
        return (int) Math.ceil(Math.max(1, pixels) / (double) Math.max(1, scale));
    }
}
