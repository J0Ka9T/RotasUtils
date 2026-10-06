package net.schwarz.rotasutils.compat.forge;

import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.compat.CuriosServerCompat;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CuriosServerCompatImpl {
    private static final String MOD_ID = "curios";

    private CuriosServerCompatImpl() {
    }

    public static boolean available() {
        return Platform.isModLoaded(MOD_ID);
    }

    public static void click(ServerPlayer player, String slotType, int slotIndex, int button) {
        if (!available() || player == null || slotType == null || slotType.isBlank()
                || slotIndex < 0 || (button != 0 && button != 1)) {
            return;
        }
        CuriosAccess.click(player, slotType, slotIndex, button);
    }

    public static boolean unequip(ServerPlayer player, String slotType, int slotIndex) {
        if (!available() || player == null || slotType == null || slotIndex < 0) {
            return false;
        }
        return CuriosAccess.unequip(player, slotType, slotIndex);
    }

    public static Map<String, List<ItemStack>> equipped(ServerPlayer player) {
        if (!available() || player == null) {
            return Map.of();
        }
        return CuriosAccess.equipped(player);
    }

    private static final class CuriosAccess {
        private CuriosAccess() {
        }

        static void click(ServerPlayer player, String slotType, int slotIndex, int button) {
            try {
                ICuriosItemHandler handler = resolve(player);
                if (handler == null) {
                    return;
                }
                ICurioStacksHandler stacksHandler = handler.getStacksHandler(slotType).orElse(null);
                if (stacksHandler == null) {
                    return;
                }
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                if (slotIndex >= stacks.getSlots()) {
                    return;
                }

                ItemStack current = stacks.getStackInSlot(slotIndex).copy();
                ItemStack carried = player.containerMenu.getCarried().copy();

                if (button == 0) {
                    if (carried.isEmpty()) {
                        if (current.isEmpty() || !mayUnequip(player, slotType, slotIndex, current)) {
                            return;
                        }
                        handler.setEquippedCurio(slotType, slotIndex, ItemStack.EMPTY);
                        player.containerMenu.setCarried(current);
                    } else if (stacks.isItemValid(slotIndex, carried)) {
                        ItemStack equipped = carried.copyWithCount(1);
                        carried.shrink(1);
                        if (!current.isEmpty()) {
                            if (!mayUnequip(player, slotType, slotIndex, current)) {
                                return;
                            }
                            giveBack(player, current);
                        }
                        handler.setEquippedCurio(slotType, slotIndex, equipped);
                        player.containerMenu.setCarried(carried);
                    }
                } else if (carried.isEmpty()) {
                    if (current.isEmpty() || !mayUnequip(player, slotType, slotIndex, current)) {
                        return;
                    }
                    int take = Math.max(1, (current.getCount() + 1) / 2);
                    ItemStack picked = current.copyWithCount(take);
                    current.shrink(take);
                    handler.setEquippedCurio(slotType, slotIndex, current);
                    player.containerMenu.setCarried(picked);
                } else if (current.isEmpty() && stacks.isItemValid(slotIndex, carried)) {
                    ItemStack equipped = carried.copyWithCount(1);
                    carried.shrink(1);
                    handler.setEquippedCurio(slotType, slotIndex, equipped);
                    player.containerMenu.setCarried(carried);
                }
                player.containerMenu.broadcastChanges();
            } catch (RuntimeException failure) {
                Rotasutils.LOG.warn("Unable to apply Character Hub Curios click", failure);
            }
        }

        static boolean unequip(ServerPlayer player, String slotType, int slotIndex) {
            try {
                ICuriosItemHandler handler = resolve(player);
                if (handler == null) {
                    return false;
                }
                ICurioStacksHandler stacksHandler = handler.getStacksHandler(slotType).orElse(null);
                if (stacksHandler == null) {
                    return false;
                }
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                if (slotIndex >= stacks.getSlots()) {
                    return false;
                }
                ItemStack current = stacks.getStackInSlot(slotIndex).copy();
                if (current.isEmpty() || !mayUnequip(player, slotType, slotIndex, current)) {
                    return false;
                }
                handler.setEquippedCurio(slotType, slotIndex, ItemStack.EMPTY);
                giveBack(player, current);
                return true;
            } catch (RuntimeException failure) {
                Rotasutils.LOG.warn("Unable to unequip a Curios slot", failure);
                return false;
            }
        }

static Map<String, List<ItemStack>> equipped(ServerPlayer player) {
            try {
                ICuriosItemHandler handler = resolve(player);
                if (handler == null) {
                    return Map.of();
                }
                Map<String, List<ItemStack>> result = new LinkedHashMap<>();
                for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
                    if (result.size() >= 64) {
                        break;
                    }
                    IDynamicStackHandler stacks = entry.getValue().getStacks();
                    int slots = Math.min(64, stacks.getSlots());
                    List<ItemStack> found = new ArrayList<>(slots);
                    for (int index = 0; index < slots; index++) {
                        found.add(stacks.getStackInSlot(index));
                    }
                    result.put(entry.getKey(), List.copyOf(found));
                }
                return Map.copyOf(result);
            } catch (RuntimeException failure) {
                Rotasutils.LOG.warn("Unable to read Curios slots", failure);
                return Map.of();
            }
        }

        private static boolean mayUnequip(ServerPlayer player, String slotType, int slotIndex, ItemStack stack) {
            boolean bindingCurse = EnchantmentHelper.hasBindingCurse(stack);
            SlotContext context = new SlotContext(slotType, player, slotIndex, false, true);
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            boolean curioAllows = curio == null || curio.canUnequip(context);
            CurioUnequipEvent event = new CurioUnequipEvent(stack, context);
            MinecraftForge.EVENT_BUS.post(event);
            return CuriosServerCompat.allowsUnequip(bindingCurse, curioAllows, event.getResult() == Event.Result.DENY);
        }

        private static ICuriosItemHandler resolve(ServerPlayer player) {
            return CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        }

        private static void giveBack(ServerPlayer player, ItemStack stack) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}

