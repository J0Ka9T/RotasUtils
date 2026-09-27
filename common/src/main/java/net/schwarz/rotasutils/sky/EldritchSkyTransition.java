package net.schwarz.rotasutils.sky;

import net.minecraft.nbt.CompoundTag;

/**
 * Pure openness/state math for the eldritch sky rupture. Server persists a snapshot
 * (state, reference tick, openness-at-reference, deterministic seed); openness at
 * any later tick is derived from that snapshot without further server traffic.
 */
public final class EldritchSkyTransition {
    /** 14 s: omen, crack, strain, split and spin-up each get room to read. */
    public static final int OPENING_TICKS = 280;
    /** 11 s: the collapse is a little quicker than the tearing. */
    public static final int CLOSING_TICKS = 220;

    /** SHATTERING (appended so saved ordinals stay valid): held open until the reference tick, then broken. */
    public enum State { OFF, OPENING, ACTIVE, CLOSING, SHATTERING }

    /** Ticks before the break in which a shattering rift draws in on itself. */
    public static final int SHATTER_GASP = 7;
    /** Ticks after the break for the darkened sky to clear; the rift itself is gone at the break. */
    public static final int SHATTER_CLEAR = 40;
    /** Openness left at the break: below where the tear shows, so only the sky's tint remains. */
    public static final float SHATTER_RESIDUE = 0.26f;

    /** The plain invasion sky. */
    public static final int VARIANT_SKY = 0;
    /** The sky with a colossal herald stepping out of the tear. */
    public static final int VARIANT_HERALD = 1;
    /** The herald in crimson, under a blood-dark sky. */
    public static final int VARIANT_HERALD_RED = 2;

    /** The plain invasion sky in crimson. */
    public static final int VARIANT_SKY_RED = 3;

    /** True for the crimson skies, with or without the herald. */
    public static boolean red(int variant) {
        return palette(variant) == PALETTE_RED;
    }

    /** The plain invasion sky in gold. */
    public static final int VARIANT_SKY_GOLD = 4;
    /** The herald in gold, under a gold sky. */
    public static final int VARIANT_HERALD_GOLD = 5;
    /** A black void sky; tentacles, not a herald, come out of the tear. */
    public static final int VARIANT_VOID = 6;
    /** Blue, gold, crimson and void rifts open at the four compass points. */
    public static final int VARIANT_FOUR_SKIES = 7;
    /** A rainbow sky: every colour of the tear is hue-turned, so the whole rupture cycles the spectrum. */
    public static final int VARIANT_SKY_RAINBOW = 8;

    /** Colour families; the client maps every sky colour and texture through one of these. */
    public static final int PALETTE_BLUE = 0;
    public static final int PALETTE_RED = 1;
    public static final int PALETTE_GOLD = 2;
    public static final int PALETTE_VOID = 3;
    public static final int PALETTE_RAINBOW = 4;
    /** How many palettes there are; the client keeps one texture cache per palette. */
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

    /** True for the skies with a herald stepping out. */
    public static boolean herald(int variant) {
        return variant == VARIANT_HERALD || variant == VARIANT_HERALD_RED || variant == VARIANT_HERALD_GOLD;
    }

    /** True for the sky with tentacles reaching out of the tear. */
    public static boolean tentacles(int variant) {
        return variant == VARIANT_VOID;
    }

    public static final class Snapshot {
        public final State state;
        public final long referenceTick;
        public final float opennessAtReference;
        public final long seed;
        /** Which sky this is; see {@link #VARIANT_SKY} and {@link #VARIANT_HERALD}. */
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

        /** Advance settled states: OPENING that reached 1 becomes ACTIVE; CLOSING that reached 0 becomes OFF. */
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

    /** Build the next snapshot for a toggle at {@code now} carrying openness forward. */
    public static Snapshot toggle(Snapshot current, long now, long freshSeed) {
        return toggle(current, now, freshSeed, VARIANT_SKY);
    }

    /**
     * Same as {@link #toggle(Snapshot, long, long)}; a sky opened from nothing takes {@code variant},
     * while reversing a sky that is still showing keeps the one it already has.
     */
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
