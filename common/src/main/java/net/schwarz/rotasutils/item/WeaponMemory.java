package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.WeaponMemoryMath;

public final class WeaponMemory {
    public static final String TAG = "RotasMemory";

    private WeaponMemory() {
    }

    public static CompoundTag read(ItemStack stack) {
        CompoundTag tag = stack == null || stack.isEmpty() ? null : stack.getTag();
        return tag != null && tag.contains(TAG, Tag.TAG_COMPOUND) ? tag.getCompound(TAG).copy() : new CompoundTag();
    }

    public static long kills(ItemStack stack) {
        return read(stack).getLong(WeaponMemoryMath.KILLS);
    }

    public static WeaponMemoryMath.Result record(ItemStack stack, String kind, boolean boss, boolean nemesis,
                                                 long[] milestones, int maxKinds) {
        CompoundTag memory = read(stack);
        WeaponMemoryMath.Result result = WeaponMemoryMath.record(memory, kind, boss, nemesis, milestones, maxKinds);
        stack.getOrCreateTag().put(TAG, memory);
        return result;
    }
}
