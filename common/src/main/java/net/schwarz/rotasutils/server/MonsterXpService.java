package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.schwarz.rotasutils.level.LevelConfig;

/**
 * Calculates Rotas XP from the monster that was actually fought.
 *
 * <p>This intentionally does not use vanilla's {@code xpReward}. Modded bosses frequently
 * keep a small vanilla orb reward even when their health, armour and damage are orders of
 * magnitude above vanilla mobs. Reading live combat attributes makes the system work with
 * unknown monster mods without hard-coded integrations, while exact entity overrides remain
 * available for scripted or mechanically unusual bosses.</p>
 */
public final class MonsterXpService {
    private MonsterXpService() {
    }

    /** True when this entity belongs in combat progression rather than farming/animal XP. */
    public static boolean isProgressionMonster(LivingEntity entity, boolean boss) {
        return boss
                || entity instanceof Enemy
                || entity.getType().getCategory() == MobCategory.MONSTER
                || entity.getTags().contains("rotasutils_progression_monster");
    }

    /**
     * Returns the base Rotas XP for a kill before the XP-source multiplier is applied.
     * A return value of zero means the entity is not a progression monster.
     */
    public static long calculate(LivingEntity entity, LevelConfig config, boolean boss) {
        if (!isProgressionMonster(entity, boss)) {
            return 0;
        }

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String entityId = id == null ? "" : id.toString();
        int override = config.monsterXpOverride(entityId);
        if (override >= 0) {
            return clamp(override, config.minimumMonsterXp(), config.maximumMonsterXp());
        }

        // Read through getAttribute rather than getAttributeValue: monsters without a
        // registered attribute (a ghast has no ATTACK_DAMAGE, for example) would otherwise
        // throw inside the kill handler. A missing attribute simply scores zero threat.
        double health = attribute(entity, Attributes.MAX_HEALTH);
        double damage = attribute(entity, Attributes.ATTACK_DAMAGE);
        double armor = attribute(entity, Attributes.ARMOR);
        double toughness = attribute(entity, Attributes.ARMOR_TOUGHNESS);
        double speed = attribute(entity, Attributes.MOVEMENT_SPEED);
        double knockbackResistance = attribute(entity, Attributes.KNOCKBACK_RESISTANCE);

        // Damage is weighted heavily by default because short, lethal encounters are as dangerous as
        // large health pools - but every weight and health tier is configurable, since the right
        // balance depends on how tanky the pack's mobs are relative to vanilla.
        double threat = config.monsterXpWeights()
                .threat(health, damage, armor, toughness, speed, knockbackResistance);

        if (id != null && !"minecraft".equals(id.getNamespace())) {
            threat *= config.moddedMobXpMultiplier();
        }
        if (boss) {
            threat *= config.bossXpMultiplier();
        }

        long xp = Math.round(threat * config.monsterXpScale());
        return clamp(xp, config.minimumMonsterXp(), config.maximumMonsterXp());
    }

    private static double attribute(LivingEntity entity,
                                    net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance == null ? 0.0 : sane(instance.getValue());
    }

    private static double sane(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static long clamp(long value, int minimum, int maximum) {
        return Math.max(minimum, Math.min((long) maximum, value));
    }
}
