package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.ProgressionRewards;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.progress.PlayerProgress;

/**
 * Level milestones: every few levels a player is paid in gold and stat points, and the big ones (10, 25, 50...)
 * are announced to the server. Every level-up gets a burst of light; a milestone gets the big screen title.
 */
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
        if (rules.celebrate) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("เลเวล " + level)
                    .withStyle(big ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("หลักไมล์ · " + reward)
                    .withStyle(ChatFormatting.WHITE)));
        }
        if (big) {
            NpcSocial.rumor(player.getGameProfile().getName() + " เพิ่งก้าวถึงเลเวล " + level + " ได้ยินว่าแกร่งขึ้นมาก");
            player.server.getPlayerList().broadcastSystemMessage(Component.literal("★ " + player.getGameProfile().getName()
                    + " ก้าวถึงเลเวล " + level + " แล้ว!").withStyle(ChatFormatting.GOLD), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    /** A ring of light rising round the player; a milestone adds a firework and a totem flash. */
    private static void burst(ServerPlayer player, boolean milestone) {
        if (!(player.level() instanceof ServerLevel level)) return;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(angle) * 0.9, y + 0.2, z + Math.sin(angle) * 0.9,
                    0, 0, 0.35, 0, 1);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1, z, 12, 0.4, 0.6, 0.4, 0);
        if (milestone) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.2, z, 60, 0.6, 0.8, 0.6, 0.35);
            level.sendParticles(ParticleTypes.FIREWORK, x, y + 2.2, z, 40, 0.2, 0.2, 0.2, 0.25);
        }
    }
}
