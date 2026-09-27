package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.quest.DangerRank;

import java.util.List;

/** Per-rank clearance requirements and experience multiplier. */
@Environment(EnvType.CLIENT)
public class RankSettingsScreen extends SimpleFieldScreen {
    private final LevelConfig config;
    private final DangerRank rank;

    public RankSettingsScreen(LevelConfig config, DangerRank rank, Screen parent) {
        super(rank.display() + "-Rank Settings", parent);
        this.config = config;
        this.rank = rank;
    }

    @Override
    protected void collectFields(List<Field> target) {
        target.add(number("Required level",
                () -> String.valueOf(config.rankLevel(rank)),
                value -> config.setRankLevel(rank, parseInt(value, rank.defaultLevel()))));
        target.add(decimal("Experience multiplier",
                () -> String.valueOf(config.rankMultiplier(rank)),
                value -> config.setRankMultiplier(rank, (float) parseDouble(value, rank.defaultMultiplier()))));
        target.add(number("Quests of the rank below required",
                () -> String.valueOf(config.rankQuestsRequired(rank)),
                value -> config.setRankQuestsRequired(rank, parseInt(value, 0))));
        target.add(pickQuest("Promotion quest",
                () -> config.rankPromotionQuest(rank).isEmpty() ? "(none)" : config.rankPromotionQuest(rank),
                value -> config.setRankPromotionQuest(rank, value)));
        target.add(toggle("Grant automatically",
                () -> config.rankAutoGrant(rank) ? "Yes" : "No",
                value -> config.setRankAutoGrant(rank, !config.rankAutoGrant(rank))));
    }
}
