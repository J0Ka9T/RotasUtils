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
import net.schwarz.rotasutils.house.HouseArea;
import net.schwarz.rotasutils.house.HouseNaming;
import net.schwarz.rotasutils.house.HouseTier;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class HouseCreateScreen extends RotasScreen {
    private static final int STEP_GAP = 8;

    private String houseId = "";
    private String houseName = "";
    private String tierId = "";
    private long configRevision;
    private boolean initialized;
    private boolean idEdited;
    private boolean showId;
    private boolean submitted;
    private String validationMessage = "";
    private String lastCreated = "";
    private RotasButton submitButton;

    private int areaY, nameY, tierY, stepsX, stepsWidth, tierRows, tierColumns, tierCardWidth;

    public HouseCreateScreen(Screen parent) {
        super(L.t("rotasutils.house.create.title"), parent);
    }

    public HouseCreateScreen() {
        this(null);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 640);
        guiHeight = Ui.fill(height, 430);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader(text("rotasutils.house.create.title", "Create House"));

        ClientHouseAdminState state = ClientState.houseAdmin();
        initialise(state);

        stepsX = guiLeft + 12;
        stepsWidth = guiWidth - 24;
        int y = guiTop + 32;

        areaY = y;
        int buttonsY = areaY + 30;
        int quarter = (stepsWidth - 16 - Ui.GAP * 3) / 4;
        for (int i = 0; i < Math.min(4, HouseArea.SIZES.length); i++) {
            final int index = i;
            addRenderableWidget(Ui.button(Ui.text(Ui.truncate(HouseArea.SIZES[i].text(), quarter - 6)), b -> area("around", index))
                    .bounds(stepsX + 8 + (quarter + Ui.GAP) * i, buttonsY, quarter, 18).build());
        }
        int third = (stepsWidth - 16 - Ui.GAP * 2) / 3;
        addRenderableWidget(Ui.button(Ui.text(text("rotasutils.house.area.corner1_look", "Corner 1 - looking")), b -> area("corner1_look", 0))
                .bounds(stepsX + 8, buttonsY + 22, third, 18).build());
        addRenderableWidget(Ui.button(Ui.text(text("rotasutils.house.area.corner2_look", "Corner 2 - looking")), b -> area("corner2_look", 0))
                .bounds(stepsX + 8 + third + Ui.GAP, buttonsY + 22, third, 18).build());
        addRenderableWidget(Ui.button(Ui.text(text("rotasutils.house.create.more_area", "More area tools...")),
                        b -> minecraft.setScreen(new HouseAreaScreen(this)))
                .bounds(stepsX + 8 + (third + Ui.GAP) * 2, buttonsY + 22, stepsWidth - 16 - (third + Ui.GAP) * 2, 18).build());
        y = buttonsY + 46 + STEP_GAP;

        nameY = y;
        int nameFieldWidth = stepsWidth - 16 - 96;
        EditBox name = new EditBox(font, stepsX + 12, nameY + 31, nameFieldWidth - 8, 14,
                L.c("rotasutils.house.create.name_hint"));
        name.setBordered(false);
        name.setMaxLength(96);
        name.setValue(houseName);
        name.setResponder(value -> {
            houseName = value;
            if (!idEdited) {
                houseId = value.isBlank() ? "" : HouseNaming.uniqueId(value, takenIds(ClientState.houseAdmin()));
            }
            refreshSubmit();
        });
        addRenderableWidget(name);
        addRenderableWidget(Ui.button(Ui.text(showId ? "Hide id" : "Edit id"), b -> {
            showId = !showId;
            rebuild();
        }).bounds(stepsX + 8 + nameFieldWidth + Ui.GAP, nameY + 27, 96 - Ui.GAP, 18).build());
        y = nameY + 52;
        if (showId) {
            EditBox id = new EditBox(font, stepsX + 12, y + 4, nameFieldWidth - 8, 14, L.c("rotasutils.house.create.id_hint"));
            id.setBordered(false);
            id.setMaxLength(64);
            id.setValue(houseId);
            id.setResponder(value -> {
                houseId = value;
                idEdited = true;
                refreshSubmit();
            });
            addRenderableWidget(id);
            y += 24;
        }
        y += STEP_GAP;

        tierY = y;
        List<HouseTier> tiers = state == null ? List.of() : state.tiers();
        tierColumns = Math.max(1, Math.min(4, (stepsWidth - 16) / 130));
        tierCardWidth = (stepsWidth - 16 - (tierColumns - 1) * Ui.GAP) / tierColumns;
        tierRows = Math.max(1, (tiers.size() + tierColumns - 1) / tierColumns);
        for (int i = 0; i < tiers.size(); i++) {
            HouseTier tier = tiers.get(i);
            int cx = stepsX + 8 + (i % tierColumns) * (tierCardWidth + Ui.GAP);
            int cy = tierY + 28 + (i / tierColumns) * 34;
            boolean selected = tier.id().equals(tierId);
            addRenderableWidget(Ui.button(Ui.text((selected ? "> " : "") + tier.id()), b -> {
                tierId = tier.id();
                Sfx.select();
                rebuild();
            }).style(selected ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                    .bounds(cx, cy, tierCardWidth, 30).build());
        }

        int footer = guiTop + guiHeight - 28;
        addRenderableWidget(Ui.button(L.c("rotasutils.common.cancel"), b -> goBack())
                .bounds(guiLeft + 12, footer, 90, 22).build());
        submitButton = addRenderableWidget(Ui.primaryButton(L.c("rotasutils.house.create.submit"), b -> submit(state))
                .bounds(guiLeft + guiWidth - 172, footer, 160, 22).build());
        refreshSubmit();
    }

    private void initialise(ClientHouseAdminState state) {
        if (!initialized) {
            if (state != null) {
                HouseAdminPresentation.CreateDraft draft = HouseAdminPresentation.createDraft(state);
                houseId = draft.id();
                houseName = draft.name();
                tierId = draft.tier();
                configRevision = draft.configRevision();
                var last = state.definitions().stream().max(java.util.Comparator.comparingLong(
                        net.schwarz.rotasutils.house.HouseDefinition::revision)).orElse(null);
                if (last != null) {
                    houseName = HouseNaming.next(last.name());
                    if (state.tiers().stream().anyMatch(t -> t.id().equals(last.tier()))) {
                        tierId = last.tier();
                    }
                    houseId = houseName.isBlank() ? "" : HouseNaming.uniqueId(houseName, takenIds(state));
                }
            }
            initialized = true;
        } else if (state != null) {
            configRevision = state.configRevision();
        }
        if (tierId.isEmpty() && state != null && !state.tiers().isEmpty()) {
            tierId = state.tiers().get(0).id();
        }
    }

    private void area(String op, int size) {
        CompoundTag payload = new CompoundTag();
        payload.putString("op", op);
        payload.putInt("size", size);
        send("house_area", payload);
        Sfx.select();
    }

    private static java.util.Set<String> takenIds(ClientHouseAdminState state) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        if (state != null) {
            state.definitions().forEach(d -> taken.add(d.id()));
        }
        return taken;
    }

    private void refreshSubmit() {
        if (submitButton != null) {
            submitButton.active = locallyValid(ClientState.houseAdmin()) && !submitted;
        }
    }

    private boolean areaDone(ClientHouseAdminState state) {
        if (state == null) return false;
        var selection = HouseAdminPresentation.selection(state);
        return selection != null && selection.complete();
    }

    private boolean locallyValid(ClientHouseAdminState state) {
        if (state == null) {
            validationMessage = text("rotasutils.house.create.no_snapshot", "Waiting for the server housing snapshot.");
            return false;
        }
        if (!areaDone(state)) {
            validationMessage = "Step 1: choose the area.";
            return false;
        }
        if (houseName.isBlank() || houseName.length() > 96) {
            validationMessage = "Step 2: give the house a name.";
            return false;
        }
        if (!houseId.matches("[a-z0-9_.-]{1,64}")) {
            validationMessage = "The id may only use a-z, 0-9, _ . - (Edit id to fix).";
            return false;
        }
        if (state.definition(houseId) != null) {
            validationMessage = format("rotasutils.house.create.id_taken", "A house with the id {0} already exists.", houseId);
            return false;
        }
        if (state.tiers().stream().noneMatch(tier -> tier.id().equals(tierId))) {
            validationMessage = state.tiers().isEmpty() ? "No tiers exist yet: add one in House Settings." : "Step 3: pick a tier.";
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
                    configRevision, true);
            send("house_create", payload);
            submitted = true;
            Sfx.save();
            rebuild();
        } catch (IllegalArgumentException invalid) {
            validationMessage = text("rotasutils.house.create.invalid_request", "The server rejected these house fields.");
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
        if (submitted && state != null && state.definition(houseId.trim()) != null) {
            lastCreated = houseName;
            houseName = HouseNaming.next(houseName);
            idEdited = false;
            houseId = houseName.isBlank() ? "" : HouseNaming.uniqueId(houseName, takenIds(state));
        }
        submitted = false;
        rebuild();
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ClientHouseAdminState state = ClientState.houseAdmin();

        boolean areaDone = areaDone(state);
        step(graphics, areaY, 74, 1, "Area", areaDone);
        String areaText;
        if (state == null) {
            areaText = text("rotasutils.house.create.no_snapshot", "Waiting for the server housing snapshot.");
        } else {
            HouseAdminPresentation.SelectionSummary summary = HouseAdminPresentation.selection(state);
            areaText = summary == null ? "Nothing chosen: pick a size round you, or set two corners." : selectionText(summary);
        }
        Ui.label(graphics, Ui.truncate(areaText, stepsWidth - 130), stepsX + 110, areaY + 8, areaDone ? Ui.GOOD : Ui.TEXT_MUTED);

        step(graphics, nameY, showId ? 76 : 52, 2, "Name", !houseName.isBlank());
        Ui.searchFrame(graphics, stepsX + 8, nameY + 27, stepsWidth - 16 - 96, 18, false);
        Ui.label(graphics, Ui.truncate("id: " + (houseId.isEmpty() ? "-" : houseId) + (idEdited ? "" : "  (auto)"), stepsWidth - 130),
                stepsX + 110, nameY + 8, Ui.TEXT_MUTED);
        if (showId) {
            Ui.searchFrame(graphics, stepsX + 8, nameY + 52, stepsWidth - 16 - 96, 18, false);
        }

        List<HouseTier> tiers = state == null ? List.of() : state.tiers();
        boolean tierDone = tiers.stream().anyMatch(t -> t.id().equals(tierId));
        step(graphics, tierY, 32 + tierRows * 34, 3, "Tier", tierDone);
        if (tiers.isEmpty()) {
            Ui.label(graphics, "No tiers yet: add one in House Settings.", stepsX + 12, tierY + 32, Ui.WARN);
        }
        for (int i = 0; i < tiers.size(); i++) {
            HouseTier tier = tiers.get(i);
            int cx = stepsX + 8 + (i % tierColumns) * (tierCardWidth + Ui.GAP);
            int cy = tierY + 28 + (i / tierColumns) * 34;
            Ui.label(graphics, Ui.truncate(tier.deposit() + " deposit  ·  " + tier.maintenance() + " rent", tierCardWidth - 10),
                    cx + 6, cy + 19, Ui.TEXT_MUTED);
        }

        int statusY = guiTop + guiHeight - 44;
        String status = submitted ? text("rotasutils.house.create.submitted", "Creating...")
                : !validationMessage.isEmpty() ? validationMessage
                : !lastCreated.isEmpty() ? "Created \"" + lastCreated + "\". Ready for the next one."
                : "Ready: press Create.";
        int color = submitted ? Ui.TEXT_MUTED : !validationMessage.isEmpty() ? Ui.WARN : Ui.GOOD;
        Ui.labelRight(graphics, Ui.truncate(status, guiWidth - 40), guiLeft + guiWidth - 12, statusY, color);
    }

    private void step(GuiGraphics graphics, int y, int height, int number, String title, boolean done) {
        Ui.modernPanel(graphics, stepsX, y, stepsWidth, height);
        int badge = done ? Ui.GOOD : Ui.ACCENT;
        Ui.roundedRect(graphics, stepsX + 8, y + 6, 14, 14, 7, badge);
        Ui.labelCentered(graphics, done ? "v" : String.valueOf(number), stepsX + 15, y + 9, 0xFF1A140E);
        Ui.label(graphics, title, stepsX + 28, y + 9, Ui.TEXT_BRIGHT);
    }

    private static String text(String key, String fallback) {
        String value = L.t(key);
        return value.equals(key) ? fallback : value;
    }

    private static String selectionText(HouseAdminPresentation.SelectionSummary summary) {
        return switch (summary.state()) {
            case MISSING -> text("rotasutils.house.selection.missing",
                    "Nothing chosen: pick a size round you, or set two corners.");
            case PARTIAL -> text("rotasutils.house.selection.partial",
                    "One corner set. Set the second corner.");
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
