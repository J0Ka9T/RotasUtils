package net.schwarz.rotasutils.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.job.JobArchetypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JobSkillTrees {
    private static final int[] MAIN_LEVEL = {1, 2, 6, 12, 20, 30, 45, 60};
    private static final int[] SUB_LEVEL = {1, 2, 5, 10, 18, 30};
    private static final int GRID = 40;
    private static final Pattern EFFECT = Pattern.compile("([A-Z]+)([+-][0-9.]+)(%?)");

    public static String categoryId(String jobId) {
        return "job_" + jobId;
    }

    private JobSkillTrees() {
    }

    public static SkillCategory create(String jobId) {
        JobArchetypes.Archetype job = JobArchetypes.all().stream().filter(a -> a.id().equals(jobId)).findFirst().orElse(null);
        if (job == null) {
            return null;
        }
        String[][] data = job.main() ? MAIN.get(jobId) : SUB.get(jobId);
        if (data == null) {
            return null;
        }
        SkillCategory tree = new SkillCategory(categoryId(jobId), "ทักษะ" + job.name());
        tree.setDescription(job.main()
                ? "ต้นไม้ทักษะหลักของ " + job.name() + " ใช้แต้มจากเลเวลอาชีพ สายละ 7 ปม และปมปิดท้าย"
                : "ต้นไม้ทักษะอาชีพเสริม " + job.name() + " ใช้แต้มจากเลเวลอาชีพเสริม");
        tree.setIcon(new ItemStack(job.icon()));
        tree.setAccentColor(job.color() | 0xFF000000);
        tree.jobs().add(jobId);
        if (job.main()) {
            main(tree, jobId, data);
        } else {
            sub(tree, jobId, data);
        }
        for (SkillNode node : tree.nodes().values()) {
            node.setRoot(node.connections().isEmpty());
        }
        return tree;
    }

    public static List<SkillCategory> all() {
        List<SkillCategory> trees = new ArrayList<>();
        for (JobArchetypes.Archetype job : JobArchetypes.all()) {
            SkillCategory tree = create(job.id());
            if (tree != null) {
                trees.add(tree);
            }
        }
        return trees;
    }

private static void main(SkillCategory tree, String job, String[][] data) {
        node(tree, job, "root", data[0], 0, 0, 1, 1, MAIN_LEVEL[0], SkillNode.NodeType.PASSIVE);
        int[] columns = {-3, 0, 3};
        String[] letters = {"a", "b", "c"};
        List<String> keystones = new ArrayList<>();
        for (int branch = 0; branch < 3; branch++) {
            int col = columns[branch];
            String p = letters[branch];
            String[][] s = java.util.Arrays.copyOfRange(data, 1 + branch * 7, 8 + branch * 7);
            node(tree, job, p + "1", s[0], col, 1, 3, 1, MAIN_LEVEL[1], SkillNode.NodeType.RANKED).link("root");
            node(tree, job, p + "2", s[1], col, 2, 3, 1, MAIN_LEVEL[2], SkillNode.NodeType.RANKED).link(p + "1");
            node(tree, job, p + "3", s[2], col - 1, 3, 3, 2, MAIN_LEVEL[3], SkillNode.NodeType.RANKED).link(p + "2");
            node(tree, job, p + "4", s[3], col + 1, 3, 3, 2, MAIN_LEVEL[3], SkillNode.NodeType.RANKED).link(p + "2");
            node(tree, job, p + "5", s[4], col, 4, 3, 2, MAIN_LEVEL[4], SkillNode.NodeType.RANKED)
                    .link(p + "3", SkillConnection.Type.REQUIRE_ANY).link(p + "4", SkillConnection.Type.REQUIRE_ANY);
            node(tree, job, p + "6", s[5], col, 5, 2, 3, MAIN_LEVEL[5], SkillNode.NodeType.RANKED).link(p + "5");
            node(tree, job, p + "k", s[6], col, 6, 1, 6, MAIN_LEVEL[6], SkillNode.NodeType.KEYSTONE).link(p + "6");
            keystones.add(p + "k");
        }
        Linked cap = node(tree, job, "cap", data[22], 0, 7, 1, 8, MAIN_LEVEL[7], SkillNode.NodeType.KEYSTONE);
        for (String keystone : keystones) {
            cap.link(keystone, SkillConnection.Type.REQUIRE_ANY);
        }
    }

    private static void sub(SkillCategory tree, String job, String[][] data) {
        node(tree, job, "root", data[0], 0, 0, 3, 1, SUB_LEVEL[0], SkillNode.NodeType.RANKED);
        int[] columns = {-2, 2};
        String[] letters = {"a", "b"};
        List<String> keystones = new ArrayList<>();
        for (int branch = 0; branch < 2; branch++) {
            int col = columns[branch];
            String p = letters[branch];
            String[][] s = java.util.Arrays.copyOfRange(data, 1 + branch * 4, 5 + branch * 4);
            node(tree, job, p + "1", s[0], col, 1, 3, 1, SUB_LEVEL[1], SkillNode.NodeType.RANKED).link("root");
            node(tree, job, p + "2", s[1], col, 2, 3, 1, SUB_LEVEL[2], SkillNode.NodeType.RANKED).link(p + "1");
            node(tree, job, p + "3", s[2], col, 3, 2, 2, SUB_LEVEL[3], SkillNode.NodeType.RANKED).link(p + "2");
            node(tree, job, p + "k", s[3], col, 4, 1, 4, SUB_LEVEL[4], SkillNode.NodeType.KEYSTONE).link(p + "3");
            keystones.add(p + "k");
        }
        Linked cap = node(tree, job, "cap", data[9], 0, 5, 1, 5, SUB_LEVEL[5], SkillNode.NodeType.KEYSTONE);
        for (String keystone : keystones) {
            cap.link(keystone, SkillConnection.Type.REQUIRE_ANY);
        }
    }

    private static Linked node(SkillCategory tree, String job, String key, String[] row, int col, int rowIndex,
                               int rank, int cost, int level, SkillNode.NodeType type) {
        String[] f = row;
        SkillNode node = new SkillNode("job_" + job + "_" + key, tree.id());
        node.setName(f[0]);
        node.setDescription(f[1]);
        ResourceLocation id = ResourceLocation.tryParse(f[2]);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            node.setIcon(new ItemStack(BuiltInRegistries.ITEM.get(id)));
        }
        node.setType(rank > 1 ? SkillNode.NodeType.RANKED : type == SkillNode.NodeType.RANKED ? SkillNode.NodeType.PASSIVE : type);
        node.setMaxRank(rank);
        node.setCostPerRank(cost);
        node.setMinLevel(level);
        node.setPosition(col * GRID, rowIndex * GRID);
        for (String token : f[3].split(";")) {
            SkillEffect effect = effect(token.trim());
            if (effect != null) {
                node.effects().add(effect);
            }
        }
        tree.putNode(node);
        return new Linked(node, job);
    }

    private static final class Linked {
        private final SkillNode node;
        private final String job;

        private Linked(SkillNode node, String job) {
            this.node = node;
            this.job = job;
        }

        Linked link(String key) {
            return link(key, SkillConnection.Type.NORMAL);
        }

        Linked link(String key, SkillConnection.Type type) {
            SkillConnection connection = new SkillConnection("job_" + job + "_" + key);
            connection.setType(type);
            node.connections().add(connection);
            return this;
        }
    }

    static SkillEffect effect(String token) {
        Matcher m = EFFECT.matcher(token);
        if (!m.matches()) {
            return null;
        }
        double value = Double.parseDouble(m.group(2));
        boolean percent = !m.group(3).isEmpty();
        EffectType type = switch (m.group(1)) {
            case "HP" -> EffectType.MAX_HEALTH;
            case "ARM" -> EffectType.ARMOR;
            case "TOU" -> EffectType.ARMOR_TOUGHNESS;
            case "ATK" -> EffectType.ATTACK_DAMAGE;
            case "AS" -> EffectType.ATTACK_SPEED;
            case "MS" -> EffectType.MOVEMENT_SPEED;
            case "KBR" -> EffectType.KNOCKBACK_RESISTANCE;
            case "LUK" -> EffectType.LUCK;
            case "CC" -> EffectType.CRIT_CHANCE;
            case "CD" -> EffectType.CRIT_DAMAGE;
            case "DEF" -> EffectType.DEFENSE_RATING;
            case "EVA" -> EffectType.DODGE_CHANCE;
            case "MAG" -> EffectType.MAGIC_POWER_BONUS;
            case "REG" -> EffectType.HEALTH_REGEN;
            case "XP" -> EffectType.ROTAS_XP_MULTIPLIER;
            case "MINE" -> EffectType.MINING_SPEED;
            case "CUR" -> EffectType.CURRENCY_MULTIPLIER;
            case "HEAL" -> EffectType.HEALING_MULTIPLIER;
            default -> throw new IllegalArgumentException("Unknown skill effect kind: " + m.group(1));
        };
        SkillEffect effect = new SkillEffect(type);
        effect.params().put("value", value);
        if (type.attribute() != null) {
            effect.params().put("percent", percent);
        }
        return effect;
    }

