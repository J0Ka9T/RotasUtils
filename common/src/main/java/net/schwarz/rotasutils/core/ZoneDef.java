package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;

/**
 * An admin-defined level zone: a named slice of a dimension that gives its mobs a level band.
 *
 * <p>A zone is the union of its {@link ZoneArea areas}; an empty list means the whole dimension,
 * so "not even a shape" is a valid zone. When several zones cover one spot the highest
 * {@code priority} wins, and when none does the server falls back to the configurable
 * distance-from-spawn ramp. Zones are authored with the Zone Wand, the admin zone manager, or a
 * JSON content pack, and persisted in {@link net.schwarz.rotasutils.data.RotasData}.</p>
 *
 * <p>{@link ZoneFeatures} carries the zone's type, mob isolation, spawn points, titles, effects and
 * movement rules.</p>
 */
public record ZoneDef(String id, String name, String dimension, List<ZoneArea> areas,
                      int levelMin, int levelMax, int priority, boolean enabled, Danger danger,
                      int recommendedMin, int recommendedMax, double xpMultiplier,
                      int transitionBlocks, boolean safe, List<ZoneArea> excludedAreas,
                      long revision, ZoneCombatRules combatRules, List<Requirement> entryRequirements,
                      ZoneFeatures features) {
    /** Areas allowed per zone; the same order of magnitude as the other bounded lists. */
    public static final int MAX_AREAS = 16;
    /** Entry gate rules allowed per zone; enough for a quest, a level and a faction check. */
    public static final int MAX_ENTRY_REQUIREMENTS = 16;

    public ZoneDef {
        if (id == null || id.isBlank() || id.length() > 128) {
            throw new IllegalArgumentException("Zone id must be 1..128 characters");
        }
        name = name == null ? "" : name;
        if (name.length() > 64) {
            throw new IllegalArgumentException("Zone name exceeds 64 characters");
        }
        dimension = dimension == null ? "" : dimension;
        if (dimension.length() > 128) {
            throw new IllegalArgumentException("Zone dimension exceeds 128 characters");
        }
        areas = List.copyOf(areas);
        excludedAreas = List.copyOf(excludedAreas == null ? List.of() : excludedAreas);
        if (areas.size() + excludedAreas.size() > MAX_AREAS) {
            throw new IllegalArgumentException("Zone area count exceeds " + MAX_AREAS);
        }
        if (revision < 0) throw new IllegalArgumentException("Zone revision cannot be negative");
        combatRules = combatRules == null ? ZoneCombatRules.inherit() : combatRules;
        entryRequirements = List.copyOf(entryRequirements == null ? List.of() : entryRequirements);
        if (entryRequirements.size() > MAX_ENTRY_REQUIREMENTS) {
            throw new IllegalArgumentException("Zone entry requirement count exceeds " + MAX_ENTRY_REQUIREMENTS);
        }
        features = features == null ? ZoneFeatures.DEFAULT : features;
        if (levelMin < 1 || levelMax < levelMin || levelMax > 10000) {
            throw new IllegalArgumentException("Zone level band must be 1..10000 with min <= max");
        }
        if (priority < -10000 || priority > 10000) {
            throw new IllegalArgumentException("Zone priority outside -10000..10000");
        }
        danger = danger == null ? Danger.NORMAL : danger;
        if (recommendedMin < 1 || recommendedMax < recommendedMin || recommendedMax > 10000) {
            throw new IllegalArgumentException("Zone recommended band must be 1..10000 with min <= max");
        }
        if (!Double.isFinite(xpMultiplier) || xpMultiplier < 0 || xpMultiplier > 10) {
            throw new IllegalArgumentException("Zone XP multiplier outside 0..10");
        }
        if (transitionBlocks < 0 || transitionBlocks > 256) {
            throw new IllegalArgumentException("Zone transition outside 0..256 blocks");
        }
    }

    /** Zone without features, for callers that predate them; features start at their defaults. */
    public ZoneDef(String id, String name, String dimension, List<ZoneArea> areas, int levelMin, int levelMax,
                   int priority, boolean enabled, Danger danger, int recommendedMin, int recommendedMax,
                   double xpMultiplier, int transitionBlocks, boolean safe, List<ZoneArea> excludedAreas,
                   long revision, ZoneCombatRules combatRules, List<Requirement> entryRequirements) {
        this(id, name, dimension, areas, levelMin, levelMax, priority, enabled, danger, recommendedMin,
                recommendedMax, xpMultiplier, transitionBlocks, safe, excludedAreas, revision, combatRules,
                entryRequirements, ZoneFeatures.DEFAULT);
    }

    public ZoneDef(String id, String name, String dimension, List<ZoneArea> areas,
                   int levelMin, int levelMax, int priority, boolean enabled) {
        this(id, name, dimension, areas, levelMin, levelMax, priority, enabled, Danger.NORMAL,
                levelMin, levelMax, 1.0, 16, false, List.of(), 0, ZoneCombatRules.inherit(), List.of(),
                ZoneFeatures.DEFAULT);
    }

    public ZoneDef(String id, String name, String dimension, List<ZoneArea> areas, int levelMin, int levelMax,
                   int priority, boolean enabled, Danger danger, int recommendedMin, int recommendedMax,
                   double xpMultiplier, int transitionBlocks, boolean safe) {
        this(id,name,dimension,areas,levelMin,levelMax,priority,enabled,danger,recommendedMin,recommendedMax,
                xpMultiplier,transitionBlocks,safe,List.of(),0,ZoneCombatRules.inherit(),List.of(),ZoneFeatures.DEFAULT);
    }

    /** True when the position is inside any area, or anywhere in the dimension when it has none. */
    public boolean contains(double x, double y, double z) {
        boolean additive = areas.isEmpty() || areas.stream().anyMatch(area -> area.contains(x,y,z));
        return additive && excludedAreas.stream().noneMatch(area -> area.contains(x,y,z));
    }

    /** Distance in blocks to the nearest area surface; 0 inside, and 0 for a whole-dimension zone. */
    public double distance(double x, double y, double z) {
        if (areas.isEmpty()) {
            return 0.0;
        }
        double nearest = Double.MAX_VALUE;
        for (ZoneArea area : areas) {
            nearest = Math.min(nearest, area.distance(x, y, z));
            if (nearest == 0.0) {
                break;
            }
        }
        return nearest;
    }

    /** True when this zone is a candidate for the given dimension. */
    public boolean appliesTo(String dimensionId) {
        return enabled && (dimension.isEmpty() || dimension.equals(dimensionId));
    }

    /** True when entering this zone is gated by at least one blocking requirement. */
    public boolean hasEntryLock() {
        for (Requirement requirement : entryRequirements) {
            if (!requirement.recommendationOnly()) {
                return true;
            }
        }
        return false;
    }

    public ZoneDef withArea(ZoneArea area) {
        List<ZoneArea> next = new ArrayList<>(areas);
        next.add(area);
        return withAreas(next);
    }

    /** Same zone with a new shape list; every gameplay setting is kept. */
    public ZoneDef withAreas(List<ZoneArea> next) {
        return new ZoneDef(id, name, dimension, next, levelMin, levelMax, priority, enabled,
                danger, recommendedMin, recommendedMax, xpMultiplier, transitionBlocks, safe, excludedAreas, revision,
                combatRules, entryRequirements, features);
    }

    public ZoneDef withExcludedAreas(List<ZoneArea> next) {
        return new ZoneDef(id,name,dimension,areas,levelMin,levelMax,priority,enabled,danger,recommendedMin,
                recommendedMax,xpMultiplier,transitionBlocks,safe,next,revision,combatRules,entryRequirements,features);
    }

    public ZoneDef withCombatRules(ZoneCombatRules next) {
        return new ZoneDef(id,name,dimension,areas,levelMin,levelMax,priority,enabled,danger,recommendedMin,
                recommendedMax,xpMultiplier,transitionBlocks,safe,excludedAreas,revision,next,entryRequirements,features);
    }

    /** Same zone with a new entry gate; an empty list opens the zone again. */
    public ZoneDef withEntryRequirements(List<Requirement> next) {
        return new ZoneDef(id,name,dimension,areas,levelMin,levelMax,priority,enabled,danger,recommendedMin,
                recommendedMax,xpMultiplier,transitionBlocks,safe,excludedAreas,revision,combatRules,next,features);
    }

    /** Same zone with new type, mob isolation, spawn points, titles, effects and movement rules. */
    public ZoneDef withFeatures(ZoneFeatures next) {
        return new ZoneDef(id,name,dimension,areas,levelMin,levelMax,priority,enabled,danger,recommendedMin,
                recommendedMax,xpMultiplier,transitionBlocks,safe,excludedAreas,revision,combatRules,entryRequirements,next);
    }

    public ZoneDef withRevision(long next) {
        return new ZoneDef(id,name,dimension,areas,levelMin,levelMax,priority,enabled,danger,recommendedMin,
                recommendedMax,xpMultiplier,transitionBlocks,safe,excludedAreas,next,combatRules,entryRequirements,features);
    }

    /** Same zone and shapes with edited settings; the recommended band is clamped to stay valid. */
    public ZoneDef withSettings(String newName, int min, int max, int newPriority, boolean isEnabled,
                                Danger newDanger, int recMin, int recMax, double xp, int transition,
                                boolean isSafe) {
        int safeMin = Math.max(1, Math.min(10000, min));
        int safeMax = Math.max(safeMin, Math.min(10000, max));
        int safeRecMin = Math.max(1, Math.min(10000, recMin));
        int safeRecMax = Math.max(safeRecMin, Math.min(10000, recMax));
        return new ZoneDef(id, newName, dimension, areas, safeMin, safeMax,
                Math.max(-10000, Math.min(10000, newPriority)), isEnabled, newDanger, safeRecMin, safeRecMax,
                Double.isFinite(xp) ? Math.max(0, Math.min(10, xp)) : 1.0,
                Math.max(0, Math.min(256, transition)), isSafe, excludedAreas, revision, combatRules, entryRequirements,
                features);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.putInt("level_min", levelMin);
        tag.putInt("level_max", levelMax);
        tag.putInt("priority", priority);
        tag.putBoolean("enabled", enabled);
        tag.putString("danger", danger.name());
        tag.putInt("recommended_min", recommendedMin);
        tag.putInt("recommended_max", recommendedMax);
        tag.putDouble("xp_multiplier", xpMultiplier);
        tag.putInt("transition_blocks", transitionBlocks);
        tag.putBoolean("safe", safe);
        tag.putLong("revision", revision);
        ListTag list = new ListTag();
        for (ZoneArea area : areas) {
            list.add(area.save());
        }
        tag.put("areas", list);
        ListTag excluded = new ListTag();
        for (ZoneArea area : excludedAreas) excluded.add(area.save());
        tag.put("excluded_areas", excluded);
        tag.put("combat_rules", combatRules.save());
        tag.put("entry_requirements", Nbt.saveList(entryRequirements, Requirement::save));
        tag.put("features", features.save());
        return tag;
    }

    public static ZoneDef load(CompoundTag tag) {
        List<ZoneArea> areas = new ArrayList<>();
        ListTag list = tag.getList("areas", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            areas.add(ZoneArea.load(list.getCompound(i)));
        }
        List<ZoneArea> excluded = new ArrayList<>();
        ListTag excludedList = tag.getList("excluded_areas", Tag.TAG_COMPOUND);
        for (int i=0;i<excludedList.size();i++) excluded.add(ZoneArea.load(excludedList.getCompound(i)));
        int min = tag.getInt("level_min");
        int max = Math.max(min, tag.getInt("level_max"));
        Danger danger = Danger.parse(tag.getString("danger"), Danger.NORMAL);
        return new ZoneDef(tag.getString("id"), tag.getString("name"), tag.getString("dimension"),
                areas, min, max, tag.getInt("priority"), !tag.contains("enabled") || tag.getBoolean("enabled"),
                danger, tag.contains("recommended_min") ? tag.getInt("recommended_min") : min,
                tag.contains("recommended_max") ? tag.getInt("recommended_max") : max,
                tag.contains("xp_multiplier") ? tag.getDouble("xp_multiplier") : 1.0,
                tag.contains("transition_blocks") ? tag.getInt("transition_blocks") : 16,
                tag.getBoolean("safe"), excluded, tag.contains("revision") ? tag.getLong("revision") : 0,
                tag.contains("combat_rules") ? ZoneCombatRules.load(tag.getCompound("combat_rules")) : ZoneCombatRules.inherit(),
                entryRequirements(tag),
                // Zones saved before features existed keep behaving exactly as before.
                tag.contains("features") ? ZoneFeatures.load(tag.getCompound("features")) : ZoneFeatures.DEFAULT);
    }

    /** Legacy zones predate the entry gate, so a missing list simply leaves the zone open. */
    private static List<Requirement> entryRequirements(CompoundTag tag) {
        return tag.contains("entry_requirements")
                ? Nbt.loadList(tag, "entry_requirements", Requirement::load) : List.of();
    }

    /** Human-readable level band, e.g. "Lv 5-20". */
    public String levelLabel() {
        return levelMin == levelMax ? "Lv " + levelMin : "Lv " + levelMin + "-" + levelMax;
    }

    /** Area count summary for admin lists. */
    public String areaLabel() {
        if (areas.isEmpty()) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.zone.area.whole");
        }
        return areas.size() == 1 ? net.schwarz.rotasutils.util.ThaiText.t("rotasutils.zone.area.one")
                : net.schwarz.rotasutils.util.ThaiText.t("rotasutils.zone.area.many", areas.size());
    }

    public static ZoneDef create(String id, String name, String dimension, int levelMin, int levelMax) {
        return new ZoneDef(id, name, dimension, List.of(), levelMin, levelMax, 0, true);
    }

    /**
     * The zone that governs a position: the highest-priority enabled zone for the dimension that
     * contains it (equal priority: lowest id), or null. Shared by the server (levels, rules, titles)
     * and the client (zone chip, entry banner) so both always agree.
     */
    public static ZoneDef select(java.util.Collection<ZoneDef> zones, String dimension, double x, double y, double z) {
        ZoneDef best = null;
        for (ZoneDef zone : zones) {
            if (!zone.appliesTo(dimension) || !zone.contains(x, y, z)) {
                continue;
            }
            if (best == null || zone.priority() > best.priority()
                    || (zone.priority() == best.priority() && zone.id().compareTo(best.id()) < 0)) {
                best = zone;
            }
        }
        return best;
    }

    /**
     * What players may know about this zone: name, band, danger, shapes, titles and display settings.
     * Entry requirements, combat rules, spawn points, dungeon setup and effects stay on the server.
     */
    public ZoneDef publicView() {
        ZoneFeatures shown = ZoneFeatures.DEFAULT.withMessages(features.messages()).withDisplay(features.display())
                .withType(features.type());
        return new ZoneDef(id, name, dimension, areas, levelMin, levelMax, priority, enabled, danger, recommendedMin,
                recommendedMax, 1.0, 0, safe, excludedAreas, revision, ZoneCombatRules.inherit(), List.of(), shown);
    }

    /** Sorts zones the way the manager lists them: priority then id. */
    public static int compare(ZoneDef a, ZoneDef b) {
        int byPriority = Integer.compare(b.priority(), a.priority());
        return byPriority != 0 ? byPriority : a.id().compareTo(b.id());
    }

    public enum Danger {
        SAFE, NORMAL, DANGEROUS, DEADLY;

        /** "Dangerous" rather than "DANGEROUS" for admin lists and hints. */
        public String label() {
            String lower = name().toLowerCase(java.util.Locale.ROOT);
            return net.schwarz.rotasutils.util.ThaiText.label("zone_danger", this,
                    Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }

        public Danger next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Danger parse(String value, Danger fallback) {
            try {
                return value == null || value.isEmpty() ? fallback : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                return fallback;
            }
        }
    }
}
