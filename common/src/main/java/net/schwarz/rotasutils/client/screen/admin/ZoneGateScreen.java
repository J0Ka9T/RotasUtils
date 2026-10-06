package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.core.ZoneDef;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ZoneGateScreen extends RotasScreen {
    private static final int ROW = 24;

    private final String zoneId;
    private final List<Requirement> requirements;
    private ScrollPanel list;

    public ZoneGateScreen(String zoneId, List<Requirement> requirements, Screen parent) {
        super("Entry lock", parent);
        this.zoneId = zoneId;
        this.requirements = requirements;
    }

    @Override
    protected int maxGuiWidth() {
        return 560;
    }

    @Override
    protected int maxGuiHeight() {
        return 400;
    }

    @Override
    protected void buildContent() {
        int listTop = guiTop + 56;
        int footerY = guiTop + guiHeight - 28;
        int buttonY = footerY - 26;
        addRenderableWidget(Ui.primaryButton(Ui.text("+ Add a requirement"), button -> {
            if (requirements.size() >= ZoneDef.MAX_ENTRY_REQUIREMENTS) {
                ClientState.feedback(false, net.schwarz.rotasutils.util.ThaiText.phrase("Entry lock is full."));
                return;
            }
            requirements.add(new Requirement(RequirementType.QUEST_COMPLETED));
            Sfx.select();
            rebuild();
        }).bounds(guiLeft + 8, buttonY, 220, 22).build());
        if (ClientState.zoneGateTestAvailable()) {
            addRenderableWidget(Ui.button(Ui.text(ClientState.zoneGateTesting()
                    ? "Test as player: ON" : "Test as player: OFF"), button -> {
                send("zone_gate_test_toggle");
                Sfx.select();
            }).bounds(guiLeft + 234, buttonY, guiWidth - 242, 22).build());
        }
        addBackButton(guiLeft + 8, 90);

        list = new ScrollPanel(guiLeft + 8, listTop, guiWidth - 16, Math.max(ROW, buttonY - 6 - listTop), ROW)
                .rowHitInsets(0, 2);
        registerPanel(list);
        list.setRows(requirements.size(), this::renderRow, this::clickRow);
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

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        Requirement requirement = requirements.get(index);
        int usable = rowWidth - 6;
        Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, false);
        int room = usable - 100;
        String head = Ui.truncate((index + 1) + ". " + requirement.type().display(), room);
        Ui.label(graphics, head, x + 8, y + 5, Ui.TEXT);
        String summary = requirement.summary();
        int used = font.width(head) + 10;
        if (!summary.isEmpty() && room - used > 24) {
            Ui.label(graphics, Ui.truncate(summary, room - used), x + 8 + used, y + 5, Ui.TEXT_DIM);
        }
        Ui.labelRight(graphics, requirement.recommendationOnly() ? "advisory" : "blocking",
                x + usable - 6, y + 5, requirement.recommendationOnly() ? Ui.TEXT_MUTED : Ui.WARN);
    }

    private void clickRow(int index, int button) {
        openRequirement(index);
    }

    private void openRequirement(int index) {
        Requirement requirement = requirements.get(index);
        minecraft.setScreen(new TypedEntryScreen<>(
                "Entry requirement",
                this,
                RequirementType.VALUES,
                RequirementType::display,
                requirement.type(),
                requirement::setType,
                () -> new ParamEditorScreen("Requirement fields", requirement.params(),
                        requirement.type().specs(), this, this::rebuild),
                () -> requirement.setRecommendationOnly(!requirement.recommendationOnly()),
                () -> requirement.recommendationOnly() ? "Advisory only: Yes" : "Advisory only: No",
                () -> {
                    requirements.remove(index);
                    rebuild();
                }));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Players cannot enter zone " + zoneId + " until these pass.",
                guiLeft + 8, guiTop + 30, Ui.TEXT_MUTED);
        if (ClientState.zoneGateTestAvailable()) {
            Ui.label(graphics, "Save the zone, then enable testing to apply its lock to yourself.",
                    guiLeft + 8, guiTop + 42, Ui.TEXT_MUTED);
        }
        if (requirements.isEmpty()) {
            Ui.wrapped(graphics, "No rules, so this zone is open to everyone. Add a requirement to lock it - "
                    + "for example a Quest Completed rule for the main quest that unlocks the area.",
                    guiLeft + 8, guiTop + 56, guiWidth - 16, Ui.TEXT_DIM);
        }
    }
}
