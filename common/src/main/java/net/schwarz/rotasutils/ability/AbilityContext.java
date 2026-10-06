package net.schwarz.rotasutils.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class AbilityContext {
    public final ServerPlayer player;
    public final ServerLevel level;
    public final long startTick;
    public final Target target;
    public final long seed;
    public Object state;

    record Delayed(long dueTick, Runnable action) {
    }

    final List<Delayed> delayed = new ArrayList<>();

    AbilityContext(ServerPlayer player, ServerLevel level, long startTick, Target target, long seed) {
        this.player = player;
        this.level = level;
        this.startTick = startTick;
        this.target = target;
        this.seed = seed;
    }

    public void after(int ticks, Runnable action) {
        delayed.add(new Delayed(level.getGameTime() + Math.max(0, ticks), action));
    }

    public double seconds() {
        return (level.getGameTime() - startTick) / 20.0;
    }
}
