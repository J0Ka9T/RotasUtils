package net.schwarz.rotasutils.core;

import java.util.Set;

public final class MonsterTypes {
    private static final Set<String> UNDEAD = Set.of("zombie", "skeleton", "wither_skeleton", "drowned", "husk", "stray", "phantom", "zombified_piglin", "wither");
    private static final Set<String> BEAST = Set.of("wolf", "fox", "bear", "polar_bear", "ravager", "hoglin", "zoglin", "goat");
    private static final Set<String> HUMANOID = Set.of("pillager", "vindicator", "evoker", "witch", "piglin", "piglin_brute");
    private static final Set<String> ARTHROPOD = Set.of("spider", "cave_spider", "silverfish", "endermite", "bee");
    private static final Set<String> AQUATIC = Set.of("guardian", "elder_guardian", "drowned", "squid", "glow_squid");

    private MonsterTypes() { }

    public static MonsterType infer(String entityId) {
        if (entityId == null) return MonsterType.UNKNOWN;
        String path = entityId.substring(Math.max(0, entityId.indexOf(':') + 1));
        if (path.equals("ender_dragon")) return MonsterType.DRAGON;
        if (path.contains("golem") || path.equals("shulker")) return MonsterType.CONSTRUCT;
        if (UNDEAD.contains(path)) return MonsterType.UNDEAD;
        if (ARTHROPOD.contains(path)) return MonsterType.ARTHROPOD;
        if (AQUATIC.contains(path)) return MonsterType.AQUATIC;
        if (HUMANOID.contains(path)) return MonsterType.HUMANOID;
        if (BEAST.contains(path)) return MonsterType.BEAST;
        if (path.contains("blaze") || path.contains("magma")) return MonsterType.ELEMENTAL;
        return MonsterType.UNKNOWN;
    }
}
