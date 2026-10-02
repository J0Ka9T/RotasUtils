package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.title.TitleDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The titles (ฉายา) page.
 *
 * <p>Left: tabs by kind of deed (combat, progress, crafting, wealth, special), a filter for earned or
 * still-locked titles, and the list - each row with its rarity stripe, its name in its own colour and,
 * for a locked title, a progress bar. Right: everything about the selected title - how it looks over
 * your head, what earns it and how far along you are, the bonus it gives while worn, and for a unique
 * title who got there first. Double-click a title you own to wear it.</p>
 *
 * <p>A hidden title is only listed once earned, so finding one stays a surprise.</p>
 */
@Environment(EnvType.CLIENT)
public class TitleScreen extends RotasScreen {
    private enum Filter { ALL, EARNED, LOCKED }

    private static final int ROW_H = 30;
    private static final long DOUBLE_CLICK_MS = 350;

    /** Null shows every category. */
    private TitleDef.Category category;
    private Filter filter = Filter.ALL;
    private String selected = "";
    private int scroll;
    private long lastClick;
    private List<TitleDef> shown = List.of();
    private ScrollPanel list;

    private int listX, listY, listW, listH;
    private int detailX, detailY, detailW, detailH;

    public TitleScreen(Screen parent) {
        super(L.t("rotasutils.title.screen"), parent);
    }

    // Data ----------------------------------------------------------------------------------------

    private static boolean visible(TitleDef title, PlayerProgress progress) {
        return title.enabled() && (!title.hidden() || progress.hasTitle(title.id()));
    }

    /** Earned first, then closest to earning, then rarest - what a player most wants to look at. */
    private List<TitleDef> filtered() {
        PlayerProgress progress = ClientState.progress();
        List<TitleDef> titles = new ArrayList<>();
        for (TitleDef title : ClientState.titles()) {
            if (!visible(title, progress)) continue;
            if (category != null && title.category() != category) continue;
            boolean owned = progress.hasTitle(title.id());
            if (filter == Filter.EARNED && !owned) continue;
            if (filter == Filter.LOCKED && owned) continue;
            titles.add(title);
        }
        titles.sort(Comparator.<TitleDef>comparingInt(t -> t.id().equals(progress.activeTitle()) ? 0 : 1)
                .thenComparingInt(t -> progress.hasTitle(t.id()) ? 0 : 1)
                .thenComparingDouble(t -> -fraction(t))
                .thenComparingInt(t -> -t.rarity().ordinal())
                .thenComparingInt(TitleDef::order));
        return titles;
    }

    private static double fraction(TitleDef title) {
        if (ClientState.progress().hasTitle(title.id())) return 1;
        if (title.condition() == TitleDef.Condition.MANUAL) return 0;
        return Math.min(1.0, ClientState.titleProgress(title.id()) / (double) Math.max(1, title.amount()));
    }

    private TitleDef selectedTitle() {
        return selected.isBlank() ? null : ClientState.title(selected);
    }

    // Layout --------------------------------------------------------------------------------------

    /** Built from server data with no draft of its own, so a push rebuilds it (scroll and typing kept). */
    @Override
    protected Refresh refreshMode() {
        return Refresh.REBUILD;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 600);
        guiHeight = Ui.fill(height, 360);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        clearPanels();

        int inner = guiLeft + 16;
        int innerW = guiWidth - 32;
        listX = inner;
        listW = innerW * 56 / 100;
        detailX = listX + listW + 10;
        detailW = inner + innerW - detailX;
        int tabsY = guiTop + 42;

        // Category tabs.
        List<TitleDef.Category> tabs = new ArrayList<>();
        tabs.add(null);
        tabs.addAll(List.of(TitleDef.Category.values()));
        int tabW = Math.max(30, (listW - (tabs.size() - 1) * 2) / tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            TitleDef.Category tab = tabs.get(i);
            String label = tab == null ? L.t("rotasutils.title.category.all") : L.t("rotasutils.title.category." + tab.key());
            addRenderableWidget(Ui.button(Ui.text(Ui.truncate(label, tabW - 6)), b -> {
                category = tab;
                scroll = 0;
                rebuildWidgets();
            }).style(tab == category ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                    .bounds(listX + i * (tabW + 2), tabsY, tabW, 16).build());
        }
        // Filter: all / earned / locked.
        int filterY = tabsY + 20;
        addRenderableWidget(Ui.button(Ui.text(L.t("rotasutils.title.filter." + filter.name().toLowerCase(Locale.ROOT)) + " ▾"), b -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            scroll = 0;
            rebuildWidgets();
        }).bounds(listX, filterY, 110, 16).build());

