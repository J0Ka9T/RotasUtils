package net.schwarz.rotasutils.client.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.compat.CuriosClientCompat;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data adapter between Rotas systems and the Character Hub renderer.
 *
 * <p>The renderer never invents job, subclass, quest, party or accessory state. It consumes this
 * snapshot, which is built from the server-owned Rotas mirror, Pufferfish category summaries and
 * the live Curios inventory.</p>
 */
@Environment(EnvType.CLIENT)
public final class CharacterHubModel {
    public record SkillPath(String categoryId, String displayName, int pointsLeft, int pointsTotal,
                            int pointsSpent, int skillsUnlocked, int skillsTotal) {
        public float skillProgress() {
            return skillsTotal <= 0 ? 0f : Math.min(1f, skillsUnlocked / (float) skillsTotal);
        }
    }

    public record QuestEntry(String id, QuestDef definition, ActiveQuest active) {
    }

    public record Snapshot(String playerName, int level, long xp, long xpForNext,
                           String clearance, String jobName, String subName,
                           SkillPath jobPath, SkillPath subPath,
                           List<ClientState.PartyMember> party,
                           List<QuestEntry> quests,
                           List<CuriosClientCompat.Entry> curios,
                           boolean puffishAvailable,
                           boolean curiosAvailable) {
        public boolean hasSub() {
            return subPath != null || (subName != null && !subName.isBlank());
        }

        public float levelProgress() {
            if (xpForNext <= 0 || xpForNext == Long.MAX_VALUE) return 1f;
            return Math.max(0f, Math.min(1f, xp / (float) xpForNext));
        }
    }

    private static UUID cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static Snapshot cachedSnapshot;

    private CharacterHubModel() {
    }

    /**
     * Builds the Character Hub's relatively expensive cross-system snapshot at most once per client tick.
     * Rendering can run at hundreds of FPS, while quest/party/Curios/Pufferfish state only needs tick-rate refresh.
     */
    public static Snapshot capture(LocalPlayer player) {
        if (player == null) throw new IllegalArgumentException("player");
        UUID playerId = player.getUUID();
        if (cachedSnapshot != null && playerId.equals(cachedPlayer) && cachedTick == player.tickCount) {
            return cachedSnapshot;
        }

        PlayerProgress progress = ClientState.progress();
        List<ClientState.SkillCategorySummary> categories = new ArrayList<>(ClientState.puffishCategories());
        // Without an explicit profile mapping, prefer the category the player has invested in most.
        categories.sort(Comparator
                .comparingInt(ClientState.SkillCategorySummary::pointsTotal).reversed()
                .thenComparing(Comparator.comparingInt(ClientState.SkillCategorySummary::pointsSpent).reversed())
                .thenComparing(ClientState.SkillCategorySummary::id));

        String requestedJob = variable(progress, "profile.job_category", "job_category");
        String requestedSub = variable(progress, "profile.sub_category", "sub_category");
        ClientState.SkillCategorySummary primary = choose(categories, requestedJob, null);
        ClientState.SkillCategorySummary secondary = choose(categories, requestedSub, primary == null ? null : primary.id());

        SkillPath jobPath = toPath(primary);
        SkillPath subPath = toPath(secondary);
        String jobName = variable(progress, "profile.job", "job");
        if (jobName.isBlank() && jobPath != null) jobName = jobPath.displayName();
        String subName = variable(progress, "profile.sub", "sub");
        if (subName.isBlank() && subPath != null) subName = subPath.displayName();
        if (!ClientState.pufferfishSkills()) {
            // Built-in trees: the job slot is the chosen Rotas job, the second slot is the Origins race.
            String job = progress.job();
            jobName = job.isBlank() ? "" : ClientState.jobName(job);
            jobPath = job.isBlank() ? null : rotasPath(category -> category.jobs().contains(job), jobName, progress);
            List<String> races = ClientState.races();
            subName = String.join(", ", races.stream().map(ClientState::raceName).toList());
            subPath = races.isEmpty() ? null
                    : rotasPath(category -> category.races().stream().anyMatch(races::contains), subName, progress);
        }

        List<QuestEntry> quests = new ArrayList<>();
        for (Map.Entry<String, ActiveQuest> entry : progress.activeQuests().entrySet()) {
            QuestDef def = ClientState.quest(entry.getKey());
            if (def != null) quests.add(new QuestEntry(entry.getKey(), def, entry.getValue()));
        }
        quests.sort(Comparator
                .comparing((QuestEntry q) -> !q.active().turnInReady())
                .thenComparing(q -> q.definition().rank().ordinal(), Comparator.reverseOrder())
                .thenComparing(q -> q.definition().name(), String.CASE_INSENSITIVE_ORDER));

        Snapshot snapshot = new Snapshot(
                player.getGameProfile().getName(),
                progress.level(), progress.xp(), ClientState.xpForNextLevel(),
                progress.highestClearance().display(),
                jobName, subName,
                jobPath, subPath,
                List.copyOf(ClientState.party()),
                List.copyOf(quests),
                CuriosClientCompat.entries(player),
                ClientState.puffishAvailable(),
                CuriosClientCompat.isReady()
        );
        cachedPlayer = playerId;
        cachedTick = player.tickCount;
        cachedSnapshot = snapshot;
        return snapshot;
    }

    public static boolean tabAvailable(CharacterHubTab tab, Snapshot snapshot) {
        return switch (tab) {
            case BAG, QUEST -> true;
            case PARTY -> ClientState.partyEnabled();
            case JOB -> snapshot.puffishAvailable() || !snapshot.jobName().isBlank();
            case SUB -> snapshot.hasSub();
        };
    }

    private static ClientState.SkillCategorySummary choose(List<ClientState.SkillCategorySummary> categories,
                                                            String requested, String excludedId) {
        if (requested != null && !requested.isBlank()) {
            for (ClientState.SkillCategorySummary category : categories) {
                if (category.id().equals(requested)) return category;
            }
        }
        for (ClientState.SkillCategorySummary category : categories) {
            if (excludedId == null || !excludedId.equals(category.id())) return category;
        }
        return null;
    }

    /** Summarises every Rotas tree matching {@code filter}, or null when none match. */
    private static SkillPath rotasPath(java.util.function.Predicate<net.schwarz.rotasutils.skill.SkillCategory> filter,
                                       String name, PlayerProgress progress) {
        String firstId = null;
        int total = 0;
        int unlocked = 0;
        for (net.schwarz.rotasutils.skill.SkillCategory category : ClientState.categories().values()) {
            if (!filter.test(category)) continue;
            if (firstId == null) firstId = category.id();
            total += category.nodes().size();
            unlocked += (int) category.nodes().keySet().stream().filter(id -> progress.skillRank(id) > 0).count();
        }
        return firstId == null ? null : new SkillPath(firstId, name, progress.skillPoints(),
                progress.totalPointsEarned(), progress.totalPointsSpent(), unlocked, total);
    }

    private static SkillPath toPath(ClientState.SkillCategorySummary category) {
        if (category == null) return null;
        return new SkillPath(category.id(), category.displayName(), category.pointsLeft(), category.pointsTotal(),
                category.pointsSpent(), category.skillsUnlocked(), category.skillsTotal());
    }

    private static String variable(PlayerProgress progress, String primary, String fallback) {
        String value = progress.questVariables().getOrDefault(primary, "").trim();
        if (!value.isBlank()) return value;
        return progress.questVariables().getOrDefault(fallback, "").trim();
    }
}
