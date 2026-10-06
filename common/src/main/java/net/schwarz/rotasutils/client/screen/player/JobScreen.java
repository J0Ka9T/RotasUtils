package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobMasteryCurve;
import net.schwarz.rotasutils.job.JobUnlockTable;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillConnection;
import net.schwarz.rotasutils.skill.SkillNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class JobScreen extends RotasScreen {
    private static final int LIST_W = 230;
    private static final int UNLOCK_ROW = 26;
    private static final int TREE_ROW = 104;

    private enum Tab { OVERVIEW, SKILLS, UNLOCKS }

    private record UnlockLine(int tier, int count, JobDef.ProductionEntry entry) {
    }

    private List<JobDef> jobs = List.of();
    private String selectedId;
    private ScrollPanel list;
    private ScrollPanel detailList;
    private Tab tab = Tab.OVERVIEW;
    private List<UnlockLine> unlockLines = List.of();
    private List<SkillCategory> trees = List.of();

    private final boolean required;

    public JobScreen(Screen parent) {
        this(parent, false);
    }

    public JobScreen(Screen parent, boolean required) {
        super(L.t("rotasutils.job.screen_title"), parent);
        this.required = required;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !required || !ClientState.progress().job().isEmpty();
    }

private int panelX() {
        return guiLeft + Ui.PAD + LIST_W + Ui.GAP * 2;
    }

    private int panelY() {
        return guiTop + 34;
    }

    private int panelW() {
        return guiLeft + guiWidth - Ui.PAD - panelX();
    }

    private int panelH() {
        return footerY() - Ui.GAP * 2 - panelY();
    }

    private int bodyTop() {
        return panelY() + 76;
    }

    private int footerY() {
        return guiTop + guiHeight - 28;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 720);
        guiHeight = Ui.fill(height, 460);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        jobs = ClientState.jobs().values().stream()
                .filter(JobDef::enabled)
                .sorted(Comparator.comparingInt(JobDef::order).thenComparing(JobDef::name))
                .toList();
        String current = ClientState.progress().job();
        if (selected() == null) {
            selectedId = jobs.stream().anyMatch(job -> job.id().equals(current)) ? current
                    : jobs.isEmpty() ? null : jobs.get(0).id();
        }

        int top = guiTop + 34;
        list = new ScrollPanel(guiLeft + Ui.PAD, top, LIST_W, Math.max(40, footerY() - Ui.GAP * 2 - top), 40)
                .withoutBackground()
                .rowHitInsets(0, 4);
        list.setRows(jobs.size(), this::renderRow, this::clickRow);
        registerPanel(list);

        JobDef job = selected();
        buildTabs(job);
        buildDetail(job);

        if (!required) {
            addBackButton();
        }
        boolean isCurrent = job != null && job.id().equals(current);
        String label = job == null ? L.t("rotasutils.job.btn.choose") : isCurrent ? L.t("rotasutils.job.btn.current_main")
                : L.t("rotasutils.job.btn.become", job.name());
        Button choose = addRenderableWidget(Ui.primaryButton(Component.literal(Ui.truncate(label, 130)), button -> confirmChoice(false))
                .bounds(guiLeft + guiWidth - Ui.PAD - 280, footerY(), 136, 22).build());
        choose.active = job != null && job.mainAllowed() && !isCurrent && ClientState.progress().level() >= job.minLevel() && !job.id().equals(ClientState.progress().subJob());
        Button sub = addRenderableWidget(Ui.button(L.c(job != null && job.id().equals(ClientState.progress().subJob()) ? "rotasutils.job.btn.current_sub" : "rotasutils.job.btn.set_sub"), button -> confirmChoice(true))
                .bounds(guiLeft + guiWidth - Ui.PAD - 138, footerY(), 138, 22).build());
        sub.active = job != null && job.subAllowed() && !job.id().equals(ClientState.progress().subJob()) && !job.id().equals(current) && ClientState.progress().level() >= job.minLevel();
        sub.visible = !required;
    }

    private void buildTabs(JobDef job) {
        if (job == null) {
            return;
        }
        List<Tab> tabs = new ArrayList<>(List.of(Tab.OVERVIEW, Tab.SKILLS));
        if (!job.production().isEmpty()) {
            tabs.add(Tab.UNLOCKS);
        }
        if (!tabs.contains(tab)) {
            tab = Tab.OVERVIEW;
        }
        int x = panelX() + 12;
        int y = panelY() + 54;
        int w = Math.min(104, (panelW() - 24 - (tabs.size() - 1) * Ui.GAP) / tabs.size());
        for (Tab each : tabs) {
            Button button = addRenderableWidget(Ui.button(L.c("rotasutils.job.tab." + each.name().toLowerCase(Locale.ROOT)), b -> {
                tab = each;
                Sfx.select();
                rebuild();
            }).bounds(x, y, w, 18).build());
            button.active = each != tab;
            x += w + Ui.GAP;
        }
    }

    private void buildDetail(JobDef job) {
        detailList = null;
        if (job == null) {
            return;
        }
        int x = panelX() + 10;
        int y = bodyTop();
        int w = panelW() - 20;
        int h = Math.max(UNLOCK_ROW, panelY() + panelH() - 8 - y);
        if (tab == Tab.UNLOCKS) {
            unlockLines = unlockLines(job);
            detailList = new ScrollPanel(x, y + 30, w, Math.max(UNLOCK_ROW, h - 30), UNLOCK_ROW).withoutBackground();
            detailList.setRows(unlockLines.size(), this::renderUnlockLine, (index, button) -> { });
            registerPanel(detailList);
        } else if (tab == Tab.SKILLS) {
            trees = ClientState.categories().values().stream()
                    .filter(category -> category.jobs().contains(job.id()))
                    .sorted(Comparator.comparingInt(SkillCategory::order))
                    .toList();
            if (!trees.isEmpty()) {
                detailList = new ScrollPanel(x, y + 18, w, Math.max(TREE_ROW, h - 18), TREE_ROW).withoutBackground();
                detailList.setRows(trees.size(), this::renderTreeRow, this::clickTreeRow);
                registerPanel(detailList);
            }
        }
    }

    private JobDef selected() {
        return selectedId == null ? null : ClientState.job(selectedId);
    }

    private void rebuild() {
        int scroll = list == null ? 0 : list.scroll();
        clearWidgets();
        clearPanels();
        buildContent();
        list.setScroll(scroll);
    }

    @Override
    public void onDataRefreshed() {
        if (required && !ClientState.progress().job().isEmpty()) {
            onClose();
            return;
        }
        rebuild();
    }

