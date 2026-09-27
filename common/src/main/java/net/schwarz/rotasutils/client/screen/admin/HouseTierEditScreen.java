package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.house.HouseAdminValidator;

import java.util.List;

/** Local tier editor. It never sends a request; the parent settings screen owns Save. */
@Environment(EnvType.CLIENT)
public final class HouseTierEditScreen extends RotasScreen {
    private final HouseSettingsScreen parentSettings;
    private final HouseConfigDraft draft;
    private final int tierIndex;
    private HouseAdminLayout.TierEditor layout;
    private String validationMessage = "";

    public HouseTierEditScreen(HouseSettingsScreen parentSettings, HouseConfigDraft draft, int tierIndex) {
        super(L.t(isNew(draft, tierIndex) ? "rotasutils.house.tier.new_title" : "rotasutils.house.tier.title"),
                parentSettings);
        this.parentSettings = parentSettings;
        this.draft = requireDraft(draft);
        this.tierIndex = tierIndex;
    }

    public HouseTierEditScreen(Screen parent, HouseConfigDraft draft, int tierIndex) {
        super(L.t(isNew(draft, tierIndex) ? "rotasutils.house.tier.new_title" : "rotasutils.house.tier.title"), parent);
        this.parentSettings = parent instanceof HouseSettingsScreen settings ? settings : null;
        this.draft = requireDraft(draft);
        this.tierIndex = tierIndex;
    }

    public HouseConfigDraft draft() {
        return draft;
    }

    public HouseSettingsScreen parentSettings() {
        return parentSettings;
    }

    public int tierIndex() {
        return tierIndex;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 320);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        HouseConfigDraft.TierDraft tier = tier();
        setHeader(format("rotasutils.house.tier.header", "Tier: {0}", tier == null ? "" : safe(tier.id())));
        layout = HouseAdminLayout.tierEditor(guiWidth, guiHeight);
        if (tier == null) {
            validationMessage = text("rotasutils.house.tier.invalid", "This tier is no longer in the draft.");
            buildFooter();
            return;
        }

        HouseAdminLayout.Rect fields = at(layout.fields());
        int innerX = fields.x() + 6;
        int innerWidth = Math.max(1, fields.width() - 12);
        int gap = Math.max(2, Math.min(Ui.GAP, innerWidth / 24));
        int width = Math.max(1, (innerWidth - gap * 2) / 3);
        int height = Math.max(1, Math.min(18, fields.height()));
        int y = fields.y() + (fields.height() >= 28 ? 16 : 1);

