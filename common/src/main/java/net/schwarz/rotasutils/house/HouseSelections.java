package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.item.HouseWandItem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HouseSelections {
    private static final Map<UUID, HouseAdminService.Selection> PENDING = new ConcurrentHashMap<>();

    private HouseSelections() {
    }

    public static HouseAdminService.Selection pending(UUID player) {
        return player == null ? null : PENDING.get(player);
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    public static void clearAll() {
        PENDING.clear();
    }

    public static void set(ServerPlayer player, int[] box) {
        String dimension = player.level().dimension().location().toString();
        BlockPos first = new BlockPos(box[0], box[1], box[2]), second = new BlockPos(box[3], box[4], box[5]);
        PENDING.put(player.getUUID(), new HouseAdminService.Selection(dimension, first, second));
        ItemStack wand = held(player);
        if (!wand.isEmpty()) {
            HouseWandItem.clear(wand);
            HouseWandItem.select(wand, "first", dimension, first);
            HouseWandItem.select(wand, "second", dimension, second);
        }
    }

    public static int[] current(ServerPlayer player) {
        ItemStack wand = held(player);
        BlockPos a, b;
        String dimension = player.level().dimension().location().toString();
        if (!wand.isEmpty()) {
            a = HouseWandItem.first(wand);
            b = HouseWandItem.second(wand);
            if (!dimension.equals(HouseWandItem.dimension(wand))) {
                return null;
            }
        } else {
            HouseAdminService.Selection s = PENDING.get(player.getUUID());
            if (s == null || !dimension.equals(s.dimension())) {
                return null;
            }
            a = s.first();
            b = s.second();
        }
        return a == null || b == null ? null : HouseArea.between(a, b);
    }

    public static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        ItemStack wand = held(player);
        if (!wand.isEmpty()) {
            HouseWandItem.clear(wand);
        }
    }

    public static void setCorner(ServerPlayer player, boolean first, BlockPos pos) {
        String dimension = player.level().dimension().location().toString();
        HouseAdminService.Selection s = PENDING.get(player.getUUID());
        ItemStack wand = held(player);
        BlockPos a = null, b = null;
        if (!wand.isEmpty() && dimension.equals(HouseWandItem.dimension(wand))) {
            a = HouseWandItem.first(wand);
            b = HouseWandItem.second(wand);
        } else if (wand.isEmpty() && s != null && dimension.equals(s.dimension())) {
            a = s.first();
            b = s.second();
        }
        if (first) {
            a = pos;
        } else {
            b = pos;
        }
        PENDING.put(player.getUUID(), new HouseAdminService.Selection(dimension, a, b));
        if (!wand.isEmpty()) {
            HouseWandItem.select(wand, first ? "first" : "second", dimension, pos);
        }
    }

    private static ItemStack held(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof HouseWandItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof HouseWandItem ? off : ItemStack.EMPTY;
    }
}
