package net.schwarz.rotasutils.compat.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.compat.CuriosServerCompat;

import java.util.List;
import java.util.Map;

public final class CuriosServerCompatImpl {
    private CuriosServerCompatImpl() {
    }

    public static boolean available() {
        return false;
    }

    public static void click(ServerPlayer player, String slotType, int slotIndex, int button) {
    }

    public static boolean unequip(ServerPlayer player, String slotType, int slotIndex) {
        return false;
    }

    public static Map<String, List<ItemStack>> equipped(ServerPlayer player) {
        return Map.of();
    }
}

