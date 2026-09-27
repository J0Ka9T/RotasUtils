package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.StatRules;
import net.schwarz.rotasutils.stat.CoreStat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spending level-up stat points.
 *
 * <p>Four fixed stats, every point worth the same. Points are planned first with + and -, shown as
 * "now -> after", and only sent when the player presses Confirm, so a stray click never spends a point.
 * A respec gives every point back for a flat price.</p>
 */
@Environment(EnvType.CLIENT)
public class StatsScreen extends RotasScreen {
    private static final int ROW_HEIGHT = 48;
    private static final int CONTROL = 18;

    private final Map<String, Integer> pending = new LinkedHashMap<>();
    private final List<CoreStat> stats = CoreStat.ALL;
    private ScrollPanel list;
    private double lastClickX;
    private long respecConfirmUntil;

    public StatsScreen(Screen parent) {
        super(net.schwarz.rotasutils.client.screen.L.t("rotasutils.stats.screen_title"), parent);
    }

    private static SeasonRules season() {
        return ClientState.levelConfig().season();
    }

    private static StatRules rules() {
        return season().stats;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 660);
        guiHeight = Ui.fill(height, 460);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int scroll = list == null ? 0 : list.scroll();

        int listTop = guiTop + 78;
        list = new ScrollPanel(guiLeft + Ui.PAD, listTop, guiWidth - Ui.PAD * 2,
                Math.max(ROW_HEIGHT, footerY() - Ui.GAP * 2 - listTop), ROW_HEIGHT)
                .withoutBackground()
                .rowHitInsets(0, 4);
        list.setRows(stats.size(), this::renderRow, this::clickRow);
        list.setScroll(scroll);
        registerPanel(list);

