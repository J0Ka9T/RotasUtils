package net.schwarz.rotasutils.client;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.core.WorthTable;
import net.schwarz.rotasutils.item.StarQuality;

public final class WorthTooltip {
    private WorthTooltip() {
    }

    public static void register() {
        dev.architectury.event.events.client.ClientTooltipEvent.ITEM.register((stack, lines, flag) -> {
            long each = of(stack);
            if (each <= 0) {
                return;
            }
            WorthTable table = ClientState.worth();
            long sells = Math.round(each * table.sellPercent() / 100.0);
            String text = stack.getCount() > 1
                    ? L.t("rotasutils.worth.tooltip_stack", each, sells, each * stack.getCount())
                    : L.t("rotasutils.worth.tooltip", each, sells);
            lines.add(Component.literal(text).withStyle(ChatFormatting.GOLD));
        });
    }

    public static long of(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return ClientState.worth().worth(id, tag -> {
            ResourceLocation location = ResourceLocation.tryParse(tag);
            return location != null && stack.is(TagKey.create(Registries.ITEM, location));
        }, StarQuality.of(stack));
    }
}
