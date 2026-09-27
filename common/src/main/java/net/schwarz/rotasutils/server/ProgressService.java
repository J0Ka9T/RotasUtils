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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Experience, levels, skill points and rank clearance. Server side only. */
public final class ProgressService {
    private static final int MAX_RECEIPTS_PER_PLAYER = 65_536;
    private static final String MONSTER_XP_RECEIPT_PREFIX = "monster_xp|";

    private ProgressService() {
    }

    /**
     * Awards the source's configured fixed amount. Dynamic combat XP uses the overload
     * that accepts a live amount calculated from the killed monster.
     */
    public static void awardFromSource(ServerPlayer player, RotasData data, XpSource source, String antiFarmKey) {
        awardFromSource(player, data, source, antiFarmKey, data.levelConfig().source(source).baseAmount());
    }

    /**
     * Awards a source-specific amount while still honouring the source multiplier,
     * level/dimension gates, anti-farm policy and nearby-party sharing.
     */
    public static void awardFromSource(ServerPlayer player, RotasData data, XpSource source,
                                       String antiFarmKey, long baseAmount) {
        awardFromSource(player, data, source, antiFarmKey, baseAmount, null);
    }

    /**
     * Awards XP for a persisted monster at most once to each recipient. The receipt is
     * recorded before XP delivery because level-up rewards and compatibility hooks may
     * re-enter progression code. If a downstream hook throws, the receipt remains claimed;
     * delivery across external hooks is deliberately fail-closed rather than atomic.
     */
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

        // The event catalogue gets the last word on the amount, and the anti-farm key is the thing the
        // event happened to - the mob, the block, the item - so a rule can name one mod or one id.
        double eventMultiplier = EventService.multiplier(data,
                net.schwarz.rotasutils.event.EventType.of(source), antiFarmKey);
        double combo = source == XpSource.MOB_KILL || source == XpSource.BOSS_KILL
                ? FarmingService.comboXp(player, data) : 1.0;
        double mount = net.schwarz.rotasutils.server.horse.HorseTraitEffects.xpMultiplier(player, source)
                * BuffService.xpMultiplier(player, data, source);
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

    /**
     * One kill under the season rules, before any recipient-specific scaling.
     *
     * @param baseXp       the monster's base EXP
     * @param multiplier   zone, repetition and boss/miniboss multipliers already combined
     * @param monsterLevel the monster's level, or 0 when it has none (vanilla mob without a profile)
     */
    public record CombatAward(long baseXp, double multiplier, int monsterLevel) {
    }

