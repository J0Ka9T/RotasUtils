package net.schwarz.rotasutils.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatStats {
    public static final String DEFENSE = "rotas:defense";
    public static final String EVASION = "rotas:evasion";
    public static final String MAGIC_ATTACK = "rotas:magic_attack";
    public static final String MAGIC_POWER = "rotas:magic_power";

    public static final String CRIT_CHANCE = "rotas:crit_chance";
    public static final String CRIT_DAMAGE = "rotas:crit_damage";
    public static final String REGEN = "rotas:regen";

    public static final String ARMOR_PEN = "rotas:armor_pen";
    public static final String COOLDOWN_REDUCTION = "rotas:cooldown_reduction";
    public static final String DROP_RATE = "rotas:drop_rate";
    public static final String LIFE_STEAL = "rotas:life_steal";
    public static final String DAMAGE_REDUCTION = "rotas:damage_reduction";
    public static final String STAMINA_REGEN = "rotas:stamina_regen";

    public static final double CRIT_BASE = 1.5;

    public static final double DEFAULT_MAX_DODGE = 0.50;
    public static final double DEFAULT_MAX_CRIT_CHANCE = 0.60;
    public static final double DEFAULT_MAX_CRIT_DAMAGE = 3.00;
    public static final double DEFAULT_MAX_REGEN = 0.05;
    public static final double DEFAULT_MAX_ARMOR_PEN = 0.40;
    public static final double DEFAULT_MAX_CDR = 0.35;
    public static final double DEFAULT_MAX_DROP_RATE = 1.00;
    public static final double DEFAULT_MAX_LIFE_STEAL = 0.10;
    public static final double DEFAULT_MAX_DAMAGE_REDUCTION = 0.30;
    public static final double DEFAULT_MAX_STAMINA_REGEN = 0.50;

    public static final double MAX_CRIT_CHANCE = DEFAULT_MAX_CRIT_CHANCE;
    public static final double MAX_CRIT_DAMAGE = DEFAULT_MAX_CRIT_DAMAGE;
    public static final double MAX_REGEN = DEFAULT_MAX_REGEN;

    public record Values(double defense, double evasion, double magicAttack, double magicPower,
                         double critChance, double critDamage, double regen,
                         double armorPen, double cooldownReduction, double dropRate,
                         double lifeSteal, double damageReduction, double staminaRegen) {
        public static final Values NONE = new Values(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static final Map<UUID, Values> VALUES = new ConcurrentHashMap<>();

    private CombatStats() {
    }

    public static boolean logical(String attribute) {
        return DEFENSE.equals(attribute) || EVASION.equals(attribute) || MAGIC_ATTACK.equals(attribute)
                || MAGIC_POWER.equals(attribute) || CRIT_CHANCE.equals(attribute) || CRIT_DAMAGE.equals(attribute)
                || REGEN.equals(attribute) || ARMOR_PEN.equals(attribute) || COOLDOWN_REDUCTION.equals(attribute)
                || DROP_RATE.equals(attribute) || LIFE_STEAL.equals(attribute)
                || DAMAGE_REDUCTION.equals(attribute) || STAMINA_REGEN.equals(attribute);
    }

    public static void set(UUID player, Values values) {
        if (values.defense() <= 0 && values.evasion() <= 0 && values.magicAttack() <= 0
                && values.magicPower() <= 0 && values.critChance() <= 0 && values.critDamage() <= 0
                && values.regen() <= 0 && values.armorPen() <= 0 && values.cooldownReduction() <= 0
                && values.dropRate() <= 0 && values.lifeSteal() <= 0 && values.damageReduction() <= 0
                && values.staminaRegen() <= 0) {
            VALUES.remove(player);
        } else {
            VALUES.put(player, values);
        }
    }

    public static Values get(UUID player) {
        return VALUES.getOrDefault(player, Values.NONE);
    }

    public static void forget(UUID player) {
        VALUES.remove(player);
    }

    public static void clear() {
        VALUES.clear();
    }

    public static boolean evade(ServerPlayer player, DamageSource source) {
        Values values = VALUES.get(player.getUUID());
        if (values == null || values.evasion() <= 0 || !(source.getEntity() instanceof LivingEntity)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || player.isInvulnerable()) {
            return false;
        }
        double chance = values.evasion();
        RotasData data = RotasData.instance();
        if (data != null && source.getEntity() instanceof ServerPlayer attacker && attacker != player) {
            chance *= SeasonService.rules(data).pvpEvasionScale;
        }
        if (player.getRandom().nextDouble() >= chance) {
            return false;
        }
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(),
                6, 0.3, 0.4, 0.3, 0.02);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || amount <= 0 || !Float.isFinite(amount)) {
            return amount;
        }
        RotasData data = RotasData.instance();
        SeasonRules rules = data == null ? null : SeasonService.rules(data);
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player ? player : null;
        ServerPlayer defender = victim instanceof ServerPlayer player ? player : null;
        boolean pvp = attacker != null && defender != null && attacker != defender;

        double result = amount;
        if (attacker != null && defender == null && data != null) {
            var record = data.peek(attacker.getUUID());
            if (record != null) {
                result *= BestiaryService.damage(data, record,
                        String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType())));
            }
        }
        if (attacker != null && rules != null && !magic(source)) {
            result += refineAttack(rules, attacker, source);
            if (defender == null) {
                result *= WeaponMemoryService.damageMultiplier(rules, attacker, source, victim);
            }
            result *= RuneService.damageMultiplier(attacker, source, victim);
        }
        if (attacker != null && magic(source)) {
            Values values = get(attacker.getUUID());
            result *= 1.0 + Math.max(0, values.magicPower());
            double bonus = values.magicAttack();
            result += rules == null ? bonus : SeasonMath.capFlatBonus(result, bonus, rules.magicBonusMaxRatio);
        }
        if (attacker != null && !magic(source) && (source.getDirectEntity() == attacker
                || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile)) {
            result *= critMultiplier(attacker, victim);
        }
        if (rules != null) {
            if (pvp) {
                result *= pvpMultiplier(rules, attacker, source);
            }
        }
        if (attacker != null) {
            Values attackerValues = get(attacker.getUUID());
            double pen = attackerValues.armorPen();
            if (pen > 0 && victim.getArmorValue() > 0 && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
                result *= 1.0 + (pen * Math.min(0.8, victim.getArmorValue() / 25.0));
            }
        }
        if (defender != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            double defense = get(defender.getUUID()).defense() + refineDefense(rules, defender);
            if (attacker != null) {
                double pen = get(attacker.getUUID()).armorPen();
                if (pen > 0) {
                    defense = Math.max(0, defense * (1.0 - Math.min(1.0, pen)));
                }
            }
            if (defense > 0) {
                double scale = rules == null ? 100 : rules.defenseScale;
                result *= SeasonMath.defenseMultiplier(defense, scale);
            }
            double reduction = get(defender.getUUID()).damageReduction();
            if (reduction > 0) {
                result *= Math.max(0, 1.0 - Math.min(1.0, reduction));
            }
        }
        if (pvp) {
            result = SeasonMath.capHit(result, defender.getMaxHealth(), rules == null ? 0 : rules.pvpMaxHitShare);
        }
        return (float) Math.max(0, result);
    }

    public static void onHitLanded(ServerPlayer attacker, LivingEntity victim, DamageSource source, float damageDealt) {
        if (attacker == null || victim == null || damageDealt <= 0 || !attacker.isAlive()) return;
        Values values = get(attacker.getUUID());
        if (values.lifeSteal() > 0 && !magic(source)) {
            float healAmount = (float) (damageDealt * values.lifeSteal());
            healAmount = Math.min(healAmount, (float) (attacker.getMaxHealth() * 0.10));
            if (healAmount > 0) {
                attacker.heal(healAmount);
                if (attacker.serverLevel() != null) {
                    attacker.serverLevel().sendParticles(ParticleTypes.HEART,
                            attacker.getX(), attacker.getY() + 1.2, attacker.getZ(),
                            2, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
    }

    public static int scaleCooldown(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) return ticks;
        double cdr = get(player.getUUID()).cooldownReduction();
        return cdr > 0 ? (int) Math.max(1, Math.round(ticks * (1.0 - Math.min(1.0, cdr)))) : ticks;
    }

    private static double critMultiplier(ServerPlayer attacker, LivingEntity victim) {
        Values values = get(attacker.getUUID());
        if (values.critChance() <= 0 || attacker.getRandom().nextDouble() >= values.critChance()) {
            return 1.0;
        }
        if (victim.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
        return CRIT_BASE + values.critDamage();
    }

    public static void regenTick(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Values values = VALUES.get(player.getUUID());
            if (values == null || values.regen() <= 0 || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
                continue;
            }
            player.heal((float) (player.getMaxHealth() * values.regen()));
        }
    }

    private static double refineAttack(SeasonRules rules, ServerPlayer attacker, DamageSource source) {
        boolean melee = source.getDirectEntity() == attacker;
        boolean shot = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
        if (!melee && !shot) {
            return 0;
        }
        return net.schwarz.rotasutils.item.ItemRefine.attackBonus(rules.refine, attacker.getMainHandItem());
    }

    private static double refineDefense(SeasonRules rules, ServerPlayer defender) {
        if (rules == null || rules.refine == null || !rules.refine.enabled) {
            return 0;
        }
        double total = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) {
                total += net.schwarz.rotasutils.item.ItemRefine.defenseBonus(rules.refine, defender.getItemBySlot(slot));
            }
        }
        return total;
    }

    private static double pvpMultiplier(SeasonRules rules, ServerPlayer attacker, DamageSource source) {
        double multiplier = rules.pvpDamageMultiplier;
        if (source.getDirectEntity() == attacker) {
            multiplier *= SeasonMath.statBonusScale(CharacterStatService.statAttackBonus(attacker), rules.pvpStatEfficiency);
        }
        return multiplier;
    }

    static boolean magic(DamageSource source) {
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return true;
        }
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("irons_spellbooks") || key.location().getPath().contains("magic"))
                .orElse(false);
    }
}
