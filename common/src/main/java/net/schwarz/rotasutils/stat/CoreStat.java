package net.schwarz.rotasutils.stat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.level.StatRules;
import net.schwarz.rotasutils.server.CombatStats;

import java.util.List;

public enum CoreStat {
    STR("rotas:str", "พลังโจมตี (STR)", "ตีแรงขึ้น ทั้งอาวุธและมือเปล่า", Items.IRON_SWORD, 0xFFE2695C),
    VIT("rotas:vit", "พลังชีวิต (VIT)", "เลือดเยอะขึ้น ทนขึ้น และฟื้นเลือดเองได้", Items.GOLDEN_APPLE, 0xFF86C05C),
    INT("rotas:int", "พลังเวทย์ (INT)", "เวทมนตร์แรงขึ้นและลดคูลดาวน์", Items.ENCHANTED_BOOK, 0xFFB08CE8),
    AGI("rotas:agi", "ความเร็ว (AGI)", "ตีไวขึ้นและหลบการโจมตีได้", Items.FEATHER, 0xFF7FD1C7),
    DEX("rotas:dex", "คริติคอล (DEX)", "ตีติดคริบ่อยขึ้น แรงขึ้น และเจาะเกราะ", Items.BOW, 0xFFE8C45C),
    LUK("rotas:luk", "โชค (LUK)", "ดรอปของและการ์ดดีขึ้น ตกปลาดีขึ้น และมีคริเล็กน้อย", Items.RABBIT_FOOT, 0xFF6FCF8E);

    public static final List<CoreStat> ALL = List.of(values());

    private final String id;
    private final String name;
    private final String description;
    private final Item icon;
    private final int color;

    CoreStat(String id, String name, String description, Item icon, int color) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.color = color;
    }

    public String id() { return id; }
    public String displayName() { return name; }
    public String description() { return description; }
    public ItemStack icon() { return new ItemStack(icon); }
    public int color() { return color; }

    public static CoreStat byId(String id) {
        for (CoreStat stat : ALL) {
            if (stat.id.equals(id)) {
                return stat;
            }
        }
        return null;
    }

    public List<CharacterStat.Effect> effects(StatRules rules) {
        return switch (this) {
            case STR -> List.of(percent("minecraft:generic.attack_damage", rules.strAttack, "พลังโจมตี"));
            case VIT -> List.of(percent("minecraft:generic.max_health", rules.vitHealth, "พลังชีวิต"),
                    flat(CombatStats.DEFENSE, rules.vitDefense, "เกราะ"),
                    new CharacterStat.Effect(CombatStats.REGEN, rules.vitRegen, CharacterStat.Operation.ADD,
                            true, "ฟื้นเลือด/วินาที", 0));
            case INT -> List.of(percent(CombatStats.MAGIC_POWER, rules.intMagic, "พลังเวทย์"),
                    chance(CombatStats.COOLDOWN_REDUCTION, rules.intCdr, "ลดคูลดาวน์"));
            case AGI -> List.of(percent("minecraft:generic.attack_speed", rules.agiAttackSpeed, "ความเร็วโจมตี"),
                    new CharacterStat.Effect(CombatStats.EVASION, rules.agiDodge, CharacterStat.Operation.ADD,
                            true, "โอกาสหลบ", 0));
            case DEX -> List.of(chance(CombatStats.CRIT_CHANCE, rules.dexCrit, "โอกาสคริติคอล"),
                    chance(CombatStats.CRIT_DAMAGE, rules.dexCritDamage, "ดาเมจคริติคอล"),
                    chance(CombatStats.ARMOR_PEN, rules.dexArmorPen, "เจาะเกราะ"));
            case LUK -> List.of(flat("minecraft:generic.luck", rules.lukLuck, "โชค"),
                    chance(CombatStats.CRIT_CHANCE, rules.lukCrit, "โอกาสคริติคอล"),
                    chance(CombatStats.DROP_RATE, rules.lukDropRate, "อัตราดรอป"));
        };
    }

    private static CharacterStat.Effect flat(String attribute, double perPoint, String label) {
        return new CharacterStat.Effect(attribute, perPoint, CharacterStat.Operation.ADD, false, label, 0);
    }

    private static CharacterStat.Effect chance(String attribute, double perPoint, String label) {
        return new CharacterStat.Effect(attribute, perPoint, CharacterStat.Operation.ADD, true, label, 0);
    }

    public String describe(StatRules rules, int points) {
        StringBuilder text = new StringBuilder();
        for (CharacterStat.Effect effect : effects(rules)) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(effect.describe(effect.perPoint() * Math.max(0, points)));
        }
        return text.toString();
    }

    private static CharacterStat.Effect percent(String attribute, double perPoint, String label) {
        return new CharacterStat.Effect(attribute, perPoint, CharacterStat.Operation.MULTIPLY_BASE, true, label, 0);
    }
}
