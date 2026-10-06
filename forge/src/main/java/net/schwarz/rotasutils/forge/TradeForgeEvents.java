package net.schwarz.rotasutils.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.schwarz.rotasutils.server.TradeService;

import java.util.List;

public final class TradeForgeEvents {
    private TradeForgeEvents() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(TradeForgeEvents.class);
    }

    @SubscribeEvent
    public static void loot(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        List<ItemStack> stacks = event.getDrops().stream().map(ItemEntity::getItem).toList();
        List<ItemStack> starred = TradeService.starLoot(player, stacks);
        if (starred == null) {
            return;
        }
        var victim = event.getEntity();
        event.getDrops().clear();
        for (ItemStack stack : starred) {
            event.getDrops().add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), stack));
        }
    }

    @SubscribeEvent
    public static void eaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeService.onEat(player, event.getItem());
            net.schwarz.rotasutils.server.PerkService.masterBrew(player, event.getItem());
        }
    }
}