private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        JobDef job = jobs.get(index);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 4, hovered, job.id().equals(selectedId));
        graphics.fill(x + 5, y + 6, x + 7, y + rowHeight - 10, job.color());
        graphics.renderFakeItem(job.icon(), x + 12, y + 9);
        PlayerProgress progress = ClientState.progress();
        boolean main = job.id().equals(progress.mainJob());
        boolean sub = job.id().equals(progress.subJob());
        boolean levelOk = progress.level() >= job.minLevel();
        String slotTag = main ? L.t("rotasutils.job.tag.current") : sub ? L.t("rotasutils.job.badge.sub_equipped") : "";
        int tagWidth = slotTag.isEmpty() ? 0 : font.width(slotTag) + 14;
        Ui.label(graphics, Ui.truncate(job.name(), w - 44 - tagWidth), x + 34, y + 6, Ui.TEXT_BRIGHT);
        Ui.label(graphics, levelOk ? L.t("rotasutils.job.from_level", job.minLevel()) : L.t("rotasutils.job.needs_level", job.minLevel()),
                x + 34, y + 18, levelOk ? Ui.TEXT_MUTED : Ui.BAD);
        if (!slotTag.isEmpty()) {
            Ui.tag(graphics, x + w - tagWidth - 4, y + 9, slotTag, Ui.GOOD);
        }
        String kinds = (job.mainAllowed() ? L.t("rotasutils.job.badge.main") : "")
                + (job.mainAllowed() && job.subAllowed() ? " · " : "")
                + (job.subAllowed() ? L.t("rotasutils.job.badge.sub") : "");
        Ui.labelRight(graphics, Ui.truncate(kinds, 90), x + w - 6, y + 18, Ui.TEXT_FAINT);
    }

    private void clickRow(int index, int button) {
        if (index < 0 || index >= jobs.size()) {
            return;
        }
        selectedId = jobs.get(index).id();
        Sfx.select();
        rebuild();
    }

    private void confirmChoice(boolean subJob) {
        JobDef job = selected();
        if (job == null) {
            return;
        }
        boolean hasJob = !ClientState.progress().job().isEmpty();
        String detail = hasJob
                ? L.t("rotasutils.job.confirm.keep") + " "
                + (ClientState.jobCooldownSeconds() > 0
                ? L.t("rotasutils.job.confirm.change_after", QuestService.formatDuration(ClientState.jobCooldownSeconds()))
                : L.t("rotasutils.job.confirm.change_any"))
                : L.t("rotasutils.job.confirm.opens", job.name());
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                CompoundTag payload = new CompoundTag();
                payload.putString("job", job.id());
                payload.putString("slot", subJob ? "sub" : "main");
                send("choose_job", payload);
                Sfx.commit();
            }
            minecraft.setScreen(this);
        }, L.c(subJob ? "rotasutils.job.confirm.title_sub" : "rotasutils.job.confirm.title_main", job.name()),
                Component.literal(detail)));
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        Ui.panel(graphics, x, y, w, h);

        JobDef job = selected();
        if (job == null) {
            Ui.labelCentered(graphics, L.t("rotasutils.job.none_title"), x + w / 2, y + h / 2 - 6, Ui.TEXT_DIM);
            Ui.labelCentered(graphics, L.t("rotasutils.job.none_hint"), x + w / 2, y + h / 2 + 6, Ui.TEXT_MUTED);
            return;
        }
        drawHeader(graphics, job, x, y, w);
        switch (tab) {
            case OVERVIEW -> drawOverview(graphics, job, x + 12, bodyTop() + 4, w - 24);
            case SKILLS -> drawSkillsHeader(graphics, job, x + 12, bodyTop() + 2, w - 24);
            case UNLOCKS -> drawUnlockSummary(graphics, job, x + 12, bodyTop() + 2, w - 24);
        }
    }

    private void drawHeader(GuiGraphics graphics, JobDef job, int x, int y, int w) {
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, job.color());
        graphics.renderFakeItem(job.icon(), x + 12, y + 14);
        Ui.scaledLabel(graphics, Ui.truncate(job.name(), w - 150), x + 36, y + 14, 1.0f, job.color());
        int tx = x + 36;
        PlayerProgress progress = ClientState.progress();
        if (job.mainAllowed()) {
            tx += Ui.chip(graphics, tx, y + 28, L.t("rotasutils.job.badge.main"), Ui.TEXT);
        }
        if (job.subAllowed()) {
            tx += Ui.chip(graphics, tx, y + 28, L.t("rotasutils.job.badge.sub"), Ui.TEXT);
        }
        boolean levelOk = progress.level() >= job.minLevel();
        Ui.chip(graphics, tx, y + 28, levelOk ? L.t("rotasutils.job.from_level", job.minLevel())
                : L.t("rotasutils.job.needs_level", job.minLevel()), levelOk ? Ui.GOOD : Ui.BAD);
        if (job.id().equals(progress.mainJob())) {
            Ui.statusTag(graphics, x + w - 10, y + 12, L.t("rotasutils.job.tag.current"), Ui.GOOD);
        } else if (job.id().equals(progress.subJob())) {
            Ui.statusTag(graphics, x + w - 10, y + 12, L.t("rotasutils.job.badge.sub_equipped"), Ui.GOOD);
        }
    }

