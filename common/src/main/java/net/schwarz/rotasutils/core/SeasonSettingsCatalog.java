package net.schwarz.rotasutils.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sections, names and hints for the season editor. The editor lists whatever {@code season.json}
 * holds (see {@link SettingsTree}); this only decides where a value is shown and what it is called.
 * A value with no name here is still listed, under its own key, so a new setting is never hidden.
 */
public final class SeasonSettingsCatalog {
    public record Section(String id, String title, String blurb) { }

    public static final String OTHER = "other";

    public static final List<Section> SECTIONS = List.of(
            new Section("leveling", "เลเวลและอาชีพ", "เส้นโค้ง EXP ของอาชีพหลักและอาชีพรอง"),
            new Section("monster", "EXP มอนสเตอร์", "โบนัสตามเลเวลมอน การลด EXP เมื่อเลเวลห่าง บอส"),
            new Section("party", "ปาร์ตี้", "การแบ่ง EXP ในปาร์ตี้"),
            new Section("repeatable", "เควสทำซ้ำ", "EXP ลดลงเมื่อทำเควสเดิมซ้ำ"),
            new Section("production", "ผลิตและเก็บของ", "EXP การผลิต การล็อกสูตร และการเก็บของ"),
            new Section("crafter", "ช่างรับจ้าง", "ค่าจ้างและโควตาช่าง NPC"),
            new Section("rank", "แรงค์", "แต้มแรงค์ เกณฑ์ และสิทธิพิเศษ"),
            new Section("stats", "สเตตัส", "STR VIT INT AGI: แต้มต่อเลเวล เพดาน ผลต่อแต้ม ค่ารีเซ็ต"),
            new Section("pvp", "PvP / PvE", "ความสมดุลการต่อสู้ระหว่างผู้เล่น"),
            new Section("farming", "ฟาร์ม", "โชค คอมโบ และมอนอีลิต/แชมเปี้ยน"),
            new Section("drops", "ดรอป", "ดรอปตามแรงค์มอน เกรดของ และตัวกรอง"),
            new Section("cards", "การ์ด", "การ์ดมอนและช่องใส่การ์ด"),
            new Section("refine", "ตีบวก", "โอกาส ราคา และแร่ที่ใช้ตีบวก"),
            new Section("runes", "รูน", "ช่องรูนและราคาเปิดช่อง"),
            new Section("salvage", "แยกชิ้นส่วน", "มูลค่าของที่แยกได้"),
            new Section("weaponMemory", "ความทรงจำอาวุธ", "โบนัสอาวุธที่ฆ่ามอนมามาก"),
            new Section("bestiary", "สมุดมอนสเตอร์", "ขั้นของสมุดและโบนัส"),
            new Section("nemesis", "ศัตรูคู่แค้น", "มอนที่ฆ่าผู้เล่นแล้วกลับมาล่า"),
            new Section("worldEvents", "เหตุการณ์โลก", "เหตุการณ์สุ่มในโลกและรางวัล"),
            new Section("events", "กฎกิจกรรม", "ตัวคูณ EXP/เงินตามการกระทำ"),
            new Section("daily", "ภารกิจรายวัน", "ขั้นรางวัลของภารกิจวันนี้"),
            new Section("seasonTrack", "เส้นทางซีซั่น", "ขั้นรางวัลตามแต้มแรงค์ทั้งซีซั่น"),
            new Section("horse", "ม้า", "สุ่มม้า คอก ขาย และตลาด"),
            new Section("titles", "ฉายา", "ใครได้ฉายาอัตโนมัติ (แอดมิน/ครีเอทีฟ) และการถอดฉายา"),
            new Section(OTHER, "อื่น ๆ", "ค่าที่ยังไม่มีหมวด"));

    /** Internal bookkeeping nobody should edit by hand. */
    private static final Set<String> HIDDEN = Set.of();

    private static final Map<String, String> TOP_SECTION = topSections();
    private static final Map<String, String> LABELS = labels();
    private static final Map<String, String> KEY_LABELS = keyLabels();
    private static final Map<String, String> HELP = help();

    private SeasonSettingsCatalog() {
    }

    public static boolean hidden(List<String> path) {
        return path.size() == 1 && HIDDEN.contains(path.get(0));
    }

    /** The section a row belongs to, decided by its top-level key. */
    public static String sectionOf(List<String> path) {
        if (path.isEmpty()) return OTHER;
        return TOP_SECTION.getOrDefault(path.get(0), OTHER);
    }

    public static Section section(String id) {
        for (Section section : SECTIONS) {
            if (section.id().equals(id)) return section;
        }
        return SECTIONS.get(SECTIONS.size() - 1);
    }

    /** Whether a name was written for this row, rather than one made from its key. */
    public static boolean named(List<String> path) {
        if (isSectionRoot(path)) return true;
        return LABELS.containsKey(String.join(".", path)) || KEY_LABELS.containsKey(lastKey(path))
                || isIndex(lastKey(path));
    }

    public static String label(List<String> path) {
        String joined = String.join(".", path);
        String label = LABELS.get(joined);
        if (label != null) return label;
        if (isSectionRoot(path)) return section(path.get(0)).title();
        String key = lastKey(path);
        if (isIndex(key)) return "ขั้นที่ " + (Integer.parseInt(key) + 1);
        label = KEY_LABELS.get(key);
        if (label != null) return label;
        return humanize(key);
    }

    /** Fixed options for a text value, clicked through instead of typed; empty when it is free text. */
    public static List<String> choices(List<String> path) {
        String joined = String.join(".", path);
        if (joined.equals("refine.onFail")) return List.of("DOWNGRADE", "RESET_TO_SAFE", "BREAK", "KEEP");
        return switch (lastKey(path)) {
            case "goal" -> List.of("KILL", "MINE", "NONE");
            case "announce" -> List.of("OFF", "PLAYER", "SERVER");
            case "fits" -> List.of("ANY", "WEAPON", "ARMOR");
            default -> List.of();
        };
    }

    /**
     * Which registry a text value (or each item of a text list) names, so the editor can offer a
     * searchable picker instead of typing ids by hand. Null when the value is free text.
     */
    public static net.schwarz.rotasutils.data.ParamSpec.ParamKind picker(List<String> path) {
        String key = lastKey(path);
        String parent = path.size() > 1 ? path.get(path.size() - 2) : "";
        if (isIndex(key) && path.size() > 1) {
            key = parent;
            parent = path.size() > 2 ? path.get(path.size() - 3) : "";
        }
        return switch (key) {
            case "trophyItem", "trophyRangedItem", "gatherFishFallback", "weaponOre", "armorOre",
                    "weaponOreEnriched", "armorOreEnriched", "blocked" -> net.schwarz.rotasutils.data.ParamSpec.ParamKind.ITEM;
            case "items" -> parent.equals("reward") ? net.schwarz.rotasutils.data.ParamSpec.ParamKind.ITEM : null;
            case "spawns", "ignore", "source" -> net.schwarz.rotasutils.data.ParamSpec.ParamKind.ENTITY;
            case "playerEffects" -> net.schwarz.rotasutils.data.ParamSpec.ParamKind.EFFECT;
            default -> null;
        };
    }

    public static String help(List<String> path) {
        String joined = String.join(".", path);
        String help = HELP.get(joined);
        if (help != null) return help;
        // Entries inside maps and lists (an event type, a tier, a card) are named by pattern:
        // "*" is any one key, a leading "**" any number of keys. The most specific pattern wins.
        String best = null;
        int bestScore = -1;
        for (Map.Entry<String, String> entry : HELP.entrySet()) {
            if (!entry.getKey().contains("*")) continue;
            int score = matchScore(entry.getKey().split("\\."), path);
            if (score > bestScore) {
                bestScore = score;
                best = entry.getValue();
            }
        }
        if (best != null) return best;
        return HELP.getOrDefault("*." + lastKey(path), "");
    }

    /** Literal keys matched, or -1 when the pattern does not fit the path. */
    static int matchScore(String[] pattern, List<String> path) {
        boolean anyPrefix = pattern.length > 0 && pattern[0].equals("**");
        int start = anyPrefix ? 1 : 0;
        int length = pattern.length - start;
        if (anyPrefix ? path.size() < length : path.size() != length) return -1;
        if (!anyPrefix && length == 2 && pattern[0].equals("*")) return -1; // the old "*.key" form, handled last
        int offset = path.size() - length;
        int score = 0;
        for (int i = 0; i < length; i++) {
            String part = pattern[start + i];
            if (part.equals("*")) continue;
            if (!part.equals(path.get(offset + i))) return -1;
            score++;
        }
        return score + (anyPrefix ? 0 : 100);
    }

    /** A nested object that is a whole section, such as {@code farming}; the section title names it. */
    private static boolean isSectionRoot(List<String> path) {
        return path.size() == 1 && sectionOf(path).equals(path.get(0));
    }

    private static String lastKey(List<String> path) {
        return path.isEmpty() ? "" : path.get(path.size() - 1);
    }

    private static boolean isIndex(String key) {
        if (key.isEmpty()) return false;
        for (int index = 0; index < key.length(); index++) {
            if (!Character.isDigit(key.charAt(index))) return false;
        }
        return true;
    }

