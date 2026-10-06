package net.schwarz.rotasutils.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.schwarz.rotasutils.house.HouseGuard;
import net.schwarz.rotasutils.house.HouseProtectionService;

public final class HouseForgeEvents {
    private HouseForgeEvents() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(HouseForgeEvents.class);
    }

    @SubscribeEvent
    public static void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockPos piston = event.getPos();
        PistonStructureResolver helper = event.getStructureHelper();
        boolean extending = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND;
        BlockPos head = event.getFaceOffsetPos();
        if (HouseGuard.crossesWall(level, piston, head) && !isEmptyPush(helper, extending)) {
            event.setCanceled(true);
            return;
        }
        if (helper == null || !helper.resolve()) {
            return;
        }
        var direction = extending ? event.getDirection() : event.getDirection().getOpposite();
        for (BlockPos moved : helper.getToPush()) {
            if (HouseGuard.crossesWall(level, piston, moved) || HouseGuard.crossesWall(level, piston, moved.relative(direction))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos broken : helper.getToDestroy()) {
            if (HouseGuard.crossesWall(level, piston, broken)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static boolean isEmptyPush(PistonStructureResolver helper, boolean extending) {
        return helper == null || !helper.resolve() || (helper.getToPush().isEmpty() && helper.getToDestroy().isEmpty());
    }

    @SubscribeEvent
    public static void bucket(FillBucketEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (!HouseProtectionService.canModify(player, hit.getBlockPos()) || !HouseProtectionService.canModify(player, target)) {
            player.displayClientMessage(Component.translatable("rotasutils.msg.house.protected"), true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || HouseGuard.houseAt(level, event.getPos()) == null) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && HouseProtectionService.canModify(player, event.getPos())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void griefing(EntityMobGriefingEvent event) {
        var entity = event.getEntity();
        if (entity == null || entity.level().isClientSide() || HouseGuard.houseAt(entity.level(), entity.blockPosition()) == null) {
            return;
        }
        event.setResult(Event.Result.DENY);
    }
}