private void drawOverview(GuiGraphics graphics, JobDef job, int x, int y, int w) {
        PlayerProgress progress = ClientState.progress();
        for (String line : Ui.wrap(job.description().isBlank() ? L.t("rotasutils.job.no_description") : job.description(), w)) {
            Ui.label(graphics, line, x, y, Ui.TEXT);
            y += 10;
        }
        y += 6;
        y = drawMastery(graphics, job, x, y, w);
        y += 8;

        List<String> good = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        List<Boolean> goodAvailable = new ArrayList<>();
        List<Boolean> badAvailable = new ArrayList<>();
        for (var modifier : job.attributeModifiers()) {
            boolean positive = modifier.amount() > 0;
            boolean fraction = modifier.attribute().equals(net.schwarz.rotasutils.server.CombatStats.EVASION)
                    || modifier.attribute().equals(net.schwarz.rotasutils.server.CombatStats.MAGIC_POWER);
            String amount = modifier.operation() == net.schwarz.rotasutils.stat.CharacterStat.Operation.ADD && !fraction
                    ? formatAmount(modifier.amount()) : formatAmount(modifier.amount() * 100) + "%";
            String line = modifier.label() + "  " + (positive ? "+" : "-") + amount;
            var attribute = ResourceLocation.tryParse(modifier.attribute());
            boolean available = net.schwarz.rotasutils.server.CombatStats.logical(modifier.attribute())
                    || attribute != null && BuiltInRegistries.ATTRIBUTE.containsKey(attribute);
            if (!available) {
                line = L.t("rotasutils.job.modifier_unavailable", line);
            }
            (positive ? good : bad).add(line);
            (positive ? goodAvailable : badAvailable).add(available);
        }
        int colW = (w - Ui.GAP * 2) / 2;
        if (!good.isEmpty() || !bad.isEmpty()) {
            int cardH = 22 + Math.max(good.size(), bad.size()) * 11 + 6;
            drawModifierCard(graphics, x, y, colW, cardH, L.t("rotasutils.job.strengths"), good, goodAvailable, Ui.GOOD, "+");
            drawModifierCard(graphics, x + colW + Ui.GAP * 2, y, colW, cardH, L.t("rotasutils.job.weaknesses"), bad, badAvailable, Ui.BAD, "-");
            y += cardH + 8;
        }

        if (job.subAllowed()) {
            int cx = x;
            cx += Ui.chip(graphics, cx, y, L.t("rotasutils.job.sub_rate", percent(job.subJobXpRate())), Ui.TEXT_MUTED);
            Ui.chip(graphics, cx, y, L.t("rotasutils.job.sub_cap", percent(job.subPassiveCap())), Ui.TEXT_MUTED);
            y += 20;
        }

        long cooldown = ClientState.jobCooldownSeconds();
        long wait = cooldown - (QuestService.nowSeconds() - progress.jobChangedAt());
        boolean hasJob = !progress.mainJob().isEmpty() || !progress.subJob().isEmpty();
        if (hasJob && cooldown > 0 && wait > 0) {
            Ui.label(graphics, L.t("rotasutils.job.cooldown", QuestService.formatDuration(wait)), x, y, Ui.WARN);
        } else if (hasJob) {
            Ui.label(graphics, Ui.truncate(L.t("rotasutils.job.refund_hint"), w), x, y, Ui.TEXT_MUTED);
        }
    }

    private void drawModifierCard(GuiGraphics graphics, int x, int y, int w, int h, String title,
                                  List<String> lines, List<Boolean> available, int color, String mark) {
        Ui.rowCard(graphics, x, y, w, h, false, false);
        graphics.fill(x + 5, y + 6, x + 7, y + 17, color);
        Ui.label(graphics, title, x + 12, y + 7, color);
        int ly = y + 22;
        if (lines.isEmpty()) {
            Ui.label(graphics, "-", x + 12, ly, Ui.TEXT_FAINT);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            Ui.label(graphics, Ui.truncate(mark + " " + lines.get(i), w - 18), x + 12, ly,
                    available.get(i) ? Ui.TEXT : Ui.TEXT_MUTED);
            ly += 11;
        }
    }

    private int drawMastery(GuiGraphics graphics, JobDef job, int x, int y, int w) {
        PlayerProgress progress = ClientState.progress();
        long xp = progress.rpg().masteryXp(job.id());
        long points = progress.rpg().jobSkillPoints(job.id());
        boolean equipped = job.id().equals(progress.mainJob()) || job.id().equals(progress.subJob());
        JobMasteryCurve curve = job.masteryCurve();
        int level = curve.levelAt(xp);
        int cardH = 44;
        Ui.rowCard(graphics, x, y, w, cardH, false, false);
        Ui.label(graphics, L.t("rotasutils.job.mastery_title"), x + 10, y + 7, Ui.TEXT_MUTED);
        if (!equipped && xp <= 0) {
            Ui.label(graphics, L.t("rotasutils.job.mastery_idle"), x + 10, y + 22, Ui.TEXT_FAINT);
            return y + cardH;
        }
        Ui.label(graphics, L.t("rotasutils.job.mastery_level", level, curve.maxLevel()), x + 10, y + 20, Ui.TEXT_BRIGHT);
        long into = curve.xpIntoLevel(xp);
        long next = curve.xpForNextLevel(level);
        boolean maxed = next <= 0;
        int barX = x + 110;
        int barW = Math.max(40, w - 110 - 120);
        Ui.bar(graphics, barX, y + 20, barW, 11, maxed ? 1.0 : (double) into / Math.max(1, next), job.color(),
                maxed ? L.t("rotasutils.job.mastery_max") : into + " / " + next);
        Ui.labelRight(graphics, L.t("rotasutils.job.mastery_points", points), x + w - 10, y + 7,
                points > 0 ? Ui.GOOD : Ui.TEXT_MUTED);
        if (!equipped) {
            Ui.labelRight(graphics, L.t("rotasutils.job.mastery_unequipped"), x + w - 10, y + 22, Ui.TEXT_FAINT);
        }
        return y + cardH;
    }

    private static String percent(double fraction) {
        return formatAmount(fraction * 100) + "%";
    }