    /** {@code comboWindowSeconds} reads as {@code Combo window seconds}. */
    static String humanize(String key) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char c = key.charAt(index);
            if (index > 0 && Character.isUpperCase(c) && Character.isLowerCase(key.charAt(index - 1))) out.append(' ');
            out.append(index == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return out.toString().replace('_', ' ');
    }

    private static Map<String, String> topSections() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : List.of("enabled", "currency", "mainBaseXp", "mainExponent", "mainMaxLevel", "subBaseXp",
                "subExponent", "subMaxLevel", "subJobProductionOnly")) map.put(key, "leveling");
        for (String key : List.of("monsterLevelBonus", "overLevelGrace", "overLevelPenaltyPerLevel",
                "overLevelMaxPenalty", "minibossMultiplier", "bossMultiplier")) map.put(key, "monster");
        for (String key : List.of("partyBonusPerMember", "partyRadius", "partyMaxSize", "partyLevelReach")) map.put(key, "party");
        for (String key : List.of("overflowCurrency", "overflowXpPerToken")) {
            map.put(key, "production");
        }
        for (String key : List.of("repeatableFullRuns", "repeatableHalfRuns", "repeatableHalfRate", "repeatableLowRate")) {
            map.put(key, "repeatable");
        }
        for (String key : List.of("tierXp", "tierMaxLevel", "craftGraceLevels", "craftPenaltyPerLevel", "craftMaxPenalty",
                "firstCraftMultiplier", "lockRecipes", "subCapToOverflow", "lockSmelting", "lockBrewing", "lockStarterRows",
                "gatherFreeLevel", "gatherDropChance", "gatherMaxCount", "gatherFishFallback")) map.put(key, "production");
        for (String key : List.of("crafterFee", "crafterPriceMultiplier", "crafterDailyLimit", "crafterHideMarker")) {
            map.put(key, "crafter");
        }
        for (String key : List.of("rankExp", "repeatableRankCapPerDay", "seasonRankTotal", "rankThresholds", "rankPerks")) {
            map.put(key, "rank");
        }
        for (String key : List.of("stats", "defenseScale", "magicBonusMaxRatio")) map.put(key, "stats");
        for (String key : List.of("pveLevelParity", "pvpDamageMultiplier", "pvpStatEfficiency", "pvpEvasionScale",
                "pvpMaxHitShare", "pvpLevelGrace", "pvpLevelGapPerLevel", "pvpLevelGapMax")) map.put(key, "pvp");
        for (Section section : SECTIONS) {
            // Sections named after a nested object: every value inside it lands there.
            if (!map.containsValue(section.id()) && !section.id().equals(OTHER)) map.put(section.id(), section.id());
        }
        return map;
    }

    private static Map<String, String> labels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("enabled", "เปิดใช้กฎซีซั่น");
        map.put("currency", "สกุลเงินหลัก");
        map.put("mainBaseXp", "อาชีพหลัก: EXP ฐาน");
        map.put("mainExponent", "อาชีพหลัก: เลขชี้กำลัง");
        map.put("mainMaxLevel", "อาชีพหลัก: เลเวลสูงสุด");
        map.put("subBaseXp", "อาชีพรอง: EXP ฐาน");
        map.put("subExponent", "อาชีพรอง: เลขชี้กำลัง");
        map.put("subMaxLevel", "อาชีพรอง: เลเวลสูงสุด");
        map.put("subJobProductionOnly", "อาชีพรองได้ EXP จากการผลิตเท่านั้น");
        map.put("monsterLevelBonus", "โบนัส EXP ต่อเลเวลมอน");
        map.put("overLevelGrace", "เลเวลต่างที่ยังไม่ลด EXP");
        map.put("overLevelPenaltyPerLevel", "ลด EXP ต่อเลเวลที่เกิน");
        map.put("overLevelMaxPenalty", "ลด EXP สูงสุด");
        map.put("minibossMultiplier", "ตัวคูณ EXP มินิบอส");
        map.put("bossMultiplier", "ตัวคูณ EXP บอส");
        map.put("partyBonusPerMember", "โบนัสต่อสมาชิก");
        map.put("partyRadius", "ระยะรับ EXP (บล็อก)");
        map.put("partyMaxSize", "สมาชิกสูงสุดที่แบ่ง EXP");
        map.put("partyLevelReach", "ระยะเลเวลที่ได้ EXP เต็ม");
        map.put("overflowCurrency", "สกุลเงินจาก EXP ส่วนเกิน");
        map.put("overflowXpPerToken", "EXP ส่วนเกินต่อ 1 เหรียญ");
        map.put("repeatableFullRuns", "รอบที่ได้เต็ม");
        map.put("repeatableHalfRuns", "รอบสุดท้ายของช่วงครึ่ง");
        map.put("repeatableHalfRate", "อัตราช่วงครึ่ง");
        map.put("repeatableLowRate", "อัตราหลังจากนั้น");
        map.put("tierXp", "EXP ฐานระดับ A,B,C,D");
        map.put("tierMaxLevel", "เลเวลสูงสุดของระดับ A,B,C,D");
        map.put("craftGraceLevels", "เลเวลเกินระดับที่ยังไม่ลด EXP");
        map.put("craftPenaltyPerLevel", "ลด EXP ต่อเลเวลที่เกิน");
        map.put("craftMaxPenalty", "ลด EXP สูงสุด");
        map.put("firstCraftMultiplier", "ตัวคูณคราฟต์ครั้งแรก");
        map.put("lockRecipes", "ล็อกสูตรที่อาชีพรองยังไม่ปลด");
        map.put("subCapToOverflow", "อาชีพรองเต็มแล้วเปลี่ยน EXP เป็นเหรียญ");
        map.put("lockSmelting", "ล็อกของหลอมที่อาชีพรองยังไม่ปลด");
        map.put("lockBrewing", "ล็อกยาที่อาชีพรองยังไม่ปลด");
        map.put("lockStarterRows", "ล็อกสูตรเลเวล 1 ด้วย");
        map.put("gatherFreeLevel", "เก็บของ: เลเวลที่ทุกคนเก็บได้เต็ม");
        map.put("gatherDropChance", "เก็บของ: โอกาสได้ของเมื่อไม่มีอาชีพรอง");
        map.put("gatherMaxCount", "เก็บของ: จำนวนสูงสุดต่อกอง");
        map.put("gatherFishFallback", "ตกปลา: ของที่ได้แทนเมื่อปลาหลุด");
        map.put("crafterFee", "ค่าจ้างระดับ A,B,C,D");
        map.put("crafterPriceMultiplier", "ตัวคูณราคา");
        map.put("crafterDailyLimit", "จ้างได้ต่อวัน (0 = ไม่จำกัด)");
        map.put("crafterHideMarker", "ซ่อนเครื่องหมายเหนือหัว");
        map.put("rankExp", "แต้มแรงค์ต่อประเภทเควส");
        map.put("repeatableRankCapPerDay", "แต้มแรงค์จากเควสทำซ้ำต่อวัน");
        map.put("seasonRankTotal", "แต้มแรงค์รวมทั้งซีซั่น");
        map.put("rankThresholds", "เกณฑ์แรงค์ (สัดส่วน)");
        map.put("rankPerks", "สิทธิพิเศษของแต่ละแรงค์");
        map.put("stats.startPoints", "แต้มเริ่มต้น");
        map.put("stats.pointsPerLevel", "แต้มต่อเลเวล");
        map.put("stats.maxPerStat", "สเตตัสละไม่เกิน (แต้ม)");
        map.put("stats.respecCost", "ค่ารีเซ็ตสเตตัส");
        map.put("stats.strAttack", "STR: พลังโจมตีต่อแต้ม");
        map.put("stats.vitHealth", "VIT: พลังชีวิตต่อแต้ม");
        map.put("stats.intMagic", "INT: พลังเวทย์ต่อแต้ม");
        map.put("stats.agiAttackSpeed", "AGI: ความเร็วโจมตีต่อแต้ม");
        map.put("stats.agiDodge", "AGI: โอกาสหลบต่อแต้ม");
        map.put("defenseScale", "ค่าคงที่พลังป้องกัน");
        map.put("magicBonusMaxRatio", "โบนัสเวทสูงสุด (เท่าของพลังฐาน)");
        map.put("pveLevelParity", "PvE: ความเท่าเทียมเลเวล");
        map.put("pvpDamageMultiplier", "PvP: ตัวคูณดาเมจ");
        map.put("pvpStatEfficiency", "PvP: ประสิทธิภาพสเตตัส");
        map.put("pvpEvasionScale", "PvP: ตัวคูณหลบหลีก");
        map.put("pvpMaxHitShare", "PvP: ดาเมจสูงสุดต่อครั้ง (สัดส่วน HP)");
        map.put("pvpLevelGrace", "PvP: เลเวลต่างที่ยังไม่ปรับ");
        map.put("pvpLevelGapPerLevel", "PvP: ปรับต่อเลเวลที่ต่าง");
        map.put("pvpLevelGapMax", "PvP: ปรับสูงสุด");

        map.put("horse.enabled", "เปิดระบบม้า");
        map.put("horse.pullCost", "ราคาสุ่ม 1 ครั้ง");
        map.put("horse.tenPullCost", "ราคาสุ่ม 10 ครั้ง");
        map.put("horse.rates", "โอกาสแต่ละระดับ");
        map.put("horse.pityRare", "การันตีหายากทุก");
        map.put("horse.pityEpic", "การันตีมหากาพย์ทุก");
        map.put("horse.pityLegendary", "การันตีตำนานทุก");
        map.put("horse.epicRareCoatChance", "โอกาสสีขนหายากของมหากาพย์");
        map.put("horse.secretCoats", "สีขนลับ (ว่าง = สีที่ผสมไม่ได้)");
        map.put("horse.rareCoats", "สีขนหายาก");
        map.put("horse.baseStableSlots", "ช่องคอกเริ่มต้น");
        map.put("horse.slotBaseCost", "ราคาช่องคอกแรก");
        map.put("horse.slotCostGrowth", "ราคาช่องคอกเพิ่มขึ้น");
        map.put("horse.maxStableSlots", "ช่องคอกสูงสุด");
        map.put("horse.summonCooldownSeconds", "คูลดาวน์เรียกม้า (วินาที)");
        map.put("horse.recoverySeconds", "เวลาพักฟื้นหลังสลบ (วินาที)");
        map.put("horse.potionUsesPerDay", "ยา XP ต่อตัวต่อวัน");
        map.put("horse.bredHorsesSellToNpc", "ม้าผสม/ม้าป่าขายให้ NPC ได้");
        map.put("horse.npcSalesPerDay", "ขายให้ NPC ต่อวัน");
        map.put("horse.sellBase", "ราคาขายพื้นฐาน");
        map.put("horse.sellPerSkillLevel", "ราคาต่อเลเวลสกิล");
        map.put("horse.sellPerAffinityLevel", "ราคาต่อเลเวลความผูกพัน");
        map.put("horse.sellSecretCoat", "โบนัสสีขนลับ");
        map.put("horse.sellRareCoat", "โบนัสสีขนหายาก");
        map.put("horse.sellRarityBonus", "โบนัสตามระดับกำเนิด");
        map.put("horse.marketFee", "ค่าธรรมเนียมตลาด (0-0.9)");
        map.put("horse.marketMaxPrice", "ราคาขายสูงสุดในตลาด");

        map.put("farming.luckEnabled", "โชค: เปิดใช้");
        map.put("farming.lootChancePerLuck", "โชค: โอกาสของดรอปต่อแต้ม");
        map.put("farming.cardChancePerLuck", "โชค: โอกาสการ์ดต่อแต้ม");
        map.put("farming.luckBonusCap", "โชค: โบนัสสูงสุด");
        map.put("farming.comboEnabled", "คอมโบ: เปิดใช้");
        map.put("farming.comboWindowSeconds", "คอมโบ: เวลาต่อคอมโบ (วินาที)");
        map.put("farming.comboXpPerKill", "คอมโบ: EXP เพิ่มต่อตัว");
        map.put("farming.comboLootPerKill", "คอมโบ: โอกาสดรอปเพิ่มต่อตัว");
        map.put("farming.comboCap", "คอมโบ: โบนัสหยุดที่กี่ตัว");
        map.put("farming.eliteEnabled", "อีลิต: เปิดใช้");
        map.put("farming.veteranChance", "โอกาสเกิดเป็นเวเทอแรน");
        map.put("farming.veteranHealth", "เวเทอแรน: ตัวคูณ HP");
        map.put("farming.veteranDamage", "เวเทอแรน: ตัวคูณดาเมจ");
        map.put("farming.eliteChance", "โอกาสเกิดเป็นอีลิต");
        map.put("farming.championChance", "โอกาสเกิดเป็นแชมเปี้ยน");
        map.put("farming.eliteHealth", "อีลิต: ตัวคูณ HP");
        map.put("farming.eliteDamage", "อีลิต: ตัวคูณดาเมจ");
        map.put("farming.championHealth", "แชมเปี้ยน: ตัวคูณ HP");
        map.put("farming.championDamage", "แชมเปี้ยน: ตัวคูณดาเมจ");
        map.put("farming.eliteRankPoints", "อีลิต: แต้มแรงค์");
        map.put("farming.championRankPoints", "แชมเปี้ยน: แต้มแรงค์");
        map.put("farming.veteranXp", "เวเทอแรน: ตัวคูณ EXP");
        map.put("farming.eliteXp", "อีลิต: ตัวคูณ EXP");
        map.put("farming.championXp", "แชมเปี้ยน: ตัวคูณ EXP");
        map.put("farming.affixesEnabled", "เปิดความสามารถพิเศษ (affix)");
        map.put("farming.eliteAffixes", "อีลิต: จำนวน affix");
        map.put("farming.championAffixes", "แชมเปี้ยน: จำนวน affix");
        map.put("farming.rankedHitCap", "ดาเมจสูงสุดต่อครั้งจากมอนแรงค์ (สัดส่วน HP)");
        map.put("farming.rankAura", "ออร่าสีตามแรงค์");
        map.put("farming.announceChampion", "ประกาศเมื่อแชมเปี้ยนเกิด");

        map.put("bestiary.tiers", "จำนวนที่ต้องฆ่าแต่ละขั้น");
        map.put("bestiary.damagePerTier", "ดาเมจเพิ่มต่อขั้น");
        map.put("bestiary.lootPerTier", "ดรอปเพิ่มต่อขั้น");
        map.put("bestiary.rankPointsPerTier", "แต้มแรงค์ต่อขั้น");
        map.put("bestiary.revealDropsAt", "แสดงของดรอปที่ขั้น");

        map.put("salvage.goldPerValue", "เงินต่อมูลค่า 1");
        map.put("salvage.orePerValue", "แร่ต่อมูลค่า 1");
        map.put("salvage.refineRefund", "คืนแร่ตีบวก (สัดส่วน)");
        map.put("salvage.returnCards", "คืนการ์ด");

        map.put("titles.staffEarnTitles", "แอดมิน (OP) ได้ฉายาอัตโนมัติ");
        map.put("titles.creativeEarnTitles", "โหมดครีเอทีฟ/สเปกเตเตอร์ได้ฉายาอัตโนมัติ");
        map.put("titles.revokeBlocksReEarn", "ฉายาที่ถูกถอด จะไม่ได้คืนอัตโนมัติ");
        map.put("nemesis.riseChance", "โอกาสกลายเป็นคู่แค้น");
        map.put("nemesis.riseCooldownMinutes", "คูลดาวน์เกิดต่อผู้เล่น (นาที)");
        map.put("nemesis.maxActive", "คู่แค้นสูงสุดทั้งเซิร์ฟ");
        map.put("nemesis.maxPerPlayer", "คู่แค้นสูงสุดต่อผู้เล่น");
        map.put("nemesis.maxRank", "แรงค์สูงสุด");
        map.put("nemesis.levelsPerRank", "เลเวลเพิ่มต่อแรงค์");
        map.put("nemesis.healthPerRank", "HP เพิ่มต่อแรงค์");
        map.put("nemesis.damagePerRank", "ดาเมจเพิ่มต่อแรงค์");
        map.put("nemesis.ambushEnabled", "ซุ่มโจมตี: เปิดใช้");
        map.put("nemesis.ambushCooldownMinutes", "ซุ่มโจมตี: คูลดาวน์ (นาที)");
        map.put("nemesis.ambushChancePerMinute", "ซุ่มโจมตี: โอกาสต่อนาที");
        map.put("nemesis.ambushMinDistance", "ซุ่มโจมตี: ระยะใกล้สุด");
        map.put("nemesis.ambushMaxDistance", "ซุ่มโจมตี: ระยะไกลสุด");
        map.put("nemesis.tauntRadius", "ระยะพูดเยาะเย้ย");
        map.put("nemesis.tauntCooldownSeconds", "คูลดาวน์เยาะเย้ย (วินาที)");
        map.put("nemesis.forgetAfterDays", "ลืมคู่แค้นหลังกี่วัน");
        map.put("nemesis.goldPerRank", "ค่าหัวต่อแรงค์ (เงิน)");
        map.put("nemesis.rankPointsPerRank", "ค่าหัวต่อแรงค์ (แต้มแรงค์)");
        map.put("nemesis.gradeByRank", "เกรดดรอปตามแรงค์");
        map.put("nemesis.revengeMultiplier", "ตัวคูณแก้แค้นด้วยตัวเอง");
        map.put("nemesis.trophyEnabled", "ถ้วยรางวัล: เปิดใช้");
        map.put("nemesis.trophyItem", "ถ้วยรางวัล: ไอเทมระยะประชิด");
        map.put("nemesis.trophyRangedItem", "ถ้วยรางวัล: ไอเทมระยะไกล");
        map.put("nemesis.trophyRefine", "ถ้วยรางวัล: ตีบวกตามแรงค์");
        map.put("nemesis.names", "ชื่อคู่แค้น");
        map.put("nemesis.epithets", "ฉายาตามวิธีฆ่า");

        map.put("worldEvents.intervalMinutes", "สุ่มทุกกี่นาที");
        map.put("worldEvents.startChance", "โอกาสเริ่มแต่ละรอบ");
        map.put("worldEvents.durationMinutes", "ระยะเวลา (นาที)");
        map.put("worldEvents.maxActive", "พร้อมกันสูงสุด");
        map.put("worldEvents.wildRadius", "รัศมีในป่า (บล็อก)");
        map.put("worldEvents.wildMinDistance", "ระยะจากผู้เล่นใกล้สุด");
        map.put("worldEvents.wildMaxDistance", "ระยะจากผู้เล่นไกลสุด");
        map.put("worldEvents.types", "ประเภทเหตุการณ์");

        map.put("weaponMemory.milestones", "จำนวนฆ่าแต่ละแรงค์");
        map.put("weaponMemory.damagePerRank", "ดาเมจเพิ่มต่อแรงค์");
        map.put("weaponMemory.favoredBonus", "โบนัสกับเหยื่อโปรด");
        map.put("weaponMemory.favoredFromRank", "เหยื่อโปรดเริ่มที่แรงค์");
        map.put("weaponMemory.announceFrom", "ประกาศทั้งเซิร์ฟตั้งแต่แรงค์");

        map.put("daily.boardId", "บอร์ดภารกิจวันนี้ (ว่าง = เควสรายวันทั้งหมด)");
        map.put("daily.countDailyRepeat", "นับเควสรายวันที่ไม่อยู่บนบอร์ด");
        map.put("daily.announceOnJoin", "แจ้งเมื่อเข้าเกมครั้งแรกของวัน");
        map.put("daily.tiers", "ขั้นรางวัล");
        map.put("seasonTrack.seasonId", "รหัสซีซั่น (เปลี่ยน = เริ่มรับใหม่)");
        map.put("seasonTrack.tiers", "ขั้นรางวัล");

        map.put("events.rules", "กฎ");
        map.put("cards.maxSockets", "ช่องการ์ดสูงสุด");
        map.put("cards.dropChance", "โอกาสดรอปการ์ดพื้นฐาน");
        map.put("cards.entries", "รายการการ์ด");

        map.put("refine.maxLevel", "ตีบวกสูงสุด");
        map.put("refine.safeLevel", "ตีบวกปลอดภัยถึง");
        map.put("refine.chances", "โอกาสสำเร็จแต่ละขั้น");
        map.put("refine.onFail", "เมื่อล้มเหลว");
        map.put("refine.attackPerLevel", "พลังโจมตีต่อขั้น");
        map.put("refine.attackPerOverLevel", "พลังโจมตีต่อขั้นเกินปลอดภัย");
        map.put("refine.defensePerLevel", "พลังป้องกันต่อขั้น");
        map.put("refine.defensePerOverLevel", "พลังป้องกันต่อขั้นเกินปลอดภัย");
        map.put("refine.goldPerAttempt", "ราคาต่อครั้ง");
        map.put("refine.goldGrowth", "ราคาเพิ่มขึ้นต่อขั้น");
        map.put("refine.enrichedBonus", "โบนัสแร่เข้มข้น");
        map.put("refine.blessingBonus", "โบนัสพร");
        map.put("refine.announceFrom", "ประกาศตั้งแต่ขั้น");
        map.put("refine.weaponOre", "แร่อาวุธ");
        map.put("refine.armorOre", "แร่เกราะ");
        map.put("refine.weaponOreEnriched", "แร่อาวุธเข้มข้น");
        map.put("refine.armorOreEnriched", "แร่เกราะเข้มข้น");
        map.put("runes.slotLevels", "ปลดช่องที่ตีบวกขั้น");
        map.put("runes.goldPerSlot", "ราคาเปิดช่อง");

        map.put("drops.ranks", "ดรอปตามแรงค์มอน");
        map.put("drops.grades", "เกรดของดรอป");
        map.put("drops.plain", "มอนทั่วไป (ไม่มีเลเวล)");
        map.put("drops.plain.hostileOnly", "เฉพาะมอนศัตรู");
        map.put("drops.plain.rule", "กฎพื้นฐาน");
        map.put("drops.plain.byEntity", "กฎแยกตามมอน");
        map.put("drops.plain.ignore", "มอนที่ไม่ดรอป");
        map.put("drops.filter", "ตัวกรองดรอปวานิลลา");
        map.put("drops.filter.blocked", "ไอเทมที่ห้ามดรอป");
        map.put("drops.filter.byEntity", "ห้ามดรอปแยกตามมอน");
        return map;
    }

    /** Names by key alone, for values that repeat inside maps and lists. */
    private static Map<String, String> keyLabels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("enabled", "เปิดใช้");
        map.put("name", "ชื่อ");
        map.put("description", "คำอธิบาย");
        map.put("title", "ฉายา");
        map.put("gold", "เงิน");
        map.put("xp", "EXP");
        map.put("rankPoints", "แต้มแรงค์");
        map.put("items", "ไอเทม");
        map.put("reward", "รางวัล");
        map.put("points", "แต้มแรงค์ที่ต้องใช้");
        map.put("completions", "ภารกิจที่ต้องทำ");
        map.put("coinChance", "โอกาสดรอปเงิน");
        map.put("coinMin", "เงินต่ำสุดต่อตัว");
        map.put("coinMax", "เงินสูงสุดต่อตัว");
        map.put("coinPerLevel", "เงินเพิ่มต่อเลเวลมอน");
        map.put("coinMultiplier", "ตัวคูณเงิน");
        map.put("lootChance", "โอกาสดรอปของ");
        map.put("grades", "เกรดที่สุ่ม");
        map.put("minGold", "เงินต่ำสุด");
        map.put("maxGold", "เงินสูงสุด");
        map.put("shopDiscount", "ส่วนลดร้านค้า");
        map.put("stableSlots", "ช่องคอกเพิ่ม");
        map.put("marketFeeDiscount", "ส่วนลดค่าธรรมเนียมตลาด");
        map.put("weight", "น้ำหนักการสุ่ม");
        map.put("mobLevelBonus", "เลเวลมอนเพิ่ม");
        map.put("eliteMultiplier", "ตัวคูณโอกาสอีลิต");
        map.put("mobHealth", "ตัวคูณ HP มอน");
        map.put("mobDamage", "ตัวคูณดาเมจมอน");
        map.put("xpMultiplier", "ตัวคูณ EXP");
        map.put("lootMultiplier", "ตัวคูณดรอป");
        map.put("healingMultiplier", "ตัวคูณการฟื้นฟู");
        map.put("oreBonusChance", "โอกาสแร่ดรอปซ้ำ");
        map.put("playerEffects", "เอฟเฟกต์ผู้เล่น");
        map.put("nightOnly", "เฉพาะกลางคืน");
        map.put("spawns", "มอนที่เรียกมา");
        map.put("waveSize", "จำนวนต่อคลื่น");
        map.put("waveSeconds", "คลื่นทุกกี่วินาที");
        map.put("maxAlive", "มอนคลื่นสูงสุดต่อผู้เล่น");
        map.put("goal", "เป้าหมาย");
        map.put("goalCount", "จำนวนเป้าหมาย");
        map.put("minContribution", "ส่วนร่วมขั้นต่ำ");
        map.put("type", "ประเภท");
        map.put("filter", "ตัวกรอง");
        map.put("xpFlat", "EXP เพิ่มคงที่");
        map.put("announce", "ประกาศ");
        map.put("cooldownSeconds", "คูลดาวน์ (วินาที)");
        map.put("source", "มอนที่ดรอป");
        map.put("fits", "ใส่ได้กับ");
        map.put("chance", "โอกาสดรอป (-1 = ค่าพื้นฐาน)");
        map.put("color", "สี (ARGB)");
        map.put("effects", "เอฟเฟกต์");
        map.put("rule", "กฎ");
        return map;
    }

    private static Map<String, String> help() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("enabled", "ปิด = กลับไปใช้ระบบเดิมก่อนมีซีซั่นทั้งหมด");
        map.put("currency", "สกุลเงินที่ใช้สุ่มม้า รีเซ็ตสเตตัส ซื้อคอก ขายม้า (ปกติ rotas:gold)");
        map.put("mainBaseXp", "ยิ่งมาก ยิ่งเลเวลช้า ปกติ 20-40");
        map.put("mainMaxLevel", "เลเวลสูงสุดของอาชีพหลัก ปกติ 100");
        map.put("subBaseXp", "ยิ่งมาก อาชีพรองยิ่งขึ้นช้า ปกติ 60-120");
        map.put("subMaxLevel", "เลเวลสูงสุดของอาชีพรอง ปกติ 20");
        map.put("subJobProductionOnly", "เปิด = อาชีพรองได้ EXP จากการผลิตเท่านั้น ไม่ได้จากการฆ่ามอน");
        map.put("monsterLevelBonus", "มอนสูงกว่า 1 เลเวล ได้ EXP เพิ่มเท่านี้ 0.15 = +15% ต่อเลเวล");
        map.put("overLevelGrace", "ผู้เล่นเลเวลสูงกว่ามอนได้กี่เลเวลก่อนเริ่มลด EXP ปกติ 3-8");
        map.put("overLevelPenaltyPerLevel", "ลด EXP ต่อเลเวลที่เกิน 0.10 = -10% ต่อเลเวล");
        map.put("overLevelMaxPenalty", "ลดได้มากสุดเท่านี้ 0.90 = เหลืออย่างน้อย 10%");
        map.put("minibossMultiplier", "มินิบอสให้ EXP กี่เท่าของมอนธรรมดา ปกติ 5-15");
        map.put("bossMultiplier", "บอสให้ EXP กี่เท่าของมอนธรรมดา ปกติ 20-60");
        map.put("partyRadius", "สมาชิกต้องอยู่ห่างไม่เกินกี่บล็อกถึงได้ EXP ปกติ 32-64");
        map.put("partyMaxSize", "จำนวนคนที่แบ่ง EXP กันได้มากสุด ปกติ 4-6");
        map.put("overflowCurrency", "EXP ส่วนที่ถูกตัดจะกลายเป็นเหรียญนี้แทน");
        map.put("overflowXpPerToken", "EXP ส่วนเกินเท่านี้ = 1 เหรียญ ปกติ 500-2000");
        map.put("repeatableFullRuns", "เควสทำซ้ำได้ EXP เต็มกี่รอบแรก");
        map.put("repeatableHalfRuns", "ถึงรอบที่เท่านี้ยังได้ครึ่งอัตรา");
        map.put("repeatableHalfRate", "อัตราช่วงกลาง 0.5 = ครึ่งเดียว");
        map.put("repeatableLowRate", "อัตราหลังจากนั้น 0.1 = 10%");
        map.put("craftGraceLevels", "ผลิตของต่ำกว่าเลเวลตัวเองได้กี่ขั้นก่อนลด EXP");
        map.put("craftPenaltyPerLevel", "ลด EXP ต่อเลเวลที่เกิน 0.15 = -15%");
        map.put("craftMaxPenalty", "ลดได้มากสุด 0.90 = เหลืออย่างน้อย 10%");
        map.put("firstCraftMultiplier", "ผลิตของชิ้นนี้ครั้งแรกได้ EXP กี่เท่า ปกติ 2-5");
        map.put("lockRecipes", "เปิด = ของในตารางปลดล็อกอาชีพ หยิบจากช่องผลิตได้เมื่อถึงเลเวลเท่านั้น");
        map.put("subCapToOverflow", "เปิด = อาชีพรองที่ตันแล้ว EXP ที่ได้จะเปลี่ยนเป็นเหรียญ");
        map.put("lockSmelting", "เปิด = ของที่เผาในตารางอาชีพ หยิบได้เฉพาะอาชีพรองนั้นเมื่อถึงเลเวล");
        map.put("lockBrewing", "เปิด = ยาที่ต้มในตารางอาชีพ หยิบได้เฉพาะอาชีพรองนั้นเมื่อถึงเลเวล");
        map.put("lockStarterRows", "เปิด = แม้ของเลเวล 1 ก็ต้องเป็นอาชีพรองนั้นถึงทำได้");
        map.put("gatherFreeLevel", "ของที่ปลดล็อกเลเวลนี้หรือต่ำกว่า ใครเก็บก็ได้ของปกติ");
        map.put("gatherMaxCount", "คนที่ไม่มีอาชีพเก็บของนั้น ได้มากสุดกี่ชิ้นต่อกอง (ไม่คิด Fortune)");
        map.put("gatherFishFallback", "คนที่ไม่ใช่ชาวประมงตกได้ของนี้แทนเมื่อพลาด");
        map.put("crafterPriceMultiplier", "ช่าง NPC คิดราคาเป็นกี่เท่าของค่าจ้างฐาน ปกติ 2-4");
        map.put("crafterDailyLimit", "สั่งช่างได้กี่ชิ้นต่อวันต่อคน 0 = ไม่จำกัด");
        map.put("crafterHideMarker", "เปิด = ไม่มีป้ายเหนือหัวช่าง ผู้เล่นต้องหาเอง");
        map.put("repeatableRankCapPerDay", "แต้มแรงค์จากเควสทำซ้ำสูงสุดต่อวัน");
        map.put("seasonRankTotal", "แต้มแรงค์ทั้งซีซั่น (ไม่รวมเควสซ้ำ) เกณฑ์แรงค์คิดเป็นสัดส่วนของค่านี้");
        map.put("defenseScale", "ดาเมจที่รับ = ค่านี้ ÷ (ค่านี้ + พลังป้องกัน) ยิ่งมาก ป้องกันยิ่งเห็นผลน้อย ปกติ 100");
        map.put("pveLevelParity", "เลเวลผู้เล่นหักล้างความแรงของมอนได้แค่ไหน 0-1 ปกติ 0.8");
        map.put("pvpDamageMultiplier", "ดาเมจ PvP ทุกครั้งคูณเท่านี้ 0.6 = สู้กันได้นานขึ้น");
        map.put("stats.startPoints", "แต้มสเตตัสตอนเริ่มเกม");
        map.put("stats.pointsPerLevel", "แต้มที่ได้ทุกเลเวล เลเวล 100 = เริ่มต้น + 99 × ค่านี้");
        map.put("stats.maxPerStat", "ลงสเตตัสเดียวได้มากสุดกี่แต้ม บังคับให้เลือก ไม่ใช่อัดตัวเดียว");
        map.put("stats.respecCost", "ราคาคืนแต้มทั้งหมด (สกุลเงินซีซั่น) 0 = ฟรี");
        map.put("stats.strAttack", "0.01 = +1% พลังโจมตีต่อแต้ม (คูณกับดาเมจอาวุธ)");
        map.put("stats.vitHealth", "0.01 = +1% พลังชีวิตสูงสุดต่อแต้ม");
        map.put("stats.intMagic", "0.01 = +1% ดาเมจเวทย์ต่อแต้ม");
        map.put("stats.agiAttackSpeed", "0.005 = +0.5% ความเร็วโจมตีต่อแต้ม");
        map.put("stats.agiDodge", "0.002 = +0.2% โอกาสหลบต่อแต้ม (รวมทุกอย่างไม่เกิน 50%)");
        map.put("pvpStatEfficiency", "โบนัสโจมตีจากสเตตัสนับใน PvP แค่ไหน 1 = เต็ม ปกติ 0.5");
        map.put("pvpEvasionScale", "โอกาสหลบคูณเท่านี้เมื่อโดนผู้เล่นตี ปกติ 0.5");
        map.put("pvpMaxHitShare", "ตีครั้งเดียวไม่เกินกี่ส่วนของ HP เหยื่อ 0.35 = 35%, 0 = ไม่จำกัด");
        map.put("pvpLevelGrace", "ผู้โจมตีเลเวลสูงกว่าได้กี่เลเวลก่อนลดดาเมจ PvP");
        map.put("pvpLevelGapPerLevel", "ลดดาเมจ PvP ต่อเลเวลที่ห่างเกิน 0.02 = -2%");
        map.put("pvpLevelGapMax", "ลดดาเมจ PvP มากสุด 0.5 = ครึ่งหนึ่ง");
        map.put("magicBonusMaxRatio", "โบนัสเวทต่อครั้งไม่เกินกี่เท่าของดาเมจ 1.0 = ไม่เกินเท่าตัว 0 = ไม่จำกัด");
        map.put("titles.staffEarnTitles", "เปิด = แอดมิน (OP 2+) ได้ฉายาอัตโนมัติเหมือนผู้เล่น ปกติปิด เพื่อไม่ให้แอดมินที่ทดสอบแย่งฉายาเฉพาะตัว");
        map.put("titles.creativeEarnTitles", "เปิด = ผู้เล่นโหมดครีเอทีฟ/สเปกเตเตอร์ได้ฉายาอัตโนมัติ");
        map.put("titles.revokeBlocksReEarn", "เปิด = ฉายาที่แอดมินถอดแล้ว ผู้เล่นคนนั้นจะไม่ได้คืนเอง (ให้ด้วยมือได้)");
        map.put("nemesis.enabled", "ปิด = ไม่มีศัตรูคู่แค้นเกิดใหม่");
        map.put("nemesis.riseChance", "มอนที่ฆ่าผู้เล่นมีโอกาสกลายเป็นคู่แค้นเท่านี้ 0.5 = 50%");
        map.put("nemesis.riseCooldownMinutes", "ผู้เล่นหนึ่งคนทำให้เกิดคู่แค้นได้ 1 ตัวต่อกี่นาที (กันฟาร์มค่าหัว)");
        map.put("nemesis.maxActive", "คู่แค้นทั้งเซิร์ฟพร้อมกันได้มากสุด");
        map.put("nemesis.maxPerPlayer", "คู่แค้นที่ตามล่าผู้เล่นคนเดียวได้มากสุด");
        map.put("nemesis.maxRank", "แรงค์สูงสุดของคู่แค้น ปกติ 5");
        map.put("nemesis.levelsPerRank", "เลเวลที่เพิ่มตอนเกิดและทุกครั้งที่ขึ้นแรงค์");
        map.put("nemesis.healthPerRank", "HP เพิ่มต่อแรงค์ 0.5 = แรงค์ 1 ×1.5, แรงค์ 5 ×3.5");
        map.put("nemesis.damagePerRank", "ดาเมจเพิ่มต่อแรงค์ 0.2 = +20%");
        map.put("nemesis.ambushEnabled", "เปิด = คู่แค้นที่ร่างหายไป จะกลับมาซุ่มโจมตีคนที่มันฆ่าล่าสุด");
        map.put("nemesis.ambushCooldownMinutes", "รอกี่นาทีก่อนซุ่มโจมตีได้อีก");
        map.put("nemesis.ambushChancePerMinute", "หลังคูลดาวน์ โอกาสต่อนาทีที่มันจะหาเจอ 0.25 = 25%");
        map.put("nemesis.ambushMinDistance", "โผล่ห่างจากผู้เล่นอย่างน้อยกี่บล็อก");
        map.put("nemesis.ambushMaxDistance", "โผล่ห่างจากผู้เล่นมากสุดกี่บล็อก");
        map.put("nemesis.tauntRadius", "คู่แค้นอยู่ใกล้ในระยะนี้ (บล็อก) จะพูดเยาะเย้ย");
        map.put("nemesis.tauntCooldownSeconds", "เยาะเย้ยได้ 1 ครั้งต่อกี่วินาที");
        map.put("nemesis.forgetAfterDays", "ไม่มีใครเจอกี่วันแล้วคู่แค้นหายไป");
        map.put("nemesis.goldPerRank", "ค่าหัวเงินต่อแรงค์ จ่ายให้คนที่ฆ่า");
        map.put("nemesis.rankPointsPerRank", "ค่าหัวแต้มแรงค์ต่อแรงค์");
        map.put("nemesis.revengeMultiplier", "เหยื่อฆ่าคู่แค้นของตัวเองได้ค่าหัวกี่เท่า");
        map.put("nemesis.trophyEnabled", "เปิด = คนฆ่าได้อาวุธที่ตั้งชื่อตามคู่แค้น (ของใหม่เสมอ)");
        map.put("nemesis.trophyItem", "ไอเทมรางวัลเมื่อคู่แค้นฆ่าด้วยระยะประชิด");
        map.put("nemesis.trophyRangedItem", "ไอเทมรางวัลเมื่อคู่แค้นฆ่าด้วยธนู/กระสุน");
        map.put("nemesis.trophyRefine", "เปิด = ของรางวัลถูกตีบวกตามแรงค์ (ไม่เกินระดับปลอดภัย)");
        map.put("worldEvents.types.*.name", "ชื่อที่ผู้เล่นเห็นตอนประกาศ");
        map.put("worldEvents.types.*.description", "คำอธิบายสั้น ๆ ตอนประกาศ");
        map.put("worldEvents.types.*.weight", "ยิ่งมาก ยิ่งถูกสุ่มบ่อย เทียบกับประเภทอื่น ปกติ 5-20");
        map.put("worldEvents.types.*.mobLevelBonus", "มอนในพื้นที่เลเวลเพิ่มเท่านี้");
        map.put("worldEvents.types.*.eliteMultiplier", "คูณโอกาสเกิดมอนอีลิต/แชมเปี้ยนในพื้นที่ 2 = สองเท่า");
        map.put("worldEvents.types.*.mobHealth", "คูณ HP มอนในพื้นที่ 1.5 = +50%");
        map.put("worldEvents.types.*.mobDamage", "คูณดาเมจมอนในพื้นที่");
        map.put("worldEvents.types.*.xpMultiplier", "คูณ EXP ที่ได้ในพื้นที่ 2 = สองเท่า");
        map.put("worldEvents.types.*.lootMultiplier", "คูณดรอปของในพื้นที่");
        map.put("worldEvents.types.*.coinMultiplier", "คูณเงินที่ได้ในพื้นที่");
        map.put("worldEvents.types.*.healingMultiplier", "คูณการฟื้นเลือดของผู้เล่นในพื้นที่ 0.5 = ฟื้นช้าลงครึ่ง");
        map.put("worldEvents.types.*.oreBonusChance", "โอกาสที่ขุดแร่แล้วได้แร่เพิ่มอีก 1 ครั้ง 0.25 = 25%");
        map.put("worldEvents.types.*.nightOnly", "เปิด = เริ่มตอนกลางคืนเท่านั้น และจบตอนเช้า");
        map.put("worldEvents.types.*.waveSize", "มอนต่อระลอก ต่อผู้เล่น");
        map.put("worldEvents.types.*.waveSeconds", "ปล่อยมอนระลอกใหม่ทุกกี่วินาที");
        map.put("worldEvents.types.*.maxAlive", "มอนระลอกที่มีชีวิตรอบผู้เล่นคนเดียวได้มากสุด");
        map.put("worldEvents.types.*.goal", "KILL = ฆ่ามอน, MINE = ขุดแร่, NONE = แค่เอาตัวรอดจนหมดเวลา");
        map.put("worldEvents.types.*.goalCount", "ต้องทำเป้าหมายรวมกันกี่ครั้ง (0 = ไม่มีเป้า)");
        map.put("worldEvents.types.*.minContribution", "ผู้เล่นต้องช่วยอย่างน้อยเท่านี้ถึงได้รางวัล");
        map.put("worldEvents.types.*.playerEffects", "เอฟเฟกต์ติดตัวผู้เล่นในพื้นที่ เช่น minecraft:speed 1 (คั่นด้วยจุลภาค)");
        map.put("worldEvents.types.*.spawns", "มอนที่ปล่อยเป็นระลอก เช่น minecraft:zombie (คั่นด้วยจุลภาค)");
        map.put("worldEvents.enabled", "ปิด = ไม่สุ่มเหตุการณ์เอง (แอดมินยังเริ่มเองได้)");
        map.put("worldEvents.intervalMinutes", "สุ่มเหตุการณ์ทุก ๆ กี่นาที ปกติ 30-60");
        map.put("worldEvents.startChance", "แต่ละรอบมีโอกาสเริ่มเท่านี้ 0.6 = 60%");
        map.put("worldEvents.durationMinutes", "เหตุการณ์หนึ่งอยู่กี่นาที ปกติ 15-30");
        map.put("worldEvents.maxActive", "เหตุการณ์พร้อมกันได้มากสุด");
        map.put("worldEvents.wildRadius", "รัศมีพื้นที่เหตุการณ์ในป่า (บล็อก)");
        map.put("worldEvents.wildMinDistance", "เหตุการณ์ในป่าเกิดห่างผู้เล่นอย่างน้อยกี่บล็อก");
        map.put("worldEvents.wildMaxDistance", "เหตุการณ์ในป่าเกิดห่างผู้เล่นมากสุดกี่บล็อก");
        map.put("weaponMemory.enabled", "ปิด = อาวุธไม่นับการฆ่า ไม่ได้โบนัส");
        map.put("weaponMemory.damagePerRank", "ดาเมจเพิ่มต่อแรงค์ความทรงจำ 0.02 = +2%");
        map.put("weaponMemory.favoredBonus", "ดาเมจเพิ่มกับมอนชนิดที่อาวุธฆ่ามากที่สุด 0.05 = +5%");
        map.put("weaponMemory.favoredFromRank", "เริ่มได้โบนัสเหยื่อโปรดตั้งแต่แรงค์นี้");
        map.put("weaponMemory.announceFrom", "อาวุธถึงแรงค์นี้ขึ้นไป ประกาศทั้งเซิร์ฟ");
        map.put("farming.luckEnabled", "ปิด = โชคไม่มีผลกับการ์ด");
        map.put("farming.cardChancePerLuck", "โชค 1 แต้มเพิ่มโอกาสดรอปการ์ดเท่านี้ 0.05 = +5%");
        map.put("farming.comboEnabled", "ปิด = ไม่มีคอมโบฆ่าต่อเนื่อง");
        map.put("farming.comboWindowSeconds", "ต้องฆ่าตัวต่อไปภายในกี่วินาทีคอมโบถึงไม่หลุด");
        map.put("farming.comboXpPerKill", "EXP เพิ่มต่อคอมโบ 1 ตัว 0.02 = +2%");
        map.put("farming.comboLootPerKill", "โอกาสดรอปเพิ่มต่อคอมโบ 1 ตัว 0.01 = +1%");
        map.put("farming.comboCap", "โบนัสหยุดเพิ่มเมื่อคอมโบถึงเท่านี้ (ตัวเลขยังนับต่อ)");
        map.put("farming.eliteEnabled", "ปิด = ไม่มีมอนเวเทอรัน/อีลิต/แชมเปี้ยน");
        map.put("farming.veteranChance", "โอกาสที่มอนเกิดเองเป็นเวเทอรัน (แกร่งขึ้นนิดหน่อย) 0.10 = 10%");
        map.put("farming.veteranHealth", "HP เวเทอรันกี่เท่า");
        map.put("farming.veteranDamage", "ดาเมจเวเทอรันกี่เท่า");
        map.put("farming.eliteChance", "โอกาสที่มอนเกิดเองเป็นอีลิต 0.03 = 3%");
        map.put("farming.eliteHealth", "HP อีลิตกี่เท่า");
        map.put("farming.eliteDamage", "ดาเมจอีลิตกี่เท่า");
        map.put("farming.championHealth", "HP แชมเปี้ยนกี่เท่า");
        map.put("farming.championDamage", "ดาเมจแชมเปี้ยนกี่เท่า");
        map.put("farming.eliteRankPoints", "ฆ่าอีลิตได้แต้มแรงค์เท่านี้");
        map.put("farming.championRankPoints", "ฆ่าแชมเปี้ยนได้แต้มแรงค์เท่านี้");
        map.put("farming.veteranXp", "ฆ่าเวเทอรันได้ EXP กี่เท่าของมอนธรรมดา");
        map.put("farming.eliteXp", "ฆ่าอีลิตได้ EXP กี่เท่า");
        map.put("farming.championXp", "ฆ่าแชมเปี้ยนได้ EXP กี่เท่า");
        map.put("farming.affixesEnabled", "เปิด = อีลิต/แชมเปี้ยนสุ่มความสามารถพิเศษ (ดูดเลือด พิษ ระเบิด ...)");
        map.put("farming.eliteAffixes", "อีลิตได้ความสามารถพิเศษกี่อย่าง");
        map.put("farming.championAffixes", "แชมเปี้ยนได้ความสามารถพิเศษกี่อย่าง");
        map.put("farming.rankAura", "เปิด = มีออร่าสีตามแรงค์รอบตัวอีลิต/แชมเปี้ยน");
        map.put("farming.announceChampion", "เปิด = บอกผู้เล่นใกล้ ๆ เมื่อแชมเปี้ยนเกิด");
        map.put("bestiary.enabled", "ปิด = สมุดมอนไม่ให้โบนัส");
        map.put("bestiary.damagePerTier", "ดาเมจต่อมอนชนิดนั้นเพิ่มต่อขั้นสมุด 0.02 = +2%");
        map.put("bestiary.lootPerTier", "ดรอปจากมอนชนิดนั้นเพิ่มต่อขั้น 0.02 = +2%");
        map.put("bestiary.rankPointsPerTier", "แต้มแรงค์ที่ได้เมื่อขึ้นขั้นสมุด");
        map.put("bestiary.revealDropsAt", "สมุดแสดงของที่ดรอปเมื่อถึงขั้นนี้ (เริ่มที่ 1)");
        map.put("salvage.enabled", "ปิด = แยกชิ้นส่วนไม่ได้");
        map.put("salvage.goldPerValue", "เงินที่ได้ต่อมูลค่าของ 1 แต้ม");
        map.put("salvage.orePerValue", "โอกาสได้แร่ตีบวกต่อมูลค่า 1 แต้ม");
        map.put("salvage.refineRefund", "คืนแร่ที่ใช้ตีบวกของชิ้นนั้นกี่ส่วน 0.5 = ครึ่งหนึ่ง");
        map.put("salvage.returnCards", "เปิด = การ์ดที่ใส่ไว้ได้คืนตอนแยก");
        map.put("**.reward.gold", "เงินรางวัล");
        map.put("**.reward.xp", "EXP รางวัล");
        map.put("**.reward.rankPoints", "แต้มแรงค์รางวัล");
        map.put("**.reward.items", "ไอเทมรางวัล เช่น minecraft:diamond 3 (คั่นด้วยจุลภาค)");
        map.put("daily.tiers.*.completions", "ต้องทำภารกิจรายวันครบกี่อันถึงได้ขั้นนี้");
        map.put("daily.enabled", "ปิด = ไม่มีภารกิจรายวัน");
        map.put("daily.countDailyRepeat", "เปิด = เควสที่ตั้งทำซ้ำรายวัน นับด้วยแม้ไม่ได้อยู่บนบอร์ดนี้");
        map.put("daily.announceOnJoin", "เปิด = เข้าเกมครั้งแรกของวันบอกว่ามีภารกิจรออยู่กี่อัน");
        map.put("seasonTrack.tiers.*.points", "ต้องมีแต้มแรงค์รวมทั้งซีซั่นเท่านี้ถึงรับขั้นนี้ได้");
        map.put("seasonTrack.tiers.*.name", "ชื่อขั้นที่ผู้เล่นเห็น");
        map.put("seasonTrack.enabled", "ปิด = ไม่มีเส้นทางซีซั่น");
        map.put("events.enabled", "ปิด = ไม่ใช้กฎกิจกรรมทั้งหมด");
        map.put("events.rules.*.type", "ชนิดเหตุการณ์ เช่น KILL_ENTITY, CRAFT, QUEST_COMPLETE (ดูในหน้า Event catalogue)");
        map.put("events.rules.*.filter", "ว่างหรือ * = ทุกอย่าง, namespace:* = ทั้งม็อด, หรือ id ตรงตัว");
        map.put("events.rules.*.enabled", "ปิดกฎนี้ชั่วคราว");
        map.put("events.rules.*.xpMultiplier", "คูณ EXP ที่เหตุการณ์นี้ให้อยู่แล้ว 1 = ไม่เปลี่ยน");
        map.put("events.rules.*.xpFlat", "EXP เพิ่มตรง ๆ ต่อครั้ง");
        map.put("events.rules.*.gold", "เงินต่อครั้ง");
        map.put("events.rules.*.announce", "OFF = เงียบ, PLAYER = บอกผู้เล่นคนนั้น, SERVER = ประกาศทั้งเซิร์ฟ");
        map.put("events.rules.*.cooldownSeconds", "ได้รางวัลจากกฎนี้ซ้ำได้ทุกกี่วินาที 0 = ไม่จำกัด");
        map.put("cards.enabled", "ปิด = ไม่มีระบบการ์ด");
        map.put("cards.maxSockets", "ของชิ้นหนึ่งมีช่องการ์ดได้มากสุด");
        map.put("cards.dropChance", "โอกาสดรอปการ์ดพื้นฐาน (ตั้งให้ต่ำมาก) 0.0005 = 0.05%");
        map.put("cards.entries.*.name", "ชื่อการ์ด");
        map.put("cards.entries.*.source", "id มอนที่ดรอป เช่น minecraft:zombie ว่าง = มอนไหนก็ได้");
        map.put("cards.entries.*.fits", "ใส่ได้ใน WEAPON (อาวุธ), ARMOR (เกราะ) หรือ ANY");
        map.put("cards.entries.*.color", "สีการ์ด ARGB เช่น 0xFFB07CE8");
        map.put("cards.entries.*.effects", "ผลของการ์ด (คั่นด้วยจุลภาค)");
        map.put("runes.enabled", "ปิด = ไม่มีระบบรูน");
        map.put("refine.enabled", "ปิด = ตีบวกไม่ได้");
        map.put("refine.maxLevel", "ตีบวกได้สูงสุด + เท่านี้ ปกติ 10");
        map.put("refine.safeLevel", "ถึงขั้นนี้ตีไม่มีวันพลาด ปกติ 4");
        map.put("refine.attackPerLevel", "พลังโจมตีเพิ่มต่อขั้น (ในช่วงปลอดภัย)");
        map.put("refine.attackPerOverLevel", "พลังโจมตีเพิ่มต่อขั้นที่เกินช่วงปลอดภัย");
        map.put("refine.defensePerLevel", "พลังป้องกันเพิ่มต่อขั้น (ในช่วงปลอดภัย)");
        map.put("refine.defensePerOverLevel", "พลังป้องกันเพิ่มต่อขั้นที่เกินช่วงปลอดภัย");
        map.put("refine.goldPerAttempt", "ค่าตี +1 ขั้นต่อไปแพงขึ้นตามตัวคูณราคา");
        map.put("refine.goldGrowth", "ราคาต่อขั้นคูณเท่านี้ 1.6 = แพงขึ้น 60% ทุกขั้น");
        map.put("refine.enrichedBonus", "แร่เสริมเพิ่มโอกาสสำเร็จ 0.15 = +15%");
        map.put("refine.blessingBonus", "ม้วนพรเพิ่มโอกาสสำเร็จ 0.20 = +20%");
        map.put("refine.announceFrom", "ตีสำเร็จขั้นนี้ขึ้นไปประกาศทั้งเซิร์ฟ 0 = ไม่ประกาศ");
        map.put("refine.weaponOre", "แร่ที่ใช้ตีอาวุธ 1 ชิ้นต่อครั้ง");
        map.put("refine.armorOre", "แร่ที่ใช้ตีเกราะ 1 ชิ้นต่อครั้ง");
        map.put("refine.weaponOreEnriched", "แร่เสริมสำหรับอาวุธ");
        map.put("refine.armorOreEnriched", "แร่เสริมสำหรับเกราะ");
        map.put("refine.chances", "โอกาสสำเร็จของแต่ละขั้น เรียงจาก +1 (1.0 = 100%)");
        map.put("drops.enabled", "ปิด = ไม่ใช้ระบบดรอปของซีซั่น");
        map.put("drops.filter.enabled", "ปิด = ไม่กรองดรอปของม็อดอื่น");
        map.put("drops.plain.enabled", "ปิด = มอนธรรมดาไม่ให้เงิน");
        map.put("drops.plain.hostileOnly", "เปิด = เฉพาะมอนศัตรู ปิด = สัตว์ก็ให้เงิน");
        map.put("**.coinMultiplier", "เงินสุ่มตามเลเวลมอนแล้วคูณด้วยค่านี้");
        map.put("drops.grades.*.minGold", "เงินต่ำสุดที่ของเกรดนี้ให้");
        map.put("drops.grades.*.maxGold", "เงินสูงสุดที่ของเกรดนี้ให้");
        map.put("rankPerks.*.title", "ชื่อสิทธิพิเศษของแรงค์นี้");
        map.put("horse.enabled", "ปิด = ไม่มีระบบม้า");
        map.put("horse.pullCost", "ราคาสุ่มม้า 1 ครั้ง");
        map.put("horse.tenPullCost", "ราคาสุ่มม้า 10 ครั้ง (ปกติถูกกว่า 10 × 1 ครั้ง)");
        map.put("horse.pityRare", "สุ่มครบกี่ครั้งได้หายากแน่นอน");
        map.put("horse.pityEpic", "สุ่มครบกี่ครั้งได้มหากาพย์แน่นอน");
        map.put("horse.pityLegendary", "สุ่มครบกี่ครั้งได้ตำนานแน่นอน");
        map.put("horse.epicRareCoatChance", "ม้ามหากาพย์มีโอกาสได้สีขนหายาก 0.35 = 35%");
        map.put("horse.baseStableSlots", "ช่องคอกม้าฟรีตอนเริ่ม");
        map.put("horse.slotBaseCost", "ราคาช่องคอกแรกที่ซื้อ");
        map.put("horse.slotCostGrowth", "ราคาช่องถัดไปคูณเท่านี้");
        map.put("horse.maxStableSlots", "ช่องคอกสูงสุด");
        map.put("horse.summonCooldownSeconds", "เรียกม้าได้ทุกกี่วินาที");
        map.put("horse.recoverySeconds", "ม้าล้มแล้วพักกี่วินาทีถึงเรียกได้อีก");
        map.put("horse.potionUsesPerDay", "ป้อนยา XP ให้ม้าตัวหนึ่งได้กี่ครั้งต่อวัน");
        map.put("horse.bredHorsesSellToNpc", "เปิด = ม้าที่ผสม/จับได้ขายให้ NPC ได้");
        map.put("horse.npcSalesPerDay", "ขายม้าให้ NPC ได้กี่ตัวต่อวัน");
        map.put("horse.sellBase", "ราคาขายพื้นฐาน");
        map.put("horse.sellPerSkillLevel", "ราคาเพิ่มต่อเลเวลสกิลม้า");
        map.put("horse.sellPerAffinityLevel", "ราคาเพิ่มต่อเลเวลความผูกพัน");
        map.put("horse.sellSecretCoat", "โบนัสราคาสีขนลับ");
        map.put("horse.sellRareCoat", "โบนัสราคาสีขนหายาก");
        map.put("horse.marketFee", "ตลาดหักกี่ส่วน 0.05 = 5%, 0 = ผู้ขายได้เต็ม");
        map.put("horse.marketMaxPrice", "ตั้งราคาขายในตลาดได้สูงสุด");
        map.put("rankExp.*", "แต้มแรงค์ที่ได้จากเควสประเภทนี้ต่อครั้ง");
        map.put("rankThresholds.*", "ต้องมีแต้มแรงค์ถึงสัดส่วนนี้ของแต้มทั้งซีซั่น 0.5 = ครึ่งหนึ่ง");
        map.put("rankPerks.*.shopDiscount", "ส่วนลดร้านค้าของแรงค์นี้ 0.05 = ลด 5%");
        map.put("rankPerks.*.stableSlots", "ช่องคอกม้าฟรีเพิ่มของแรงค์นี้");
        map.put("rankPerks.*.marketFeeDiscount", "ลดค่าธรรมเนียมตลาดม้า 0.05 = ลด 5%");
        map.put("drops.plain.ignore", "id มอนที่ไม่ให้เงิน (คั่นด้วยจุลภาค)");
        map.put("drops.filter.blocked", "ไอเทมที่ถูกลบออกจากดรอปของม็อดอื่น (คั่นด้วยจุลภาค)");
        map.put("horse.secretCoats", "สีขนลับ (คั่นด้วยจุลภาค)");
        map.put("horse.rareCoats", "สีขนหายาก (คั่นด้วยจุลภาค)");
        map.put("horse.sellRarityBonus", "โบนัสราคาขายตามความหายาก ธรรมดา → ตำนาน (5 ค่า)");
        map.put("bestiary.tiers", "ฆ่ามอนชนิดนั้นครบกี่ตัวถึงขึ้นขั้นสมุด 1, 2, 3 ...");
        map.put("nemesis.names", "ชื่อที่สุ่มให้คู่แค้น (คั่นด้วยจุลภาค)");
        map.put("nemesis.epithets.*", "ฉายาของคู่แค้นตามวิธีที่มันฆ่า (คั่นด้วยจุลภาค)");
        map.put("weaponMemory.milestones", "อาวุธฆ่าครบกี่ตัวถึงขึ้นแรงค์ความทรงจำ 1, 2, 3 ...");
        map.put("runes.slotLevels", "ของต้องตีบวกถึงขั้นนี้ถึงเปิดช่องรูนที่ 1, 2, 3 ...");
        map.put("runes.goldPerSlot", "ราคาเปิดช่องรูนที่ 1, 2, 3 ...");
        map.put("mainExponent", "EXP ต่อเลเวล = ฐาน × เลเวล^เลขชี้กำลัง");
        map.put("subExponent", "EXP ต่อเลเวล = ฐาน × เลเวล^เลขชี้กำลัง");
        map.put("partyBonusPerMember", "โบนัสรวมต่อสมาชิกที่เพิ่มขึ้น ก่อนหารเท่ากัน");
        map.put("partyLevelReach", "สมาชิกได้ EXP เหมือนมอนสูงกว่าตัวเองไม่เกินกี่เลเวล");
        map.put("rankThresholds", "สัดส่วนของแต้มแรงค์รวมทั้งซีซั่น");
        map.put("horse.rates", "ธรรมดา, ไม่ธรรมดา, หายาก, มหากาพย์, ตำนาน");
        map.put("gatherDropChance", "0-1 ต่อกอง สำหรับผู้ที่ไม่มีอาชีพรองที่เก็บของนั้น");
        map.put("crafterFee", "ค่าจ้างต่อชิ้นก่อนคูณตัวคูณราคา");
        map.put("tierXp", "4 ค่า คั่นด้วยจุลภาค");
        map.put("tierMaxLevel", "4 ค่า คั่นด้วยจุลภาค เรียงจากน้อยไปมาก");
        map.put("farming.lootChancePerLuck", "0.10 = +10% ของโอกาสพื้นฐานต่อแต้มโชค");
        map.put("farming.luckBonusCap", "1.0 = เพิ่มได้สูงสุดเท่าตัว");
        map.put("farming.rankedHitCap", "0 = ปิดเพดาน");
        map.put("farming.championChance", "สุ่มก่อนอีลิต");
        map.put("seasonTrack.seasonId", "เปลี่ยนรหัสเมื่อเริ่มซีซั่นใหม่ ผู้เล่นจะรับรางวัลได้ใหม่ทั้งหมด");
        map.put("daily.boardId", "ใช้การหมุนเวียนรายวันของบอร์ดนี้เป็นภารกิจวันนี้");
        map.put("refine.chances", "0-1 ต่อขั้น เริ่มจากขั้น +1");
        map.put("refine.onFail", "DOWNGRADE ลดขั้น, RESET_TO_SAFE กลับขั้นปลอดภัย, BREAK ของแตก, KEEP เสียวัสดุ");
        map.put("nemesis.gradeByRank", "ค่าสุดท้ายใช้กับแรงค์ที่สูงกว่า");
        map.put("*.items", "รูปแบบ: minecraft:diamond 1-3 @0.5 (จำนวน, โอกาส)");
        map.put("*.playerEffects", "รูปแบบ: minecraft:speed 1");
        map.put("*.spawns", "รหัสมอน คั่นด้วยจุลภาค");
        map.put("*.effects", "รูปแบบ: minecraft:generic.max_health 0.04 percent");
        map.put("*.grades", "common, medium, rare, epic");
        map.put("*.chance", "0-1");
        map.put("*.coinChance", "0-1");
        map.put("*.coinMin", "เหรียญน้อยสุดต่อตัว (ก่อนบวกตามเลเวล)");
        map.put("*.coinMax", "เหรียญมากสุดต่อตัว (ก่อนบวกตามเลเวล)");
        map.put("*.coinPerLevel", "มอนเลเวล 40 × 0.15 = +6 เหรียญ");
        map.put("*.lootChance", "0-1");
        return map;
    }
}