    /**
     * Season combat EXP: {@code base * (1 + bonus * level)}, shared by the killer's party in range. The pool
     * grows by the party bonus per extra member and is split evenly. Each member earns as if the monster were
     * at most {@code partyLevelReach} levels above them, and the over-level penalty uses their own level, so a
     * high-level carry cannot power-level newcomers and a veteran farming weak mobs earns little.
     */
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
        // The season path pays kills too, so it answers to the event catalogue exactly like the plain path.
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
                    * share * sourceConfig.multiplier() * eventMultiplier
                    // A combo is the killer's own streak; party members share the kill, not the streak.
                    * (recipient == killer ? FarmingService.comboXp(killer, data) : 1.0)
                    * net.schwarz.rotasutils.server.horse.HorseTraitEffects.xpMultiplier(recipient, source)
                    * BuffService.xpMultiplier(recipient, data, source);
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
        if (progress.claimedRewards().contains(receipt)
                || progress.claimedRewards().size() >= MAX_RECEIPTS_PER_PLAYER) {
            return false;
        }
        return progress.claimOnce(receipt);
    }

    /** Adds experience and resolves every level-up it triggers. */
    public static int addExperience(ServerPlayer player, RotasData data, long amount, boolean fromQuest) {
        if (!player.server.isSameThread()) { throw new IllegalStateException("XP awards require the server thread"); }
        if (amount <= 0) {
            return 0;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        // RotasCommu uses Pufferfish Skills as the build layer. When it is present, legacy
        // Rotas skill-tree XP multipliers must not silently influence the new level system.
        double multiplier = PuffishSkillsCompat.active(data)
                ? 1.0
                : SkillService.experienceMultiplier(data, progress, fromQuest);
        long scaled = net.schwarz.rotasutils.level.ProgressionMath.scale(amount, multiplier);
        if (scaled == 0) { return 0; }
        var main=data.job(progress.mainJob());
        if(main!=null&&main.enabled()) JobService.grantMastery(progress,main,net.schwarz.rotasutils.job.JobSlot.MAIN,scaled);
        var sub=data.job(progress.subJob());
        // In a season the sub job only learns from production, never from combat or quest EXP.
        boolean subFromCombat = !SeasonService.active(data) || !SeasonService.rules(data).subJobProductionOnly;
        if(sub!=null&&sub.enabled()&&subFromCombat) JobService.grantMastery(progress,sub,net.schwarz.rotasutils.job.JobSlot.SUB,scaled);
        int levelsGained = net.schwarz.rotasutils.level.ProgressionMath.award(progress,
                data.levelConfig().curve(), scaled, level -> onLevelReached(player, data, progress, level));
        data.setDirty();
        if (levelsGained > 0) {
            CharacterStatService.grantLevelPoints(progress, data);
            if (!PuffishSkillsCompat.active(data)) {
                SkillService.recalculate(player, data);
            }
            // New stat points and level-scaled job bonuses have to reach the attributes right away.
            CharacterStatService.apply(player, data);
        }
        syncVanillaLevelMirror(player, data);
        if (levelsGained > 0) {
            // One sound per award, not per level: a multi-level grant would otherwise stack
            // the jingle on itself for every level crossed in the same tick.
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.7f, 1.2f);
            RpgKernel.emit(player, "rotas:player_level_changed", java.util.UUID.randomUUID().toString(),
                    java.util.Map.of("event.levels_gained", Integer.toString(levelsGained)));
        }
        return levelsGained;
    }

    private static void onLevelReached(ServerPlayer player, RotasData data, PlayerProgress progress, int level) {
        // Stat points are granted per award by CharacterStatService.grantLevelPoints.
        LevelConfig config = data.levelConfig();
        int points = level % config.skillPointInterval() == 0 ? config.skillPointsPerLevel() : 0;
        int puffishCategories = 0;
        if (points > 0) {
            if (PuffishSkillsCompat.active(data)) {
                puffishCategories = PuffishSkillsCompat.addPointsToUnlockedCategories(player, points);
                // Do not destroy earned progression when the server datapack has not unlocked a
                // Puffish category yet. Legacy points act as a small compatibility escrow.
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
        EventService.fire(player, data, net.schwarz.rotasutils.event.EventType.LEVEL_UP, String.valueOf(level));
        // A level is a title condition of its own, and the level-up rewards may have moved the wallet too.
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

    /**
     * Keeps vanilla's public level fields as a read-only compatibility mirror for mods that
     * inspect Player.experienceLevel. No vanilla XP is accumulated or spent.
     */
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

        // These fields are compatibility projections only. PlayerExperienceMixin rejects all
        // vanilla mutations, and the vanilla HUD is hidden by GuiExperienceMixin.
        if (player.experienceLevel != level
                || Math.abs(player.experienceProgress - fraction) > 0.0001f
                || player.totalExperience != totalMirror) {
            player.experienceLevel = level;
            player.experienceProgress = fraction;
            player.totalExperience = totalMirror;
        }
    }

    /**
     * Re-evaluates every rank clearance the player could hold.
     *
     * <p>A rank is auto-granted when its level requirement and its "complete N quests
     * of the rank below" requirement are both met, and any configured promotion quest
     * has been completed. Manually granted clearances are never revoked here.
     */
    public static void refreshClearance(ServerPlayer player, RotasData data, PlayerProgress progress) {
        LevelConfig config = data.levelConfig();
        if (!config.rankRequirementsEnabled()) {
            for (DangerRank rank : DangerRank.VALUES) {
                progress.grantClearance(rank);
            }
            return;
        }
        if (SeasonService.active(data)) {
            // Season ranks come from rank points alone. SSS stays a manual, event-only grant.
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

    /** Lists the outstanding requirements for the next clearance the player lacks. */
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

    /**
     * Revision of the level and stat system. Raising it wipes every character's level, EXP, stat points and
     * skill trees once, on the next server start. 1 = the four fixed stats (2026-09-25 rebuild).
     */
    public static final int STAT_SYSTEM_VERSION = 1;

    /** Runs once per world per {@link #STAT_SYSTEM_VERSION}, before anyone can join. */
    public static int migrateStatSystem(RotasData data) {
        if (data.statSystemVersion() >= STAT_SYSTEM_VERSION) {
            return 0;
        }
        int wiped = 0;
        int startLevel = data.levelConfig().startingLevel();
        for (PlayerProgress progress : data.allPlayers()) {
            CharacterStatService.wipeProgression(progress, data, startLevel);
            wiped++;
        }
        data.setStatSystemVersion(STAT_SYSTEM_VERSION);
        data.audit("stat system v" + STAT_SYSTEM_VERSION + ": wiped level, stats and skills of " + wiped + " players");
        return wiped;
    }

    /** Sets a level directly, used by the admin player progress manager. */
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
        // Level-scaled job bonuses have to reach the attributes, same as a natural level-up.
        CharacterStatService.apply(player, data);
        syncVanillaLevelMirror(player, data);
        data.setDirty();
    }
}
