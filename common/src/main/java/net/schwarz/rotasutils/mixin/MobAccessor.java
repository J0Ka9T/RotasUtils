package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Mob}'s goal selectors, which are {@code protected}. RotasUtils uses this to strip a
 * third-party mob ability at spawn without a hard dependency on that mod.
 */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("goalSelector")
    GoalSelector rotasutils$goalSelector();

    @Accessor("targetSelector")
    GoalSelector rotasutils$targetSelector();
}
