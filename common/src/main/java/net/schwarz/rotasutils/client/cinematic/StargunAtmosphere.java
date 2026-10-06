package net.schwarz.rotasutils.client.cinematic;

import static net.schwarz.rotasutils.ability.StargunTimings.*;

public final class StargunAtmosphere {
    private StargunAtmosphere() {
    }

    public static final double AFTERGLOW_END = END + 20;
    public static final float RED_RADIUS = 160;

    public static double red(double t) {
        return Curves.smootherstep(Curves.window(t, IMPACT + 3, FRONT_END))
                * (1 - Curves.smootherstep(Curves.window(t, END + 10, AFTERGLOW_END)));
    }

    public static double flash(double t) {
        if (t >= END) {
            return 0;
        }
        double fire = t < FIRE ? 0 : 0.78 * Math.exp(-(t - FIRE) / 0.16);
        double impact = t < IMPACT ? 0 : Math.exp(-(t - IMPACT) / 0.22);
        return Math.max(fire, impact);
    }
}
