package net.schwarz.rotasutils.compat;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Server-authoritative Curios slot bridge used by the Character Hub.
 *
 * <p>Curios exists on Forge only, so the implementation is platform specific. On Fabric every call
 * is a harmless no-op and the hub simply shows no accessories.</p>
 */
public final class CuriosServerCompat {
    private CuriosServerCompat() {
    }

    /** True when the platform can talk to Curios on this server. */
    @ExpectPlatform public static boolean available() { throw new AssertionError(); }

    /** Applies a normal left/right inventory click to a real Curios slot. */
    @ExpectPlatform public static void click(ServerPlayer player, String slotType, int slotIndex, int button) { throw new AssertionError(); }

    /** Removes one equipped Curios stack, honouring Curios' own unequip rules. */
    @ExpectPlatform public static boolean unequip(ServerPlayer player, String slotType, int slotIndex) { throw new AssertionError(); }

    /**
     * Equipped Curios stacks keyed by slot type. Empty when Curios is absent or unresolved, so
     * callers treat Curios as an optional extension of the vanilla slots.
     */
    @ExpectPlatform public static Map<String, List<ItemStack>> equipped(ServerPlayer player) { throw new AssertionError(); }

    /**
     * The gate every platform applies before removing a curio. Curse of binding, the curio's own
     * {@code canUnequip} answer and a denied {@code CurioUnequipEvent} each block removal; a curio
     * with no registered {@code ICurio} (a plain item in a curio slot) only has to clear the other
     * two rules.
     */
    public static boolean allowsUnequip(boolean bindingCurse, boolean curioAllowsUnequip, boolean eventDenied) {
        return !bindingCurse && curioAllowsUnequip && !eventDenied;
    }
}
