package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which accepted quest the HUD tracker follows.
 *
 * <p>Tracking is a client-side view preference, not progression: the server neither knows
 * nor cares which quest a player is watching, so nothing here needs a packet. The choice
 * survives a restart through one small file next to the game's other client settings.</p>
 *
 * <p>When nothing is tracked - a fresh profile, or the tracked quest was turned in - the
 * tracker falls back to whichever accepted quest is closest to done, so the panel is
 * useful before the player has ever pressed Track.</p>
 */
@Environment(EnvType.CLIENT)
public final class ClientQuestTracker {
    private static final String FILE_NAME = "rotasutils-tracked-quest.txt";
    /** Empty means "no explicit choice"; the resolver then picks one. */
    private static String trackedId = "";
    private static boolean loaded;
    /** Set when the player explicitly clears tracking, so the fallback stays off. */
    private static boolean suppressed;

    private ClientQuestTracker() {
    }

    /** The quest the HUD should show, or empty when there is nothing to show. */
    public static String resolved() {
        load();
        PlayerProgress progress = ClientState.progress();
        if (!trackedId.isEmpty() && progress.active(trackedId) != null) {
            return trackedId;
        }
        if (suppressed) {
            return "";
        }
        // Fall back to the active quest that is furthest along, so the panel opens on
        // the contract the player is most likely finishing.
        String best = "";
        double bestFraction = -1d;
        for (ActiveQuest active : progress.activeQuests().values()) {
            QuestDef quest = ClientState.quest(active.questId());
            if (quest == null) {
                continue;
            }
            double fraction = active.turnInReady() ? 2d : fractionDone(quest, active);
            if (fraction > bestFraction) {
                bestFraction = fraction;
                best = active.questId();
            }
        }
        return best;
    }

    /** Completed objectives over total, counting a required objective only. */
    public static double fractionDone(QuestDef quest, ActiveQuest active) {
        int total = 0;
        int done = 0;
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (quest.objectives().get(i).optional()) {
                continue;
            }
            total++;
            if (active.isComplete(i)) {
                done++;
            }
        }
        return total == 0 ? 0d : (double) done / total;
    }

    public static boolean isTracked(String questId) {
        return !questId.isEmpty() && questId.equals(resolved());
    }

    /** Explicit choice from the quest page. Tracking the tracked quest clears it. */
    public static void toggle(String questId) {
        load();
        if (questId.equals(resolved())) {
            trackedId = "";
            suppressed = true;
        } else {
            trackedId = questId;
            suppressed = false;
        }
        save();
    }

    /** Called when a quest is accepted so the newest contract takes the panel. */
    public static void track(String questId) {
        load();
        trackedId = questId == null ? "" : questId;
        suppressed = false;
        save();
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) {
                return;
            }
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (value.equals("-")) {
                suppressed = true;
            } else {
                trackedId = value;
            }
        } catch (IOException | RuntimeException failure) {
            // A preference file is never worth interrupting a client for.
            Rotasutils.LOG.debug("Could not read the quest tracker preference", failure);
        }
    }

    private static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, suppressed && trackedId.isEmpty() ? "-" : trackedId,
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException failure) {
            Rotasutils.LOG.debug("Could not save the quest tracker preference", failure);
        }
    }
}
