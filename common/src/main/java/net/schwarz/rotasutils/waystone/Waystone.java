package net.schwarz.rotasutils.waystone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One warp pillar in the world.
 *
 * <p>The id is the world position itself ("dimension|x|y|z"), so a pillar cannot be duplicated and a
 * broken pillar is found again without a side table. Discovery is stored per player on
 * {@link net.schwarz.rotasutils.progress.PlayerProgress}; this record is only the shared half.
 */
public record Waystone(String id, String name, String dimension, BlockPos pos) {
    /** Longest name an administrator may give a pillar; long enough for Thai place names. */
    public static final int MAX_NAME = 32;

    public Waystone {
        if (id == null || id.isEmpty() || id.length() > 200) {
            throw new IllegalArgumentException("Invalid waystone id");
        }
        if (name == null || name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Invalid waystone name");
        }
        if (dimension == null || dimension.isEmpty() || pos == null) {
            throw new IllegalArgumentException("Invalid waystone position");
        }
    }

    public static String idOf(String dimension, BlockPos pos) {
        return dimension + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public static String idOf(Level level, BlockPos pos) {
        return idOf(level.dimension().location().toString(), pos);
    }

    public Waystone renamed(String newName) {
        return new Waystone(id, newName, dimension, pos);
    }

    /** The dimension key, or null when the stored dimension is not a valid id any more. */
    public ResourceLocation dimensionId() {
        return ResourceLocation.tryParse(dimension);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static Waystone load(CompoundTag tag) {
        return new Waystone(tag.getString("id"), tag.getString("name"), tag.getString("dimension"),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
    }
}