        listY = filterY + 22;
        int barY = guiTop + guiHeight - 34;
        listH = barY - 8 - listY;
        detailY = tabsY;
        detailH = barY - 8 - detailY;

        shown = filtered();
        if (selected.isBlank() || shown.stream().noneMatch(t -> t.id().equals(selected))) {
            selected = shown.isEmpty() ? "" : shown.get(0).id();
        }
        list = new ScrollPanel(listX, listY, listW, listH, ROW_H).withoutBackground();
        list.setRows(shown.size(), this::renderRow, (index, button) -> clickRow(index));
        list.setScroll(scroll);
        registerPanel(list);

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.refine.close"), button -> goBack())
                .bounds(inner, barY, 100, 24).build());
        PlayerProgress progress = ClientState.progress();
        TitleDef title = selectedTitle();
        boolean owned = title != null && progress.hasTitle(title.id());
        boolean worn = title != null && title.id().equals(progress.activeTitle());
        var wear = Ui.boardPrimaryButton(L.c(worn ? "rotasutils.title.take_off" : "rotasutils.title.wear"), button -> {
            CompoundTag payload = new CompoundTag();
            if (!worn) payload.putString("title", selected);
            send("title_wear", payload);
        }).bounds(inner + innerW - 150, barY, 150, 24).build();
        wear.active = owned;
        addRenderableWidget(wear);
    }

    private void clickRow(int index) {
        if (index < 0 || index >= shown.size()) return;
        String id = shown.get(index).id();
        long now = System.currentTimeMillis();
        boolean again = id.equals(selected) && now - lastClick < DOUBLE_CLICK_MS;
        lastClick = now;
        selected = id;
        scroll = list.scroll();
        PlayerProgress progress = ClientState.progress();
        if (again && progress.hasTitle(id) && !id.equals(progress.activeTitle())) {
            CompoundTag payload = new CompoundTag();
            payload.putString("title", id);
            send("title_wear", payload);
        }
        rebuildWidgets();
    }

    // Rendering -----------------------------------------------------------------------------------

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 12, guiTop + 12, guiWidth - 24, guiHeight - 54, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PlayerProgress progress = ClientState.progress();
        Ui.scaledLabel(graphics, L.t("rotasutils.title.screen"), guiLeft + 18, guiTop + 20, 1.3f, Ui.INK);
        long earned = ClientState.titles().stream().filter(t -> visible(t, progress) && progress.hasTitle(t.id())).count();
        long total = ClientState.titles().stream().filter(t -> visible(t, progress)).count();
        Ui.labelRight(graphics, L.t("rotasutils.cmd.title.summary", earned, total), guiLeft + guiWidth - 18, guiTop + 18, Ui.INK_SOFT);
        Ui.labelRight(graphics, Ui.truncate(collectionLine(progress), guiWidth - 200), guiLeft + guiWidth - 18, guiTop + 29, 0xFF8A6428);
        Ui.labelRight(graphics, L.t("rotasutils.title.double_click"), listX + listW, listY - 17, Ui.INK_FADE);
        if (shown.isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.title.none_here"), listX + listW / 2, listY + 20, Ui.INK_FADE);
        }
        renderDetail(graphics, progress);
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        if (index >= shown.size()) return;
        TitleDef title = shown.get(index);
        PlayerProgress progress = ClientState.progress();
        boolean owned = progress.hasTitle(title.id());
        boolean worn = title.id().equals(progress.activeTitle());
        Ui.rowCard(graphics, x, y + 1, w - 6, h - 3, hovered, title.id().equals(selected));
        graphics.fill(x, y + 1, x + 3, y + h - 2, title.rarity().color);
        int nameColor = owned ? title.color() : Ui.INK_FADE;
        String badge = worn ? "  ✔ " + L.t("rotasutils.title.worn_now") : title.unique() ? "  ★" : "";
        Ui.label(graphics, Ui.truncate(title.name(), w - 90) + badge, x + 9, y + 5, nameColor);
        Ui.labelRight(graphics, L.t("rotasutils.title.rarity." + title.rarity().key()), x + w - 12, y + 5, title.rarity().color);
        int lineY = y + 17;
        if (owned) {
            Ui.label(graphics, Ui.truncate(title.description(), w - 24), x + 9, lineY, Ui.INK_SOFT);
        } else if (title.condition() == TitleDef.Condition.MANUAL) {
            Ui.label(graphics, L.t("rotasutils.title.how.manual"), x + 9, lineY, Ui.INK_FADE);
        } else if (title.unique() && !ClientState.titleHolder(title.id()).isBlank()) {
            Ui.label(graphics, L.t("rotasutils.title.held_by", ClientState.titleHolder(title.id())), x + 9, lineY, Ui.INK_FADE);
        } else {
            long have = Math.min(ClientState.titleProgress(title.id()), title.amount());
            Ui.bar(graphics, x + 9, lineY, w - 30, 9, fraction(title), title.rarity().color,
                    compact(have) + " / " + compact(title.amount()));
        }
    }

    private void renderDetail(GuiGraphics graphics, PlayerProgress progress) {
        Ui.rowCard(graphics, detailX, detailY, detailW, detailH, false, false);
        TitleDef title = selectedTitle();
        int x = detailX + 10, w = detailW - 20, y = detailY + 8;
        if (title == null) {
            Ui.wrapped(graphics, L.t("rotasutils.title.pick_one"), x, y, w, Ui.INK_FADE);
            return;
        }
        boolean owned = progress.hasTitle(title.id());
        // How it reads over your head.
        String player = net.minecraft.client.Minecraft.getInstance().player == null ? ""
                : net.minecraft.client.Minecraft.getInstance().player.getGameProfile().getName();
        graphics.fill(x, y, x + w, y + 18, 0xB0101010);
        String tag = "[" + title.name() + "] ";
        int plateW = Ui.textWidth(tag + player);
        int plateX = x + Math.max(4, (w - plateW) / 2);
        Ui.label(graphics, tag, plateX, y + 5, title.color());
        Ui.label(graphics, player, plateX + Ui.textWidth(tag), y + 5, 0xFFFFFFFF);
        y += 24;
        Ui.label(graphics, L.t("rotasutils.title.rarity." + title.rarity().key()), x, y, title.rarity().color);
        Ui.labelRight(graphics, L.t("rotasutils.title.category." + title.category().key()), x + w, y, Ui.INK_SOFT);
        y += 14;
        if (!title.description().isBlank()) {
            for (String line : Ui.wrap(title.description(), w)) {
                Ui.label(graphics, line, x, y, Ui.INK);
                y += 10;
            }
            y += 4;
        }
        // What earns it, and how far along this character is.
        Ui.label(graphics, L.t("rotasutils.title.section.earn"), x, y, Ui.INK_SOFT);
        y += 11;
        for (String line : Ui.wrap(howToEarn(title), w)) {
            Ui.label(graphics, line, x, y, Ui.INK);
            y += 10;
        }
        y += 2;
        if (owned) {
            Ui.label(graphics, "✔ " + L.t("rotasutils.cmd.title.state_owned"), x, y, Ui.INK_GOOD);
            y += 14;
        } else if (title.condition() != TitleDef.Condition.MANUAL) {
            long have = Math.min(ClientState.titleProgress(title.id()), title.amount());
            Ui.bar(graphics, x, y, w, 11, fraction(title), title.rarity().color,
                    compact(have) + " / " + compact(title.amount()) + "  (" + (int) Math.floor(fraction(title) * 100) + "%)");
            y += 17;
        }
        // What earning it pays, once, and what it adds to the collection for good.
        if (!owned) {
            var rules = ClientState.levelConfig().season().titles;
            int rarity = title.rarity().ordinal();
            Ui.label(graphics, Ui.truncate("รางวัล: +" + rules.rarityGold[rarity] + " ทอง · +" + rules.rarityXp[rarity]
                    + " EXP · +" + rules.rarityPoints[rarity] + " แต้มสะสม", w), x, y, 0xFF8A6428);
            y += 13;
        }
        // The bonus while worn.
        Ui.label(graphics, L.t("rotasutils.title.section.bonus"), x, y, Ui.INK_SOFT);
        y += 11;
        if (title.effects().isEmpty()) {
            Ui.label(graphics, L.t("rotasutils.title.no_bonus"), x, y, Ui.INK_FADE);
            y += 10;
        } else {
            for (CharacterStat.Effect effect : title.effects()) {
                Ui.label(graphics, "• " + bonus(effect), x, y, 0xFF3F7A3A);
                y += 10;
            }
        }
        y += 4;
        // A unique title: who holds it, or that it is still there for the taking.
        if (title.unique()) {
            String holder = ClientState.titleHolder(title.id());
            Ui.label(graphics, L.t("rotasutils.title.section.unique"), x, y, Ui.INK_SOFT);
            y += 11;
            String text = holder.isBlank() ? L.t("rotasutils.title.unclaimed") : L.t("rotasutils.title.held_by", holder);
            for (String line : Ui.wrap(text, w)) {
                Ui.label(graphics, line, x, y, holder.isBlank() ? 0xFFB07A2A : Ui.INK);
                y += 10;
            }
        }
    }

    // Collection ----------------------------------------------------------------------------------

    private static int collectionPoints(PlayerProgress progress) {
        int[] points = ClientState.levelConfig().season().titles.rarityPoints;
        int total = 0;
        for (TitleDef title : ClientState.titles()) {
            if (title.enabled() && progress.hasTitle(title.id())) total += points[title.rarity().ordinal()];
        }
        return total;
    }

    /** "Collection 23 pts · next at 30: +3% Health" - why every title is worth earning. */
    private static String collectionLine(PlayerProgress progress) {
        int points = collectionPoints(progress);
        var rules = ClientState.levelConfig().season().titles;
        int reached = 0;
        for (var tier : rules.collection) {
            if (tier.points <= points) {
                reached++;
                continue;
            }
            return "แต้มสะสมฉายา " + points + " · โบนัสถาวร " + reached + " ขั้น · ถัดไปที่ " + tier.points + ": "
                    + bonus(new CharacterStat.Effect(tier.attribute, tier.amount, CharacterStat.Operation.ADD, tier.percent, tier.label, 0));
        }
        return "แต้มสะสมฉายา " + points + " · ปลดล็อกโบนัสถาวรครบทุกขั้นแล้ว!";
    }

    // Wording -------------------------------------------------------------------------------------

    /** The condition in a sentence: "Kill 1,000 monsters", "Finish the quest Lost Lamb". */
    private static String howToEarn(TitleDef title) {
        String amount = String.format(Locale.ROOT, "%,d", title.amount());
        String key = "rotasutils.title.how." + title.condition().key();
        return switch (title.condition()) {
            case KILL_ENTITY -> L.t(key, amount, entityName(title.target()));
            case QUEST -> {
                var quest = ClientState.quest(title.target());
                yield L.t(key, quest == null ? title.target() : quest.name());
            }
            case MANUAL -> L.t(key);
            default -> L.t(key, amount);
        };
    }

    private static String entityName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(location)) return id;
        return BuiltInRegistries.ENTITY_TYPE.get(location).getDescription().getString();
    }

    /** "+2% Attack", "+1 Defense". */
    public static String bonusText(CharacterStat.Effect effect) {
        return bonus(effect);
    }

    static String bonus(CharacterStat.Effect effect) {
        double value = effect.percent() ? effect.perPoint() * 100 : effect.perPoint();
        String number = value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
        String label = effect.label().isBlank() ? effect.attribute() : effect.label();
        return (value >= 0 ? "+" : "") + number + (effect.percent() ? "% " : " ") + label;
    }

    private static String compact(long value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0).replace(".0M", "M");
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fK", value / 1_000.0).replace(".0K", "K");
        return String.format(Locale.ROOT, "%,d", value);
    }
}
