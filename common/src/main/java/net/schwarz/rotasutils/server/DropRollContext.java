package net.schwarz.rotasutils.server;

import net.minecraft.world.entity.Entity;

public final class DropRollContext {
    private static final ThreadLocal<Entity> DROPPING = new ThreadLocal<>();

    private DropRollContext() {
    }

    public static void begin(Entity entity) {
        DROPPING.set(entity);
    }

    public static void end() {
        DROPPING.remove();
    }

    public static boolean isDropping(Entity entity) {
        return entity != null && DROPPING.get() == entity;
    }
}
