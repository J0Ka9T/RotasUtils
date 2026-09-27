package net.schwarz.rotasutils.server;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class MonsterStorage {
    private MonsterStorage() { }

    public static double healthRatio(float health, float maxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) { return 0.0D; }
        return Math.max(0.0D, Math.min(1.0D, health / maxHealth));
    }

    public static float healthAtRatio(float maxHealth, double ratio) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F || !Double.isFinite(ratio)) { return 0.0F; }
        return (float) (maxHealth * Math.max(0.0D, Math.min(1.0D, ratio)));
    }

    @ExpectPlatform public static CompoundTag read(LivingEntity entity) { throw new AssertionError(); }
    @ExpectPlatform public static void write(LivingEntity entity, CompoundTag state) { throw new AssertionError(); }
}
