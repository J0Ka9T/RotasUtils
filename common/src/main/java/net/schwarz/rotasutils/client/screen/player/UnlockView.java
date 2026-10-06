package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.job.JobDef;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class UnlockView {
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private UnlockView() {
    }

    public static int activityColor(JobDef.ProductionEntry.Activity activity) {
        return switch (activity) {
            case CRAFT -> 0xFFC98B3D;
            case SMELT -> 0xFFE0643C;
            case MINE -> 0xFF8E9AA3;
            case HARVEST -> 0xFF7BB661;
            case FISH -> 0xFF4FA3D9;
            case BREW -> 0xFFB07CD8;
        };
    }

    public static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xFF8FB36B;
            case 2 -> 0xFF5FA8D3;
            case 3 -> 0xFFB07CD8;
            default -> 0xFFE0A13C;
        };
    }

    public static ItemStack icon(String selector) {
        return ICONS.computeIfAbsent(selector, UnlockView::resolveIcon);
    }

    private static ItemStack resolveIcon(String selector) {
        boolean tag = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
        if (id == null) {
            return new ItemStack(Items.PAPER);
        }
        if (!tag) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
            var block = BuiltInRegistries.BLOCK.get(id);
            return block.asItem() != Items.AIR ? new ItemStack(block.asItem()) : new ItemStack(Items.PAPER);
        }
        var items = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id));
        if (items.isPresent() && items.get().size() > 0) {
            return new ItemStack(items.get().get(0).value());
        }
        var blocks = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id));
        if (blocks.isPresent() && blocks.get().size() > 0 && blocks.get().get(0).value().asItem() != Items.AIR) {
            return new ItemStack(blocks.get().get(0).value().asItem());
        }
        return new ItemStack(Items.NAME_TAG);
    }

    public static String name(String selector) {
        if (selector.startsWith("#")) {
            String path = selector.substring(selector.indexOf(':') + 1);
            return L.t("rotasutils.job.unlock_tag", path.substring(path.lastIndexOf('/') + 1).replace('_', ' '));
        }
        ResourceLocation id = ResourceLocation.tryParse(selector);
        if (id == null) {
            return selector;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != Items.AIR) {
            return new ItemStack(item).getHoverName().getString();
        }
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id).getName().getString() : selector;
    }
}
