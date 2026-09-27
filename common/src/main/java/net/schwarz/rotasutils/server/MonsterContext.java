package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.core.MonsterState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MonsterContext implements KernelContext {
    private final MonsterService service;
    private final Mob mob;
    private final MonsterState state;
    private final LivingEntity target;
    private final Map<String, String> facts;

    public MonsterContext(MonsterService service, Mob mob, MonsterState state, LivingEntity target, Map<String, String> facts) {
        this.service = service; this.mob = mob; this.state = state; this.target = target; this.facts = Map.copyOf(facts);
    }
    @Override public double number(String name) {
        return switch (name) {
            case "monster.level" -> state.level();
            case "monster.tier" -> {
                var tier = service.kernel().content().monsters().tiers().get(state.tier()); yield tier == null ? Double.NaN : tier.rank();
            }
            case "monster.health" -> mob.getHealth();
            case "monster.max_health" -> mob.getMaxHealth();
            case "monster.health_ratio" -> mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 0;
            case "monster.base_xp" -> state.xp();
            default -> {
                if (name.startsWith("rpg.")) {
                    try { yield Double.parseDouble(state.runtime().getString("var:" + name)); }
                    catch (NumberFormatException unavailable) { yield Double.NaN; }
                }
                if (name.startsWith("event.")) {
                    try { yield Double.parseDouble(facts.getOrDefault(name, "")); }
                    catch (NumberFormatException unavailable) { yield Double.NaN; }
                }
                if (name.startsWith("player.") && target instanceof ServerPlayer player) { yield new KernelPlayerContext(player, facts).number(name); }
                yield service.environment(mob).numbers().getOrDefault(name, Double.NaN);
            }
        };
    }
    @Override public String text(String name) {
        return switch (name) {
            case "monster.profile" -> state.profile().value();
            case "monster.tier" -> state.tier().value();
            case "monster.entity_type" -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            case "monster.dimension" -> mob.level().dimension().location().toString();
            case "region.id" -> service.environment(mob).region();
            default -> name.startsWith("rpg.") ? state.runtime().getString("var:" + name) : facts.get(name);
        };
    }
    @Override public boolean requirement(String type, Map<String, String> parameters) {
        if (!(target instanceof ServerPlayer player)) { throw new IllegalArgumentException("Player requirement has no player target"); }
        return new KernelPlayerContext(player, facts).requirement(type, parameters);
    }
    @Override public Transaction begin() { return new Transaction(); }

    public final class Transaction implements KernelContext.Transaction {
        private final CompoundTag before = state.runtime();
        private final CompoundTag next = before.copy();
        private final List<Runnable> effects = new ArrayList<>();
        private boolean closed;
        private void open() { if (closed) { throw new IllegalStateException("Monster transaction is closed"); } }
        public void cooldown(String key, long time) { open(); next.putLong(key, time); }
        @Override public String variable(String key) { open(); return next.contains("var:" + key) ? next.getString("var:" + key) : null; }
        @Override public void variable(String key, String value) {
            open(); if (!key.matches("rpg\\.[a-z0-9_.-]{1,120}") || value == null || value.length() > 1024) { throw new IllegalArgumentException("Invalid monster variable"); }
            next.putString("var:" + key, value);
        }
        @Override public void unlock(String id) { throw new UnsupportedOperationException("Monster cannot unlock player quests"); }
        @Override public boolean claimed(String receipt) { open(); return next.getBoolean(receipt(receipt)); }
        @Override public void claim(String receipt) { open(); next.putBoolean(receipt(receipt), true); }
        private String receipt(String value) { return "claim:" + java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        private LivingEntity recipient(String name) {
            open(); LivingEntity entity = name.equals("self") ? mob : name.equals("target") ? target : null;
            if (entity == null || entity.level() != mob.level() || !entity.isAlive() || entity.isRemoved() || entity.distanceToSqr(mob) > 32 * 32) {
                throw new IllegalArgumentException("Entity action target unavailable or outside 32 blocks");
            }
            return entity;
        }
        @Override public void heal(String name, double amount) { LivingEntity entity = recipient(name); add(() -> entity.heal((float) amount)); }
        @Override public void damage(String name, double amount) {
            LivingEntity entity = recipient(name); add(() -> entity.hurt(mob.damageSources().mobAttack(mob), (float) amount));
        }
        @Override public void effect(String name, String id, int duration, int amplifier) {
            LivingEntity entity = recipient(name); var effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(id));
            if (effect == null) { throw new IllegalArgumentException("Unknown mob effect " + id); }
            add(() -> entity.addEffect(new MobEffectInstance(effect, duration, amplifier), mob));
        }
        private void add(Runnable effect) { if (effects.size() >= 128) { throw new IllegalStateException("Entity effect budget exceeded"); } effects.add(effect); }
        @Override public void commit() {
            open(); if (!mob.getServer().isSameThread() || !state.runtime().equals(before)) { throw new IllegalStateException("Conflicting monster transaction"); }
            state.runtime(next); service.persist(mob, state); closed = true;
            effects.forEach(Runnable::run);
        }
        @Override public void close() { closed = true; effects.clear(); }
    }
}
