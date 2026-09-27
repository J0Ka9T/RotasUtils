package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.WeaponMemoryMath;

/**
 * What a weapon remembers (ความทรงจำอาวุธ), stored in a {@code RotasMemory} compound on the stack.
 *
 * <p>Like refinement it lives on the item, so it follows the weapon through a chest, a trade and a
 * death, and works on any weapon the game treats as one. Reading never creates the compound: a weapon
 * that has killed nothing carries nothing.</p>
 */
public final class WeaponMemory {
    public static final String TAG = "RotasMemory";

    private WeaponMemory() {
    }

    /** The memory compound, or an empty one when the weapon has none. Never the live tag. */
    public static CompoundTag read(ItemStack stack) {
        CompoundTag tag = stack == null || stack.isEmpty() ? null : stack.getTag();
        return tag != null && tag.contains(TAG, Tag.TAG_COMPOUND) ? tag.getCompound(TAG).copy() : new CompoundTag();
    }

    public static long kills(ItemStack stack) {
        return read(stack).getLong(WeaponMemoryMath.KILLS);
    }

    /** Counts one kill on the stack itself and returns the rank change it caused. */
    public static WeaponMemoryMath.Result record(ItemStack stack, String kind, boolean boss, boolean nemesis,
                                                 long[] milestones, int maxKinds) {
        CompoundTag memory = read(stack);
        WeaponMemoryMath.Result result = WeaponMemoryMath.record(memory, kind, boss, nemesis, milestones, maxKinds);
        stack.getOrCreateTag().put(TAG, memory);
        return result;
    }
}
