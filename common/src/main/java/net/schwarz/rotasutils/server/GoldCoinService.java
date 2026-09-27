package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.GoldCoins;
import net.schwarz.rotasutils.network.RotasNetwork;

/**
 * The gold wallet and the coin items that go in and out of it.
 *
 * <p>A kill drops its coins on the ground for the killer; picking them up puts them in the wallet.
 * Coins taken out of the wallet are one stack of any size, and coins picked up off the ground join the
 * stack already in the bag, so a player never has more than one coin slot.</p>
 */
public final class GoldCoinService {
    public static final String CURRENCY = "rotas:gold";
    /** Largest single withdrawal. */
    public static final int MAX_WITHDRAWAL = 1_000_000;
    /** Kill coins belong to the killer for this long, then anyone may take them. */
    private static final int OWNER_LOCK_TICKS = 60 * 20;

    private GoldCoinService() {}

    public static boolean validWithdrawal(int amount) {
        return amount >= 1 && amount <= MAX_WITHDRAWAL;
    }

    public static boolean canDeposit(long balance, long amount, long maximum) {
        return amount > 0 && balance <= maximum - amount;
    }

    public static boolean depositHeld(ServerPlayer player, ItemStack coins) {
        if (!GoldCoins.is(coins)) return false;
        long amount = GoldCoins.amount(coins);
        if (!deposit(player, amount)) {
            message(player, false, "rotasutils.wallet.deposit_full");
            return false;
        }
        coins.setCount(0);
        message(player, true, "rotasutils.wallet.deposited", GoldCoins.format(amount, false),
                GoldCoins.format(balance(player), false));
        return true;
    }

    /** Adds gold to the wallet; false when it would pass the wallet limit. */
    public static boolean deposit(ServerPlayer player, long amount) {
        RotasData data = RotasData.get(player.server);
        var progress = data.progress(player.getUUID());
        long balance = progress.rpg().currency(CURRENCY);
        if (!canDeposit(balance, amount, net.schwarz.rotasutils.progress.RpgProfile.MAX_BALANCE)) {
            return false;
        }
        progress.rpg().currency(CURRENCY, amount);
        progress.markDirty();
        data.setDirty();
        RotasNetwork.syncProgress(player);
        return true;
    }

    private static long balance(ServerPlayer player) {
        return RotasData.get(player.server).progress(player.getUUID()).rpg().currency(CURRENCY);
    }

    public static boolean withdraw(ServerPlayer player, int amount) {
        if (!validWithdrawal(amount)) return false;
        RotasData data = RotasData.get(player.server);
        var progress = data.progress(player.getUUID());
        long balance = progress.rpg().currency(CURRENCY);
        if (balance < amount) {
            message(player, false, "rotasutils.wallet.insufficient", GoldCoins.format(balance, false));
            return false;
        }
        ItemStack coins = GoldCoins.stack(amount);
        if (!addToBag(player, coins)) {
            message(player, false, "rotasutils.wallet.inventory_full");
            return false;
        }
        progress.rpg().currency(CURRENCY, -amount);
        progress.markDirty();
        data.setDirty();
        player.inventoryMenu.broadcastChanges();
        RotasNetwork.syncProgress(player);
        message(player, true, "rotasutils.wallet.withdrew", GoldCoins.format(amount, false),
                GoldCoins.format(balance - amount, false));
        return true;
    }

    /** Joins the coin stack already in the bag, or takes a free slot. False when neither works. */
    private static boolean addToBag(ServerPlayer player, ItemStack coins) {
        var items = player.getInventory().items;
        for (ItemStack existing : items) {
            if (GoldCoins.is(existing) && !GoldCoins.isLoot(existing)) {
                GoldCoins.merge(existing, coins);
                if (coins.isEmpty()) {
                    return true;
                }
            }
        }
        return player.getInventory().add(coins) && coins.isEmpty();
    }

    /**
     * Picking up coins. Kill coins go to the wallet; other loose coins join the bag's coin stack. Returns
     * true when the pickup was handled here and vanilla must not add the item as well.
     */
    public static boolean pickUp(ServerPlayer player, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!GoldCoins.is(stack) || entity.hasPickUpDelay()) {
            return false;
        }
        if (!GoldCoins.mayTake(stack, player.getUUID()) && entity.getAge() < OWNER_LOCK_TICKS) {
            // Someone else's kill: leave it on the ground, and do not let vanilla hand it over either.
            return true;
        }
        long amount = GoldCoins.amount(stack);
        if (GoldCoins.isLoot(stack)) {
            if (!deposit(player, amount)) {
                return false;
            }
            player.displayClientMessage(Component.translatable("rotasutils.coin.picked", GoldCoins.format(amount, false))
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            ItemStack copy = stack.copy();
            if (!addToBag(player, copy)) {
                return false;
            }
        }
        player.take(entity, 1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_STEP,
                SoundSource.PLAYERS, 0.5f, 1.6f + player.getRandom().nextFloat() * 0.3f);
        entity.discard();
        return true;
    }

    /** Drops a kill's coins at {@code (x, y, z)}; only {@code owner} can pick them up. */
    public static void dropLoot(ServerPlayer owner, net.minecraft.server.level.ServerLevel level,
                                double x, double y, double z, long amount) {
        if (amount <= 0) {
            return;
        }
        ItemEntity entity = new ItemEntity(level, x, y + 0.5, z, GoldCoins.loot(amount, owner.getUUID()));
        entity.setPickUpDelay(8);
        entity.setGlowingTag(true);
        level.addFreshEntity(entity);
    }

    private static void message(ServerPlayer player, boolean success, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args)
                .withStyle(success ? ChatFormatting.GOLD : ChatFormatting.RED), true);
    }
}
