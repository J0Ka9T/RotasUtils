package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.schwarz.rotasutils.compat.PuffishSkillsCompat;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.LevelCurve;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.level.XpSourceConfig;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.reward.Reward;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ProgressService {
    private static final int MAX_RECEIPTS_PER_PLAYER = 65_536;
    private static final int MONSTER_RECEIPT_EVICTION_BATCH = 4_096;
    private static final String MONSTER_XP_RECEIPT_PREFIX = "monster_xp|";

    private ProgressService() {
    }

    public static void awardFromSource(ServerPlayer player, RotasData data, XpSource source, String antiFarmKey) {
        awardFromSource(player, data, source, antiFarmKey, data.levelConfig().source(source).baseAmount());
    }

    public static void awardFromSource(ServerPlayer player, RotasData data, XpSource source,
                                       String antiFarmKey, long baseAmount) {
        awardFromSource(player, data, source, antiFarmKey, baseAmount, null);
    }

    public static void awardFromSourceOnce(ServerPlayer player, RotasData data, XpSource source,
                                           String antiFarmKey, long baseAmount, UUID monsterId) {
        Objects.requireNonNull(monsterId, "monsterId");
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("XP awards require the server thread");
        }
        awardFromSource(player, data, source, antiFarmKey, baseAmount, monsterId);
    }

    private static void awardFromSource(ServerPlayer player, RotasData data, XpSource source,
                                        String antiFarmKey, long baseAmount, UUID monsterId) {
        LevelConfig config = data.levelConfig();
        XpSourceConfig sourceConfig = config.source(source);
        if (!sourceConfig.enabled() || baseAmount <= 0) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (sourceConfig.minLevel() > 0 && progress.level() < sourceConfig.minLevel()) {
            return;
        }
        if (sourceConfig.maxLevel() > 0 && progress.level() > sourceConfig.maxLevel()) {
            return;
        }
        if (!sourceConfig.dimension().isEmpty()
                && !player.level().dimension().location().toString().equals(sourceConfig.dimension())) {
            return;
        }
        if (sourceConfig.antiFarm() && !AntiFarm.allow(player, data, source, sourceConfig, antiFarmKey)) {
            return;
        }

        double eventMultiplier = EventService.multiplier(data,
                net.schwarz.rotasutils.event.EventType.of(source), antiFarmKey);
        double combo = source == XpSource.MOB_KILL || source == XpSource.BOSS_KILL
                ? FarmingService.comboXp(player, data) : 1.0;
        double mount = net.schwarz.rotasutils.server.horse.HorseTraitEffects.xpMultiplier(player, source)
                * BuffService.xpMultiplier(player, data, source)
                * ExplorationService.variety(player, data, source);
        long amount = Math.max(0L, Math.round(baseAmount * sourceConfig.multiplier() * eventMultiplier * combo * mount));
        if (amount <= 0) {
            return;
        }
        if (monsterId != null) {
            if (!claimMonsterXpReceipt(progress, monsterId)) {
                return;
            }
            data.setDirty();
        }
        addExperience(player, data, amount, false);

        double share = sourceConfig.partyShare();
        if (share <= 0 || progress.partyId() == null) {
            return;
        }
        long sharedAmount = Math.max(1L, Math.round(amount * share));
        double radius = data.serverSettings().partyNearbyRadius();
        double radiusSq = radius * radius;
        for (ServerPlayer member : PartyService.online(player.server, data, progress.partyId())) {
            if (member.getUUID().equals(player.getUUID()) || member.level() != player.level()) {
                continue;
            }
            if (member.distanceToSqr(player) > radiusSq) {
                continue;
            }
            if (monsterId != null) {
                if (!claimMonsterXpReceipt(data.progress(member.getUUID()), monsterId)) {
                    continue;
                }
                data.setDirty();
            }
            addExperience(member, data, sharedAmount, false);
            RotasNetwork.syncProgress(member);
        }
    }

    public record CombatAward(long baseXp, double multiplier, int monsterLevel) {
    }

    public static void awardCombat(ServerPlayer killer, RotasData data, XpSource source, String antiFarmKey,
                                   CombatAward award, UUID monsterId) {
        LevelConfig config = data.levelConfig();
        XpSourceConfig sourceConfig = config.source(source);
        if (!sourceConfig.enabled() || award.baseXp() <= 0) {
            return;
        }
        PlayerProgress killerProgress = data.progress(killer.getUUID());
        if (sourceConfig.minLevel() > 0 && killerProgress.level() < sourceConfig.minLevel()) return;
        if (sourceConfig.maxLevel() > 0 && killerProgress.level() > sourceConfig.maxLevel()) return;
        if (!sourceConfig.dimension().isEmpty()
                && !killer.level().dimension().location().toString().equals(sourceConfig.dimension())) return;
        if (sourceConfig.antiFarm() && !AntiFarm.allow(killer, data, source, sourceConfig, antiFarmKey)) return;

        var rules = SeasonService.rules(data);
        List<ServerPlayer> recipients = new java.util.ArrayList<>();
        recipients.add(killer);
        if (killerProgress.partyId() != null) {
            double radiusSq = rules.partyRadius * rules.partyRadius;
            for (ServerPlayer member : PartyService.online(killer.server, data, killerProgress.partyId())) {
                if (recipients.size() >= rules.partyMaxSize) break;
                if (member.getUUID().equals(killer.getUUID()) || member.level() != killer.level()
                        || member.isSpectator() || member.distanceToSqr(killer) > radiusSq) continue;
                recipients.add(member);
            }
        }
        double share = net.schwarz.rotasutils.level.SeasonMath.partyShare(recipients.size(), rules.partyBonusPerMember);
        double eventMultiplier = EventService.multiplier(data, net.schwarz.rotasutils.event.EventType.of(source), antiFarmKey);
        for (ServerPlayer recipient : recipients) {
            PlayerProgress progress = data.progress(recipient.getUUID());
            int level = progress.level();
            int monsterLevel = award.monsterLevel() > 0 ? award.monsterLevel() : 1;
            int effectiveLevel = (int) Math.min(monsterLevel, (long) level + rules.partyLevelReach);
            double xp = net.schwarz.rotasutils.level.SeasonMath.monsterXp(award.baseXp(), effectiveLevel, rules.monsterLevelBonus)
                    * Math.max(0, award.multiplier())
                    * net.schwarz.rotasutils.level.SeasonMath.overLevelMultiplier(level, monsterLevel,
                    rules.overLevelGrace, rules.overLevelPenaltyPerLevel, rules.overLevelMaxPenalty)
                    * net.schwarz.rotasutils.level.SeasonMath.catchUpMultiplier(level, rules.catchUpLevel, rules.catchUpBonus)
                    * share * sourceConfig.multiplier() * eventMultiplier
                    * (recipient == killer ? FarmingService.comboXp(killer, data) : 1.0)
                    * net.schwarz.rotasutils.server.horse.HorseTraitEffects.xpMultiplier(recipient, source)
                    * BuffService.xpMultiplier(recipient, data, source)
                    * ExplorationService.variety(recipient, data, source);
            long amount = !Double.isFinite(xp) ? 0 : Math.max(0, Math.round(Math.min(xp, 1_000_000_000d)));
            if (amount <= 0) continue;
            if (monsterId != null) {
                if (!claimMonsterXpReceipt(progress, monsterId)) continue;
                data.setDirty();
            }
            addExperience(recipient, data, amount, false);
            if (recipient != killer) RotasNetwork.syncProgress(recipient);
        }
    }

    static boolean claimMonsterXpReceipt(PlayerProgress progress, UUID monsterId) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(monsterId, "monsterId");
        String receipt = MONSTER_XP_RECEIPT_PREFIX + monsterId;
        Set<String> claimed = progress.claimedRewards();
        if (claimed.contains(receipt)) {
            return false;
        }
        if (claimed.size() >= MAX_RECEIPTS_PER_PLAYER) {
            evictOldestMonsterReceipts(claimed);
            if (claimed.size() >= MAX_RECEIPTS_PER_PLAYER) {
                return false;
            }
        }
        return progress.claimOnce(receipt);
    }

    private static void evictOldestMonsterReceipts(Set<String> claimed) {
        int toDrop = MONSTER_RECEIPT_EVICTION_BATCH;
        for (Iterator<String> it = claimed.iterator(); toDrop > 0 && it.hasNext(); ) {
            if (it.next().startsWith(MONSTER_XP_RECEIPT_PREFIX)) {
                it.remove();
                toDrop--;
            }
        }
    }

    public static int addExperience(ServerPlayer player, RotasData data, long amount, boolean fromQuest) {
        if (!player.server.isSameThread()) { throw new IllegalStateException("XP awards require the server thread"); }
        if (amount <= 0) {
            return 0;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        double multiplier = PuffishSkillsCompat.active(data)
                ? 1.0
                : SkillService.experienceMultiplier(data, progress, fromQuest);
        long scaled = net.schwarz.rotasutils.level.ProgressionMath.scale(amount, multiplier);
        if (scaled == 0) { return 0; }
        var main=data.job(progress.mainJob());
        if(main!=null&&main.enabled()) JobService.grantMastery(progress,main,net.schwarz.rotasutils.job.JobSlot.MAIN,scaled);
        var sub=data.job(progress.subJob());
        boolean subFromCombat = !SeasonService.active(data) || !SeasonService.rules(data).subJobProductionOnly;
        if(sub!=null&&sub.enabled()&&subFromCombat) JobService.grantMastery(progress,sub,net.schwarz.rotasutils.job.JobSlot.SUB,scaled);
        if (progress.level() >= data.levelConfig().curve().maxLevel()) {
            long tokens = SeasonService.overflowMaxLevel(data, progress, scaled);
            if (tokens > 0) {
                player.sendSystemMessage(Component.literal("EXP ล้นเลเวลสูงสุด → +" + tokens + " โทเคน").withStyle(ChatFormatting.AQUA));
            }
        }
        int levelsGained = net.schwarz.rotasutils.level.ProgressionMath.award(progress,
                data.levelConfig().curve(), scaled, level -> onLevelReached(player, data, progress, level));
        data.setDirty();
        if (levelsGained > 0) {
            CharacterStatService.grantLevelPoints(progress, data);
            if (!PuffishSkillsCompat.active(data)) {
                SkillService.recalculate(player, data);
            }
            CharacterStatService.apply(player, data);
        }
        syncVanillaLevelMirror(player, data);
        if (levelsGained > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.7f, 1.2f);
            RpgKernel.emit(player, "rotas:player_level_changed", java.util.UUID.randomUUID().toString(),
                    java.util.Map.of("event.levels_gained", Integer.toString(levelsGained)));
        }
        return levelsGained;
    }

    private static void onLevelReached(ServerPlayer player, RotasData data, PlayerProgress progress, int level) {
        LevelConfig config = data.levelConfig();
        int points = level % config.skillPointInterval() == 0 ? config.skillPointsPerLevel() : 0;
        int puffishCategories = 0;
        if (points > 0) {
            if (PuffishSkillsCompat.active(data)) {
                puffishCategories = PuffishSkillsCompat.addPointsToUnlockedCategories(player, points);
                if (puffishCategories == 0) {
                    progress.addSkillPoints(points);
                }
            } else {
                progress.addSkillPoints(points);
            }
        }

        List<Reward> rewards = config.levelRewards(level);
        if (!rewards.isEmpty()) {
            RewardService.grant(player, data, rewards, RewardService.Context.levelUp(level));
        }
        refreshClearance(player, data, progress);
        MilestoneService.onLevel(player, data, progress, level);
        EventService.fire(player, data, net.schwarz.rotasutils.event.EventType.LEVEL_UP, String.valueOf(level));
        TitleService.onProgress(player, data);

        if (config.announceLevelUp() && !"false".equals(progress.questVariables().get("pref.pref_toast"))) {
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.level.up", level)
                    .withStyle(ChatFormatting.GOLD));
            if (points > 0) {
                player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c(puffishCategories > 0
                        ? "rotasutils.msg.level.puffish_points" : "rotasutils.msg.level.skill_points", points)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    public static void syncVanillaLevelMirror(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.peek(player.getUUID());
        if (progress == null) {
            return;
        }
        int level = progress.level();
        long needed = data.levelConfig().curve().xpToNext(level);
        float fraction = needed <= 0 || needed == Long.MAX_VALUE
                ? 0.0f
                : (float) Math.max(0.0, Math.min(1.0, progress.xp() / (double) needed));
        int totalMirror = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, progress.totalXp()));

        if (player.experienceLevel != level
                || Math.abs(player.experienceProgress - fraction) > 0.0001f
                || player.totalExperience != totalMirror) {
            player.experienceLevel = level;
            player.experienceProgress = fraction;
            player.totalExperience = totalMirror;
        }
    }

    public static void refreshClearance(ServerPlayer player, RotasData data, PlayerProgress progress) {
        LevelConfig config = data.levelConfig();
        if (!config.rankRequirementsEnabled()) {
            for (DangerRank rank : DangerRank.VALUES) {
                progress.grantClearance(rank);
            }
            return;
        }
        if (SeasonService.active(data)) {
            DangerRank reached = SeasonService.rank(progress, SeasonService.rules(data));
            for (DangerRank rank : DangerRank.VALUES) {
                if (rank.ordinal() <= reached.ordinal() && progress.grantClearance(rank) && rank != DangerRank.F) {
                    player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.rank.unlocked", rank.display())
                            .withStyle(rank.color()));
                }
            }
            return;
        }
        for (DangerRank rank : DangerRank.VALUES) {
            if (progress.hasClearance(rank) || !config.rankAutoGrant(rank)) {
                continue;
            }
            if (progress.level() < config.rankLevel(rank)) {
                continue;
            }
            int questsNeeded = config.rankQuestsRequired(rank);
            if (questsNeeded > 0 && rank.ordinal() > 0) {
                DangerRank below = DangerRank.VALUES[rank.ordinal() - 1];
                int done = progress.completionsOfRank(below, id -> {
                    QuestDef quest = data.quest(id);
                    return quest == null ? null : quest.rank();
                });
                if (done < questsNeeded) {
                    continue;
                }
            }
            String promotion = config.rankPromotionQuest(rank);
            if (!promotion.isEmpty() && progress.completionCount(promotion) <= 0) {
                continue;
            }
            if (progress.grantClearance(rank)) {
                player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.rank.unlocked", rank.display())
                        .withStyle(rank.color()));
            }
        }
    }

    public static String nextClearanceSummary(RotasData data, PlayerProgress progress) {
        LevelConfig config = data.levelConfig();
        if (SeasonService.active(data)) {
            var rules = SeasonService.rules(data);
            String next = SeasonService.nextRank(progress, rules);
            if (next == null) {
                return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.rank.all_unlocked");
            }
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.season.rank_next", next,
                    net.schwarz.rotasutils.level.SeasonMath.rankThreshold(next, rules.seasonRankTotal, rules.rankThresholds),
                    progress.rankPoints());
        }
        for (DangerRank rank : DangerRank.VALUES) {
            if (progress.hasClearance(rank)) {
                continue;
            }
            StringBuilder builder = new StringBuilder(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.rank.requires", rank.display()));
            builder.append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.req.level", config.rankLevel(rank)));
            int quests = config.rankQuestsRequired(rank);
            if (quests > 0 && rank.ordinal() > 0) {
                builder.append(", ").append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.rank.quests", quests,
                        DangerRank.VALUES[rank.ordinal() - 1].display()));
            }
            String promotion = config.rankPromotionQuest(rank);
            if (!promotion.isEmpty()) {
                QuestDef quest = data.quest(promotion);
                builder.append(", \"").append(quest == null ? promotion : quest.name()).append("\"");
            }
            return builder.toString();
        }
        return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.rank.all_unlocked");
    }

    public static final int STAT_SYSTEM_VERSION = 1;

    public static int migrateStatSystem(RotasData data) {
        if (data.statSystemVersion() >= STAT_SYSTEM_VERSION) {
            return 0;
        }
        int wiped = 0;
        int startLevel = data.levelConfig().startingLevel();
        for (PlayerProgress progress : data.allPlayersToModify()) {
            CharacterStatService.wipeProgression(progress, data, startLevel);
            wiped++;
        }
        data.setStatSystemVersion(STAT_SYSTEM_VERSION);
        data.audit("stat system v" + STAT_SYSTEM_VERSION + ": wiped level, stats and skills of " + wiped + " players");
        return wiped;
    }

    public static void setLevel(ServerPlayer player, RotasData data, int level) {
        PlayerProgress progress = data.progress(player.getUUID());
        int clamped = Math.max(1, Math.min(data.levelConfig().curve().maxLevel(), level));
        progress.setLevel(clamped);
        progress.setXp(0);
        refreshClearance(player, data, progress);
        CharacterStatService.grantLevelPoints(progress, data);
        if (!PuffishSkillsCompat.active(data)) {
            SkillService.recalculate(player, data);
        }
        CharacterStatService.apply(player, data);
        syncVanillaLevelMirror(player, data);
        data.setDirty();
    }
}
