package net.schwarz.rotasutils.ability;

import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place that starts, times and ends cinematic abilities. An item only asks {@link #start}; this
 * validates (item, cooldown, caster state), locks a target, tells the clients, and then drives the
 * ability's {@link Timeline} from a single server clock until it ends or is cut short. Damage and
 * knockback happen only in timeline events the server itself fires; a client can request nothing but
 * the start, and never reports a hit.
 */
public final class AbilityManager {
    private AbilityManager() {
    }

    public enum Result {
        STARTED, ON_COOLDOWN, BUSY, INVALID
    }

    private static final class Active {
        final AbilityDefinition definition;
        final AbilityContext context;
        double previous = -1;

        Active(AbilityDefinition definition, AbilityContext context) {
            this.definition = definition;
            this.context = context;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static boolean initialised;

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        TickEvent.SERVER_POST.register(AbilityManager::tick);
        PlayerEvent.PLAYER_QUIT.register(player -> cancel(player.getUUID()));
        PlayerEvent.CHANGE_DIMENSION.register((player, oldWorld, newWorld) -> cancel(player.getUUID()));
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            cancel(entity.getUUID());
            return dev.architectury.event.EventResult.pass();
        });
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static long cooldownLeft(ServerPlayer player, AbilityDefinition definition) {
        Map<ResourceLocation, Long> map = COOLDOWNS.get(player.getUUID());
        Long until = map == null ? null : map.get(definition.id());
        return until == null ? 0 : Math.max(0, until - player.serverLevel().getGameTime());
    }

    /** Validates and begins {@code definition} for {@code player}. */
    public static Result start(ServerPlayer player, AbilityDefinition definition) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()
                || !definition.canStart(player)) {
            return Result.INVALID;
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            return Result.BUSY;
        }
        if (cooldownLeft(player, definition) > 0) {
            player.displayClientMessage(Component.translatable("rotasutils.ability.cooldown",
                    String.format("%.1f", cooldownLeft(player, definition) / 20.0)), true);
            return Result.ON_COOLDOWN;
        }
        ServerLevel level = player.serverLevel();
        Target target = TargetFinder.acquire(player, RedTimings.RANGE, 12.0);
        AbilityContext context = new AbilityContext(player, level, level.getGameTime(), target,
                level.getRandom().nextLong());
        ACTIVE.put(player.getUUID(), new Active(definition, context));
        COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(definition.id(), level.getGameTime() + definition.cooldownTicks());
        AbilityNet.sendStart(context, definition.id());
        Rotasutils.LOG.debug("{} began {}", player.getGameProfile().getName(), definition.id());
        return Result.STARTED;
    }

    /** Cuts an ability short (caster died, left, changed world). Clients are told so they drop the cutscene. */
    public static void cancel(UUID player) {
        Active active = ACTIVE.remove(player);
        if (active != null) {
            finish(active, false);
        }
    }

    private static void finish(Active active, boolean completed) {
        active.definition.end(active.context, completed);
        AbilityNet.sendEnd(active.context, completed);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Active active = it.next().getValue();
            AbilityContext c = active.context;
            ServerPlayer player = c.player;
            if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.serverLevel() != c.level
                    || server.getPlayerList().getPlayer(player.getUUID()) != player) {
                it.remove();
                finish(active, false);
                continue;
            }
            long now = c.level.getGameTime();
            for (Iterator<AbilityContext.Delayed> d = c.delayed.iterator(); d.hasNext(); ) {
                AbilityContext.Delayed delayed = d.next();
                if (delayed.dueTick() <= now) {
                    d.remove();
                    delayed.action().run();
                }
            }
            double seconds = (now - c.startTick) / 20.0;
            active.definition.timeline().advance(active.previous, seconds, c);
            active.previous = seconds;
            if (now - c.startTick >= active.definition.durationTicks()) {
                it.remove();
                finish(active, true);
            }
        }
    }
}
