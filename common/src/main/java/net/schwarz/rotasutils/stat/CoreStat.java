package net.schwarz.rotasutils.stat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.level.StatRules;
import net.schwarz.rotasutils.server.CombatStats;

import java.util.List;

/**
 * The four character stats. Fixed in code, tuned by {@link StatRules}.
 *
 * <p>Each point adds the same percent, so the Stats screen can say exactly what a point does and the
 * server applies exactly that. Allocations are stored in the player's RPG profile under {@link #id()}.</p>
 */
public enum CoreStat {
    STR("rotas:str", "STR พลัง", "ตีแรงขึ้น ทั้งอาวุธและมือเปล่า", Items.IRON_SWORD, 0xFFE2695C),
    VIT("rotas:vit", "VIT ความอึด", "พลังชีวิตสูงสุดมากขึ้น", Items.GOLDEN_APPLE, 0xFF86C05C),
    INT("rotas:int", "INT เวทมนตร์", "เวทมนตร์แรงขึ้น", Items.ENCHANTED_BOOK, 0xFFB08CE8),
    AGI("rotas:agi", "AGI ความไว", "โจมตีเร็วขึ้นและหลบการโจมตีได้", Items.FEATHER, 0xFF7FD1C7);

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

    /** What one point gives. The amount of each effect is {@code perPoint * points}. */
    public List<CharacterStat.Effect> effects(StatRules rules) {
        return switch (this) {
            case STR -> List.of(percent("minecraft:generic.attack_damage", rules.strAttack, "พลังโจมตี"));
            case VIT -> List.of(percent("minecraft:generic.max_health", rules.vitHealth, "พลังชีวิต"));
            case INT -> List.of(percent(CombatStats.MAGIC_POWER, rules.intMagic, "พลังเวทย์"));
            case AGI -> List.of(percent("minecraft:generic.attack_speed", rules.agiAttackSpeed, "ความเร็วโจมตี"),
                    new CharacterStat.Effect(CombatStats.EVASION, rules.agiDodge, CharacterStat.Operation.ADD,
                            true, "โอกาสหลบ", 0));
        };
    }

    /** Player-facing text for {@code points} in this stat, e.g. "+20% พลังโจมตี". */
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
