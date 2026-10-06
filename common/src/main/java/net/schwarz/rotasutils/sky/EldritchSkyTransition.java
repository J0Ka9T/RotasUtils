package net.schwarz.rotasutils.sky;

import net.minecraft.nbt.CompoundTag;

public final class EldritchSkyTransition {
    public static final int OPENING_TICKS = 280;
    public static final int CLOSING_TICKS = 220;

    public enum State { OFF, OPENING, ACTIVE, CLOSING, SHATTERING }

    public static final int SHATTER_GASP = 7;
    public static final int SHATTER_CLEAR = 40;
    public static final float SHATTER_RESIDUE = 0.26f;

    public static final int VARIANT_SKY = 0;
    public static final int VARIANT_HERALD = 1;
    public static final int VARIANT_HERALD_RED = 2;

    public static final int VARIANT_SKY_RED = 3;

    public static boolean red(int variant) {
        return palette(variant) == PALETTE_RED;
    }

    public static final int VARIANT_SKY_GOLD = 4;
    public static final int VARIANT_HERALD_GOLD = 5;
    public static final int VARIANT_VOID = 6;
    public static final int VARIANT_FOUR_SKIES = 7;
    public static final int VARIANT_SKY_RAINBOW = 8;

    public static final int PALETTE_BLUE = 0;
    public static final int PALETTE_RED = 1;
    public static final int PALETTE_GOLD = 2;
    public static final int PALETTE_VOID = 3;
    public static final int PALETTE_RAINBOW = 4;
    public static final int PALETTES = 5;

    public static int palette(int variant) {
        return switch (variant) {
            case VARIANT_HERALD_RED, VARIANT_SKY_RED -> PALETTE_RED;
            case VARIANT_SKY_GOLD, VARIANT_HERALD_GOLD -> PALETTE_GOLD;
            case VARIANT_VOID -> PALETTE_VOID;
            case VARIANT_SKY_RAINBOW -> PALETTE_RAINBOW;
            default -> PALETTE_BLUE;
        };
    }

    public static boolean herald(int variant) {
        return variant == VARIANT_HERALD || variant == VARIANT_HERALD_RED || variant == VARIANT_HERALD_GOLD;
    }

    public static boolean tentacles(int variant) {
        return variant == VARIANT_VOID;
    }

    public static final class Snapshot {
        public final State state;
        public final long referenceTick;
        public final float opennessAtReference;
        public final long seed;
        public final int variant;

        public Snapshot(State state, long referenceTick, float opennessAtReference, long seed) {
            this(state, referenceTick, opennessAtReference, seed, VARIANT_SKY);
        }

        public Snapshot(State state, long referenceTick, float opennessAtReference, long seed, int variant) {
            this.state = state;
            this.referenceTick = referenceTick;
            this.opennessAtReference = clamp01(opennessAtReference);
            this.seed = seed;
            this.variant = variant;
        }

        public static Snapshot off() {
            return new Snapshot(State.OFF, 0L, 0f, 0L);
        }

        public float opennessAt(long tick, float partial) {
            float t = (float) (tick - referenceTick) + partial;
            switch (state) {
                case OFF: return 0f;
                case ACTIVE: return 1f;
                case OPENING: return clamp01(opennessAtReference + t / (float) OPENING_TICKS);
                case CLOSING: return clamp01(opennessAtReference - t / (float) CLOSING_TICKS);
                case SHATTERING: {
                    if (t < -SHATTER_GASP) return opennessAtReference;
                    if (t < 0f) return opennessAtReference * (1f - 0.18f * (t + SHATTER_GASP) / SHATTER_GASP);
                    return clamp01(SHATTER_RESIDUE * (1f - t / SHATTER_CLEAR));
                }
                default: return 0f;
            }
        }

        public Snapshot settle(long tick) {
            float openness = opennessAt(tick, 0f);
            if (state == State.OPENING && openness >= 1f) {
                return new Snapshot(State.ACTIVE, tick, 1f, seed, variant);
            }
            if ((state == State.CLOSING || state == State.SHATTERING) && openness <= 0f && tick >= referenceTick) {
                return new Snapshot(State.OFF, tick, 0f, 0L);
            }
            return this;
        }

        public boolean active() {
            return state == State.OPENING || state == State.ACTIVE;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putByte("s", (byte) state.ordinal());
            tag.putLong("ref", referenceTick);
            tag.putFloat("open", opennessAtReference);
            tag.putLong("seed", seed);
            tag.putByte("variant", (byte) variant);
            return tag;
        }

        public static Snapshot load(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) return off();
            State[] all = State.values();
            int index = tag.getByte("s");
            State state = index >= 0 && index < all.length ? all[index] : State.OFF;
            return new Snapshot(state, tag.getLong("ref"), tag.getFloat("open"), tag.getLong("seed"),
                    tag.getByte("variant"));
        }
    }

    public static Snapshot toggle(Snapshot current, long now, long freshSeed) {
        return toggle(current, now, freshSeed, VARIANT_SKY);
    }

    public static Snapshot toggle(Snapshot current, long now, long freshSeed, int variant) {
        Snapshot present = current == null ? Snapshot.off() : current;
        float openness = present.opennessAt(now, 0f);
        boolean wantActive = !present.active();
        boolean fresh = present.state == State.OFF;
        long seed = fresh ? freshSeed : present.seed;
        int kind = fresh ? variant : present.variant;
        if (wantActive) {
            return new Snapshot(State.OPENING, now, openness, seed, kind);
        }
        return new Snapshot(State.CLOSING, now, openness, seed, kind);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private EldritchSkyTransition() {
    }
}
