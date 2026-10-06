package net.schwarz.rotasutils.client.screen;

public final class ScreenScale {
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 400;
    public static final int LEGIBLE_MIN_HEIGHT = 720;
    public static final int HUB_MIN_WIDTH = 500;
    public static final int HUB_MIN_HEIGHT = 320;

    private ScreenScale() {
    }

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

    public static int hubScale(int playerScale, int framebufferWidth, int framebufferHeight) {
        int preferred = effectiveScale(playerScale, framebufferWidth, framebufferHeight);
        if (preferred >= 2 || playerScale < 2) {
            return preferred;
        }
        return fits(2, framebufferWidth, framebufferHeight, HUB_MIN_WIDTH, HUB_MIN_HEIGHT) ? 2 : preferred;
    }

    static boolean fits(int scale, int framebufferWidth, int framebufferHeight) {
        return fits(scale, framebufferWidth, framebufferHeight, MIN_WIDTH, MIN_HEIGHT);
    }

    static boolean fits(int scale, int framebufferWidth, int framebufferHeight, int minWidth, int minHeight) {
        return canvas(framebufferWidth, scale) >= minWidth && canvas(framebufferHeight, scale) >= minHeight;
    }

    public static int canvas(int pixels, int scale) {
        return (int) Math.ceil(Math.max(1, pixels) / (double) Math.max(1, scale));
    }
}