private void drawSkillsHeader(GuiGraphics graphics, JobDef job, int x, int y, int w) {
        Ui.sectionHeading(graphics, L.t("rotasutils.job.trees_title"), x, y, w);
        if (trees.isEmpty()) {
            Ui.wrapped(graphics, L.t("rotasutils.job.no_trees"), x, y + 22, w, Ui.TEXT_MUTED);
        }
    }

    private void renderTreeRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        SkillCategory tree = trees.get(index);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 6, hovered, false);
        graphics.fill(x + 5, y + 6, x + 7, y + rowHeight - 12, tree.accentColor() | 0xFF000000);
        graphics.renderFakeItem(tree.icon(), x + 12, y + 8);
        PlayerProgress progress = ClientState.progress();
        int unlocked = 0;
        int ranks = 0;
        int maxRanks = 0;
        for (SkillNode node : tree.nodes().values()) {
            int rank = progress.skillRank(node.id());
            unlocked += rank > 0 ? 1 : 0;
            ranks += rank;
            maxRanks += node.maxRank();
        }
        long points = progress.rpg().jobSkillPoints(selectedId);
        Ui.label(graphics, Ui.truncate(tree.name(), 150), x + 34, y + 8, Ui.TEXT_BRIGHT);
        Ui.label(graphics, L.t("rotasutils.job.tree_progress", unlocked, tree.nodes().size()), x + 34, y + 21, Ui.TEXT_MUTED);
        Ui.bar(graphics, x + 12, y + 38, 150, 9, maxRanks == 0 ? 0 : (double) ranks / maxRanks,
                tree.accentColor() | 0xFF000000, ranks + " / " + maxRanks);
        Ui.label(graphics, L.t("rotasutils.job.tree_points", points), x + 12, y + 54, points > 0 ? Ui.GOOD : Ui.TEXT_MUTED);
        Ui.label(graphics, L.t("rotasutils.job.tree_open"), x + 12, y + 68, hovered ? Ui.ACCENT_HOVER : Ui.ACCENT);
        int mapX = x + 176;
        int mapY = y + 6;
        int mapW = w - 176 - 10;
        int mapH = rowHeight - 6 - 12;
        drawTreeMap(graphics, tree, progress, mapX, mapY, mapW, mapH);
    }

    private void clickTreeRow(int index, int button) {
        if (index >= 0 && index < trees.size()) {
            Sfx.select();
            minecraft.setScreen(new SkillTreeScreen(this, trees.get(index).id()));
        }
    }

    private static void drawTreeMap(GuiGraphics graphics, SkillCategory tree, PlayerProgress progress,
                                    int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, Ui.PANEL_INSET);
        if (tree.nodes().isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SkillNode node : tree.nodes().values()) {
            minX = Math.min(minX, node.x());
            maxX = Math.max(maxX, node.x());
            minY = Math.min(minY, node.y());
            maxY = Math.max(maxY, node.y());
        }
        int pad = 8;
        double scale = Math.min((w - pad * 2) / (double) Math.max(1, maxX - minX), (h - pad * 2) / (double) Math.max(1, maxY - minY));
        scale = Math.min(scale, 1.0);
        double offsetX = x + w / 2.0 - (minX + maxX) / 2.0 * scale;
        double offsetY = y + h / 2.0 - (minY + maxY) / 2.0 * scale;
        int accent = tree.accentColor() | 0xFF000000;
        for (SkillNode node : tree.nodes().values()) {
            int tx = (int) Math.round(offsetX + node.x() * scale);
            int ty = (int) Math.round(offsetY + node.y() * scale);
            for (SkillConnection connection : node.connections()) {
                SkillNode from = tree.node(connection.fromId());
                if (from == null) {
                    continue;
                }
                int fx = (int) Math.round(offsetX + from.x() * scale);
                int fy = (int) Math.round(offsetY + from.y() * scale);
                boolean lit = progress.skillRank(from.id()) > 0 && progress.skillRank(node.id()) > 0;
                line(graphics, fx, fy, tx, ty, lit ? accent : Ui.BORDER_SUBTLE);
            }
        }
        for (SkillNode node : tree.nodes().values()) {
            int tx = (int) Math.round(offsetX + node.x() * scale);
            int ty = (int) Math.round(offsetY + node.y() * scale);
            int rank = progress.skillRank(node.id());
            int size = node.type() == SkillNode.NodeType.KEYSTONE ? 6 : 4;
            int color = rank >= node.maxRank() ? Ui.GOOD : rank > 0 ? accent : Ui.TEXT_FAINT;
            graphics.fill(tx - size / 2, ty - size / 2, tx - size / 2 + size, ty - size / 2 + size, color);
        }
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / steps;
            int py = y1 + (y2 - y1) * i / steps;
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

