package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.schwarz.rotasutils.core.FarmingMath;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * The monster book (สมุดมอนสเตอร์).
 *
 * <p>Every kind of hostile monster a player kills has its own count, and the count climbs rungs. A rung
 * is knowledge: a little more damage against that kind, a little more loot from it, rank points once,
 * and - from the reveal rung on - the book lists what that monster drops. It rewards the long farm of
 * one monster, which is exactly the loop a loot-driven server wants.</p>
 */
public final class BestiaryService {
    private BestiaryService() {
    }

    private static SeasonRules.BestiaryRules rules(RotasData data) {
        return SeasonService.rules(data).bestiary;
    }

    /** True for the monsters the book records: hostile creatures, not farm animals. */
    public static boolean recordable(LivingEntity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
    }

    /** One kill in the book; announces a rung the moment it is reached and pays its rank points. */
    public static void onKill(ServerPlayer player, RotasData data, LivingEntity victim, String entityId) {
        SeasonRules.BestiaryRules book = rules(data);
        if (book == null || !book.enabled || !recordable(victim)) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        long before = progress.bestiaryKills(entityId);
        long after = progress.addBestiaryKill(entityId, SeasonRules.BestiaryRules.MAX_ENTRIES);
        if (after < 0) {
            return;
        }
        data.setDirty();
        int oldTier = FarmingMath.bestiaryTier(before, book.tiers);
        int newTier = FarmingMath.bestiaryTier(after, book.tiers);
        if (before == 0) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.bestiary.new", victim.getType().getDescription().getString())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (newTier > oldTier) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.bestiary.tier",
                    victim.getType().getDescription().getString(), newTier,
                    Math.round(newTier * book.damagePerTier * 100)).withStyle(ChatFormatting.GREEN));
            if (book.rankPointsPerTier > 0) {
                progress.addRankPoints(book.rankPointsPerTier);
                ProgressService.refreshClearance(player, data, progress);
            }
        }
    }

    /** The rung this player has reached against one kind of monster. */
    public static int tier(RotasData data, PlayerProgress progress, String entityId) {
        SeasonRules.BestiaryRules book = rules(data);
        if (book == null || !book.enabled || progress == null) {
            return 0;
        }
        return FarmingMath.bestiaryTier(progress.bestiaryKills(entityId), book.tiers);
    }

    /** What the book multiplies this player's damage against that kind by. */
    public static double damage(RotasData data, PlayerProgress progress, String entityId) {
        SeasonRules.BestiaryRules book = rules(data);
        int tier = tier(data, progress, entityId);
        return tier <= 0 ? 1.0 : 1.0 + tier * book.damagePerTier;
    }

    /** What the book multiplies loot chances from that kind by. */
    public static double loot(RotasData data, PlayerProgress progress, String entityId) {
        SeasonRules.BestiaryRules book = rules(data);
        int tier = tier(data, progress, entityId);
        return tier <= 0 ? 1.0 : 1.0 + tier * book.lootPerTier;
    }

    /** True once the book lists this monster's drops for this player. */
    public static boolean revealed(RotasData data, PlayerProgress progress, String entityId) {
        SeasonRules.BestiaryRules book = rules(data);
        return book != null && book.enabled && tier(data, progress, entityId) >= Math.max(1, book.revealDropsAt);
    }
}
