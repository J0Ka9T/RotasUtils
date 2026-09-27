package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.List;

/** Searchable, server-snapshot-backed housing administration overview. */
@Environment(EnvType.CLIENT)
public final class HouseManagerScreen extends RotasScreen {
    private final List<HouseAdminPresentation.OverviewRow> emptyRows = List.of();
    private HouseAdminLayout.Overview layout;
    private ScrollPanel houseList;
    private EditBox search;
    private String searchText = "";
    private List<HouseAdminPresentation.OverviewRow> allRows = emptyRows;
    private List<HouseAdminPresentation.OverviewRow> rows = emptyRows;

    public HouseManagerScreen(Screen parent) {
        super(L.t("rotasutils.house.admin.title"), parent);
    }

    public HouseManagerScreen() {
        this(null);
    }

    /** Kept as a small pure seam so the overview route can be checked without a client runtime. */
    public static boolean rentalSettingsEnabled() {
        return true;
    }

    public static HouseScreenActions.Route routeOverviewAction(String action) {
        return HouseScreenActions.routeOverview(action);
    }

    @Override
    protected boolean renderPanelsAfterContent() {
        // The list is registered without a background. Paint it after the
        // content frames so rows stay visible inside the inset list region.
        return true;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 520);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader(text("rotasutils.house.admin.title", "Housing"));
        layout = HouseAdminLayout.overview(guiWidth, guiHeight);

        ClientHouseAdminState state = ClientState.houseAdmin();
        allRows = state == null ? emptyRows : HouseAdminPresentation.overviewRows(state, "");
        rows = state == null ? emptyRows : HouseAdminPresentation.overviewRows(state, searchText);

        HouseAdminLayout.Rect searchRect = at(layout.search());
        search = new EditBox(font, searchRect.x(), searchRect.y(), Math.max(1, searchRect.width()),
                Math.max(12, Math.min(20, searchRect.height())), L.c("rotasutils.house.admin.search"));
        search.setBordered(false);
        search.setMaxLength(96);
        search.setValue(searchText);
        search.setHint(Ui.text(text("rotasutils.house.admin.search", "Search houses")));
        search.setTextColor(Ui.TEXT_BRIGHT);
        search.setTextColorUneditable(Ui.TEXT_MUTED);
        search.setResponder(value -> {
            searchText = value;
            refreshRows();
        });
        addRenderableWidget(search);

        HouseAdminLayout.Rect listRect = at(layout.list());
        houseList = new ScrollPanel(listRect.x(), listRect.y(), Math.max(1, listRect.width()),
                Math.max(1, listRect.height()), layout.rowHeight())
                .withoutBackground()
                .rowHitInsets(0, 2);
        houseList.setRows(rows.size(), this::renderHouseRow, this::clickHouseRow);
        registerPanel(houseList);

        buildActions(state);
        HouseAdminLayout.Rect footer = at(layout.footer());
        addRenderableWidget(Ui.button(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(footer.x(), footer.y(), Math.max(1, Math.min(92, footer.width())), footer.height())
                .build());
    }

    private void buildActions(ClientHouseAdminState state) {
        HouseAdminLayout.OverviewControls controls = HouseAdminLayout.overviewControls(layout);
        HouseAdminLayout.Rect wandBounds = at(controls.wand());
        HouseAdminLayout.Rect createBounds = at(controls.create());
        HouseAdminLayout.Rect settingsBounds = at(controls.settings());

        if (HouseAdminLayout.renderableButton(controls.wand())) {
            addRenderableWidget(Ui.button(Ui.text(text(
                    "rotasutils.house.admin.get_wand", "Get House Wand")), button -> {
                send("give_house_wand");
                Sfx.add();
            }).tooltip(Tooltip.create(Ui.text(text("rotasutils.house.admin.wand_hint",
                    "Use the wand to mark two corners."))))
                    .bounds(wandBounds.x(), wandBounds.y(), wandBounds.width(), wandBounds.height()).build());
        }

        HouseAdminPresentation.SelectionSummary selection = state == null
                ? null : HouseAdminPresentation.selection(state);
        boolean canCreate = selection != null && selection.complete()
                && state != null && !state.tiers().isEmpty();
        if (HouseAdminLayout.renderableButton(controls.create())) {
            RotasButton create = addRenderableWidget(Ui.primaryButton(Ui.text(text(
                    "rotasutils.house.admin.create", "Create from selection")), button -> {
                if (canCreate) {
                    minecraft.setScreen(new HouseCreateScreen(this));
                    Sfx.page();
                }
            }).bounds(createBounds.x(), createBounds.y(), createBounds.width(), createBounds.height()).build());
            create.active = canCreate;
        }

        if (HouseAdminLayout.renderableButton(controls.settings())) {
            RotasButton settings = addRenderableWidget(Ui.button(Ui.text(text(
                    "rotasutils.house.admin.rental_settings", "Rental settings")), button -> {
                minecraft.setScreen(new HouseSettingsScreen(this));
                Sfx.page();
            }).tooltip(Tooltip.create(Ui.text(text("rotasutils.house.admin.rental_settings_hint",
                    "Edit billing, membership, and house tiers."))))
                    .bounds(settingsBounds.x(), settingsBounds.y(), settingsBounds.width(), settingsBounds.height()).build());
            settings.active = rentalSettingsEnabled();
        }
    }

