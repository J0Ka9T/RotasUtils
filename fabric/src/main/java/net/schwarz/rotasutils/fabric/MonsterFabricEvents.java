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

public final class MonsterFabricEvents {
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
            String direct = net.schwarz.rotasutils.server.SpawnReasons.take(mob);
            if (reason == null && direct != null) {
                reason = direct;
                if (net.schwarz.rotasutils.server.MobSpawnDirector.deniedAtJoin(mob, direct)) {
                    server.execute(mob::discard);
                    return;
                }
            }
            final String spawnReason = reason;
            server.execute(() -> joined(mob, spawnReason));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) { return; }
            level.getServer().execute(() -> {
                var data = RotasData.instance();
                if (data != null && data.kernel() != null) { data.kernel().monsters().forget(mob); }
            });
        });
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
        MonsterRollCompat.stripRollGoals(mob);
        var data = RotasData.instance();
        if (data == null || data.kernel() == null || mob.isRemoved()) { return; }
        boolean fresh = reason != null || !mob.getTags().contains(SEEN);
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
