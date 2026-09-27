package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.schwarz.rotasutils.core.FarmingMath;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.CardItem;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.item.ItemSockets;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Salvage (แยกชิ้นส่วน): turning gear nobody wants into what the refine bench needs.
 *
 * <p>Farming leaves a bag full of weapons and armour that are slightly worse than what the player
 * wears. Salvage gives each one a price in gold and refine ore, taken from what the item actually is -
 * its attack or armour, its enchantments, its refinement - and hands back any card set in it. Only the
 * main inventory is offered, never a worn piece, and the screen asks twice before anything is broken.</p>
 */
public final class SalvageService {
    private SalvageService() {
    }

    /** What one item would give. {@code oreMin..oreMax} because the fraction of the last ore is rolled. */
    public record Yield(boolean possible, long gold, String ore, int oreMin, int oreMax, List<String> cards, double value) {
        static final Yield NONE = new Yield(false, 0, "", 0, 0, List.of(), 0);
    }

    private static SeasonRules.SalvageRules rules(RotasData data) {
        return SeasonService.rules(data).salvage;
    }

    /** What an item would give, without breaking it. */
    public static Yield preview(RotasData data, ItemStack stack) {
        SeasonRules.SalvageRules salvage = rules(data);
        ItemRefine.Category category = ItemRefine.categoryOf(stack);
        if (salvage == null || !salvage.enabled || !category.refinable()) {
            return Yield.NONE;
        }
        int refine = ItemRefine.level(stack);
        List<String> cards = salvage.returnCards ? ItemSockets.cards(stack) : List.of();
        int enchantLevels = EnchantmentHelper.getEnchantments(stack).values().stream().mapToInt(Integer::intValue).sum();
        double value = FarmingMath.salvageValue(strength(stack, category), enchantLevels, refine, cards.size());
        if (value <= 0) {
            return Yield.NONE;
        }
        double ores = value * salvage.orePerValue;
        int refund = FarmingMath.refineRefund(refine, salvage.refineRefund);
        int oreMin = (int) Math.floor(ores) + refund;
        int oreMax = (int) Math.ceil(ores) + refund;
        String ore = category == ItemRefine.Category.ARMOR
                ? SeasonService.rules(data).refine.armorOre : SeasonService.rules(data).refine.weaponOre;
        return new Yield(true, Math.round(value * salvage.goldPerValue), ore, oreMin, oreMax, cards, value);
    }

    /**
     * How strong the item is at what it does: attack damage for a weapon, armour plus toughness for
     * armour, read from the item's own modifiers so a modded item is priced the same way.
     */
    private static double strength(ItemStack stack, ItemRefine.Category category) {
        EquipmentSlot slot = category == ItemRefine.Category.ARMOR ? LivingEntity.getEquipmentSlotForItem(stack)
                : EquipmentSlot.MAINHAND;
        var modifiers = stack.getAttributeModifiers(slot);
        double total = 0;
        if (category == ItemRefine.Category.ARMOR) {
            total += sum(modifiers.get(Attributes.ARMOR)) + sum(modifiers.get(Attributes.ARMOR_TOUGHNESS));
        } else {
            total += sum(modifiers.get(Attributes.ATTACK_DAMAGE));
            if (total <= 0) {
                // A bow has no attack attribute; it is still worth taking apart.
                total = 3;
            }
        }
        return total;
    }

    private static double sum(java.util.Collection<AttributeModifier> modifiers) {
        double total = 0;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                total += modifier.getAmount();
            }
        }
        return total;
    }

    /** Breaks the item in one main-inventory slot. The server re-reads the slot; the screen only names it. */
    public static boolean salvage(ServerPlayer player, RotasData data, int slot) {
        var items = player.getInventory().items;
        if (slot < 0 || slot >= items.size()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.salvage.nothing"));
            return false;
        }
        ItemStack stack = items.get(slot);
        Yield yield = preview(data, stack);
        if (!yield.possible()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.salvage.cannot"));
            return false;
        }
        String name = stack.getHoverName().getString();
        Random random = new Random(player.getRandom().nextLong());
        double ores = yield.value() * rules(data).orePerValue;
        int ore = (int) Math.floor(ores) + (random.nextDouble() < ores - Math.floor(ores) ? 1 : 0)
                + (yield.oreMin() - (int) Math.floor(ores));
        items.set(slot, ItemStack.EMPTY);

        List<ItemStack> result = new ArrayList<>();
        var oreItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                new net.minecraft.resources.ResourceLocation(yield.ore()));
        if (ore > 0 && oreItem != net.minecraft.world.item.Items.AIR) {
            result.add(new ItemStack(oreItem, Math.min(64, ore)));
        }
        for (String card : yield.cards()) {
            result.add(CardItem.of(RotasRegistry.CARD.get(), card, 1));
        }
        if (yield.gold() > 0) {
            data.progress(player.getUUID()).rpg().currency(SeasonService.rules(data).currency, yield.gold());
        }
        if (!result.isEmpty()) {
            LootService.deliver(player, result);
        }
        data.progress(player.getUUID()).markDirty();
        data.setDirty();
        data.audit(player.getGameProfile().getName() + " salvaged " + name + " gold=" + yield.gold() + " ore=" + ore);
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.salvage.done", name, yield.gold(), ore));
        RotasNetwork.syncProgress(player);
        return true;
    }
}
