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
import net.schwarz.rotasutils.house.HouseDefinition;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class HouseEditScreen extends RotasScreen {
    private final String houseId;
    private String houseName = "";
    private String tierId = "";
    private boolean enabled = true;
    private long definitionRevision;
    private boolean initialized;
    private boolean submitted;
    private String validationMessage = "";

    public HouseEditScreen(String houseId, Screen parent) {
        super(L.t("rotasutils.house.edit.title"), parent);
        if (houseId == null || houseId.isBlank()) {
            throw new IllegalArgumentException("House id is required");
        }
        this.houseId = houseId;
    }

    public HouseEditScreen(HouseScreenActions.EditorContext context, Screen parent) {
        this(context.houseId(), parent);
        HouseAdminPresentation.EditDraft draft = context.draft();
        houseName = draft.name();
        tierId = draft.tier();
        enabled = draft.enabled();
        definitionRevision = draft.definitionRevision();
        initialized = true;
    }

    public HouseEditScreen(String houseId) {
        this(houseId, null);
    }

    public String houseId() {
        return houseId;
    }

    public HouseScreenActions.EditorContext editorContext() {
        return HouseScreenActions.editorContext(houseId,
                new HouseAdminPresentation.EditDraft(houseId, houseName, tierId, enabled, definitionRevision));
    }

    @Override
    protected int maxGuiWidth() {
        return 560;
    }

    @Override
    protected int maxGuiHeight() {
        return 360;
    }

    private int columnWidth() {
        return (guiWidth - Ui.PAD * 2 - Ui.GAP) / 2;
    }

    private int rightX() {
        return guiLeft + Ui.PAD + columnWidth() + Ui.GAP;
    }

    @Override
    protected void buildContent() {
        setHeader(format("rotasutils.house.edit.header", "Edit House: {0}", houseNameOrId()));
        ClientHouseAdminState state = ClientState.houseAdmin();
        HouseDefinition definition = state == null ? null : state.definition(houseId);
        if (!initialized) {
            if (definition != null) {
                HouseAdminPresentation.EditDraft draft = HouseAdminPresentation.editDraft(state, houseId);
                houseName = draft.name();
                tierId = draft.tier();
                enabled = draft.enabled();
                definitionRevision = draft.definitionRevision();
            } else {
                validationMessage = text("rotasutils.house.edit.not_found", "This house is no longer in the server snapshot.");
            }
            initialized = true;
        } else if (definition != null) {
            definitionRevision = definition.revision();
        }
        if (tierId.isEmpty() && state != null && !state.tiers().isEmpty()) {
            tierId = state.tiers().get(0).id();
        }

        int x = guiLeft + Ui.PAD;
        int w = columnWidth();
        int rx = rightX();
        int y = guiTop + 44;

        EditBox name = new EditBox(font, x, y, w, 20, L.c("rotasutils.house.edit.name_hint"));
        name.setMaxLength(96);
        name.setValue(houseName);
        name.setResponder(value -> houseName = value);
        addRenderableWidget(name);
        RotasButton tier = addRenderableWidget(Ui.button(Ui.text(tierLabel()), button -> cycleTier(state))
                .bounds(rx, y, w, 20).build());
        tier.active = state != null && state.tiers().size() > 1;

        int y2 = y + 44;
        HouseAdminPresentation.OverviewRow row = overviewRow(state);
        boolean occupied = row != null && !HouseScreenActions.canDisableOrRemove(row);
        RotasButton enabledButton = addRenderableWidget(Ui.button(Ui.text(enabled
                ? text("rotasutils.house.edit.enabled_on", "Protection: ON")
                : text("rotasutils.house.edit.enabled_off", "Protection: OFF")), button -> {
            if (occupied && enabled) {
                Sfx.error();
                return;
            }
            enabled = !enabled;
            Sfx.toggle(enabled);
            rebuild();
        }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(occupied
                        ? text("rotasutils.house.edit.occupied_reason", "House is occupied")
                        : text("rotasutils.house.edit.protection_hint", "Controls protection for unoccupied houses."))))
                .bounds(x, y2, w, 20).build());
        enabledButton.active = !occupied || !enabled;
        addRenderableWidget(Ui.button(L.c("rotasutils.house.edit.own_settings"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("house", houseId);
            send("house_settings_open", payload);
        }).bounds(rx, y2, w, 20).build());

        int y4 = y2 + 110;
        boolean hasSelection = state != null && HouseAdminPresentation.selection(state).complete();
        RotasButton replace = addRenderableWidget(Ui.button(L.c("rotasutils.house.edit.replace_bounds"),
                        button -> replaceBounds(state))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(L.c("rotasutils.house.edit.replace_hint")))
                .bounds(x, y4, w, 20).build());
        replace.active = hasSelection && !submitted;
        RotasButton remove = addRenderableWidget(Ui.dangerButton(L.c("rotasutils.house.edit.remove"), button -> openRemoval(row))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(occupied
                        ? text("rotasutils.house.edit.occupied_reason", "House is occupied")
                        : text("rotasutils.house.edit.remove_hint", "Removes the definition; world blocks and items remain."))))
                .bounds(rx, y4, w, 20).build());
        remove.active = row != null && !occupied && !submitted;

        int footer = guiTop + guiHeight - 28;
        RotasButton save = addRenderableWidget(Ui.primaryButton(L.c("rotasutils.common.save"), button -> save(state, row))
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footer, 110, 22).build());
        save.active = locallyValid(state) && !submitted;
        addRenderableWidget(Ui.button(L.c("rotasutils.common.cancel"), button -> goBack())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110 - Ui.GAP - 110, footer, 110, 22).build());
    }

    private String houseNameOrId() {
        return houseName.isBlank() ? houseId : houseName;
    }

    private String tierLabel() {
        return format("rotasutils.house.edit.tier_value", "Tier: {0}", tierId);
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

    private HouseAdminPresentation.OverviewRow overviewRow(ClientHouseAdminState state) {
        if (state == null) {
            return null;
        }
        return HouseAdminPresentation.overviewRows(state, "").stream()
                .filter(row -> row.id().equals(houseId)).findFirst().orElse(null);
    }

    private boolean locallyValid(ClientHouseAdminState state) {
        if (state == null || houseName.isBlank() || houseName.length() > 96
                || state.tiers().stream().noneMatch(tier -> tier.id().equals(tierId))) {
            validationMessage = text("rotasutils.house.edit.validation", "Enter a valid name and tier.");
            return false;
        }
        validationMessage = "";
        return true;
    }

    private void save(ClientHouseAdminState state, HouseAdminPresentation.OverviewRow row) {
        if (!locallyValid(state) || submitted || (enabled != (row == null || row.enabled())
                && enabled == false && row != null && !HouseScreenActions.canDisableOrRemove(row))) {
            Sfx.error();
            rebuild();
            return;
        }
        try {
            CompoundTag payload = HouseScreenActions.editPayload(houseId, houseName.trim(), tierId,
                    enabled, definitionRevision);
            send("house_edit", payload);
            submitted = true;
            Sfx.save();
            rebuild();
        } catch (IllegalArgumentException invalid) {
            validationMessage = text("rotasutils.house.edit.invalid_request",
                    "The server rejected these house fields.");
            Sfx.error();
            rebuild();
        }
    }

    private void replaceBounds(ClientHouseAdminState state) {
        if (state == null || !HouseAdminPresentation.selection(state).complete() || submitted) {
            Sfx.error();
            return;
        }
        try {
            send("house_replace_bounds", HouseScreenActions.replaceBoundsPayload(houseId, definitionRevision));
            submitted = true;
            Sfx.save();
            rebuild();
        } catch (IllegalArgumentException invalid) {
            validationMessage = text("rotasutils.house.edit.invalid_request",
                    "The server rejected these house fields.");
            Sfx.error();
            rebuild();
        }
    }

    private void openRemoval(HouseAdminPresentation.OverviewRow row) {
        if (row == null || !HouseScreenActions.canDisableOrRemove(row)) {
            validationMessage = text("rotasutils.house.edit.occupied_reason", "House is occupied");
            Sfx.error();
            rebuild();
            return;
        }
        Sfx.remove();
        minecraft.setScreen(new HouseRemoveConfirmScreen(this));
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        ClientHouseAdminState state = ClientState.houseAdmin();
        if (state != null && state.definition(houseId) == null) {
            if (parentScreen() != null) {
                minecraft.setScreen(parentScreen());
            }
            return;
        }
        submitted = false;
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int rx = rightX();
        int y = guiTop + 44;
        Ui.label(graphics, text("rotasutils.house.edit.name", "DISPLAY NAME"), x, y - 11, Ui.TEXT_MUTED);
        Ui.label(graphics, text("rotasutils.house.edit.tier", "TIER"), rx, y - 11, Ui.TEXT_MUTED);
        int y2 = y + 44;
        Ui.label(graphics, text("rotasutils.house.edit.enabled", "PROTECTION"), x, y2 - 11, Ui.TEXT_MUTED);
        Ui.label(graphics, text("rotasutils.house.edit.own_heading", "THIS HOUSE"), rx, y2 - 11, Ui.TEXT_MUTED);

        int cardY = y2 + 30;
        int cardW = guiWidth - Ui.PAD * 2;
        Ui.modernPanel(graphics, x, cardY, cardW, 68);
        HouseAdminPresentation.OverviewRow row = overviewRow(ClientState.houseAdmin());
        if (row == null) {
            Ui.wrapped(graphics, text("rotasutils.house.edit.not_found", "This house is no longer in the server snapshot."),
                    x + 8, cardY + 8, cardW - 16, Ui.WARN);
        } else {
            Ui.label(graphics, Ui.truncate(format("rotasutils.house.edit.definition_summary", "{0}  -  {1}  -  tier {2}",
                    row.dimension(), row.size(), row.tier()), cardW - 120), x + 8, cardY + 8, Ui.TEXT);
            String status = statusText(row);
            Ui.tag(graphics, x + cardW - Ui.textWidth(status) - 18, cardY + 6, status, statusColor(row));
            String members = row.memberCount() == 0
                    ? text("rotasutils.house.edit.no_occupants", "No occupants")
                    : text("rotasutils.house.edit.occupants", "{0} occupant(s)").replace("{0}", Integer.toString(row.memberCount()));
            Ui.label(graphics, members, x + 8, cardY + 26, Ui.TEXT_MUTED);
            Ui.label(graphics, Ui.truncate(text("rotasutils.house.edit.wand_tip",
                    "Wand: sneak + right-click air in the house loads its area; sneak + right-click a block moves a wall."),
                    cardW - 16), x + 8, cardY + 46, Ui.TEXT_MUTED);
        }

        String message = !validationMessage.isEmpty() ? validationMessage
                : submitted ? text("rotasutils.house.edit.submitted", "Request sent to the server.") : "";
        if (!message.isEmpty()) {
            Ui.label(graphics, Ui.truncate(message, guiWidth - Ui.PAD * 2 - 240), x, guiTop + guiHeight - 22,
                    validationMessage.isEmpty() ? Ui.TEXT_MUTED : Ui.WARN);
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
        return text("rotasutils.house.status." + row.tag().toLowerCase(java.util.Locale.ROOT), row.tag());
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
