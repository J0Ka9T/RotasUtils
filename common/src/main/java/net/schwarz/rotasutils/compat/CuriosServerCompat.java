package net.schwarz.rotasutils.compat;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public final class CuriosServerCompat {
    private CuriosServerCompat() {
    }

    @ExpectPlatform public static boolean available() { throw new AssertionError(); }

    @ExpectPlatform public static void click(ServerPlayer player, String slotType, int slotIndex, int button) { throw new AssertionError(); }

    @ExpectPlatform public static boolean unequip(ServerPlayer player, String slotType, int slotIndex) { throw new AssertionError(); }

    @ExpectPlatform public static Map<String, List<ItemStack>> equipped(ServerPlayer player) { throw new AssertionError(); }

    public static boolean allowsUnequip(boolean bindingCurse, boolean curioAllowsUnequip, boolean eventDenied) {
        return !bindingCurse && curioAllowsUnequip && !eventDenied;
    }
}
