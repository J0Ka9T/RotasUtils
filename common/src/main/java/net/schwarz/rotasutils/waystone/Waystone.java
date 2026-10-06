package net.schwarz.rotasutils.waystone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record Waystone(String id, String name, String dimension, BlockPos pos) {
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
