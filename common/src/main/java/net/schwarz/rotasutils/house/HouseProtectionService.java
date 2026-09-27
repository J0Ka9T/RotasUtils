package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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
        // Admins bypass housing protection: an AVAILABLE house has no owner and no members, so
        // without this nobody at all - including operators - could build or fix anything inside it.
        if (net.schwarz.rotasutils.server.BoardService.isAdmin(player, housing)) { return true; }
        HouseTenancy tenancy = housing.houseTenancy(house.id());
        return player.getUUID().equals(tenancy.owner()) || tenancy.members().contains(player.getUUID());
    }

    /**
     * Right-clicking a block: owners and members may use anything, visitors only what the house's
     * settings open to guests (doors, buttons and levers, containers).
     */
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
