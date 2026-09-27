package net.schwarz.rotasutils.level;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-wide level, rank and experience settings, all editable from the level manager UI. */
public final class LevelConfig {
    public enum FirstJoinMode { NONE, CHOOSE, ASSIGN }
    /** Increment when defaults/semantics change so old development worlds can migrate safely. */
    public static final int CURRENT_PROGRESSION_VERSION = 5;

    /** Built from the season rules' main curve; never stored on its own. */
    private final LevelCurve curve = new LevelCurve();
    /** Season design numbers; also mirrored to config/rotasutils/season.json for hand editing. */
    private SeasonRules season = new SeasonRules();

    public SeasonRules season() { return season; }

    public void setSeason(SeasonRules value) {
        season = value == null ? new SeasonRules() : value.sanitize();
        curve.set(season);
    }
    private int startingLevel = 1;
    /** Amount granted each time the configured interval is reached. */
    private int skillPointsPerLevel = 1;
    /** Sends Rotas skill points to Pufferfish Skills instead of the built-in job and race trees. */
    private boolean pufferfishSkills;

    public boolean pufferfishSkills() { return pufferfishSkills; }
    public void setPufferfishSkills(boolean value) { pufferfishSkills = value; }

    /** 2 means one point grant every two Rotas levels. */
    private int skillPointInterval = 2;

    /** Dynamic monster XP tuning. The entity's live combat attributes are the source of truth. */
    private double monsterXpScale = 0.65;
    private double bossXpMultiplier = 3.0;
    private double moddedMobXpMultiplier = 1.15;
    private int minimumMonsterXp = 2;
    private int maximumMonsterXp = 5000;
    /** Exact entity-id overrides, e.g. cataclysm:ender_guardian -> 1800. */
    private final Map<String, Integer> monsterXpOverrides = new LinkedHashMap<>();
    private boolean rankRequirementsEnabled = true;
    private boolean announceLevelUp = true;
    private boolean showLevelUpToast = true;
    /** Point mode: global pool, category pools, or both. */
    private PointMode pointMode = PointMode.GLOBAL;
    /** Universal per-mob leveling plus the zone/spawn ramp band it reads. */
    private MobLevelConfig mobLevel = new MobLevelConfig();
    private MonsterXpWeights monsterXpWeights = new MonsterXpWeights();
    private AdventureXpConfig adventureXp = new AdventureXpConfig();
    private FirstJoinMode firstJoinMode=FirstJoinMode.CHOOSE;
    private String firstJoinMainJob="", firstJoinSubJob="";

    public FirstJoinMode firstJoinMode(){return firstJoinMode;}
    public void setFirstJoinMode(FirstJoinMode value){firstJoinMode=value==null?FirstJoinMode.CHOOSE:value;}
    public String firstJoinMainJob(){return firstJoinMainJob;}
    public void setFirstJoinMainJob(String value){firstJoinMainJob=jobId(value);}
    public String firstJoinSubJob(){return firstJoinSubJob;}
    public void setFirstJoinSubJob(String value){firstJoinSubJob=jobId(value);}
    private static String jobId(String value){value=value==null?"":value.trim();if(!value.isEmpty()&&!value.matches("[a-z0-9_]{1,32}"))throw new IllegalArgumentException("Invalid first-join job id");return value;}

    private final EnumMap<DangerRank, Integer> rankLevel = new EnumMap<>(DangerRank.class);
    private final EnumMap<DangerRank, Float> rankMultiplier = new EnumMap<>(DangerRank.class);
    /** How many quests of the rank below must be cleared before clearance unlocks. */
    private final EnumMap<DangerRank, Integer> rankQuestsRequired = new EnumMap<>(DangerRank.class);
    private final EnumMap<DangerRank, String> rankPromotionQuest = new EnumMap<>(DangerRank.class);
    private final EnumMap<DangerRank, Boolean> rankAutoGrant = new EnumMap<>(DangerRank.class);

    private final Map<XpSource, XpSourceConfig> sources = new LinkedHashMap<>();
    /** level -> rewards granted on reaching it. */
    private final Map<Integer, List<Reward>> levelRewards = new LinkedHashMap<>();

    public LevelConfig() {
        for (DangerRank rank : DangerRank.VALUES) {
            rankLevel.put(rank, rank.defaultLevel());
            rankMultiplier.put(rank, rank.defaultMultiplier());
            rankQuestsRequired.put(rank, rank.ordinal() == 0 ? 0 : 3);
            rankPromotionQuest.put(rank, "");
            rankAutoGrant.put(rank, true);
        }
        for (XpSource source : XpSource.VALUES) {
            sources.put(source, new XpSourceConfig(source));
        }
    }

