package net.schwarz.rotasutils.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.BossDefinitions;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.data.RotasData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Boss encounters: phases, arena leashing, enrage, contribution tracking and shared rewards.
 *
 * <p>Encounter state lives in the monster's own persisted runtime data, so a chunk unload, reload or
 * restart restores the same phase and the same contribution ledger instead of a fresh fight.
 */
public final class BossService {
    /** Contributors are bounded so a raid cannot grow the entity tag without limit. */
    public static final int MAX_CONTRIBUTORS = 32;
    private static final String PHASE = "boss:phase";
    private static final String ORIGIN = "boss:origin";
    private static final String ENRAGE = "boss:enrage";
    private static final String SEEN = "boss:seen";
    private static final String INTERVAL = "boss:interval";
    private static final String DAMAGE = "boss:dmg:";

    private final MinecraftServer server;
    private final RpgKernel kernel;
    private long transitions, resets, payouts, rejected;

    public BossService(MinecraftServer server, RpgKernel kernel) { this.server = server; this.kernel = kernel; }

    public BossDefinitions.Boss definition(MonsterState state) {
        var profile = kernel.content().monsters().profiles().get(state.profile());
        return profile == null || profile.boss() == null ? null : kernel.content().monsters().bosses().get(profile.boss());
    }

    /** Records damage dealt by a player and returns the ledger after the update. */
    public Map<String, Double> contribute(Mob mob, MonsterState state, LivingEntity source, double amount) {
        if (!(source instanceof ServerPlayer player) || !(amount > 0)) { return contributions(state); }
        CompoundTag runtime = state.runtime();
        String key = DAMAGE + player.getUUID();
        if (!runtime.contains(key) && count(runtime) >= MAX_CONTRIBUTORS) { return contributions(state); }
        double previous = runtime.getDouble(key);
        runtime.putDouble(key, Math.min(1.0E12, previous + Math.min(amount, 1.0E9)));
        state.runtime(runtime);
        return contributions(state);
    }

    public Map<String, Double> contributions(MonsterState state) {
        CompoundTag runtime = state.runtime();
        Map<String, Double> result = new TreeMap<>();
        runtime.getAllKeys().forEach(key -> {
            if (key.startsWith(DAMAGE)) { result.put(key.substring(DAMAGE.length()), runtime.getDouble(key)); }
        });
        return Map.copyOf(result);
    }

    /** Called for every monster trigger; drives phases, contribution and the encounter start. */
    public void handle(Mob mob, MonsterState state, LivingEntity target, MonsterDefinitions.Trigger event, Map<String, String> facts) {
        var boss = definition(state);
        if (boss == null) { return; }
        try {
            if (event == MonsterDefinitions.Trigger.SPAWN) { begin(mob, state, boss); return; }
            if (event == MonsterDefinitions.Trigger.HURT) {
                double amount = parse(facts.get("event.amount"));
                contribute(mob, state, target, amount);
            }
            if (!state.runtime().contains(ORIGIN)) { begin(mob, state, boss); }
            phase(mob, state, boss);
        } catch (RuntimeException failure) { error("boss:" + boss.id(), failure); }
    }

    private void begin(Mob mob, MonsterState state, BossDefinitions.Boss boss) {
        CompoundTag runtime = state.runtime();
        runtime.putString(ORIGIN, mob.blockPosition().getX() + "," + mob.blockPosition().getY() + "," + mob.blockPosition().getZ());
        runtime.putInt(PHASE, boss.phaseAt(fraction(mob)));
        runtime.putLong(SEEN, server.overworld().getGameTime());
        if (boss.enrageTicks() > 0) { runtime.putLong(ENRAGE, server.overworld().getGameTime() + boss.enrageTicks()); }
        state.runtime(runtime);
        kernel.monsters().persist(mob, state);
        apply(mob, boss, runtime.getInt(PHASE), false);
    }

