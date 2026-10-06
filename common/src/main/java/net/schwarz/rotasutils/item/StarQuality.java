package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class StarQuality {
    public static final String TAG = "RotasStar";

    private StarQuality() {
    }

    public static int of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(2, tag.getInt(TAG)));
    }

    public static ItemStack apply(ItemStack stack, int star) {
        if (star < 1 || star > 2) {
            return stack;
        }
        stack.getOrCreateTag().putInt(TAG, star);
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        Component line = Component.translatable(star == 2 ? "rotasutils.star.gold" : "rotasutils.star.silver")
                .withStyle(style -> style.withColor(star == 2 ? 0xFFD24A : 0xC9D3DD).withItalic(false));
        lore.add(0, StringTag.valueOf(Component.Serializer.toJson(line)));
        display.put("Lore", lore);
        return stack;
    }
}
