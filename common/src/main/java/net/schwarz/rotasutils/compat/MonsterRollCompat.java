package net.schwarz.rotasutils.compat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.mixin.MobAccessor;

import java.util.ArrayList;

public final class MonsterRollCompat {
    public static final String MONSTER_EXPANSION_PACKAGE = "net.saksolm.monsterexpansion";
    public static final String ROLL = "Roll";

    private MonsterRollCompat() {
    }

    public static boolean handles(String className) {
        return className != null && className.startsWith(MONSTER_EXPANSION_PACKAGE);
    }

    public static boolean isRollGoal(Class<?> goalClass) {
        return goalClass != null && goalClass.getSimpleName().contains(ROLL);
    }

    public static int stripRollGoals(Mob mob) {
        if (mob == null || !handles(mob.getClass().getName())) {
            return 0;
        }
        int removed = strip(((MobAccessor) mob).rotasutils$goalSelector())
                + strip(((MobAccessor) mob).rotasutils$targetSelector());
        if (removed > 0) {
            Rotasutils.LOG.info("RotasUtils removed {} roll goal(s) from {}", removed, mob.getType());
        }
        return removed;
    }

    private static int strip(GoalSelector selector) {
        int removed = 0;
        for (WrappedGoal wrapped : new ArrayList<>(selector.getAvailableGoals())) {
            if (isRollGoal(wrapped.getGoal().getClass())) {
                selector.removeGoal(wrapped.getGoal());
                removed++;
            }
        }
        return removed;
    }
}
