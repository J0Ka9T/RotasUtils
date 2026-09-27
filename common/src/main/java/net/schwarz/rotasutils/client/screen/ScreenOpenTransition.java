package net.schwarz.rotasutils.client.screen;

final class ScreenOpenTransition {
    static final long DURATION_MILLIS = 180L;
    private static final int START_ALPHA = 160;

    private ScreenOpenTransition() {
    }

    static int overlayAlpha(long elapsedMillis) {
        float time = Math.max(0.0f, Math.min(1.0f, elapsedMillis / (float) DURATION_MILLIS));
        float remaining = 1.0f - time;
        return Math.round(START_ALPHA * remaining * remaining * remaining);
    }
}
