package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.schwarz.rotasutils.Rotasutils;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

final class FixedDamage {
    static final float TARGET_RATIO = 0.01f;
    static final float ATTACK_MULTIPLIER = 8f;

    static final float BONUS_MULTIPLIER = 1f;

    private static final String[] PERCENT_TYPES = {
            "yesman.epicfight.world.damagesource.ExtraDamageInstance#EVISCERATE_LOST_HEALTH",
            "com.hm.efn.gameasset.EFNExtraDamageInstance#LOST_HEALTH_DAMAGE_WITH_SCALING_CAP",
            "com.hm.efn.gameasset.EFNExtraDamageInstance#MAX_HEALTH_PERCENTAGE_DAMAGE",
            "net.corruptdog.cdm.world.damagesources.EFRExtraDamageInstance#EFR_TARGET_MAX_HEALTH",
            "net.corruptdog.cdm.world.damagesources.EFRExtraDamageInstance#EFR_TARGET_CURRENT_HEALTH",
            "reascer.wom.world.damagesources.WOMExtraDamageInstance#WOM_TARGET_MAX_HEALTH",
            "reascer.wom.world.damagesources.WOMExtraDamageInstance#WOM_TARGET_MISSING_HEALTH",
            "reascer.wom.world.damagesources.WOMExtraDamageInstance#WOM_TARGET_CURRENT_HEALTH"};

    private FixedDamage() {
    }

    static void install() {
        int wrapped = 0;
        for (String entry : PERCENT_TYPES) {
            try {
                String[] part = entry.split("#");
                Object type = Class.forName(part[0]).getField(part[1]).get(null);
                java.lang.reflect.Field function = type.getClass().getDeclaredField("extraDamage");
                function.setAccessible(true);
                var original = (yesman.epicfight.world.damagesource.ExtraDamageInstance.ExtraDamageFunction) function.get(type);
                function.set(type, (yesman.epicfight.world.damagesource.ExtraDamageInstance.ExtraDamageFunction)
                        (attacker, weapon, target, base, params) ->
                                Math.min(original.getBonusDamage(attacker, weapon, target, base, params), Math.max(0, base) * BONUS_MULTIPLIER));
                wrapped++;
            } catch (ClassNotFoundException | NoSuchFieldException absent) {
            } catch (ReflectiveOperationException | RuntimeException failed) {
                Rotasutils.LOG.warn("Could not fix Epic Fight bonus damage {}: {}", entry, failed.toString());
            }
        }
        Rotasutils.LOG.info("RotasUtils fixed {} Epic Fight health-percent damage type(s)", wrapped);
    }

    static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide()) {
            return;
        }
        if (ours(event.getSource().getDirectEntity()) || EpicFightCapabilities.getEntityPatch(attacker, LivingEntityPatch.class) == null) {
            return;
        }
        float cap = (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * ATTACK_MULTIPLIER);
        float amount = event.getAmount();
        if (cap > 0 && amount > cap && amount > event.getEntity().getMaxHealth() * TARGET_RATIO) {
            event.setAmount(cap);
        }
    }

    private static boolean ours(Entity direct) {
        return direct != null && Rotasutils.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace());
    }
}
