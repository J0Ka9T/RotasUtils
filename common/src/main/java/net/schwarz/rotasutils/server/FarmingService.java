package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.schwarz.rotasutils.core.FarmingMath;
import net.schwarz.rotasutils.core.MonsterRank;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Luck and kill combos: the two things that make the next kill feel worth more than the last.
 *
 * <p>Luck reads the player's own {@code generic.luck} attribute, so everything that already raises it -
 * a card, a potion, a piece of gear - makes farming better without knowing this exists. A combo lives
 * only in memory: it is a feel-good number for the next few seconds, not progress, so a restart or a
 * relog simply ends it.</p>
 */
public final class FarmingService {
    private record Combo(int count, long lastMillis) {
    }

    private static final Map<UUID, Combo> COMBOS = new ConcurrentHashMap<>();

    private FarmingService() {
    }

    public static void forget(UUID player) {
        COMBOS.remove(player);
    }

    public static void clear() {
        COMBOS.clear();
    }

    private static SeasonRules.FarmingRules rules(RotasData data) {
        return SeasonService.rules(data).farming;
    }

    // Luck -----------------------------------------------------------------------------------------

    public static double luck(ServerPlayer player) {
        var instance = player.getAttribute(Attributes.LUCK);
        return instance == null ? 0 : instance.getValue();
    }

    /** What the player's luck multiplies a loot chance by. */
    public static double lootLuck(ServerPlayer player, RotasData data) {
        SeasonRules.FarmingRules farming = rules(data);
        return farming == null || !farming.luckEnabled ? 1.0
                : FarmingMath.luckMultiplier(luck(player), farming.lootChancePerLuck, farming.luckBonusCap);
    }

    /** What the player's luck multiplies a card chance by. */
    public static double cardLuck(ServerPlayer player, RotasData data) {
        SeasonRules.FarmingRules farming = rules(data);
        return farming == null || !farming.luckEnabled ? 1.0
                : FarmingMath.luckMultiplier(luck(player), farming.cardChancePerLuck, farming.luckBonusCap);
    }

    // Combo ----------------------------------------------------------------------------------------

    /**
     * One kill in a chain. Called before the kill's experience is worked out, so the kill that extends
     * a combo is also the first to be paid at the new rate.
     */
    public static int onKill(ServerPlayer player, RotasData data) {
        SeasonRules.FarmingRules farming = rules(data);
        if (farming == null || !farming.comboEnabled) {
            return 0;
        }
        long now = System.currentTimeMillis();
        Combo previous = COMBOS.get(player.getUUID());
        int count = previous == null ? 1
                : FarmingMath.nextCombo(previous.count(), previous.lastMillis(), now, farming.comboWindowSeconds);
        COMBOS.put(player.getUUID(), new Combo(count, now));
        if (count >= 2) {
            int bonus = (int) Math.round((FarmingMath.comboMultiplier(count, farming.comboXpPerKill, farming.comboCap) - 1) * 100);
            player.displayClientMessage(ThaiText.c("rotasutils.msg.combo", count, bonus)
                    .withStyle(count >= farming.comboCap ? ChatFormatting.GOLD : ChatFormatting.YELLOW), true);
        }
        return count;
    }

    /** The live chain, or 0 once it has lapsed. */
    public static int combo(ServerPlayer player, RotasData data) {
        SeasonRules.FarmingRules farming = rules(data);
        Combo combo = COMBOS.get(player.getUUID());
        if (farming == null || !farming.comboEnabled || combo == null
                || !FarmingMath.comboAlive(combo.lastMillis(), System.currentTimeMillis(), farming.comboWindowSeconds)) {
            return 0;
        }
        return combo.count();
    }

    public static double comboXp(ServerPlayer player, RotasData data) {
        SeasonRules.FarmingRules farming = rules(data);
        return farming == null ? 1.0
                : FarmingMath.comboMultiplier(combo(player, data), farming.comboXpPerKill, farming.comboCap);
    }

    public static double comboLoot(ServerPlayer player, RotasData data) {
        SeasonRules.FarmingRules farming = rules(data);
        return farming == null ? 1.0
                : FarmingMath.comboMultiplier(combo(player, data), farming.comboLootPerKill, farming.comboCap);
    }

    // Elite monsters -------------------------------------------------------------------------------

    /**
     * Whether a freshly levelled mob is promoted. Only natural spawns roll, and only hostile ones: an
     * elite cow is a joke, and an elite from a spawner is a farm.
     */
    public static MonsterRank rollElite(RotasData data, net.minecraft.world.entity.Mob mob, String reason) {
        SeasonRules.FarmingRules farming = rules(data);
        if (farming == null || !farming.eliteEnabled) {
            return MonsterRank.NORMAL;
        }
        // A world event's waves count as the wild: they are brought by the event, not by a farm.
        boolean natural = "NATURAL".equals(reason) || "CHUNK_GENERATION".equals(reason)
                || mob.getTags().contains(WorldEventService.WAVE_TAG);
        if (!natural || !BestiaryService.recordable(mob)) {
            return MonsterRank.NORMAL;
        }
        var event = WorldEventService.at(mob);
        double boost = event == null ? 1.0 : event.eliteMultiplier;
        double champion = Math.min(1.0, farming.championChance * boost);
        double elite = Math.min(1.0 - champion, farming.eliteChance * boost);
        double veteran = Math.min(1.0 - champion - elite, farming.veteranChance * boost);
        double roll = mob.getRandom().nextDouble();
        if (roll < champion) {
            return MonsterRank.CHAMPION;
        }
        if (roll < champion + elite) {
            return MonsterRank.ELITE;
        }
        return roll < champion + elite + veteran ? MonsterRank.VETERAN : MonsterRank.NORMAL;
    }

    /**
     * A champion is announced to the players near it; its name plate and the target panel carry the
     * mark. It does not glow: an outline through walls read as a debug overlay, not as a threat.
     */
    public static void marked(net.minecraft.world.entity.Mob mob, MonsterRank rank, RotasData data) {
        if (rank != MonsterRank.CHAMPION) {
            return;
        }
        SeasonRules.FarmingRules farming = rules(data);
        if (farming != null && farming.announceChampion && mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(mob) <= 64 * 64) {
                    player.sendSystemMessage(ThaiText.c("rotasutils.msg.elite.champion_nearby",
                            mob.getType().getDescription().getString()).withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    /** Rank points an elite or champion kill is worth; the season track climbs on these. */
    public static void onRankedKill(ServerPlayer player, RotasData data, MonsterRank rank) {
        SeasonRules.FarmingRules farming = rules(data);
        if (farming == null || !farming.eliteEnabled || rank == null) {
            return;
        }
        long points = switch (rank) {
            case ELITE -> farming.eliteRankPoints;
            case CHAMPION -> farming.championRankPoints;
            default -> 0;
        };
        if (points <= 0) {
            return;
        }
        var progress = data.progress(player.getUUID());
        progress.addRankPoints(points);
        ProgressService.refreshClearance(player, data, progress);
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.elite.rank_points", points).withStyle(ChatFormatting.LIGHT_PURPLE));
        data.setDirty();
    }

    /**
     * Strips the endless, hidden glow older builds put on champions, so mobs saved before the change
     * stop glowing once they load. Only that exact effect is removed; a glow from a spectral arrow,
     * a potion or a command is left alone.
     */
    public static void clearLegacyGlow(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob) || mob.level().isClientSide) {
            return;
        }
        var glow = mob.getEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        if (glow != null && glow.getDuration() > 1_000_000 && !glow.isVisible()) {
            mob.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        }
    }
}
