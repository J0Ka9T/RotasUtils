package net.schwarz.rotasutils.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logical combat values from character stats: defense, evasion and magic attack. They are rebuilt with the
 * attribute modifiers in {@link CharacterStatService#apply} and read by the damage mixin.
 *
 * <p>The damage hook also carries the combat balance from {@link SeasonRules}: against monsters a player's
 * level cancels part of the monster level curve (level parity), and between players the stat bonus, dodge
 * chance, level gap and single-hit burst are all bounded so PvP is not decided by one stacked stat.</p>
 */
public final class CombatStats {
    public static final String DEFENSE = "rotas:defense";
    public static final String EVASION = "rotas:evasion";
    public static final String MAGIC_ATTACK = "rotas:magic_attack";
    /** Percent bonus to magic damage (0.5 = +50%); the magic counterpart of STR's percent attack. */
    public static final String MAGIC_POWER = "rotas:magic_power";

    public record Values(double defense, double evasion, double magicAttack, double magicPower) {
        public static final Values NONE = new Values(0, 0, 0, 0);
    }

    private static final Map<UUID, Values> VALUES = new ConcurrentHashMap<>();

    private CombatStats() {
    }

    public static boolean logical(String attribute) {
        return DEFENSE.equals(attribute) || EVASION.equals(attribute) || MAGIC_ATTACK.equals(attribute)
                || MAGIC_POWER.equals(attribute);
    }

    public static void set(UUID player, Values values) {
        if (values.defense() <= 0 && values.evasion() <= 0 && values.magicAttack() <= 0
                && values.magicPower() <= 0) {
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

    /** Rolls evasion against a hit from a living attacker; environmental damage cannot be dodged. */
    public static boolean evade(ServerPlayer player, DamageSource source) {
        Values values = VALUES.get(player.getUUID());
        if (values == null || values.evasion() <= 0 || !(source.getEntity() instanceof LivingEntity)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || player.isInvulnerable()) {
            return false;
        }
        double chance = values.evasion();
        // A dodge roll decides a player duel far more than a monster fight, so it counts for less there.
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
            // Refinement is flat attack on the weapon that struck, so it lands before every multiplier
            // and is scaled by the PvP and level-parity rules exactly like the weapon's own damage.
            result += refineAttack(rules, attacker, source);
            if (defender == null) {
                // A weapon's memory is of monsters, so it only counts against them.
                result *= WeaponMemoryService.damageMultiplier(rules, attacker, source, victim);
            }
            result *= RuneService.damageMultiplier(attacker, source, victim);
        }
        if (attacker != null && magic(source)) {
            Values values = get(attacker.getUUID());
            // Percent first, so it scales the spell rather than the flat bonus sitting on top of it.
            result *= 1.0 + Math.max(0, values.magicPower());
            double bonus = values.magicAttack();
            // A flat bonus on every hit made fast multi-hit spells outscale everything; bound it by the hit.
            result += rules == null ? bonus : SeasonMath.capFlatBonus(result, bonus, rules.magicBonusMaxRatio);
        }
        if (rules != null) {
            result *= pvp ? pvpMultiplier(data, rules, attacker, defender, source)
                    : pveMultiplier(data, rules, attacker, defender, victim, source);
        }
        if (defender != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            // Worn armour is read here rather than cached, so a piece swapped mid-fight counts at once.
            double defense = get(defender.getUUID()).defense() + refineDefense(rules, defender);
            if (defense > 0) {
                double scale = rules == null ? 100 : rules.defenseScale;
                result *= SeasonMath.defenseMultiplier(defense, scale);
            }
        }
        if (pvp) {
            // No single player hit may take more than a set share of the victim's health.
            result = SeasonMath.capHit(result, defender.getMaxHealth(), rules == null ? 0 : rules.pvpMaxHitShare);
        }
        return (float) Math.max(0, result);
    }

    /**
     * Flat attack the attacker's refined weapon adds to this hit. Only the weapon that actually struck
     * counts: a melee swing, or a projectile fired from the weapon in hand.
     */
    private static double refineAttack(SeasonRules rules, ServerPlayer attacker, DamageSource source) {
        boolean melee = source.getDirectEntity() == attacker;
        boolean shot = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
        if (!melee && !shot) {
            return 0;
        }
        return net.schwarz.rotasutils.item.ItemRefine.attackBonus(rules.refine, attacker.getMainHandItem());
    }

    /** Defence the defender's refined armour adds, on the same channel Vitality feeds. */
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

    /** Only part of a stacked attack build counts, fights run longer, and a far higher level hits for less. */
    private static double pvpMultiplier(RotasData data, SeasonRules rules, ServerPlayer attacker, ServerPlayer defender,
                                        DamageSource source) {
        double multiplier = rules.pvpDamageMultiplier;
        // The stat bonus is a multiplier on melee attack damage; projectiles and spells never carried it.
        if (source.getDirectEntity() == attacker) {
            multiplier *= SeasonMath.statBonusScale(CharacterStatService.statAttackBonus(attacker), rules.pvpStatEfficiency);
        }
        multiplier *= SeasonMath.levelGapMultiplier(level(data, attacker), level(data, defender),
                rules.pvpLevelGrace, rules.pvpLevelGapPerLevel, rules.pvpLevelGapMax);
        return multiplier;
    }

    /**
     * Level parity against leveled monsters. A player hitting a monster deals more, and a monster hitting a
     * player deals less, by the share of the monster curve both levels have in common.
     */
    private static double pveMultiplier(RotasData data, SeasonRules rules, ServerPlayer attacker, ServerPlayer defender,
                                        LivingEntity victim, DamageSource source) {
        if (rules.pveLevelParity <= 0) {
            return 1.0;
        }
        var curve = data.levelConfig().mobLevel();
        if (attacker != null && defender == null) {
            MonsterState monster = MonsterService.state(victim);
            if (monster != null) {
                return SeasonMath.levelParity(level(data, attacker), monster.level(), curve.healthPerLevel(), rules.pveLevelParity);
            }
        } else if (defender != null && attacker == null && source.getEntity() instanceof LivingEntity mob) {
            MonsterState monster = MonsterService.state(mob);
            if (monster != null) {
                return 1.0 / SeasonMath.levelParity(level(data, defender), monster.level(), curve.damagePerLevel(), rules.pveLevelParity);
            }
        }
        return 1.0;
    }

    private static int level(RotasData data, ServerPlayer player) {
        var progress = data.peek(player.getUUID());
        return progress == null ? 1 : progress.level();
    }

    private static boolean magic(DamageSource source) {
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return true;
        }
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("irons_spellbooks") || key.location().getPath().contains("magic"))
                .orElse(false);
    }
}
