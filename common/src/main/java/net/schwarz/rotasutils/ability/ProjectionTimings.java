package net.schwarz.rotasutils.ability;

public final class ProjectionTimings {
    private ProjectionTimings() {
    }

    public static final double FPS = 24.0;

    public static final double CLICK_1 = 0.5;
    public static final double CLICK_2 = 0.9;
    public static final double CALC = 1.3;
    public static final double CELL_STEP = 0.05;
    public static final double VANISH = 2.62;
    public static final double DASH = 2.78;
    public static final double TOUCH = 3.10;
    public static final double FAR = 3.40;
    public static final double STUDY = 3.3;
    public static final double BEHIND = 4.4;
    public static final double GONE = 5.0;
    public static final double REVEAL = 5.18;
    public static final double CONTACT = 5.5;
    public static final double SHATTER = 6.4;
    public static final double PUNCH = 6.45;
    public static final double BOOM = 6.57;

    public static final double UNDER = 6.90;
    public static final double OVER = 7.45;
    public static final double SIDE = 7.85;
    public static final double TRAP = 8.15;

    public static final double[] BEATS = new double[24];

    static {
        double t = 8.45;
        for (int i = 0; i <= 20; i++) {
            BEATS[i] = t;
            t += 0.22 - 0.0033 * i;
        }
        BEATS[21] = BEATS[20] + 0.22;
        BEATS[22] = BEATS[21] + 0.30;
        BEATS[23] = BEATS[22] + 0.40;
    }

    public static final double BREAK = BEATS[23];

    public static final double CRASH_1 = BREAK + 0.40;
    public static final double KICK_1 = CRASH_1 + 0.20;
    public static final double CRASH_2 = KICK_1 + 0.40;
    public static final double KICK_2 = CRASH_2 + 0.20;
    public static final double CRASH_3 = KICK_2 + 0.40;
    public static final double REST = CRASH_3 + 0.90;

    public static final double STANCE = REST + 0.3;
    public static final double[] PASSES = {STANCE + 1.5, STANCE + 2.2, STANCE + 2.8, STANCE + 3.3};
    public static final double PASS_TIME = 0.21;
    public static final double LAPS = STANCE + 3.7;
    public static final double LAPS_END = LAPS + 2.7;

    public static final double CHARGE = LAPS_END + 0.35;
    public static final double TOUCH_2 = CHARGE + 0.21;
    public static final double RING = TOUCH_2 + 0.85;
    public static final double RING_STEP = 0.055;
    public static final double COLLAPSE = RING + 24 * RING_STEP + 0.12;
    public static final double BLACK = COLLAPSE + 0.15;
    public static final double BLACK_END = BLACK + 0.10;

    public static final double REVEAL_2 = BLACK_END + 0.5;
    public static final double FOOT = REVEAL_2 + 0.3;
    public static final double RISE = FOOT + 0.5;
    public static final double HOLD = RISE + 1.2;
    public static final double STRIKE = HOLD + 0.4;
    public static final int SHOWS = 4;
    public static final double SHOW_TIME = 0.34;
    public static final double STRIKE_SPAN = 0.18;
    public static final double STRIKE_LANDS = 0.125;
    public static final double FINAL_HIT = STRIKE + SHOW_TIME * (SHOWS - 1) + SHOW_TIME * STRIKE_LANDS / STRIKE_SPAN;
    public static final double BOOM_2 = FINAL_HIT + 0.22;

    public static final double DRIFT = FINAL_HIT + 0.55;
    public static final double WALK = DRIFT + 0.4;
    public static final double WALK_END = WALK + 2.5;
    public static final double LAST_CLICK = WALK_END + 0.2;
    public static final double WIDE = LAST_CLICK + 0.25;
    public static final double RESUME = WIDE + 0.6;
    public static final double CAMERA_RETURN = RESUME + 0.8;
    public static final double END = CAMERA_RETURN + 2.0;

public static final double RANGE = 40.0;
    public static final double MIN_RANGE = 3.0;
    public static final float DAMAGE = 120f;

public static final double[] HOLD_AT = {PUNCH, UNDER, OVER, SIDE, BEATS[21], BEATS[22], BEATS[23], CRASH_1, KICK_1, CRASH_2, KICK_2,
            CRASH_3, FINAL_HIT};
    public static final double[] HOLD_FOR = {0.08, 0.06, 0.06, 0.06, 0.06, 0.06, 0.08, 0.06, 0.06, 0.06, 0.06, 0.06, 0.12};

    public static double film(double real) {
        double shift = 0;
        for (int i = 0; i < HOLD_AT.length; i++) {
            double start = HOLD_AT[i] + shift;
            if (real <= start) {
                return real - shift;
            }
            if (real < start + HOLD_FOR[i]) {
                return HOLD_AT[i];
            }
            shift += HOLD_FOR[i];
        }
        return real - shift;
    }

    public static double real(double film) {
        double real = film;
        for (int i = 0; i < HOLD_AT.length; i++) {
            if (HOLD_AT[i] < film) {
                real += HOLD_FOR[i];
            }
        }
        return real;
    }

    public static final double RESUME_REAL = real(RESUME);
    public static final double END_REAL = real(END);
    public static final int END_TICKS = (int) Math.round(END_REAL * 20);

    public static double frame(double t) {
        return Math.floor(t * FPS + 1.0e-6) / FPS;
    }

    public static double strikeAction(double t) {
        if (t < STRIKE) {
            return 0;
        }
        int show = (int) ((t - STRIKE) / SHOW_TIME);
        if (show >= SHOWS) {
            return STRIKE_SPAN;
        }
        return (t - STRIKE - show * SHOW_TIME) * STRIKE_SPAN / SHOW_TIME;
    }

    public static int strikeShow(double t) {
        if (t < STRIKE || t >= STRIKE + SHOW_TIME * SHOWS) {
            return -1;
        }
        return (int) ((t - STRIKE) / SHOW_TIME);
    }
}
