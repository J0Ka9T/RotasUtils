package net.schwarz.rotasutils.server.horse;

import dev.architectury.event.EventResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.schwarz.rotasutils.core.HorseTrait;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.XpSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HorseTraitEffects {
    private static final UUID HORSE_ARMOR = UUID.fromString("7b8f2a52-3d6e-4f0a-9d1c-6a1e0c9b4a01");
    private static final UUID HORSE_SPEED = UUID.fromString("7b8f2a52-3d6e-4f0a-9d1c-6a1e0c9b4a02");
    private static final UUID RIDER_DAMAGE = UUID.fromString("7b8f2a52-3d6e-4f0a-9d1c-6a1e0c9b4a03");
    private static final UUID RIDER_ARMOR = UUID.fromString("7b8f2a52-3d6e-4f0a-9d1c-6a1e0c9b4a04");

    private static final Map<UUID, List<HorseTrait>> RIDING = new HashMap<>();

    private HorseTraitEffects() {
    }

    static void tick(MinecraftServer server, StableData stables, SeasonRules.HorseRules rules) {
        RIDING.clear();
        for (StableData.Horse horse : stables.all()) {
            if (horse.active == null) continue;
            Entity entity = null;
            for (ServerLevel level : server.getAllLevels()) {
                entity = level.getEntity(horse.active);
                if (entity != null) break;
            }
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            List<HorseTrait> traits = horse.traits;
            set(living, Attributes.ARMOR, HORSE_ARMOR, "Rotas ironhide",
                    traits.contains(HorseTrait.IRONHIDE) ? rules.ironhideArmor : 0, AttributeModifier.Operation.ADDITION);
            set(living, Attributes.MOVEMENT_SPEED, HORSE_SPEED, "Rotas windrunner",
                    traits.contains(HorseTrait.WINDRUNNER) ? rules.windrunnerSpeed : 0, AttributeModifier.Operation.MULTIPLY_BASE);
            if (traits.contains(HorseTrait.STARBORN) && living.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD, living.getX(), living.getY() + 1.2, living.getZ(),
                        3, 0.5, 0.6, 0.5, 0.01);
            }
            if (living.getFirstPassenger() instanceof ServerPlayer rider) RIDING.put(rider.getUUID(), traits);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<HorseTrait> traits = RIDING.getOrDefault(player.getUUID(), List.of());
            set(player, Attributes.ATTACK_DAMAGE, RIDER_DAMAGE, "Rotas warhorse",
                    traits.contains(HorseTrait.WARHORSE) ? rules.warhorseDamage : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            set(player, Attributes.ARMOR, RIDER_ARMOR, "Rotas valiant",
                    traits.contains(HorseTrait.VALIANT) ? rules.valiantArmor : 0, AttributeModifier.Operation.ADDITION);
        }
    }

    private static void set(LivingEntity entity, Attribute attribute, UUID id, String name, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && existing.getAmount() == amount && existing.getOperation() == operation) return;
        if (existing != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    public static double xpMultiplier(ServerPlayer player, XpSource source) {
        List<HorseTrait> traits = RIDING.get(player.getUUID());
        if (traits == null || traits.isEmpty()) return 1;
        SeasonRules.HorseRules rules = HorseService.rules(RotasData.get(player.server));
        double bonus = traits.contains(HorseTrait.STARBORN) ? rules.starbornXp : 0;
        switch (source) {
            case MINING, FARMING, FISHING, CRAFTING, SMELTING -> {
                if (traits.contains(HorseTrait.FORAGER)) bonus += rules.foragerXp;
            }
            case DISCOVERY, ADVANCEMENT -> {
                if (traits.contains(HorseTrait.TRAILBLAZER)) bonus += rules.trailblazerXp;
            }
            case TRADING -> {
                if (traits.contains(HorseTrait.PEDDLER)) bonus += rules.peddlerXp;
            }
            default -> {
            }
        }
        return 1 + bonus;
    }

    public static EventResult onHurt(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide || !source.is(DamageTypeTags.IS_FALL)) return EventResult.pass();
        List<HorseTrait> traits = RIDING.get(entity.getUUID());
        if (traits == null && entity.getServer() != null && SwemCompat.isHorse(entity)) {
            StableData.Horse horse = StableData.get(entity.getServer()).byActive(entity.getUUID());
            traits = horse == null ? null : horse.traits;
        }
        return traits != null && traits.contains(HorseTrait.SUREFOOT) ? EventResult.interruptFalse() : EventResult.pass();
    }
}
