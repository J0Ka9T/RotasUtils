package net.schwarz.rotasutils.job;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import net.schwarz.rotasutils.stat.CharacterStat;

/** Curated, editable starting points for the combat and profession layout. */
public final class JobArchetypes {
    public record Archetype(String id, String name, String description, boolean main,
                            int color, Item icon, List<String> items, List<String> activities) { }

    private static final Map<String, Archetype> TEMPLATES = templates();

    private JobArchetypes() { }

    public static List<Archetype> all() { return List.copyOf(TEMPLATES.values()); }

    public static JobDef create(String id) {
        Archetype source = TEMPLATES.get(id);
        if (source == null) return null;
        JobDef job = new JobDef(source.id());
        job.setName(source.name());
        job.setDescription(source.description());
        job.setMainAllowed(source.main());
        job.setSubAllowed(!source.main());
        job.setColor(source.color());
        job.setIcon(new ItemStack(source.icon()));
        job.itemSelectors().addAll(source.items());
        job.masteryActivities().addAll(source.activities());
        addBalance(job);
        applySeason(job, new net.schwarz.rotasutils.level.SeasonRules());
        return job;
    }

    /**
     * Season setup for a template job: sub jobs get the power curve (90 * level^1.8, cap 20), their profession
     * rate and a starter unlock table. Only jobs made from a template are touched; the table is a starting
     * point for admins, who should add one unlock per level.
     */
    public static boolean applySeason(JobDef job, net.schwarz.rotasutils.level.SeasonRules rules) {
        double rate = switch (job.id()) {
            case "miner" -> 0.08;
            case "farmer" -> 0.10;
            case "fisher", "rancher" -> 0.12;
            case "chef" -> 0.144;
            case "alchemy" -> 0.35;
            // The brief's x2.0 made the first levels a two-craft affair; x1.5 keeps a little effort.
            case "blacksmith" -> 1.5;
            default -> -1;
        };
        if (rate < 0) return false;
        job.setMasteryCurve(JobMasteryCurve.season(rules));
        job.setProductionXpRate(rate);
        job.production().clear();
        switch (job.id()) {
            case "miner" -> production(job,
                    "MINE #minecraft:coal_ores 1", "MINE #minecraft:copper_ores 1", "MINE minecraft:stone 1",
                    "MINE #minecraft:iron_ores 5", "SMELT minecraft:iron_ingot 5", "MINE minecraft:obsidian 8",
                    "MINE #minecraft:gold_ores 10", "MINE #minecraft:redstone_ores 10", "MINE #minecraft:lapis_ores 12",
                    "SMELT minecraft:gold_ingot 12", "MINE #minecraft:diamond_ores 15", "MINE #minecraft:emerald_ores 16",
                    "MINE minecraft:ancient_debris 18", "SMELT minecraft:netherite_scrap 19");
            case "farmer" -> production(job,
                    "HARVEST minecraft:wheat 1", "HARVEST minecraft:carrots 1", "HARVEST minecraft:potatoes 1",
                    "CRAFT minecraft:hay_block 3", "HARVEST minecraft:beetroots 5", "HARVEST minecraft:melon 6",
                    "HARVEST minecraft:pumpkin 7", "CRAFT minecraft:bone_meal 8", "HARVEST minecraft:cocoa 10",
                    "HARVEST minecraft:torchflower_crop 12", "HARVEST minecraft:pitcher_crop 14", "HARVEST minecraft:nether_wart 15");
            case "fisher" -> production(job,
                    "FISH #minecraft:fishes 1", "FISH minecraft:tropical_fish 5", "FISH minecraft:pufferfish 6",
                    "FISH minecraft:ink_sac 7", "FISH minecraft:nautilus_shell 10", "FISH minecraft:name_tag 11",
                    "FISH minecraft:saddle 12", "FISH minecraft:enchanted_book 15", "FISH minecraft:bow 16");
            case "chef" -> production(job,
                    "CRAFT minecraft:bread 1", "SMELT minecraft:cooked_beef 1", "SMELT minecraft:cooked_chicken 1",
                    "SMELT minecraft:cooked_porkchop 2", "SMELT minecraft:baked_potato 2", "CRAFT minecraft:cookie 3",
                    "CRAFT minecraft:mushroom_stew 5", "CRAFT minecraft:pumpkin_pie 6", "CRAFT minecraft:beetroot_soup 7",
                    "SMELT minecraft:cooked_salmon 8", "CRAFT minecraft:cake 10", "CRAFT minecraft:golden_carrot 12",
                    "CRAFT minecraft:rabbit_stew 15", "CRAFT minecraft:golden_apple 18");
            case "alchemy" -> production(job,
                    "BREW minecraft:potion 1", "CRAFT minecraft:blaze_powder 4", "CRAFT minecraft:fermented_spider_eye 5",
                    "CRAFT minecraft:glistering_melon_slice 6", "CRAFT minecraft:magma_cream 8", "BREW minecraft:splash_potion 10",
                    "CRAFT minecraft:brewing_stand 12", "BREW minecraft:lingering_potion 15", "CRAFT minecraft:end_crystal 19");
            case "blacksmith" -> production(job,
                    "CRAFT minecraft:iron_sword 1", "CRAFT minecraft:iron_pickaxe 1", "CRAFT minecraft:iron_axe 2",
                    "CRAFT minecraft:iron_shovel 2", "CRAFT minecraft:shield 3", "CRAFT minecraft:iron_helmet 5",
                    "CRAFT minecraft:iron_boots 5", "CRAFT minecraft:iron_leggings 6", "CRAFT minecraft:iron_chestplate 7",
                    "CRAFT minecraft:anvil 9", "CRAFT minecraft:diamond_sword 10", "CRAFT minecraft:diamond_pickaxe 11",
                    "CRAFT minecraft:diamond_axe 12", "CRAFT minecraft:diamond_helmet 15", "CRAFT minecraft:diamond_boots 16",
                    "CRAFT minecraft:diamond_leggings 17", "CRAFT minecraft:diamond_chestplate 18");
            case "rancher" -> production(job,
                    "CRAFT minecraft:lead 1", "CRAFT minecraft:hay_block 2", "CRAFT minecraft:leather_horse_armor 5",
                    "SMELT minecraft:leather 8", "CRAFT minecraft:item_frame 10");
            default -> { }
        }
        return true;
    }

