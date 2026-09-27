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
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.house.HouseAdminValidator;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseTier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The themed, atomic editor for global rental rules and house tiers. */
@Environment(EnvType.CLIENT)
public final class HouseSettingsScreen extends RotasScreen {
    private static final int COMPACT_ROW_HEIGHT = 34;
    private HouseConfigDraft draft;
    private HouseAdminLayout.Settings layout;
    private ScrollPanel tierList;
    private RotasButton saveButton;
    private String validationMessage = "";
    private boolean submitted;
    private long submittedBaseRevision = -1L;
    private HouseConfig submittedCandidate;
    private boolean submittedRejected;
    private boolean compactFields;
    private int compactScroll;
    private final List<CompactRow> compactRows = new ArrayList<>();
    private final List<EditBox> compactInputs = new ArrayList<>();

    public HouseSettingsScreen(Screen parent) {
        this(parent, null);
    }

    public HouseSettingsScreen(Screen parent, HouseConfigDraft draft) {
        super(L.t("rotasutils.house.settings.title"), parent);
        this.draft = draft;
    }

    public HouseSettingsScreen(HouseConfigDraft draft, Screen parent) {
        this(parent, draft);
    }

    public HouseSettingsScreen() {
        this((Screen) null, null);
    }

    public HouseConfigDraft draft() {
        return draft;
    }

    /**
     * A content refresh is a successful save only when it advances the revision
     * captured by this request and carries the same complete candidate values.
     * This pure seam intentionally does not consult global feedback state.
     */
    public static boolean shouldRebaseSubmittedDraft(HouseConfig snapshot, long submittedBaseRevision,
                                                     HouseConfig submittedCandidate) {
        return snapshot != null && submittedCandidate != null
                && snapshot.revision() > submittedBaseRevision
                && sameConfigValues(snapshot, submittedCandidate);
    }

    /** First field-specific local error, used even while Save is disabled. */
    public static String firstLocalError(HouseConfigDraft draft) {
        if (draft == null) {
            return "";
        }
        return draft.validationErrors().stream()
                .findFirst()
                .map(error -> error.field() + ": " + error.message())
                .orElse("");
    }

    private static boolean sameConfigValues(HouseConfig left, HouseConfig right) {
        return left != null && right != null
                && java.util.Objects.equals(left.currency(), right.currency())
                && left.paymentIntervalMillis() == right.paymentIntervalMillis()
                && left.reminderLeadMillis() == right.reminderLeadMillis()
                && left.graceMillis() == right.graceMillis()
                && left.buyoutMultiplier() == right.buyoutMultiplier()
                && left.baseMemberLimit() == right.baseMemberLimit()
                && left.memberSlotPrice() == right.memberSlotPrice()
                && left.maxPurchasedMemberSlots() == right.maxPurchasedMemberSlots()
                && java.util.Objects.equals(left.tiers(), right.tiers());
    }

    @Override
    protected boolean renderPanelsAfterContent() {
        // Tier rows and the compact settings scroller are transparent overlays;
        // paint them after the opaque calculated panels so they remain visible.
        return true;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 520);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader(text("rotasutils.house.settings.header", "Rental Settings"));

        ClientHouseAdminState state = ClientState.houseAdmin();
        if (draft == null && state != null) {
            draft = HouseConfigDraft.from(state);
        }
        if (draft == null) {
            addUnavailableBackButton();
            return;
        }

