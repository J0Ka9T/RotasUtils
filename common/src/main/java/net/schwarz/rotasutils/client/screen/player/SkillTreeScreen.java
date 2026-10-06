package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.skill.EffectType;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillConnection;
import net.schwarz.rotasutils.skill.SkillEffect;
import net.schwarz.rotasutils.skill.SkillNode;
import net.schwarz.rotasutils.skill.SkillRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class SkillTreeScreen extends RotasScreen {
    private static final int RAIL_W = 156;
    private static final int DETAIL_W = 200;
    private static final int CELL = 26;
    private static final long DOUBLE_CLICK_MS = 300;

    private enum State { LOCKED, AVAILABLE, AFFORDABLE, UNLOCKED, MAX, EXCLUDED, DISABLED }

    private record RailRow(String text, SkillCategory category, String lock, boolean jobHint) {
    }

    private final List<RailRow> rail = new ArrayList<>();
    private String categoryId;
    private String selectedNodeId = "";
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean fitted;
    private boolean dragging;
    private boolean syncRequested;
    private boolean awaiting;
    private String lastClickedNode = "";
    private long lastClickAt;
    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;
    private ScrollPanel railPanel;

    public SkillTreeScreen(Screen parent) {
        super(L.t("rotasutils.skills.screen_title"), parent);
    }

    public SkillTreeScreen(Screen parent, String categoryId) {
        this(parent);
        this.categoryId = categoryId;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 960);
        guiHeight = Ui.fill(height, 540);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        if (!syncRequested) {
            syncRequested = true;
            send("request_sync");
        }
        buildRail();
        SkillCategory category = category();

        int footerY = guiTop + guiHeight - 28;
        int bodyTop = guiTop + 34;
        int bodyBottom = footerY - Ui.GAP * 2;
        canvasX = guiLeft + Ui.PAD + RAIL_W + Ui.GAP * 2;
        canvasY = bodyTop;
        canvasW = Math.max(60, guiLeft + guiWidth - Ui.PAD - DETAIL_W - Ui.GAP * 2 - canvasX);
        canvasH = Math.max(60, bodyBottom - bodyTop);

        railPanel = new ScrollPanel(guiLeft + Ui.PAD, bodyTop + 16, RAIL_W, Math.max(22, bodyBottom - bodyTop - 16), 22)
                .withoutBackground();
        railPanel.setRows(rail.size(), this::renderRailRow, this::clickRailRow);
        registerPanel(railPanel);

        if (!fitted && category != null) {
            fit(category);
            fitted = true;
        }

        addBackButton();
        int x = guiLeft + Ui.PAD + 58;
        addRenderableWidget(Ui.button(L.c("rotasutils.skills.fit_view"), button -> {
            if (category() != null) {
                fit(category());
            }
        }).bounds(x, footerY, 66, 22).build());
        x += 70;
        Button reset = addRenderableWidget(Ui.dangerButton(L.c("rotasutils.skills.reset_tree"), button -> confirmReset())
                .bounds(x, footerY, 78, 22).build());
        reset.active = category != null && ownsAny(category);

        SkillNode node = selected();
        State state = node == null || category == null ? null : state(category, node);
        String label = node == null ? L.t("rotasutils.skills.select_skill")
                : state == State.MAX ? L.t("rotasutils.skills.state.max")
                : L.t("rotasutils.skills.learn_cost", node.costForRank(ClientState.progress().skillRank(node.id())));
        Button learn = addRenderableWidget(Ui.primaryButton(Component.literal(label), button -> unlockSelected())
                .bounds(guiLeft + guiWidth - Ui.PAD - DETAIL_W, footerY, DETAIL_W, 22).build());
        learn.active = state == State.AFFORDABLE && !awaiting;
    }

    private void rebuild() {
        int scroll = railPanel == null ? 0 : railPanel.scroll();
        clearWidgets();
        clearPanels();
        buildContent();
        railPanel.setScroll(scroll);
    }

    @Override
    public void onDataRefreshed() {
        awaiting = false;
        rebuild();
    }

