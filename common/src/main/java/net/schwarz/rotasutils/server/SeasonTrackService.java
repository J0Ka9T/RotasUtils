package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.DailyTrack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * The season track: rungs on the rank points a player already earns.
 *
 * <p>Nothing here earns points - quests, bosses, the daily track and the bestiary already do. A claim is
 * recorded in the existing claimed-rewards set under the season id and the rung's point value, so a
 * rung is paid once per season however the rungs are reordered, and a new season id starts over.</p>
 */
public final class SeasonTrackService {
    private SeasonTrackService() {
    }

    public record Result(boolean success, String message) {
    }

    private static SeasonRules.SeasonTrackRules rules(RotasData data) {
        return SeasonService.rules(data).seasonTrack;
    }

    /** The claimed-rewards key for one rung of the current season. */
    public static String key(SeasonRules.SeasonTrackRules track, SeasonRules.SeasonTier tier) {
        return "season:" + track.seasonId + ":" + tier.points;
    }

    /** Which rungs are claimed, as a bitmask over the current rung order, for the screen. */
    public static int claimedMask(RotasData data, PlayerProgress progress) {
        SeasonRules.SeasonTrackRules track = rules(data);
        int mask = 0;
        for (int tier = 0; tier < track.tiers.length && tier < DailyTrack.MAX_TIERS; tier++) {
            if (progress.claimedRewards().contains(key(track, track.tiers[tier]))) {
                mask = DailyTrack.claim(mask, tier);
            }
        }
        return mask;
    }

    public static Result claim(ServerPlayer player, RotasData data, int tier) {
        SeasonRules.SeasonTrackRules track = rules(data);
        if (track == null || !track.enabled) {
            return new Result(false, ThaiText.t("rotasutils.msg.season_track.disabled"));
        }
        if (tier < 0 || tier >= track.tiers.length) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.no_tier"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        SeasonRules.SeasonTier rung = track.tiers[tier];
        if (progress.rankPoints() < rung.points) {
            return new Result(false, ThaiText.t("rotasutils.msg.season_track.not_yet",
                    rung.points - progress.rankPoints()));
        }
        // The claimed set is the record; adding to it first means a re-entrant payout cannot pay twice.
        if (!progress.claimOnce(key(track, rung))) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.already"));
        }
        TrackRewards.Paid paid = TrackRewards.pay(player, data, rung.reward, "season|" + track.seasonId + "|" + rung.points);
        data.audit(player.getGameProfile().getName() + " claimed season tier " + rung.points);
        RotasNetwork.syncProgress(player);
        return new Result(true, ThaiText.t("rotasutils.msg.season_track.claimed",
                rung.name.isBlank() ? String.valueOf(tier + 1) : rung.name, TrackRewards.describe(paid)));
    }

    /** How many reached rungs are still waiting to be claimed. */
    public static int waiting(RotasData data, PlayerProgress progress) {
        SeasonRules.SeasonTrackRules track = rules(data);
        if (track == null || !track.enabled) {
            return 0;
        }
        return DailyTrack.waiting(track.thresholds(), progress.rankPoints(), claimedMask(data, progress));
    }
}
