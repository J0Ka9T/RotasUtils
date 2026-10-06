package net.schwarz.rotasutils.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.schwarz.rotasutils.npc.NpcDef;

public final class NpcMerchant implements Merchant {
    private MerchantOffers offers;
    private Player tradingPlayer;

    private NpcMerchant(MerchantOffers offers) {
        this.offers = offers;
    }

    public static boolean open(ServerPlayer player, NpcDef npc) {
        MerchantOffers offers = new MerchantOffers();
        for (NpcDef.Trade trade : npc.trades()) {
            if (trade.valid()) {
                offers.add(new MerchantOffer(trade.costA().copy(), trade.costB().copy(), trade.result().copy(),
                        0, Integer.MAX_VALUE, 0, 0.0f));
            }
        }
        if (offers.isEmpty()) {
            return false;
        }
        NpcMerchant merchant = new NpcMerchant(offers);
        merchant.setTradingPlayer(player);
        merchant.openTradingScreen(player, Component.literal(npc.name()), 0);
        return true;
    }

    @Override public void setTradingPlayer(Player player) { tradingPlayer = player; }
    @Override public Player getTradingPlayer() { return tradingPlayer; }
    @Override public MerchantOffers getOffers() { return offers; }
    @Override public void overrideOffers(MerchantOffers offers) { this.offers = offers; }
    @Override public void notifyTrade(MerchantOffer offer) { }
    @Override public void notifyTradeUpdated(ItemStack stack) { }
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) { }
    @Override public boolean showProgressBar() { return false; }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.VILLAGER_YES; }
    @Override public boolean isClientSide() { return false; }
}
