package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.ProgressionRewards;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.progress.PlayerProgress;

public final class MilestoneService {
    private MilestoneService() {
    }

    public static void onLevel(ServerPlayer player, RotasData data, PlayerProgress progress, int level) {
        SeasonRules.MilestoneRules rules = SeasonService.rules(data).milestones;
        boolean milestone = ProgressionRewards.milestone(rules, level);
        if (rules.celebrate) burst(player, milestone);
        if (!milestone) return;
        boolean big = ProgressionRewards.big(rules, level);
        long gold = ProgressionRewards.gold(rules, level);
        int points = ProgressionRewards.statPoints(rules, level);
        if (gold > 0) progress.rpg().currency(GoldCoinService.CURRENCY, gold);
        if (points > 0) progress.rpg().addStatPoints(points);
        progress.markDirty();
        data.setDirty();
        String reward = "+" + gold + " ทอง" + (points > 0 ? " · +" + points + " แต้มสเตตัส" : "");
        player.sendSystemMessage(Component.literal("★ หลักไมล์เลเวล " + level + "! " + reward).withStyle(ChatFormatting.GOLD));
        if (big) {
            NpcSocial.rumor(player.getGameProfile().getName() + " เพิ่งก้าวถึงเลเวล " + level + " ได้ยินว่าแกร่งขึ้นมาก");
            player.server.getPlayerList().broadcastSystemMessage(Component.literal("★ " + player.getGameProfile().getName()
                    + " ก้าวถึงเลเวล " + level + " แล้ว!").withStyle(ChatFormatting.GOLD), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    private static void burst(ServerPlayer player, boolean milestone) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Fx.spiral(player, Fx.dust(0xFFD77A, milestone ? 1.3f : 1.0f), milestone ? 30 : 20, 0.85, milestone ? 2.6 : 2.0);
        Fx.ring(player, Fx.dust(0xFFE9B0, 0.9f), 22, 1.0);
        if (milestone) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.6, player.getZ(), 8, 0.15, 0.1, 0.15, 0.04);
        }
    }
}
