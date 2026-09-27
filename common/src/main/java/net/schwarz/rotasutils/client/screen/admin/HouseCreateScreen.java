package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.List;

/** Form for creating a house from the administrator's server-held wand selection. */
@Environment(EnvType.CLIENT)
public final class HouseCreateScreen extends RotasScreen {
    private String houseId = "";
    private String houseName = "";
    private String tierId = "";
    private long configRevision;
    private boolean initialized;
    private boolean submitted;
    private String validationMessage = "";
    private HouseAdminLayout.Form layout;

    public HouseCreateScreen(Screen parent) {
        super(L.t("rotasutils.house.create.title"), parent);
    }

    public HouseCreateScreen() {
        this(null);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 720);
        guiHeight = Ui.fill(height, 440);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader(text("rotasutils.house.create.title", "Create House"));
        layout = HouseAdminLayout.houseCreate(guiWidth, guiHeight);

        ClientHouseAdminState state = ClientState.houseAdmin();
        if (!initialized) {
            if (state != null) {
                HouseAdminPresentation.CreateDraft draft = HouseAdminPresentation.createDraft(state);
                houseId = draft.id();
                houseName = draft.name();
                tierId = draft.tier();
                configRevision = draft.configRevision();
            }
            initialized = true;
        } else if (state != null) {
            configRevision = state.configRevision();
        }
        if (tierId.isEmpty() && state != null && !state.tiers().isEmpty()) {
            tierId = state.tiers().get(0).id();
        }

        HouseAdminLayout.Rect fields = at(layout.fields());
        FieldGeometry geometry = fieldGeometry(fields);
        EditBox id = new EditBox(font, geometry.firstX(), geometry.fieldY(), geometry.firstWidth(), geometry.controlHeight(),
                L.c("rotasutils.house.create.id_hint"));
        id.setBordered(false);
        id.setMaxLength(64);
        id.setValue(houseId);
        id.setResponder(value -> houseId = value);
        addRenderableWidget(id);

        EditBox name = new EditBox(font, geometry.nameX(), geometry.fieldY(), geometry.nameWidth(), geometry.controlHeight(),
                L.c("rotasutils.house.create.name_hint"));
        name.setBordered(false);
        name.setMaxLength(96);
        name.setValue(houseName);
        name.setResponder(value -> houseName = value);
        addRenderableWidget(name);

        // The tier picker is a regular RotasButton so it shares focus, narration,
        // sound, truncation, and hit geometry with the rest of the console.
        RotasButton tier = addRenderableWidget(Ui.button(Ui.text(tierLabel(state)), button -> cycleTier(state))
                .bounds(geometry.tierX(), geometry.tierY(), geometry.tierWidth(), geometry.tierHeight()).build());
        tier.active = state != null && state.tiers().size() > 1;

        HouseAdminLayout.Rect actions = at(layout.actions());
        boolean canSubmit = locallyValid(state);
        RotasButton submit = addRenderableWidget(Ui.primaryButton(
                L.c("rotasutils.house.create.submit"), button -> submit(state))
                .bounds(actions.x(), actions.y(), Math.max(1, actions.width()), Math.max(1, actions.height()))
                .build());
        submit.active = canSubmit && !submitted;

