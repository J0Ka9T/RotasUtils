package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The sockets (รู) on a weapon or a piece of armour, and the cards sitting in them.
 *
 * <p>Both live in a {@code RotasSockets} compound on the stack, next to the refine level, so an item
 * carries its whole history by itself. A socket is punched once and never disappears; a card put into
 * one can be taken out again, which is what makes a socketed item worth trading.</p>
 */
public final class ItemSockets {
    public static final String TAG = "RotasSockets";
    private static final String COUNT = "count";
    private static final String CARDS = "cards";

    private ItemSockets() {
    }

    /** How many sockets this item has. */
    public static int count(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return 0;
        }
        return Math.max(0, tag.getCompound(TAG).getInt(COUNT));
    }

    /** The cards in this item's sockets, in socket order. */
    public static List<String> cards(ItemStack stack) {
        List<String> cards = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return cards;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return cards;
        }
        ListTag list = tag.getCompound(TAG).getList(CARDS, Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            cards.add(list.getString(index));
        }
        return cards;
    }

    /** Free sockets left on this item. */
    public static int free(ItemStack stack) {
        return Math.max(0, count(stack) - cards(stack).size());
    }

    /**
     * Punches one more socket, up to {@code max}. Returns the new socket count, or 0 when the item takes
     * no sockets at all - it must be something the game treats as a weapon or as armour.
     */
    public static int punch(ItemStack stack, int max) {
        if (!ItemRefine.categoryOf(stack).refinable()) {
            return 0;
        }
        int current = count(stack);
        if (current >= Math.max(0, max)) {
            return current;
        }
        write(stack, current + 1, cards(stack));
        return current + 1;
    }

    /** Puts a card in the first free socket. False when there is no room. */
    public static boolean insert(ItemStack stack, String cardId) {
        if (cardId == null || cardId.isBlank() || free(stack) <= 0) {
            return false;
        }
        List<String> cards = cards(stack);
        cards.add(cardId);
        write(stack, count(stack), cards);
        return true;
    }

    /** Takes the card out of one socket and returns its id, or null when that socket was empty. */
    public static String remove(ItemStack stack, int socket) {
        List<String> cards = cards(stack);
        if (socket < 0 || socket >= cards.size()) {
            return null;
        }
        String removed = cards.remove(socket);
        write(stack, count(stack), cards);
        return removed;
    }

    private static void write(ItemStack stack, int sockets, List<String> cards) {
        CompoundTag tag = stack.getOrCreateTag();
        if (sockets <= 0 && cards.isEmpty()) {
            tag.remove(TAG);
            return;
        }
        CompoundTag sockets_ = new CompoundTag();
        sockets_.putInt("schema", 1);
        sockets_.putInt(COUNT, Math.max(sockets, cards.size()));
        ListTag list = new ListTag();
        for (String card : cards) {
            list.add(StringTag.valueOf(card));
        }
        sockets_.put(CARDS, list);
        tag.put(TAG, sockets_);
    }
}
