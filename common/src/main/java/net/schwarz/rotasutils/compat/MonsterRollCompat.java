package net.schwarz.rotasutils.compat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.mixin.MobAccessor;

import java.util.ArrayList;

/**
 * Strips third-party mob "roll" abilities.
 *
 * <p>Monster Expansion gives its Rakoth a rolling attack ({@code RakothRollGoal}), gated by that
 * mod's own {@code enable_roll} config. A pack may want the roll gone regardless of that setting,
 * so this removes any roll goal a Monster Expansion mob carries. It matches on the class name
 * instead of importing the mod, so RotasUtils keeps working with or without it installed.</p>
 */
public final class MonsterRollCompat {
    /** Only mobs whose class lives under this package are touched, so other mods are never harmed. */
    public static final String MONSTER_EXPANSION_PACKAGE = "net.saksolm.monsterexpansion";
    /** A goal whose class simple name contains this is treated as a roll ability. */
    public static final String ROLL = "Roll";

    private MonsterRollCompat() {
    }

    /** True when the mob class belongs to Monster Expansion (matched by name, no hard dependency). */
    public static boolean handles(String className) {
        return className != null && className.startsWith(MONSTER_EXPANSION_PACKAGE);
    }

    /** True when a goal class names a roll ability this helper would strip. */
    public static boolean isRollGoal(Class<?> goalClass) {
        return goalClass != null && goalClass.getSimpleName().contains(ROLL);
    }

    /**
     * Removes every roll goal from a Monster Expansion mob. Safe on any mob: it is a no-op for other
     * mods' entities and for vanilla mobs.
     *
     * @return how many goals were removed.
     */
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
        // Copy first: removeGoal mutates the selector's goal set.
        for (WrappedGoal wrapped : new ArrayList<>(selector.getAvailableGoals())) {
            if (isRollGoal(wrapped.getGoal().getClass())) {
                selector.removeGoal(wrapped.getGoal());
                removed++;
            }
        }
        return removed;
    }
}
