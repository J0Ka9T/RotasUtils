package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ItemCatalog;
import net.schwarz.rotasutils.core.ItemDefinitions;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;

public final class ItemFactory {
    public static final String TAG = "RotasItem";
    private ItemFactory() { }

    public static ItemStack create(ItemCatalog catalog, ContentId profileId, int sourceLevel, int count, RandomGenerator random) {
        var profile = catalog.profiles().get(profileId);
        if (profile == null) { throw new IllegalArgumentException("Unknown or disabled item profile: " + profileId); }
        if (count < 1 || count > 64) { throw new IllegalArgumentException("Item count must be 1..64"); }
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation(profile.item()));
        if (item == net.minecraft.world.item.Items.AIR) { throw new IllegalArgumentException("Unknown item: " + profile.item()); }
        ContentId rarityId = ItemDefinitions.rarity(profile, catalog.rarities(), random);
        var rarity = catalog.rarities().get(rarityId);
        int level = profile.level(sourceLevel);
        ItemStack stack = new ItemStack(item, Math.min(count, item.getMaxStackSize()));

        CompoundTag data = new CompoundTag();
        data.putInt("schema", 1);
        data.putString("profile", profileId.value());
        data.putString("rarity", rarityId.value());
        data.putInt("level", level);
        if (profile.set() != null) { data.putString("set", profile.set().value()); }
        if (!profile.curiosSlot().isEmpty()) { data.putString("curios_slot", profile.curiosSlot()); }
        stack.getOrCreateTag().put(TAG, data);

        EquipmentSlot slot = profile.slot().isEmpty() ? null : EquipmentSlot.valueOf(profile.slot());
        if (slot != null) {
            for (ItemDefinitions.Operation operation : ItemDefinitions.Operation.values()) {
                ItemDefinitions.derive(profile.modifiers(), level, rarity.modifierMultiplier(), operation)
                        .forEach((attribute, value) -> {
                            Attribute target = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(attribute));
                            if (target == null) { throw new IllegalArgumentException("Unknown item attribute: " + attribute); }
                            stack.addAttributeModifier(target, new AttributeModifier(modifierId(profileId, attribute, operation),
                                    "rotasutils.item/" + profileId, value, vanilla(operation)), slot);
                        });
            }
        }
        stack.setHoverName(name(profile, rarity, level, stack));
        lore(stack, profile, rarity, level);
        return stack;
    }

    public static Map<String, Map<ItemDefinitions.Operation, Double>> curiosModifiers(ItemCatalog catalog, ItemStack stack) {
        var profile = profile(catalog, stack);
        if (profile == null || profile.curiosSlot().isEmpty()) { return Map.of(); }
        var rarity = catalog.rarities().get(rarity(stack));
        if (rarity == null) { return Map.of(); }
        Map<String, Map<ItemDefinitions.Operation, Double>> result = new LinkedHashMap<>();
        for (ItemDefinitions.Operation operation : ItemDefinitions.Operation.values()) {
            ItemDefinitions.derive(profile.modifiers(), level(stack), rarity.modifierMultiplier(), operation)
                    .forEach((attribute, value) -> result.computeIfAbsent(attribute, key -> new LinkedHashMap<>()).put(operation, value));
        }
        return Map.copyOf(result);
    }

    public static AttributeModifier.Operation vanilla(ItemDefinitions.Operation operation) {
        return switch (operation) {
            case ADDITION -> AttributeModifier.Operation.ADDITION;
            case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
        };
    }

    public static UUID modifierId(ContentId owner, String attribute, ItemDefinitions.Operation operation) {
        return UUID.nameUUIDFromBytes(("rotasutils.item/" + owner + "/" + attribute + "/" + operation).getBytes(StandardCharsets.UTF_8));
    }

    public static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG, net.minecraft.nbt.Tag.TAG_COMPOUND) ? tag.getCompound(TAG) : new CompoundTag();
    }

    public static boolean isRpgItem(ItemStack stack) { return !data(stack).isEmpty(); }

    public static ItemDefinitions.Profile profile(ItemCatalog catalog, ItemStack stack) {
        ContentId id = profileId(stack);
        return id == null ? null : catalog.profiles().get(id);
    }

    public static ContentId profileId(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data.getInt("schema") != 1 || !data.contains("profile", net.minecraft.nbt.Tag.TAG_STRING)) { return null; }
        try { return new ContentId(data.getString("profile")); }
        catch (IllegalArgumentException malformed) { return null; }
    }

    public static ContentId rarity(ItemStack stack) {
        CompoundTag data = data(stack);
        if (!data.contains("rarity", net.minecraft.nbt.Tag.TAG_STRING)) { return null; }
        try { return new ContentId(data.getString("rarity")); }
        catch (IllegalArgumentException malformed) { return null; }
    }

    public static int level(ItemStack stack) { return Math.max(1, data(stack).getInt("level")); }

    public static ContentId set(ItemStack stack) {
        CompoundTag data = data(stack);
        if (!data.contains("set", net.minecraft.nbt.Tag.TAG_STRING)) { return null; }
        try { return new ContentId(data.getString("set")); }
        catch (IllegalArgumentException malformed) { return null; }
    }

    public static String unmet(ItemCatalog catalog, ItemStack stack, int level, Function<String, Double> stats) {
        var profile = profile(catalog, stack);
        return profile == null ? "" : profile.requirement().unmet(level, stats);
    }

    private static MutableComponent name(ItemDefinitions.Profile profile, ItemDefinitions.Rarity rarity, int level, ItemStack stack) {
        String template = profile.name().isEmpty() ? "{name}" : profile.name();
        String text = template.replace("{name}", stack.getItem().getDescription().getString())
                .replace("{rarity}", rarity.label()).replace("{level}", Integer.toString(level));
        MutableComponent component = Component.literal(text);
        ChatFormatting colour = ChatFormatting.getByName(rarity.color());
        return colour == null ? component : component.withStyle(colour);
    }

    private static void lore(ItemStack stack, ItemDefinitions.Profile profile, ItemDefinitions.Rarity rarity, int level) {
        ListTag lines = new ListTag();
        lines.add(StringTag.valueOf(Component.Serializer.toJson(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.rarity_level", rarity.label(), level)
                .withStyle(ChatFormatting.GRAY))));
        profile.lore().forEach(line -> lines.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal(line.replace("{level}", Integer.toString(level)).replace("{rarity}", rarity.label()))
                        .withStyle(ChatFormatting.DARK_GRAY)))));
        String requirement = requirementText(profile);
        if (!requirement.isEmpty()) {
            lines.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(requirement).withStyle(ChatFormatting.DARK_RED))));
        }
        stack.getOrCreateTagElement("display").put("Lore", lines);
    }

    private static String requirementText(ItemDefinitions.Profile profile) {
        var requirement = profile.requirement();
        if (requirement.minLevel() <= 1 && requirement.stats().isEmpty()) { return ""; }
        StringBuilder text = new StringBuilder(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.item.requires_level", requirement.minLevel()));
        requirement.stats().forEach((stat, value) -> text.append(", ").append(stat).append(' ').append(value));
        return text.toString();
    }
}
