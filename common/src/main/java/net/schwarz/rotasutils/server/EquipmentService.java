package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.compat.CuriosServerCompat;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ItemCatalog;
import net.schwarz.rotasutils.core.ItemDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Enforces item requirements and applies set bonuses plus Curios item modifiers.
 *
 * <p>Vanilla equipment modifiers are carried by the stacks themselves. Curios slots and set bonuses
 * have no vanilla equivalent, so they are applied here as transient, mod-owned attribute modifiers
 * that are removed on logout, reload and shutdown exactly like the stat modifiers.
 */
public final class EquipmentService {
    /** How often equipped items are re-checked; requirements are enforced within one second. */
    public static final int INTERVAL_TICKS = 20;
    private record Applied(ServerPlayer player, String signature, Map<Attribute, List<UUID>> modifiers) { }
    private final Map<UUID, Applied> applied = new HashMap<>();
    /** The stack each player was last seen holding, so a weapon swap is noticed the tick it happens. */
    private final Map<UUID, ItemStack> heldWeapon = new HashMap<>();
    private long tick;

    public void tick(net.minecraft.server.MinecraftServer server, RotasData data) {
        boolean scheduled = ++tick % INTERVAL_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // A card in a weapon has to count from the swing it is swung with, not up to a second later,
            // so a changed main hand refreshes at once and everything else waits for the interval.
            ItemStack held = player.getMainHandItem();
            boolean swapped = heldWeapon.get(player.getUUID()) != held;
            heldWeapon.put(player.getUUID(), held);
            if (!scheduled && !swapped) { continue; }
            try { refresh(player, data); }
            catch (RuntimeException failure) {
                Rotasutils.LOG.error("RPG equipment refresh failed for {}: {}", player.getGameProfile().getName(), failure.getMessage());
            }
        }
    }

    /** Re-evaluates one player; returns the number of items removed for unmet requirements. */
    public int refresh(ServerPlayer player, RotasData data) {
        if (!player.server.isSameThread()) { throw new IllegalStateException("Equipment updates require the server thread"); }
        Map<String, Map<ItemDefinitions.Operation, Double>> pending = new LinkedHashMap<>();
        int removed = 0;
        // Item profiles, sets and Curios need the kernel; cards do not, so they are collected either way.
        if (data.kernel() != null) {
            var catalog = data.kernel().content().items();
            removed = enforce(player, data, catalog);
            Map<ContentId, Integer> owned = new TreeMap<>();
            for (ItemStack stack : equipped(player)) {
                if (stack.isEmpty() || !ItemFactory.isRpgItem(stack)) { continue; }
                ContentId set = ItemFactory.set(stack);
                if (set != null && catalog.sets().containsKey(set)) { owned.merge(set, 1, Integer::sum); }
                ItemFactory.curiosModifiers(catalog, stack).forEach((attribute, byOperation) ->
                        byOperation.forEach((operation, value) -> pending.computeIfAbsent(attribute, key -> new LinkedHashMap<>())
                                .merge(operation, value, Double::sum)));
            }
            catalog.bonuses(owned).forEach((set, bonuses) -> bonuses.forEach(bonus -> {
                for (ItemDefinitions.Operation operation : ItemDefinitions.Operation.values()) {
                    ItemDefinitions.derive(bonus.modifiers(), 1, 1, operation).forEach((attribute, value) ->
                            pending.computeIfAbsent(attribute, key -> new LinkedHashMap<>()).merge(operation, value, Double::sum));
                }
            }));
        }
        collectCards(player, pending);
        apply(player, pending);
        return removed;
    }

    /**
     * The cards in the player's worn armour and the weapon in their hand. A card only ever moves a plain
     * Minecraft attribute, so it joins the same pass the set bonuses use.
     */
    private static void collectCards(ServerPlayer player, Map<String, Map<ItemDefinitions.Operation, Double>> pending) {
        List<ItemStack> socketed = new ArrayList<>(equipped(player));
        socketed.add(player.getMainHandItem());
        for (ItemStack stack : socketed) {
            if (stack.isEmpty()) { continue; }
            for (var effect : net.schwarz.rotasutils.server.CardService.effects(stack)) {
                ItemDefinitions.Operation operation = effect.operation() == net.schwarz.rotasutils.stat.CharacterStat.Operation.ADD
                        ? ItemDefinitions.Operation.ADDITION
                        : effect.operation() == net.schwarz.rotasutils.stat.CharacterStat.Operation.MULTIPLY_BASE
                        ? ItemDefinitions.Operation.MULTIPLY_BASE : ItemDefinitions.Operation.MULTIPLY_TOTAL;
                pending.computeIfAbsent(effect.attribute(), key -> new LinkedHashMap<>())
                        .merge(operation, effect.perPoint(), Double::sum);
            }
        }
    }

    private int enforce(ServerPlayer player, RotasData data, ItemCatalog catalog) {
        int level = data.progress(player.getUUID()).level();
        Map<String, Double> stats = data.kernel().stats(player);
        int removed = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (slot == EquipmentSlot.MAINHAND || stack.isEmpty()) { continue; }
            String unmet = ItemFactory.unmet(catalog, stack, level, stats::get);
            if (unmet.isEmpty()) { continue; }
            player.setItemSlot(slot, ItemStack.EMPTY);
            giveBack(player, stack);
            notify(player, stack, unmet);
            removed++;
        }
        for (var entry : CuriosServerCompat.equipped(player).entrySet()) {
            List<ItemStack> stacks = entry.getValue();
            for (int index = 0; index < stacks.size(); index++) {
                ItemStack stack = stacks.get(index);
                if (stack.isEmpty()) { continue; }
                String unmet = ItemFactory.unmet(catalog, stack, level, stats::get);
                if (unmet.isEmpty()) { continue; }
                if (CuriosServerCompat.unequip(player, entry.getKey(), index)) { notify(player, stack, unmet); removed++; }
            }
        }
        return removed;
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (player.getInventory().add(copy) && copy.isEmpty()) { return; }
        var progress = RotasData.get(player.server).progress(player.getUUID());
        // The slot is already cleared, so a full mailbox must not throw here and destroy the item.
        if (progress.mailbox().size() < net.schwarz.rotasutils.progress.PlayerProgress.MAILBOX_LIMIT) {
            progress.addMail(copy.save(new net.minecraft.nbt.CompoundTag()));
        } else {
            player.drop(copy, false);
        }
    }

    private static void notify(ServerPlayer player, ItemStack stack, String unmet) {
        player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.equip.requires", stack.getHoverName().getString(), unmet));
    }

    /** Equipped stacks that can carry RPG data: armour, off-hand and every Curios slot. */
    private static List<ItemStack> equipped(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.MAINHAND) { stacks.add(player.getItemBySlot(slot)); }
        }
        CuriosServerCompat.equipped(player).values().forEach(stacks::addAll);
        return stacks;
    }

    private void apply(ServerPlayer player, Map<String, Map<ItemDefinitions.Operation, Double>> pending) {
        String signature = pending.toString();
        Applied previous = applied.get(player.getUUID());
        if (previous != null && previous.player() == player && previous.signature().equals(signature)) { return; }
        remove(previous);
        Map<Attribute, List<UUID>> owned = new HashMap<>();
        pending.forEach((attribute, byOperation) -> {
            Attribute target = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(attribute));
            if (target == null) { throw new IllegalArgumentException("Unknown equipment attribute: " + attribute); }
            var instance = player.getAttribute(target);
            if (instance == null) { return; }
            List<UUID> ids = new ArrayList<>();
            byOperation.forEach((operation, value) -> {
                UUID id = UUID.nameUUIDFromBytes(("rotasutils.equipment/" + attribute + "/" + operation).getBytes(StandardCharsets.UTF_8));
                instance.removeModifier(id);
                instance.addTransientModifier(new AttributeModifier(id, "rotasutils.equipment", value, ItemFactory.vanilla(operation)));
                ids.add(id);
            });
            owned.put(target, List.copyOf(ids));
        });
        if (player.getHealth() > player.getMaxHealth()) { player.setHealth(player.getMaxHealth()); }
        applied.put(player.getUUID(), new Applied(player, signature, Map.copyOf(owned)));
    }

    public void forget(UUID id) { remove(applied.remove(id)); heldWeapon.remove(id); }
    public void clear() { applied.values().forEach(EquipmentService::remove); applied.clear(); heldWeapon.clear(); }
    /** Content changes can retire attributes, so the next tick rebuilds every player's modifiers. */
    public void contentReloaded() { clear(); }

    private static void remove(Applied entry) {
        if (entry == null) { return; }
        entry.modifiers().forEach((attribute, ids) -> {
            var instance = entry.player().getAttribute(attribute);
            if (instance != null) { ids.forEach(instance::removeModifier); }
        });
    }
}
