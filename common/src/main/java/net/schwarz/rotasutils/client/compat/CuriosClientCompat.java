package net.schwarz.rotasutils.client.compat;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.network.RotasNetwork;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional Curios client bridge for the Character Hub.
 *
 * <p>Curios remains the owner of accessory storage and validation. Rotas only reads the real
 * equipped slot handlers for presentation and delegates editing back to Curios' own container.
 * Reflection keeps the common/Fabric build free of Forge-only Curios classes.</p>
 */
@Environment(EnvType.CLIENT)
public final class CuriosClientCompat {
    public static final String MOD_ID = "curios";

    public record Entry(String slotType, int slotIndex, ItemStack stack) {
        public String displayName() {
            if (slotType == null || slotType.isBlank()) return "Accessory";
            String[] words = slotType.replace('-', '_').split("_");
            StringBuilder out = new StringBuilder();
            for (String word : words) {
                if (word.isBlank()) continue;
                if (!out.isEmpty()) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return out.isEmpty() ? slotType : out.toString();
        }
    }

    private static boolean attempted;
    private static boolean ready;
    private static boolean loggedFailure;
    private static Method getCuriosInventory;
    private static Method lazyResolve;
    private static Method handlerGetCurios;
    private static Method stacksHandlerGetStacks;
    private static Method stacksHandlerIsVisible;
    private static Method dynamicGetSlots;
    private static Method dynamicGetStackInSlot;

    private static UUID cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static List<Entry> cachedEntries = List.of();

    private CuriosClientCompat() {
    }

    public static boolean isPresent() {
        return Platform.isModLoaded(MOD_ID);
    }

    public static boolean isReady() {
        return prepare();
    }

    /** Returns every real visible Curios slot, including empty slots, in Curios' own order. */
    public static List<Entry> entries(LocalPlayer player) {
        if (player == null || !prepare()) return List.of();
        UUID id = player.getUUID();
        if (id.equals(cachedPlayer) && cachedTick == player.tickCount) return cachedEntries;

        List<Entry> out = new ArrayList<>();
        try {
            Object lazy = getCuriosInventory.invoke(null, player);
            Object resolved = lazyResolve.invoke(lazy);
            if (resolved instanceof Optional<?> optional && optional.isPresent()) {
                Object handler = optional.get();
                Object rawMap = handlerGetCurios.invoke(handler);
                if (rawMap instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> rawEntry : map.entrySet()) {
                        String slotType = String.valueOf(rawEntry.getKey());
                        Object stackHandler = rawEntry.getValue();
                        if (stackHandler == null) continue;
                        Object visible = stacksHandlerIsVisible.invoke(stackHandler);
                        if (visible instanceof Boolean b && !b) continue;
                        Object stacks = stacksHandlerGetStacks.invoke(stackHandler);
                        int slots = (Integer) dynamicGetSlots.invoke(stacks);
                        for (int i = 0; i < slots; i++) {
                            Object rawStack = dynamicGetStackInSlot.invoke(stacks, i);
                            ItemStack stack = rawStack instanceof ItemStack item ? item.copy() : ItemStack.EMPTY;
                            out.add(new Entry(slotType, i, stack));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return List.of();
        }
        cachedPlayer = id;
        cachedTick = player.tickCount;
        cachedEntries = List.copyOf(out);
        return cachedEntries;
    }

    /** Sends a click on a Character Hub Curios slot to the authoritative server handler. */
    public static boolean click(String slotType, int slotIndex, int button) {
        if (slotType == null || slotType.isBlank() || slotIndex < 0 || (button != 0 && button != 1)
                || !prepare()) {
            return false;
        }
        net.minecraft.nbt.CompoundTag payload = new net.minecraft.nbt.CompoundTag();
        payload.putString("slot_type", slotType);
        payload.putInt("slot_index", slotIndex);
        payload.putInt("button", button);
        RotasNetwork.sendAction("curio_click", payload);
        return true;
    }

    private static boolean prepare() {
        if (!isPresent()) return false;
        if (attempted) return ready;
        attempted = true;
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> lazyOptional = Class.forName("net.minecraftforge.common.util.LazyOptional");
            Class<?> itemHandler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> stacksHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> dynamicHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            getCuriosInventory = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            lazyResolve = lazyOptional.getMethod("resolve");
            handlerGetCurios = itemHandler.getMethod("getCurios");
            stacksHandlerGetStacks = stacksHandler.getMethod("getStacks");
            stacksHandlerIsVisible = stacksHandler.getMethod("isVisible");
            dynamicGetSlots = dynamicHandler.getMethod("getSlots");
            dynamicGetStackInSlot = dynamicHandler.getMethod("getStackInSlot", int.class);
            ready = true;
            Rotasutils.LOG.info("Curios detected; Character Hub accessory slots are linked to the live Curios inventory");
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
        return ready;
    }

    private static void fail(Exception e) {
        ready = false;
        if (!loggedFailure) {
            loggedFailure = true;
            Rotasutils.LOG.error("Curios is installed but Character Hub integration could not be linked", e);
        }
    }
}
