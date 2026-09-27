package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public record HouseBounds(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final long MAX_VOLUME = 16_777_216L;

    public HouseBounds {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > 128 || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid house bounds");
        }
        if (volumeOf(minX, minY, minZ, maxX, maxY, maxZ) > MAX_VOLUME) {
            throw new IllegalArgumentException("House volume exceeds " + MAX_VOLUME);
        }
    }

    public static HouseBounds between(String dimension, BlockPos first, BlockPos second) {
        return new HouseBounds(dimension,
                Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
    }

    public boolean contains(String targetDimension, BlockPos pos) {
        return dimension.equals(targetDimension) && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean overlaps(HouseBounds other) {
        return dimension.equals(other.dimension) && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public long volume() { return volumeOf(minX, minY, minZ, maxX, maxY, maxZ); }

    private static long volumeOf(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        try {
            return Math.multiplyExact(Math.multiplyExact((long) maxX - minX + 1L, (long) maxY - minY + 1L), (long) maxZ - minZ + 1L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension);
        tag.putIntArray("min", new int[]{minX, minY, minZ});
        tag.putIntArray("max", new int[]{maxX, maxY, maxZ});
        return tag;
    }

    public static HouseBounds load(CompoundTag tag) {
        int[] min = tag.getIntArray("min");
        int[] max = tag.getIntArray("max");
        if (min.length != 3 || max.length != 3) throw new IllegalArgumentException("Corrupt house bounds");
        return new HouseBounds(tag.getString("dimension"), min[0], min[1], min[2], max[0], max[1], max[2]);
    }
}