private static String[] n(String name, String description, String item, String effects) {
        return new String[]{name, description, item, effects};
    }

    private static final java.util.Map<String, String[][]> MAIN = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String[][]> SUB = new java.util.LinkedHashMap<>();

    static {
        MAIN.put("fighter", new String[][]{
                n("สัญชาตญาณนักรบ", "จุดเริ่มต้นของนักรบ โจมตีแรงขึ้นเล็กน้อย", "minecraft:iron_sword", "ATK+1%"),
                n("ฝึกดาบ", "ท่าพื้นฐานคมขึ้น โจมตีแรงขึ้น", "minecraft:iron_sword", "ATK+1.5%"),
                n("ข้อมือเหล็ก", "ฟันเร็วขึ้น", "minecraft:iron_axe", "AS+1.5%"),
                n("ฟันเฉียบ", "ติดคริติคอลบ่อยขึ้น", "minecraft:flint", "CC+1.5"),
                n("ฟันหนัก", "คริติคอลแรงขึ้น", "minecraft:anvil", "CD+6"),
                n("ท่วงท่านักดาบ", "ทั้งแรงทั้งไว", "minecraft:diamond_sword", "ATK+2%;AS+1%"),
                n("เจตนาสังหาร", "โจมตีแรงมาก", "minecraft:netherite_sword", "ATK+3%"),
                n("ปรมาจารย์ดาบ", "คริติคอลถี่และหนักหน่วง", "minecraft:enchanted_golden_apple", "CC+8;CD+40;ATK+4%"),
                n("ร่างกายแข็งแรง", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("เกราะเหล็ก", "เกราะหนาขึ้น", "minecraft:iron_chestplate", "ARM+1"),
                n("ยืนหยัด", "ไม่ค่อยกระเด็นเวลาโดนตี", "minecraft:shield", "KBR+0.05"),
                n("พักหายใจ", "ฟื้นเลือดเองช้า ๆ", "minecraft:golden_apple", "REG+0.15"),
                n("ผิวหนังหนา", "ลดความเสียหายที่ได้รับ", "minecraft:iron_leggings", "DEF+1.5"),
                n("เลือดนักสู้", "เลือดสูงสุดเพิ่มมาก", "minecraft:glistering_melon_slice", "HP+2.5%"),
                n("ไม่ยอมล้ม", "เลือดเยอะและฟื้นไว", "minecraft:totem_of_undying", "HP+8%;REG+0.5;DEF+8"),
                n("ก้าวเท้าเร็ว", "เคลื่อนที่ไวขึ้น", "minecraft:leather_boots", "MS+1%"),
                n("ประสบการณ์สนาม", "ได้ EXP มากขึ้น", "minecraft:experience_bottle", "XP+2"),
                n("สายตานักรบ", "หลบการโจมตีได้บ้าง", "minecraft:ender_eye", "EVA+0.8"),
                n("ค่าหัว", "ได้เงินมากขึ้นจากการต่อสู้", "minecraft:gold_nugget", "CUR+2"),
                n("ลีลานักสู้", "ไวและหลบเก่ง", "minecraft:feather", "MS+1%;EVA+1"),
                n("ผู้เชี่ยวชาญศึก", "ได้ EXP และเงินมากขึ้น", "minecraft:emerald", "XP+3;CUR+3"),
                n("แม่ทัพสนามรบ", "คล่องตัวและอยู่รอดได้นาน", "minecraft:netherite_helmet", "MS+4%;EVA+5;XP+8"),
                n("นักรบไร้พ่าย", "จุดสูงสุดของนักรบ", "minecraft:nether_star", "ATK+5%;HP+5%;CC+3")});
        MAIN.put("archer", new String[][]{
                n("สัญชาตญาณนักธนู", "จุดเริ่มต้นของนักธนู", "minecraft:bow", "CC+1"),
                n("สายตาเหยี่ยว", "ติดคริติคอลบ่อยขึ้น", "minecraft:spyglass", "CC+1.5"),
                n("ลมหายใจนิ่ง", "คริติคอลแรงขึ้น", "minecraft:feather", "CD+6"),
                n("เล็งขาด", "ยิงแรงขึ้น", "minecraft:arrow", "ATK+1.5%"),
                n("ปลายธนูคม", "ติดคริเพิ่ม", "minecraft:flint", "CC+1.5"),
                n("จุดตาย", "คริติคอลหนักหน่วง", "minecraft:target", "CD+8"),
                n("นิ้วเบาดั่งขนนก", "ยิงเร็วและแรง", "minecraft:crossbow", "ATK+2%;AS+1.5%"),
                n("ราชาแม่นธนู", "ทุกลูกธนูคือเป้าหมาย", "minecraft:spectral_arrow", "CC+10;CD+45;ATK+4%"),
                n("ฝีเท้าเบา", "เคลื่อนที่ไวขึ้น", "minecraft:leather_boots", "MS+1.5%"),
                n("ยิงรัว", "ยิงเร็วขึ้น", "minecraft:string", "AS+2%"),
                n("ลอบหลบ", "หลบการโจมตีได้", "minecraft:phantom_membrane", "EVA+1"),
                n("ถอยยิง", "ขยับได้ไวกว่าเดิม", "minecraft:rabbit_foot", "MS+1.5%"),
                n("นักล่าว่องไว", "ไวและหลบเก่ง", "minecraft:elytra", "MS+1%;EVA+1;AS+1%"),
                n("เงาลม", "หลบได้มากขึ้น", "minecraft:ender_pearl", "EVA+1.5"),
                n("ผู้ไร้เงา", "ไวสุดขีด หลบยาก", "minecraft:ender_eye", "MS+5%;EVA+6;AS+4%"),
                n("ผู้รอดป่า", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("ฟื้นกำลัง", "ฟื้นเลือดเอง", "minecraft:sweet_berries", "REG+0.15"),
                n("นักสำรวจ", "ได้ EXP มากขึ้น", "minecraft:compass", "XP+2"),
                n("โชคนักล่า", "โชคดีขึ้น ของดรอปดีขึ้น", "minecraft:rabbit_foot", "LUK+0.1"),
                n("เสื้อคลุมป่า", "ป้องกันได้เล็กน้อย", "minecraft:leather_chestplate", "DEF+1.5"),
                n("ผู้เชี่ยวชาญป่า", "ได้ EXP และโชคมากขึ้น", "minecraft:emerald", "XP+3;LUK+0.15"),
                n("ลูกหลานแห่งป่า", "ทนทานและโชคดี", "minecraft:oak_sapling", "HP+6%;REG+0.5;LUK+0.5"),
                n("นักธนูในตำนาน", "จุดสูงสุดของนักธนู", "minecraft:nether_star", "CC+5;CD+20;MS+3%")});
        MAIN.put("tank", new String[][]{
                n("สัญชาตญาณผู้พิทักษ์", "จุดเริ่มต้นของแทงก์", "minecraft:shield", "DEF+1"),
                n("โล่หนา", "ลดความเสียหายที่ได้รับ", "minecraft:shield", "DEF+2"),
                n("เกราะแน่น", "เกราะหนาขึ้น", "minecraft:iron_chestplate", "ARM+1"),
                n("ความแกร่ง", "ลดแรงกระแทก ความแกร่งเกราะเพิ่ม", "minecraft:iron_ingot", "TOU+0.5"),
                n("ไม่ล้มง่าย", "แรงผลักน้อยลง", "minecraft:anvil", "KBR+0.06"),
                n("กำแพงเหล็ก", "ลดความเสียหายมาก", "minecraft:iron_block", "DEF+3"),
                n("เกราะเสริมแกร่ง", "เกราะและความแกร่ง", "minecraft:diamond_chestplate", "ARM+1;TOU+0.5"),
                n("ป้อมปราการ", "เกราะหนักจนไม่มีใครฝ่า", "minecraft:netherite_chestplate", "DEF+15;ARM+4;KBR+0.2"),
                n("ร่างกายมหึมา", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+2%"),
                n("พลังฟื้นตัว", "ฟื้นเลือดเอง", "minecraft:golden_apple", "REG+0.2"),
                n("กินจุ", "เลือดสูงสุดเพิ่มอีก", "minecraft:cooked_beef", "HP+2%"),
                n("ผิวหินผา", "ลดความเสียหายต่อเนื่อง", "minecraft:stone", "DEF+2"),
                n("ร่างมังกรเขียว", "อึดและฟื้นไว", "minecraft:enchanted_golden_apple", "HP+2%;REG+0.15"),
                n("เลือดหนาแน่น", "เลือดสูงสุดเพิ่มมาก", "minecraft:glistering_melon_slice", "HP+3%"),
                n("ร่างอมตะ", "เลือดมหาศาลและฟื้นไว", "minecraft:totem_of_undying", "HP+10%;REG+0.6;DEF+10"),
                n("ตีโต้", "โจมตีแรงขึ้นเล็กน้อย", "minecraft:stone_sword", "ATK+1%"),
                n("ยั่วยุ", "ได้ EXP มากขึ้น", "minecraft:experience_bottle", "XP+2"),
                n("หัวหน้าแนวหน้า", "ได้เงินมากขึ้น", "minecraft:gold_ingot", "CUR+2"),
                n("ก้าวหนัก", "ไม่ช้าเพราะเกราะ", "minecraft:iron_boots", "MS+1%"),
                n("เสียงคำราม", "โจมตีแรงขึ้นอีก", "minecraft:goat_horn", "ATK+1.5%"),
                n("ผู้นำทัพ", "ได้ EXP และเงินมากขึ้น", "minecraft:emerald", "XP+3;CUR+3"),
                n("ยอดผู้นำ", "แข็งแรงและนำทัพ", "minecraft:white_banner", "ATK+3%;XP+8;MS+3%"),
                n("เทพผู้พิทักษ์", "จุดสูงสุดของแทงก์", "minecraft:nether_star", "DEF+8;HP+5%;KBR+0.15")});
        MAIN.put("brawler", new String[][]{
                n("สัญชาตญาณนักสู้", "จุดเริ่มต้นของนักสู้มือเปล่า", "minecraft:blaze_powder", "ATK+1%"),
                n("กำปั้นหนัก", "หมัดแรงขึ้น", "minecraft:iron_nugget", "ATK+1.5%"),
                n("หมัดรัว", "ชกไวขึ้น", "minecraft:sugar", "AS+2%"),
                n("เข้าจังหวะ", "ติดคริบ่อยขึ้น", "minecraft:flint", "CC+1.5"),
                n("หมัดทลาย", "คริติคอลหนักขึ้น", "minecraft:tnt", "CD+6"),
                n("ชกต่อเนื่อง", "แรงและไว", "minecraft:blaze_rod", "ATK+2%;AS+1.5%"),
                n("หมัดมังกร", "ทุกหมัดหนักอึ้ง", "minecraft:dragon_breath", "ATK+3%"),
                n("เจ้าหมัดสังหาร", "คริถี่ แรงหนัก", "minecraft:netherite_scrap", "CC+8;CD+40;ATK+4%"),
                n("ลีลาก้าวถอย", "เคลื่อนที่ไวขึ้น", "minecraft:leather_boots", "MS+1.5%"),
                n("เกลี้ยกเกลา", "ชกไวขึ้นอีก", "minecraft:feather", "AS+2%"),
                n("หลบเฉียด", "หลบการโจมตีได้", "minecraft:phantom_membrane", "EVA+1"),
                n("เบี่ยงตัว", "หลบได้มากขึ้น", "minecraft:rabbit_foot", "EVA+1"),
                n("ท่าเต้นสังหาร", "ไวและหลบเก่ง", "minecraft:elytra", "MS+1%;EVA+1;AS+1%"),
                n("ลมหายใจนักสู้", "ไวขึ้นอีก", "minecraft:sugar_cane", "AS+2.5%"),
                n("ผู้ไร้เงา", "ไวสุดขีด หลบยาก", "minecraft:ender_eye", "MS+5%;EVA+6;AS+5%"),
                n("ร่างเหล็ก", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("หายใจลึก", "ฟื้นเลือดเอง", "minecraft:sweet_berries", "REG+0.2"),
                n("ผิวหนาด้าน", "ลดความเสียหาย", "minecraft:leather_chestplate", "DEF+1.5"),
                n("ไม่ถอย", "แรงผลักน้อยลง", "minecraft:anvil", "KBR+0.06"),
                n("เลือดร้อน", "เลือดและการฟื้นตัว", "minecraft:golden_apple", "HP+2%;REG+0.15"),
                n("ร่างเหล็กกล้า", "เลือดและเกราะ", "minecraft:iron_chestplate", "HP+2%;DEF+2"),
                n("ร่างไม่เจ็บ", "ทนทานสุดขีด", "minecraft:totem_of_undying", "HP+6%;REG+0.5;DEF+6"),
                n("ราชามวย", "จุดสูงสุดของนักสู้", "minecraft:nether_star", "ATK+5%;AS+4%;CC+3")});
        MAIN.put("rogue", new String[][]{
                n("สัญชาตญาณโจร", "จุดเริ่มต้นของโจร", "minecraft:iron_sword", "CC+1"),
                n("ฟันลับ", "ติดคริบ่อยขึ้น", "minecraft:flint", "CC+1.5"),
                n("แทงจุดตาย", "คริติคอลแรงขึ้น", "minecraft:iron_sword", "CD+7"),
                n("ใบมีดคม", "โจมตีแรงขึ้น", "minecraft:shears", "ATK+1.5%"),
                n("แม่นยำ", "ติดคริเพิ่ม", "minecraft:spyglass", "CC+1.5"),
                n("ฟันซ้ำ", "คริติคอลหนักขึ้น", "minecraft:netherite_sword", "CD+8"),
                n("ฟันไว", "แรงและไว", "minecraft:iron_axe", "ATK+2%;AS+1.5%"),
                n("ฆาตกรเงียบ", "คริติคอลตายคาที่", "minecraft:wither_skeleton_skull", "CC+10;CD+50;ATK+4%"),
                n("ก้าวย่องเบา", "เคลื่อนที่ไวขึ้น", "minecraft:leather_boots", "MS+1.5%"),
                n("หลบหลีก", "หลบการโจมตีได้", "minecraft:phantom_membrane", "EVA+1.2"),
                n("ลื่นไหล", "หลบได้มากขึ้น", "minecraft:rabbit_foot", "EVA+1.2"),
                n("ใบมีดไว", "ฟันไวขึ้น", "minecraft:sugar", "AS+2%"),
                n("เงาในความมืด", "ไวและหลบเก่ง", "minecraft:ender_pearl", "MS+1%;EVA+1.5"),
                n("ไร้ร่องรอย", "หลบได้มาก", "minecraft:ender_eye", "EVA+2"),
                n("ราชาแห่งเงา", "เร็วและหลบเกือบทุกอย่าง", "minecraft:elytra", "MS+5%;EVA+8;AS+4%"),
                n("มือไว", "โชคดีขึ้น ของดรอปดีขึ้น", "minecraft:rabbit_foot", "LUK+0.1"),
                n("นักล้วงกระเป๋า", "ได้เงินมากขึ้น", "minecraft:gold_nugget", "CUR+2"),
                n("ข่าวกรอง", "ได้ EXP มากขึ้น", "minecraft:book", "XP+2"),
                n("ตาแหลม", "โชคดีเพิ่ม", "minecraft:spyglass", "LUK+0.1"),
                n("ขโมยมืออาชีพ", "เงินและ EXP", "minecraft:emerald", "CUR+3;XP+2"),
                n("เสน่ห์จอมโจร", "ได้เงินและโชคมากขึ้น", "minecraft:diamond", "CUR+2;LUK+0.1"),
                n("ราชาแห่งโชค", "เงินเยอะ โชคดี", "minecraft:diamond", "CUR+4;LUK+0.15;XP+3"),
                n("จอมโจรตำนาน", "จุดสูงสุดของโจร", "minecraft:nether_star", "CC+6;CD+25;EVA+5")});
        MAIN.put("wizard", new String[][]{
                n("สัญชาตญาณจอมเวท", "จุดเริ่มต้นของจอมเวท", "minecraft:blaze_rod", "MAG+1"),
                n("พลังเวทเบื้องต้น", "เวทแรงขึ้น", "minecraft:book", "MAG+1.5"),
                n("สมาธิ", "เวทแรงขึ้นอีก", "minecraft:enchanted_book", "MAG+1.5"),
                n("ความรู้ต้องห้าม", "เวทแรงขึ้นมาก", "minecraft:writable_book", "MAG+2"),
                n("เปลวเพลิงอาคม", "เวทแรงขึ้น", "minecraft:blaze_powder", "MAG+2"),
                n("พายุเวท", "เวทแรงขึ้นมาก", "minecraft:lightning_rod", "MAG+2.5"),
                n("ธาตุทั้งห้า", "เวทหนักหน่วง", "minecraft:nether_star", "MAG+3"),
                n("อาร์คเมจ", "จอมเวทผู้เรืองอำนาจ", "minecraft:end_crystal", "MAG+15;CC+3;CD+25"),
                n("ผ้าคลุมเวท", "ลดความเสียหาย", "minecraft:leather_chestplate", "DEF+1.5"),
                n("ร่างเวท", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("เกราะอาคม", "ลดความเสียหายอีก", "minecraft:shield", "DEF+2"),
                n("ฟื้นพลัง", "ฟื้นเลือดเอง", "minecraft:glow_berries", "REG+0.15"),
                n("เกราะผลึก", "ทนทานขึ้น", "minecraft:amethyst_shard", "DEF+2;HP+1.5%"),
                n("ธาตุปกป้อง", "ลดความเสียหายมาก", "minecraft:prismarine_crystals", "DEF+3"),
                n("ปราการเวท", "อึดที่สุดในสายนี้", "minecraft:totem_of_undying", "HP+6%;REG+0.5;DEF+8"),
                n("ใฝ่รู้", "ได้ EXP มากขึ้น", "minecraft:experience_bottle", "XP+2"),
                n("นักเล่นแร่", "ได้เงินมากขึ้น", "minecraft:gold_ingot", "CUR+2"),
                n("พลังรักษา", "การรักษาแรงขึ้น", "minecraft:glistering_melon_slice", "HEAL+3"),
                n("โชคเวท", "โชคดีขึ้น", "minecraft:lapis_lazuli", "LUK+0.1"),
                n("ปราชญ์", "EXP และเงิน", "minecraft:emerald", "XP+3;CUR+3"),
                n("นักปราชญ์ผู้ยิ่งใหญ่", "รู้ลึกรู้จริง", "minecraft:enchanted_golden_apple", "XP+6;HEAL+6;LUK+0.3"),
                n("ดวงตาแห่งปัญญา", "เห็นทุกสิ่ง ได้ทุกอย่าง", "minecraft:ender_eye", "XP+8;CUR+6;HEAL+10;LUK+0.4"),
                n("เทพเวทตำนาน", "จุดสูงสุดของจอมเวท", "minecraft:nether_star", "MAG+10;HP+5%;REG+0.4")});

        SUB.put("miner", new String[][]{
                n("มือใหม่ในเหมือง", "ขุดเร็วขึ้นเล็กน้อย", "minecraft:iron_pickaxe", "MINE+2"),
                n("ขุดไว", "ขุดเร็วขึ้น", "minecraft:iron_pickaxe", "MINE+3"),
                n("แขนแกร่ง", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1%"),
                n("ตาแร่", "โชคดีขึ้น", "minecraft:raw_gold", "LUK+0.1"),
                n("ราชาเหมือง", "ขุดเร็วสุดขีด", "minecraft:diamond_pickaxe", "MINE+10;LUK+0.3"),
                n("หมวกนิรภัย", "ป้องกันมากขึ้น", "minecraft:iron_helmet", "DEF+1"),
                n("ปอดเหมือง", "ฟื้นเลือดเอง", "minecraft:sweet_berries", "REG+0.1"),
                n("ขุดลึก", "ได้ EXP มากขึ้น", "minecraft:experience_bottle", "XP+2"),
                n("ผู้รอดชีวิต", "ทนทานขึ้นมาก", "minecraft:totem_of_undying", "HP+5%;DEF+4"),
                n("ปรมาจารย์เหมือง", "จุดสูงสุดของนักขุด", "minecraft:netherite_pickaxe", "MINE+8;XP+4")});
        SUB.put("farmer", new String[][]{
                n("มือปลูก", "ได้ EXP มากขึ้นเล็กน้อย", "minecraft:wheat_seeds", "XP+1"),
                n("ฤดูเก็บเกี่ยว", "ได้ EXP มากขึ้น", "minecraft:wheat", "XP+2"),
                n("ขายได้ราคา", "ได้เงินมากขึ้น", "minecraft:gold_nugget", "CUR+2"),
                n("โชคเกษตร", "โชคดีขึ้น", "minecraft:rabbit_foot", "LUK+0.1"),
                n("ราชาแห่งไร่", "ผลผลิตเฟื่องฟู", "minecraft:golden_carrot", "XP+5;CUR+5;LUK+0.2"),
                n("ชาวนาแกร่ง", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("อากาศบริสุทธิ์", "ฟื้นเลือดเอง", "minecraft:sweet_berries", "REG+0.1"),
                n("เดินทุ่ง", "เคลื่อนที่ไวขึ้น", "minecraft:leather_boots", "MS+1%"),
                n("ร่างกายชาวไร่", "ทนทานขึ้นมาก", "minecraft:hay_block", "HP+5%;REG+0.3"),
                n("ปรมาจารย์เกษตร", "จุดสูงสุดของชาวไร่", "minecraft:diamond_hoe", "XP+5;CUR+4")});
        SUB.put("fisher", new String[][]{
                n("มือใหม่ริมน้ำ", "โชคดีขึ้นเล็กน้อย", "minecraft:fishing_rod", "LUK+0.1"),
                n("ตาหาปลา", "โชคดีขึ้น", "minecraft:cod", "LUK+0.15"),
                n("ขายปลา", "ได้เงินมากขึ้น", "minecraft:gold_nugget", "CUR+2"),
                n("คัมภีร์ท้องทะเล", "ได้ EXP มากขึ้น", "minecraft:book", "XP+2"),
                n("ราชาแห่งทะเล", "โชคและเงินมหาศาล", "minecraft:nautilus_shell", "LUK+0.5;CUR+5"),
                n("ขาริมแม่น้ำ", "เคลื่อนที่ไวขึ้น", "minecraft:leather_boots", "MS+1%"),
                n("ลื่นเหมือนปลา", "หลบการโจมตีได้", "minecraft:salmon", "EVA+1"),
                n("ลมทะเล", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("ชาวทะเลผู้อึด", "คล่องตัวและทนทาน", "minecraft:heart_of_the_sea", "MS+3%;EVA+3;HP+4%"),
                n("ปรมาจารย์ตกปลา", "จุดสูงสุดของนักตกปลา", "minecraft:trident", "LUK+0.4;XP+5")});
        SUB.put("chef", new String[][]{
                n("มือใหม่ในครัว", "อาหารฟื้นพลังได้มากขึ้นเล็กน้อย", "minecraft:bread", "HEAL+2"),
                n("ปรุงด้วยใจ", "การรักษาแรงขึ้น", "minecraft:cooked_beef", "HEAL+3"),
                n("เมนูพลังงาน", "ฟื้นเลือดเอง", "minecraft:golden_carrot", "REG+0.1"),
                n("กินอิ่ม", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("เชฟมือทอง", "อาหารทรงพลัง", "minecraft:cake", "HEAL+8;REG+0.3;HP+3%"),
                n("เปิดร้าน", "ได้เงินมากขึ้น", "minecraft:gold_nugget", "CUR+2"),
                n("ลูกค้าประจำ", "ได้ EXP มากขึ้น", "minecraft:experience_bottle", "XP+2"),
                n("วัตถุดิบดี", "โชคดีขึ้น", "minecraft:rabbit_foot", "LUK+0.1"),
                n("ร้านดัง", "เงินและ EXP", "minecraft:emerald", "CUR+5;XP+4"),
                n("เชฟระดับโลก", "จุดสูงสุดของเชฟ", "minecraft:pumpkin_pie", "HEAL+6;CUR+4;XP+3")});
        SUB.put("alchemy", new String[][]{
                n("ศิษย์เล่นแร่", "เวทแรงขึ้นเล็กน้อย", "minecraft:brewing_stand", "MAG+1"),
                n("สูตรลับ", "เวทแรงขึ้น", "minecraft:glass_bottle", "MAG+1.5"),
                n("น้ำยารักษา", "การรักษาแรงขึ้น", "minecraft:glistering_melon_slice", "HEAL+3"),
                n("ฟื้นพลังด้วยยา", "ฟื้นเลือดเอง", "minecraft:potion", "REG+0.15"),
                n("นักปรุงยาเทพ", "ยาและเวทร่วมกัน", "minecraft:dragon_breath", "MAG+8;HEAL+8;REG+0.3"),
                n("ภูมิต้านทาน", "ลดความเสียหาย", "minecraft:milk_bucket", "DEF+1"),
                n("ร่างกายทนยา", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("หลบด้วยควัน", "หลบการโจมตีได้", "minecraft:phantom_membrane", "EVA+1"),
                n("ผู้รอดชีวิต", "ทนทานและหลบเก่ง", "minecraft:totem_of_undying", "HP+4%;DEF+3;EVA+3"),
                n("ปรมาจารย์เล่นแร่", "จุดสูงสุดของนักเล่นแร่", "minecraft:enchanted_golden_apple", "MAG+6;XP+4")});
        SUB.put("blacksmith", new String[][]{
                n("ลูกมือช่างตี", "โจมตีแรงขึ้นเล็กน้อย", "minecraft:iron_ingot", "ATK+0.5%"),
                n("ค้อนหนัก", "โจมตีแรงขึ้น", "minecraft:iron_axe", "ATK+1%"),
                n("เกราะตีเอง", "เกราะหนาขึ้น", "minecraft:iron_chestplate", "ARM+1"),
                n("เหล็กกล้า", "ความแกร่งเกราะเพิ่ม", "minecraft:iron_block", "TOU+0.5"),
                n("ช่างตีเหล็กเอก", "อาวุธและเกราะเหนือชั้น", "minecraft:anvil", "ATK+4%;ARM+3;TOU+1"),
                n("ซ่อมได้ดี", "ลดความเสียหาย", "minecraft:smithing_table", "DEF+1"),
                n("แขนช่าง", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("ขายของดี", "ได้เงินมากขึ้น", "minecraft:gold_ingot", "CUR+2"),
                n("ช่างแห่งเมือง", "ทนทานและมีเงิน", "minecraft:diamond_block", "DEF+3;HP+4%;CUR+3"),
                n("ปรมาจารย์ช่างตี", "จุดสูงสุดของช่างตี", "minecraft:netherite_ingot", "ATK+4%;ARM+3;XP+4")});
        SUB.put("rancher", new String[][]{
                n("มือใหม่ในคอก", "โชคดีขึ้นเล็กน้อย", "minecraft:lead", "LUK+0.1"),
                n("เลี้ยงสัตว์เก่ง", "ได้ EXP มากขึ้น", "minecraft:wheat", "XP+2"),
                n("ขายสัตว์", "ได้เงินมากขึ้น", "minecraft:gold_nugget", "CUR+2"),
                n("นมสดและไข่", "โชคดีขึ้น", "minecraft:egg", "LUK+0.1"),
                n("เจ้าของฟาร์ม", "ฟาร์มเฟื่องฟู", "minecraft:hay_block", "XP+5;CUR+5;LUK+0.2"),
                n("นักขี่ม้า", "เคลื่อนที่ไวขึ้น", "minecraft:saddle", "MS+1.5%"),
                n("ผูกใจม้า", "เลือดสูงสุดมากขึ้น", "minecraft:apple", "HP+1.5%"),
                n("นั่งมั่น", "แรงผลักน้อยลง", "minecraft:lead", "KBR+0.05"),
                n("ผู้ขี่แห่งทุ่ง", "ไวและมั่นคง", "minecraft:golden_horse_armor", "MS+4%;KBR+0.1;HP+4%"),
                n("ปรมาจารย์ผู้เลี้ยง", "จุดสูงสุดของผู้เลี้ยงสัตว์", "minecraft:diamond_horse_armor", "XP+4;CUR+4;MS+3%")});
    }
}
