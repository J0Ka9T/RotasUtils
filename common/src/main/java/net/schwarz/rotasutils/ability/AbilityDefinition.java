package net.schwarz.rotasutils.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface AbilityDefinition {
    ResourceLocation id();

    int cooldownTicks();

    int durationTicks();

    boolean canStart(ServerPlayer player);

    default Target retarget(ServerPlayer player, Target aimed) {
        return aimed;
    }

    default boolean accepts(ServerPlayer player, Target target) {
        return true;
    }

    default boolean tick(AbilityContext context) {
        return true;
    }

    Timeline<AbilityContext> timeline();

    default void end(AbilityContext context, boolean completed) {
    }
}
