package net.schwarz.rotasutils.server;

import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SpawnReasons {
    private static final Map<Mob, String> DIRECT = Collections.synchronizedMap(new WeakHashMap<>());

    private SpawnReasons() {
    }

    public static void note(Mob mob, net.minecraft.world.entity.MobSpawnType type) {
        if (type != null) DIRECT.put(mob, type.name());
    }

    public static String take(Mob mob) {
        return DIRECT.remove(mob);
    }
}
