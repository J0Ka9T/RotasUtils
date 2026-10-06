package net.schwarz.rotasutils.client.screen.admin;

import com.schwarz.lenlorui.ui.UiColor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.server.Validation;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ValidationScreen extends RotasScreen {
    private final List<Validation.Issue> issues = new ArrayList<>();
    private ScrollPanel list;
    private boolean errorsOnly;

    public ValidationScreen(Screen parent) {
        super("Validation Report", parent);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 520);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int margin = 10;
        int footerY = guiTop + guiHeight - 34;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Back"), button -> goBack())
                .bounds(guiLeft + margin, footerY, 92, 24).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Re-run"), button -> send("validate"))
                .bounds(guiLeft + guiWidth - margin - 220, footerY, 100, 24).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(errorsOnly ? "Show all" : "Errors only"), button -> {
            errorsOnly = !errorsOnly;
            rebuild();
        }).bounds(guiLeft + guiWidth - margin - 114, footerY, 114, 24).build());

        int listY = guiTop + 52;
        list = new ScrollPanel(guiLeft + margin + 4, listY,
                guiWidth - (margin + 4) * 2,
                Math.max(38, footerY - listY - 12), 38)
                .withoutBackground()
                .rowHitInsets(0, 6);
        registerPanel(list);
        refreshRows();
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.scaledLabel(graphics, "Validation Report", guiLeft + 14, guiTop + 10,
                1.15f, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, "CONTENT HEALTH", guiLeft + guiWidth - 14, guiTop + 13, Ui.TEXT_MUTED);
        graphics.fill(guiLeft + 14, guiTop + 31, guiLeft + guiWidth - 14, guiTop + 32, 0x59BE9E68);

        int margin = 12;
        int footerY = guiTop + guiHeight - 36;
        Ui.modernPanel(graphics, guiLeft + margin, guiTop + 43,
                guiWidth - margin * 2, footerY - guiTop - 52);
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        refreshRows();
    }

    private void refreshRows() {
        issues.clear();
        for (Validation.Issue issue : ClientState.issues()) {
            if (!errorsOnly || issue.severity() == Validation.Severity.ERROR) {
                issues.add(issue);
            }
        }
        list.setRows(issues.size(), this::renderRow, this::clickRow);
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        Validation.Issue issue = issues.get(index);
        int color = switch (issue.severity()) {
            case ERROR -> Ui.BAD;
            case WARNING -> Ui.WARN;
            case INFO -> Ui.TEXT_DIM;
        };

        int cardHeight = rowHeight - 6;
        int background = UiColor.mix(Ui.PANEL, color, hovered ? 0.22f : 0.13f);
        graphics.fill(x, y, x + rowWidth - 2, y + cardHeight, background);
        Ui.border(graphics, x, y, rowWidth - 2, cardHeight,
                UiColor.multiplyAlpha(color, hovered ? 0.82f : 0.56f));
        if (hovered) {
            graphics.fill(x, y + 2, x + 3, y + cardHeight - 2, color);
        }

        int iconX = x + 16;
        int iconY = y + cardHeight / 2;
        Ui.disc(graphics, iconX, iconY, 7, UiColor.multiplyAlpha(color, 0.30f));
        Ui.border(graphics, iconX - 7, iconY - 7, 14, 14, color);
        Ui.labelCentered(graphics, "!", iconX, iconY - 4, color);

        String target = issue.targetKind() + ":" + issue.targetId();
        int targetWidth = Math.min(150, font.width(target));
        int messageWidth = Math.max(60, rowWidth - 56 - targetWidth - 20);
        Ui.label(graphics, Ui.truncate(issue.message(), messageWidth), x + 32, y + 12, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, Ui.truncate(target, 150), x + rowWidth - 12, y + 12,
                hovered ? color : Ui.TEXT_DIM);
    }

    private void clickRow(int index, int button) {
        Validation.Issue issue = issues.get(index);
        switch (issue.targetKind()) {
            case "quest" -> minecraft.setScreen(new QuestCreatorScreen(issue.targetId(), this));
            case "board" -> minecraft.setScreen(new BoardConfigScreen(issue.targetId(), this));
            case "category" -> minecraft.setScreen(new SkillEditorScreen(issue.targetId(), this));
            case "node" -> {
                for (var category : ClientState.categories().values()) {
                    if (category.node(issue.targetId()) != null) {
                        minecraft.setScreen(new SkillEditorScreen(category.id(), this));
                        return;
                    }
                }
            }
            case "level" -> minecraft.setScreen(new LevelManagerScreen(this));
            default -> {
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long errors = ClientState.issues().stream()
                .filter(issue -> issue.severity() == Validation.Severity.ERROR).count();
        long warnings = ClientState.issues().stream()
                .filter(issue -> issue.severity() == Validation.Severity.WARNING).count();

        int footerY = guiTop + guiHeight - 34;
        int summaryX = guiLeft + 116;
        Ui.disc(graphics, summaryX, footerY + 12, 5, 0x44D8756E);
        Ui.labelCentered(graphics, "!", summaryX, footerY + 8, Ui.BAD);
        Ui.label(graphics, errors + " error(s)", summaryX + 10, footerY + 8,
                errors > 0 ? Ui.BAD : Ui.GOOD);

        int warningX = summaryX + 18 + font.width(errors + " error(s)");
        Ui.label(graphics, "/", warningX, footerY + 8, Ui.TEXT_MUTED);
        Ui.label(graphics, warnings + " warning(s)", warningX + 10, footerY + 8,
                warnings > 0 ? Ui.WARN : Ui.TEXT_DIM);

        if (issues.isEmpty()) {
            int centerX = guiLeft + guiWidth / 2;
            int centerY = guiTop + guiHeight / 2 - 8;
            Ui.disc(graphics, centerX, centerY - 28, 16, 0x2A4E7A3F);
            Ui.labelCentered(graphics, "OK", centerX, centerY - 32, Ui.GOOD);
            Ui.scaledCentered(graphics, "Content compiled without problems", centerX, centerY,
                    1.2f, Ui.TEXT_BRIGHT);
            Ui.labelCentered(graphics, "Your current RotasUtils content passed validation.",
                    centerX, centerY + 20, Ui.TEXT_DIM);
        }
    }
}
