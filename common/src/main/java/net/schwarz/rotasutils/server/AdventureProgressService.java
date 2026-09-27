package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.AdventureXpConfig;
import net.schwarz.rotasutils.level.AdventureXpMath;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.util.Map;

/** Applies location, challenge, and bounded encounter familiarity to combat XP. */
public final class AdventureProgressService {
    private static final String FAMILIARITY_PREFIX = "rpg.adventure.kills.";
    private static final int FAMILIARITY_LIMIT = 256;
    private static final int MAX_REMEMBERED = 1000;

    private AdventureProgressService() {
    }

    /**
     * Combat XP for one kill. The zone multiplier comes from where the monster died, not where the
     * player stood, so shooting into a deadly zone from outside still pays the deadly rate.
     */
    public static long combatXp(ServerPlayer player, RotasData data, MonsterState monster, LivingEntity victim,
                                String identity) {
        AdventureXpConfig config = data.levelConfig().adventureXp();
        PlayerProgress progress = data.progress(player.getUUID());
        String key = FAMILIARITY_PREFIX + identity;
        long now = player.server.overworld().getGameTime();
        Familiarity remembered = Familiarity.parse(progress.questVariables().get(key));
        int kills = AdventureXpMath.decayedKills(remembered.count(), remembered.tick(), now,
                config.repetitionWindowTicks());
        ServerLevel level = victim.level() instanceof ServerLevel victimLevel ? victimLevel : player.serverLevel();
        ZoneService.Region region = ZoneService.region(data, level, victim.getX(), victim.getY(), victim.getZ());
        long award = AdventureXpMath.finalAward(monster.xp(), region.xpMultiplier(),
                AdventureXpMath.challengeMultiplier(progress.level(), monster.level(), config), 1.0,
                AdventureXpMath.repetitionMultiplier(kills, config), data.levelConfig().maximumMonsterXp());
        remember(progress, key, new Familiarity(Math.min(MAX_REMEMBERED, kills + 1), now));
        return award;
    }

    /**
     * Season combat award for one kill: the profile's base EXP with the zone and repetition multipliers and
     * the boss or miniboss multiplier. Level scaling, party sharing and the over-level penalty are applied per
     * recipient in {@link ProgressService#awardCombat}.
     */
    public static ProgressService.CombatAward seasonAward(ServerPlayer player, RotasData data, MonsterState monster,
                                                          LivingEntity victim, String identity) {
        AdventureXpConfig config = data.levelConfig().adventureXp();
        var rules = SeasonService.rules(data);
        PlayerProgress progress = data.progress(player.getUUID());
        String key = FAMILIARITY_PREFIX + identity;
        long now = player.server.overworld().getGameTime();
        Familiarity remembered = Familiarity.parse(progress.questVariables().get(key));
        int kills = AdventureXpMath.decayedKills(remembered.count(), remembered.tick(), now,
                config.repetitionWindowTicks());
        ServerLevel level = victim.level() instanceof ServerLevel victimLevel ? victimLevel : player.serverLevel();
        ZoneService.Region region = ZoneService.region(data, level, victim.getX(), victim.getY(), victim.getZ());
        var catalog = data.kernel() == null ? null : data.kernel().content().monsters();
        var profile = catalog == null ? null : catalog.profiles().get(monster.profile());
        var tier = catalog == null ? null : catalog.tiers().get(monster.tier());
        double base = profile != null ? profile.baseXp()
                : monster.xp() / (1.0 + rules.monsterLevelBonus * Math.max(1, monster.level()));
        double multiplier = region.xpMultiplier() * AdventureXpMath.repetitionMultiplier(kills, config);
        if (monster.boss()) {
            multiplier *= rules.bossMultiplier;
        } else if (tier != null && tier.rank() >= net.schwarz.rotasutils.core.MonsterRank.MINIBOSS.ordinal()) {
            multiplier *= rules.minibossMultiplier;
        } else if (tier != null) {
            multiplier *= tier.xpMultiplier();
        }
        remember(progress, key, new Familiarity(Math.min(MAX_REMEMBERED, kills + 1), now));
        return new ProgressService.CombatAward(Math.max(0, Math.round(base)), multiplier, monster.level());
    }

    /** Kill count and the play-time tick of the latest kill, stored as {@code count:tick}. */
    record Familiarity(int count, long tick) {
        static Familiarity parse(String value) {
            if (value == null || value.isEmpty()) {
                return new Familiarity(0, 0);
            }
            try {
                int split = value.indexOf(':');
                // Legacy entries were a bare count with no time; tick 0 lets them decay away.
                int count = Integer.parseInt(split < 0 ? value : value.substring(0, split));
                long tick = split < 0 ? 0 : Long.parseLong(value.substring(split + 1));
                return new Familiarity(Math.max(0, Math.min(MAX_REMEMBERED, count)), Math.max(0, tick));
            } catch (NumberFormatException ignored) {
                return new Familiarity(0, 0);
            }
        }

        String encode() {
            return count + ":" + tick;
        }
    }

    private static void remember(PlayerProgress progress, String key, Familiarity value) {
        Map<String, String> variables = progress.questVariables();
        if (!variables.containsKey(key)) {
            String oldest = null;
            Familiarity oldestValue = null;
            int entries = 0;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                if (!entry.getKey().startsWith(FAMILIARITY_PREFIX)) {
                    continue;
                }
                entries++;
                Familiarity candidate = Familiarity.parse(entry.getValue());
                if (oldest == null || candidate.tick() < oldestValue.tick()
                        || (candidate.tick() == oldestValue.tick() && entry.getKey().compareTo(oldest) < 0)) {
                    oldest = entry.getKey();
                    oldestValue = candidate;
                }
            }
            // Evict the least recently killed type, so the choice never depends on map order.
            if (entries >= FAMILIARITY_LIMIT && oldest != null) {
                variables.remove(oldest);
            }
        }
        variables.put(key, value.encode());
        progress.markDirty();
    }
}
