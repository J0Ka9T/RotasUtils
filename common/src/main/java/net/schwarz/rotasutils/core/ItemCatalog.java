package net.schwarz.rotasutils.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemCatalog {
    private final Map<ContentId, ItemDefinitions.Profile> profiles;
    private final Map<ContentId, ItemDefinitions.Rarity> rarities;
    private final Map<ContentId, ItemDefinitions.ItemSet> sets;
    private final Map<ContentId, ItemDefinitions.LootTable> loot;
    private final Map<ContentId, ContentId> membership = new HashMap<>();

    public ItemCatalog(Map<ContentId, ItemDefinitions.Profile> profiles, Map<ContentId, ItemDefinitions.Rarity> rarities,
                       Map<ContentId, ItemDefinitions.ItemSet> sets, Map<ContentId, ItemDefinitions.LootTable> loot) {
        if (profiles.size() > 1024 || rarities.size() > 64 || sets.size() > 256 || loot.size() > 512) {
            throw new IllegalArgumentException("Item definition budget exceeded");
        }
        this.profiles = Map.copyOf(profiles); this.rarities = Map.copyOf(rarities);
        this.sets = Map.copyOf(sets); this.loot = Map.copyOf(loot);
        sets.forEach((id, set) -> set.pieces().forEach(piece -> {
            ContentId previous = membership.put(piece, id);
            if (previous != null) { throw new IllegalArgumentException("Item " + piece + " belongs to two sets"); }
        }));
        profiles.forEach((id, profile) -> {
            if (profile.set() != null && !profile.set().equals(membership.get(id))) {
                throw new IllegalArgumentException("Item " + id + " declares a set that does not list it");
            }
        });
    }

    public static ItemCatalog empty() { return new ItemCatalog(Map.of(), Map.of(), Map.of(), Map.of()); }
    public Map<ContentId, ItemDefinitions.Profile> profiles() { return profiles; }
    public Map<ContentId, ItemDefinitions.Rarity> rarities() { return rarities; }
    public Map<ContentId, ItemDefinitions.ItemSet> sets() { return sets; }
    public Map<ContentId, ItemDefinitions.LootTable> loot() { return loot; }
    public ContentId setOf(ContentId profile) { return membership.get(profile); }

    public Map<ContentId, List<ItemDefinitions.SetBonus>> bonuses(Map<ContentId, Integer> owned) {
        Map<ContentId, List<ItemDefinitions.SetBonus>> result = new HashMap<>();
        owned.forEach((id, count) -> {
            var set = sets.get(id);
            if (set == null) { return; }
            var active = set.active(count);
            if (!active.isEmpty()) { result.put(id, active); }
        });
        return Map.copyOf(result);
    }
}
