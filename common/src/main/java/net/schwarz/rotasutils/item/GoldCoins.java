package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.Locale;

/**
 * Gold coin stacks that hold any amount.
 *
 * <p>Vanilla slots and packets stop a stack at 64, so a coin stack keeps its real amount on the item
 * ({@value #AMOUNT}) and stays at a count of 1. Old untagged coins still count one per item. Coins that
 * came out of a kill are also marked {@value #LOOT}: picking those up puts them straight in the wallet.</p>
 */
public final class GoldCoins {
    public static final String AMOUNT = "RotasCoins";
    public static final String LOOT = "RotasCoinLoot";
    public static final String OWNER = "RotasCoinOwner";
    /** Most gold one stack can hold. */
    public static final long MAX = 1_000_000_000L;

    private GoldCoins() {
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.is(RotasRegistry.GOLD_COIN.get());
    }

    /** Gold this stack is worth. */
    public static long amount(ItemStack stack) {
        if (!is(stack)) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        long each = tag != null && tag.contains(AMOUNT) ? Math.max(1, tag.getLong(AMOUNT)) : 1;
        return Math.min(MAX, each * stack.getCount());
    }

    /** One stack worth {@code amount} gold. */
    public static ItemStack stack(long amount) {
        ItemStack stack = new ItemStack(RotasRegistry.GOLD_COIN.get());
        setAmount(stack, amount);
        return stack;
    }

    /** A kill's coins: same as {@link #stack} but they go to the wallet when picked up. */
    public static ItemStack loot(long amount, java.util.UUID owner) {
        ItemStack stack = stack(amount);
        stack.getOrCreateTag().putBoolean(LOOT, true);
        if (owner != null) {
            stack.getOrCreateTag().putUUID(OWNER, owner);
        }
        return stack;
    }

    /** True when {@code player} may pick these coins up: not kill coins, or that player's own. */
    public static boolean mayTake(ItemStack stack, java.util.UUID player) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.hasUUID(OWNER) || tag.getUUID(OWNER).equals(player);
    }

    public static boolean isLoot(ItemStack stack) {
        return is(stack) && stack.getTag() != null && stack.getTag().getBoolean(LOOT);
    }

    public static void setAmount(ItemStack stack, long amount) {
        long clamped = Math.max(1, Math.min(MAX, amount));
        stack.setCount(1);
        if (clamped == 1) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(AMOUNT);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
        } else {
            stack.getOrCreateTag().putLong(AMOUNT, clamped);
        }
    }

    /**
     * Moves {@code from} into {@code into}. Returns true when anything moved; whatever does not fit under
     * {@link #MAX} stays in {@code from}.
     */
    public static boolean merge(ItemStack into, ItemStack from) {
        if (!is(into) || !is(from) || into == from) {
            return false;
        }
        long have = amount(into);
        long room = MAX - have;
        if (room <= 0) {
            return false;
        }
        long moving = Math.min(room, amount(from));
        setAmount(into, have + moving);
        long left = amount(from) - moving;
        if (left <= 0) {
            from.setCount(0);
        } else {
            setAmount(from, left);
        }
        return true;
    }

    /** "1,250", or "1.2k" / "3.4M" when {@code compact}. */
    public static String format(long amount, boolean compact) {
        if (!compact || amount < 1_000) {
            return String.format(Locale.ROOT, "%,d", amount);
        }
        if (amount < 1_000_000) {
            return trim(amount / 1000.0) + "k";
        }
        if (amount < 1_000_000_000) {
            return trim(amount / 1_000_000.0) + "M";
        }
        return trim(amount / 1_000_000_000.0) + "B";
    }

    private static String trim(double value) {
        String text = value >= 100 ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
