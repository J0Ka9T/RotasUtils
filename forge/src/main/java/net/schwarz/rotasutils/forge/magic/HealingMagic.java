package net.schwarz.rotasutils.forge.magic;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.HealingRules;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.entity.CelestialFxEntity;
import java.util.*;

public final class HealingMagic {
    private HealingMagic() {}
    public enum Kind {
        MENDING_LIGHT("mending_light", "Mending Light", 12, 20),
        VERDANT_RENEWAL("verdant_renewal", "Verdant Renewal", 22, 30),
        SANCTUARY("healing_sanctuary", "Healing Sanctuary", 35, 55),
        CHAIN("mercy_chain", "Mercy Chain", 25, 45),
        CLEANSE("purifying_tide", "Purifying Tide", 28, 35),
        AEGIS("restorative_aegis", "Restorative Aegis", 35, 45),
        BLOOM("delayed_bloom", "Delayed Bloom", 20, 30),
        TRANSFER("life_offering", "Life Offering", 12, 15),
        TETHER("lifeline", "Lifeline", 30, 40),
        EMERGENCY("last_light", "Last Light", 45, 60);
        public final String id, title;
        final int cooldown, mana;
        Kind(String id, String title, int cooldown, int mana) {
            this.id = id; this.title = title; this.cooldown = cooldown; this.mana = mana;
        }
    }
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, Rotasutils.MOD_ID);
    public static void init(IEventBus bus) {
        for (Kind kind : Kind.values()) SPELLS.register(kind.id, () -> new HealingSpell(kind));
        SPELLS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(HealingMagic::tick);
        MinecraftForge.EVENT_BUS.addListener(HealingMagic::stop);
    }

    static final class HealingSpell extends CelestialSpells.Base {
        final Kind kind;
        HealingSpell(Kind kind) {
            super(kind.id, CastType.LONG, kind.ordinal() >= 5 ? SpellRarity.RARE : SpellRarity.UNCOMMON,
                    5, kind.cooldown, kind.mana, 4, 6, 2, kind == Kind.EMERGENCY ? 10 : 20);
            this.kind = kind;
        }
        @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.SELF_CAST_TWO_HANDS; }
        @Override public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("spell.rotasutils." + kind.id + ".mechanic", String.format(Locale.ROOT, "%.1f", HealingRules.power(getSpellPower(level, caster)))));
        }
        @Override public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
            if (world instanceof ServerLevel server && caster.isAlive()) {
                cast(server, caster, kind, HealingRules.power(getSpellPower(level, caster)));
            }
            super.onCast(world, level, caster, source, data);
        }
    }

    static boolean ally(LivingEntity caster, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target instanceof ArmorStand) return false;
        if (caster == target || caster.isAlliedTo(target) || target.isAlliedTo(caster)) return true;
        if (target instanceof TamableAnimal pet && caster.getUUID().equals(pet.getOwnerUUID())) return true;
        if (caster instanceof Player && target instanceof Player && RotasData.instance() != null) {
            var a = RotasData.instance().peek(caster.getUUID());
            var b = RotasData.instance().peek(target.getUUID());
            return a != null && b != null && a.partyId() != null && a.partyId().equals(b.partyId());
        }
        return false;
    }
    static List<LivingEntity> allies(ServerLevel world, LivingEntity caster, Vec3 at, double radius) {
        return world.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> ally(caster, e) && e.distanceToSqr(at) <= radius * radius && caster.hasLineOfSight(e));
    }
    static LivingEntity target(ServerLevel world, LivingEntity caster) {
        var hit = io.redspace.ironsspellbooks.api.util.Utils.raycastForEntity(world, caster, 16, true);
        if (hit instanceof net.minecraft.world.phys.EntityHitResult h && h.getEntity() instanceof LivingEntity e && ally(caster, e)) return e;
        return caster;
    }
    static void heal(LivingEntity target, float amount) {
        float accepted = HealingRules.emergency(target.getHealth(), target.getMaxHealth(), amount);
        if (accepted > 0 && target.isAlive()) target.heal(accepted);
    }
    static CelestialFxEntity visual(ServerLevel world, LivingEntity owner, Vec3 at, Kind kind, int duration, float radius, Vec3 end) {
        var fx = CelestialFxEntity.create(world, 20 + kind.ordinal(), owner, at, duration, radius);
        if (end != null) fx.target(end);
        world.addFreshEntity(fx);
        return fx;
    }

    static void cast(ServerLevel world, LivingEntity caster, Kind kind, float power) {
        LivingEntity chosen = target(world, caster);
        Vec3 anchor = CelestialSpells.Base.aim(world, caster, 12);
        switch (kind) {
            case MENDING_LIGHT -> { heal(chosen, power); visual(world, chosen, chosen.position(), kind, 28, 1, null); }
            case VERDANT_RENEWAL -> schedule(world, caster, chosen, kind, power, chosen.position(), 160, 20);
            case SANCTUARY -> schedule(world, caster, caster, kind, power, anchor, 120, 20);
            case CHAIN -> {
                var visited = new HashSet<UUID>();
                LivingEntity previous = caster, next = chosen;
                for (int hop = 0; hop < 4 && next != null; hop++) {
                    visited.add(next.getUUID());
                    heal(next, power * (float)Math.pow(0.8, hop));
                    visual(world, next, next.position(), kind, 24, 1, previous.getBoundingBox().getCenter());
                    previous = next;
                    var center = next.position();
                    next = allies(world, caster, center, 6).stream().filter(e -> !visited.contains(e.getUUID()))
                            .min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
                }
            }
            case CLEANSE -> {
                for (var e : allies(world, caster, caster.position(), 5)) {
                    for (var effect : List.copyOf(e.getActiveEffects())) if (!effect.getEffect().isBeneficial()) e.removeEffect(effect.getEffect());
                    e.clearFire(); heal(e, power * 0.5f);
                }
                visual(world, caster, caster.position(), kind, 32, 5, null);
            }
            case AEGIS -> {
                heal(chosen, power * 0.5f);
                chosen.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, Math.min(3, (int)(power / 8)), false, false, true));
                visual(world, chosen, chosen.position(), kind, 160, 1.3f, null);
            }
            case BLOOM -> schedule(world, caster, chosen, kind, power, chosen.position(), 60, 60);
            case TRANSFER -> {
                if (chosen != caster) {
                    float amount = HealingRules.transfer(caster.getHealth(), chosen.getHealth(), chosen.getMaxHealth(), power);
                    float before = chosen.getHealth(); heal(chosen, amount);
                    caster.setHealth(Math.max(1, caster.getHealth() - Math.min(amount, Math.max(0, chosen.getHealth() - before))));
                    visual(world, chosen, chosen.position(), kind, 32, 1, caster.getBoundingBox().getCenter());
                }
            }
            case TETHER -> schedule(world, caster, chosen, kind, power, chosen.position(), 100, 20);
            case EMERGENCY -> {
                for (var e : allies(world, caster, caster.position(), 6)) {
                    if (e.getHealth() <= e.getMaxHealth() * 0.35f) {
                        heal(e, power * 1.5f);
                        e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false, true));
                        visual(world, e, e.position(), kind, 36, 1.4f, null);
                    }
                }
            }
        }
        CelestialSpells.Base.sound(world, caster.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.8f + kind.ordinal() * 0.07f);
    }

    private record Job(ServerLevel world, UUID caster, UUID target, Kind kind, float power, Vec3 anchor, long start, int duration, int interval, int visualId) {}
    private static final List<Job> JOBS = new ArrayList<>();
    static void schedule(ServerLevel world, LivingEntity caster, LivingEntity target, Kind kind, float power, Vec3 anchor, int duration, int interval) {
        JOBS.removeIf(j -> {
            if (j.world != world || !j.caster.equals(caster.getUUID()) || j.kind != kind) return false;
            clearVisual(j); return true;
        });
        if (JOBS.size() >= 1024) return;
        var fx = visual(world, target, kind == Kind.SANCTUARY ? anchor : target.position(), kind, duration, kind == Kind.SANCTUARY ? 4 : 1, caster.getBoundingBox().getCenter());
        JOBS.add(new Job(world, caster.getUUID(), target.getUUID(), kind, power, anchor, world.getGameTime(), duration, interval, fx.getId()));
    }
    private static void clearVisual(Job j) {
        var visual = j.world.getEntity(j.visualId);
        if (visual instanceof CelestialFxEntity) visual.discard();
    }
    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job j = iterator.next();
            var owner = j.world.getEntity(j.caster);
            var entity = j.world.getEntity(j.target);
            long elapsed = j.world.getGameTime() - j.start;
            if (!(owner instanceof LivingEntity caster) || !caster.isAlive() || !(entity instanceof LivingEntity target)
                    || !ally(caster, target) || elapsed > j.duration || elapsed < 0) { clearVisual(j); iterator.remove(); continue; }
            if (j.kind == Kind.TETHER && (caster.distanceToSqr(target) > 144 || !caster.hasLineOfSight(target))) { clearVisual(j); iterator.remove(); continue; }
            if (j.kind == Kind.TETHER && j.world.getEntity(j.visualId) instanceof CelestialFxEntity fx) fx.target(caster.getBoundingBox().getCenter());
            if (elapsed <= 0 || elapsed % j.interval != 0) continue;
            switch (j.kind) {
                case SANCTUARY -> { for (var e : allies(j.world, caster, j.anchor, 4)) heal(e, j.power / 6); }
                case VERDANT_RENEWAL -> heal(target, j.power / 8);
                case BLOOM -> { heal(target, j.power * 1.5f); visual(j.world, target, target.position(), Kind.MENDING_LIGHT, 28, 1.4f, null); }
                case TETHER -> { heal(target, j.power / 5); visual(j.world, target, target.position(), j.kind, 22, 1, caster.getBoundingBox().getCenter()); }
                default -> { }
            }
            if (elapsed >= j.duration) { clearVisual(j); iterator.remove(); }
        }
    }
    private static void stop(ServerStoppedEvent event) { JOBS.clear(); }
}

