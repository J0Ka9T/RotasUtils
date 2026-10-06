package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.data.RotasData;

public final class HouseProtectionService {
    private HouseProtectionService() {}
    public static boolean canModify(ServerPlayer player, BlockPos pos) {
        RotasData housing = RotasData.get(player.server);
        HouseDefinition house = HouseRegistry.at(housing.houses().values(), player.level().dimension().location().toString(), pos);
        if (house == null) return true;
        if (net.schwarz.rotasutils.server.BoardService.isAdmin(player, housing)) { return true; }
        HouseTenancy tenancy = housing.houseTenancy(house.id());
        return player.getUUID().equals(tenancy.owner()) || tenancy.members().contains(player.getUUID());
    }

    public static boolean canPlaceAgainst(ServerPlayer player, BlockPos clicked, Direction face, ItemStack held) {
        if (face == null || !(held.getItem() instanceof net.minecraft.world.item.FlintAndSteelItem
                || held.getItem() instanceof net.minecraft.world.item.FireChargeItem
                || held.getItem() instanceof net.minecraft.world.item.SpawnEggItem
                || held.getItem() instanceof net.minecraft.world.item.ArmorStandItem
                || held.getItem() instanceof net.minecraft.world.item.HangingEntityItem
                || held.getItem() instanceof net.minecraft.world.item.BoatItem
                || held.getItem() instanceof net.minecraft.world.item.MinecartItem)) {
            return true;
        }
        return canModify(player, clicked.relative(face));
    }

    public static boolean canInteract(ServerPlayer player, BlockPos pos) {
        if (canModify(player, pos)) return true;
        RotasData housing = RotasData.get(player.server);
        HouseDefinition house = HouseRegistry.at(housing.houses().values(), player.level().dimension().location().toString(), pos);
        if (house == null) return true;
        HouseSettings settings = housing.houseSettings(house.id());
        BlockState state = player.level().getBlockState(pos);
        if (settings.guestDoors() && (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock)) return true;
        if (settings.guestButtons() && (state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock)) return true;
        return settings.guestContainers() && player.level().getBlockEntity(pos) instanceof Container;
    }
}
