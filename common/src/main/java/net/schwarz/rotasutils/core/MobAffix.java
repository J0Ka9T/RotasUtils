package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum MobAffix {
    VAMPIRIC(0xE0485C, 1, 1.20, Map.of()),
    FRENZIED(0xFF8A3D, 1, 1.15, Map.of(
            "minecraft:generic.movement_speed", new MonsterDefinitions.Scale(1.30, 0, 0),
            "minecraft:generic.attack_damage", new MonsterDefinitions.Scale(1.15, 0, 0))),
    ARMORED(0xAAB6C8, 1, 1.15, Map.of(
            "minecraft:generic.armor", new MonsterDefinitions.Scale(1, 0, 8),
            "minecraft:generic.armor_toughness", new MonsterDefinitions.Scale(1, 0, 4),
            "minecraft:generic.knockback_resistance", new MonsterDefinitions.Scale(1, 0, 0.6))),
    VENOMOUS(0x7ADB52, 3, 1.15, Map.of()),
    FROSTBOUND(0x8FE3FF, 3, 1.15, Map.of()),
    THORNED(0xB8C45A, 5, 1.20, Map.of()),
    REGENERATING(0x5CE08A, 5, 1.20, Map.of()),
    BERSERK(0xFF3B3B, 8, 1.25, Map.of()),
    VOLATILE(0xFFB23D, 10, 1.25, Map.of()),
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
