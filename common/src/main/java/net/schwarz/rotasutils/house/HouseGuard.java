package net.schwarz.rotasutils.house;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.ExplosionEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.data.RotasData;

public final class HouseGuard {
    private HouseGuard() {
    }

    public static HouseDefinition houseAt(Level level, BlockPos pos) {
        RotasData data = RotasData.instance();
        if (data == null || level == null || level.isClientSide() || data.houses().isEmpty()) {
            return null;
        }
        return HouseRegistry.at(data.houses().values(), level.dimension().location().toString(), pos);
    }

    public static boolean protectsEntity(Entity entity) {
        return entity instanceof HangingEntity || entity instanceof ArmorStand || entity instanceof Boat || entity instanceof AbstractMinecart;
    }

    public static boolean mayHandle(ServerPlayer player, Entity entity) {
        if (!protectsEntity(entity)) {
            return true;
        }
        return HouseProtectionService.canModify(player, entity.blockPosition());
    }

    public static boolean crossesWall(Level level, BlockPos from, BlockPos to) {
        HouseDefinition a = houseAt(level, from), b = houseAt(level, to);
        return a != b && (a == null || b == null || !a.id().equals(b.id()));
    }

    public static void register() {
        ExplosionEvent.DETONATE.register((level, explosion, affected) -> {
            if (level.isClientSide()) {
                return;
            }
            explosion.getToBlow().removeIf(pos -> houseAt(level, pos) != null);
            affected.removeIf(entity -> protectsEntity(entity) && houseAt(level, entity.blockPosition()) != null);
        });
        PlayerEvent.ATTACK_ENTITY.register((player, level, entity, hand, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer server)
                    || mayHandle(server, entity) && !(entity instanceof net.minecraft.world.entity.animal.Animal
                            && !HouseProtectionService.canModify(server, entity.blockPosition()))) {
                return EventResult.pass();
            }
            server.displayClientMessage(Component.translatable("rotasutils.msg.house.protected"), true);
            return EventResult.interruptFalse();
        });
    }
}
