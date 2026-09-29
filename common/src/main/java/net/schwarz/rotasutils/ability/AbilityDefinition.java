package net.schwarz.rotasutils.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A cinematic ability. It says how long it runs, how long it cools down, and what the server does at
 * which moment; the {@link AbilityManager} owns everything else (validation, cooldown, the clock,
 * networking, cleanup), so a new ability is one small class and not a new system.
 */
public interface AbilityDefinition {
    ResourceLocation id();

    int cooldownTicks();

    int durationTicks();

    /** Whether the caster may begin now (the right item in hand, and so on). Cooldown is checked separately. */
    boolean canStart(ServerPlayer player);

    /** The gameplay events, in seconds since the ability began. */
    Timeline<AbilityContext> timeline();

    /** Called once when it finishes ({@code completed}) or is cut short (caster died, left, changed world). */
    default void end(AbilityContext context, boolean completed) {
    }
}