    /** Applies phase transitions; entering a later phase runs its actions once. */
    public boolean phase(Mob mob, MonsterState state, BossDefinitions.Boss boss) {
        CompoundTag runtime = state.runtime();
        int current = runtime.getInt(PHASE);
        int next = boss.phaseAt(fraction(mob));
        if (next == current) { return false; }
        runtime.putInt(PHASE, next);
        state.runtime(runtime);
        kernel.monsters().persist(mob, state);
        apply(mob, boss, next, next > current);
        transitions++;
        return true;
    }

    private void apply(Mob mob, BossDefinitions.Boss boss, int index, boolean announce) {
        var phase = boss.phases().get(Math.max(0, Math.min(boss.phases().size() - 1, index)));
        kernel.monsters().applyOwnedScales(mob, "boss", phase.attributes());
        if (!announce) { return; }
        if (!phase.label().isEmpty()) { broadcast(mob, boss.arenaRadius(), phase.label()); }
        run(mob, phase.onEnter());
        summon(mob, phase);
    }

    private void summon(Mob mob, BossDefinitions.Phase phase) {
        if (phase.summon() == null || phase.summonCount() <= 0) { return; }
        var monsters = kernel.monsters();
        var profile = kernel.content().monsters().profiles().get(phase.summon());
        if (profile == null) { throw new IllegalArgumentException("Unknown summon profile: " + phase.summon()); }
        for (int i = 0; i < phase.summonCount(); i++) {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                    new net.minecraft.resources.ResourceLocation(profile.selector().entities().stream().findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Summon profile needs an entity selector"))));
            var spawned = type.create(mob.level());
            if (!(spawned instanceof Mob adds)) { continue; }
            adds.moveTo(mob.getX() + (i % 4) - 1.5, mob.getY(), mob.getZ() + (i / 4) - 1.5, mob.getYRot(), 0);
            if (!mob.level().addFreshEntity(adds)) { continue; }
            monsters.assign(adds, phase.summon(), null, "BOSS_PHASE");
        }
    }

    private void run(Mob mob, List<net.schwarz.rotasutils.core.ActionEngine.Action> actions) {
        if (actions.isEmpty()) { return; }
        var state = kernel.monsters().peek(mob);
        if (state == null) { return; }
        var context = new MonsterContext(kernel.monsters(), mob, state, mob.getTarget(), Map.of());
        try (KernelContext.Transaction transaction = context.begin()) {
            actions.forEach(action -> action.stage(transaction));
            transaction.commit();
        }
    }

    /** Per-tick arena, enrage, interval mechanics and reset handling for one loaded boss. */
    public void tick(Mob mob, MonsterState state) {
        var boss = definition(state);
        if (boss == null) { return; }
        try {
            CompoundTag runtime = state.runtime();
            if (!runtime.contains(ORIGIN)) { return; }
            long now = server.overworld().getGameTime();
            Vec3 origin = origin(runtime);
            int resetTicks = boss.resetTicks();
            double radiusSquared = (double) boss.arenaRadius() * boss.arenaRadius();
            // Presence only has to be noticed before the reset window expires, so it is staggered
            // per boss instead of scanning every online player for every boss every tick.
            if (BossPresenceScan.due(BossPresenceScan.intervalFor(resetTicks), now, mob.getId())
                    && engaged(mob, origin, radiusSquared)) {
                runtime.putLong(SEEN, now);
                state.runtime(runtime);
            }
            if (now - runtime.getLong(SEEN) > resetTicks) {
                reset(mob, state, boss, origin);
                return;
            }
            if (boss.leash() && mob.position().distanceToSqr(origin) > radiusSquared) {
                mob.teleportTo(origin.x, origin.y, origin.z);
            }
            if (boss.enrageTicks() > 0 && runtime.contains(ENRAGE) && now >= runtime.getLong(ENRAGE)
                    && !boss.enrageAttributes().isEmpty()) {
                kernel.monsters().applyOwnedScales(mob, "boss_enrage", boss.enrageAttributes());
            }
            var phase = boss.phases().get(Math.max(0, Math.min(boss.phases().size() - 1, runtime.getInt(PHASE))));
            if (phase.interval() > 0 && !phase.onInterval().isEmpty() && now >= runtime.getLong(INTERVAL)) {
                runtime.putLong(INTERVAL, now + phase.interval());
                state.runtime(runtime);
                kernel.monsters().persist(mob, state);
                run(mob, phase.onInterval());
            }
        } catch (RuntimeException failure) { error("boss-tick:" + boss.id(), failure); }
    }

    /** True when a live, non-spectating player of the boss's own level stands inside the arena. */
    private static boolean engaged(Mob mob, Vec3 origin, double radiusSquared) {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return false;
        }
        // level.players() is already local to this dimension, so other dimensions are never walked.
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()
                    && player.position().distanceToSqr(origin) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    /** Restores full health, phase zero and an empty ledger once every player has left the arena. */
    public void reset(Mob mob, MonsterState state, BossDefinitions.Boss boss, Vec3 origin) {
        CompoundTag runtime = state.runtime();
        List<String> keys = new ArrayList<>(runtime.getAllKeys());
        keys.forEach(key -> { if (key.startsWith(DAMAGE)) { runtime.remove(key); } });
        runtime.putInt(PHASE, 0);
        runtime.putLong(SEEN, server.overworld().getGameTime());
        if (boss.enrageTicks() > 0) { runtime.putLong(ENRAGE, server.overworld().getGameTime() + boss.enrageTicks()); }
        state.runtime(runtime);
        kernel.monsters().persist(mob, state);
        kernel.monsters().applyOwnedScales(mob, "boss_enrage", Map.of());
        apply(mob, boss, 0, false);
        mob.setHealth(mob.getMaxHealth());
        mob.teleportTo(origin.x, origin.y, origin.z);
        resets++;
    }

    /**
     * Pays every contributor above the minimum share when the boss dies. Rewards use the boss UUID as
     * occurrence, so a duplicate death event or a retry cannot pay twice.
     */
    public int reward(Mob mob, MonsterState state) {
        var boss = definition(state);
        if (boss == null) { return 0; }
        Map<String, Double> shares = BossDefinitions.shares(contributions(state));
        int paid = 0;
        for (var entry : new LinkedHashMap<>(shares).entrySet()) {
            if (entry.getValue() < boss.minimumShare()) { continue; }
            ServerPlayer player;
            try { player = server.getPlayerList().getPlayer(UUID.fromString(entry.getKey())); }
            catch (IllegalArgumentException malformed) { continue; }
            if (player == null) { continue; }
            try {
                if (boss.reward() != null) { kernel.grant(player, boss.reward(), "boss:" + mob.getUUID()); }
                if (boss.loot() != null) {
                    LootService.grantOnce(player, RotasData.get(server), boss.loot(), "boss:" + mob.getUUID(),
                            state.level(), state.lootMultiplier());
                }
                paid++;
            } catch (RuntimeException failure) { error("boss-reward:" + boss.id(), failure); }
        }
        payouts += paid;
        return paid;
    }

    private void broadcast(Mob mob, int radius, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == mob.level() && player.distanceToSqr(mob) <= (double) radius * radius) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static double fraction(Mob mob) {
        return mob.getMaxHealth() <= 0 ? 0 : Math.max(0, Math.min(1, mob.getHealth() / mob.getMaxHealth()));
    }

    private static int count(CompoundTag runtime) {
        int total = 0;
        for (String key : runtime.getAllKeys()) { if (key.startsWith(DAMAGE)) { total++; } }
        return total;
    }

    private static Vec3 origin(CompoundTag runtime) {
        String[] parts = runtime.getString(ORIGIN).split(",");
        if (parts.length != 3) { throw new IllegalArgumentException("Corrupt boss arena origin"); }
        return new Vec3(Double.parseDouble(parts[0]) + 0.5, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]) + 0.5);
    }

    private static double parse(String value) {
        try { return value == null ? 0 : Double.parseDouble(value); }
        catch (NumberFormatException unavailable) { return 0; }
    }

    public String diagnostics() {
        return "Bosses transitions=" + transitions + " resets=" + resets + " payouts=" + payouts + " rejected=" + rejected;
    }

    private void error(String id, RuntimeException failure) {
        rejected++;
        Rotasutils.LOG.error("RPG {}: {}", id, failure.getMessage(), failure);
    }
}
