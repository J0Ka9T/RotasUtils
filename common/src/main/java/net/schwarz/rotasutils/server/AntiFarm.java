package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.level.XpSourceConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory farming guard.
 *
 * <p>Records are keyed by player + source + target and expire on their own, so the
 * map stays bounded without a per-tick sweep. Nothing here is persisted: a restart
 * resets the counters, which is the conservative direction to fail in.
 */
public final class AntiFarm {
    private record Key(UUID player, XpSource source, String target) {
    }

    private static final class Record {
        long lastAwardMillis;
        int targetCount;
        int dailyCount;
        long dayStartMillis;
        long expiresAtMillis;
    }

    private static final Map<Key, Record> RECORDS = new HashMap<>();
    private static long lastSweepMillis;

    private AntiFarm() {
    }

    /** Returns true when the award should be granted. */
    public static boolean allow(ServerPlayer player, RotasData data, XpSource source,
                                XpSourceConfig config, String target) {
        long now = System.currentTimeMillis();
        sweep(now, data);

        if (!data.serverSettings().antiFarmEnabled()) {
            return true;
        }
        return withinPlainLimits(player, source, config, target, now, data);
    }

    private static boolean withinPlainLimits(ServerPlayer player, XpSource source, XpSourceConfig config,
                                             String target, long now, RotasData data) {
        if (config.cooldownSeconds() <= 0 && config.perTargetLimit() <= 0 && config.dailyLimit() <= 0) {
            return true;
        }
        long retention = data.serverSettings().antiFarmMemorySeconds() * 1000L;

        Key targetKey = new Key(player.getUUID(), source, target == null ? "" : target);
        Record targetRecord = RECORDS.computeIfAbsent(targetKey, k -> {
            Record created = new Record();
            created.dayStartMillis = now;
            return created;
        });
        targetRecord.expiresAtMillis = now + retention;

        if (config.cooldownSeconds() > 0
                && now - targetRecord.lastAwardMillis < config.cooldownSeconds() * 1000L) {
            return false;
        }
        if (config.perTargetLimit() > 0 && targetRecord.targetCount >= config.perTargetLimit()) {
            return false;
        }

        // Daily limits are per player + XP source, not per entity/item target.
        Key dailyKey = new Key(player.getUUID(), source, "\u0000rotas_daily");
        Record dailyRecord = RECORDS.computeIfAbsent(dailyKey, k -> {
            Record created = new Record();
            created.dayStartMillis = now;
            return created;
        });
        dailyRecord.expiresAtMillis = Math.max(now + retention, dailyRecord.dayStartMillis + 86_400_000L);
        if (now - dailyRecord.dayStartMillis >= 86_400_000L) {
            dailyRecord.dayStartMillis = now;
            dailyRecord.dailyCount = 0;
        }
        if (config.dailyLimit() > 0 && dailyRecord.dailyCount >= config.dailyLimit()) {
            return false;
        }

        targetRecord.lastAwardMillis = now;
        targetRecord.targetCount++;
        dailyRecord.dailyCount++;
        return true;
    }

    /**
     * Player-kill guard: refuses repeat kills on the same victim inside the cooldown
     * and refuses victims below the configured minimum playtime.
     */
    public static boolean allowPlayerKill(ServerPlayer killer, ServerPlayer victim, RotasData data,
                                          int victimCooldownSeconds, int minVictimPlaytimeMinutes) {
        if (!data.serverSettings().antiFarmEnabled()) {
            return true;
        }
        if (killer.getUUID().equals(victim.getUUID())) {
            return false;
        }
        long now = System.currentTimeMillis();
        sweep(now, data);

        if (minVictimPlaytimeMinutes > 0) {
            int ticksPlayed = victim.getStats().getValue(
                    net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            if (ticksPlayed < minVictimPlaytimeMinutes * 60 * 20) {
                return false;
            }
        }
        if (victimCooldownSeconds <= 0) {
            return true;
        }
        Key key = new Key(killer.getUUID(), XpSource.PLAYER_KILL, victim.getUUID().toString());
        Record record = RECORDS.get(key);
        if (record != null && now - record.lastAwardMillis < victimCooldownSeconds * 1000L) {
            return false;
        }
        Record updated = record == null ? new Record() : record;
        updated.lastAwardMillis = now;
        updated.expiresAtMillis = now + Math.max(victimCooldownSeconds * 1000L,
                data.serverSettings().antiFarmMemorySeconds() * 1000L);
        RECORDS.put(key, updated);
        return true;
    }

    /** Drops expired records; runs at most once every 30 seconds. */
    private static void sweep(long now, RotasData data) {
        if (now - lastSweepMillis < 30_000L) {
            return;
        }
        lastSweepMillis = now;
        Iterator<Map.Entry<Key, Record>> iterator = RECORDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Record> entry = iterator.next();
            if (entry.getValue().expiresAtMillis <= now) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        RECORDS.clear();
    }
}
