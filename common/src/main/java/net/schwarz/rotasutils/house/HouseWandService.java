package net.schwarz.rotasutils.house;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.HouseWandItem;
import net.schwarz.rotasutils.server.BoardService;

public final class HouseWandService {
    private HouseWandService() {}

    public static boolean selectFirst(ServerPlayer player, ItemStack wand, BlockPos pos) {
        if (!authorized(player)) return false;
        HouseWandItem.clear(wand);
        HouseWandItem.select(wand, "first", dimension(player), pos);
        player.sendSystemMessage(Component.translatable("rotasutils.msg.house.first", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static void selectSecond(ServerPlayer player, ItemStack wand, BlockPos pos) {
        if (!authorized(player)) return;
        String dimension = dimension(player);
        if (!HouseWandItem.dimension(wand).isEmpty() && !dimension.equals(HouseWandItem.dimension(wand))) {
            HouseWandItem.clear(wand);
            player.sendSystemMessage(Component.translatable("rotasutils.msg.house.other_dimension").withStyle(ChatFormatting.RED));
            return;
        }
        HouseWandItem.select(wand, "second", dimension, pos);
        player.sendSystemMessage(Component.translatable("rotasutils.msg.house.second", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        BlockPos first = HouseWandItem.first(wand);
        if (first != null) {
            describe(player, dimension, first, pos);
        }
    }

    public static void clear(ServerPlayer player, ItemStack wand) {
        if (!authorized(player)) return;
        HouseWandItem.clear(wand);
        player.displayClientMessage(Component.translatable("rotasutils.msg.house.cleared").withStyle(ChatFormatting.GRAY), true);
    }

    public static boolean loadHouse(ServerPlayer player, ItemStack wand) {
        if (!authorized(player)) return false;
        RotasData data = RotasData.get(player.server);
        HouseDefinition house = HouseRegistry.at(data.houses().values(), dimension(player), player.blockPosition());
        if (house == null) return false;
        HouseBounds b = house.bounds();
        HouseWandItem.clear(wand);
        HouseWandItem.select(wand, "first", b.dimension(), new BlockPos(b.minX(), b.minY(), b.minZ()));
        HouseWandItem.select(wand, "second", b.dimension(), new BlockPos(b.maxX(), b.maxY(), b.maxZ()));
        player.sendSystemMessage(Component.translatable("rotasutils.msg.house.loaded", house.name()).withStyle(ChatFormatting.AQUA));
        net.schwarz.rotasutils.network.RotasNetwork.syncContent(player);
        return true;
    }

    public static void pushFace(ServerPlayer player, ItemStack wand, BlockPos pos) {
        if (!authorized(player)) return;
        BlockPos first = HouseWandItem.first(wand);
        BlockPos second = HouseWandItem.second(wand);
        if (first == null || second == null || !dimension(player).equals(HouseWandItem.dimension(wand))) return;
        int[] box = pushed(new int[]{Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())}, pos);
        HouseWandItem.select(wand, "first", dimension(player), new BlockPos(box[0], box[1], box[2]));
        HouseWandItem.select(wand, "second", dimension(player), new BlockPos(box[3], box[4], box[5]));
        describe(player, dimension(player), new BlockPos(box[0], box[1], box[2]), new BlockPos(box[3], box[4], box[5]));
        net.schwarz.rotasutils.network.RotasNetwork.syncContent(player);
    }

    static int[] pushed(int[] box, BlockPos pos) {
        int[] p = {pos.getX(), pos.getY(), pos.getZ()};
        int[] out = box.clone();
        boolean outside = false;
        for (int axis = 0; axis < 3; axis++) {
            if (p[axis] < out[axis]) { out[axis] = p[axis]; outside = true; }
            if (p[axis] > out[axis + 3]) { out[axis + 3] = p[axis]; outside = true; }
        }
        if (outside) return out;
        int bestFace = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int face = 0; face < 6; face++) {
            int axis = face % 3;
            int distance = Math.abs(p[axis] - out[face]);
            if (distance < bestDistance) { bestDistance = distance; bestFace = face; }
        }
        out[bestFace] = p[bestFace % 3];
        return out;
    }

    public static boolean openHouseHere(ServerPlayer player) {
        if (!authorized(player)) return false;
        RotasData data = RotasData.get(player.server);
        HouseDefinition house = HouseRegistry.at(data.houses().values(), dimension(player), player.blockPosition());
        if (house == null) return false;
        net.schwarz.rotasutils.network.RotasNetwork.openHouse(player, house.id());
        return true;
    }

    public static void openCreate(ServerPlayer player) {
        if (!authorized(player)) return;
        net.schwarz.rotasutils.network.RotasNetwork.syncContent(player);
        net.schwarz.rotasutils.network.RotasNetwork.openScreen(player, "house_create", new net.minecraft.nbt.CompoundTag());
    }

    public static void report(ServerPlayer player, String dimension, int[] box) {
        describe(player, dimension, new BlockPos(box[0], box[1], box[2]), new BlockPos(box[3], box[4], box[5]));
    }

    private static void describe(ServerPlayer player, String dimension, BlockPos a, BlockPos b) {
        long x = Math.abs((long) a.getX() - b.getX()) + 1;
        long y = Math.abs((long) a.getY() - b.getY()) + 1;
        long z = Math.abs((long) a.getZ() - b.getZ()) + 1;
        long volume = x * y * z;
        if (volume > HouseBounds.MAX_VOLUME) {
            player.sendSystemMessage(Component.translatable("rotasutils.msg.house.too_big", HouseBounds.MAX_VOLUME).withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable("rotasutils.msg.house.selection", x, y, z, volume).withStyle(ChatFormatting.GREEN));
        HouseBounds bounds = HouseBounds.between(dimension, a, b);
        for (HouseDefinition house : RotasData.get(player.server).houses().values()) {
            if (house.enabled() && house.bounds().overlaps(bounds)) {
                player.sendSystemMessage(Component.translatable("rotasutils.msg.house.overlap", house.name()).withStyle(ChatFormatting.YELLOW));
                return;
            }
        }
    }

    private static boolean authorized(ServerPlayer player) { return BoardService.isAdmin(player, RotasData.get(player.server)); }
    private static String dimension(ServerPlayer player) { return player.level().dimension().location().toString(); }
}
