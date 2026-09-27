package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MonsterCatalog {
    private final Map<ContentId, MonsterDefinitions.Profile> profiles;
    private final Map<ContentId, MonsterDefinitions.Tier> tiers;
    private final Map<ContentId, MonsterDefinitions.Affix> affixes;
    private final Map<ContentId, BossDefinitions.Boss> bosses;
    private final Map<String, List<MonsterDefinitions.Profile>> exact = new HashMap<>();
    private final List<MonsterDefinitions.Profile> broad = new ArrayList<>();
    private static final Comparator<MonsterDefinitions.Profile> ORDER = Comparator.comparingInt(MonsterDefinitions.Profile::priority).reversed().thenComparing(MonsterDefinitions.Profile::id);

    public MonsterCatalog(Map<ContentId, MonsterDefinitions.Profile> profiles, Map<ContentId, MonsterDefinitions.Tier> tiers,
                          Map<ContentId, MonsterDefinitions.Affix> affixes, Map<ContentId, BossDefinitions.Boss> bosses) {
        if (profiles.size() > 256 || tiers.size() > 64 || affixes.size() > 128 || bosses.size() > 128) {
            throw new IllegalArgumentException("Monster definition budget exceeded");
        }
        this.profiles = Map.copyOf(profiles); this.tiers = Map.copyOf(tiers); this.affixes = Map.copyOf(affixes);
        this.bosses = Map.copyOf(bosses);
        profiles.values().stream().sorted(ORDER).forEach(profile -> {
            if (profile.manualOnly()) { return; }
            if (profile.selector().entities().isEmpty()) { broad.add(profile); }
            else { profile.selector().entities().forEach(id -> exact.computeIfAbsent(id, ignored -> new ArrayList<>()).add(profile)); }
        });
    }
    public static MonsterCatalog empty() { return new MonsterCatalog(Map.of(), Map.of(), Map.of(), Map.of()); }
    public Map<ContentId, MonsterDefinitions.Profile> profiles() { return profiles; }
    public Map<ContentId, MonsterDefinitions.Tier> tiers() { return tiers; }
    public Map<ContentId, MonsterDefinitions.Affix> affixes() { return affixes; }
    public Map<ContentId, BossDefinitions.Boss> bosses() { return bosses; }
    public List<MonsterDefinitions.Profile> candidates(String entity) {
        List<MonsterDefinitions.Profile> result = new ArrayList<>(exact.getOrDefault(entity, List.of()));
        result.addAll(broad); result.sort(ORDER); return List.copyOf(result);
    }
}