        addBackButton();
        int right = guiLeft + guiWidth - Ui.PAD;
        Button undo = addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.L.c("rotasutils.stats.btn.undo"), button -> {
            pending.clear();
            rebuild();
        }).bounds(right - 196, footerY(), 88, 22).build());
        undo.active = !pending.isEmpty();
        int planned = pendingTotal();
        Button confirm = addRenderableWidget(Ui.primaryButton(
                planned == 0 ? net.schwarz.rotasutils.client.screen.L.c("rotasutils.stats.btn.confirm")
                        : net.schwarz.rotasutils.client.screen.L.c("rotasutils.stats.btn.confirm_points", planned),
                button -> confirm()).bounds(right - 104, footerY(), 104, 22).build());
        confirm.active = planned > 0;

        long cost = rules().respecCost;
        boolean confirming = Util.getMillis() < respecConfirmUntil;
        String label = confirming ? "กดอีกครั้งเพื่อรีเซ็ต"
                : "รีเซ็ตแต้ม · " + (cost <= 0 ? "ฟรี" : Currencies.amount(cost) + " " + Currencies.name(season().currency));
        Button respec = addRenderableWidget(Ui.dangerButton(Ui.text(label), button -> respec())
                .bounds(right - 204 - 150, footerY(), 150, 22).build());
        respec.active = allocatedTotal() > 0 && pending.isEmpty();
    }

    private void respec() {
        if (Util.getMillis() < respecConfirmUntil) {
            respecConfirmUntil = 0;
            send("respec_stats", new CompoundTag());
            Sfx.commit();
        } else {
            respecConfirmUntil = Util.getMillis() + 3000;
            Sfx.select();
        }
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (respecConfirmUntil != 0 && Util.getMillis() >= respecConfirmUntil) {
            respecConfirmUntil = 0;
            rebuild();
        }
    }

    private int footerY() {
        return guiTop + guiHeight - 28;
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        if (pendingTotal() > ClientState.progress().rpg().statPoints()) {
            pending.clear();
        }
        rebuild();
    }

    private int pendingTotal() {
        return pending.values().stream().mapToInt(Integer::intValue).sum();
    }

    private int allocatedTotal() {
        return stats.stream().mapToInt(StatsScreen::allocated).sum();
    }

    private int remaining() {
        return ClientState.progress().rpg().statPoints() - pendingTotal();
    }

    private static int allocated(CoreStat stat) {
        return (int) Math.round(ClientState.progress().rpg().stats().getOrDefault(stat.id(), 0.0));
    }


    private void confirm() {
        if (pending.isEmpty()) {
            return;
        }
        CompoundTag stats = new CompoundTag();
        pending.forEach(stats::putInt);
        CompoundTag payload = new CompoundTag();
        payload.put("stats", stats);
        send("allocate_stats", payload);
        pending.clear();
        Sfx.commit();
        rebuild();
    }

    // Rows -----------------------------------------------------------------

    private int plusX(int rowX, int rowWidth) {
        return rowX + rowWidth - 12 - CONTROL;
    }

    private int minusX(int rowX, int rowWidth) {
        return plusX(rowX, rowWidth) - 76;
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        CoreStat stat = stats.get(index);
        StatRules rules = rules();
        int have = allocated(stat);
        int adding = pending.getOrDefault(stat.id(), 0);
        int cardWidth = rowWidth - 6;
        Ui.rowCard(graphics, x, y, cardWidth, rowHeight - 4, hovered, adding > 0);
        graphics.fill(x + 5, y + 6, x + 7, y + rowHeight - 10, stat.color());
        graphics.renderFakeItem(stat.icon(), x + 14, y + 14);

        int textX = x + 38;
        int textWidth = minusX(x, cardWidth) - textX - 8;
        Ui.label(graphics, Ui.truncate(stat.displayName(), textWidth), textX, y + 6, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate(stat.description() + " · แต้มละ " + stat.describe(rules, 1), textWidth),
                textX, y + 17, Ui.TEXT_MUTED);
        String effect = have + adding == 0 ? "ยังไม่ได้ลงแต้ม" : stat.describe(rules, have);
        if (adding > 0) {
            effect = (have == 0 ? "" : effect + "  ->  ") + stat.describe(rules, have + adding);
        }
        Ui.label(graphics, Ui.truncate(effect, textWidth), textX, y + 29, adding > 0 ? Ui.GOOD : Ui.TEXT_DIM);

        int controlY = y + (rowHeight - 4 - CONTROL) / 2;
        int minus = minusX(x, cardWidth);
        int plus = plusX(x, cardWidth);
        control(graphics, minus, controlY, "-", adding > 0);
        control(graphics, plus, controlY, "+", canAdd(stat) > 0);
        String points = (have + adding) + " / " + rules.maxPerStat;
        Ui.labelCentered(graphics, points, (minus + CONTROL + plus) / 2, controlY + 5,
                adding > 0 ? Ui.GOOD : Ui.TEXT);
    }

    private static void control(GuiGraphics graphics, int x, int y, String text, boolean enabled) {
        PixelUi.frame(graphics, x, y, CONTROL, CONTROL, 1,
                enabled ? RotasTheme.ACCENT : RotasTheme.SEPARATOR,
                enabled ? RotasTheme.CONTROL : RotasTheme.CONTROL_DISABLED);
        Ui.labelCentered(graphics, text, x + CONTROL / 2 + 1, y + 5, enabled ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
    }

    private int canAdd(CoreStat stat) {
        int room = rules().maxPerStat - allocated(stat) - pending.getOrDefault(stat.id(), 0);
        return Math.max(0, Math.min(remaining(), room));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastClickX = mouseX;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void clickRow(int index, int button) {
        if (button != 0 || index < 0 || index >= stats.size()) {
            return;
        }
        CoreStat stat = stats.get(index);
        int rowX = list.x() + 1;
        int cardWidth = list.width() - 2 - 6;
        int step = hasShiftDown() ? 10 : 1;
        int adding = pending.getOrDefault(stat.id(), 0);
        int plus = plusX(rowX, cardWidth);
        int minus = minusX(rowX, cardWidth);
        if (lastClickX >= plus && lastClickX < plus + CONTROL) {
            int added = Math.min(step, canAdd(stat));
            if (added <= 0) {
                return;
            }
            pending.put(stat.id(), adding + added);
            Sfx.select();
        } else if (lastClickX >= minus && lastClickX < minus + CONTROL && adding > 0) {
            int left = Math.max(0, adding - step);
            if (left == 0) {
                pending.remove(stat.id());
            } else {
                pending.put(stat.id(), left);
            }
            Sfx.select();
        } else {
            return;
        }
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int y = guiTop + 34;
        int w = guiWidth - Ui.PAD * 2;
        Ui.panel(graphics, x, y, w, 38);
        String points = String.valueOf(remaining());
        Ui.scaledLabel(graphics, points, x + 12, y + 11, 2.0f, remaining() > 0 ? Ui.ACCENT : Ui.TEXT_MUTED);
        int after = x + 12 + (int) Ui.scaledWidth(points, 2.0f) + 8;
        Ui.label(graphics, L.t("rotasutils.stats.points_to_spend"), after, y + 8, Ui.TEXT_BRIGHT);
        StatRules rules = rules();
        String earn = "ได้ " + rules.pointsPerLevel + " แต้มต่อเลเวล · สเตตัสละไม่เกิน " + rules.maxPerStat
                + " แต้ม · Shift+คลิก = ทีละ 10";
        Ui.label(graphics, Ui.truncate(earn, w - (after - x) - 70), after, y + 21, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, L.t("rotasutils.stats.level", ClientState.progress().level()), x + w - 12, y + 14, Ui.TEXT);
    }
}
