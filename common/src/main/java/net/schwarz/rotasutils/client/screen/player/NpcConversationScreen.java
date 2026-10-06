package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.*;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class NpcConversationScreen extends RotasScreen {
    private static final long REVEAL_MS_PER_CHAR = 18L;
    private static final int MIN_ROW_HEIGHT = 20;
    private static final int STRIP_FILL = 0xE619120C;
    private static final int STRIP_DISABLED = 0x99140E08;
    private static final int STRIP_SELECTED = 0xFFE2C18E;
    private static final int DIALOGUE_FILL = 0xE619120C;
    private static final int DIALOGUE_EDGE = 0xFFC89C62;
    private static final int DIALOGUE_TEXT = 0xFFF2E2C8;
    private static final int DIALOGUE_TEXT_MUTED = 0xFFD1B792;
    private static final int DIALOGUE_TEXT_SELECTED = 0xFF2A1A10;

    private record Row(String text, String detail, String response, boolean enabled) {}

    private static final int HEART_FULL = 0xFFFF6FA3;
    private static final int HEART_EMPTY = 0xFF5E4048;
    private static final long BLIP_MS = 55L;

    private final CompoundTag snapshot;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> speechLines = new ArrayList<>();
    private final List<String> pages = new ArrayList<>();
    private int page;
    private long lastBlip;
    private int lastBlipChars;
    private NpcConversationLayout layout;
    private ScrollPanel choices;
    private boolean waiting;
    private boolean choicesShown;
    private boolean revealAll;
    private long revealStart;
    private int speechChars;

    public NpcConversationScreen(CompoundTag snapshot) {
        super(snapshot.getString("name"), previewParent(snapshot));
        this.snapshot = snapshot.copy();
    }

    private static net.minecraft.client.gui.screens.Screen previewParent(CompoundTag snapshot) {
        if (!snapshot.getBoolean("preview")) return null;
        var current = net.minecraft.client.Minecraft.getInstance().screen;
        return current instanceof NpcConversationScreen conversation ? conversation.parentScreen() : current;
    }

    @Override public void onClose() {
        if (snapshot.getBoolean("preview")) minecraft.setScreen(parentScreen());
        else super.onClose();
    }

    @Override protected void init() {
        guiLeft = 0;
        guiTop = 0;
        guiWidth = width;
        guiHeight = height;
        clearPanels();
        buildContent();
    }

    @Override protected void buildContent() {
        waiting = false;
        choicesShown = false;
        revealAll = false;
        revealStart = System.nanoTime();
        rows.clear();
        speechLines.clear();
        speechChars = 0;
        pages.clear();
        for (Tag value : snapshot.getList("pages", Tag.TAG_STRING)) {
            if (!value.getAsString().isBlank()) pages.add(value.getAsString());
        }
        if (pages.isEmpty() && !snapshot.getString("speech").isBlank()) pages.add(snapshot.getString("speech"));
        page = 0;

        for (Tag value : snapshot.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            rows.add(new Row(row.getString("label"), row.getString("detail"),
                    row.getString("id"), row.getBoolean("enabled")));
        }
        if (!snapshot.getBoolean("preview")) {
            rows.add(new Row(L.t("rotasutils.board.journal"), "", "@journal", true));
            rows.add(new Row(L.t("rotasutils.npc.talk_again"), "", "@reopen", true));
        }
        rows.add(new Row(L.t("rotasutils.npc.leave"), "", "@leave", true));

        NpcConversationLayout probe = NpcConversationLayout.compute(width, height, rows.size());
        int readable = Math.max(1, probe.optionsHeight() / MIN_ROW_HEIGHT);
        int displayCount = Math.min(rows.size(), readable);
        layout = displayCount == rows.size() ? probe
                : NpcConversationLayout.compute(width, height, displayCount);

        loadPage();

        choices = new ScrollPanel(layout.optionsX(), layout.optionsY(), layout.optionsWidth(),
                layout.optionsHeight(), layout.optionRowHeight()).withoutBackground().rowHitInsets(1, 1);
        choices.setRows(0, this::renderChoice, this::clickChoice);
        registerPanel(choices);
    }

    @Override protected void renderFrame(GuiGraphics graphics) {
    }

    @Override protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0x56000000, 0x78000000);
        graphics.fillGradient(0, height * 40 / 100, width, height, 0x10000000, 0xB8000000);
    }

    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureChoicesShown();
        renderFeedback(graphics, (width - feedbackWidth()) / 2, 8,
                Ui.PANEL_ALT, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
        renderDialogue(graphics);
        renderSnapshotFeedback(graphics);
    }

    private void loadPage() {
        speechLines.clear();
        speechChars = 0;
        revealAll = false;
        revealStart = System.nanoTime();
        lastBlipChars = 0;
        int speechWidth = Math.max(16, layout.dialogueWidth() - 40);
        String text = page < pages.size() ? pages.get(page) : "";
        for (String line : Ui.wrap(text, speechWidth)) {
            speechLines.add(line);
            speechChars += line.length() + 1;
        }
        if (speechChars == 0) revealAll = true;
    }

    private boolean lastPage() {
        return page >= pages.size() - 1;
    }

    private boolean advancePage() {
        if (lastPage()) return false;
        page++;
        loadPage();
        Sfx.page();
        return true;
    }

    private void ensureChoicesShown() {
        if (choicesShown || !fullyRevealed() || !lastPage()) return;
        choicesShown = true;
        choices.setRows(rows.size(), this::renderChoice, this::clickChoice);
    }

    private void renderDialogue(GuiGraphics graphics) {
        int x = layout.dialogueX();
        int y = layout.dialogueY();
        int width = layout.dialogueWidth();
        int height = layout.dialogueHeight();
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x8A000000);
        graphics.fill(x, y, x + width, y + height, DIALOGUE_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, DIALOGUE_FILL);
        graphics.fill(x + 10, y + 9, x + 13, y + height - 9, DIALOGUE_EDGE);

        int pad = 20;
        String name = Ui.truncate(snapshot.getString("name"), width - pad * 2);
        Ui.scaledLabel(graphics, name, x + pad, y + 7, 1.15f, DIALOGUE_TEXT);
        String title = snapshot.getString("title");
        if (!title.isBlank()) {
            int titleX = x + pad + (int) Ui.scaledWidth(name, 1.15f) + 10;
            int titleRoom = x + width - pad - titleX;
            if (titleRoom > 12) {
                Ui.label(graphics, Ui.truncate(title, titleRoom), titleX, y + 10, DIALOGUE_TEXT_MUTED);
            }
        }
        graphics.fill(x + pad, y + 24, x + Math.min(width - pad, pad + Math.max(72, width / 4)), y + 25, DIALOGUE_EDGE);
        if (snapshot.getBoolean("romance")) renderAffection(graphics, x + width - pad, y + 9);
        renderSpeech(graphics, x + pad, y + 32, y + height - 7);
        blip();
        if (fullyRevealed() && !lastPage()) {
            int bob = (int) ((System.currentTimeMillis() / 250) % 2);
            Ui.labelRight(graphics, "▼", x + width - pad, y + height - 14 + bob, DIALOGUE_EDGE);
        }
        if (pages.size() > 1) {
            Ui.labelRight(graphics, (page + 1) + "/" + pages.size(), x + width - pad - 12, y + height - 13, DIALOGUE_TEXT_MUTED);
        }
    }

    private void renderAffection(GuiGraphics graphics, int right, int y) {
        int affection = snapshot.getInt("affection");
        String tier = L.t("rotasutils.affection.tier." + snapshot.getString("tier"));
        Ui.labelRight(graphics, tier, right, y, HEART_FULL);
        int hx = right - Ui.textWidth(tier) - 8 - 5 * 9;
        int filled = net.schwarz.rotasutils.core.Affection.hearts(affection);
        for (int i = 0; i < 5; i++) {
            Ui.label(graphics, "♥", hx + i * 9, y, i < filled ? HEART_FULL : HEART_EMPTY);
        }
    }

    private void blip() {
        if (fullyRevealed()) return;
        int shown = revealedChars();
        long now = System.currentTimeMillis();
        if (shown > lastBlipChars + 1 && now - lastBlip >= BLIP_MS) {
            lastBlip = now;
            lastBlipChars = shown;
            Sfx.blip();
        }
    }

    private void renderSpeech(GuiGraphics graphics, int x, int y, int maxY) {
        int remaining = revealedChars();
        int lineY = y;
        for (String line : speechLines) {
            if (remaining <= 0 || lineY + 9 > maxY) break;
            String shown = line.length() <= remaining ? line : line.substring(0, Math.max(0, remaining));
            if (!shown.isEmpty()) Ui.label(graphics, shown, x, lineY, DIALOGUE_TEXT);
            remaining -= line.length() + 1;
            lineY += 10;
        }
    }

    private void renderSnapshotFeedback(GuiGraphics graphics) {
        String feedback = snapshot.getString("feedback");
        if (feedback.isBlank()) return;
        int room = Math.max(40, layout.dialogueWidth() - layout.optionsWidth() - 20);
        Ui.labelRight(graphics, Ui.truncate(feedback, room),
                layout.dialogueX() + layout.dialogueWidth() - 10, layout.dialogueY() - 12, Ui.TEXT_DIM);
    }

    private void renderChoice(GuiGraphics graphics, int index, int x, int y, int rowWidth,
                              int rowHeight, boolean hovered) {
        Row row = rows.get(index);
        NpcActionPresentation.Kind kind = NpcActionPresentation.kind(row.response());
        boolean usable = row.enabled() && !waiting;
        boolean hot = usable && hovered;
        int fill = !row.enabled() ? STRIP_DISABLED : hot ? STRIP_SELECTED : STRIP_FILL;
        int edge = hot ? DIALOGUE_EDGE : 0xFF8F6A42;
        PixelUi.fill(graphics, x + 2, y + 3, rowWidth - 4, rowHeight - 4, 0, 0x66000000);
        PixelUi.fill(graphics, x, y + 1, rowWidth - 2, rowHeight - 2, 0, fill);
        PixelUi.fill(graphics, x, y + 1, hot ? 3 : 1, rowHeight - 2, 0, edge);

        int textY = y + Math.max(2, (rowHeight - 9) / 2);
        int accent = switch (kind) {
            case QUEST -> 0xFFD6A75E;
            case SHOP -> 0xFFD5A05A;
            case GIFT -> 0xFF8FAD7D;
            case DIALOGUE -> DIALOGUE_EDGE;
            case SERVICE -> 0xFFB8C3CE;
            case NAVIGATION -> DIALOGUE_TEXT_MUTED;
        };
        int iconFill = row.enabled() ? accent : 0xFF7A6654;
        PixelUi.fill(graphics, x + 7, y + Math.max(3, (rowHeight - 13) / 2), 13, 13, 1, iconFill);
        Ui.labelCentered(graphics, NpcActionPresentation.glyph(row.response()), x + 13, textY,
                hot ? DIALOGUE_TEXT_SELECTED : DIALOGUE_FILL);

        int detailWidth = 0;
        String detail = row.detail();
        if (!detail.isEmpty()) {
            String shown = Ui.truncate(detail, Math.max(0, rowWidth * 45 / 100 - 6));
            detailWidth = Ui.textWidth(shown) + 12;
            Ui.labelRight(graphics, shown, x + rowWidth - 10, textY,
                    hot ? DIALOGUE_TEXT_SELECTED : row.enabled() ? DIALOGUE_TEXT_MUTED : 0xFF8F7A67);
        }
        int labelColor = hot ? DIALOGUE_TEXT_SELECTED : row.enabled() ? DIALOGUE_TEXT : 0xFF8F7A67;
        Ui.label(graphics, Ui.truncate(row.text(), rowWidth - 34 - detailWidth), x + 26, textY, labelColor);
    }

    private void clickChoice(int index, int button) {
        if (button != 0 || waiting) return;
        Row row = rows.get(index);
        if (!row.enabled() || row.response().isEmpty()) return;
        switch (row.response()) {
            case "@leave" -> onClose();
            case "@journal" -> minecraft.setScreen(new MainMenuScreen(MainMenuScreen.Tab.JOURNAL));
            case "@reopen" -> {
                CompoundTag request = new CompoundTag();
                request.putString("npc", snapshot.getString("npc"));
                waiting = true;
                send("npc_reopen", request);
            }
            default -> {
                CompoundTag request = new CompoundTag();
                request.putUUID("nonce", snapshot.getUUID("nonce"));
                request.putString("response", row.response());
                waiting = true;
                send("npc_response", request);
            }
        }
    }

    private int revealedChars() {
        if (revealAll || speechChars == 0) return Integer.MAX_VALUE;
        long elapsed = (System.nanoTime() - revealStart) / 1_000_000L;
        return (int) Math.max(0, elapsed / REVEAL_MS_PER_CHAR);
    }

    private boolean fullyRevealed() {
        return revealAll || speechChars == 0 || revealedChars() >= speechChars;
    }

    private void finishReveal() {
        revealAll = true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!fullyRevealed()) {
            if (button == 0) {
                finishReveal();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && advancePage()) return true;
        ensureChoicesShown();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean advance = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
        if (advance && !fullyRevealed()) {
            finishReveal();
            return true;
        }
        if (advance && advancePage()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