        layout = HouseAdminLayout.settings(guiWidth, guiHeight, draft.tiers().size());
        buildFields();
        buildTierList();
        buildActions();
    }

    private void addUnavailableBackButton() {
        HouseAdminLayout.Rect footer = new HouseAdminLayout.Rect(
                guiLeft + Ui.PAD, guiTop + guiHeight - 30, Math.max(1, guiWidth - Ui.PAD * 2), 22);
        addRenderableWidget(Ui.button(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(footer.x(), footer.y(), footer.width(), footer.height()).build());
    }

    private void buildFields() {
        HouseAdminLayout.Rect fields = at(layout.fields());
        compactFields = !layout.fieldRowsReachable();
        compactRows.clear();
        compactInputs.clear();
        compactScroll = 0;
        if (compactFields) {
            buildCompactFields(fields);
            return;
        }
        List<HouseAdminLayout.Rect> rows = layout.fieldRows();
        HouseAdminLayout.Rect currency = rows.get(0);
        addTextField(currency.x(), currency.y(), currency.width(), currency.height(), draft.currency(), 128,
                "rotasutils.house.settings.currency", draft::setCurrency);
        addTimeFields(rows.get(1), draft.paymentDays(), draft.paymentHours(), draft.paymentMinutes(),
                draft::setPaymentDays, draft::setPaymentHours, draft::setPaymentMinutes);
        addTimeFields(rows.get(2), draft.reminderDays(), draft.reminderHours(), draft.reminderMinutes(),
                draft::setReminderDays, draft::setReminderHours, draft::setReminderMinutes);
        addTimeFields(rows.get(3), draft.graceDays(), draft.graceHours(), draft.graceMinutes(),
                draft::setGraceDays, draft::setGraceHours, draft::setGraceMinutes);
        addPairFields(rows.get(4), draft.buyoutMultiplier(), draft.baseMemberLimit(), 12, 12,
                "rotasutils.house.settings.buyout", draft::setBuyoutMultiplier,
                "rotasutils.house.settings.base_members", draft::setBaseMemberLimit);
        addPairFields(rows.get(5), draft.memberSlotPrice(), draft.maxPurchasedMemberSlots(), 20, 12,
                "rotasutils.house.settings.slot_price", draft::setMemberSlotPrice,
                "rotasutils.house.settings.max_slots", draft::setMaxPurchasedMemberSlots);
    }

    private void addTimeFields(HouseAdminLayout.Rect row, String days, String hours, String minutes,
                               Consumer<String> setDays, Consumer<String> setHours, Consumer<String> setMinutes) {
        int gap = Math.min(Ui.GAP, Math.max(1, row.width() / 24));
        int width = Math.max(1, (row.width() - gap * 2) / 3);
        addTextField(row.x(), row.y(), width, row.height(), days, 19,
                "rotasutils.house.settings.days", setDays);
        addTextField(row.x() + width + gap, row.y(), width, row.height(), hours, 19,
                "rotasutils.house.settings.hours", setHours);
        addTextField(row.x() + (width + gap) * 2, row.y(),
                Math.max(1, row.width() - (width + gap) * 2), row.height(), minutes, 19,
                "rotasutils.house.settings.minutes", setMinutes);
    }

    private void addPairFields(HouseAdminLayout.Rect row, String first, String second,
                               int firstMaxLength, int secondMaxLength, String firstHint,
                               Consumer<String> setFirst, String secondHint, Consumer<String> setSecond) {
        int gap = Math.min(Ui.GAP, Math.max(1, row.width() / 24));
        int width = Math.max(1, (row.width() - gap) / 2);
        addTextField(row.x(), row.y(), width, row.height(), first, firstMaxLength, firstHint, setFirst);
        addTextField(row.x() + width + gap, row.y(), Math.max(1, row.width() - width - gap),
                row.height(), second, secondMaxLength, secondHint, setSecond);
    }

    /**
     * The smallest supported canvas cannot display all fourteen inputs at once.
     * Keep them as real EditBoxes, but present the groups as a compact, scrollable
     * form so every raw value remains reachable without overlapping the Save row.
     */
    private void buildCompactFields(HouseAdminLayout.Rect fields) {
        compactRows.add(new CompactRow("rotasutils.house.settings.currency",
                List.of(entry("rotasutils.house.settings.currency", 128, draft::currency, draft::setCurrency))));
        compactRows.add(new CompactRow("rotasutils.house.settings.payment",
                List.of(entry("rotasutils.house.settings.days", 19, draft::paymentDays, draft::setPaymentDays),
                        entry("rotasutils.house.settings.hours", 19, draft::paymentHours, draft::setPaymentHours),
                        entry("rotasutils.house.settings.minutes", 19, draft::paymentMinutes, draft::setPaymentMinutes))));
        compactRows.add(new CompactRow("rotasutils.house.settings.reminder",
                List.of(entry("rotasutils.house.settings.days", 19, draft::reminderDays, draft::setReminderDays),
                        entry("rotasutils.house.settings.hours", 19, draft::reminderHours, draft::setReminderHours),
                        entry("rotasutils.house.settings.minutes", 19, draft::reminderMinutes, draft::setReminderMinutes))));
        compactRows.add(new CompactRow("rotasutils.house.settings.grace",
                List.of(entry("rotasutils.house.settings.days", 19, draft::graceDays, draft::setGraceDays),
                        entry("rotasutils.house.settings.hours", 19, draft::graceHours, draft::setGraceHours),
                        entry("rotasutils.house.settings.minutes", 19, draft::graceMinutes, draft::setGraceMinutes))));
        compactRows.add(new CompactRow("rotasutils.house.settings.buyout",
                List.of(entry("rotasutils.house.settings.buyout", 12, draft::buyoutMultiplier, draft::setBuyoutMultiplier),
                        entry("rotasutils.house.settings.base_members", 12, draft::baseMemberLimit, draft::setBaseMemberLimit))));
        compactRows.add(new CompactRow("rotasutils.house.settings.slot_price",
                List.of(entry("rotasutils.house.settings.slot_price", 20, draft::memberSlotPrice, draft::setMemberSlotPrice),
                        entry("rotasutils.house.settings.max_slots", 12, draft::maxPurchasedMemberSlots,
                                draft::setMaxPurchasedMemberSlots))));

        for (CompactRow row : compactRows) {
            for (CompactEntry entry : row.entries()) {
                EditBox field = new EditBox(font, fields.x() + 6, fields.y() + 13,
                        Math.max(1, fields.width() - 12), 18, L.c(entry.hintKey()));
                field.setBordered(false);
                field.setMaxLength(entry.maxLength());
                field.setValue(safe(entry.getter().get()));
                field.setTextColor(Ui.TEXT_BRIGHT);
                field.setTextColorUneditable(Ui.TEXT_MUTED);
                field.setResponder(input -> {
                    entry.setter().accept(input);
                    validationMessage = "";
                    updateSaveButton();
                });
                compactInputs.add(field);
                addRenderableWidget(field);
            }
        }
        repositionCompactFields();
    }

    private static CompactEntry entry(String hintKey, int maxLength, Supplier<String> getter,
                                      Consumer<String> setter) {
        return new CompactEntry(hintKey, maxLength, getter, setter);
    }

    private void repositionCompactFields() {
        if (!compactFields || layout == null) {
            return;
        }
        HouseAdminLayout.Rect fields = at(layout.fields());
        int innerX = fields.x() + 6;
        int innerWidth = Math.max(1, fields.width() - 12);
        int rowGap = 2;
        int inputHeight = Math.min(18, Math.max(1, fields.height() - 14));
        int inputIndex = 0;
        for (int rowIndex = 0; rowIndex < compactRows.size(); rowIndex++) {
            CompactRow row = compactRows.get(rowIndex);
            int rowY = fields.y() + 1 + (rowIndex - compactScroll) * COMPACT_ROW_HEIGHT;
            int gap = Math.min(Ui.GAP, Math.max(1, innerWidth / 24));
            int entryWidth = Math.max(1, (innerWidth - gap * (row.entries().size() - 1))
                    / row.entries().size());
            for (int column = 0; column < row.entries().size(); column++) {
                EditBox field = compactInputs.get(inputIndex++);
                int x = innerX + column * (entryWidth + gap);
                int width = column == row.entries().size() - 1
                        ? Math.max(1, innerWidth - (entryWidth + gap) * column)
                        : entryWidth;
                field.setX(x);
                field.setY(rowY + 13);
                field.setWidth(width);
                boolean visible = rowY + 13 >= fields.y() + rowGap
                        && rowY + 13 + inputHeight <= fields.bottom() - rowGap;
                field.setVisible(visible);
            }
        }
    }

    private void renderCompactFields(GuiGraphics graphics, HouseAdminLayout.Rect fields,
                                     int mouseX, int mouseY) {
        int innerX = fields.x() + 3;
        int rowWidth = Math.max(1, fields.width() - 6);
        graphics.enableScissor(fields.x() + 1, fields.y() + 1,
                fields.right() - 1, fields.bottom() - 1);
        for (int index = 0; index < compactRows.size(); index++) {
            int rowY = fields.y() + 1 + (index - compactScroll) * COMPACT_ROW_HEIGHT;
            if (rowY + COMPACT_ROW_HEIGHT <= fields.y() || rowY >= fields.bottom()) {
                continue;
            }
            CompactRow row = compactRows.get(index);
            Ui.rowCard(graphics, innerX, rowY, rowWidth, COMPACT_ROW_HEIGHT - 2,
                    Ui.inside(mouseX, mouseY, innerX, rowY, rowWidth, COMPACT_ROW_HEIGHT - 2), false);
            Ui.label(graphics, text(row.labelKey(), row.labelKey()), innerX + 6, rowY + 3,
                    Ui.TEXT_MUTED);
        }
        graphics.disableScissor();
        if (compactRows.size() > Math.max(1, fields.height() / COMPACT_ROW_HEIGHT)) {
            int trackX = fields.right() - 5;
            int trackHeight = Math.max(1, fields.height() - 4);
            int visible = Math.max(1, fields.height() / COMPACT_ROW_HEIGHT);
            int thumbHeight = Math.max(8, trackHeight * visible / compactRows.size());
            int maxScroll = Math.max(1, compactRows.size() - visible);
            int thumbY = fields.y() + 2 + (trackHeight - thumbHeight) * compactScroll / maxScroll;
            graphics.fill(trackX, fields.y() + 2, trackX + 2, fields.bottom() - 2, Ui.PANEL_INSET);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, Ui.ACCENT);
        }
    }

    private void addTextField(int x, int y, int width, int height, String value, int maxLength,
                              String hintKey, java.util.function.Consumer<String> responder) {
        EditBox field = new EditBox(font, x, y, Math.max(1, width), Math.max(1, height), L.c(hintKey));
        field.setBordered(false);
        field.setMaxLength(maxLength);
        field.setValue(value == null ? "" : value);
        field.setTextColor(Ui.TEXT_BRIGHT);
        field.setTextColorUneditable(Ui.TEXT_MUTED);
        field.setResponder(input -> {
            responder.accept(input);
            validationMessage = "";
            updateSaveButton();
        });
        addRenderableWidget(field);
    }

    private void buildTierList() {
        HouseAdminLayout.Rect viewport = at(layout.tierViewport());
        tierList = new ScrollPanel(viewport.x(), viewport.y(), Math.max(1, viewport.width()),
                Math.max(1, viewport.height()), layout.tierRowHeight())
                .withoutBackground().rowHitInsets(0, 2);
        tierList.setRows(draft.tiers().size(), this::renderTierRow, this::openTier);
        registerPanel(tierList);
    }

    private void buildActions() {
        HouseAdminLayout.Rect actions = at(layout.actions());
        int gap = Math.min(Ui.GAP, Math.max(2, actions.width() / 40));
        int addWidth = Math.min(92, Math.max(1, (actions.width() - gap * 2) / 3));
        int saveWidth = Math.min(132, Math.max(1, (actions.width() - addWidth - gap) / 2));
        addRenderableWidget(Ui.button(L.c("rotasutils.house.settings.add_tier"), button -> addTier())
                .tooltip(Tooltip.create(Ui.text(text("rotasutils.house.settings.tier_hint",
                        "Open a tier to edit its prices."))))
                .bounds(actions.x(), actions.y(), addWidth, Math.max(1, actions.height())).build());
        saveButton = addRenderableWidget(Ui.primaryButton(L.c("rotasutils.house.settings.save"), button -> save())
                .bounds(actions.x() + addWidth + gap, actions.y(), saveWidth, Math.max(1, actions.height())).build());
        updateSaveButton();

        HouseAdminLayout.Rect footer = at(layout.footer());
        addRenderableWidget(Ui.button(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(footer.x(), footer.y(), Math.max(1, footer.width()), Math.max(1, footer.height())).build());
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = !submitted && draft != null && draft.isValid() && draft.hasChanges();
        }
    }

    private void renderTierRow(GuiGraphics graphics, int index, int x, int y,
                               int rowWidth, int rowHeight, boolean hovered) {
        HouseConfigDraft.TierDraft tier = draft.tiers().get(index);
        Ui.rowCard(graphics, x, y, rowWidth, Math.max(1, rowHeight - 2), hovered, false);
        int usable = Math.max(1, rowWidth - 14);
        String id = tier.id() == null || tier.id().isBlank()
                ? text("rotasutils.house.tier.new_title", "New tier") : tier.id();
        String label = format("rotasutils.house.settings.tier_row", "{0}  -  deposit {1}  -  maintenance {2}",
                id, safe(tier.deposit()), safe(tier.maintenance()));
        Ui.label(graphics, Ui.truncate(label, usable), x + 7, y + 4, hovered ? Ui.TEXT_BRIGHT : Ui.TEXT);
    }

    private void openTier(int index, int button) {
        if (index < 0 || index >= draft.tiers().size()) {
            return;
        }
        Sfx.select();
        minecraft.setScreen(new HouseTierEditScreen(this, draft, index));
    }

    private void addTier() {
        if (draft.tiers().size() >= 64) {
            validationMessage = text("rotasutils.house.settings.too_many_tiers",
                    "At most 64 house tiers are allowed.");
            Sfx.error();
            return;
        }
        draft.addTier("", "0", "0");
        Sfx.add();
        minecraft.setScreen(new HouseTierEditScreen(this, draft, draft.tiers().size() - 1));
    }

    private void save() {
        if (submitted || draft == null) {
            return;
        }
        List<HouseAdminValidator.Error> errors = draft.validationErrors();
        if (!errors.isEmpty()) {
            validationMessage = errors.get(0).field() + ": " + errors.get(0).message();
            Sfx.error();
            updateSaveButton();
            return;
        }
        try {
            net.minecraft.nbt.CompoundTag payload = draft.toPayload();
            HouseAdminService.ConfigRequest request = draft.toConfigRequest();
            HouseConfig candidate = candidateConfig(request);
            send("house_save_config", payload);
            submittedBaseRevision = request.expectedConfigRevision();
            submittedCandidate = candidate;
            submittedRejected = false;
            submitted = true;
            validationMessage = "";
            Sfx.save();
            updateSaveButton();
        } catch (IllegalArgumentException invalid) {
            validationMessage = invalid.getMessage();
            Sfx.error();
            updateSaveButton();
        }
    }

    private static HouseConfig candidateConfig(HouseAdminService.ConfigRequest request) {
        Map<String, HouseTier> tiers = new LinkedHashMap<>();
        for (HouseTier tier : request.tiers()) {
            tiers.put(tier.id(), tier);
        }
        return new HouseConfig(request.currency(), request.paymentIntervalMillis(), request.reminderLeadMillis(),
                request.graceMillis(), request.buyoutMultiplier(), request.baseMemberLimit(),
                request.memberSlotPrice(), request.maxPurchasedMemberSlots(), tiers,
                request.expectedConfigRevision());
    }

    @Override
    public void onDataRefreshed() {
        ClientHouseAdminState state = ClientState.houseAdmin();
        if (state == null) {
            return;
        }
        if (draft == null) {
            draft = HouseConfigDraft.from(state);
            clearSubmission();
        } else if (submitted) {
            if (!submittedRejected && shouldRebaseSubmittedDraft(state.config(), submittedBaseRevision,
                    submittedCandidate)) {
                // Only an advanced, exact candidate snapshot completes the request.
                draft.rebase(state);
                clearSubmission();
                rebuild();
                return;
            }
            if (state.config().revision() <= submittedBaseRevision) {
                // Same-revision refreshes are unrelated to this request; do not
                // let a global feedback flag discard or rebase the typed draft.
                return;
            }
            // A stale or external advanced snapshot becomes the new concurrency
            // baseline, while the submitted raw values remain for review/retry.
            draft.rebasePreservingInput(state);
            clearSubmission();
        } else {
            // Unsubmitted refreshes retain the exact raw fields as well.
            draft.rebasePreservingInput(state);
        }
        rebuild();
    }

    private void clearSubmission() {
        submitted = false;
        submittedBaseRevision = -1L;
        submittedCandidate = null;
        submittedRejected = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (compactFields && layout != null) {
            HouseAdminLayout.Rect fields = at(layout.fields());
            if (Ui.inside((int) mouseX, (int) mouseY, fields.x(), fields.y(), fields.width(), fields.height())
                    && compactRows.size() > 1) {
                int visible = Math.max(1, fields.height() / COMPACT_ROW_HEIGHT);
                int maxScroll = Math.max(0, compactRows.size() - visible);
                int amount = Math.max(1, (int) Math.ceil(Math.abs(delta)));
                compactScroll = Math.max(0, Math.min(maxScroll,
                        compactScroll - (int) Math.signum(delta) * amount));
                repositionCompactFields();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void tick() {
        super.tick();
        if (!submitted) {
            return;
        }
        String feedback = ClientState.feedbackMessage();
        if (!feedback.isEmpty() && !ClientState.feedbackOk()) {
            submittedRejected = true;
            validationMessage = feedback;
            if (!feedback.equals("house.stale")) {
                // Failed validation has no content sync, so release the save gate
                // on the next client tick while retaining the typed draft for retry.
                clearSubmission();
            }
            updateSaveButton();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layout == null || draft == null) {
            HouseAdminLayout.Rect body = new HouseAdminLayout.Rect(guiLeft + Ui.PAD, guiTop + 50,
                    Math.max(1, guiWidth - Ui.PAD * 2), Math.max(1, guiHeight - 84));
            Ui.modernPanel(graphics, body.x(), body.y(), body.width(), body.height());
            Ui.labelCentered(graphics, text("rotasutils.house.settings.no_snapshot",
                    "Waiting for the server housing snapshot."), body.x() + body.width() / 2, body.y() + 22, Ui.TEXT_DIM);
            return;
        }
        HouseAdminLayout.Rect content = at(layout.content());
        HouseAdminLayout.Rect fields = at(layout.fields());
        HouseAdminLayout.Rect viewport = at(layout.tierViewport());
        HouseAdminLayout.Rect actions = at(layout.actions());
        Ui.modernPanel(graphics, content.x(), content.y(), content.width(), content.height());
        Ui.modernPanel(graphics, fields.x(), fields.y(), fields.width(), fields.height());
        Ui.modernPanel(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height());
        Ui.modernPanel(graphics, actions.x(), actions.y(), actions.width(), actions.height());

        if (compactFields) {
            renderCompactFields(graphics, fields, mouseX, mouseY);
        } else {
            renderFieldLabels(graphics, fields);
        }
        HouseAdminLayout.Rect tierHeading = at(layout.tierHeading());
        if (tierHeading.height() >= 12) {
            Ui.sectionHeading(graphics, text("rotasutils.house.settings.tiers_heading", "TIERS"),
                    tierHeading.x() + 6, tierHeading.y(), Math.max(1, tierHeading.width() - 12));
        }
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        super.renderFrame(graphics);
        if (draft == null) {
            return;
        }
        String changed = format("rotasutils.house.settings.changed", "Changed: {0}", draft.changedFieldsSummary());
        Ui.label(graphics, Ui.truncate(changed, Math.max(1, guiWidth - Ui.PAD * 2)),
                guiLeft + Ui.PAD, guiTop + 31, draft.hasChanges() ? Ui.ACCENT : Ui.TEXT_MUTED);
        String localError = validationMessage.isEmpty() ? firstLocalError(draft) : validationMessage;
        if (!localError.isEmpty()) {
            Ui.label(graphics, Ui.truncate(localError, Math.max(1, guiWidth - Ui.PAD * 2)),
                    guiLeft + Ui.PAD, guiTop + 42, Ui.WARN);
        } else if (submitted) {
            Ui.label(graphics, text("rotasutils.house.settings.request_sent", "Request sent to the server."),
                    guiLeft + Ui.PAD, guiTop + 42, Ui.TEXT_MUTED);
        }
    }

    private void renderFieldLabels(GuiGraphics graphics, HouseAdminLayout.Rect fields) {
        if (layout == null || fields.height() < 12) {
            return;
        }
        List<HouseAdminLayout.Rect> bands = layout.sectionBands();
        if (bands.size() >= 3) {
            Ui.sectionHeading(graphics, text("rotasutils.house.settings.currency_heading", "CURRENCY"),
                    bands.get(0).x(), bands.get(0).y(), bands.get(0).width());
            Ui.sectionHeading(graphics, text("rotasutils.house.settings.billing_heading", "BILLING"),
                    bands.get(1).x(), bands.get(1).y(), bands.get(1).width());
            Ui.sectionHeading(graphics, text("rotasutils.house.settings.membership_heading", "MEMBERSHIP"),
                    bands.get(2).x(), bands.get(2).y(), bands.get(2).width());
        }
    }

    private HouseAdminLayout.Rect at(HouseAdminLayout.Rect rect) {
        return new HouseAdminLayout.Rect(guiLeft + rect.x(), guiTop + rect.y(), rect.width(), rect.height());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String text(String key, String fallback) {
        String value = L.t(key);
        return value.equals(key) ? fallback : value;
    }

    private static String format(String key, String fallback, Object... args) {
        String value = text(key, fallback);
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value;
    }

    private record CompactEntry(String hintKey, int maxLength, Supplier<String> getter,
                                Consumer<String> setter) {
    }

    private record CompactRow(String labelKey, List<CompactEntry> entries) {
        private CompactRow {
            entries = List.copyOf(entries);
        }
    }
}