    public LevelCurve curve() {
        return curve;
    }

    public int startingLevel() {
        return startingLevel;
    }

    public void setStartingLevel(int startingLevel) {
        this.startingLevel = Math.max(1, startingLevel);
    }

    public int skillPointsPerLevel() {
        return skillPointsPerLevel;
    }

    public void setSkillPointsPerLevel(int value) {
        this.skillPointsPerLevel = Math.max(0, value);
    }

    public int skillPointInterval() {
        return skillPointInterval;
    }

    public void setSkillPointInterval(int value) {
        this.skillPointInterval = Math.max(1, value);
    }

    public double monsterXpScale() {
        return monsterXpScale;
    }

    public void setMonsterXpScale(double value) {
        this.monsterXpScale = clampFinite(value, 0.01, 100.0, 0.65);
    }

    public double bossXpMultiplier() {
        return bossXpMultiplier;
    }

    public void setBossXpMultiplier(double value) {
        this.bossXpMultiplier = clampFinite(value, 1.0, 100.0, 3.0);
    }

    public double moddedMobXpMultiplier() {
        return moddedMobXpMultiplier;
    }

    public void setModdedMobXpMultiplier(double value) {
        this.moddedMobXpMultiplier = clampFinite(value, 0.1, 20.0, 1.15);
    }

