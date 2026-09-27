package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.schwarz.rotasutils.core.RefineMath;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.Locale;

/**
 * The refine level a weapon or a piece of armour carries, and how it is read back.
 *
 * <p>The level lives in a {@code RotasRefine} compound on the stack itself, so it survives a chest, a
 * death, a trade and a server restart without a side table, and it works on any item - vanilla, kernel
 * or another mod's - as long as the game treats it as a weapon or as armour.</p>
 */
public final class ItemRefine {
    public static final String TAG = "RotasRefine";
    private static final String LEVEL = "level";
    private static final String BASE_NAME = "base_name";

    private ItemRefine() {
    }

    /** What a stack can be refined as. */
    public enum Category {
        WEAPON, ARMOR, NONE;

        public boolean refinable() {
            return this != NONE;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How the game itself classifies the stack. A weapon is anything swung for damage or fired - which
     * covers modded swords and bows without naming a single mod - and armour is anything the game would
     * put in an armour slot.
     */
    public static Category categoryOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getMaxStackSize() != 1) {
            return Category.NONE;
        }
        if (stack.getItem() instanceof ArmorItem) {
            return Category.ARMOR;
        }
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        if (slot != null && slot.getType() == EquipmentSlot.Type.ARMOR) {
            return Category.ARMOR;
        }
        if (stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem) {
            return Category.WEAPON;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)
                ? Category.WEAPON : Category.NONE;
    }

    /** The refine level on the stack, 0 when it has never been refined. */
    public static int level(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return 0;
        }
        return Math.max(0, tag.getCompound(TAG).getInt(LEVEL));
    }

    /**
     * Writes a refine level and rebuilds the displayed name. Level 0 removes the mark completely, so an
     * item reset by a failure looks exactly like one that was never refined.
     */
    public static void setLevel(ItemStack stack, int level) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int value = Math.max(0, level);
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag refine = tag.contains(TAG, Tag.TAG_COMPOUND) ? tag.getCompound(TAG) : new CompoundTag();
        Component base = baseName(stack, refine);
        if (value <= 0) {
            tag.remove(TAG);
            if (refine.contains(BASE_NAME, Tag.TAG_STRING)) {
                stack.setHoverName(base);
            } else {
                stack.resetHoverName();
            }
            return;
        }
        refine.putInt("schema", 1);
        refine.putInt(LEVEL, value);
        if (!refine.contains(BASE_NAME, Tag.TAG_STRING)) {
            refine.putString(BASE_NAME, Component.Serializer.toJson(base));
        }
        tag.put(TAG, refine);
        stack.setHoverName(Component.literal("+" + value + " ").withStyle(color(value)).append(base));
    }

    /**
     * The name the item had before it was ever refined: its own custom name when it had one, otherwise
     * the plain item name. Stored once, so "+8 " never ends up written twice into the same name.
     */
    private static Component baseName(ItemStack stack, CompoundTag refine) {
        if (refine.contains(BASE_NAME, Tag.TAG_STRING)) {
            Component stored = Component.Serializer.fromJson(refine.getString(BASE_NAME));
            if (stored != null) {
                return stored;
            }
        }
        return stack.hasCustomHoverName() ? stack.getHoverName() : Component.translatable(stack.getDescriptionId());
    }

    /** The colour a refine level is shown in, so its worth reads at a glance. */
    public static ChatFormatting color(int level) {
        if (level >= 10) {
            return ChatFormatting.LIGHT_PURPLE;
        }
        if (level >= 9) {
            return ChatFormatting.GOLD;
        }
        if (level >= 7) {
            return ChatFormatting.YELLOW;
        }
        return level >= 5 ? ChatFormatting.AQUA : ChatFormatting.WHITE;
    }

    /** Flat attack damage this weapon's refinement is worth. */
    public static double attackBonus(SeasonRules.RefineRules rules, ItemStack stack) {
        if (rules == null || !rules.enabled || categoryOf(stack) != Category.WEAPON) {
            return 0;
        }
        return RefineMath.bonusValue(level(stack), rules.safeLevel, rules.attackPerLevel, rules.attackPerOverLevel);
    }

    /** Defence this armour piece's refinement is worth, on the same channel character stats use. */
    public static double defenseBonus(SeasonRules.RefineRules rules, ItemStack stack) {
        if (rules == null || !rules.enabled || categoryOf(stack) != Category.ARMOR) {
            return 0;
        }
        return RefineMath.bonusValue(level(stack), rules.safeLevel, rules.defensePerLevel, rules.defensePerOverLevel);
    }

    /** The name with its refine mark, for chat lines and screens. */
    public static MutableComponent displayName(ItemStack stack) {
        return stack.getHoverName().copy();
    }
}
