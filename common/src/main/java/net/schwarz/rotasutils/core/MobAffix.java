package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The built-in elite affixes: what makes one elite zombie fight differently from the next.
 *
 * <p>Natural elites and champions roll these at spawn (see {@code MobAffixService}). Each affix is one
 * readable idea - it heals from hits, it poisons, it explodes - so a player can learn the list and plan
 * around the words on the plate. Stat affixes work through attribute scales; the rest are combat hooks.
 * They are stored in {@link MonsterState#affixes()} as {@code rotas:affix/<name>}, which the kernel's
 * content catalog does not know and therefore skips.</p>
 */
public enum MobAffix {
    /** Heals for a share of the melee damage it deals. */
    VAMPIRIC(0xE0485C, 1, 1.20, Map.of()),
    /** Faster, and hits a little harder. */
    FRENZIED(0xFF8A3D, 1, 1.15, Map.of(
            "minecraft:generic.movement_speed", new MonsterDefinitions.Scale(1.30, 0, 0),
            "minecraft:generic.attack_damage", new MonsterDefinitions.Scale(1.15, 0, 0))),
    /** Plated: armour, toughness and it barely flinches. */
    ARMORED(0xAAB6C8, 1, 1.15, Map.of(
            "minecraft:generic.armor", new MonsterDefinitions.Scale(1, 0, 8),
            "minecraft:generic.armor_toughness", new MonsterDefinitions.Scale(1, 0, 4),
            "minecraft:generic.knockback_resistance", new MonsterDefinitions.Scale(1, 0, 0.6))),
    /** Its hits poison. */
    VENOMOUS(0x7ADB52, 3, 1.15, Map.of()),
    /** Its hits slow. */
    FROSTBOUND(0x8FE3FF, 3, 1.15, Map.of()),
    /** Melee attackers take a share of their own hit back. */
    THORNED(0xB8C45A, 5, 1.20, Map.of()),
    /** Recovers quickly once it has not been hurt for a few seconds. */
    REGENERATING(0x5CE08A, 5, 1.20, Map.of()),
    /** Hits much harder once it is below a third of its health. */
    BERSERK(0xFF3B3B, 8, 1.25, Map.of()),
    /** Bursts when it dies. Break line of sight or back off. */
    VOLATILE(0xFFB23D, 10, 1.25, Map.of()),
    /** Calls two of its kind the first time it drops below half health. */
    SUMMONER(0xB98CFF, 12, 1.35, Map.of());

    public static final String PREFIX = "rotas:affix/";

    private final int rgb;
    private final int minLevel;
    private final double xpMultiplier;
    private final Map<String, MonsterDefinitions.Scale> attributes;

    MobAffix(int rgb, int minLevel, double xpMultiplier, Map<String, MonsterDefinitions.Scale> attributes) {
        this.rgb = rgb;
        this.minLevel = minLevel;
        this.xpMultiplier = xpMultiplier;
        this.attributes = attributes;
    }

    public int rgb() { return rgb; }
    public int minLevel() { return minLevel; }
    public double xpMultiplier() { return xpMultiplier; }
    public Map<String, MonsterDefinitions.Scale> attributes() { return attributes; }
    public String key() { return name().toLowerCase(Locale.ROOT); }
    public ContentId id() { return new ContentId(PREFIX + key()); }

    /** The affix a stored id names, or null for a kernel affix or anything unknown. */
    public static MobAffix fromId(ContentId id) {
        return id == null ? null : fromKey(id.value().startsWith(PREFIX) ? id.value().substring(PREFIX.length()) : "");
    }

    public static MobAffix fromKey(String key) {
        for (MobAffix affix : values()) {
            if (affix.key().equals(key)) return affix;
        }
        return null;
    }

    public static List<MobAffix> of(List<ContentId> ids) {
        List<MobAffix> result = new ArrayList<>();
        for (ContentId id : ids) {
            MobAffix affix = fromId(id);
            if (affix != null) result.add(affix);
        }
        return result;
    }

    // Name mark -------------------------------------------------------------------------------------

    /**
     * The rank and affixes ride on the mob's custom name as the style's click-insertion text, which
     * the vanilla entity-data sync already carries to every client. No packet, no extra tracking, and
     * a client without the mod just sees the plain name.
     */
    public static final String MARK = "rotas:mark|";

    public record Mark(MonsterRank rank, List<MobAffix> affixes) {
        public static final Mark NONE = new Mark(MonsterRank.NORMAL, List.of());
    }

    public static String encode(MonsterRank rank, List<MobAffix> affixes) {
        StringBuilder text = new StringBuilder(MARK).append(rank.name()).append('|');
        for (int i = 0; i < affixes.size(); i++) {
            if (i > 0) text.append(',');
            text.append(affixes.get(i).key());
        }
        return text.toString();
    }

    public static Mark decode(String insertion) {
        if (insertion == null || !insertion.startsWith(MARK)) return Mark.NONE;
        String[] parts = insertion.substring(MARK.length()).split("\\|", -1);
        MonsterRank rank = MonsterRank.byName(parts[0]);
        List<MobAffix> affixes = new ArrayList<>();
        if (parts.length > 1 && !parts[1].isEmpty()) {
            for (String key : parts[1].split(",")) {
                MobAffix affix = fromKey(key);
                if (affix != null && affixes.size() < 4) affixes.add(affix);
            }
        }
        return new Mark(rank, List.copyOf(affixes));
    }
}