    private void refreshRows() {
        ClientHouseAdminState state = ClientState.houseAdmin();
        allRows = state == null ? emptyRows : HouseAdminPresentation.overviewRows(state, "");
        rows = state == null ? emptyRows : HouseAdminPresentation.overviewRows(state, searchText);
        if (houseList != null) {
            houseList.setRows(rows.size(), this::renderHouseRow, this::clickHouseRow);
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        rebuild();
    }

    private void renderHouseRow(GuiGraphics graphics, int index, int x, int y,
                                int rowWidth, int rowHeight, boolean hovered) {
        HouseAdminPresentation.OverviewRow row = rows.get(index);
        Ui.rowCard(graphics, x, y, rowWidth, Math.max(1, rowHeight - 2), hovered, false);
        String status = statusText(row);
        int tagWidth = Ui.textWidth(status) + 12;
        int right = x + rowWidth - 6;
        Ui.tag(graphics, Math.max(x + 2, right - tagWidth), y + 3, status, statusColor(row));

        int labelX = x + 10;
        int labelWidth = Math.max(30, right - tagWidth - labelX - 8);
        Ui.label(graphics, Ui.truncate(row.name(), labelWidth), labelX, y + 3, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate(format("rotasutils.house.admin.row_identity", "{0}  -  {1}",
                        row.id(), row.dimension()), labelWidth),
                labelX, y + 13, Ui.TEXT_MUTED);
        String details = format("rotasutils.house.admin.row_details", "{0}  -  tier {1}", row.size(), row.tier());
        Ui.labelRight(graphics, Ui.truncate(details, tagWidth + 8), right - 2, y + 13, Ui.TEXT_DIM);
    }

    private void clickHouseRow(int index, int button) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        HouseAdminPresentation.OverviewRow row = rows.get(index);
        HouseScreenActions.Route route = HouseScreenActions.routeOverview("house:" + row.id());
        if (route.target() == HouseScreenActions.OverviewTarget.EDIT) {
            Sfx.select();
            minecraft.setScreen(new HouseEditScreen(route.houseId(), this));
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HouseAdminLayout.Rect content = at(layout.content());
        HouseAdminLayout.Rect searchRect = at(layout.search());
        HouseAdminLayout.Rect listRect = at(layout.list());
        HouseAdminLayout.Rect listHeading = at(layout.listHeading());
        HouseAdminLayout.Rect actionHeading = at(layout.actionHeading());
        HouseAdminLayout.Rect selectionRect = at(layout.selection());
        HouseAdminLayout.Rect actionRect = at(layout.action());

        if (content.height() > 0) {
            Ui.modernPanel(graphics, content.x(), content.y(), content.width(), content.height());
        }
        if (searchRect.height() > 0) {
            Ui.searchFrame(graphics, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(),
                    search != null && search.isFocused());
        }
        if (listRect.height() > 0) {
            Ui.inset(graphics, listRect.x(), listRect.y(), listRect.width(), listRect.height());
        }
        if (selectionRect.height() > 0) {
            Ui.inset(graphics, selectionRect.x(), selectionRect.y(), selectionRect.width(), selectionRect.height());
        }
        if (actionRect.height() > 0) {
            Ui.modernPanel(graphics, actionRect.x(), actionRect.y(), actionRect.width(), actionRect.height());
        }
        if (listHeading.height() > 0) {
            Ui.sectionHeading(graphics, text("rotasutils.house.admin.houses_heading", "HOUSES"),
                    listHeading.x(), listHeading.y(), listHeading.width());
        }
        if (actionHeading.height() > 0) {
            Ui.sectionHeading(graphics, text("rotasutils.house.admin.actions_heading", "ACTIONS"),
                    actionHeading.x(), actionHeading.y(), actionHeading.width());
        }

        ClientHouseAdminState state = ClientState.houseAdmin();
        if (state == null) {
            renderEmptyState(graphics, text("rotasutils.house.admin.waiting", "Housing data is not available."),
                    "", Ui.TEXT_DIM);
        } else if (rows.isEmpty()) {
            boolean filtering = !searchText.trim().isEmpty();
            boolean noHouses = !filtering && allRows.isEmpty();
            renderEmptyState(graphics,
                    noHouses ? text("rotasutils.house.admin.no_houses_label", "No houses yet")
                            : text("rotasutils.house.admin.no_results", "No houses match this search."),
                    noHouses ? text("rotasutils.house.admin.no_houses",
                            "No houses yet. Use the House Wand to mark two corners, then create a house.")
                            : text("rotasutils.house.admin.search_empty_hint", "Try a different name or id."),
                    noHouses ? Ui.TEXT_BRIGHT : Ui.TEXT_DIM);
        }

        if (state != null) {
            HouseAdminPresentation.SelectionSummary selection = HouseAdminPresentation.selection(state);
            HouseAdminLayout.Rect selectionHeading = at(layout.selectionHeading());
            if (selection != null && layout.selectionSummaryVisible()
                    && selectionRect.height() > 0 && selectionHeading.height() > 0) {
                Ui.sectionHeading(graphics, text("rotasutils.house.admin.selection_heading", "WAND SELECTION"),
                        selectionHeading.x(), selectionHeading.y(), selectionHeading.width());
                renderBounded(graphics, layout.selectionText(), selectionText(selection), Ui.TEXT_MUTED);
            }
        }
    }

    private void renderEmptyState(GuiGraphics graphics, String title, String body, int color) {
        HouseAdminLayout.EmptyState empty = layout.emptyState();
        HouseAdminLayout.TextRegion titleRegion = empty.title();
        if (titleRegion.bounds().height() > 0) {
            HouseAdminLayout.Rect titleRect = at(titleRegion.bounds());
            Ui.labelCentered(graphics, Ui.truncate(title, Math.max(1, titleRect.width())),
                    titleRect.x() + titleRect.width() / 2, titleRect.y(), color);
        } else if (body.isEmpty()) {
            renderBounded(graphics, empty.body(), title, color);
        }
        if (!body.isEmpty()) {
            renderBounded(graphics, empty.body(), body, Ui.TEXT_DIM);
        }
    }

    private void renderBounded(GuiGraphics graphics, HouseAdminLayout.TextRegion region,
                               String value, int color) {
        if (region.maxLines() <= 0 || region.bounds().width() <= 0 || region.bounds().height() <= 0) {
            return;
        }
        List<String> lines = Ui.wrap(value, region.bounds().width());
        int count = Math.min(region.maxLines(), lines.size());
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == count - 1 && lines.size() > count) {
                line = Ui.truncate(line + "…", region.bounds().width());
            }
            HouseAdminLayout.Rect lineRect = at(region.line(i));
            Ui.label(graphics, Ui.truncate(line, Math.max(1, lineRect.width())),
                    lineRect.x(), lineRect.y(), color);
        }
    }

    private static int statusColor(HouseAdminPresentation.OverviewRow row) {
        return switch (row.statusRole()) {
            case GOOD -> Ui.GOOD;
            case ACCENT -> Ui.ACCENT;
            case WARNING -> Ui.WARN;
            case OWNED -> Ui.ACCENT;
            case MUTED -> Ui.TEXT_MUTED;
        };
    }

    private static String statusText(HouseAdminPresentation.OverviewRow row) {
        String tag = row.tag().toLowerCase(java.util.Locale.ROOT);
        return text("rotasutils.house.status." + tag, row.tag());
    }

    private HouseAdminLayout.Rect at(HouseAdminLayout.Rect rect) {
        return new HouseAdminLayout.Rect(guiLeft + rect.x(), guiTop + rect.y(), rect.width(), rect.height());
    }

    private static String text(String key, String fallback) {
        String value = L.t(key);
        return value.equals(key) ? fallback : value;
    }

    private static String selectionText(HouseAdminPresentation.SelectionSummary summary) {
        return switch (summary.state()) {
            case MISSING -> text("rotasutils.house.selection.missing",
                    "No House Wand selection. Mark two corners to create a house.");
            case PARTIAL -> text("rotasutils.house.selection.partial",
                    "Selection is partial. Mark the second corner with the House Wand.");
            case COMPLETE -> format("rotasutils.house.selection.complete",
                    "Selection: {0} {1}x{2}x{3} ({4} blocks)", summary.dimension(), summary.sizeX(),
                    summary.sizeY(), summary.sizeZ(), summary.volume());
        };
    }

    private static String format(String key, String fallback, Object... args) {
        String value = text(key, fallback);
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value;
    }
}
