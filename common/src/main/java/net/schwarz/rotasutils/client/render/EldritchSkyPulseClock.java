package net.schwarz.rotasutils.client.render;

public final class EldritchSkyPulseClock {
    public enum Channel {
        FRACTURE(6f, 10f, 0.55f, 1.15f, 0x46A7_11D3L),
        HALO(9f, 16f, 0.75f, 1.45f, 0x71B3_92E5L),
        BODY(12f, 22f, 1.00f, 1.50f, 0x2F81_C4A9L),
        LIGHTNING(14f, 26f, 0.40f, 0.82f, 0x19D4_EB67L);

        private final float minPeriod;
        private final float maxPeriod;
        private final float minDuration;
        private final float maxDuration;
        private final long salt;

        Channel(float minPeriod, float maxPeriod, float minDuration, float maxDuration, long salt) {
            this.minPeriod = minPeriod;
            this.maxPeriod = maxPeriod;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.salt = salt;
        }
    }

    private EldritchSkyPulseClock() {
    }

    public static float period(long seed, Channel channel) {
        return lerp(channel.minPeriod, channel.maxPeriod,
                EldritchSkyCelestial.hash01(seed ^ channel.salt));
    }

    public static float duration(long seed, Channel channel) {
        return lerp(channel.minDuration, channel.maxDuration,
                EldritchSkyCelestial.hash01(seed ^ (channel.salt * 0x9E37L)));
    }

    public static float phaseSeconds(long seed, Channel channel) {
        return period(seed, channel) * EldritchSkyCelestial.hash01(seed ^ (channel.salt * 0xC2B2L));
    }

    public static float envelope(long seed, Channel channel, float seconds) {
        float period = period(seed, channel);
        float duration = Math.min(period * 0.45f, duration(seed, channel));
        float local = positiveModulo(seconds + phaseSeconds(seed, channel), period);
        if (local >= duration) {
            return 0f;
        }
        float t = local / duration;
        float attack = EldritchSkyCelestial.smoothstep(0f, 0.28f, t);
        float decay = 1f - EldritchSkyCelestial.smoothstep(0.38f, 1f, t);
        return attack * decay;
    }

    public static float cycleProgress(long seed, Channel channel, float seconds) {
        return positiveModulo(seconds + phaseSeconds(seed, channel), period(seed, channel))
                / period(seed, channel);
    }

    public static float travelingPulse(int segment, int segmentCount, float seconds, float periodSeconds,
                                       float phase, float direction) {
        if (segmentCount <= 0 || periodSeconds <= 0f) return 0f;
        float position = segment / (float) segmentCount;
        float head = positiveModulo(phase + direction * seconds / periodSeconds, 1f);
        float distance = Math.abs(position - head);
        distance = Math.min(distance, 1f - distance);
        return 1f - EldritchSkyCelestial.smoothstep(0.018f, 0.145f, distance);
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0f ? result + divisor : result;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