private List<UnlockLine> unlockLines(JobDef job) {
        List<UnlockLine> lines = new ArrayList<>();
        for (JobUnlockTable.Group group : JobUnlockTable.groups(job)) {
            lines.add(new UnlockLine(group.tier(), group.entries().size(), null));
            for (JobDef.ProductionEntry entry : group.entries()) {
                lines.add(new UnlockLine(group.tier(), 0, entry));
            }
        }
        return lines;
    }

    private int knownLevel(JobDef job) {
        PlayerProgress progress = ClientState.progress();
        if (!job.id().equals(progress.subJob())) {
            return -1;
        }
        return job.masteryCurve().levelAt(progress.rpg().masteryXp(job.id()));
    }

    private void drawUnlockSummary(GuiGraphics graphics, JobDef job, int x, int y, int w) {
        int level = knownLevel(job);
        int total = job.production().size();
        String summary = level < 0 ? L.t("rotasutils.job.unlock_preview", total)
                : L.t("rotasutils.job.unlock_summary", JobUnlockTable.unlocked(job, level), total);
        Ui.label(graphics, summary, x, y + 4, level < 0 ? Ui.TEXT_MUTED : Ui.TEXT_BRIGHT);
        int cx = x + w;
        var counts = JobUnlockTable.counts(job);
        var activities = JobDef.ProductionEntry.Activity.values();
        for (int i = activities.length - 1; i >= 0; i--) {
            Integer count = counts.get(activities[i]);
            if (count == null) {
                continue;
            }
            String text = activityName(activities[i]) + " " + count;
            int width = font.width(text) + 14;
            cx -= width;
            Ui.chip(graphics, cx, y, text, UnlockView.activityColor(activities[i]));
        }
    }

    private void renderUnlockLine(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        UnlockLine line = unlockLines.get(index);
        JobDef job = selected();
        int w = rowWidth - 6;
        if (line.entry() == null) {
            int color = UnlockView.tierColor(line.tier());
            graphics.fill(x, y + rowHeight - 6, x + w, y + rowHeight - 5, color);
            Ui.label(graphics, L.t("rotasutils.job.unlock_tier." + line.tier()), x + 4, y + 6, color);
            Ui.labelRight(graphics, L.t("rotasutils.job.unlock_count", line.count()), x + w - 4, y + 6, Ui.TEXT_MUTED);
            return;
        }
        JobDef.ProductionEntry entry = line.entry();
        int level = job == null ? -1 : knownLevel(job);
        boolean locked = level >= 0 && entry.unlockLevel() > level;
        int accent = UnlockView.activityColor(entry.activity());
        Ui.rowCard(graphics, x + 6, y, w - 6, rowHeight - 3, hovered, false);
        graphics.fill(x + 9, y + 4, x + 11, y + rowHeight - 7, locked ? Ui.TEXT_FAINT : accent);
        graphics.renderFakeItem(UnlockView.icon(entry.selector()), x + 16, y + 3);
        int nameX = x + 38;
        int rightEdge = x + w - 8;
        String levelText = L.t("rotasutils.job.unlock_level", entry.unlockLevel());
        Ui.labelRight(graphics, levelText, rightEdge, y + 7, locked ? Ui.BAD : level >= 0 ? Ui.GOOD : Ui.TEXT_MUTED);
        int chipW = font.width(activityName(entry.activity())) + 14;
        Ui.chip(graphics, rightEdge - font.width(levelText) - chipW - 8, y + 4, activityName(entry.activity()),
                locked ? Ui.TEXT_FAINT : accent);
        int nameW = rightEdge - font.width(levelText) - chipW - 16 - nameX;
        Ui.label(graphics, Ui.truncate(UnlockView.name(entry.selector()), Math.max(30, nameW)), nameX, y + 7,
                locked ? Ui.TEXT_MUTED : Ui.TEXT_BRIGHT);
    }

    private String activityName(JobDef.ProductionEntry.Activity activity) {
        return L.t("rotasutils.settings.activity." + activity.name().toLowerCase(Locale.ROOT));
    }

    private static String formatAmount(double value) {
        double magnitude = Math.abs(value);
        return magnitude == Math.rint(magnitude) ? Long.toString((long) magnitude)
                : String.format(java.util.Locale.ROOT, "%.1f", magnitude);
    }
}
