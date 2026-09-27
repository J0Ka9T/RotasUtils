package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.CardEffects;
import net.schwarz.rotasutils.core.CardIndex;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.CardItem;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.item.ItemSockets;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cards (การ์ด): where they come from, what they fit and what they are worth.
 *
 * <p>A card is the thinnest drop in the game - a few in ten thousand kills - and the one that changes a
 * build. Once it is in a socket its bonus is an ordinary attribute bonus applied with the rest of the
 * player's equipment, so nothing about a card needs its own runtime state.</p>
 */
public final class CardService {
    private CardService() {
    }

    /** Rebuilds the shared display table from the configuration. Called whenever season.json loads. */
    public static void refreshIndex(RotasData data) {
        SeasonRules.CardRules rules = SeasonService.rules(data).cards;
        Map<String, CardIndex.Entry> entries = new LinkedHashMap<>();
        if (rules != null && rules.entries != null) {
            rules.entries.forEach((id, card) -> entries.put(id, new CardIndex.Entry(id, card.name, card.color,
                    card.fits, CardEffects.parseAll(card.effects))));
        }
        CardIndex.set(entries);
    }

    /**
     * One kill's chance at a card. Only a card whose source names this entity - or a card with no source
     * at all - can drop, so a server decides exactly which monster carries which card.
     */
    public static void onKill(ServerPlayer killer, RotasData data, LivingEntity entity, String entityId) {
        SeasonRules.CardRules rules = SeasonService.rules(data).cards;
        if (rules == null || !rules.enabled || rules.entries == null || rules.entries.isEmpty()) {
            return;
        }
        for (Map.Entry<String, SeasonRules.CardDef> entry : rules.entries.entrySet()) {
            SeasonRules.CardDef card = entry.getValue();
            if (card == null) {
                continue;
            }
            if (!card.source.isBlank() && !card.source.equalsIgnoreCase(entityId)) {
                continue;
            }
            double chance = net.schwarz.rotasutils.core.FarmingMath.boosted(
                    card.chance >= 0 ? card.chance : rules.dropChance,
                    FarmingService.cardLuck(killer, data),
                    BestiaryService.loot(data, data.progress(killer.getUUID()), entityId));
            if (chance <= 0 || killer.getRandom().nextDouble() >= chance) {
                continue;
            }
            ItemStack stack = CardItem.of(RotasRegistry.CARD.get(), entry.getKey(), 1);
            LootService.deliver(killer, List.of(stack));
            killer.server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.card.dropped",
                    killer.getGameProfile().getName(), CardIndex.name(entry.getKey())), false);
            data.audit(killer.getGameProfile().getName() + " found card " + entry.getKey());
            EventService.fire(killer, data, net.schwarz.rotasutils.event.EventType.CARD_DROP, entry.getKey());
            RotasNetwork.syncProgress(killer);
            // One kill leaves at most one card; a lucky roll is not a jackpot of every card at once.
            return;
        }
    }

    /** The bonuses the cards in one item contribute. */
    public static List<CharacterStat.Effect> effects(ItemStack stack) {
        List<CharacterStat.Effect> effects = new ArrayList<>();
        for (String id : ItemSockets.cards(stack)) {
            CardIndex.Entry card = CardIndex.get(id);
            if (card != null) {
                effects.addAll(card.effects());
            }
        }
        return effects;
    }

    /** What went wrong when a card cannot go into an item, or an empty string when it can. */
    public static String cannotInsert(RotasData data, ItemStack target, String cardId) {
        SeasonRules.CardRules rules = SeasonService.rules(data).cards;
        if (rules == null || !rules.enabled) {
            return ThaiText.t("rotasutils.msg.card.disabled");
        }
        ItemRefine.Category category = ItemRefine.categoryOf(target);
        if (!category.refinable()) {
            return ThaiText.t("rotasutils.msg.card.bad_target");
        }
        CardIndex.Entry card = CardIndex.get(cardId);
        if (card == null) {
            return ThaiText.t("rotasutils.msg.card.unknown");
        }
        if (!card.fits(category.name())) {
            return ThaiText.t("rotasutils.msg.card.wrong_kind", card.name());
        }
        if (ItemSockets.free(target) <= 0) {
            return ThaiText.t("rotasutils.msg.card.no_socket");
        }
        return "";
    }

    /**
     * Puts the card the player is holding into one of their items. The card is consumed only once it is
     * actually in the socket, and a card in a socket stays there until it is prised out again.
     */
    public static boolean insert(ServerPlayer player, RotasData data, int inventorySlot) {
        ItemStack card = player.getMainHandItem();
        if (!CardItem.isCard(card)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.card.hold_one"));
            return false;
        }
        ItemStack target = target(player, inventorySlot);
        String cardId = CardItem.idOf(card);
        String refusal = cannotInsert(data, target, cardId);
        if (!refusal.isEmpty()) {
            RotasNetwork.feedback(player, false, refusal);
            return false;
        }
        if (!ItemSockets.insert(target, cardId)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.card.no_socket"));
            return false;
        }
        card.shrink(1);
        data.audit(player.getGameProfile().getName() + " socketed " + cardId);
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.card.inserted",
                CardIndex.name(cardId), target.getHoverName().getString()));
        return true;
    }

    /** Punches one socket into the chosen item, consuming a socket punch from the inventory. */
    public static boolean punch(ServerPlayer player, RotasData data, int inventorySlot) {
        SeasonRules.CardRules rules = SeasonService.rules(data).cards;
        ItemStack target = target(player, inventorySlot);
        if (rules == null || !rules.enabled) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.card.disabled"));
            return false;
        }
        if (!ItemRefine.categoryOf(target).refinable()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.card.bad_target"));
            return false;
        }
        int held = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(RotasRegistry.SOCKET_PUNCH.get())) {
                held += stack.getCount();
            }
        }
        if (held <= 0) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.card.need_punch"));
            return false;
        }
        int before = ItemSockets.count(target);
        int after = ItemSockets.punch(target, rules.maxSockets);
        if (after <= before) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.card.socket_full", rules.maxSockets));
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(RotasRegistry.SOCKET_PUNCH.get())) {
                stack.shrink(1);
                break;
            }
        }
        data.audit(player.getGameProfile().getName() + " punched a socket (" + after + ")");
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.card.socket_added", after));
        return true;
    }

    /** The item a screen picked: an inventory slot, or the off-hand when the slot is -1. */
    private static ItemStack target(ServerPlayer player, int inventorySlot) {
        if (inventorySlot < 0) {
            return player.getOffhandItem();
        }
        var items = player.getInventory().items;
        return inventorySlot < items.size() ? items.get(inventorySlot) : ItemStack.EMPTY;
    }
}
