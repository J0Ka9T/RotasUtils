package net.schwarz.rotasutils.level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tunable number of the season design: job levels, monster and craft EXP, party sharing,
 * rank points, character stats and the horse system.
 *
 * <p>The whole object round-trips through JSON so admins can edit {@code config/rotasutils/season.json}
 * and reload without a rebuild. Fields are public on purpose: this is a plain settings bag, and
 * {@link #sanitize()} clamps anything a hand-edited file gets wrong before it is used.</p>
 */
public final class SeasonRules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Off restores the pre-season behaviour everywhere these rules hook in. */
    public boolean enabled = true;
    /** Currency used for gacha, respec, stable slots and horse sales. */
    public String currency = "rotas:gold";

    // Main job and sub job curves ---------------------------------------------------------------
    public double mainBaseXp = 25;
    public double mainExponent = 2.1;
    public int mainMaxLevel = 100;
    /**
     * When on (the default) the main curve is set by pacing: each level costs a target number of kills of a
     * same-level monster (see {@link LevelPacing}), and {@code mainBaseXp}/{@code mainExponent} are ignored.
     */
    public boolean pacingEnabled = true;
    /** Kills of a same-level monster for the first level-up, and for the last. */
    public double killsAtStart = 8;
    public double killsAtMax = 120;
    /** Shape of the rise between them: 1 is a straight line, above 1 keeps the early levels quicker. */
    public double killsCurve = 1.5;
    /** EXP a level-1 monster pays before its level bonus; the yardstick pacing is measured in. */
    public double referenceMonsterXp = 20;
    /** Characters below this level earn extra EXP, most at level 1, fading to nothing here. */
    public int catchUpLevel = 30;
    public double catchUpBonus = 0.5;
    public double subBaseXp = 90;
    public double subExponent = 1.8;
    public int subMaxLevel = 20;
    /** Sub-job EXP comes from production only, never from combat. */
    public boolean subJobProductionOnly = true;

    // Monster EXP ---------------------------------------------------------------------------------
    public double monsterLevelBonus = 0.15;
    public int overLevelGrace = 5;
    public double overLevelPenaltyPerLevel = 0.10;
    public double overLevelMaxPenalty = 0.90;
    public double minibossMultiplier = 10;
    public double bossMultiplier = 40;

    // Party ---------------------------------------------------------------------------------------
    /** Bonus added to the pool for every member after the first, before the equal split. */
    public double partyBonusPerMember = 0.25;
    public double partyRadius = 40;
    public int partyMaxSize = 5;
    /** A member earns as if the monster were at most this many levels above them (stops carries). */
    public int partyLevelReach = 10;

    // Overflow currency --------------------------------------------------------------------------
    /** EXP a maxed sub job cannot use turns into this currency. */
    public String overflowCurrency = "rotas:season_token";
    public long overflowXpPerToken = 1000;

    // Repeatable quests ---------------------------------------------------------------------------
    public int repeatableFullRuns = 15;
    public int repeatableHalfRuns = 30;
    public double repeatableHalfRate = 0.5;
    public double repeatableLowRate = 0.1;

    // Production (sub job) EXP --------------------------------------------------------------------
    /** Base EXP per tier A, B, C, D. */
    public long[] tierXp = {25, 150, 420, 830};
    /** Highest sub-job level of tier A, B, C, D. */
    public int[] tierMaxLevel = {4, 9, 14, 19};
    public int craftGraceLevels = 3;
    public double craftPenaltyPerLevel = 0.15;
    public double craftMaxPenalty = 0.90;
    public double firstCraftMultiplier = 3;
    /** Items in a job's unlock table can only be taken from a crafting result at the unlock level. */
    public boolean lockRecipes = true;
    /** EXP a capped sub job would have earned turns into overflow currency at this rate. */
    public boolean subCapToOverflow = true;

    // Sub-role gates ------------------------------------------------------------------------------
    /** Smelting results in a job's unlock table can only be taken by that sub job at the unlock level. */
    public boolean lockSmelting = true;
    /** Brewed potions in a job's unlock table can only be taken by that sub job at the unlock level. */
    public boolean lockBrewing = true;
    /** Level 1 rows are sealed too, so only the sub-role makes even the starter items. */
    public boolean lockStarterRows = true;
    /** Gathering rows at or below this unlock level drop normally for everyone. */
    public int gatherFreeLevel = 1;
    /** Chance each drop stack survives for a player without the sub-role that gathers it. */
    public double gatherDropChance = 0.35;
    /** Most items a surviving drop stack keeps for a player without the sub-role (no Fortune bonus). */
    public int gatherMaxCount = 1;
    /** What a player without the Fisher role pulls up when a sealed catch slips away. */
    public String gatherFishFallback = "minecraft:cod";
    /** Artisan NPC fee per craft of tier A, B, C, D, before the price multiplier. */
    public long[] crafterFee = {80, 400, 1200, 3000};
    public double crafterPriceMultiplier = 3.0;
    /** Crafts one player may commission per day across every artisan. 0 means no limit. */
    public int crafterDailyLimit = 5;
    /** Artisans show no marker over their heads, so players have to find them. */
    public boolean crafterHideMarker = true;

    // Ranks ---------------------------------------------------------------------------------------
    public Map<String, Integer> rankExp = defaultRankExp();
    public int repeatableRankCapPerDay = 10;
    /** Rank points available across the season without repeatables; thresholds are shares of it. */
    public long seasonRankTotal = 6900;
    public Map<String, Double> rankThresholds = defaultThresholds();
    public Map<String, RankPerk> rankPerks = defaultPerks();

    // Character stats -----------------------------------------------------------------------------
    /** The four fixed stats (STR, VIT, INT, AGI) and the points that buy them. */
    public StatRules stats = new StatRules();
    /** Damage taken is multiplied by scale / (scale + defense). */
    public double defenseScale = 100;

    /*
     * Combat balance. Level does not change a hit: a player's level gives stat points and nothing else, and a
     * monster's level only grows that monster's own health and damage. The PvP values keep player fights from
     * being decided by one stacked stat, a dodge roll or a single hit.
     */
    /** Every player-versus-player hit is multiplied by this, so fights last several exchanges. */
    public double pvpDamageMultiplier = 0.6;
    /** Share of the attacker's stat attack bonus that counts in PvP (1 = all of it). */
    public double pvpStatEfficiency = 0.5;
    /** Evasion is multiplied by this against player attacks. */
    public double pvpEvasionScale = 0.5;
    /** Largest share of the victim's max health one PvP hit may deal; 0 turns the cap off. */
    public double pvpMaxHitShare = 0.35;
    /** Magic attack's flat bonus on one hit is capped at this multiple of the hit; 0 turns the cap off. */
    public double magicBonusMaxRatio = 1.0;


    // Monster drops -------------------------------------------------------------------------------
    /** What a monster leaves when it dies, by rank, and what is inside each grade of drop box. */
    public DropRules drops = new DropRules();

    // Horses --------------------------------------------------------------------------------------
    public HorseRules horse = new HorseRules();

    // Farming -------------------------------------------------------------------------------------
    /** Luck, kill combos and elite monsters: what makes an hour of farming feel like progress. */
    public FarmingRules farming = new FarmingRules();
    /** The monster book: kill a kind enough and you learn how to fight it. */
    public BestiaryRules bestiary = new BestiaryRules();
    /** Breaking unwanted gear down into refine materials and gold. */
    public SalvageRules salvage = new SalvageRules();
    /** Monsters that kill a player remember it, grow, and come back. */
    public NemesisRules nemesis = new NemesisRules();
    /** Who may earn titles automatically. */
    public TitleRules titles = new TitleRules();
    /** Prices and offers of the service NPCs: smith, alchemist, inn, priest, bank, bounties, collector, auction. */
    public NpcServiceRules npcServices = new NpcServiceRules();
    /** NPCs as people: friendship, hired companions and the rumours they pass on. */
    public NpcSocialRules npcSocial = new NpcSocialRules();

    public static final class NpcSocialRules {
        public boolean friendship = true;
        /** Points for the first talk with an NPC each day, and for each paid service it performs. */
        public int chatPoints = 3;
        public int servicePoints = 2;
        /** Service points one NPC can give one player per day. */
        public int servicePointsPerDay = 10;
        /** Points needed for each friendship level after Stranger. */
        public int[] levels = {30, 100, 250, 500};
        /** Discount on that NPC's services per friendship level. */
        public double discountPerLevel = 0.03;
        public boolean companions = true;
        public long companionCost = 300;
        public int companionMinutes = 10;
        /** The hired fighter; iron golems fight monsters on their own and never turn on players. */
        public String companionEntity = "minecraft:iron_golem";
        /** Companion health and damage grow by this per level of the one who hired it. */
        public double companionPerLevel = 0.02;
        public boolean rumors = true;
        public int rumorsShown = 5;
    }

    /** Rewards and celebration every few levels, so a level feels like more than a number. */
    public MilestoneRules milestones = new MilestoneRules();
    /** EXP for seeing the world: zones, waystones, new monsters, advancements, distance travelled. */
    public ExplorationRules exploration = new ExplorationRules();

    public static final class MilestoneRules {
        public boolean enabled = true;
        /** A milestone every this many levels. */
        public int every = 5;
        /** Gold at a milestone: level × this. */
        public long goldPerLevel = 20;
        /** Extra stat points at every milestone. */
        public int statPoints = 1;
        /** Levels that are big milestones: announced to the server, paid {@link #bigMultiplier} times more. */
        public int[] bigLevels = {10, 25, 50, 75, 100};
        public double bigMultiplier = 5;
        public int bigStatPoints = 3;
        /** Big screen title and a burst of light on level-up. */
        public boolean celebrate = true;
    }

    public static final class ExplorationRules {
        public boolean enabled = true;
        /** Discovery EXP grows with the player's level so it stays worth something: base × (1 + level × this). */
        public double levelScale = 0.05;
        /** First time in a zone: base EXP, plus this per recommended zone level. */
        public long zoneBase = 120;
        public long zonePerLevel = 8;
        public long waystoneXp = 150;
        /** First kill of a monster kind (a new bestiary entry). */
        public long newMonsterXp = 60;
        /** Vanilla advancements by frame: task, goal, challenge. Recipe unlocks give nothing. */
        public long[] advancementXp = {40, 150, 500};
        /** Blocks walked, ridden or flown between two distance rewards (teleports do not count). */
        public int travelBlocks = 1000;
        public long travelXp = 80;
        /** Variety: each other kind of activity done in the window adds this to all EXP, up to the cap. */
        public double varietyBonus = 0.05;
        public double varietyMax = 0.25;
        public int varietyWindowMinutes = 30;
    }

    /** One potion an alchemist brews on the spot. */
    public static final class Brew {
        public String effect = "minecraft:speed";
        public int amplifier = 0;
        public int seconds = 480;
        public long price = 60;

        public Brew() {
        }

        public Brew(String effect, int amplifier, int seconds, long price) {
            this.effect = effect;
            this.amplifier = amplifier;
            this.seconds = seconds;
            this.price = price;
        }
    }

    /** An item the collector buys. {@code price} is per item; today's picks pay {@code collectorBonus} times more. */
    public static final class Wanted {
        public String item = "minecraft:wheat";
        public long price = 2;

        public Wanted() {
        }

        public Wanted(String item, long price) {
            this.item = item;
            this.price = price;
        }
    }

    /** A bounty contract: kill {@code count} of {@code entity} (an id or #tag) for gold and EXP. */
    public static final class BountyDef {
        public String entity = "minecraft:zombie";
        public int count = 15;
        public long gold = 120;
        public long xp = 200;
        public int minLevel = 1;

        public BountyDef() {
        }

        public BountyDef(String entity, int count, long gold, long xp, int minLevel) {
            this.entity = entity;
            this.count = count;
            this.gold = gold;
            this.xp = xp;
            this.minLevel = minLevel;
        }
    }

    public static final class NpcServiceRules {
        // Blacksmith
        /** Gold per point of durability restored. */
        public double repairPerDurability = 0.4;
        public long repairMinCost = 10;
        // Enchanter
        public long disenchantBaseCost = 150;
        public long disenchantPerLevel = 60;
        // Alchemist
        public Brew[] brews = {
                new Brew("minecraft:speed", 0, 480, 60),
                new Brew("minecraft:haste", 0, 480, 80),
                new Brew("minecraft:strength", 0, 180, 150),
                new Brew("minecraft:regeneration", 0, 90, 120),
                new Brew("minecraft:night_vision", 0, 480, 40),
                new Brew("minecraft:fire_resistance", 0, 480, 90),
                new Brew("minecraft:water_breathing", 0, 480, 60),
                new Brew("minecraft:luck", 0, 600, 200)};
        // Innkeeper
        public long restCost = 40;
        /** Minutes of Rested (bonus EXP from every source) a night at the inn gives. */
        public int restedMinutes = 30;
        public double restedXp = 0.10;
        public long homeCost = 100;
        // Priest
        public long cleanseCost = 30;
        public long blessingCost = 150;
        public int blessingMinutes = 10;
        public long liftCurseCost = 500;
        /** Free prayer once a day: heals and gives a short blessing. */
        public boolean dailyPrayer = true;
        // Fortune teller
        public long fortuneCost = 100;
        public int fortuneMinutes = 60;
        /** Bonus of the fortune's favoured activity; a bad omen gives nothing but the reading. */
        public double fortuneXp = 0.20;
        public double badOmenChance = 0.10;
        // Banker
        public long[] withdrawSteps = {100, 1000, 10000};
        // Bounty master
        public BountyDef[] bounties = {
                new BountyDef("minecraft:zombie", 15, 120, 200, 1),
                new BountyDef("minecraft:skeleton", 15, 140, 220, 1),
                new BountyDef("minecraft:spider", 12, 130, 200, 1),
                new BountyDef("minecraft:creeper", 8, 180, 260, 5),
                new BountyDef("minecraft:witch", 5, 260, 380, 10),
                new BountyDef("minecraft:enderman", 6, 300, 420, 15),
                new BountyDef("minecraft:blaze", 8, 360, 500, 20),
                new BountyDef("minecraft:wither_skeleton", 6, 520, 700, 30)};
        /** Contracts on offer each day, picked from the list by the date so everyone sees the same board. */
        public int bountiesPerDay = 3;
        public int bountiesCompletedPerDay = 3;
        // Collector
        public Wanted[] wanted = {
                new Wanted("minecraft:wheat", 2), new Wanted("minecraft:carrot", 2), new Wanted("minecraft:potato", 2),
                new Wanted("minecraft:beetroot", 3), new Wanted("minecraft:sugar_cane", 2), new Wanted("minecraft:pumpkin", 6),
                new Wanted("minecraft:melon_slice", 1), new Wanted("minecraft:cod", 5), new Wanted("minecraft:salmon", 6),
                new Wanted("minecraft:leather", 6), new Wanted("minecraft:string", 3), new Wanted("minecraft:bone", 3),
                new Wanted("minecraft:gunpowder", 6), new Wanted("minecraft:coal", 3), new Wanted("minecraft:raw_iron", 8),
                new Wanted("minecraft:raw_copper", 3), new Wanted("minecraft:raw_gold", 14), new Wanted("minecraft:lapis_lazuli", 5),
                new Wanted("minecraft:redstone", 3), new Wanted("minecraft:oak_log", 2), new Wanted("minecraft:honeycomb", 12)};
        public int collectorPicksPerDay = 4;
        public double collectorBonus = 1.5;
        /** Items one player may sell the collector per day. */
        public int collectorDailyItems = 256;
        /** Trading EXP per gold earned at the collector. */
        public double collectorXpPerGold = 0.5;
        // Auction house
        public double auctionFee = 0.05;
        public int auctionMaxListings = 8;
        public int auctionHours = 72;
        public long auctionMaxPrice = 100_000_000;
    }

    /**
     * Titles (ฉายา): who the automatic checker considers. Staff testing a server should not claim a
     * unique title before real players get the chance.
     */
    public static final class TitleRules {
        /** Players with admin permission (OP 2+) earn titles automatically. Grants by hand always work. */
        public boolean staffEarnTitles = false;
        /** Players in creative or spectator mode earn titles automatically. */
        public boolean creativeEarnTitles = false;
        /** A title an admin revoked is not earned again automatically by that player. */
        public boolean revokeBlocksReEarn = true;
        /** Collection points per rarity (common, uncommon, rare, epic, legendary); points unlock {@link #collection}. */
        public int[] rarityPoints = {1, 2, 3, 5, 8};
        /** Gold paid once when a title of each rarity is earned. */
        public long[] rarityGold = {50, 150, 400, 1000, 3000};
        /** EXP paid once when a title of each rarity is earned. */
        public long[] rarityXp = {100, 300, 800, 2000, 5000};
        /** Rarity index (0-4) from which an earned title is announced to the whole server. */
        public int announceFromRarity = 3;
        /** A worn legendary title glows faintly around its wearer. */
        public boolean legendaryAura = true;
        /** Permanent bonuses for the whole collection, every reached tier applies, worn title or not. */
        public CollectionTier[] collection = {
                new CollectionTier(5, "minecraft:generic.max_health", 2, "ADD", false, "พลังชีวิต"),
                new CollectionTier(12, "minecraft:generic.attack_damage", 0.02, "MULTIPLY_BASE", true, "พลังโจมตี"),
                new CollectionTier(20, "rotas:defense", 2, "ADD", false, "พลังป้องกัน"),
                new CollectionTier(30, "minecraft:generic.max_health", 0.03, "MULTIPLY_BASE", true, "พลังชีวิต"),
                new CollectionTier(45, "minecraft:generic.movement_speed", 0.03, "MULTIPLY_BASE", true, "ความเร็ว"),
                new CollectionTier(60, "minecraft:generic.attack_damage", 0.03, "MULTIPLY_BASE", true, "พลังโจมตี"),
                new CollectionTier(80, "rotas:defense", 4, "ADD", false, "พลังป้องกัน"),
                new CollectionTier(100, "minecraft:generic.luck", 1, "ADD", false, "โชค")};
    }

    /** One step of the title collection: at {@code points}, the bonus applies for good. */
    public static final class CollectionTier {
        public int points = 10;
        public String attribute = "minecraft:generic.max_health";
        public double amount = 1;
        /** ADD, MULTIPLY_BASE or MULTIPLY_TOTAL. */
        public String operation = "ADD";
        public boolean percent;
        public String label = "";

        public CollectionTier() {
        }

        public CollectionTier(int points, String attribute, double amount, String operation, boolean percent, String label) {
            this.points = points;
            this.attribute = attribute;
            this.amount = amount;
            this.operation = operation;
            this.percent = percent;
            this.label = label;
        }
    }
    /** Timed events that take over a zone or a patch of wilderness and give players somewhere to go. */
    public WorldEventRules worldEvents = new WorldEventRules();
    /** A weapon remembers what it killed and grows a little from it. */
    public WeaponMemoryRules weaponMemory = new WeaponMemoryRules();

    /**
     * Nemeses (ศัตรูคู่แค้น).
     *
     * <p>A hostile mob that kills a player may rise: it gets a name, a few levels, tougher stats, and it
     * stops despawning. Every further player it kills ranks it up. If its body is lost - it burned, it
     * drowned, the chunk was left behind - it is not gone: it waits, and later ambushes the player it
     * last killed. Only a player can end it, and the one who does takes a bounty and a trophy; the victim
     * doing it themselves is revenge, and pays {@link #revengeMultiplier} times as much.</p>
     */
    public static final class NemesisRules {
        public boolean enabled = true;
        /** Chance a mob that kills a player rises as a nemesis. */
        public double riseChance = 0.5;
        /** One player's deaths raise at most one nemesis per this many minutes, so dying is not a bounty farm. */
        public int riseCooldownMinutes = 30;
        /** Nemeses alive on the whole server at once. */
        public int maxActive = 48;
        /** Nemeses hunting one player at once. */
        public int maxPerPlayer = 3;
        public int maxRank = 5;
        /** Levels gained when it rises and on every rank after. */
        public int levelsPerRank = 3;
        /** Extra max health per rank, as a share of the mob's own: 0.5 is x1.5 at rank 1, x3.5 at rank 5. */
        public double healthPerRank = 0.5;
        public double damagePerRank = 0.2;
        /** A nemesis whose body was lost may come looking for the player it last killed. */
        public boolean ambushEnabled = true;
        public int ambushCooldownMinutes = 30;
        /** Chance per minute, once the cooldown is over, that it finds its target. */
        public double ambushChancePerMinute = 0.25;
        public int ambushMinDistance = 16;
        public int ambushMaxDistance = 28;
        /** A nemesis near a player it has killed says so, at most once per this many seconds. */
        public int tauntRadius = 24;
        public int tauntCooldownSeconds = 300;
        /** A nemesis nobody has met for this many days is forgotten. */
        public int forgetAfterDays = 14;
        /** Bounty per rank, paid to whoever slays it. */
        public long goldPerRank = 150;
        public long rankPointsPerRank = 2;
        /** Drop grade by rank, 1-based; the last entry covers every rank above it. */
        public String[] gradeByRank = {"medium", "medium", "rare", "rare", "epic"};
        /** The victim slaying their own nemesis multiplies the bounty. */
        public double revengeMultiplier = 2.0;
        /**
         * The slayer gets a weapon named after the nemesis. It is always a new item of this kind, never a
         * copy of what the mob held, which may be a player's own gear.
         */
        public boolean trophyEnabled = true;
        public String trophyItem = "minecraft:iron_sword";
        /** The trophy of a nemesis that killed with arrows or other projectiles. */
        public String trophyRangedItem = "minecraft:bow";
        /** The trophy starts refined to the nemesis' rank, never past the refine safe level. */
        public boolean trophyRefine = true;
        public String[] names = {"กรัค", "มอร์กัธ", "ซาร์โกธ", "วัลคัส", "ดราเกีย", "โกลธ", "อุซรัก", "เคลธาร์",
                "บรอคส์", "ไวเซอร์", "นาร์กุล", "ฮรอธ", "เซเรธ", "ออร์ซา", "กรีมนาร์", "ทาลุก"};
        /** Epithets by how the kill was made: melee, ranged, magic, fire, explosion, other. */
        public Map<String, String[]> epithets = defaultEpithets();

        static Map<String, String[]> defaultEpithets() {
            Map<String, String[]> map = new LinkedHashMap<>();
            map.put("melee", new String[]{"ผู้บดกระดูก", "ผู้กระหายเลือด", "ผู้ฉีกเนื้อ", "กรงเล็บเหล็ก"});
            map.put("ranged", new String[]{"ธนูเงา", "ผู้เล็งไม่พลาด", "มือสังหารไกล"});
            map.put("magic", new String[]{"ผู้สาปแช่ง", "เงามนตรา", "ผู้กลืนวิญญาณ"});
            map.put("fire", new String[]{"เปลวเพลิงคลั่ง", "ผู้เผาผลาญ"});
            map.put("explosion", new String[]{"ผู้ระเบิดทุกสิ่ง", "เสียงคำรามแห่งหายนะ"});
            map.put("other", new String[]{"ผู้ไร้ปรานี", "เงาที่ไม่ลืม", "ผู้รอคอย"});
            return map;
        }
    }

    /**
     * One kind of world event (เหตุการณ์โลก).
     *
     * <p>While it runs, mobs spawning inside it are levelled and promoted by these numbers, players inside
     * it are paid by them, and - when the event has a goal - everyone who helped reach it is paid the
     * reward once. Mobs listed in {@link #spawns} are brought in waves around players inside, so an event
     * always has something to fight.</p>
     */
    public static final class WorldEventDef {
        public String name = "";
        public String description = "";
        public int weight = 10;
        public int mobLevelBonus = 0;
        /** Multiplies the elite and champion chances of mobs levelled inside. */
        public double eliteMultiplier = 1.0;
        public double mobHealth = 1.0;
        public double mobDamage = 1.0;
        public double xpMultiplier = 1.0;
        public double lootMultiplier = 1.0;
        public double coinMultiplier = 1.0;
        public double healingMultiplier = 1.0;
        /** Chance a broken ore inside drops its ore once more. */
        public double oreBonusChance = 0.0;
        /** Potion effects kept on players inside, {@code "minecraft:speed 1"} (level 1-5). */
        public String[] playerEffects = {};
        /** Only starts at night, and ends at sunrise. */
        public boolean nightOnly = false;
        /** Mobs brought in waves around players inside. */
        public String[] spawns = {};
        public int waveSize = 3;
        public int waveSeconds = 30;
        /** Wave mobs alive around one player at most. */
        public int maxAlive = 12;
        /** What counts toward the goal: KILL (hostile kills inside), MINE (ores broken inside) or NONE. */
        public String goal = "NONE";
        public int goalCount = 0;
        /** A player has to have done at least this much of the goal to share the reward. */
        public int minContribution = 3;
        public TrackReward reward = new TrackReward();

        public WorldEventDef() { }
    }

    /**
     * World events.
     *
     * <p>Every {@link #intervalMinutes}, while players are online and fewer than {@link #maxActive}
     * events run, one may start: in a random enabled zone that is not safe, or - when the server has no
     * such zone - in a circle of {@link #wildRadius} blocks near an online player. It is announced with
     * where it is, shown as a boss bar to everyone inside, and ends when its time runs out or its goal
     * is met.</p>
     */
    public static final class WorldEventRules {
        public boolean enabled = true;
        public int intervalMinutes = 40;
        public double startChance = 0.6;
        public int durationMinutes = 20;
        public int maxActive = 2;
        public int wildRadius = 80;
        /** A wilderness event is placed this far from the player it was placed for. */
        public int wildMinDistance = 96;
        public int wildMaxDistance = 192;
        public Map<String, WorldEventDef> types = defaultWorldEvents();
        public static final int MAX_TYPES = 32;
    }

    /**
     * Weapon memory (ความทรงจำอาวุธ).
     *
     * <p>A weapon counts the monsters it killed, on the item itself. Each milestone reached is a rank, and
     * every rank is worth a little more damage from that weapon. The kind of monster it killed most is
     * its favoured prey, and from {@link #favoredFromRank} it hits that kind harder. The bonuses are small
     * on purpose: the point is that an old weapon is worth keeping, not that it replaces refinement.</p>
     */
    public static final class WeaponMemoryRules {
        public boolean enabled = true;
        public long[] milestones = {50, 250, 1000, 5000};
        public double damagePerRank = 0.02;
        public double favoredBonus = 0.05;
        public int favoredFromRank = 2;
        /** A rank at or above this is announced to the whole server. */
        public int announceFrom = 4;
        /** Kinds of monster one weapon remembers; the least-killed is forgotten first. */
        public static final int MAX_KINDS = 8;
    }

    /**
     * The farming loop.
     *
     * <p>Luck is the player's own {@code generic.luck} attribute - cards, potions and gear already raise
     * it - and every point makes loot and cards a little likelier. A combo is a chain of kills with no
     * more than {@link #comboWindowSeconds} between them; it grows experience and loot chance up to a cap,
     * and breaks the moment the chain does. Elite and champion monsters are ordinary mobs promoted at
     * spawn: tougher, marked, and paid on their rank's better drop rules.</p>
     */
    public static final class FarmingRules {
        public boolean luckEnabled = true;
        /** Relative loot chance each luck point adds; 0.10 means +10% of the base chance. */
        public double lootChancePerLuck = 0.10;
        public double cardChancePerLuck = 0.05;
        /** Most luck can add, as a share of the base chance. 1.0 doubles it at most. */
        public double luckBonusCap = 1.0;

        public boolean comboEnabled = true;
        public int comboWindowSeconds = 8;
        public double comboXpPerKill = 0.02;
        public double comboLootPerKill = 0.01;
        /** The bonus stops growing at this many kills; the count itself keeps going for bragging rights. */
        public int comboCap = 25;

        public boolean eliteEnabled = true;
        /** Chance a naturally spawning, levelled mob is a veteran: a little tougher, no affixes. */
        public double veteranChance = 0.10;
        public double veteranHealth = 1.4;
        public double veteranDamage = 1.15;
        /** Chance a naturally spawning, levelled mob is promoted to elite. */
        public double eliteChance = 0.03;
        /** Chance it is promoted to champion instead; rolled first. */
        public double championChance = 0.004;
        public double eliteHealth = 2.0;
        public double eliteDamage = 1.4;
        public double championHealth = 4.0;
        public double championDamage = 1.8;
        public long eliteRankPoints = 1;
        public long championRankPoints = 3;
        /** Experience a ranked kill is worth, as a multiple of a normal mob of the same level. */
        public double veteranXp = 1.5;
        public double eliteXp = 2.5;
        public double championXp = 6.0;
        /** Built-in affixes (vampiric, venomous, volatile...) rolled on elites and champions. */
        public boolean affixesEnabled = true;
        public int eliteAffixes = 1;
        public int championAffixes = 2;
        /**
         * Most of a player's maximum health one hit from a ranked mob may take, so an elite is a fight
         * rather than a one-shot. 0 turns the cap off.
         */
        public double rankedHitCap = 0.6;
        /** Rank-coloured particles around elites and champions. */
        public boolean rankAura = true;
        /** Tell players nearby when a champion appears. */
        public boolean announceChampion = true;
    }

    /**
     * The monster book (bestiary).
     *
     * <p>Each kind of monster a player kills climbs its own rungs. Every rung reached is worth a little
     * more damage against that kind and a little more loot from it, pays rank points once, and at
     * {@link #revealDropsAt} the book starts listing what the monster drops.</p>
     */
    public static final class BestiaryRules {
        public boolean enabled = true;
        public long[] tiers = {10, 50, 200, 1000};
        public double damagePerTier = 0.02;
        public double lootPerTier = 0.02;
        public long rankPointsPerTier = 2;
        /** The rung (1-based) at which the book shows the drops. */
        public int revealDropsAt = 1;
        /** Most kinds of monster one book records. */
        public static final int MAX_ENTRIES = 512;
    }

    /**
     * Salvage.
     *
     * <p>A weapon or a piece of armour is worth what it does - its attack or its armour - plus its
     * enchantments, its refinement and anything socketed in it. Salvage turns that worth into gold and
     * refine ore, returns the cards, and gives back part of the ore sunk into refining it, so farmed
     * gear nobody wants still feeds the refinement loop.</p>
     */
    public static final class SalvageRules {
        public boolean enabled = true;
        public long goldPerValue = 4;
        /** Refine ore rolled per point of value. */
        public double orePerValue = 0.03;
        /** Share of the ore that went into refining the item that comes back. */
        public double refineRefund = 0.5;
        public boolean returnCards = true;
    }

    // Reward tracks -------------------------------------------------------------------------------
    /** Today's missions and the rungs a day of them is worth. */
    public DailyRules daily = new DailyRules();
    /** The season's long track, climbed with rank points. */
    public SeasonTrackRules seasonTrack = new SeasonTrackRules();

    /**
     * What one rung of a track pays. Items use the drop-grade line format, so a server writes a track
     * reward the same way it already writes a boss drop.
     */
    public static final class TrackReward {
        public long gold = 0;
        public long xp = 0;
        public long rankPoints = 0;
        public String[] items = {};

        public TrackReward() { }

        TrackReward(long gold, long xp, long rankPoints, String... items) {
            this.gold = gold;
            this.xp = xp;
            this.rankPoints = rankPoints;
            this.items = items;
        }
    }

    /** One rung of the daily track: reached after this many missions today. */
    public static final class DailyTier {
        public long completions = 1;
        public TrackReward reward = new TrackReward();

        public DailyTier() { }

        DailyTier(long completions, TrackReward reward) {
            this.completions = completions;
            this.reward = reward;
        }
    }

    /**
     * Today's missions.
     *
     * <p>The missions themselves are whatever the board named by {@link #boardId} shows today - boards
     * already rotate daily, so this never decides which quests are today's. What it adds is the count:
     * every mission finished today climbs the track, and each rung pays once. The count resets at local
     * midnight, and so does any rung reached but not claimed.</p>
     */
    public static final class DailyRules {
        public boolean enabled = true;
        /** The board whose rotation is "today's missions"; empty means every daily quest counts. */
        public String boardId = "";
        /** Quests set to repeat daily count even when they are not on that board. */
        public boolean countDailyRepeat = true;
        /** One line on the first join of a day saying how many missions are waiting. */
        public boolean announceOnJoin = true;
        public DailyTier[] tiers = defaultDailyTiers();

        public long[] thresholds() {
            long[] values = new long[tiers.length];
            for (int index = 0; index < tiers.length; index++) values[index] = tiers[index].completions;
            return values;
        }
    }

    /** One rung of the season track: reached at this many rank points. */
    public static final class SeasonTier {
        public long points = 100;
        public String name = "";
        public TrackReward reward = new TrackReward();

        public SeasonTier() { }

        SeasonTier(long points, String name, TrackReward reward) {
            this.points = points;
            this.name = name;
            this.reward = reward;
        }
    }

    /**
     * The season track.
     *
     * <p>Rank points already come from quests, bosses, the daily track and the bestiary, so the track
     * needs no earning path of its own - only rungs to claim. A claim is recorded by the rung's point
     * value, so reordering rungs mid-season never hands one out twice; a new {@link #seasonId} starts
     * every claim over.</p>
     */
    public static final class SeasonTrackRules {
        public boolean enabled = true;
        public String seasonId = "s1";
        public SeasonTier[] tiers = defaultSeasonTiers();

        public long[] thresholds() {
            long[] values = new long[tiers.length];
            for (int index = 0; index < tiers.length; index++) values[index] = tiers[index].points;
            return values;
        }
    }

    private static WorldEventDef worldEvent(String name, String description, int weight, String goal, int goalCount,
                                           TrackReward reward) {
        WorldEventDef def = new WorldEventDef();
        def.name = name;
        def.description = description;
        def.weight = weight;
        def.goal = goal;
        def.goalCount = goalCount;
        def.reward = reward;
        return def;
    }

    static Map<String, WorldEventDef> defaultWorldEvents() {
        Map<String, WorldEventDef> map = new LinkedHashMap<>();
        WorldEventDef overrun = worldEvent("ฝูงอสูรบุก", "ฝูงมอนสเตอร์บุกเข้ามา ช่วยกันกวาดล้างก่อนหมดเวลา",
                12, "KILL", 60, new TrackReward(400, 2000, 3, "rotasutils:oridecon 1-2", "rotasutils:elunium 1 @0.5"));
        overrun.mobLevelBonus = 3;
        overrun.eliteMultiplier = 3.0;
        overrun.xpMultiplier = 1.25;
        overrun.lootMultiplier = 1.25;
        overrun.spawns = new String[]{"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk"};
        overrun.waveSize = 3;
        overrun.waveSeconds = 25;
        overrun.minContribution = 5;
        map.put("overrun", overrun);

        WorldEventDef blessed = worldEvent("แดนศักดิ์สิทธิ์", "พรจากเบื้องบน ล่าในเขตนี้ได้ EXP มากขึ้นและฟื้นตัวเร็วขึ้น",
                8, "NONE", 0, new TrackReward());
        blessed.xpMultiplier = 1.5;
        blessed.healingMultiplier = 1.5;
        blessed.playerEffects = new String[]{"minecraft:regeneration 1"};
        map.put("blessed", blessed);

        WorldEventDef corrupted = worldEvent("แดนมลทิน", "มลทินแผ่คลุม มอนสเตอร์แกร่งขึ้นแต่ของดรอปดีขึ้นเท่าตัว",
                8, "KILL", 40, new TrackReward(600, 3000, 4, "rotasutils:enriched_oridecon 1 @0.5",
                        "rotasutils:enriched_elunium 1 @0.5"));
        corrupted.mobLevelBonus = 6;
        corrupted.mobHealth = 1.5;
        corrupted.mobDamage = 1.4;
        corrupted.eliteMultiplier = 2.0;
        corrupted.lootMultiplier = 2.0;
        corrupted.coinMultiplier = 1.5;
        corrupted.spawns = new String[]{"minecraft:zombie_villager", "minecraft:stray", "minecraft:witch"};
        corrupted.minContribution = 4;
        map.put("corrupted", corrupted);

        WorldEventDef treasure = worldEvent("พายุสมบัติ", "เหรียญโปรยปราย มอนสเตอร์ในเขตนี้ดรอปเหรียญและของมากขึ้น",
                8, "KILL", 30, new TrackReward(300, 800, 2, "rotasutils:gold_coin 8-16"));
        treasure.coinMultiplier = 3.0;
        treasure.lootMultiplier = 2.0;
        treasure.spawns = new String[]{"minecraft:zombie", "minecraft:skeleton"};
        treasure.waveSize = 2;
        map.put("treasure_storm", treasure);

        WorldEventDef ore = worldEvent("สายแร่อุดม", "สายแร่ผุดขึ้น แร่ที่ขุดในเขตนี้มีโอกาสได้เพิ่มอีกชุด",
                8, "MINE", 80, new TrackReward(300, 1500, 2, "rotasutils:elunium 1-2", "rotasutils:oridecon 1 @0.5"));
        ore.oreBonusChance = 0.5;
        ore.playerEffects = new String[]{"minecraft:haste 1"};
        ore.minContribution = 8;
        map.put("rich_ore", ore);

        WorldEventDef night = worldEvent("ค่ำคืนต้องสาป", "ราตรีนี้มอนสเตอร์ดุร้ายเป็นพิเศษ เอาตัวรอดและกวาดล้างให้ได้",
                10, "KILL", 80, new TrackReward(700, 4000, 5, "rotasutils:blessing_scroll 1 @0.25"));
        night.nightOnly = true;
        night.mobLevelBonus = 5;
        night.eliteMultiplier = 4.0;
        night.mobDamage = 1.3;
        night.xpMultiplier = 1.5;
        night.lootMultiplier = 1.5;
        night.spawns = new String[]{"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:witch"};
        night.waveSize = 4;
        night.minContribution = 6;
        map.put("cursed_night", night);
        return map;
    }

    private static DailyTier[] defaultDailyTiers() {
        return new DailyTier[]{
                new DailyTier(1, new TrackReward(150, 300, 2)),
                new DailyTier(3, new TrackReward(400, 900, 5,
                        "rotasutils:oridecon 1 @0.25", "rotasutils:elunium 1 @0.25")),
                new DailyTier(5, new TrackReward(900, 2000, 15,
                        "rotasutils:blessing_scroll 1 @0.15", "rotasutils:socket_punch 1 @0.05")),
        };
    }

    private static SeasonTier[] defaultSeasonTiers() {
        return new SeasonTier[]{
                new SeasonTier(100, "\u0e01\u0e49\u0e32\u0e27\u0e41\u0e23\u0e01", new TrackReward(300, 500, 0)),
                new SeasonTier(250, "", new TrackReward(500, 0, 0, "rotasutils:oridecon 2", "rotasutils:elunium 2")),
                new SeasonTier(450, "", new TrackReward(800, 1500, 0)),
                new SeasonTier(700, "", new TrackReward(0, 0, 0, "rotasutils:blessing_scroll 1")),
                new SeasonTier(1000, "\u0e19\u0e31\u0e01\u0e1c\u0e08\u0e0d\u0e20\u0e31\u0e22\u0e15\u0e31\u0e27\u0e08\u0e23\u0e34\u0e07",
                        new TrackReward(1500, 3000, 0, "rotasutils:socket_punch 1")),
                new SeasonTier(1400, "", new TrackReward(0, 0, 0, "rotasutils:enriched_oridecon 1", "rotasutils:enriched_elunium 1")),
                new SeasonTier(1900, "", new TrackReward(2500, 6000, 0)),
                new SeasonTier(2500, "", new TrackReward(0, 0, 0, "rotasutils:protection_scroll 1")),
                new SeasonTier(3200, "\u0e04\u0e23\u0e36\u0e48\u0e07\u0e17\u0e32\u0e07",
                        new TrackReward(4000, 10000, 0, "rotasutils:socket_punch 1")),
                new SeasonTier(4000, "", new TrackReward(0, 0, 0, "rotasutils:protection_scroll 2", "rotasutils:blessing_scroll 2")),
                new SeasonTier(5000, "", new TrackReward(8000, 20000, 0)),
                new SeasonTier(6500, "\u0e15\u0e33\u0e19\u0e32\u0e19\u0e41\u0e2b\u0e48\u0e07\u0e0b\u0e35\u0e0b\u0e31\u0e19",
                        new TrackReward(15000, 40000, 0, "rotasutils:certificate_scroll 1")),
        };
    }

    // Events --------------------------------------------------------------------------------------
    /** What the server does about each thing that happens in the game. */
    public EventRules events = new EventRules();

    /**
     * The event catalogue.
     *
     * <p>One rule per line: a type, an optional filter, and what the server does when it happens. The
     * plain entry for a type has no filter and answers for everything; a rule with {@code "alexsmobs:*"}
     * or an exact id answers only for that, and beats the plain one. Switching a rule off means this mod
     * stops paying and announcing for that event - it never stops the event itself.</p>
     */
    public static final class EventRules {
        public boolean enabled = true;
        public EventRule[] rules = defaultEventRules();
        /** Most rules a server may write. */
        public static final int MAX_RULES = 512;
    }

    /** One line of the event catalogue. */
    public static final class EventRule {
        /** An {@link net.schwarz.rotasutils.event.EventType} name. */
        public String type = "KILL_ENTITY";
        /** Empty or {@code *} for everything, {@code namespace:*} for one mod, or an exact id. */
        public String filter = "";
        public boolean enabled = true;
        /** Scales the experience this event already pays; 1 leaves it alone. */
        public double xpMultiplier = 1.0;
        /** Flat experience on top, per occurrence. */
        public long xpFlat = 0;
        /** Season currency paid per occurrence. */
        public long gold = 0;
        /** OFF, PLAYER or SERVER. */
        public String announce = "OFF";
        /** Seconds before the same player can be paid for this rule again; 0 means no limit. */
        public int cooldownSeconds = 0;

        public EventRule() { }

        EventRule(String type, String filter) {
            this.type = type;
            this.filter = filter;
        }

        public net.schwarz.rotasutils.event.EventType eventType() {
            return net.schwarz.rotasutils.event.EventType.byName(type);
        }

        public net.schwarz.rotasutils.event.EventRules.Announce announcement() {
            return net.schwarz.rotasutils.event.EventRules.Announce.byName(announce);
        }
    }

    /** One plain rule per type, so the catalogue is complete the first time it is opened. */
    private static EventRule[] defaultEventRules() {
        var types = net.schwarz.rotasutils.event.EventType.values();
        EventRule[] rules = new EventRule[types.length];
        for (int index = 0; index < types.length; index++) {
            rules[index] = new EventRule(types[index].name(), "");
        }
        return rules;
    }

    // Cards ---------------------------------------------------------------------------------------
    /** Monster cards and the sockets they go into. */
    public CardRules cards = new CardRules();

    /**
     * Cards (การ์ด) and sockets (รู).
     *
     * <p>A card is the rarest thing an ordinary monster can leave. It goes into a socket on a weapon or
     * a piece of armour and stays there, and its bonus is a plain attribute bonus, so a card never needs
     * a system of its own to be worth chasing.</p>
     */
    public static final class CardRules {
        public boolean enabled = true;
        /** Most sockets one item can ever have. */
        public int maxSockets = 4;
        /** Chance a kill leaves a card when the card itself does not set one. Ragnarok-thin on purpose. */
        public double dropChance = 0.0005;
        /** Card id -> the card. */
        public Map<String, CardDef> entries = defaultCards();
    }

    /** One card: where it comes from, what it fits and what it gives. */
    public static final class CardDef {
        public String name = "";
        /** Entity id that leaves it; empty means any monster can. */
        public String source = "";
        /** WEAPON, ARMOR or ANY. */
        public String fits = "ANY";
        /** Its own drop chance; below zero uses {@link CardRules#dropChance}. */
        public double chance = -1;
        public int color = 0xFFB07CE8;
        /** Effect lines, e.g. {@code "minecraft:generic.attack_damage 0.08 percent"}. */
        public String[] effects = {};

        public CardDef() { }

        CardDef(String name, String source, String fits, double chance, int color, String... effects) {
            this.name = name;
            this.source = source;
            this.fits = fits;
            this.chance = chance;
            this.color = color;
            this.effects = effects;
        }
    }

    private static Map<String, CardDef> defaultCards() {
        Map<String, CardDef> map = new LinkedHashMap<>();
        map.put("rotas:zombie", new CardDef("การ์ดซอมบี้", "minecraft:zombie", "ARMOR", 0.0008, 0xFF86C05C,
                "minecraft:generic.max_health 0.04 percent"));
        map.put("rotas:skeleton", new CardDef("การ์ดโครงกระดูก", "minecraft:skeleton", "WEAPON", 0.0008, 0xFFD8D8D8,
                "minecraft:generic.attack_speed 0.05 percent"));
        map.put("rotas:spider", new CardDef("การ์ดแมงมุม", "minecraft:spider", "ARMOR", 0.0008, 0xFF8A6130,
                "minecraft:generic.movement_speed 0.04 percent"));
        map.put("rotas:creeper", new CardDef("การ์ดครีปเปอร์", "minecraft:creeper", "ARMOR", 0.0006, 0xFF5CC05C,
                "minecraft:generic.armor 2", "minecraft:generic.knockback_resistance 0.1"));
        map.put("rotas:blaze", new CardDef("การ์ดเบลซ", "minecraft:blaze", "WEAPON", 0.0004, 0xFFE0AC4C,
                "minecraft:generic.attack_damage 0.06 percent"));
        map.put("rotas:enderman", new CardDef("การ์ดเอนเดอร์แมน", "minecraft:enderman", "ANY", 0.0003, 0xFFB07CE8,
                "minecraft:generic.movement_speed 0.06 percent", "minecraft:generic.luck 1"));
        map.put("rotas:wither_skeleton", new CardDef("การ์ดวิเธอร์สเกเลตัน", "minecraft:wither_skeleton", "WEAPON",
                0.0002, 0xFF3F3F46, "minecraft:generic.attack_damage 0.10 percent"));
        map.put("rotas:warden", new CardDef("การ์ดวอร์เดน", "minecraft:warden", "ARMOR", 0.02, 0xFF2E5F63,
                "minecraft:generic.max_health 0.10 percent", "minecraft:generic.armor_toughness 4"));
        map.put("rotas:ender_dragon", new CardDef("การ์ดเอนเดอร์ดราก้อน", "minecraft:ender_dragon", "WEAPON",
                0.05, 0xFFE0AC4C, "minecraft:generic.attack_damage 0.15 percent"));
        return map;
    }

    // Refinement ----------------------------------------------------------------------------------
    /** Weapon and armour refinement: how far a player can push a piece and what it pays. */
    public RefineRules refine = new RefineRules();

    /** Rune inscribing at the Rune Altar. */
    public RuneRules runes = new RuneRules();

    /**
     * Runes (อักษรรูน) inscribed into a weapon at the Rune Altar.
     *
     * <p>Slots open with refinement, so a rune is the reward for pushing a weapon rather than a way
     * around it. What each rune does is fixed in {@link net.schwarz.rotasutils.core.RuneType}.</p>
     */
    public static final class RuneRules {
        public boolean enabled = true;
        /** Refine level at which each rune slot opens, in slot order. */
        public int[] slotLevels = {4, 7, 10};
        /** Gold to inscribe into each slot, in slot order; a replaced rune is lost. */
        public long[] goldPerSlot = {500, 1500, 4000};
    }

    /**
     * Refinement (ตีบวก).
     *
     * <p>An attempt at or below {@link #safeLevel} always succeeds; above it {@link #chances} decides,
     * and a failure does what {@link #onFail} says. The bonus a level pays is deliberately split in two:
     * levels inside the safe range pay the plain rate, levels above it pay the over rate, so a +10 is
     * worth far more than twice a +5 and is worth chasing.</p>
     */
    public static final class RefineRules {
        public boolean enabled = true;
        /** Highest refine level an item can reach. */
        public int maxLevel = 10;
        /** At or below this level an attempt cannot fail. */
        public int safeLevel = 4;
        /** Success chance of an attempt on level 1, 2, 3 ... The classic Ragnarok table. */
        public double[] chances = {1.0, 1.0, 1.0, 1.0, 0.60, 0.40, 0.40, 0.20, 0.20, 0.10};
        /** DOWNGRADE, RESET_TO_SAFE, BREAK or KEEP. */
        public String onFail = "DOWNGRADE";
        /** Flat attack damage each refine level inside the safe range adds to a weapon. */
        public double attackPerLevel = 1.0;
        /** Flat attack damage each level above the safe range adds. */
        public double attackPerOverLevel = 1.5;
        /** Defence (the {@code rotas:defense} channel) each level inside the safe range adds to armour. */
        public double defensePerLevel = 0.8;
        /** Defence each level above the safe range adds. */
        public double defensePerOverLevel = 1.2;
        /** Gold an attempt on +1 costs; every level multiplies it by {@link #goldGrowth}. */
        public long goldPerAttempt = 200;
        public double goldGrowth = 1.6;
        /** Success chance the enriched ore adds. */
        public double enrichedBonus = 0.15;
        /** Success chance a blessing scroll adds. */
        public double blessingBonus = 0.20;
        /** A success at or above this level is announced to everyone on the server; 0 turns that off. */
        public int announceFrom = 8;
        /** One of these is consumed per attempt, by what is being refined. */
        public String weaponOre = "rotasutils:oridecon";
        public String armorOre = "rotasutils:elunium";
        public String weaponOreEnriched = "rotasutils:enriched_oridecon";
        public String armorOreEnriched = "rotasutils:enriched_elunium";

        public net.schwarz.rotasutils.core.RefineMath.Fail failMode() {
            return net.schwarz.rotasutils.core.RefineMath.Fail.byKey(onFail);
        }
    }

    /**
     * Drops by monster rank.
     *
     * <p>Every number here is editable in {@code season.json} and takes effect on
     * {@code /rotas season reload}. The shipped defaults are the design in the docs: ordinary ranks
     * pay coins and sometimes common loot, a miniboss always leaves common or medium loot, and a
     * boss always leaves rare or epic loot. Nothing here is a container the player has to open;
     * everything drops as the items themselves.
     */
    public static final class DropRules {
        public boolean enabled = true;
        /** Monster rank name -> what it leaves. Unlisted ranks drop nothing. */
        public Map<String, RankDrop> ranks = defaultRankDrops();
        /** Drop grade -> what that grade pays. Unlisted grades fall back to the common grade. */
        public Map<String, GradeLoot> grades = defaultGrades();
        /** What a mob nobody configured leaves behind. */
        public PlainDrop plain = new PlainDrop();
        /** Which of a mob's ordinary drops are switched off. */
        public DropFilter filter = new DropFilter();
    }

    /**
     * Switching off drops a mob would otherwise leave.
     *
     * <p>This is the other half of {@link PlainDrop}: that one adds, this one takes away. A blocked item
     * is simply never spawned when a mob dies, so a server can stop rotten flesh filling every bag
     * without touching a loot table or a data pack.</p>
     */
    public static final class DropFilter {
        public boolean enabled = true;
        /** Item ids no mob ever drops. */
        public String[] blocked = {};
        /** Entity id -> item ids that particular mob never drops. */
        public Map<String, String[]> byEntity = new LinkedHashMap<>();
        /** Most globally blocked items. */
        public static final int MAX_BLOCKED = 1024;
        /** Most entities with a list of their own, and most items in one such list. */
        public static final int MAX_ENTITIES = 512;
        public static final int MAX_PER_ENTITY = 128;
    }

    /**
     * Drops for the mobs that have no Mob Setup of their own.
     *
     * <p>A server does not configure every entity in its pack, and until now those mobs paid nothing
     * at all. {@link #rule} is what any of them leaves; {@link #byEntity} gives one entity type its own
     * rule without a full setup, and {@link #ignore} names the ones that must never pay - villagers,
     * pets and the golems that are part of a town rather than a fight.</p>
     */
    public static final class PlainDrop {
        public boolean enabled = true;
        /** Only mobs the game itself calls hostile pay. Off makes animals pay too. */
        public boolean hostileOnly = true;
        /** The drop every unconfigured mob rolls. Deliberately thinner than a configured monster's. */
        public RankDrop rule = new RankDrop(0.25, 0.5, 0.02, "common");
        /** Entity id -> its own rule, for "zombies pay more than bats" without a Mob Setup. */
        public Map<String, RankDrop> byEntity = new LinkedHashMap<>();
        /** Entity ids that never pay, whatever else says. */
        public String[] ignore = {"minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem",
                "minecraft:snow_golem", "minecraft:armor_stand", "minecraft:allay"};
    }

    /** One monster rank's drop rule. */
    public static final class RankDrop {
        /** Chance (0..1) this rank pays coins at all. */
        public double coinChance = 0.35;
        /** Coins per kill: coinMin..coinMax, plus coinPerLevel per monster level, then times coinMultiplier. */
        public int coinMin = 1;
        public int coinMax = 2;
        public double coinPerLevel = 0.15;
        public double coinMultiplier = 1.0;
        /** Chance (0..1) this rank leaves loot on top of its coins. */
        public double lootChance = 0.05;
        /** Grades one of which is picked at random when loot drops; empty means coins only. */
        public String[] grades = {"common"};
        /**
         * Item lines this rule always rolls, on top of the grade: {@code "minecraft:bone 1-2 @0.4"}.
         * This is how one entity type gets its own drop without a content pack behind it.
         */
        public String[] items = {};

        public RankDrop() { }

        RankDrop(double coinChance, double coinMultiplier, double lootChance, String... grades) {
            this.coinChance = coinChance;
            this.coinMultiplier = coinMultiplier;
            this.lootChance = lootChance;
            this.grades = grades;
        }
    }

    /**
     * What one grade pays: gold, plus item lines written as {@code "minecraft:diamond 1-3"} or
     * {@code "minecraft:diamond 1-3 @0.5"} for a chance.
     */
    public static final class GradeLoot {
        public long minGold = 10;
        public long maxGold = 30;
        public String[] items = {};

        public GradeLoot() { }

        GradeLoot(long minGold, long maxGold, String... items) {
            this.minGold = minGold;
            this.maxGold = maxGold;
            this.items = items;
        }
    }

    private static Map<String, RankDrop> defaultRankDrops() {
        Map<String, RankDrop> map = new LinkedHashMap<>();
        map.put("NORMAL", new RankDrop(0.35, 1.0, 0.05, "common"));
        map.put("VETERAN", new RankDrop(0.35, 1.5, 0.05, "common"));
        map.put("ELITE", new RankDrop(0.35, 2.0, 0.08, "common"));
        map.put("CHAMPION", new RankDrop(0.35, 3.0, 0.12, "common", "medium"));
        map.put("MINIBOSS", new RankDrop(1.0, 6.0, 1.0, "common", "medium"));
        map.put("BOSS", new RankDrop(1.0, 12.0, 1.0, "rare", "epic"));
        map.put("WORLD_BOSS", new RankDrop(1.0, 24.0, 1.0, "rare", "epic"));
        return map;
    }

    private static Map<String, GradeLoot> defaultGrades() {
        Map<String, GradeLoot> map = new LinkedHashMap<>();
        map.put("common", new GradeLoot(10, 30,
                "minecraft:bread 2-4", "minecraft:iron_ingot 1-3 @0.5", "minecraft:coal 1-3 @0.5"));
        map.put("medium", new GradeLoot(30, 80,
                "minecraft:iron_ingot 3-6", "minecraft:gold_ingot 2-4", "minecraft:experience_bottle 2-5",
                // Refine ore starts here, at a rate that makes a +5 a project rather than an afternoon.
                "rotasutils:oridecon 1 @0.04", "rotasutils:elunium 1 @0.04"));
        map.put("rare", new GradeLoot(80, 200,
                "minecraft:diamond 1-3", "minecraft:emerald 4-8", "minecraft:golden_apple 1-2",
                "minecraft:ender_pearl 1-3",
                "rotasutils:oridecon 1-2 @0.18", "rotasutils:elunium 1-2 @0.18",
                "rotasutils:protection_scroll 1 @0.03", "rotasutils:blessing_scroll 1 @0.05",
                // Runes only matter from +4, so they start where refine ore is already flowing.
                "rotasutils:rune_fire 1 @0.02", "rotasutils:rune_frost 1 @0.02", "rotasutils:rune_venom 1 @0.02",
                "rotasutils:rune_lifesteal 1 @0.01", "rotasutils:rune_fury 1 @0.01"));
        map.put("epic", new GradeLoot(200, 500,
                "minecraft:diamond 4-8", "minecraft:netherite_scrap 1-2",
                "minecraft:enchanted_golden_apple 1", "minecraft:experience_bottle 8-16",
                "rotasutils:oridecon 2-4 @0.45", "rotasutils:elunium 2-4 @0.45",
                "rotasutils:enriched_oridecon 1 @0.08", "rotasutils:enriched_elunium 1 @0.08",
                "rotasutils:protection_scroll 1 @0.12", "rotasutils:blessing_scroll 1 @0.15",
                "rotasutils:rune_fire 1 @0.08", "rotasutils:rune_frost 1 @0.08", "rotasutils:rune_venom 1 @0.08",
                "rotasutils:rune_lifesteal 1 @0.05", "rotasutils:rune_fury 1 @0.05",
                // The one item that guarantees a +10 attempt. A boss a week, at best.
                "rotasutils:certificate_scroll 1 @0.01"));
        return map;
    }

    public static final class RankPerk {
        public double shopDiscount;
        public int stableSlots;
        public double marketFeeDiscount;
        public String title = "";

        public RankPerk() { }

        RankPerk(double shopDiscount, int stableSlots, double marketFeeDiscount, String title) {
            this.shopDiscount = shopDiscount;
            this.stableSlots = stableSlots;
            this.marketFeeDiscount = marketFeeDiscount;
            this.title = title;
        }
    }

    public static final class HorseRules {
        public boolean enabled = true;
        public long pullCost = 500;
        public long tenPullCost = 4500;
        /** Chance per pull for COMMON, UNCOMMON, RARE, EPIC, LEGENDARY; normalised when read. */
        public double[] rates = {0.55, 0.25, 0.12, 0.06, 0.02};
        public int pityRare = 10;
        public int pityEpic = 40;
        public int pityLegendary = 150;
        public double epicRareCoatChance = 0.35;
        /** Coat ids that count as secret. Empty means every coat SWEM does not let foals inherit. */
        public String[] secretCoats = {};
        /** Coat ids that count as rare (Epic pulls may roll one). */
        public String[] rareCoats = {};
        public int baseStableSlots = 3;
        public long slotBaseCost = 1000;
        public double slotCostGrowth = 1.5;
        public int maxStableSlots = 30;
        public int summonCooldownSeconds = 10;
        public int recoverySeconds = 300;
        public int potionUsesPerDay = 3;
        public boolean bredHorsesSellToNpc = false;
        public int npcSalesPerDay = 3;
        public long sellBase = 40;
        public long sellPerSkillLevel = 60;
        public long sellPerAffinityLevel = 15;
        public long sellSecretCoat = 900;
        public long sellRareCoat = 150;
        public long[] sellRarityBonus = {0, 20, 60, 150, 400};
        /** Percent kept by the market; 0 means sellers receive the full price. */
        public double marketFee = 0;
        public long marketMaxPrice = 10_000_000;

        // Breeding (see HorseBreeding) ---------------------------------------------------------------
        public boolean breedEnabled = true;
        public long breedBaseCost = 800;
        public long breedCostPerLineage = 150;
        public int gestationMinutes = 30;
        public int breedCooldownMinutes = 120;
        public int breedsPerHorse = 3;
        public int fertileBonusBreeds = 2;
        /** Share of the parents' trained levels a foal is born with (0 = always level I). */
        public double breedLevelInheritance = 0.5;
        public double breedTraitInheritChance = 0.5;
        public double breedMutationChance = 0.08;
        public double prizedLineMutationBonus = 0.10;
        public double starbornChance = 0.03;
        public double breedRareCoatChance = 0.35;
        public double breedSecretCoatChance = 0.12;
        public long studMaxFee = 1_000_000;
        /** Extra NPC price per generation of lineage (capped at 10 generations). */
        public long sellPerLineage = 60;
        /** Expected traits per draw rarity: whole part guaranteed, fraction is a chance of one more. */
        public double[] gachaTraits = {0.05, 0.25, 0.6, 1.0, 1.5};

        // Trait strength -----------------------------------------------------------------------------
        public double warhorseDamage = 0.12;
        public double ironhideArmor = 6;
        public double valiantArmor = 3;
        public double windrunnerSpeed = 0.10;
        public double goldenBloodPrice = 0.30;
        public double peddlerPrice = 0.10;
        public double starbornPrice = 0.50;
        public double foragerXp = 0.15;
        public double trailblazerXp = 0.25;
        public double peddlerXp = 0.15;
        public double starbornXp = 0.05;
    }

    private static Map<String, Integer> defaultRankExp() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("MAIN", 100);
        map.put("SIDE", 30);
        map.put("WEEKLY", 40);
        map.put("DAILY", 5);
        map.put("REPEATABLE", 2);
        return map;
    }

    private static Map<String, Double> defaultThresholds() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("F", 0.0);
        map.put("E", 0.10);
        map.put("D", 0.22);
        map.put("C", 0.35);
        map.put("B", 0.60);
        map.put("A", 0.78);
        map.put("S", 0.90);
        map.put("SS", 0.97);
        return map;
    }

    private static Map<String, RankPerk> defaultPerks() {
        Map<String, RankPerk> map = new LinkedHashMap<>();
        map.put("E", new RankPerk(0, 0, 0, ""));
        map.put("D", new RankPerk(0.02, 0, 0, ""));
        map.put("C", new RankPerk(0.03, 1, 0, ""));
        map.put("B", new RankPerk(0.05, 1, 0, ""));
        map.put("A", new RankPerk(0.07, 2, 0, "นักผจญภัยแรงค์ A"));
        map.put("S", new RankPerk(0.10, 2, 0.5, "ยอดฝีมือแรงค์ S"));
        map.put("SS", new RankPerk(0.12, 3, 1.0, "ตำนานแรงค์ SS"));
        return map;
    }

    /** Rank points one completion of this kind of quest is worth. */
    public int rankExpFor(String questType) {
        return Math.max(0, rankExp.getOrDefault(questType, 0));
    }

    /** What an artisan charges per craft of this tier; tiers past D charge the D fee. */
    public long crafterFeeFor(int tier) {
        long base = crafterFee[Math.max(0, Math.min(crafterFee.length - 1, tier))];
        return Math.max(0, Math.round(base * crafterPriceMultiplier));
    }

    public RankPerk perk(String rank) {
        return rankPerks.getOrDefault(rank, new RankPerk());
    }

    /** Clamps every value into a safe range, so a hand-edited file cannot break the game. */
    public SeasonRules sanitize() {
        mainBaseXp = clamp(mainBaseXp, 1, 1e9, 25);
        mainExponent = clamp(mainExponent, 0.5, 4, 2.1);
        mainMaxLevel = clamp(mainMaxLevel, 2, 10000);
        killsAtStart = clamp(killsAtStart, 0.1, 100000, 8);
        killsAtMax = clamp(killsAtMax, 0.1, 100000, 120);
        killsCurve = clamp(killsCurve, 0.1, 6, 1.5);
        referenceMonsterXp = clamp(referenceMonsterXp, 1, 1e9, 20);
        catchUpLevel = clamp(catchUpLevel, 1, 10000);
        catchUpBonus = clamp(catchUpBonus, 0, 10, 0.5);
        subBaseXp = clamp(subBaseXp, 1, 1e9, 90);
        subExponent = clamp(subExponent, 0.5, 4, 1.8);
        subMaxLevel = clamp(subMaxLevel, 1, 1000);
        monsterLevelBonus = clamp(monsterLevelBonus, 0, 10, 0.15);
        overLevelGrace = clamp(overLevelGrace, 0, 1000);
        overLevelPenaltyPerLevel = clamp(overLevelPenaltyPerLevel, 0, 1, 0.1);
        overLevelMaxPenalty = clamp(overLevelMaxPenalty, 0, 1, 0.9);
        minibossMultiplier = clamp(minibossMultiplier, 1, 1000, 10);
        bossMultiplier = clamp(bossMultiplier, 1, 1000, 40);
        partyBonusPerMember = clamp(partyBonusPerMember, 0, 5, 0.25);
        partyRadius = clamp(partyRadius, 4, 512, 40);
        partyMaxSize = clamp(partyMaxSize, 1, 64);
        partyLevelReach = clamp(partyLevelReach, 0, 10000);
        overflowXpPerToken = Math.max(1, overflowXpPerToken);
        repeatableFullRuns = clamp(repeatableFullRuns, 0, 100000);
        repeatableHalfRuns = Math.max(repeatableFullRuns, clamp(repeatableHalfRuns, 0, 100000));
        repeatableHalfRate = clamp(repeatableHalfRate, 0, 1, 0.5);
        repeatableLowRate = clamp(repeatableLowRate, 0, 1, 0.1);
        tierXp = fourLongs(tierXp, new long[]{25, 150, 420, 830});
        tierMaxLevel = fourInts(tierMaxLevel, new int[]{4, 9, 14, 19});
        craftGraceLevels = clamp(craftGraceLevels, 0, 1000);
        craftPenaltyPerLevel = clamp(craftPenaltyPerLevel, 0, 1, 0.15);
        craftMaxPenalty = clamp(craftMaxPenalty, 0, 1, 0.9);
        firstCraftMultiplier = clamp(firstCraftMultiplier, 1, 100, 3);
        gatherFreeLevel = clamp(gatherFreeLevel, 0, 10000);
        gatherDropChance = clamp(gatherDropChance, 0, 1, 0.35);
        gatherMaxCount = clamp(gatherMaxCount, 1, 64);
        if (gatherFishFallback == null || gatherFishFallback.isBlank()) gatherFishFallback = "minecraft:cod";
        crafterFee = fourLongs(crafterFee, new long[]{80, 400, 1200, 3000});
        crafterPriceMultiplier = clamp(crafterPriceMultiplier, 0, 1000, 3);
        crafterDailyLimit = clamp(crafterDailyLimit, 0, 100000);
        if (rankExp == null) rankExp = defaultRankExp();
        repeatableRankCapPerDay = clamp(repeatableRankCapPerDay, 0, 1_000_000);
        seasonRankTotal = Math.max(1, seasonRankTotal);
        if (rankThresholds == null || rankThresholds.isEmpty()) rankThresholds = defaultThresholds();
        if (rankPerks == null) rankPerks = defaultPerks();
        stats = stats == null ? new StatRules() : stats.sanitize();
        defenseScale = clamp(defenseScale, 1, 1e6, 100);
        pvpDamageMultiplier = clamp(pvpDamageMultiplier, 0, 10, 0.6);
        pvpStatEfficiency = clamp(pvpStatEfficiency, 0, 1, 0.5);
        pvpEvasionScale = clamp(pvpEvasionScale, 0, 1, 0.5);
        pvpMaxHitShare = clamp(pvpMaxHitShare, 0, 1, 0.35);
        magicBonusMaxRatio = clamp(magicBonusMaxRatio, 0, 100, 1.0);
        if (drops == null) drops = new DropRules();
        if (drops.ranks == null || drops.ranks.isEmpty()) drops.ranks = defaultRankDrops();
        if (drops.grades == null || drops.grades.isEmpty()) drops.grades = defaultGrades();
        if (drops.filter == null) drops.filter = new DropFilter();
        if (drops.filter.blocked == null) drops.filter.blocked = new String[0];
        if (drops.filter.blocked.length > DropFilter.MAX_BLOCKED) {
            drops.filter.blocked = java.util.Arrays.copyOf(drops.filter.blocked, DropFilter.MAX_BLOCKED);
        }
        if (drops.filter.byEntity == null) drops.filter.byEntity = new LinkedHashMap<>();
        if (drops.filter.byEntity.size() > DropFilter.MAX_ENTITIES) {
            Map<String, String[]> trimmed = new LinkedHashMap<>();
            for (var entry : drops.filter.byEntity.entrySet()) {
                if (trimmed.size() >= DropFilter.MAX_ENTITIES) break;
                trimmed.put(entry.getKey(), entry.getValue());
            }
            drops.filter.byEntity = trimmed;
        }
        drops.filter.byEntity.replaceAll((entity, items) -> items == null ? new String[0]
                : items.length > DropFilter.MAX_PER_ENTITY
                ? java.util.Arrays.copyOf(items, DropFilter.MAX_PER_ENTITY) : items);
        if (drops.plain == null) drops.plain = new PlainDrop();
        if (drops.plain.rule == null) drops.plain.rule = new RankDrop(0.25, 0.5, 0.02, "common");
        if (drops.plain.byEntity == null) drops.plain.byEntity = new LinkedHashMap<>();
        if (drops.plain.ignore == null) drops.plain.ignore = new String[0];
        java.util.List<RankDrop> everyRule = new java.util.ArrayList<>(drops.ranks.values());
        everyRule.add(drops.plain.rule);
        everyRule.addAll(drops.plain.byEntity.values());
        for (RankDrop rank : everyRule) {
            if (rank == null) continue;
            rank.coinChance = clamp(rank.coinChance, 0, 1, 0.35);
            rank.coinMultiplier = clamp(rank.coinMultiplier, 0, 10000, 1);
            rank.lootChance = clamp(rank.lootChance, 0, 1, 0);
            if (rank.grades == null) rank.grades = new String[0];
            if (rank.items == null) rank.items = new String[0];
        }
        for (GradeLoot loot : drops.grades.values()) {
            if (loot == null) continue;
            loot.minGold = Math.max(0, Math.min(1_000_000, loot.minGold));
            loot.maxGold = Math.max(loot.minGold, Math.min(1_000_000, loot.maxGold));
            if (loot.items == null) loot.items = new String[0];
        }
        if (runes == null) runes = new RuneRules();
        if (runes.slotLevels == null) runes.slotLevels = new int[]{4, 7, 10};
        if (runes.slotLevels.length > 6) runes.slotLevels = java.util.Arrays.copyOf(runes.slotLevels, 6);
        for (int i = 0; i < runes.slotLevels.length; i++) runes.slotLevels[i] = clamp(runes.slotLevels[i], 0, 100);
        long[] defaultRuneGold = {500, 1500, 4000};
        long[] runeGold = new long[runes.slotLevels.length];
        for (int i = 0; i < runeGold.length; i++) {
            long fallback = defaultRuneGold[Math.min(i, defaultRuneGold.length - 1)];
            runeGold[i] = Math.max(0, runes.goldPerSlot != null && i < runes.goldPerSlot.length
                    ? runes.goldPerSlot[i] : fallback);
        }
        runes.goldPerSlot = runeGold;
        if (refine == null) refine = new RefineRules();
        RefineRules r = refine;
        r.maxLevel = clamp(r.maxLevel, 0, 100);
        r.safeLevel = clamp(r.safeLevel, 0, r.maxLevel);
        if (r.chances == null || r.chances.length == 0) {
            r.chances = new double[]{1.0, 1.0, 1.0, 1.0, 0.60, 0.40, 0.40, 0.20, 0.20, 0.10};
        }
        for (int i = 0; i < r.chances.length; i++) r.chances[i] = clamp(r.chances[i], 0, 1, 0);
        r.onFail = net.schwarz.rotasutils.core.RefineMath.Fail.byKey(r.onFail).name();
        r.attackPerLevel = clamp(r.attackPerLevel, 0, 1000, 1.0);
        r.attackPerOverLevel = clamp(r.attackPerOverLevel, 0, 1000, 1.5);
        r.defensePerLevel = clamp(r.defensePerLevel, 0, 1000, 0.8);
        r.defensePerOverLevel = clamp(r.defensePerOverLevel, 0, 1000, 1.2);
        r.goldPerAttempt = Math.max(0, Math.min(1_000_000_000L, r.goldPerAttempt));
        r.goldGrowth = clamp(r.goldGrowth, 1, 100, 1.6);
        r.enrichedBonus = clamp(r.enrichedBonus, 0, 1, 0.15);
        r.blessingBonus = clamp(r.blessingBonus, 0, 1, 0.20);
        r.announceFrom = clamp(r.announceFrom, 0, 100);
        if (r.weaponOre == null || r.weaponOre.isBlank()) r.weaponOre = "rotasutils:oridecon";
        if (r.armorOre == null || r.armorOre.isBlank()) r.armorOre = "rotasutils:elunium";
        if (r.weaponOreEnriched == null || r.weaponOreEnriched.isBlank()) r.weaponOreEnriched = "rotasutils:enriched_oridecon";
        if (r.armorOreEnriched == null || r.armorOreEnriched.isBlank()) r.armorOreEnriched = "rotasutils:enriched_elunium";
        if (farming == null) farming = new FarmingRules();
        FarmingRules f = farming;
        f.lootChancePerLuck = clamp(f.lootChancePerLuck, 0, 10, 0.10);
        f.cardChancePerLuck = clamp(f.cardChancePerLuck, 0, 10, 0.05);
        f.luckBonusCap = clamp(f.luckBonusCap, 0, 10, 1.0);
        f.comboWindowSeconds = clamp(f.comboWindowSeconds, 1, 600);
        f.comboXpPerKill = clamp(f.comboXpPerKill, 0, 1, 0.02);
        f.comboLootPerKill = clamp(f.comboLootPerKill, 0, 1, 0.01);
        f.comboCap = clamp(f.comboCap, 0, 10000);
        f.eliteChance = clamp(f.eliteChance, 0, 1, 0.03);
        f.championChance = clamp(f.championChance, 0, 1, 0.004);
        f.eliteHealth = clamp(f.eliteHealth, 1, 100, 2.0);
        f.eliteDamage = clamp(f.eliteDamage, 1, 100, 1.4);
        f.championHealth = clamp(f.championHealth, 1, 100, 4.0);
        f.championDamage = clamp(f.championDamage, 1, 100, 1.8);
        f.eliteRankPoints = Math.max(0, Math.min(100000, f.eliteRankPoints));
        f.championRankPoints = Math.max(0, Math.min(100000, f.championRankPoints));
        f.veteranChance = clamp(f.veteranChance, 0, 1, 0.10);
        f.veteranHealth = clamp(f.veteranHealth, 1, 100, 1.4);
        f.veteranDamage = clamp(f.veteranDamage, 1, 100, 1.15);
        f.veteranXp = clamp(f.veteranXp, 0, 1000, 1.5);
        f.eliteXp = clamp(f.eliteXp, 0, 1000, 2.5);
        f.championXp = clamp(f.championXp, 0, 1000, 6.0);
        f.eliteAffixes = clamp(f.eliteAffixes, 0, 4);
        f.championAffixes = clamp(f.championAffixes, 0, 4);
        f.rankedHitCap = clamp(f.rankedHitCap, 0, 1, 0.6);
        if (bestiary == null) bestiary = new BestiaryRules();
        bestiary.tiers = net.schwarz.rotasutils.core.DailyTrack.normalise(
                bestiary.tiers == null || bestiary.tiers.length == 0 ? new long[]{10, 50, 200, 1000} : bestiary.tiers);
        bestiary.damagePerTier = clamp(bestiary.damagePerTier, 0, 1, 0.02);
        bestiary.lootPerTier = clamp(bestiary.lootPerTier, 0, 1, 0.02);
        bestiary.rankPointsPerTier = Math.max(0, Math.min(100000, bestiary.rankPointsPerTier));
        bestiary.revealDropsAt = clamp(bestiary.revealDropsAt, 0, 30);
        if (salvage == null) salvage = new SalvageRules();
        salvage.goldPerValue = Math.max(0, Math.min(1_000_000, salvage.goldPerValue));
        salvage.orePerValue = clamp(salvage.orePerValue, 0, 10, 0.03);
        salvage.refineRefund = clamp(salvage.refineRefund, 0, 1, 0.5);
        sanitizeNemesis();
        sanitizeWorldEvents();
        if (weaponMemory == null) weaponMemory = new WeaponMemoryRules();
        weaponMemory.milestones = net.schwarz.rotasutils.core.DailyTrack.normalise(
                weaponMemory.milestones == null || weaponMemory.milestones.length == 0
                        ? new long[]{50, 250, 1000, 5000} : weaponMemory.milestones);
        if (weaponMemory.milestones.length > 10) {
            weaponMemory.milestones = java.util.Arrays.copyOf(weaponMemory.milestones, 10);
        }
        weaponMemory.damagePerRank = clamp(weaponMemory.damagePerRank, 0, 0.5, 0.02);
        weaponMemory.favoredBonus = clamp(weaponMemory.favoredBonus, 0, 1, 0.05);
        weaponMemory.favoredFromRank = clamp(weaponMemory.favoredFromRank, 0, 10);
        weaponMemory.announceFrom = clamp(weaponMemory.announceFrom, 0, 11);
        if (daily == null) daily = new DailyRules();
        if (daily.boardId == null) daily.boardId = "";
        if (daily.tiers == null || daily.tiers.length == 0) daily.tiers = defaultDailyTiers();
        daily.tiers = sanitizeDailyTiers(daily.tiers);
        if (seasonTrack == null) seasonTrack = new SeasonTrackRules();
        if (seasonTrack.seasonId == null || seasonTrack.seasonId.isBlank()) seasonTrack.seasonId = "s1";
        if (seasonTrack.tiers == null || seasonTrack.tiers.length == 0) seasonTrack.tiers = defaultSeasonTiers();
        seasonTrack.tiers = sanitizeSeasonTiers(seasonTrack.tiers);
        if (events == null) events = new EventRules();
        if (events.rules == null) events.rules = defaultEventRules();
        if (events.rules.length > EventRules.MAX_RULES) {
            events.rules = java.util.Arrays.copyOf(events.rules, EventRules.MAX_RULES);
        }
        java.util.List<EventRule> keptRules = new java.util.ArrayList<>();
        for (EventRule rule : events.rules) {
            // A rule naming a type this build does not have is dropped rather than kept as a trap.
            if (rule == null || rule.eventType() == null) continue;
            if (!net.schwarz.rotasutils.event.EventRules.validFilter(rule.filter)) rule.filter = "";
            rule.filter = rule.filter == null ? "" : rule.filter.trim().toLowerCase(java.util.Locale.ROOT);
            rule.type = rule.eventType().name();
            rule.xpMultiplier = clamp(rule.xpMultiplier, 0, 1000, 1);
            rule.xpFlat = Math.max(0, Math.min(1_000_000_000L, rule.xpFlat));
            rule.gold = Math.max(0, Math.min(1_000_000_000L, rule.gold));
            rule.announce = rule.announcement().name();
            rule.cooldownSeconds = clamp(rule.cooldownSeconds, 0, 86400);
            keptRules.add(rule);
        }
        // Every type always has a plain entry, so a deleted line cannot make a type unreachable.
        for (var type : net.schwarz.rotasutils.event.EventType.values()) {
            boolean present = false;
            for (EventRule rule : keptRules) {
                if (rule.eventType() == type && rule.filter.isEmpty()) { present = true; break; }
            }
            if (!present) keptRules.add(new EventRule(type.name(), ""));
        }
        events.rules = keptRules.toArray(new EventRule[0]);
        if (cards == null) cards = new CardRules();
        cards.maxSockets = clamp(cards.maxSockets, 0, 8);
        cards.dropChance = clamp(cards.dropChance, 0, 1, 0.0005);
        if (cards.entries == null) cards.entries = defaultCards();
        for (CardDef card : cards.entries.values()) {
            if (card == null) continue;
            if (card.name == null) card.name = "";
            if (card.source == null) card.source = "";
            card.fits = switch (card.fits == null ? "" : card.fits.toUpperCase(java.util.Locale.ROOT)) {
                case "WEAPON" -> "WEAPON";
                case "ARMOR" -> "ARMOR";
                default -> "ANY";
            };
            card.chance = Double.isFinite(card.chance) ? Math.min(1, card.chance) : -1;
            card.color |= 0xFF000000;
            if (card.effects == null) card.effects = new String[0];
        }
        if (horse == null) horse = new HorseRules();
        HorseRules h = horse;
        h.pullCost = Math.max(0, h.pullCost);
        h.tenPullCost = Math.max(0, h.tenPullCost);
        if (h.rates == null || h.rates.length != 5) h.rates = new double[]{0.55, 0.25, 0.12, 0.06, 0.02};
        for (int i = 0; i < 5; i++) h.rates[i] = clamp(h.rates[i], 0, 1, 0);
        h.pityRare = clamp(h.pityRare, 0, 100000);
        h.pityEpic = clamp(h.pityEpic, 0, 100000);
        h.pityLegendary = clamp(h.pityLegendary, 0, 100000);
        h.epicRareCoatChance = clamp(h.epicRareCoatChance, 0, 1, 0.35);
        if (h.secretCoats == null) h.secretCoats = new String[0];
        if (h.rareCoats == null) h.rareCoats = new String[0];
        h.baseStableSlots = clamp(h.baseStableSlots, 0, 1000);
        h.maxStableSlots = Math.max(h.baseStableSlots, clamp(h.maxStableSlots, 1, 1000));
        h.slotCostGrowth = clamp(h.slotCostGrowth, 1, 100, 1.5);
        h.summonCooldownSeconds = clamp(h.summonCooldownSeconds, 0, 86400);
        h.recoverySeconds = clamp(h.recoverySeconds, 0, 604800);
        h.potionUsesPerDay = clamp(h.potionUsesPerDay, 0, 1000);
        h.npcSalesPerDay = clamp(h.npcSalesPerDay, 0, 1000);
        if (h.sellRarityBonus == null || h.sellRarityBonus.length != 5) h.sellRarityBonus = new long[]{0, 20, 60, 150, 400};
        h.marketFee = clamp(h.marketFee, 0, 0.9, 0);
        h.marketMaxPrice = Math.max(1, h.marketMaxPrice);
        h.breedBaseCost = Math.max(0, h.breedBaseCost);
        h.breedCostPerLineage = Math.max(0, h.breedCostPerLineage);
        h.gestationMinutes = clamp(h.gestationMinutes, 0, 10080);
        h.breedCooldownMinutes = clamp(h.breedCooldownMinutes, 0, 10080);
        h.breedsPerHorse = clamp(h.breedsPerHorse, 0, 100);
        h.fertileBonusBreeds = clamp(h.fertileBonusBreeds, 0, 100);
        h.breedLevelInheritance = clamp(h.breedLevelInheritance, 0, 1, 0.5);
        h.breedTraitInheritChance = clamp(h.breedTraitInheritChance, 0, 1, 0.5);
        h.breedMutationChance = clamp(h.breedMutationChance, 0, 1, 0.08);
        h.prizedLineMutationBonus = clamp(h.prizedLineMutationBonus, 0, 1, 0.10);
        h.starbornChance = clamp(h.starbornChance, 0, 1, 0.03);
        h.breedRareCoatChance = clamp(h.breedRareCoatChance, 0, 1, 0.35);
        h.breedSecretCoatChance = clamp(h.breedSecretCoatChance, 0, 1, 0.12);
        h.studMaxFee = Math.max(0, h.studMaxFee);
        h.sellPerLineage = Math.max(0, h.sellPerLineage);
        if (h.gachaTraits == null || h.gachaTraits.length != 5) h.gachaTraits = new double[]{0.05, 0.25, 0.6, 1.0, 1.5};
        for (int i = 0; i < 5; i++) h.gachaTraits[i] = clamp(h.gachaTraits[i], 0, 3, 0);
        h.warhorseDamage = clamp(h.warhorseDamage, 0, 5, 0.12);
        h.ironhideArmor = clamp(h.ironhideArmor, 0, 30, 6);
        h.valiantArmor = clamp(h.valiantArmor, 0, 30, 3);
        h.windrunnerSpeed = clamp(h.windrunnerSpeed, 0, 2, 0.10);
        h.goldenBloodPrice = clamp(h.goldenBloodPrice, 0, 10, 0.30);
        h.peddlerPrice = clamp(h.peddlerPrice, 0, 10, 0.10);
        h.starbornPrice = clamp(h.starbornPrice, 0, 10, 0.50);
        h.foragerXp = clamp(h.foragerXp, 0, 10, 0.15);
        h.trailblazerXp = clamp(h.trailblazerXp, 0, 10, 0.25);
        h.peddlerXp = clamp(h.peddlerXp, 0, 10, 0.15);
        h.starbornXp = clamp(h.starbornXp, 0, 10, 0.05);
        if (currency == null || currency.isBlank()) currency = "rotas:gold";
        if (overflowCurrency == null || overflowCurrency.isBlank()) overflowCurrency = "rotas:season_token";
        return this;
    }

    private void sanitizeSocial() {
        if (npcSocial == null) npcSocial = new NpcSocialRules();
        NpcSocialRules s = npcSocial;
        s.chatPoints = clamp(s.chatPoints, 0, 1000);
        s.servicePoints = clamp(s.servicePoints, 0, 1000);
        s.servicePointsPerDay = clamp(s.servicePointsPerDay, 0, 100000);
        if (s.levels == null || s.levels.length == 0) s.levels = new int[]{30, 100, 250, 500};
        java.util.Arrays.sort(s.levels);
        s.discountPerLevel = clamp(s.discountPerLevel, 0, 0.25, 0.03);
        s.companionCost = Math.max(0, s.companionCost);
        s.companionMinutes = clamp(s.companionMinutes, 1, 240);
        if (s.companionEntity == null || s.companionEntity.isBlank()) s.companionEntity = "minecraft:iron_golem";
        s.companionPerLevel = clamp(s.companionPerLevel, 0, 1, 0.02);
        s.rumorsShown = clamp(s.rumorsShown, 0, 20);
    }

    private void sanitizeProgression() {
        sanitizeSocial();
        if (milestones == null) milestones = new MilestoneRules();
        MilestoneRules m = milestones;
        m.every = clamp(m.every, 1, 1000);
        m.goldPerLevel = Math.max(0, m.goldPerLevel);
        m.statPoints = clamp(m.statPoints, 0, 100);
        m.bigStatPoints = clamp(m.bigStatPoints, 0, 100);
        if (m.bigLevels == null) m.bigLevels = new int[0];
        m.bigMultiplier = clamp(m.bigMultiplier, 0, 1000, 5);
        if (exploration == null) exploration = new ExplorationRules();
        ExplorationRules e = exploration;
        e.levelScale = clamp(e.levelScale, 0, 10, 0.05);
        e.zoneBase = Math.max(0, e.zoneBase);
        e.zonePerLevel = Math.max(0, e.zonePerLevel);
        e.waystoneXp = Math.max(0, e.waystoneXp);
        e.newMonsterXp = Math.max(0, e.newMonsterXp);
        if (e.advancementXp == null || e.advancementXp.length != 3) e.advancementXp = new long[]{40, 150, 500};
        for (int i = 0; i < 3; i++) e.advancementXp[i] = Math.max(0, e.advancementXp[i]);
        e.travelBlocks = clamp(e.travelBlocks, 50, 1_000_000);
        e.travelXp = Math.max(0, e.travelXp);
        e.varietyBonus = clamp(e.varietyBonus, 0, 1, 0.05);
        e.varietyMax = clamp(e.varietyMax, 0, 10, 0.25);
        e.varietyWindowMinutes = clamp(e.varietyWindowMinutes, 1, 1440);
    }

    private void sanitizeNpcServices() {
        sanitizeProgression();
        if (npcServices == null) npcServices = new NpcServiceRules();
        NpcServiceRules n = npcServices;
        n.repairPerDurability = clamp(n.repairPerDurability, 0, 1000, 0.4);
        n.repairMinCost = Math.max(0, n.repairMinCost);
        n.disenchantBaseCost = Math.max(0, n.disenchantBaseCost);
        n.disenchantPerLevel = Math.max(0, n.disenchantPerLevel);
        if (n.brews == null) n.brews = new Brew[0];
        n.brews = java.util.Arrays.stream(n.brews).filter(java.util.Objects::nonNull).limit(32).toArray(Brew[]::new);
        for (Brew brew : n.brews) {
            if (brew.effect == null) brew.effect = "";
            brew.amplifier = clamp(brew.amplifier, 0, 9);
            brew.seconds = clamp(brew.seconds, 1, 86400);
            brew.price = Math.max(0, brew.price);
        }
        n.restCost = Math.max(0, n.restCost);
        n.restedMinutes = clamp(n.restedMinutes, 0, 1440);
        n.restedXp = clamp(n.restedXp, 0, 10, 0.10);
        n.homeCost = Math.max(0, n.homeCost);
        n.cleanseCost = Math.max(0, n.cleanseCost);
        n.blessingCost = Math.max(0, n.blessingCost);
        n.blessingMinutes = clamp(n.blessingMinutes, 1, 1440);
        n.liftCurseCost = Math.max(0, n.liftCurseCost);
        n.fortuneCost = Math.max(0, n.fortuneCost);
        n.fortuneMinutes = clamp(n.fortuneMinutes, 1, 1440);
        n.fortuneXp = clamp(n.fortuneXp, 0, 10, 0.20);
        n.badOmenChance = clamp(n.badOmenChance, 0, 1, 0.10);
        if (n.withdrawSteps == null || n.withdrawSteps.length == 0) n.withdrawSteps = new long[]{100, 1000, 10000};
        if (n.bounties == null) n.bounties = new BountyDef[0];
        n.bounties = java.util.Arrays.stream(n.bounties).filter(java.util.Objects::nonNull).limit(128).toArray(BountyDef[]::new);
        for (BountyDef bounty : n.bounties) {
            if (bounty.entity == null) bounty.entity = "";
            bounty.count = clamp(bounty.count, 1, 10000);
            bounty.gold = Math.max(0, bounty.gold);
            bounty.xp = Math.max(0, bounty.xp);
            bounty.minLevel = clamp(bounty.minLevel, 0, 10000);
        }
        n.bountiesPerDay = clamp(n.bountiesPerDay, 0, 20);
        n.bountiesCompletedPerDay = clamp(n.bountiesCompletedPerDay, 0, 100);
        if (n.wanted == null) n.wanted = new Wanted[0];
        n.wanted = java.util.Arrays.stream(n.wanted).filter(java.util.Objects::nonNull).limit(256).toArray(Wanted[]::new);
        for (Wanted wanted : n.wanted) {
            if (wanted.item == null) wanted.item = "";
            wanted.price = Math.max(0, wanted.price);
        }
        n.collectorPicksPerDay = clamp(n.collectorPicksPerDay, 0, 64);
        n.collectorBonus = clamp(n.collectorBonus, 1, 100, 1.5);
        n.collectorDailyItems = clamp(n.collectorDailyItems, 0, 1_000_000);
        n.collectorXpPerGold = clamp(n.collectorXpPerGold, 0, 1000, 0.5);
        n.auctionFee = clamp(n.auctionFee, 0, 0.9, 0.05);
        n.auctionMaxListings = clamp(n.auctionMaxListings, 0, 100);
        n.auctionHours = clamp(n.auctionHours, 1, 24 * 60);
        n.auctionMaxPrice = Math.max(1, n.auctionMaxPrice);
    }

    private void sanitizeNemesis() {
        sanitizeNpcServices();
        if (nemesis == null) nemesis = new NemesisRules();
        if (titles == null) titles = new TitleRules();
        TitleRules t = titles;
        if (t.rarityPoints == null || t.rarityPoints.length != 5) t.rarityPoints = new int[]{1, 2, 3, 5, 8};
        if (t.rarityGold == null || t.rarityGold.length != 5) t.rarityGold = new long[]{50, 150, 400, 1000, 3000};
        if (t.rarityXp == null || t.rarityXp.length != 5) t.rarityXp = new long[]{100, 300, 800, 2000, 5000};
        for (int i = 0; i < 5; i++) {
            t.rarityPoints[i] = clamp(t.rarityPoints[i], 0, 1000);
            t.rarityGold[i] = Math.max(0, t.rarityGold[i]);
            t.rarityXp[i] = Math.max(0, t.rarityXp[i]);
        }
        t.announceFromRarity = clamp(t.announceFromRarity, 0, 5);
        if (t.collection == null) t.collection = new CollectionTier[0];
        t.collection = java.util.Arrays.stream(t.collection).filter(java.util.Objects::nonNull).limit(64)
                .sorted(java.util.Comparator.comparingInt(tier -> tier.points)).toArray(CollectionTier[]::new);
        for (CollectionTier tier : t.collection) {
            tier.points = clamp(tier.points, 0, 100000);
            if (tier.attribute == null) tier.attribute = "";
            if (!Double.isFinite(tier.amount)) tier.amount = 0;
            if (!java.util.Set.of("ADD", "MULTIPLY_BASE", "MULTIPLY_TOTAL").contains(tier.operation)) tier.operation = "ADD";
            if (tier.label == null) tier.label = "";
        }
        NemesisRules n = nemesis;
        n.riseChance = clamp(n.riseChance, 0, 1, 0.5);
        n.riseCooldownMinutes = clamp(n.riseCooldownMinutes, 0, 10080);
        n.maxActive = clamp(n.maxActive, 0, 256);
        n.maxPerPlayer = clamp(n.maxPerPlayer, 0, 16);
        n.maxRank = clamp(n.maxRank, 1, 10);
        n.levelsPerRank = clamp(n.levelsPerRank, 0, 50);
        n.healthPerRank = clamp(n.healthPerRank, 0, 10, 0.5);
        n.damagePerRank = clamp(n.damagePerRank, 0, 10, 0.2);
        n.ambushCooldownMinutes = clamp(n.ambushCooldownMinutes, 1, 10080);
        n.ambushChancePerMinute = clamp(n.ambushChancePerMinute, 0, 1, 0.25);
        n.ambushMinDistance = clamp(n.ambushMinDistance, 6, 64);
        n.ambushMaxDistance = clamp(n.ambushMaxDistance, n.ambushMinDistance, 96);
        n.tauntRadius = clamp(n.tauntRadius, 0, 128);
        n.tauntCooldownSeconds = clamp(n.tauntCooldownSeconds, 10, 86400);
        n.forgetAfterDays = clamp(n.forgetAfterDays, 1, 3650);
        n.goldPerRank = Math.max(0, Math.min(1_000_000, n.goldPerRank));
        n.rankPointsPerRank = Math.max(0, Math.min(100_000, n.rankPointsPerRank));
        if (n.gradeByRank == null || n.gradeByRank.length == 0) n.gradeByRank = new String[]{"medium", "medium", "rare", "rare", "epic"};
        n.revengeMultiplier = clamp(n.revengeMultiplier, 1, 100, 2.0);
        if (n.trophyItem == null || n.trophyItem.isBlank()) n.trophyItem = "minecraft:iron_sword";
        if (n.trophyRangedItem == null || n.trophyRangedItem.isBlank()) n.trophyRangedItem = "minecraft:bow";
        if (n.names == null || n.names.length == 0) n.names = new NemesisRules().names;
        if (n.epithets == null) n.epithets = NemesisRules.defaultEpithets();
        n.epithets.replaceAll((style, list) -> list == null ? new String[0] : list);
    }

    private void sanitizeWorldEvents() {
        if (worldEvents == null) worldEvents = new WorldEventRules();
        WorldEventRules w = worldEvents;
        w.intervalMinutes = clamp(w.intervalMinutes, 1, 1440);
        w.startChance = clamp(w.startChance, 0, 1, 0.6);
        w.durationMinutes = clamp(w.durationMinutes, 1, 1440);
        w.maxActive = clamp(w.maxActive, 0, 8);
        w.wildRadius = clamp(w.wildRadius, 16, 256);
        w.wildMinDistance = clamp(w.wildMinDistance, 0, 2048);
        w.wildMaxDistance = clamp(w.wildMaxDistance, w.wildMinDistance, 4096);
        if (w.types == null) w.types = defaultWorldEvents();
        Map<String, WorldEventDef> kept = new LinkedHashMap<>();
        for (var entry : w.types.entrySet()) {
            if (kept.size() >= WorldEventRules.MAX_TYPES) break;
            String id = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
            WorldEventDef def = entry.getValue();
            if (def == null || !id.matches("[a-z0-9_.-]{1,48}")) continue;
            if (def.name == null || def.name.isBlank()) def.name = id;
            if (def.description == null) def.description = "";
            def.weight = clamp(def.weight, 0, 1000);
            def.mobLevelBonus = clamp(def.mobLevelBonus, -100, 100);
            def.eliteMultiplier = clamp(def.eliteMultiplier, 0, 100, 1);
            def.mobHealth = clamp(def.mobHealth, 0.1, 100, 1);
            def.mobDamage = clamp(def.mobDamage, 0.1, 100, 1);
            def.xpMultiplier = clamp(def.xpMultiplier, 0, 100, 1);
            def.lootMultiplier = clamp(def.lootMultiplier, 0, 100, 1);
            def.coinMultiplier = clamp(def.coinMultiplier, 0, 100, 1);
            def.healingMultiplier = clamp(def.healingMultiplier, 0, 100, 1);
            def.oreBonusChance = clamp(def.oreBonusChance, 0, 1, 0);
            if (def.playerEffects == null) def.playerEffects = new String[0];
            if (def.playerEffects.length > 8) def.playerEffects = java.util.Arrays.copyOf(def.playerEffects, 8);
            if (def.spawns == null) def.spawns = new String[0];
            if (def.spawns.length > 16) def.spawns = java.util.Arrays.copyOf(def.spawns, 16);
            def.waveSize = clamp(def.waveSize, 0, 16);
            def.waveSeconds = clamp(def.waveSeconds, 5, 3600);
            def.maxAlive = clamp(def.maxAlive, 0, 64);
            String goal = def.goal == null ? "" : def.goal.trim().toUpperCase(java.util.Locale.ROOT);
            def.goal = goal.equals("KILL") || goal.equals("MINE") ? goal : "NONE";
            def.goalCount = def.goal.equals("NONE") ? 0 : clamp(def.goalCount, 1, 100_000);
            def.minContribution = clamp(def.minContribution, 1, 100_000);
            def.reward = sanitizeReward(def.reward);
            kept.put(id, def);
        }
        w.types = kept;
    }

    private static TrackReward sanitizeReward(TrackReward reward) {
        TrackReward r = reward == null ? new TrackReward() : reward;
        r.gold = Math.max(0, Math.min(1_000_000_000L, r.gold));
        r.xp = Math.max(0, Math.min(1_000_000_000L, r.xp));
        r.rankPoints = Math.max(0, Math.min(1_000_000L, r.rankPoints));
        if (r.items == null) r.items = new String[0];
        if (r.items.length > 16) r.items = java.util.Arrays.copyOf(r.items, 16);
        return r;
    }

    /** Rungs sorted by what they need, no two at the same count, at most the track's limit. */
    private static DailyTier[] sanitizeDailyTiers(DailyTier[] tiers) {
        java.util.List<DailyTier> kept = new java.util.ArrayList<>();
        for (DailyTier tier : tiers) {
            if (tier == null) continue;
            tier.completions = Math.max(1, Math.min(1000, tier.completions));
            tier.reward = sanitizeReward(tier.reward);
            kept.add(tier);
        }
        kept.sort(java.util.Comparator.comparingLong(tier -> tier.completions));
        java.util.List<DailyTier> unique = new java.util.ArrayList<>();
        for (DailyTier tier : kept) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).completions != tier.completions) unique.add(tier);
        }
        while (unique.size() > net.schwarz.rotasutils.core.DailyTrack.MAX_TIERS) unique.remove(unique.size() - 1);
        return unique.toArray(new DailyTier[0]);
    }

    private static SeasonTier[] sanitizeSeasonTiers(SeasonTier[] tiers) {
        java.util.List<SeasonTier> kept = new java.util.ArrayList<>();
        for (SeasonTier tier : tiers) {
            if (tier == null) continue;
            tier.points = Math.max(1, Math.min(10_000_000L, tier.points));
            if (tier.name == null) tier.name = "";
            tier.reward = sanitizeReward(tier.reward);
            kept.add(tier);
        }
        kept.sort(java.util.Comparator.comparingLong(tier -> tier.points));
        java.util.List<SeasonTier> unique = new java.util.ArrayList<>();
        for (SeasonTier tier : kept) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).points != tier.points) unique.add(tier);
        }
        while (unique.size() > net.schwarz.rotasutils.core.DailyTrack.MAX_TIERS) unique.remove(unique.size() - 1);
        return unique.toArray(new SeasonTier[0]);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static SeasonRules fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new SeasonRules();
        }
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        SeasonRules rules = GSON.fromJson(object, SeasonRules.class);
        if (rules == null) {
            return new SeasonRules();
        }
        return rules.sanitize();
    }

    public SeasonRules copy() {
        return fromJson(toJson());
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long[] fourLongs(long[] values, long[] fallback) {
        if (values == null || values.length != 4) return fallback;
        long[] result = new long[4];
        for (int i = 0; i < 4; i++) result[i] = Math.max(0, values[i]);
        return result;
    }

    private static int[] fourInts(int[] values, int[] fallback) {
        if (values == null || values.length != 4) return fallback;
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            result[i] = Math.max(i == 0 ? 1 : result[i - 1], values[i]);
        }
        return result;
    }
}
