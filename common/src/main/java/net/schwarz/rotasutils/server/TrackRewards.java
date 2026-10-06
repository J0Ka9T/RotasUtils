package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class TrackRewards {
    private TrackRewards() {
    }

    public record Paid(long gold, long xp, long rankPoints, List<ItemStack> items, int mailed) {
    }

    public static Paid pay(ServerPlayer player, RotasData data, SeasonRules.TrackReward reward, String seed) {
        PlayerProgress progress = data.progress(player.getUUID());
        SeasonRules rules = SeasonService.rules(data);
        if (reward.gold > 0) {
            progress.rpg().currency(rules.currency, reward.gold);
        }
        if (reward.xp > 0) {
            ProgressService.addExperience(player, data, reward.xp, true);
        }
        if (reward.rankPoints > 0) {
            progress.addRankPoints(reward.rankPoints);
            ProgressService.refreshClearance(player, data, progress);
        }
        RandomGenerator random = LootService.random(seed + "|" + player.getUUID() + "|" + System.nanoTime());
        List<ItemStack> items = new ArrayList<>();
        for (String line : reward.items) {
            ItemStack stack = DropLoot.parse(line, random);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        int mailed = items.isEmpty() ? 0 : LootService.deliver(player, items);
        progress.markDirty();
        data.setDirty();
        return new Paid(reward.gold, reward.xp, reward.rankPoints, List.copyOf(items), mailed);
    }

    public static String describe(Paid paid) {
        List<String> parts = new ArrayList<>();
        if (paid.gold() > 0) {
            parts.add("+" + paid.gold() + " " + net.schwarz.rotasutils.util.ThaiText.t("rotasutils.track.gold"));
        }
        if (paid.xp() > 0) {
            parts.add("+" + paid.xp() + " EXP");
        }
        if (paid.rankPoints() > 0) {
            parts.add("+" + paid.rankPoints() + " " + net.schwarz.rotasutils.util.ThaiText.t("rotasutils.track.rank"));
        }
        for (ItemStack stack : paid.items()) {
            parts.add(stack.getCount() + "x " + stack.getHoverName().getString());
        }
        return String.join(", ", parts);
    }
}
