package net.schwarz.rotasutils.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.schwarz.rotasutils.compat.MonsterRollCompat;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.event.RotasEvents;
import java.util.Map;
import java.util.WeakHashMap;

public final class MonsterForgeEvents {
    private static final Map<Mob, String> REASONS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private MonsterForgeEvents() { }
    public static void init() { MinecraftForge.EVENT_BUS.register(MonsterForgeEvents.class); }
    @SubscribeEvent public static void finalized(MobSpawnEvent.FinalizeSpawn event) {
        REASONS.put(event.getEntity(), event.getSpawnType().name());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) { return; }
        MinecraftServer server = event.getLevel().getServer(); if (server == null) { return; }
        String reason = REASONS.remove(mob);
        server.execute(() -> {
            // Strip third-party roll abilities (Monster Expansion's Rakoth) before RotasUtils
            // hands the mob to the kernel; no-op for mobs from any other mod.
            MonsterRollCompat.stripRollGoals(mob);
            var data = RotasData.instance();
            if (data != null && data.kernel() != null && !mob.isRemoved()) {
                data.kernel().monsters().enqueue(mob, reason == null ? "UNKNOWN" : reason, event.loadedFromDisk());
            }
        });
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) { return; }
        MinecraftServer server = event.getLevel().getServer(); if (server == null) { return; }
        server.execute(() -> {
            var data = RotasData.instance(); if (data != null && data.kernel() != null) { data.kernel().monsters().forget(mob); }
        });
    }
    /**
     * Feeds the {@code event.amount} fact used by monster HURT/ATTACK conditions.
     *
     * <p>{@code LivingHurtEvent}, not {@code LivingDamageEvent}: Fabric reports this through
     * Architectury's {@code LIVING_HURT}, which fires before armour and absorption. Reading the
     * post-mitigation number here made identical hits produce different facts per loader, so the
     * same ability threshold fired on one loader and not the other.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damaged(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) { return; }
        var data = RotasData.instance(); if (data == null || data.kernel() == null) { return; }
        var service = data.kernel().monsters();
        Map<String, String> facts = Map.of("event.amount", Float.toString(event.getAmount()));
        if (event.getEntity() instanceof Mob victim) {
            service.trigger(victim, event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : null,
                    MonsterDefinitions.Trigger.HURT, facts);
        }
        if (event.getSource().getEntity() instanceof Mob attacker) {
            service.trigger(attacker, event.getEntity(), MonsterDefinitions.Trigger.ATTACK, facts);
        }
    }
    /**
     * Confirms a kernel monster's death.
     *
     * <p>This used to listen to {@code LivingDropsEvent}, which only fires when the mob actually
     * drops something: with {@code doMobLoot} off, or drops cancelled by another mod, the kill was
     * never confirmed and the monster's rewards, quest KILL_ENTITY credit and combat XP were all
     * skipped. Fabric confirms from {@code AFTER_DEATH} and always fired, so the two loaders
     * disagreed. {@code LivingDeathEvent} is the Forge equivalent and fires on every death.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) { return; }
        var data = RotasData.instance(); if (data == null || data.kernel() == null) { return; }
        if (data.kernel().monsters().confirmDeath(mob, event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : null)) {
            RotasEvents.confirmedMonsterDeath(mob, event.getSource());
        }
    }
}
