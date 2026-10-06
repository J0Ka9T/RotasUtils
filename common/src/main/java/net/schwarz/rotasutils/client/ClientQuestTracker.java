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

@Environment(EnvType.CLIENT)
public final class ClientQuestTracker {
    private static final String FILE_NAME = "rotasutils-tracked-quest.txt";
    private static String trackedId = "";
    private static boolean loaded;
    private static boolean suppressed;

    private ClientQuestTracker() {
    }

    public static String resolved() {
        load();
        PlayerProgress progress = ClientState.progress();
        if (!trackedId.isEmpty() && progress.active(trackedId) != null) {
            return trackedId;
        }
        if (suppressed) {
            return "";
        }
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
