package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.core.CardEffects;
import net.schwarz.rotasutils.core.CardIndex;
import net.schwarz.rotasutils.core.RefineMath;
import net.schwarz.rotasutils.item.CardItem;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.item.ItemSockets;
import net.schwarz.rotasutils.item.WeaponMemory;
import net.schwarz.rotasutils.core.WeaponMemoryMath;

import java.util.List;
import java.util.Locale;

/**
 * What an item's RotasUtils marks are worth, shown wherever it is hovered.
 *
 * <p>The name already says "+7" and the card's own name says which card it is; this says what either of
 * them actually does. Every number comes from the server with the content snapshot, so a server that
 * retuned refinement or rewrote a card reads correctly here instead of showing the shipped defaults.</p>
 */
@Environment(EnvType.CLIENT)
public final class RefineTooltip {
    private RefineTooltip() {
    }

    public static void register() {
        dev.architectury.event.events.client.ClientTooltipEvent.ITEM.register((stack, lines, flag) -> append(stack, lines));
    }

    static void append(ItemStack stack, List<Component> lines) {
        appendCards(stack, lines);
        appendRefine(stack, lines);
        appendRunes(stack, lines);
        appendMemory(stack, lines);
    }

    /** Each inscribed rune in slot order, in its own colour, with what it does. */
    private static void appendRunes(ItemStack stack, List<Component> lines) {
        List<net.schwarz.rotasutils.core.RuneType> runes = net.schwarz.rotasutils.item.ItemRunes.read(stack);
        for (int i = 0; i < runes.size(); i++) {
            net.schwarz.rotasutils.core.RuneType rune = runes.get(i);
            if (rune == null) {
                continue;
            }
            int tier = net.schwarz.rotasutils.item.ItemRunes.tier(stack, i);
            String name = L.t("item.rotasutils." + rune.itemPath())
                    + (tier > 1 ? " " + net.schwarz.rotasutils.item.ItemRunes.numeral(tier) : "");
            lines.add(Component.literal(L.t("rotasutils.rune.tooltip", name,
                    L.t("rotasutils.rune.effect." + rune.id()))).withStyle(rune.color()));
        }
    }

    /** What the weapon remembers: its rank, what it killed, and what that is worth now. */
    private static void appendMemory(ItemStack stack, List<Component> lines) {
        CompoundTag memory = WeaponMemory.read(stack);
        long kills = memory.getLong(WeaponMemoryMath.KILLS);
        CompoundTag rules = ClientState.weaponMemoryRules();
        if (kills <= 0 || rules.isEmpty() || !rules.getBoolean("enabled")) {
            return;
        }
        long[] milestones = rules.getLongArray("milestones");
        int rank = WeaponMemoryMath.rank(kills, milestones);
        String title = rank <= 0 ? L.t("rotasutils.memory.rank.0") : L.t("rotasutils.memory.rank." + Math.min(5, rank));
        lines.add(Component.literal(L.t("rotasutils.memory.tooltip", title, "★".repeat(Math.max(0, rank))))
                .withStyle(rank >= 4 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        lines.add(Component.literal(L.t("rotasutils.memory.tooltip.kills", kills,
                memory.getInt(WeaponMemoryMath.BOSSES), memory.getInt(WeaponMemoryMath.NEMESES))).withStyle(ChatFormatting.GRAY));
        long next = WeaponMemoryMath.toNext(kills, milestones);
        if (next > 0) {
            lines.add(Component.literal(L.t("rotasutils.memory.tooltip.next", next)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (rank > 0) {
            long percent = Math.round(rank * rules.getDouble("damage_per_rank") * 100);
            if (percent > 0) {
                lines.add(Component.literal(L.t("rotasutils.memory.tooltip.damage", percent)).withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        String favored = WeaponMemoryMath.favored(memory);
        if (!favored.isEmpty()) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(favored);
            String name = id != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString() : favored;
            boolean active = rank >= Math.max(1, rules.getInt("favored_from_rank"));
            long bonus = Math.round(rules.getDouble("favored_bonus") * 100);
            lines.add(Component.literal(active && bonus > 0
                    ? L.t("rotasutils.memory.tooltip.favored_active", name, bonus)
                    : L.t("rotasutils.memory.tooltip.favored", name)).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    /** The refine level of a weapon or a piece of armour, and the bonus it carries. */
    private static void appendRefine(ItemStack stack, List<Component> lines) {
        int level = ItemRefine.level(stack);
        if (level <= 0) {
            return;
        }
        CompoundTag rules = ClientState.refineRules();
        ItemRefine.Category category = ItemRefine.categoryOf(stack);
        lines.add(Component.literal(L.t("rotasutils.refine.tooltip", level)).withStyle(ItemRefine.color(level)));
        if (rules.isEmpty() || !rules.getBoolean("enabled") || !category.refinable()) {
            return;
        }
        boolean armour = category == ItemRefine.Category.ARMOR;
        double bonus = RefineMath.bonusValue(level, rules.getInt("safe_level"),
                rules.getDouble(armour ? "defense_per_level" : "attack_per_level"),
                rules.getDouble(armour ? "defense_per_over_level" : "attack_per_over_level"));
        if (bonus <= 0) {
            return;
        }
        String amount = String.format(Locale.ROOT, "%.1f", bonus).replaceAll("\\.0$", "");
        lines.add(Component.literal(L.t(armour ? "rotasutils.refine.tooltip.defense"
                : "rotasutils.refine.tooltip.attack", amount)).withStyle(ChatFormatting.DARK_GREEN));
    }

    /** Sockets and the cards in them - or, on a card itself, what that card is worth and what it fits. */
    private static void appendCards(ItemStack stack, List<Component> lines) {
        String cardId = CardItem.idOf(stack);
        if (!cardId.isBlank()) {
            CardIndex.Entry card = CardIndex.get(cardId);
            if (card == null) {
                return;
            }
            for (var effect : card.effects()) {
                lines.add(Component.literal(CardEffects.describe(effect, L::t)).withStyle(ChatFormatting.DARK_GREEN));
            }
            lines.add(Component.literal(L.t("rotasutils.card.fits." + card.fits().toLowerCase(Locale.ROOT)))
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int sockets = ItemSockets.count(stack);
        if (sockets <= 0) {
            return;
        }
        List<String> cards = ItemSockets.cards(stack);
        lines.add(Component.literal(L.t("rotasutils.card.sockets", cards.size(), sockets))
                .withStyle(ChatFormatting.AQUA));
        for (String id : cards) {
            CardIndex.Entry card = CardIndex.get(id);
            lines.add(Component.literal(" - " + (card == null ? id : card.name()))
                    .withStyle(style -> style.withColor(CardIndex.color(id) & 0xFFFFFF)));
            if (card != null) {
                for (var effect : card.effects()) {
                    lines.add(Component.literal("   " + CardEffects.describe(effect, L::t))
                            .withStyle(ChatFormatting.DARK_GREEN));
                }
            }
        }
    }
}
