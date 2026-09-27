package net.schwarz.rotasutils.fabric;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.schwarz.rotasutils.compat.MonsterRollCompat;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.event.RotasEvents;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fabric counterpart of the Forge monster event bridge.
 *
 * <p>Fabric has no loader event that reports whether an entity was deserialised from disk, so a
 * persistent scoreboard tag records that this mod already saw the mob once. A mob without that tag
 * and without a recorded spawn reason is only treated as existing content after its first load,
 * which keeps {@code apply_existing} profiles from retro-fitting mobs that predate the pack.
 */
public final class MonsterFabricEvents {
    /** Spawn reasons observed for mobs that are still waiting for their level-load callback. */
    private static final Map<Mob, String> REASONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final String SEEN = "rotasutils.seen";
    private MonsterFabricEvents() { }

    public static void init() {
        EntityEvent.LIVING_CHECK_SPAWN.register((entity, level, x, y, z, type, spawner) -> {
            if (entity instanceof Mob mob) { REASONS.put(mob, type.name()); }
            return EventResult.pass();
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) { return; }
            MinecraftServer server = level.getServer();
            String reason = REASONS.remove(mob);
            server.execute(() -> joined(mob, reason));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) { return; }
            level.getServer().execute(() -> {
                var data = RotasData.instance();
                if (data != null && data.kernel() != null) { data.kernel().monsters().forget(mob); }
            });
        });
        // Architectury's living hurt event maps to Fabric's damage hook; Forge uses its own
        // LivingDamageEvent, so this registration stays inside the Fabric entrypoint.
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (!entity.level().isClientSide && amount > 0) { damaged(entity, source.getEntity(), amount); }
            return EventResult.pass();
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide || !(entity instanceof Mob mob)) { return; }
            var data = RotasData.instance();
            if (data == null || data.kernel() == null) { return; }
            LivingEntity killer = source.getEntity() instanceof LivingEntity living ? living : null;
            if (data.kernel().monsters().confirmDeath(mob, killer)) { RotasEvents.confirmedMonsterDeath(mob, source); }
        });
    }

    private static void joined(Mob mob, String reason) {
        // Strip third-party roll abilities (Monster Expansion's Rakoth); no-op for other mobs.
        MonsterRollCompat.stripRollGoals(mob);
        var data = RotasData.instance();
        if (data == null || data.kernel() == null || mob.isRemoved()) { return; }
        boolean fresh = reason != null || !mob.getTags().contains(SEEN);
        // Mark every mob we have seen, not just the ones that had a matching profile at the time.
        // Gating the tag on candidates meant a mob loaded before its profile existed never got
        // marked, stayed "fresh" on every later load, and so skipped the apply_existing filter -
        // letting newly added profiles retro-fit exactly the mobs this is meant to protect.
        if (fresh && !mob.getTags().contains(SEEN)) {
            mob.addTag(SEEN);
        }
        data.kernel().monsters().enqueue(mob, reason == null ? "UNKNOWN" : reason, !fresh);
    }

    private static void damaged(LivingEntity entity, net.minecraft.world.entity.Entity attacker, float amount) {
        var data = RotasData.instance();
        if (data == null || data.kernel() == null) { return; }
        var service = data.kernel().monsters();
        Map<String, String> facts = Map.of("event.amount", Float.toString(amount));
        if (entity instanceof Mob victim) {
            service.trigger(victim, attacker instanceof LivingEntity living ? living : null, MonsterDefinitions.Trigger.HURT, facts);
        }
        if (attacker instanceof Mob mob) {
            service.trigger(mob, entity, MonsterDefinitions.Trigger.ATTACK, facts);
        }
    }
}
