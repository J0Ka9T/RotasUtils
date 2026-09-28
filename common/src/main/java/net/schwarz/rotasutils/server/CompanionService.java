package net.schwarz.rotasutils.server;

import dev.architectury.event.EventResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A fighter hired from a guard for a few minutes. One per player; it keeps up with its employer (a teleport when
 * left behind), attacks whatever attacks them or what they attack, and leaves when the contract ends, when the
 * employer logs out, or when it falls - without drops, so hiring is never an iron farm.
 *
 * <p>Performance: one entity per hiring player, checked once a second; the list lives in memory and a companion
 * reloaded after a restart is refused when its chunk loads.</p>
 */
public final class CompanionService {
    public static final String TAG = "rotas_companion";

    private record Hire(UUID entity, long until) {
    }

    private static final Map<UUID, Hire> HIRES = new HashMap<>();

    private CompanionService() {
    }

    public static void clear() {
        HIRES.clear();
    }

    private static SeasonRules.NpcSocialRules rules(RotasData data) {
        return SeasonService.rules(data).npcSocial;
    }

    public static boolean hasCompanion(ServerPlayer player) {
        return HIRES.containsKey(player.getUUID());
    }

    /** Spawns the companion beside the player. Null on success, otherwise why not. */
    public static String hire(ServerPlayer player, RotasData data) {
        SeasonRules.NpcSocialRules rules = rules(data);
        if (!rules.companions) return "ตอนนี้ไม่มีผู้คุ้มกันให้จ้าง";
        if (hasCompanion(player)) return "ท่านมีผู้คุ้มกันอยู่แล้ว";
        EntityType<?> type = SpawnPlacer.mobType(rules.companionEntity);
        ServerLevel level = player.serverLevel();
        BlockPos pos = type == null ? null : SpawnPlacer.find(level, player.blockPosition(), 1, 3, type, level.random, null);
        if (pos == null) pos = player.blockPosition();
        Mob mob = type == null ? null : SpawnPlacer.create(level, type, pos, level.random);
        if (mob == null) return "ผู้คุ้มกันมาไม่ได้";
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        if (mob instanceof IronGolem golem) golem.setPlayerCreated(true);
        int playerLevel = data.progress(player.getUUID()).level();
        double scale = Math.max(0, playerLevel - 1) * rules.companionPerLevel;
        if (scale > 0) {
            var health = mob.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) health.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "Rotas companion", scale,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
            var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "Rotas companion", scale,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        mob.setCustomName(Component.literal("ผู้คุ้มกันของ " + player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA));
        mob.setCustomNameVisible(true);
        // Registered before it joins the world, so the chunk-load guard lets this one in.
        HIRES.put(player.getUUID(), new Hire(mob.getUUID(), System.currentTimeMillis() + rules.companionMinutes * 60_000L));
        if (!level.addFreshEntity(mob)) {
            HIRES.remove(player.getUUID());
            return "ผู้คุ้มกันมาไม่ได้";
        }
        level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
        return null;
    }

    /** Once a second: follow, fight for the employer, and end contracts. */
    public static void tick(MinecraftServer server) {
        if (HIRES.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Hire>> iterator = HIRES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Hire> entry = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            Entity entity = find(server, entry.getValue().entity());
            if (owner == null || !(entity instanceof Mob mob) || !mob.isAlive() || now >= entry.getValue().until()) {
                if (entity != null) dismiss(entity);
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("ผู้คุ้มกันของท่านหมดสัญญาและกลับไปแล้ว").withStyle(ChatFormatting.GRAY));
                }
                iterator.remove();
                continue;
            }
            if (mob.level() != owner.level() || mob.distanceToSqr(owner) > 24 * 24) {
                BlockPos near = SpawnPlacer.find(owner.serverLevel(), owner.blockPosition(), 1, 3, mob.getType(), owner.getRandom(), null);
                if (near != null && mob.level() == owner.level()) {
                    mob.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
                }
            } else if (mob.getTarget() == null && mob.distanceToSqr(owner) > 6 * 6) {
                mob.getNavigation().moveTo(owner, 1.0);
            }
            LivingEntity threat = owner.getLastHurtByMob() != null ? owner.getLastHurtByMob() : owner.getLastHurtMob();
            if (threat != null && threat.isAlive() && threat != mob && !(threat instanceof ServerPlayer)
                    && (threat instanceof Enemy || threat == owner.getLastHurtByMob()) && threat.distanceToSqr(mob) < 32 * 32) {
                mob.setTarget(threat);
            }
        }
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void dismiss(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
        }
        entity.discard();
    }

    /** A companion that falls just leaves: no drops, no death screen spam. */
    public static EventResult onDeath(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.getTags().contains(TAG)) return EventResult.pass();
        dismiss(entity);
        return EventResult.interruptFalse();
    }

    /** Companions never attack players, and players never hurt their own. */
    public static boolean protectedFrom(Entity victim, Entity attacker) {
        return victim.getTags().contains(TAG) && attacker instanceof ServerPlayer player
                && HIRES.containsKey(player.getUUID()) && HIRES.get(player.getUUID()).entity().equals(victim.getUUID());
    }

    /** Refuses a companion that reloads with its chunk after its contract is gone (a restart, a crash). */
    public static EventResult onAdd(Entity entity, Level level) {
        if (level.isClientSide || !entity.getTags().contains(TAG)) return EventResult.pass();
        for (Hire hire : HIRES.values()) if (hire.entity().equals(entity.getUUID())) return EventResult.pass();
        return EventResult.interruptFalse();
    }
}
