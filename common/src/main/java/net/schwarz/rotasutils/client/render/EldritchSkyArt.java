package net.schwarz.rotasutils.client.render;

/** V4 Apotheosis art direction and performance budget in one reviewable place. */
public final class EldritchSkyArt {
    public static final float SKY_TAKEOVER = 0.998f;
    public static final float CLOUD_TAKEOVER = 1.0f;
    public static final float FOG_TAKEOVER = 0.982f;
    public static final float DAYLIGHT_TAKEOVER = 0.94f;
    public static final float ECLIPSED_DAYLIGHT = 0.28f;
    public static final float ZENITH_COVERAGE = 0.982f;
    public static final float HORIZON_COVERAGE = 0.925f;

    public static final float ZENITH_RED = 0.010f;
    public static final float ZENITH_GREEN = 0.008f;
    public static final float ZENITH_BLUE = 0.032f;
    public static final float HORIZON_RED = 0.056f;
    public static final float HORIZON_GREEN = 0.046f;
    public static final float HORIZON_BLUE = 0.086f;
    public static final float SKY_RED = 0.018f;
    public static final float SKY_GREEN = 0.014f;
    public static final float SKY_BLUE = 0.044f;
    public static final float CLOUD_RED = 0.092f;
    public static final float CLOUD_GREEN = 0.080f;
    public static final float CLOUD_BLUE = 0.126f;
    public static final float FOG_RED = 0.044f;
    public static final float FOG_GREEN = 0.038f;
    public static final float FOG_BLUE = 0.066f;

    public static final float VOID_RED = 0.0015f;
    public static final float VOID_GREEN = 0.0020f;
    public static final float VOID_BLUE = 0.0090f;
    public static final float DEEP_RED = 0.0005f;
    public static final float DEEP_GREEN = 0.0002f;
    public static final float DEEP_BLUE = 0.0020f;
    public static final float ROYAL_BLUE_RED = 0.055f;
    public static final float ROYAL_BLUE_GREEN = 0.145f;
    public static final float ROYAL_BLUE_BLUE = 0.920f;
    public static final float INDIGO_RED = 0.040f;
    public static final float INDIGO_GREEN = 0.032f;
    public static final float INDIGO_BLUE = 0.240f;
    public static final float VIOLET_RED = 0.300f;
    public static final float VIOLET_GREEN = 0.070f;
    public static final float VIOLET_BLUE = 0.720f;
    public static final float ELECTRIC_CYAN_RED = 0.050f;
    public static final float ELECTRIC_CYAN_GREEN = 0.820f;
    public static final float ELECTRIC_CYAN_BLUE = 1.000f;
    public static final float ICE_BLUE_RED = 0.720f;
    public static final float ICE_BLUE_GREEN = 0.930f;
    public static final float ICE_BLUE_BLUE = 1.000f;
    public static final float NEAR_WHITE_RED = 0.920f;
    public static final float NEAR_WHITE_GREEN = 0.975f;
    public static final float NEAR_WHITE_BLUE = 1.000f;
    public static final float BRUISE_RED = 0.055f;
    public static final float BRUISE_GREEN = 0.048f;
    public static final float BRUISE_BLUE = 0.180f;
    public static final float HOT_RED = ELECTRIC_CYAN_RED;
    public static final float HOT_GREEN = ELECTRIC_CYAN_GREEN;
    public static final float HOT_BLUE = ELECTRIC_CYAN_BLUE;
    public static final float RIM_RED = 0.075f;
    public static final float RIM_GREEN = 0.105f;
    public static final float RIM_BLUE = 0.440f;

    public static final float APERTURE_HALF_HEIGHT_DEG = 24.5f;
    public static final float APERTURE_HALF_WIDTH_DEG = 10.8f;
    public static final float APERTURE_SCALE_MIN = 0.22f;
    public static final float APERTURE_SCALE_MAX = 1.35f;
    public static final float BREATH_MIN = 0.965f;
    public static final float BREATH_MAX = 1.035f;
    public static final float BREATH_PERIOD_SECONDS = 38f;

    public static final float FOCAL_ELEVATION_MIN = 26f;
    public static final float FOCAL_ELEVATION_RANGE = 14f;
    public static final float DOME_RADIUS = 97.4f;
    public static final float STORM_RADIUS = 96.7f;
    public static final float PRESENCE_RADIUS = 95.8f;
    public static final float CROWN_RADIUS = 95.0f;
    public static final float SHOCKWAVE_RADIUS = 94.7f;
    public static final float FRACTURE_RADIUS = 94.3f;
    public static final float CORONA_RADIUS = 93.9f;
    public static final float PLATE_RADIUS = 93.5f;
    public static final float EDGE_RADIUS = 93.1f;
    public static final float VOID_RADIUS = 92.7f;
    public static final float VOID_DEEP_RADIUS = 91.0f;
    public static final float DEBRIS_RADIUS = 90.6f;

    public static final float FLOW_MIN_PERIOD_SECONDS = 18f;
    public static final float FLOW_MAX_PERIOD_SECONDS = 45f;
    public static final float NEBULA_MIN_PERIOD_SECONDS = 32f;
    public static final float NEBULA_MAX_PERIOD_SECONDS = 72f;

    public static final float STORM_MIN_PERIOD_SECONDS = 31f;
    public static final float STORM_MAX_PERIOD_SECONDS = 58f;
    public static final float CROWN_MIN_PERIOD_SECONDS = 28f;
    public static final float CROWN_MAX_PERIOD_SECONDS = 60f;
    public static final float PRESENCE_MIN_PERIOD_SECONDS = 18f;
    public static final float PRESENCE_MAX_PERIOD_SECONDS = 45f;
    public static final float PRESENCE_MIN_ALPHA = 0.18f;
    public static final float PRESENCE_MAX_ALPHA = 0.45f;

    public static final int DOME_AZIMUTH_SEGMENTS = 48;
    public static final int RING_SEGMENTS = 56;
    public static final int PRESENCE_SEGMENTS = 24;
    public static final int BODY_COLUMNS = 14;
    public static final int BODY_ROWS = 5;

    public static final int STORM_MASSES = 4;
    public static final int VOID_SHELLS = 4;
    public static final int FLOW_BANDS = 5;
    public static final int NEBULA_FILAMENTS = 6;
    public static final int CROWNS = 3;
    public static final int SHOCKWAVES = 3;
    public static final int PRESENCE_LIMBS = 4;
    public static final int MAX_CUSTOM_VERTICES = 35_000;

    private EldritchSkyArt() {
    }
}
