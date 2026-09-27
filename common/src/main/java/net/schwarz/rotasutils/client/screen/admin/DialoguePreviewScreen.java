package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.client.screen.player.NpcConversationLayout;
import net.schwarz.rotasutils.client.screen.player.NpcDialogueScreen;
import net.schwarz.rotasutils.core.DialoguePreview;
import net.schwarz.rotasutils.core.DialogueStudio;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class DialoguePreviewScreen extends RotasScreen {
    private static final int STRIP_FILL = 0x4A140E08;
    private static final int STRIP_HOVER = 0x8A2C2218;
    private static final int DIALOGUE_FILL = 0xB8120C07;

    private record Row(String text, String detail, Runnable action, boolean muted) { }

    private final DialogueStudio studio;
    private final String name;
    private final String title;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> speech = new ArrayList<>();
    private NpcConversationLayout layout;
    private int message;
    private boolean ended;
    private String feedback = "";

    public DialoguePreviewScreen(DialogueStudio studio, String name, String title, Screen parent) {
        super(name, parent);
        this.studio = studio;
        this.name = name;
        this.title = title == null ? "" : title;
        this.message = studio.startIndex();
    }

    @Override
    protected void init() {
        guiLeft = 0;
        guiTop = 0;
        guiWidth = width;
        guiHeight = height;
        clearPanels();
        buildContent();
    }

    @Override
    protected void buildContent() {
        rows.clear();
        speech.clear();
        DialoguePreview.Step step = DialoguePreview.step(studio, message);
        if (!ended) {
            for (DialoguePreview.Reply reply : step.replies()) {
                String text = reply.text().isBlank() ? DialogueText.t("unlabeled_reply") : reply.text();
                rows.add(new Row(text, detail(reply), () -> choose(reply.index()), false));
            }
        }
        rows.add(new Row(DialogueText.t("preview.restart"), "", this::restart, true));
        rows.add(new Row(DialogueText.t("preview.back"), "", () -> minecraft.setScreen(parentScreen()), true));
        layout = NpcDialogueScreen.presentationLayout(width, height, rows.size());
        String said = step.message() < 0 ? DialogueText.t("preview.no_opening") : step.speech();
        speech.addAll(Ui.wrap(said, Math.max(16, layout.dialogueWidth() - 20)));
        ScrollPanel choices = new ScrollPanel(layout.optionsX(), layout.optionsY(), layout.optionsWidth(),
                layout.optionsHeight(), layout.optionRowHeight()).withoutBackground().rowHitInsets(1, 1);
        choices.setRows(rows.size(), this::renderRow, this::click);
        registerPanel(choices);
    }

    private String detail(DialoguePreview.Reply reply) {
        String outcome = switch (reply.outcome()) {
            case GO_TO -> {
                int next = studio.nextIndex(message, reply.index());
                yield next >= 0 ? "→ " + DialogueStudio.number(next) : DialogueText.t("dest.unresolved");
            }
            default -> "[" + DialogueText.outcome(reply.outcome()) + "]";
        };
        return reply.conditions().isEmpty() ? outcome : DialogueText.t("preview.conditional") + "  " + outcome;
    }

    private void choose(int reply) {
        DialoguePreview.Result result = DialoguePreview.choose(studio, message, reply);
        StringBuilder text = new StringBuilder();
        if (result.broken()) {
            text.append(DialogueText.t("preview.broken"));
        } else {
            if (result.action() != null) {
                text.append(DialogueText.t("preview.action", DialogueText.outcomeWithTarget(result.action(), result.target())));
            }
            if (result.ended()) {
                text.append(text.isEmpty() ? "" : "  ").append(DialogueText.t("preview.ended"));
            }
        }
        feedback = text.toString();
        message = result.message();
        ended = result.ended();
        Sfx.page();
        rebuild();
    }

    private void restart() {
        message = studio.startIndex();
        ended = false;
        feedback = "";
        Sfx.page();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void click(int index, int button) {
        if (button == 0 && index >= 0 && index < rows.size()) {
            rows.get(index).action().run();
        }
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        Row row = rows.get(index);
        PixelUi.fill(graphics, x, y + 1, rowWidth - 2, rowHeight - 2, 0, hovered ? STRIP_HOVER : STRIP_FILL);
        if (hovered) {
            PixelUi.fill(graphics, x, y + 1, 2, rowHeight - 2, 0, Ui.ACCENT);
        }
        int textY = y + Math.max(2, (rowHeight - 9) / 2);
        int detailWidth = 0;
        if (!row.detail().isEmpty()) {
            String shown = Ui.truncate(row.detail(), Math.max(0, rowWidth * 45 / 100 - 6));
            detailWidth = font.width(shown) + 12;
            Ui.labelRight(graphics, shown, x + rowWidth - 10, textY, Ui.TEXT_DIM);
        }
        int color = row.muted() ? Ui.TEXT_DIM : hovered ? Ui.TEXT_BRIGHT : Ui.TEXT;
        Ui.label(graphics, Ui.truncate(row.text(), rowWidth - 18 - detailWidth), x + 10, textY, color);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.SCRIM_TOP, Ui.SCRIM_BOTTOM);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String banner = DialogueText.t("preview.banner");
        int bannerWidth = font.width(banner) + 16;
        Ui.infoPill(graphics, (width - bannerWidth) / 2, 8, banner, Ui.ACCENT);

        int x = layout.dialogueX();
        int y = layout.dialogueY();
        int w = layout.dialogueWidth();
        int h = layout.dialogueHeight();
        graphics.fill(x, y, x + w, y + h, DIALOGUE_FILL);
        graphics.fill(x, y, x + w, y + 2, Ui.ACCENT);
        int pad = 10;
        String shownName = Ui.truncate(name, w - pad * 2);
        Ui.scaledLabel(graphics, shownName, x + pad, y + 5, 1.15f, Ui.TEXT_BRIGHT);
        if (!title.isBlank()) {
            int titleX = x + pad + (int) Ui.scaledWidth(shownName, 1.15f) + 10;
            if (x + w - pad - titleX > 12) {
                Ui.label(graphics, Ui.truncate(title, x + w - pad - titleX), titleX, y + 8, Ui.ACCENT);
            }
        }
        int lineY = y + 22;
        for (String line : speech) {
            if (lineY + 9 > y + h - 5) {
                break;
            }
            Ui.label(graphics, line, x + pad, lineY, Ui.TEXT_BRIGHT);
            lineY += 10;
        }
        if (!feedback.isEmpty()) {
            Ui.labelRight(graphics, Ui.truncate(feedback, Math.max(40, w - 20)), x + w - 10, y - 12, Ui.ACCENT_HOVER);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen());
    }
}