    /**
     * Clamps into a range, falling back for a non-finite value. Plain {@code Math.max/min} passes
     * NaN straight through, so a NaN in a save file or a save packet would poison every XP payout.
     */
    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) { return fallback; }
        return Math.max(min, Math.min(max, value));
    }

    public int minimumMonsterXp() {
        return minimumMonsterXp;
    }

    public void setMinimumMonsterXp(int value) {
        this.minimumMonsterXp = Math.max(0, value);
        if (maximumMonsterXp < minimumMonsterXp) {
            maximumMonsterXp = minimumMonsterXp;
        }
    }

    public int maximumMonsterXp() {
        return maximumMonsterXp;
    }

    public void setMaximumMonsterXp(int value) {
        this.maximumMonsterXp = Math.max(minimumMonsterXp, value);
    }

    public int monsterXpOverride(String entityId) {
        return monsterXpOverrides.getOrDefault(entityId, -1);
    }

    public Map<String, Integer> monsterXpOverrides() {
        return monsterXpOverrides;
    }

    public void setMonsterXpOverride(String entityId, int xp) {
        if (entityId == null || entityId.isBlank() || xp < 0) {
            if (entityId != null) {
                monsterXpOverrides.remove(entityId);
            }
            return;
        }
        monsterXpOverrides.put(entityId, Math.min(Integer.MAX_VALUE, xp));
    }

    public boolean rankRequirementsEnabled() {
        return rankRequirementsEnabled;
    }

    public void setRankRequirementsEnabled(boolean value) {
        this.rankRequirementsEnabled = value;
    }

    public boolean announceLevelUp() {
        return announceLevelUp;
    }

    public void setAnnounceLevelUp(boolean value) {
        this.announceLevelUp = value;
    }

    public boolean showLevelUpToast() {
        return showLevelUpToast;
    }

    public void setShowLevelUpToast(boolean value) {
        this.showLevelUpToast = value;
    }

    public PointMode pointMode() {
        return pointMode;
    }

    public void setPointMode(PointMode pointMode) {
        this.pointMode = pointMode;
    }

    public MobLevelConfig mobLevel() {
        return mobLevel;
    }

    public void setMobLevel(MobLevelConfig value) {
        this.mobLevel = value == null ? new MobLevelConfig() : value;
    }

    public MonsterXpWeights monsterXpWeights() {
        return monsterXpWeights;
    }

    public void setMonsterXpWeights(MonsterXpWeights value) {
        this.monsterXpWeights = value == null ? new MonsterXpWeights() : value;
    }

    public AdventureXpConfig adventureXp() { return adventureXp; }

    public void setAdventureXp(AdventureXpConfig value) {
        adventureXp = value == null ? new AdventureXpConfig() : value;
    }

    public int rankLevel(DangerRank rank) {
        return rankLevel.getOrDefault(rank, rank.defaultLevel());
    }

    public void setRankLevel(DangerRank rank, int level) {
        rankLevel.put(rank, Math.max(1, level));
    }

    public float rankMultiplier(DangerRank rank) {
        return rankMultiplier.getOrDefault(rank, rank.defaultMultiplier());
    }

    public void setRankMultiplier(DangerRank rank, float multiplier) {
        rankMultiplier.put(rank, Math.max(0f, multiplier));
    }

    public int rankQuestsRequired(DangerRank rank) {
        return rankQuestsRequired.getOrDefault(rank, 0);
    }

    public void setRankQuestsRequired(DangerRank rank, int amount) {
        rankQuestsRequired.put(rank, Math.max(0, amount));
    }

    public String rankPromotionQuest(DangerRank rank) {
        return rankPromotionQuest.getOrDefault(rank, "");
    }

    public void setRankPromotionQuest(DangerRank rank, String questId) {
        rankPromotionQuest.put(rank, questId == null ? "" : questId);
    }

    public boolean rankAutoGrant(DangerRank rank) {
        return rankAutoGrant.getOrDefault(rank, true);
    }

    public void setRankAutoGrant(DangerRank rank, boolean value) {
        rankAutoGrant.put(rank, value);
    }

    public XpSourceConfig source(XpSource source) {
        return sources.computeIfAbsent(source, XpSourceConfig::new);
    }

    public Map<XpSource, XpSourceConfig> sources() {
        return sources;
    }

    public List<Reward> levelRewards(int level) {
        return levelRewards.getOrDefault(level, List.of());
    }

    public Map<Integer, List<Reward>> allLevelRewards() {
        return levelRewards;
    }

    public void setLevelRewards(int level, List<Reward> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            levelRewards.remove(level);
        } else {
            levelRewards.put(level, new ArrayList<>(rewards));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("progression_version", CURRENT_PROGRESSION_VERSION);
        tag.putString("season", season.toJson());
        tag.putInt("starting_level", startingLevel);
        tag.putInt("points_per_level", skillPointsPerLevel);
        tag.putBoolean("pufferfish_skills", pufferfishSkills);
        tag.putInt("point_interval", skillPointInterval);
        tag.putDouble("monster_xp_scale", monsterXpScale);
        tag.putDouble("boss_xp_mult", bossXpMultiplier);
        tag.putDouble("modded_mob_xp_mult", moddedMobXpMultiplier);
        tag.putInt("monster_xp_min", minimumMonsterXp);
        tag.putInt("monster_xp_max", maximumMonsterXp);
        CompoundTag monsterOverrides = new CompoundTag();
        monsterXpOverrides.forEach(monsterOverrides::putInt);
        tag.put("monster_xp_overrides", monsterOverrides);
        tag.putBoolean("rank_requirements", rankRequirementsEnabled);
        tag.putBoolean("announce", announceLevelUp);
        tag.putBoolean("toast", showLevelUpToast);
        tag.putString("point_mode", pointMode.name());

        CompoundTag ranks = new CompoundTag();
        for (DangerRank rank : DangerRank.VALUES) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("level", rankLevel(rank));
            entry.putFloat("mult", rankMultiplier(rank));
            entry.putInt("quests", rankQuestsRequired(rank));
            entry.putString("promotion", rankPromotionQuest(rank));
            entry.putBoolean("auto", rankAutoGrant(rank));
            ranks.put(rank.name(), entry);
        }
        tag.put("ranks", ranks);
        tag.put("sources", Nbt.saveList(sources.values(), XpSourceConfig::save));
        tag.put("mob_level", mobLevel.save());
        tag.put("monster_xp_weights", monsterXpWeights.save());
        tag.put("adventure_xp", adventureXp.save());
        tag.putString("first_join_mode",firstJoinMode.name()); tag.putString("first_join_main_job",firstJoinMainJob); tag.putString("first_join_sub_job",firstJoinSubJob);

        CompoundTag rewards = new CompoundTag();
        levelRewards.forEach((level, list) -> {
            CompoundTag holder = new CompoundTag();
            holder.put("rewards", Nbt.saveList(list, Reward::save));
            rewards.put(String.valueOf(level), holder);
        });
        tag.put("level_rewards", rewards);
        return tag;
    }

    public static LevelConfig load(CompoundTag tag) {
        LevelConfig config = new LevelConfig();
        int storedVersion = tag.getInt("progression_version");
        config.pufferfishSkills = tag.getBoolean("pufferfish_skills");
        if (tag.contains("season")) {
            try {
                config.season = SeasonRules.fromJson(tag.getString("season"));
            } catch (RuntimeException malformed) {
                // A broken stored copy falls back to the defaults instead of blocking the world load.
                config.season = new SeasonRules();
            }
        }
        config.curve.set(config.season);
        config.startingLevel = Math.max(1, tag.contains("starting_level") ? tag.getInt("starting_level") : 1);
        config.skillPointsPerLevel = tag.contains("points_per_level") ? Math.max(0, tag.getInt("points_per_level")) : 1;
        config.skillPointInterval = tag.contains("point_interval") ? Math.max(1, tag.getInt("point_interval")) : 2;
        // Through the setters, not the fields: this tag also arrives straight from the admin screen's
        // save packet, so reading it raw let a value outside the setters' range - including a
        // negative one or a NaN - into the live XP economy and back out to disk.
        config.setMonsterXpScale(tag.contains("monster_xp_scale") ? tag.getDouble("monster_xp_scale") : 0.65);
        config.setBossXpMultiplier(tag.contains("boss_xp_mult") ? tag.getDouble("boss_xp_mult") : 3.0);
        config.setModdedMobXpMultiplier(tag.contains("modded_mob_xp_mult")
                ? tag.getDouble("modded_mob_xp_mult") : 1.15);
        config.minimumMonsterXp = tag.contains("monster_xp_min") ? Math.max(0, tag.getInt("monster_xp_min")) : 2;
        config.maximumMonsterXp = tag.contains("monster_xp_max")
                ? Math.max(config.minimumMonsterXp, tag.getInt("monster_xp_max")) : 5000;
        CompoundTag monsterOverrides = tag.getCompound("monster_xp_overrides");
        for (String entityId : monsterOverrides.getAllKeys()) {
            config.monsterXpOverrides.put(entityId, Math.max(0, monsterOverrides.getInt(entityId)));
        }
        config.rankRequirementsEnabled = !tag.contains("rank_requirements") || tag.getBoolean("rank_requirements");
        config.announceLevelUp = !tag.contains("announce") || tag.getBoolean("announce");
        config.showLevelUpToast = !tag.contains("toast") || tag.getBoolean("toast");
        config.pointMode = Nbt.readEnum(tag, "point_mode", PointMode.class, PointMode.GLOBAL);
        if (tag.contains("mob_level")) {
            config.mobLevel = MobLevelConfig.load(tag.getCompound("mob_level"));
            config.monsterXpWeights = MonsterXpWeights.load(tag.getCompound("monster_xp_weights"));
        }
        if (tag.contains("adventure_xp")) {
            config.adventureXp = AdventureXpConfig.load(tag.getCompound("adventure_xp"));
        }
        config.firstJoinMode=Nbt.readEnum(tag,"first_join_mode",FirstJoinMode.class,FirstJoinMode.CHOOSE);
        config.setFirstJoinMainJob(tag.getString("first_join_main_job")); config.setFirstJoinSubJob(tag.getString("first_join_sub_job"));

        CompoundTag ranks = tag.getCompound("ranks");
        for (DangerRank rank : DangerRank.VALUES) {
            if (!ranks.contains(rank.name())) {
                continue;
            }
            CompoundTag entry = ranks.getCompound(rank.name());
            config.rankLevel.put(rank, entry.getInt("level"));
            config.rankMultiplier.put(rank, entry.getFloat("mult"));
            config.rankQuestsRequired.put(rank, entry.getInt("quests"));
            config.rankPromotionQuest.put(rank, entry.getString("promotion"));
            config.rankAutoGrant.put(rank, entry.getBoolean("auto"));
        }
        List<XpSourceConfig> loadedSources = Nbt.loadList(tag, "sources", XpSourceConfig::load);
        boolean legacyEverythingEnabled = storedVersion < 2
                && !loadedSources.isEmpty()
                && loadedSources.stream().allMatch(XpSourceConfig::enabled);
        for (XpSourceConfig loaded : loadedSources) {
            config.sources.put(loaded.source(), loaded);
        }
        if (legacyEverythingEnabled) {
            // Old builds exposed many XP sources that had no event hook. Migrate the old
            // all-enabled default into the combat-only policy instead of pretending they work.
            for (XpSource source : XpSource.VALUES) {
                config.source(source).setEnabled(source.enabledByDefault());
            }
        }
        if (storedVersion < 5) {
            // Version 5 hooked discovery and advancements up; turn them on in worlds made before they worked.
            config.source(XpSource.DISCOVERY).setEnabled(true);
            config.source(XpSource.ADVANCEMENT).setEnabled(true);
        }
        CompoundTag rewards = tag.getCompound("level_rewards");
        for (String key : rewards.getAllKeys()) {
            try {
                int level = Integer.parseInt(key);
                config.levelRewards.put(level, Nbt.loadList(rewards.getCompound(key), "rewards", Reward::load));
            } catch (NumberFormatException ignored) {
                // Stale key from an older format; drop it rather than failing the world load.
            }
        }
        return config;
    }

    public enum PointMode {
        GLOBAL("Global points"),
        CATEGORY("Category points"),
        BOTH("Global and category points");

        public static final PointMode[] VALUES = values();
        private final String display;

        PointMode(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("point_mode", this, display);
        }
    }
}
