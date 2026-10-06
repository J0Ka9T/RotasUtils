package net.schwarz.rotasutils.server;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.core.MobAffix;
import net.schwarz.rotasutils.core.MonsterRank;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class MobAffixService {
    public static final String CALLED_TAG = "rotas_affix_called";
    public static final String MINION_TAG = "rotas_minion";

    private MobAffixService() {
    }

    private static SeasonRules.FarmingRules rules(LivingEntity entity) {
        RotasData data = entity.getServer() == null ? null : RotasData.get(entity.getServer());
        return data == null ? null : SeasonService.rules(data).farming;
    }

    public static List<MobAffix> roll(Mob mob, MonsterRank rank, int level, SeasonRules.FarmingRules farming) {
        if (farming == null || !farming.affixesEnabled || mob.getTags().contains(MINION_TAG)) {
            return List.of();
        }
        int count = switch (rank) {
            case ELITE -> farming.eliteAffixes;
            case CHAMPION -> farming.championAffixes;
            default -> 0;
        };
        List<MobAffix> pool = new ArrayList<>();
        for (MobAffix affix : MobAffix.values()) {
            if (level < affix.minLevel()) continue;
            if (mob instanceof Creeper && (affix == MobAffix.VOLATILE || affix == MobAffix.SUMMONER)) continue;
            pool.add(affix);
        }
        EnumSet<MobAffix> chosen = EnumSet.noneOf(MobAffix.class);
        while (chosen.size() < count && !pool.isEmpty()) {
            chosen.add(pool.remove(mob.getRandom().nextInt(pool.size())));
        }
        return List.copyOf(chosen);
    }

    private static List<MobAffix> affixes(LivingEntity entity) {
        if (!(entity instanceof Mob)) return List.of();
        MonsterState state = MonsterService.state(entity);
        return state == null ? List.of() : MobAffix.of(state.affixes());
    }

    private static boolean melee(DamageSource source, LivingEntity attacker) {
        return source.getDirectEntity() == attacker && !source.is(DamageTypeTags.IS_PROJECTILE);
    }

public static float modifyIncoming(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || !(source.getEntity() instanceof Mob attacker)) {
            return amount;
        }
        MonsterState state = MonsterService.state(attacker);
        if (state == null) {
            return amount;
        }
        if (MobAffix.of(state.affixes()).contains(MobAffix.BERSERK)
                && attacker.getHealth() < attacker.getMaxHealth() / 3f) {
            amount *= 1.6f;
        }
        MonsterRank rank = state.rank() == null ? MonsterRank.NORMAL : state.rank();
        SeasonRules.FarmingRules farming = rules(victim);
        boolean promoted = rank == MonsterRank.VETERAN || rank == MonsterRank.ELITE || rank == MonsterRank.CHAMPION;
        if (victim instanceof ServerPlayer && promoted && farming != null && farming.rankedHitCap > 0) {
            amount = Math.min(amount, (float) (victim.getMaxHealth() * farming.rankedHitCap));
        }
        return amount;
    }

    public static void afterHurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || amount <= 0 || source.is(DamageTypes.THORNS)) {
            return;
        }
        if (source.getEntity() instanceof Mob attacker && melee(source, attacker)) {
            for (MobAffix affix : affixes(attacker)) {
                switch (affix) {
                    case VAMPIRIC -> attacker.heal(amount * 0.25f);
                    case VENOMOUS -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), attacker);
                    case FROSTBOUND -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), attacker);
                    default -> { }
                }
            }
        }
        List<MobAffix> own = affixes(victim);
        if (own.isEmpty()) {
            return;
        }
        if (own.contains(MobAffix.THORNED) && source.getEntity() instanceof LivingEntity attacker
                && melee(source, attacker) && attacker != victim) {
            attacker.hurt(victim.damageSources().thorns(victim), Math.max(0.5f, amount * 0.2f));
        }
        if (own.contains(MobAffix.SUMMONER) && victim instanceof Mob mob && victim.isAlive()
                && victim.getHealth() < victim.getMaxHealth() / 2f && !mob.getTags().contains(CALLED_TAG)) {
            mob.addTag(CALLED_TAG);
            callHelp(mob, source.getEntity() instanceof LivingEntity target ? target : null);
        }
    }

    private static void callHelp(Mob caller, LivingEntity target) {
        if (!(caller.level() instanceof ServerLevel level)) {
            return;
        }
        EntityType<?> type = caller.getType();
        for (int i = 0; i < 2; i++) {
            if (!(type.create(level) instanceof Mob helper)) {
                return;
            }
            double angle = caller.getRandom().nextDouble() * Math.PI * 2;
            helper.moveTo(caller.getX() + Math.cos(angle) * 2, caller.getY(), caller.getZ() + Math.sin(angle) * 2,
                    caller.getYRot(), 0);
            helper.addTag(MINION_TAG);
            helper.finalizeSpawn(level, level.getCurrentDifficultyAt(helper.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (target != null) {
                helper.setTarget(target);
            }
            level.addFreshEntity(helper);
            level.sendParticles(ParticleTypes.PORTAL, helper.getX(), helper.getY() + 1, helper.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
        }
    }

    public static void onDeath(LivingEntity entity) {
        if (entity.level().isClientSide || !affixes(entity).contains(MobAffix.VOLATILE)) {
            return;
        }
        entity.level().explode(entity, entity.getX(), entity.getY() + 0.5, entity.getZ(), 2.5f, Level.ExplosionInteraction.NONE);
    }

static void tick(Mob mob, MonsterState state, SeasonRules.FarmingRules farming) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
            return;
        }
        List<MobAffix> affixes = MobAffix.of(state.affixes());
        if (affixes.contains(MobAffix.REGENERATING) && mob.getHealth() < mob.getMaxHealth()
                && mob.tickCount - mob.getLastHurtByMobTimestamp() > 100) {
            mob.heal(mob.getMaxHealth() * 0.01f);
        }
        if (level.getNearestPlayer(mob, 32) == null) {
            return;
        }
        MonsterRank rank = state.rank() == null ? MonsterRank.NORMAL : state.rank();
        if (farming != null && farming.rankAura && (rank == MonsterRank.ELITE || rank == MonsterRank.CHAMPION)) {
            int rgb = rank.rgb();
            var dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f),
                    rank == MonsterRank.CHAMPION ? 1.3f : 0.9f);
            double width = mob.getBbWidth() * 0.6;
            level.sendParticles(dust, mob.getX(), mob.getY() + 0.15, mob.getZ(), rank == MonsterRank.CHAMPION ? 4 : 2,
                    width, 0.05, width, 0);
            if (rank == MonsterRank.CHAMPION && mob.getRandom().nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(),
                        1, width, 0.3, width, 0.01);
            }
        }
        if (affixes.contains(MobAffix.VOLATILE) && mob.getHealth() < mob.getMaxHealth() * 0.25f) {
            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
        }
    }
}