        EditBox id = field(innerX, y, width, height, tier.id(), 64,
                "rotasutils.house.tier.id_hint", tier::setId);
        id.setEditable(tier.idEditable());
        if (!tier.idEditable()) {
            id.setTooltip(Tooltip.create(Ui.text(text("rotasutils.house.tier.id_readonly",
                    "Existing tier IDs cannot change."))));
        }
        addRenderableWidget(id);
        EditBox deposit = field(innerX + width + gap, y, width, height, tier.deposit(), 20,
                "rotasutils.house.tier.deposit_hint", tier::setDeposit);
        addRenderableWidget(deposit);
        EditBox maintenance = field(innerX + (width + gap) * 2, y,
                Math.max(1, innerWidth - (width + gap) * 2), height, tier.maintenance(), 20,
                "rotasutils.house.tier.maintenance_hint", tier::setMaintenance);
        addRenderableWidget(maintenance);
        buildActions(tier);
        buildFooter();
    }

    private EditBox field(int x, int y, int width, int height, String value, int maxLength,
                          String hintKey, java.util.function.Consumer<String> responder) {
        EditBox field = new EditBox(font, x, y, Math.max(1, width), Math.max(1, height), L.c(hintKey));
        field.setBordered(false);
        field.setMaxLength(maxLength);
        field.setValue(safe(value));
        field.setTextColor(Ui.TEXT_BRIGHT);
        field.setTextColorUneditable(Ui.TEXT_MUTED);
        field.setResponder(input -> {
            responder.accept(input);
            validationMessage = "";
        });
        return field;
    }

    private void buildActions(HouseConfigDraft.TierDraft tier) {
        HouseAdminLayout.Rect actions = at(layout.actions());
        int gap = Math.min(Ui.GAP, Math.max(2, actions.width() / 24));
        int keepWidth = Math.max(1, (actions.width() - gap) / 2);
        addRenderableWidget(Ui.button(L.c("rotasutils.house.tier.apply"), button -> keepTier())
                .bounds(actions.x(), actions.y(), keepWidth, Math.max(1, actions.height())).build());
        RotasButton remove = addRenderableWidget(Ui.dangerButton(L.c("rotasutils.house.tier.remove"), button -> removeTier())
                .tooltip(Tooltip.create(Ui.text(text("rotasutils.house.tier.remove_hint",
                        "Removal stays local until settings are saved."))))
                .bounds(actions.x() + keepWidth + gap, actions.y(),
                        Math.max(1, actions.width() - keepWidth - gap), Math.max(1, actions.height())).build());
        remove.active = draft.tiers().size() > 1;
        if (draft.tiers().size() <= 1) {
            remove.setTooltip(Tooltip.create(Ui.text(text("rotasutils.house.tier.remove_last",
                    "At least one house tier must remain."))));
        }
    }

    private void buildFooter() {
        HouseAdminLayout.Rect footer = at(layout.footer());
        addRenderableWidget(Ui.button(L.c("rotasutils.house.tier.back"), button -> keepTier())
                .bounds(footer.x(), footer.y(), Math.max(1, footer.width()), Math.max(1, footer.height())).build());
    }

    private HouseConfigDraft.TierDraft tier() {
        List<HouseConfigDraft.TierDraft> current = draft.tiers();
        return tierIndex >= 0 && tierIndex < current.size() ? current.get(tierIndex) : null;
    }

    private void keepTier() {
        HouseConfigDraft.TierDraft current = tier();
        if (current == null) {
            returnToSettings();
            return;
        }
        List<HouseAdminValidator.Error> errors = draft.validationErrors();
        boolean tierError = errors.stream().anyMatch(error -> error.field().startsWith("tiers"));
        if (tierError) {
            validationMessage = errors.stream().filter(error -> error.field().startsWith("tiers"))
                    .findFirst().map(error -> error.field() + ": " + error.message())
                    .orElse(text("rotasutils.house.tier.invalid", "Fix the tier values before returning."));
            Sfx.error();
            return;
        }
        Sfx.click();
        returnToSettings();
    }

    private void removeTier() {
        HouseConfigDraft.TierDraft current = tier();
        if (current == null) {
            return;
        }
        HouseConfigDraft.TierRemoval removal = draft.removeTierResult(current.id());
        if (!removal.removed()) {
            validationMessage = removal.referencedHouses().isEmpty()
                    ? removal.reason().contains("At least")
                    ? text("rotasutils.house.tier.remove_last", "At least one house tier must remain.")
                    : text("rotasutils.house.tier.remove_missing", "Tier was not found in this draft.")
                    : format("rotasutils.house.tier.remove_referenced", "Tier is referenced by house(s): {0}",
                    String.join(", ", removal.referencedHouses()));
            Sfx.error();
            rebuild();
            return;
        }
        Sfx.remove();
        returnToSettings();
    }

    private void returnToSettings() {
        if (parentSettings != null) {
            minecraft.setScreen(parentSettings);
        } else if (parentScreen() != null) {
            minecraft.setScreen(parentScreen());
        } else {
            onClose();
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onClose() {
        if (minecraft != null && (parentSettings != null || parentScreen() != null)) {
            returnToSettings();
        } else {
            super.onClose();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layout == null) {
            return;
        }
        HouseAdminLayout.Rect content = at(layout.content());
        HouseAdminLayout.Rect fields = at(layout.fields());
        HouseAdminLayout.Rect actions = at(layout.actions());
        Ui.modernPanel(graphics, content.x(), content.y(), content.width(), content.height());
        Ui.modernPanel(graphics, fields.x(), fields.y(), fields.width(), fields.height());
        Ui.modernPanel(graphics, actions.x(), actions.y(), actions.width(), actions.height());

        HouseConfigDraft.TierDraft tier = tier();
        if (tier != null && fields.height() >= 12) {
            int innerX = fields.x() + 6;
            int innerWidth = Math.max(1, fields.width() - 12);
            int gap = Math.max(2, Math.min(Ui.GAP, innerWidth / 24));
            int width = Math.max(1, (innerWidth - gap * 2) / 3);
            Ui.sectionHeading(graphics, text("rotasutils.house.settings.tiers_heading", "TIER"),
                    innerX, fields.y() + 3, innerWidth);
            Ui.label(graphics, text("rotasutils.house.tier.id", "TIER ID"), innerX, fields.y() + 6, Ui.TEXT_MUTED);
            Ui.label(graphics, text("rotasutils.house.tier.deposit", "DEPOSIT"),
                    innerX + width + gap, fields.y() + 6, Ui.TEXT_MUTED);
            Ui.label(graphics, text("rotasutils.house.tier.maintenance", "MAINTENANCE"),
                    innerX + (width + gap) * 2, fields.y() + 6, Ui.TEXT_MUTED);
        }
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        super.renderFrame(graphics);
        if (!validationMessage.isEmpty()) {
            Ui.label(graphics, Ui.truncate(validationMessage, Math.max(1, guiWidth - Ui.PAD * 2)),
                    guiLeft + Ui.PAD, guiTop + 31, Ui.WARN);
        }
    }

    private HouseAdminLayout.Rect at(HouseAdminLayout.Rect rect) {
        return new HouseAdminLayout.Rect(guiLeft + rect.x(), guiTop + rect.y(), rect.width(), rect.height());
    }

    private static HouseConfigDraft requireDraft(HouseConfigDraft value) {
        if (value == null) {
            throw new IllegalArgumentException("House configuration draft is required");
        }
        return value;
    }

    private static boolean isNew(HouseConfigDraft value, int index) {
        return value != null && index >= 0 && index < value.tiers().size() && value.tiers().get(index).isNew();
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
}