        HouseAdminLayout.Rect footer = at(layout.footer());
        addRenderableWidget(Ui.button(L.c("rotasutils.common.cancel"), button -> goBack())
                .bounds(footer.x(), footer.y(), Math.max(1, footer.width()), footer.height()).build());
    }

    private String tierLabel(ClientHouseAdminState state) {
        String label = tierId.isEmpty() ? text("rotasutils.house.create.choose_tier", "Choose tier") : tierId;
        return format("rotasutils.house.create.tier_value", "Tier: {0}", label);
    }

    private void cycleTier(ClientHouseAdminState state) {
        if (state == null || state.tiers().isEmpty()) {
            return;
        }
        List<net.schwarz.rotasutils.house.HouseTier> tiers = state.tiers();
        int current = -1;
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(tierId)) {
                current = i;
                break;
            }
        }
        tierId = tiers.get((current + 1 + tiers.size()) % tiers.size()).id();
        Sfx.select();
        rebuild();
    }

    private boolean locallyValid(ClientHouseAdminState state) {
        if (state == null || state.tiers().isEmpty()
                || !houseId.matches("[a-z0-9_.-]{1,64}")
                || houseName.isBlank() || houseName.length() > 96
                || state.tiers().stream().noneMatch(tier -> tier.id().equals(tierId))) {
            validationMessage = state == null
                    ? text("rotasutils.house.create.no_snapshot", "Waiting for the server housing snapshot.")
                    : text("rotasutils.house.create.validation", "Enter a valid id, name, and tier.");
            return false;
        }
        HouseAdminPresentation.SelectionSummary selection = HouseAdminPresentation.selection(state);
        if (selection == null || !selection.complete()) {
            validationMessage = selection == null
                    ? text("rotasutils.house.create.no_selection", "No House Wand selection.")
                    : selectionText(selection);
            return false;
        }
        validationMessage = "";
        return true;
    }

    private void submit(ClientHouseAdminState state) {
        if (!locallyValid(state) || submitted) {
            Sfx.error();
            rebuild();
            return;
        }
        try {
            CompoundTag payload = HouseScreenActions.createPayload(houseId.trim(), houseName.trim(), tierId,
                    configRevision);
            send("house_create", payload);
            submitted = true;
            Sfx.save();
            rebuild();
        } catch (IllegalArgumentException invalid) {
            validationMessage = text("rotasutils.house.create.invalid_request",
                    "The server rejected these house fields.");
            Sfx.error();
            rebuild();
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        ClientHouseAdminState state = ClientState.houseAdmin();
        if (state != null) {
            configRevision = state.configRevision();
        }
        submitted = false;
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HouseAdminLayout.Rect fields = at(layout.fields());
        HouseAdminLayout.Rect selection = at(layout.selection());
        HouseAdminLayout.Rect selectionHeading = at(layout.selectionHeading());
        HouseAdminLayout.TextRegion selectionText = layout.selectionText();
        HouseAdminLayout.Rect validation = at(layout.validation());
        HouseAdminLayout.Rect actions = at(layout.actions());
        FieldGeometry geometry = fieldGeometry(fields);

        if (fields.height() > 0) {
            Ui.modernPanel(graphics, fields.x(), fields.y(), fields.width(), fields.height());
        }
        if (!geometry.ultraCompact()) {
            Ui.label(graphics, text("rotasutils.house.create.id", "HOUSE ID"), fields.x() + 4, fields.y() + 4, Ui.TEXT_MUTED);
            Ui.label(graphics, text("rotasutils.house.create.name", "DISPLAY NAME"),
                    geometry.nameX(), fields.y() + 4, Ui.TEXT_MUTED);
            Ui.label(graphics, text("rotasutils.house.create.tier", "TIER"), geometry.tierX(),
                    geometry.compact() ? fields.y() + 4 : fields.y() + 27, Ui.TEXT_MUTED);
        }

        if (geometry.controlHeight() > 0) {
            Ui.searchFrame(graphics, geometry.firstX(), geometry.fieldY(), geometry.firstWidth(), geometry.controlHeight(), false);
            Ui.searchFrame(graphics, geometry.nameX(), geometry.fieldY(), geometry.nameWidth(), geometry.controlHeight(), false);
        }
        if (selection.height() > 0) {
            Ui.modernPanel(graphics, selection.x(), selection.y(), selection.width(), selection.height());
        }
        if (selectionHeading.height() > 0) {
            Ui.sectionHeading(graphics, text("rotasutils.house.create.selection", "SERVER WAND SELECTION"),
                    selectionHeading.x(), selectionHeading.y(), selectionHeading.width());
        }

        ClientHouseAdminState state = ClientState.houseAdmin();
        if (layout.selectionSummaryVisible()
                && selectionHeading.height() > 0 && selectionText.bounds().height() > 0) {
            if (state == null) {
                renderBounded(graphics, selectionText,
                        text("rotasutils.house.create.no_snapshot", "Waiting for the server housing snapshot."),
                        Ui.TEXT_DIM);
            } else {
                HouseAdminPresentation.SelectionSummary summary = HouseAdminPresentation.selection(state);
                renderBounded(graphics, selectionText, selectionText(summary),
                        summary.complete() ? Ui.TEXT : Ui.WARN);
                if (summary.complete() && !geometry.compact()
                        && selectionText.bounds().height() >= 20) {
                    Ui.label(graphics, format("rotasutils.house.create.selection_corners",
                                    "First: {0}  Second: {1}", summary.first(), summary.second()),
                            selection.x() + 8, selection.bottom() - 18, Ui.TEXT_MUTED);
                }
            }
        }
        if (validation.height() > 0) {
            Ui.modernPanel(graphics, validation.x(), validation.y(), validation.width(), validation.height());
            if (!validationMessage.isEmpty()) {
                renderValidation(graphics, validation, validationMessage, Ui.WARN);
            } else if (submitted) {
                renderValidation(graphics, validation,
                        text("rotasutils.house.create.submitted", "Request sent to the server."), Ui.TEXT_MUTED);
            }
        }
        if (actions.height() > 0) {
            Ui.modernPanel(graphics, actions.x(), actions.y(), actions.width(), actions.height());
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
            HouseAdminLayout.Rect line = at(region.line(i));
            Ui.label(graphics, Ui.truncate(lines.get(i), Math.max(1, line.width())),
                    line.x(), line.y(), color);
        }
    }

    private HouseAdminLayout.Rect at(HouseAdminLayout.Rect rect) {
        return new HouseAdminLayout.Rect(guiLeft + rect.x(), guiTop + rect.y(), rect.width(), rect.height());
    }

    private static FieldGeometry fieldGeometry(HouseAdminLayout.Rect fields) {
        int innerWidth = Math.max(3, fields.width() - 8);
        int gap = Math.max(2, Math.min(Ui.GAP, innerWidth / 24));
        int firstX = fields.x() + 4;
        boolean compact = fields.height() < 58;
        boolean ultraCompact = fields.height() < 34;
        int controlHeight = Math.max(1, Math.min(18, fields.height()));
        int fieldY = ultraCompact ? fields.y() + Math.max(0, (fields.height() - controlHeight) / 2) : fields.y() + 15;
        if (compact) {
            int columnWidth = Math.max(1, (innerWidth - gap * 2) / 3);
            int nameX = firstX + columnWidth + gap;
            int tierX = nameX + columnWidth + gap;
            return new FieldGeometry(firstX, columnWidth, nameX, columnWidth, tierX,
                    Math.max(1, innerWidth - columnWidth * 2 - gap * 2), fieldY, fieldY, controlHeight,
                    controlHeight, true, ultraCompact);
        }
        int firstWidth = Math.max(1, (innerWidth - gap) / 3);
        return new FieldGeometry(firstX, firstWidth, firstX + firstWidth + gap,
                Math.max(1, innerWidth - firstWidth - gap), firstX, innerWidth,
                fieldY, fields.y() + 37, 18, 20, false, false);
    }

    private record FieldGeometry(int firstX, int firstWidth, int nameX, int nameWidth,
                                 int tierX, int tierWidth, int fieldY, int tierY,
                                 int controlHeight, int tierHeight, boolean compact, boolean ultraCompact) {
    }

    private static void renderValidation(GuiGraphics graphics, HouseAdminLayout.Rect validation,
                                         String message, int color) {
        int width = Math.max(20, validation.width() - 16);
        if (validation.height() < 12) {
            Ui.label(graphics, Ui.truncate(message, width), validation.x() + 8, validation.y() + 1, color);
        } else {
            Ui.wrapped(graphics, message, validation.x() + 8, validation.y() + 2, width, color);
        }
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