    private static void production(JobDef job, String... rows) {
        for (String row : rows) job.production().add(JobDef.ProductionEntry.decode(row));
    }

    /**
     * Strengths and weaknesses per template. Combat uses Epic Fight 20.14.17 and magic uses Iron's
     * Spells 3.16.1 attribute IDs, verified against the RotasCommu jars; on a server without either
     * mod those modifiers are skipped by {@code CharacterStatService} and flagged in the job screen.
     */
    /** The combat jobs every world starts with, in picker order. */
    public static final List<String> STARTER_JOBS = List.of("archer", "fighter", "tank", "rogue", "wizard");

    /**
     * Each combat job's signature buff. These use the mod's own combat stats (defense, dodge, magic power)
     * or vanilla attributes, so they work on a server without Epic Fight or Iron's Spells.
     */
    public static List<JobAttributeModifier> signature(String id) {
        String defense = net.schwarz.rotasutils.server.CombatStats.DEFENSE;
        String evasion = net.schwarz.rotasutils.server.CombatStats.EVASION;
        String magic = net.schwarz.rotasutils.server.CombatStats.MAGIC_POWER;
        return switch (id) {
            case "archer" -> List.of(mod("Keen eye", evasion, .05, CharacterStat.Operation.ADD),
                    mod("Steady aim", "minecraft:generic.attack_speed", .05, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "fighter" -> List.of(mod("Battle hardened", defense, 8, CharacterStat.Operation.ADD),
                    mod("Veteran vigour", "minecraft:generic.max_health", 2, CharacterStat.Operation.ADD));
            case "tank" -> List.of(mod("Bulwark", defense, 15, CharacterStat.Operation.ADD),
                    mod("Toughness", "minecraft:generic.max_health", 4, CharacterStat.Operation.ADD));
            case "rogue" -> List.of(mod("Evasion", evasion, .08, CharacterStat.Operation.ADD),
                    mod("Quick strikes", "minecraft:generic.attack_speed", .08, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "wizard" -> List.of(mod("Arcane focus", magic, .15, CharacterStat.Operation.ADD));
            default -> List.of();
        };
    }

    private static void addBalance(JobDef job) {
        job.attributeModifiers().addAll(signature(job.id()));
        switch (job.id()) {
            case "archer" -> modifiers(job,
                    mod("Footwork", "minecraft:generic.movement_speed", .06, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Stamina recovery", "epicfight:stamina_regen", .12, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Light armour", "minecraft:generic.armor", -2, CharacterStat.Operation.ADD),
                    mod("Weak in melee", "minecraft:generic.attack_damage", -1, CharacterStat.Operation.ADD));
            case "fighter" -> modifiers(job,
                    mod("Weapon damage", "minecraft:generic.attack_damage", 2, CharacterStat.Operation.ADD),
                    mod("Combat stamina", "epicfight:staminar", 3, CharacterStat.Operation.ADD),
                    mod("Impact", "epicfight:impact", .5, CharacterStat.Operation.ADD),
                    mod("Untrained in magic", "irons_spellbooks:spell_power", -.15, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Heavy footwork", "minecraft:generic.movement_speed", -.03, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "tank" -> modifiers(job,
                    mod("Armour", "minecraft:generic.armor", 4, CharacterStat.Operation.ADD),
                    mod("Stun armour", "epicfight:stun_armor", 2, CharacterStat.Operation.ADD),
                    mod("Steady footing", "minecraft:generic.knockback_resistance", .2, CharacterStat.Operation.ADD),
                    mod("Slow attacks", "minecraft:generic.attack_speed", -.12, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Heavy gear", "minecraft:generic.movement_speed", -.05, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "brawler" -> modifiers(job,
                    mod("Quick hands", "minecraft:generic.attack_speed", .10, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Light on the feet", "minecraft:generic.movement_speed", .05, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("No armour training", "minecraft:generic.armor", -2, CharacterStat.Operation.ADD),
                    mod("Untrained in magic", "irons_spellbooks:spell_power", -.15, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "rogue" -> modifiers(job,
                    mod("Mobility", "minecraft:generic.movement_speed", .10, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Armour penetration", "epicfight:armor_negation", 5, CharacterStat.Operation.ADD),
                    mod("Low vitality", "minecraft:generic.max_health", -4, CharacterStat.Operation.ADD),
                    mod("Unarmoured", "minecraft:generic.armor", -2, CharacterStat.Operation.ADD));
            case "wizard" -> modifiers(job,
                    mod("Spell power", "irons_spellbooks:spell_power", .15, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Mana", "irons_spellbooks:max_mana", 100, CharacterStat.Operation.ADD),
                    mod("Mana flow", "irons_spellbooks:mana_regen", .10, CharacterStat.Operation.MULTIPLY_TOTAL),
                    mod("Fragile armour", "minecraft:generic.armor", -3, CharacterStat.Operation.ADD),
                    mod("Weak in melee", "minecraft:generic.attack_damage", -1, CharacterStat.Operation.ADD));
            case "miner" -> modifiers(job, mod("Mining health", "minecraft:generic.max_health", 2, CharacterStat.Operation.ADD), mod("Tool weight", "minecraft:generic.attack_speed", -.05, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "chef" -> modifiers(job, mod("Healthy meals", "minecraft:generic.max_health", 2, CharacterStat.Operation.ADD), mod("Kitchen pace", "minecraft:generic.movement_speed", -.03, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "fisher" -> modifiers(job, mod("River legs", "minecraft:generic.movement_speed", .04, CharacterStat.Operation.MULTIPLY_TOTAL), mod("Light build", "minecraft:generic.armor", -1, CharacterStat.Operation.ADD));
            case "alchemy" -> modifiers(job, mod("Spell resistance", "irons_spellbooks:spell_resist", .08, CharacterStat.Operation.MULTIPLY_TOTAL), mod("Physical frailty", "minecraft:generic.max_health", -2, CharacterStat.Operation.ADD));
            case "blacksmith" -> modifiers(job, mod("Forged defence", "minecraft:generic.armor", 2, CharacterStat.Operation.ADD), mod("Workshop weight", "minecraft:generic.movement_speed", -.04, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "farmer" -> modifiers(job, mod("Hardy", "minecraft:generic.max_health", 2, CharacterStat.Operation.ADD), mod("Field tools", "minecraft:generic.attack_speed", -.04, CharacterStat.Operation.MULTIPLY_TOTAL));
            case "rancher" -> modifiers(job, mod("Rider speed", "minecraft:generic.movement_speed", .05, CharacterStat.Operation.MULTIPLY_TOTAL), mod("Light armour", "minecraft:generic.armor", -1, CharacterStat.Operation.ADD));
            default -> { }
        }
    }

    private static JobAttributeModifier mod(String label, String attribute, double amount, CharacterStat.Operation operation) {
        return new JobAttributeModifier(translated("rotasutils.job.mod." + slug(label), label), attribute, amount, operation);
    }

    /** Thai text for a template string when the mod ships one; the English original otherwise. */
    private static String translated(String key, String english) {
        return net.schwarz.rotasutils.util.ThaiText.has(key) ? net.schwarz.rotasutils.util.ThaiText.t(key) : english;
    }

    private static String slug(String label) {
        return label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static void modifiers(JobDef job, JobAttributeModifier... modifiers) {
        job.attributeModifiers().addAll(List.of(modifiers));
    }

    private static Map<String, Archetype> templates() {
        Map<String, Archetype> result = new LinkedHashMap<>();
        add(result, "archer", "Archer", "Ranged combat specialist.", true, 0xFFEE4444, Items.BOW,
                List.of("#minecraft:arrows", "minecraft:bow", "minecraft:crossbow"), List.of("ranged_combat"));
        add(result, "fighter", "Fighter", "Balanced close-combat specialist.", true, 0xFF67C84A, Items.IRON_SWORD,
                List.of("#minecraft:swords"), List.of("melee_combat"));
        add(result, "tank", "Tank", "Armoured defender focused on survival and control.", true, 0xFFFF6A18, Items.SHIELD,
                List.of("minecraft:shield", "#minecraft:trimmable_armor"), List.of("blocking", "damage_taken"));
        add(result, "brawler", "Brawler", "Bare-handed fighter built on agility and raw strength.", true, 0xFFD96A3B, Items.BLAZE_POWDER,
                List.of(), List.of("unarmed_combat"));
        add(result, "rogue", "Rogue", "Fast specialist using light weapons and precision.", true, 0xFF5021D9, Items.IRON_SWORD,
                List.of("#forge:tools/knives", "minecraft:crossbow"), List.of("precision_combat", "stealth"));
        add(result, "wizard", "Wizard", "Magic specialist with spell and catalyst equipment.", true, 0xFF0098B4, Items.BLAZE_ROD,
                List.of("#rotasutils:wizard_items"), List.of("magic_combat"));
        add(result, "miner", "Miner", "Extracts ore and stone and develops mining efficiency.", false, 0xFFB7A16A, Items.IRON_PICKAXE,
                List.of("#minecraft:pickaxes", "#forge:ores"), List.of("mining"));
        add(result, "chef", "Chef", "Prepares meals and improves food crafting.", false, 0xFFE5D6B3, Items.COOKED_BEEF,
                List.of("#forge:foods"), List.of("cooking"));
        add(result, "fisher", "Fisher", "Catches fish and discovers aquatic resources.", false, 0xFF55A8D8, Items.FISHING_ROD,
                List.of("minecraft:fishing_rod", "#minecraft:fishes"), List.of("fishing"));
        add(result, "alchemy", "Alchemy", "Brews potions and processes magical ingredients.", false, 0xFF9B65C7, Items.BREWING_STAND,
                List.of("#rotasutils:alchemy_items"), List.of("brewing"));
        add(result, "blacksmith", "Blacksmith", "Forges, upgrades, and repairs equipment.", false, 0xFF70777B, Items.ANVIL,
                List.of("#minecraft:anvil", "#minecraft:trimmable_armor"), List.of("forging", "repair"));
        add(result, "farmer", "Farmer", "Grows crops and manages cultivated resources.", false, 0xFF79A94B, Items.WHEAT,
                List.of("#minecraft:crops", "#forge:seeds"), List.of("farming", "harvesting"));
        add(result, "rancher", "Rancher", "Raises animals and specialises in horse care.", false, 0xFFA7774F, Items.SADDLE,
                List.of("minecraft:saddle", "#forge:foods/animal"), List.of("animal_care", "breeding", "horse_care"));
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Archetype> target, String id, String name, String description,
                            boolean main, int color, Item icon, List<String> items, List<String> activities) {
        target.put(id, new Archetype(id, translated("rotasutils.job.template." + id + ".name", name),
                translated("rotasutils.job.template." + id + ".description", description), main, color, icon, items, activities));
    }
}
