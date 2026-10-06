package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.function.Predicate;

public final class SpawnPlacer {
    private static final int ATTEMPTS = 12;

    private SpawnPlacer() {
    }

    public static BlockPos find(ServerLevel level, BlockPos center, int min, int max, EntityType<?> type,
                                RandomSource random, Predicate<BlockPos> allowed) {
        int low = Math.max(1, min);
        int high = Math.max(low, max);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = low + random.nextInt(high - low + 1);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            for (int dy = 6; dy >= -8; dy--) {
                BlockPos pos = new BlockPos(x, center.getY() + dy, z);
                if (standable(level, pos, type) && (allowed == null || allowed.test(pos))) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }
        return level.noCollision(type.getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    public static EntityType<?> mobType(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id == null ? "" : id.trim());
        if (location == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(location)) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(location);
    }

    public static Mob create(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random) {
        if (!(type.create(level) instanceof Mob mob)) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        return mob;
    }
}
