package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.DailyTrack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

public final class SeasonTrackService {
    private SeasonTrackService() {
    }

    public record Result(boolean success, String message) {
    }

    private static SeasonRules.SeasonTrackRules rules(RotasData data) {
        return SeasonService.rules(data).seasonTrack;
    }

    public static String key(SeasonRules.SeasonTrackRules track, SeasonRules.SeasonTier tier) {
        return "season:" + track.seasonId + ":" + tier.points;
    }

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
        if (!progress.claimOnce(key(track, rung))) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.already"));
        }
        TrackRewards.Paid paid = TrackRewards.pay(player, data, rung.reward, "season|" + track.seasonId + "|" + rung.points);
        data.audit(player.getGameProfile().getName() + " claimed season tier " + rung.points);
        RotasNetwork.syncProgress(player);
        return new Result(true, ThaiText.t("rotasutils.msg.season_track.claimed",
                rung.name.isBlank() ? String.valueOf(tier + 1) : rung.name, TrackRewards.describe(paid)));
    }

    public static int waiting(RotasData data, PlayerProgress progress) {
        SeasonRules.SeasonTrackRules track = rules(data);
        if (track == null || !track.enabled) {
            return 0;
        }
        return DailyTrack.waiting(track.thresholds(), progress.rankPoints(), claimedMask(data, progress));
    }
}
