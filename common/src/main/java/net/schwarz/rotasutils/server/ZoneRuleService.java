package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.core.ResolvedZoneRules;
import net.schwarz.rotasutils.core.ZoneRuleResolver;
import net.schwarz.rotasutils.data.RotasData;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ZoneRuleService {
    private static final Set<UUID> KEPT = ConcurrentHashMap.newKeySet();

    private ZoneRuleService() {
    }

    public static ResolvedZoneRules rulesAt(LivingEntity entity) {
        RotasData data = RotasData.instance();
        if (data == null || data.zones().isEmpty() || entity.level().isClientSide()) {
            return null;
        }
        return ZoneRuleResolver.resolve(data.zones().values(), entity.level().dimension().location().toString(),
                entity.getX(), entity.getY(), entity.getZ());
    }

    public static boolean blocksPvp(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer) || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
            return false;
        }
        return pvpBlocked(rulesAt(victim), rulesAt(attacker));
    }

    public static float scaleDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0 || !Float.isFinite(amount) || victim.level().isClientSide()) {
            return amount;
        }
        ResolvedZoneRules victimRules = victim instanceof ServerPlayer ? rulesAt(victim) : null;
        ResolvedZoneRules attackerRules = source.getEntity() instanceof ServerPlayer attacker ? rulesAt(attacker) : null;
        return scaled(amount, victimRules, attackerRules);
    }

    public static float scaleHealing(LivingEntity entity, float amount) {
        if (!(entity instanceof ServerPlayer) || amount <= 0 || !Float.isFinite(amount)) {
            return amount;
        }
        float scaled = (float) (amount * WorldEventService.healingMultiplier(entity));
        ResolvedZoneRules rules = rulesAt(entity);
        return rules == null ? scaled : (float) (scaled * rules.healingMultiplier().orElse(1.0));
    }

    public static boolean keepInventoryAtDeath(ServerPlayer player) {
        ResolvedZoneRules rules = rulesAt(player);
        if (rules == null || !rules.keepInventory().orElse(false)) {
            return false;
        }
        KEPT.add(player.getUUID());
        return true;
    }

    public static boolean kept(UUID player) {
        return KEPT.contains(player);
    }

    public static boolean consumeKept(UUID player) {
        return KEPT.remove(player);
    }

    public static void forget(UUID player) {
        KEPT.remove(player);
    }

    public static void clear() {
        KEPT.clear();
    }

    public static void applyXpLoss(ServerPlayer player, RotasData data) {
        ResolvedZoneRules rules = rulesAt(player);
        if (rules == null || rules.rotasXpLossPercentage().isEmpty()) {
            return;
        }
        var progress = data.progress(player.getUUID());
        long after = xpAfterLoss(progress.xp(), rules.rotasXpLossPercentage().getAsDouble());
        if (after != progress.xp()) {
            progress.setXp(after);
        }
    }

static boolean pvpBlocked(ResolvedZoneRules victim, ResolvedZoneRules attacker) {
        return (victim != null && victim.pvpEnabled().isPresent() && !victim.pvpEnabled().get())
                || (attacker != null && attacker.pvpEnabled().isPresent() && !attacker.pvpEnabled().get());
    }

    static float scaled(float amount, ResolvedZoneRules victim, ResolvedZoneRules attacker) {
        double result = amount;
        if (victim != null) {
            result *= victim.playerDamageTakenMultiplier().orElse(1.0);
        }
        if (attacker != null) {
            result *= attacker.playerDamageDealtMultiplier().orElse(1.0);
        }
        return (float) Math.max(0, Math.min(Float.MAX_VALUE, result));
    }

    static long xpAfterLoss(long xp, double percent) {
        double kept = Math.max(0, Math.min(100, 100 - percent)) / 100.0;
        return Math.max(0, Math.round(Math.max(0, xp) * kept));
    }
}