private void buildRail() {
        rail.clear();
        List<SkillCategory> sorted = new ArrayList<>(ClientState.categories().values());
        sorted.sort(Comparator.comparingInt(SkillCategory::order).thenComparing(SkillCategory::name));
        List<RailRow> general = new ArrayList<>();
        List<RailRow> job = new ArrayList<>();
        List<RailRow> race = new ArrayList<>();
        for (SkillCategory category : sorted) {
            String audience = ClientState.audienceBlock(category);
            if (audience != null && !ClientState.admin()) {
                continue;
            }
            RailRow row = new RailRow(category.name(), category, audience != null ? "preview" : lockReason(category), false);
            if (!category.jobs().isEmpty()) {
                job.add(row);
            } else if (!category.races().isEmpty()) {
                race.add(row);
            } else {
                general.add(row);
            }
        }
        PlayerProgress progress = ClientState.progress();
        if (!general.isEmpty()) {
            rail.add(new RailRow("GENERAL", null, null, false));
            rail.addAll(general);
        }
        rail.add(new RailRow(progress.job().isEmpty() ? L.t("rotasutils.skills.rail.job")
                : L.t("rotasutils.skills.rail.job_named", ClientState.jobName(progress.job()).toUpperCase(Locale.ROOT)), null, null, false));
        if (job.isEmpty()) {
            rail.add(new RailRow(progress.job().isEmpty() ? L.t("rotasutils.skills.choose_job")
                            : L.t("rotasutils.skills.no_job_trees"),
                    null, null, progress.job().isEmpty()));
        }
        rail.addAll(job);
        if (!race.isEmpty() || !ClientState.races().isEmpty()) {
            List<String> names = ClientState.races().stream().map(ClientState::raceName).toList();
            rail.add(new RailRow(names.isEmpty() ? L.t("rotasutils.skills.rail.race")
                    : L.t("rotasutils.skills.rail.race_named", String.join(", ", names).toUpperCase(Locale.ROOT)),
                    null, null, false));
            if (race.isEmpty()) {
                rail.add(new RailRow(L.t("rotasutils.skills.no_race_trees"), null, null, false));
            }
            rail.addAll(race);
        }
    }

    private SkillCategory category() {
        if (categoryId != null) {
            for (RailRow row : rail) {
                if (row.category() != null && row.category().id().equals(categoryId)) {
                    return row.category();
                }
            }
        }
        for (RailRow row : rail) {
            if (row.category() != null) {
                categoryId = row.category().id();
                return row.category();
            }
        }
        categoryId = null;
        return null;
    }

    private SkillNode selected() {
        SkillCategory category = category();
        return category == null || selectedNodeId.isEmpty() ? null : category.node(selectedNodeId);
    }

    private static String lockReason(SkillCategory category) {
        PlayerProgress progress = ClientState.progress();
        if (progress.unlockedCategories().contains(category.id())) {
            return null;
        }
        if (category.lockedByDefault()) {
            return "locked";
        }
        if (progress.level() < category.minLevel()) {
            return "Lv " + category.minLevel();
        }
        return null;
    }

    private static boolean usesCategoryPool(SkillCategory category) {
        return switch (ClientState.levelConfig().pointMode()) {
            case GLOBAL -> false;
            case CATEGORY -> true;
            case BOTH -> category.usesCategoryPoints();
        };
    }

    private static int pointsFor(SkillCategory category) {
        PlayerProgress progress = ClientState.progress();
        String job = category.jobs().contains(progress.mainJob()) ? progress.mainJob()
                : category.jobs().contains(progress.subJob()) ? progress.subJob() : "";
        if (!job.isEmpty()) return (int)Math.min(Integer.MAX_VALUE,progress.rpg().jobSkillPoints(job));
        return usesCategoryPool(category) ? progress.categoryPoints(category.id()) : progress.skillPoints();
    }

    private static boolean ownsAny(SkillCategory category) {
        return category.nodes().keySet().stream().anyMatch(id -> ClientState.progress().skillRank(id) > 0);
    }

    private State state(SkillCategory category, SkillNode node) {
        PlayerProgress progress = ClientState.progress();
        int rank = progress.skillRank(node.id());
        if (node.disabled()) {
            return State.DISABLED;
        }
        if (rank >= node.maxRank()) {
            return State.MAX;
        }
        for (String exclusive : node.exclusiveWith()) {
            if (progress.skillRank(exclusive) > 0) {
                return State.EXCLUDED;
            }
        }
        for (SkillConnection connection : node.connections()) {
            if (connection.type() == SkillConnection.Type.EXCLUSIVE && progress.skillRank(connection.fromId()) > 0) {
                return State.EXCLUDED;
            }
        }
        boolean gated = progress.level() < node.requiredLevel(rank)
                || SkillRules.connectionsSatisfied(ClientState::node, progress, node) != null
                || ClientState.audienceBlock(category) != null
                || lockReason(category) != null;
        if (gated) {
            return rank > 0 ? State.UNLOCKED : State.LOCKED;
        }
        if (pointsFor(category) >= node.costForRank(rank)) {
            return State.AFFORDABLE;
        }
        return rank > 0 ? State.UNLOCKED : State.AVAILABLE;
    }

    private static String stateLabel(State state) {
        return switch (state) {
            case MAX -> L.t("rotasutils.skills.state.max");
            case UNLOCKED -> L.t("rotasutils.skills.state.learned");
            case AFFORDABLE -> L.t("rotasutils.skills.state.ready");
            case AVAILABLE -> L.t("rotasutils.skills.state.needs_points");
            case LOCKED -> L.t("rotasutils.skills.state.locked");
            case EXCLUDED -> L.t("rotasutils.skills.state.blocked");
            case DISABLED -> L.t("rotasutils.skills.state.disabled");
        };
    }

    private static int stateColor(State state) {
        return switch (state) {
            case MAX, UNLOCKED -> Ui.GOOD;
            case AFFORDABLE -> RotasTheme.ACCENT_STRONG;
            case AVAILABLE -> Ui.TEXT;
            case EXCLUDED -> Ui.BAD;
            default -> Ui.TEXT_MUTED;
        };
    }

    private static boolean revealsHidden(PlayerProgress progress) {
        for (String nodeId : progress.skillRanks().keySet()) {
            SkillNode node = ClientState.node(nodeId);
            if (node != null && node.effects().stream().anyMatch(effect -> effect.type() == EffectType.REVEAL_HIDDEN_QUESTS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visible(SkillNode node, boolean revealHidden) {
        return !node.hidden() || revealHidden || ClientState.progress().skillRank(node.id()) > 0;
    }

    private int nodeSize(SkillNode node) {
        int base = switch (node.type()) {
            case KEYSTONE -> 34;
            case CHOICE -> 30;
            default -> CELL;
        };
        return (int) Math.round(base * nodeScale());
    }

    private float nodeScale() {
        return (float) Math.max(0.6, Math.min(1.4, zoom));
    }

private int centerX(SkillNode node) {
        return canvasX + canvasW / 2 + (int) Math.round((node.x() + CELL / 2.0 + panX) * zoom);
    }

    private int centerY(SkillNode node) {
        return canvasY + canvasH / 2 + (int) Math.round((node.y() + CELL / 2.0 + panY) * zoom);
    }

    private void fit(SkillCategory category) {
        boolean reveal = revealsHidden(ClientState.progress());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SkillNode node : category.nodes().values()) {
            if (!visible(node, reveal)) {
                continue;
            }
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + CELL);
            maxY = Math.max(maxY, node.y() + CELL);
        }
        if (minX == Integer.MAX_VALUE) {
            panX = -CELL / 2.0;
            panY = -CELL / 2.0;
            zoom = 1.0;
            return;
        }
        double width = Math.max(1, maxX - minX);
        double height = Math.max(1, maxY - minY);
        zoom = Math.max(0.4, Math.min(1.6, Math.min((canvasW - 90) / width, (canvasH - 110) / height)));
        panX = -(minX + maxX) / 2.0;
        panY = -(minY + maxY) / 2.0 + 12 / zoom;
    }

private void renderRailRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        RailRow row = rail.get(index);
        if (row.category() == null) {
            if (row.text().startsWith("GENERAL") || row.text().startsWith("JOB") || row.text().startsWith("RACE")) {
                Ui.label(graphics, Ui.truncate(row.text(), w - 6), x + 2, y + 9, Ui.TEXT_MUTED);
                graphics.fill(x + 2, y + h - 3, x + w - 4, y + h - 2, Ui.BORDER_SUBTLE);
            } else {
                Ui.label(graphics, Ui.truncate(row.text(), w - 10), x + 8, y + 7,
                        row.jobHint() && hovered ? Ui.ACCENT : Ui.TEXT_DIM);
            }
            return;
        }
        SkillCategory category = row.category();
        boolean selected = category.id().equals(categoryId);
        if (selected || hovered) {
            Ui.rowCard(graphics, x + 1, y + 1, w - 4, h - 2, hovered, selected);
        }
        Ui.icon(graphics, category.icon(), x + 5, y + 3);
        int ready = row.lock() == null ? readyCount(category) : 0;
        String right = row.lock() != null ? row.lock()
                : (ready > 0 ? "+" + ready + "  " : "") + ownedCount(category) + "/" + category.nodes().size();
        int rightWidth = font.width(right);
        Ui.label(graphics, Ui.truncate(category.name(), w - 34 - rightWidth), x + 25, y + 7,
                selected ? Ui.ACCENT : row.lock() != null ? Ui.TEXT_DIM : Ui.TEXT);
        Ui.labelRight(graphics, right, x + w - 8, y + 7, row.lock() != null ? Ui.WARN : ready > 0 ? Ui.ACCENT : Ui.TEXT_MUTED);
    }

    private int readyCount(SkillCategory category) {
        boolean reveal = revealsHidden(ClientState.progress());
        return (int) category.nodes().values().stream()
                .filter(node -> visible(node, reveal) && state(category, node) == State.AFFORDABLE).count();
    }

    private static int ownedCount(SkillCategory category) {
        return (int) category.nodes().keySet().stream().filter(id -> ClientState.progress().skillRank(id) > 0).count();
    }

    private void clickRailRow(int index, int button) {
        RailRow row = rail.get(index);
        if (row.category() == null) {
            if (row.jobHint()) {
                minecraft.setScreen(new JobScreen(this));
            }
            return;
        }
        if (!row.category().id().equals(categoryId)) {
            categoryId = row.category().id();
            selectedNodeId = "";
            fitted = false;
            Sfx.page();
            rebuild();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.sectionHeading(graphics, L.t("rotasutils.skills.heading"), guiLeft + Ui.PAD, guiTop + 34, RAIL_W);
        Ui.inset(graphics, canvasX, canvasY, canvasW, canvasH);
        SkillCategory category = category();
        if (category == null) {
            boolean noJob = ClientState.progress().job().isEmpty();
            Ui.labelCentered(graphics, L.t("rotasutils.skills.none"),
                    canvasX + canvasW / 2, canvasY + canvasH / 2 - 8, Ui.TEXT_DIM);
            if (noJob) {
                Ui.labelCentered(graphics, L.t("rotasutils.skills.choose_job_hint"),
                        canvasX + canvasW / 2, canvasY + canvasH / 2 + 6, Ui.TEXT_MUTED);
            }
            renderDetail(graphics, null, null);
            return;
        }

        boolean reveal = revealsHidden(ClientState.progress());
        graphics.enableScissor(canvasX + 1, canvasY + 1, canvasX + canvasW - 1, canvasY + canvasH - 1);
        drawGrid(graphics);
        drawLinks(graphics, category, reveal);
        SkillNode hovered = drawNodes(graphics, category, reveal, mouseX, mouseY);
        drawCanvasOverlay(graphics, category);
        graphics.disableScissor();

        renderDetail(graphics, category, selected() != null ? selected() : hovered);
        if (hovered != null) {
            State state = state(category, hovered);
            int rank = ClientState.progress().skillRank(hovered.id());
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.name()));
            lines.add(Component.literal(hovered.maxRank() > 1
                            ? L.t("rotasutils.skills.rank_tooltip", rank, hovered.maxRank(), stateLabel(state))
                            : stateLabel(state))
                    .withStyle(style -> style.withColor(stateColor(state) & 0xFFFFFF)));
            if (state == State.AFFORDABLE) {
                lines.add(Component.literal(L.t("rotasutils.skills.double_click")).withStyle(style -> style.withColor(Ui.TEXT_MUTED & 0xFFFFFF)));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawGrid(GuiGraphics graphics) {
        int spacing = (int) Math.max(12, Math.round(40 * zoom));
        int originX = canvasX + canvasW / 2 + (int) Math.round(panX * zoom);
        int originY = canvasY + canvasH / 2 + (int) Math.round(panY * zoom);
        int startX = canvasX + Math.floorMod(originX - canvasX, spacing);
        int startY = canvasY + Math.floorMod(originY - canvasY, spacing);
        for (int x = startX; x < canvasX + canvasW; x += spacing) {
            for (int y = startY; y < canvasY + canvasH; y += spacing) {
                graphics.fill(x, y, x + 1, y + 1, 0x26FFFFFF);
            }
        }
    }

    private void drawLinks(GuiGraphics graphics, SkillCategory category, boolean reveal) {
        PlayerProgress progress = ClientState.progress();
        for (SkillNode node : category.nodes().values()) {
            if (!visible(node, reveal)) {
                continue;
            }
            for (SkillConnection connection : node.connections()) {
                SkillNode source = category.node(connection.fromId());
                if (source == null || !visible(source, reveal)) {
                    continue;
                }
                int sourceRank = progress.skillRank(source.id());
                if (connection.type() == SkillConnection.Type.HIDDEN && sourceRank <= 0) {
                    continue;
                }
                boolean owned = sourceRank > 0 && progress.skillRank(node.id()) > 0;
                boolean met = sourceRank >= connection.requiredRank();
                int color;
                boolean dashed = false;
                switch (connection.type()) {
                    case EXCLUSIVE -> {
                        color = 0xAAE2695C;
                        dashed = true;
                    }
                    case VISUAL_ONLY -> {
                        color = 0x55FFFFFF;
                        dashed = true;
                    }
                    case REQUIRE_ANY -> {
                        color = owned ? RotasTheme.ACCENT_STRONG : met ? Ui.TEXT_MUTED : 0xFF5A4832;
                        dashed = true;
                    }
                    default -> color = owned ? RotasTheme.ACCENT_STRONG : met ? Ui.TEXT_MUTED : 0xFF5A4832;
                }
                line(graphics, centerX(source), centerY(source), centerX(node), centerY(node), owned ? 2 : 1, color, dashed);
            }
        }
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color, boolean dashed) {
        int offset = thickness / 2;
        if (!dashed && (x1 == x2 || y1 == y2)) {
            graphics.fill(Math.min(x1, x2) - offset, Math.min(y1, y2) - offset,
                    Math.max(x1, x2) - offset + thickness, Math.max(y1, y2) - offset + thickness, color);
            return;
        }
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        int stride = Math.max(1, thickness);
        for (int i = 0; i <= steps; i += stride) {
            if (dashed && (i / 6) % 2 == 1) {
                continue;
            }
            int px = x1 + dx * i / steps - offset;
            int py = y1 + dy * i / steps - offset;
            graphics.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    private SkillNode drawNodes(GuiGraphics graphics, SkillCategory category, boolean reveal, int mouseX, int mouseY) {
        PlayerProgress progress = ClientState.progress();
        SkillNode hovered = null;
        float pulse = (float) (0.5 + 0.5 * Math.sin(Util.getMillis() / 260.0));
        boolean mouseInCanvas = Ui.inside(mouseX, mouseY, canvasX, canvasY, canvasW, canvasH);
        for (SkillNode node : category.nodes().values()) {
            if (!visible(node, reveal)) {
                continue;
            }
            int size = nodeSize(node);
            int cx = centerX(node);
            int cy = centerY(node);
            int left = cx - size / 2;
            int top = cy - size / 2;
            if (left + size < canvasX || left > canvasX + canvasW || top + size < canvasY || top > canvasY + canvasH) {
                continue;
            }
            State state = state(category, node);
            int edge;
            int fill;
            switch (state) {
                case MAX -> { edge = Ui.GOOD; fill = 0xFF2F3F24; }
                case UNLOCKED -> { edge = Ui.GOOD; fill = 0xFF29331F; }
                case AFFORDABLE -> { edge = RotasTheme.ACCENT_STRONG; fill = RotasTheme.ACCENT_WASH; }
                case AVAILABLE -> { edge = RotasTheme.PANEL_BORDER; fill = RotasTheme.SURFACE_HIGH; }
                case EXCLUDED -> { edge = Ui.BAD; fill = 0xFF3B211E; }
                case DISABLED -> { edge = RotasTheme.SEPARATOR; fill = RotasTheme.CONTROL_DISABLED; }
                default -> { edge = RotasTheme.SEPARATOR; fill = RotasTheme.PANEL; }
            }
            if (state == State.AFFORDABLE) {
                int alpha = (int) (0x28 + 0x50 * pulse);
                PixelUi.fill(graphics, left - 3, top - 3, size + 6, size + 6, 1, (alpha << 24) | (RotasTheme.ACCENT_STRONG & 0xFFFFFF));
            }
            if (node.id().equals(selectedNodeId)) {
                Ui.border(graphics, left - 2, top - 2, size + 4, size + 4, Ui.TEXT_BRIGHT);
            }
            PixelUi.frame(graphics, left, top, size, size, 1, edge, fill);
            if (node.type() == SkillNode.NodeType.KEYSTONE) {
                Ui.border(graphics, left + 2, top + 2, size - 4, size - 4, RotasTheme.ACCENT);
            }
            float scale = nodeScale();
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(node.icon(), -8, -8);
            graphics.pose().popPose();

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            if (state == State.LOCKED || state == State.DISABLED) {
                graphics.fill(left + 1, top + 1, left + size - 1, top + size - 1, 0x99100B06);
            }
            int rank = progress.skillRank(node.id());
            if (node.maxRank() > 1 && node.maxRank() <= 8) {
                int pipsWidth = node.maxRank() * 5 - 1;
                int pipX = cx - pipsWidth / 2;
                for (int i = 0; i < node.maxRank(); i++) {
                    graphics.fill(pipX + i * 5, top + size + 2, pipX + i * 5 + 4, top + size + 5,
                            i < rank ? Ui.GOOD : RotasTheme.SEPARATOR);
                }
            } else if (node.maxRank() > 8) {
                Ui.labelCentered(graphics, rank + "/" + node.maxRank(), cx, top + size + 2,
                        rank >= node.maxRank() ? Ui.GOOD : Ui.TEXT_MUTED);
            }
            graphics.pose().popPose();

            if (mouseInCanvas && Ui.inside(mouseX, mouseY, left, top, size, size)) {
                hovered = node;
            }
        }
        return hovered;
    }

    private void drawCanvasOverlay(GuiGraphics graphics, SkillCategory category) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        boolean hasDescription = !category.description().isBlank();
        int stripHeight = hasDescription ? 30 : 20;
        graphics.fill(canvasX + 1, canvasY + 1, canvasX + canvasW - 1, canvasY + stripHeight, 0xD01A140E);
        graphics.fill(canvasX + 1, canvasY + stripHeight, canvasX + canvasW - 1, canvasY + stripHeight + 1,
                (category.accentColor() & 0xFFFFFF) | 0x88000000);
        String points = L.t("rotasutils.skills.points", pointsFor(category));
        int tagWidth = font.width(points) + 10;
        Ui.label(graphics, Ui.truncate(category.name(), canvasW - tagWidth - 24), canvasX + 8, canvasY + 7, Ui.TEXT_BRIGHT);
        if (hasDescription) {
            Ui.label(graphics, Ui.truncate(category.description(), canvasW - 16), canvasX + 8, canvasY + 18, Ui.TEXT_MUTED);
        }
        Ui.tag(graphics, canvasX + canvasW - 8 - tagWidth, canvasY + 4, points, pointsFor(category) > 0 ? Ui.ACCENT : Ui.TEXT_MUTED);
        Ui.label(graphics, L.t("rotasutils.skills.controls"), canvasX + 8, canvasY + canvasH - 12, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, Math.round(zoom * 100) + "%", canvasX + canvasW - 8, canvasY + canvasH - 12, Ui.TEXT_MUTED);
        graphics.pose().popPose();
    }

    private void renderDetail(GuiGraphics graphics, SkillCategory category, SkillNode node) {
        int x = guiLeft + guiWidth - Ui.PAD - DETAIL_W;
        int y = canvasY;
        int w = DETAIL_W;
        int h = canvasH;
        int innerW = w - 20;
        int bottom = y + h - 12;
        Ui.panel(graphics, x, y, w, h);
        if (node == null || category == null) {
            Ui.sectionHeading(graphics, L.t("rotasutils.skills.details_heading"), x + 10, y + 10, innerW);
            Ui.wrapped(graphics, L.t("rotasutils.skills.details_hint"),
                    x + 10, y + 28, innerW, Ui.TEXT_MUTED);
            int ly = y + 80;
            ly = legend(graphics, x + 10, ly, Ui.GOOD, L.t("rotasutils.skills.state.learned"));
            ly = legend(graphics, x + 10, ly, RotasTheme.ACCENT_STRONG, L.t("rotasutils.skills.state.ready"));
            ly = legend(graphics, x + 10, ly, RotasTheme.PANEL_BORDER, L.t("rotasutils.skills.state.needs_points"));
            ly = legend(graphics, x + 10, ly, RotasTheme.SEPARATOR, L.t("rotasutils.skills.state.locked"));
            legend(graphics, x + 10, ly, Ui.BAD, L.t("rotasutils.skills.state.blocked"));
            return;
        }
        PlayerProgress progress = ClientState.progress();
        int rank = progress.skillRank(node.id());
        State state = state(category, node);
        int ty = y + 10;
        graphics.renderFakeItem(node.icon(), x + 10, ty);
        Ui.label(graphics, Ui.truncate(node.name(), innerW - 22), x + 32, ty, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate(node.type().display() + (node.maxRank() > 1
                ? L.t("rotasutils.skills.rank_suffix", rank, node.maxRank()) : ""), innerW - 22), x + 32, ty + 10, Ui.TEXT_MUTED);
        ty += 24;
        Ui.tag(graphics, x + 10, ty, stateLabel(state), stateColor(state));
        ty += 20;

        int shownRank = rank < node.maxRank() ? rank + 1 : rank;
        String description = node.rankDescription(Math.max(1, shownRank));
        for (String line : Ui.wrap(description.isBlank() ? L.t("rotasutils.skills.no_description") : description, innerW)) {
            if (ty > bottom - 60) break;
            Ui.label(graphics, line, x + 10, ty, Ui.TEXT);
            ty += 10;
        }

        if (!node.effects().isEmpty() && ty < bottom - 40) {
            ty += 6;
            Ui.sectionHeading(graphics, L.t("rotasutils.skills.effects"), x + 10, ty, innerW);
            ty += 17;
            for (SkillEffect effect : node.effects()) {
                if (ty > bottom - 40) break;
                String now = effectValue(effect, rank, progress.level());
                String next = rank < node.maxRank() ? effectValue(effect, rank + 1, progress.level()) : "";
                String text = effect.type().display() + (now.isEmpty() && next.isEmpty() ? ""
                        : " " + (rank == 0 ? next : now) + (rank > 0 && !next.isEmpty() && !next.equals(now) ? " -> " + next : ""));
                Ui.label(graphics, Ui.truncate(text, innerW), x + 10, ty, Ui.TEXT_DIM);
                ty += 10;
            }
        }

        if (rank >= node.maxRank() || ty > bottom - 20) {
            return;
        }
        ty += 6;
        Ui.sectionHeading(graphics, L.t("rotasutils.skills.needs"), x + 10, ty, innerW);
        ty += 17;
        int requiredLevel = node.requiredLevel(rank);
        ty = check(graphics, x + 10, ty, innerW, L.t("rotasutils.skills.check_level", requiredLevel), progress.level() >= requiredLevel);
        String links = SkillRules.connectionsSatisfied(ClientState::node, progress, node);
        if (!node.connections().isEmpty()) {
            ty = check(graphics, x + 10, ty, innerW, links == null ? L.t("rotasutils.skills.check_earlier") : links, links == null);
        }
        String treeLock = ClientState.audienceBlock(category);
        if (treeLock == null && lockReason(category) != null) {
            treeLock = lockReason(category).equals("locked")
                    ? L.t("rotasutils.skills.tree_unlock_first")
                    : L.t("rotasutils.skills.tree_opens_at", lockReason(category));
        }
        if (treeLock != null) {
            ty = check(graphics, x + 10, ty, innerW, treeLock, false);
        }
        int cost = node.costForRank(rank);
        int have = pointsFor(category);
        ty = check(graphics, x + 10, ty, innerW, L.t("rotasutils.skills.check_cost", cost, have), have >= cost);
        long extra = node.requirements().stream().filter(requirement -> !requirement.recommendationOnly()).count();
        if (extra > 0) {
            Ui.label(graphics, Ui.truncate(L.t("rotasutils.skills.check_server", extra), innerW), x + 10, ty, Ui.WARN);
        }
    }

    private static String effectValue(SkillEffect effect, int rank, int level) {
        if (rank <= 0) {
            return "";
        }
        double value = effect.valueAt(rank, level);
        if (value == 0) {
            return "";
        }
        String number = value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
        return (value > 0 ? "+" : "") + number + (effect.percentage() || effect.type().isMultiplier() ? "%" : "");
    }

    private static int check(GuiGraphics graphics, int x, int y, int width, String text, boolean ok) {
        Ui.label(graphics, Ui.truncate((ok ? "✔ " : "✘ ") + text, width), x, y, ok ? Ui.GOOD : Ui.BAD);
        return y + 11;
    }

    private static int legend(GuiGraphics graphics, int x, int y, int color, String text) {
        PixelUi.frame(graphics, x, y, 10, 10, 1, color, RotasTheme.SURFACE_HIGH);
        Ui.label(graphics, text, x + 16, y + 1, Ui.TEXT);
        return y + 14;
    }

private void unlockSelected() {
        SkillNode node = selected();
        if (node == null || awaiting) {
            return;
        }
        awaiting = true;
        CompoundTag payload = new CompoundTag();
        payload.putString("node", node.id());
        send("unlock_skill", payload);
        Sfx.commit();
        rebuild();
    }

    private void confirmReset() {
        SkillCategory category = category();
        if (category == null) {
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                CompoundTag payload = new CompoundTag();
                payload.putString("category", category.id());
                send("reset_skills", payload);
            }
            minecraft.setScreen(this);
        }, L.c("rotasutils.skills.reset_confirm", category.name()),
                L.c("rotasutils.skills.reset_detail")));
    }

private SkillNode nodeAt(double mouseX, double mouseY) {
        SkillCategory category = category();
        if (category == null) {
            return null;
        }
        boolean reveal = revealsHidden(ClientState.progress());
        for (SkillNode node : category.nodes().values()) {
            int size = nodeSize(node);
            if (visible(node, reveal) && Ui.inside((int) mouseX, (int) mouseY,
                    centerX(node) - size / 2, centerY(node) - size / 2, size, size)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Ui.inside((int) mouseX, (int) mouseY, canvasX, canvasY, canvasW, canvasH)) {
            SkillNode node = nodeAt(mouseX, mouseY);
            if (node == null) {
                dragging = true;
                return true;
            }
            long now = Util.getMillis();
            boolean doubleClick = node.id().equals(lastClickedNode) && now - lastClickAt < DOUBLE_CLICK_MS;
            lastClickedNode = node.id();
            lastClickAt = now;
            if (!node.id().equals(selectedNodeId)) {
                selectedNodeId = node.id();
                Sfx.select();
                rebuild();
            }
            if (doubleClick && category() != null && state(category(), node) == State.AFFORDABLE) {
                unlockSelected();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            panX += dragX / zoom;
            panY += dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside((int) mouseX, (int) mouseY, canvasX, canvasY, canvasW, canvasH)) {
            double previous = zoom;
            zoom = Math.max(0.4, Math.min(2.5, zoom * (delta > 0 ? 1.15 : 1 / 1.15)));
            double offsetX = mouseX - (canvasX + canvasW / 2.0);
            double offsetY = mouseY - (canvasY + canvasH / 2.0);
            panX += offsetX / zoom - offsetX / previous;
            panY += offsetY / zoom - offsetY / previous;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 70 && getFocused() == null && category() != null) {
            fit(category());
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && selected() != null && state(category(), selected()) == State.AFFORDABLE) {
            unlockSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
