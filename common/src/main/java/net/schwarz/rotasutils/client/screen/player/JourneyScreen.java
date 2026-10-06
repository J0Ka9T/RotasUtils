package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.DailyTrack;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class JourneyScreen extends RotasScreen {
    private static final int MISSION_ROWS = 6;
    private static final int SEASON_ROWS = 7;

    public enum Tab { TODAY, SEASON }

    private final List<String> missions;
    private final String boardId;
    private Tab tab;
    private int scroll;

    public JourneyScreen(CompoundTag payload, Screen parent) {
        super(L.t("rotasutils.journey.title"), parent);
        CompoundTag state = payload == null ? new CompoundTag() : payload;
        this.missions = Nbt.loadStrings(state, "missions");
        this.boardId = state.getString("board");
        this.tab = "SEASON".equals(state.getString("tab")) ? Tab.SEASON : Tab.TODAY;
    }

    private static CompoundTag daily() {
        return ClientState.tracks().getCompound("daily");
    }

    private static CompoundTag season() {
        return ClientState.tracks().getCompound("season");
    }

    private static long[] thresholds(CompoundTag track) {
        ListTag tiers = track.getList("tiers", Tag.TAG_COMPOUND);
        long[] values = new long[tiers.size()];
        for (int index = 0; index < tiers.size(); index++) {
            values[index] = tiers.getCompound(index).getLong("needed");
        }
        return values;
    }

    @Override
    public void onDataRefreshed() {
        rebuildWidgets();
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 520);
        guiHeight = Ui.fill(height, 340);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        addRenderableWidget(Ui.boardTab(L.c("rotasutils.journey.today"), tab == Tab.TODAY, button -> {
            tab = Tab.TODAY;
            scroll = 0;
            rebuildWidgets();
        }).bounds(guiLeft + 16, guiTop + 38, 110, 20).build());
        addRenderableWidget(Ui.boardTab(L.c("rotasutils.journey.season"), tab == Tab.SEASON, button -> {
            tab = Tab.SEASON;
            scroll = 0;
            rebuildWidgets();
        }).bounds(guiLeft + 130, guiTop + 38, 110, 20).build());

        if (tab == Tab.TODAY) {
            buildTrackButtons(daily(), "daily_claim", guiLeft + guiWidth / 2 + 8, guiTop + 72, 1);
        } else {
            buildSeasonButtons();
        }
        addBackButton();
    }

    private void buildTrackButtons(CompoundTag track, String action, int x, int y, int columns) {
        long progress = track.getInt("done");
        int mask = track.getInt("claimed");
        long[] thresholds = thresholds(track);
        int cardWidth = guiLeft + guiWidth - 16 - x;
        for (int tier = 0; tier < thresholds.length && tier < 6; tier++) {
            int top = y + tier * 44;
            if (DailyTrack.claimable(thresholds, tier, progress, mask)) {
                int index = tier;
                addRenderableWidget(Ui.primaryButton(L.c("rotasutils.journey.claim"), button -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putInt("tier", index);
                    send(action, payload);
                }).bounds(x + cardWidth - 76, top + 20, 70, 18).build());
            }
        }
    }

    private void buildSeasonButtons() {
        CompoundTag track = season();
        long points = track.getLong("points");
        int mask = track.getInt("claimed");
        long[] thresholds = thresholds(track);
        for (int row = 0; row < SEASON_ROWS; row++) {
            int tier = scroll + row;
            if (tier >= thresholds.length) {
                break;
            }
            if (DailyTrack.claimable(thresholds, tier, points, mask)) {
                int index = tier;
                addRenderableWidget(Ui.primaryButton(L.c("rotasutils.journey.claim"), button -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putInt("tier", index);
                    send("season_claim", payload);
                }).bounds(guiLeft + guiWidth - 100, guiTop + 98 + row * 30, 76, 20).build());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.TODAY) {
            int rowY = guiTop + 72;
            for (int index = 0; index < missions.size() && index < MISSION_ROWS; index++) {
                if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth / 2 - 28, 32)) {
                    minecraft.setScreen(new QuestDetailScreen(missions.get(index), boardId, this));
                    return true;
                }
                rowY += 36;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.SEASON) {
            int max = Math.max(0, thresholds(season()).length - SEASON_ROWS);
            scroll = (int) Math.max(0, Math.min(max, scroll - delta));
            rebuildWidgets();
        }
        return true;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 12, guiTop + 62, guiWidth - 24, guiHeight - 104, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.scaledLabel(graphics, L.t("rotasutils.journey.title"), guiLeft + 16, guiTop + 14, 1.3f, Ui.PARCHMENT_ALT);
        if (tab == Tab.TODAY) {
            renderToday(graphics, mouseX, mouseY);
        } else {
            renderSeason(graphics);
        }
    }

    private void renderToday(GuiGraphics graphics, int mouseX, int mouseY) {
        CompoundTag track = daily();
        int done = track.getInt("done");
        long[] thresholds = thresholds(track);
        long goal = thresholds.length == 0 ? 0 : thresholds[thresholds.length - 1];
        Ui.labelRight(graphics, L.t("rotasutils.journey.today_count", done, goal,
                        DailyTrack.countdown(track.getLong("reset_in"))),
                guiLeft + guiWidth - 16, guiTop + 20, Ui.PARCHMENT_ALT);

        int half = guiWidth / 2;
        PlayerProgress progress = ClientState.progress();
        if (!track.getBoolean("enabled")) {
            Ui.wrapped(graphics, L.t("rotasutils.journey.daily_off"), guiLeft + 24, guiTop + 76, half - 40, Ui.INK_FADE);
        } else if (missions.isEmpty()) {
            Ui.wrapped(graphics, L.t("rotasutils.journey.no_missions"), guiLeft + 24, guiTop + 76, half - 40, Ui.INK_FADE);
        }
        int rowY = guiTop + 72;
        for (int index = 0; index < missions.size() && index < MISSION_ROWS; index++) {
            String id = missions.get(index);
            QuestDef quest = ClientState.quest(id);
            boolean hovered = Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, half - 28, 32);
            Ui.rowCard(graphics, guiLeft + 16, rowY, half - 28, 32, hovered, false);
            Ui.label(graphics, Ui.truncate(quest == null ? id : quest.name(), half - 48), guiLeft + 24, rowY + 6, Ui.INK);
            String state;
            int colour;
            if (progress.active(id) != null) {
                state = L.t("rotasutils.journey.mission_active");
                colour = Ui.INK_WARN;
            } else if (progress.cooldownUntil(id) > System.currentTimeMillis() / 1000L) {
                state = L.t("rotasutils.journey.mission_done");
                colour = Ui.INK_GOOD;
            } else {
                state = L.t("rotasutils.journey.mission_new");
                colour = Ui.INK_SOFT;
            }
            Ui.label(graphics, state, guiLeft + 24, rowY + 19, colour);
            rowY += 36;
        }

        renderTrack(graphics, track, done, guiLeft + half + 8, guiTop + 72, "rotasutils.journey.tier_missions");
    }

    private void renderTrack(GuiGraphics graphics, CompoundTag track, long progress, int x, int y, String neededKey) {
        ListTag tiers = track.getList("tiers", Tag.TAG_COMPOUND);
        int mask = track.getInt("claimed");
        long[] thresholds = thresholds(track);
        int cardWidth = guiLeft + guiWidth - 16 - x;
        for (int tier = 0; tier < tiers.size() && tier < 6; tier++) {
            CompoundTag rung = tiers.getCompound(tier);
            int top = y + tier * 44;
            boolean reached = DailyTrack.reached(thresholds, tier, progress);
            boolean claimed = DailyTrack.claimed(mask, tier);
            Ui.rowCard(graphics, x, top, cardWidth, 40, reached && !claimed, false);
            Ui.label(graphics, L.t(neededKey, rung.getLong("needed")), x + 8, top + 6,
                    reached ? Ui.INK : Ui.INK_FADE);
            Ui.label(graphics, Ui.truncate(rewardText(rung), cardWidth - 96), x + 8, top + 22, Ui.INK_SOFT);
            if (claimed) {
                Ui.labelRight(graphics, L.t("rotasutils.journey.claimed"), x + cardWidth - 8, top + 24, Ui.INK_GOOD);
            } else if (!reached) {
                Ui.labelRight(graphics, L.t("rotasutils.journey.to_go", rung.getLong("needed") - progress),
                        x + cardWidth - 8, top + 24, Ui.INK_FADE);
            }
        }
    }

    private void renderSeason(GuiGraphics graphics) {
        CompoundTag track = season();
        long points = track.getLong("points");
        long[] thresholds = thresholds(track);
        long top = thresholds.length == 0 ? 1 : thresholds[thresholds.length - 1];
        Ui.labelRight(graphics, L.t("rotasutils.journey.season_points", points, top),
                guiLeft + guiWidth - 16, guiTop + 20, Ui.PARCHMENT_ALT);
        Ui.bar(graphics, guiLeft + 24, guiTop + 74, guiWidth - 48, 10,
                Math.min(1.0, points / (double) Math.max(1, top)), Ui.WAX, "");
        if (!track.getBoolean("enabled")) {
            Ui.wrapped(graphics, L.t("rotasutils.journey.season_off"), guiLeft + 24, guiTop + 98,
                    guiWidth - 48, Ui.INK_FADE);
            return;
        }

        ListTag tiers = track.getList("tiers", Tag.TAG_COMPOUND);
        int mask = track.getInt("claimed");
        for (int row = 0; row < SEASON_ROWS; row++) {
            int tier = scroll + row;
            if (tier >= tiers.size()) {
                break;
            }
            CompoundTag rung = tiers.getCompound(tier);
            int y = guiTop + 94 + row * 30;
            boolean reached = DailyTrack.reached(thresholds, tier, points);
            boolean claimed = DailyTrack.claimed(mask, tier);
            Ui.rowCard(graphics, guiLeft + 20, y, guiWidth - 40, 28, reached && !claimed, false);
            String name = rung.getString("name");
            Ui.label(graphics, rung.getLong("needed") + (name.isBlank() ? "" : "  " + name),
                    guiLeft + 28, y + 5, reached ? Ui.INK : Ui.INK_FADE);
            Ui.label(graphics, Ui.truncate(rewardText(rung), guiWidth - 190), guiLeft + 28, y + 16, Ui.INK_SOFT);
            if (claimed) {
                Ui.labelRight(graphics, L.t("rotasutils.journey.claimed"), guiLeft + guiWidth - 28, y + 10, Ui.INK_GOOD);
            } else if (!reached) {
                Ui.labelRight(graphics, L.t("rotasutils.journey.to_go", rung.getLong("needed") - points),
                        guiLeft + guiWidth - 28, y + 10, Ui.INK_FADE);
            }
        }
    }

    static String rewardText(CompoundTag rung) {
        List<String> parts = new ArrayList<>();
        if (rung.getLong("gold") > 0) {
            parts.add(rung.getLong("gold") + " " + L.t("rotasutils.track.gold"));
        }
        if (rung.getLong("xp") > 0) {
            parts.add(rung.getLong("xp") + " EXP");
        }
        if (rung.getLong("rank") > 0) {
            parts.add(rung.getLong("rank") + " " + L.t("rotasutils.track.rank"));
        }
        for (String line : Nbt.loadStrings(rung, "items")) {
            String id = line.trim().split("\\s+")[0];
            var location = net.minecraft.resources.ResourceLocation.tryParse(id);
            var item = location == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location);
            String name = item == null ? id : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
            parts.add(line.contains("@") ? name + " (" + L.t("rotasutils.track.chance") + ")" : name);
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }
}
