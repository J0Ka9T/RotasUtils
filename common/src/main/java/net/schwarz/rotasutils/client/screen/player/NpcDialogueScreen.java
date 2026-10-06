package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.npc.NpcState;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class NpcDialogueScreen extends RotasScreen {
    private static final long REVEAL_MS_PER_CHAR = 18L;
    private static final int MIN_ROW_HEIGHT = 20;
    private static final int STRIP_FILL = 0xE619120C;
    private static final int STRIP_SELECTED = 0xFFE2C18E;
    private static final int DIALOGUE_FILL = 0xE619120C;
    private static final int DIALOGUE_EDGE = 0xFFC89C62;
    private static final int DIALOGUE_EDGE_SOFT = 0xFF8F6A42;
    private static final int DIALOGUE_TEXT = 0xFFF2E2C8;
    private static final int DIALOGUE_TEXT_MUTED = 0xFFD1B792;
    private static final int DIALOGUE_TEXT_SELECTED = 0xFF2A1A10;
    private static final int SHADOW = 0x8A000000;

    private record Option(String label, String detail, int color, String action, String questId) {
    }

    private final String npcId;
    private final NpcDef fallback;
    private final NpcState state;
    private final List<String> offers = new ArrayList<>();
    private final List<Option> options = new ArrayList<>();
    private final List<String> speechLines = new ArrayList<>();
    private NpcConversationLayout layout;
    private ScrollPanel choices;
    private boolean choicesShown;
    private boolean revealAll;
    private long revealStart;
    private int speechChars;

    public NpcDialogueScreen(String npcId, CompoundTag npcTag, String state, List<String> offers) {
        super("Conversation", null);
        this.npcId = npcId;
        this.fallback = npcTag == null || npcTag.isEmpty() ? null : NpcDef.load(npcTag);
        NpcState resolved;
        try {
            resolved = NpcState.valueOf(state);
        } catch (IllegalArgumentException ignored) {
            resolved = NpcState.NO_QUEST;
        }
        this.state = resolved;
        this.offers.addAll(offers);
    }

    private NpcDef npc() {
        NpcDef synced = ClientState.npc(npcId);
        return synced == null ? fallback : synced;
    }

    public static NpcConversationLayout presentationLayout(int width, int height, int optionCount) {
        int count = Math.max(1, optionCount);
        NpcConversationLayout probe = NpcConversationLayout.compute(width, height, count);
        int readable = Math.max(1, probe.optionsHeight() / MIN_ROW_HEIGHT);
        int display = Math.min(count, readable);
        return display >= count ? probe : NpcConversationLayout.compute(width, height, display);
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
        NpcDef npc = npc();
        setHeader(npc == null ? "Conversation" : npc.name());

        choicesShown = false;
        revealAll = false;
        revealStart = System.nanoTime();
        speechLines.clear();
        speechChars = 0;

        layout = presentationLayout(width, height, 1);
        if (npc != null) {
            int speechWidth = Math.max(16, layout.dialogueWidth() - 20);
            for (String line : Ui.wrap(npc.lineFor(state), speechWidth)) {
                speechLines.add(line);
                speechChars += line.length() + 1;
            }
        }
        if (speechChars == 0) {
            revealAll = true;
        }

        buildOptions();
        installChoices();
    }

    @Override
    public void onDataRefreshed() {
        buildOptions();
        installChoices();
    }

    private void buildOptions() {
        options.clear();
        NpcDef npc = npc();
        if (npc != null) {
            for (String questId : offers) {
                QuestDef quest = ClientState.quest(questId);
                if (quest == null) {
                    continue;
                }
                ActiveQuest active = ClientState.progress().active(questId);
                if (active == null) {
                    options.add(new Option(L.t("rotasutils.npc.take", quest.name()),
                            L.t("rotasutils.npc.offer_detail", quest.rank().display(), quest.shortDescription()),
                            Ui.TEXT_BRIGHT, "accept", questId));
                } else if (active.turnInReady()) {
                    options.add(new Option(L.t("rotasutils.npc.hand_in", quest.name()),
                            L.t("rotasutils.status.ready"), Ui.GOOD, "turn_in", questId));
                } else {
                    options.add(new Option(L.t("rotasutils.npc.in_progress", quest.name()),
                            objectiveSummary(quest, active), Ui.TEXT_DIM, "read", questId));
                }
            }
            if (!npc.boardId().isEmpty() && ClientState.board(npc.boardId()) != null) {
                options.add(new Option(L.t("rotasutils.npc.board",
                        ClientState.board(npc.boardId()).name()), "", Ui.TEXT, "board", ""));
            }
            if (npc.role().hasScreen() && npc.role() != NpcDef.Role.MERCHANT && npc.role() != NpcDef.Role.BOARD_KEEPER) {
                options.add(new Option(npc.role().display(), "", Ui.TEXT, "shop", ""));
            }
            if (npc.hasShop()) {
                options.add(new Option(L.t("rotasutils.npc.trade"),
                        npc.trades().isEmpty() ? npc.merchantId() : L.t("rotasutils.npc.trade_count", npc.trades().size()),
                        Ui.TEXT, "shop", ""));
            }
        }
        options.add(new Option(L.t("rotasutils.board.journal"), "", Ui.TEXT_DIM, "journal", ""));
        options.add(new Option(L.t("rotasutils.npc.leave"), "", Ui.TEXT_DIM, "leave", ""));
    }

    private void installChoices() {
        layout = presentationLayout(width, height, Math.max(1, options.size()));
        clearPanels();
        choices = new ScrollPanel(layout.optionsX(), layout.optionsY(), layout.optionsWidth(),
                layout.optionsHeight(), layout.optionRowHeight()).withoutBackground().rowHitInsets(1, 1);
        choices.setRows(choicesShown ? options.size() : 0, this::renderOption, this::clickOption);
        registerPanel(choices);
    }

    private static String objectiveSummary(QuestDef quest, ActiveQuest active) {
        int required = 0;
        int done = 0;
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (quest.objectives().get(i).optional()) {
                continue;
            }
            required++;
            if (active.isComplete(i)) {
                done++;
            }
        }
        return L.t("rotasutils.quest.progress", done, required);
    }

    private void ensureChoicesShown() {
        if (choicesShown || !fullyRevealed()) {
            return;
        }
        choicesShown = true;
        choices.setRows(options.size(), this::renderOption, this::clickOption);
    }

    private void renderOption(GuiGraphics graphics, int index, int x, int y,
                              int rowWidth, int rowHeight, boolean hovered) {
        Option option = options.get(index);
        PixelUi.fill(graphics, x + 2, y + 3, rowWidth - 4, rowHeight - 4, 0, SHADOW);
        int fill = hovered ? STRIP_SELECTED : STRIP_FILL;
        int edge = hovered ? DIALOGUE_EDGE : DIALOGUE_EDGE_SOFT;
        PixelUi.fill(graphics, x, y + 1, rowWidth - 2, rowHeight - 2, 0, fill);
        PixelUi.fill(graphics, x, y + 1, hovered ? 3 : 1, rowHeight - 2, 0, edge);

        int textY = y + Math.max(2, (rowHeight - 9) / 2);
        int primaryText = hovered ? DIALOGUE_TEXT_SELECTED : DIALOGUE_TEXT;
        int secondaryText = hovered ? DIALOGUE_TEXT_SELECTED : DIALOGUE_TEXT_MUTED;
        Ui.label(graphics, Integer.toString(index + 1), x + 8, textY, secondaryText);
        int detailWidth = 0;
        if (!option.detail().isEmpty()) {
            String detail = Ui.truncate(option.detail(), Math.max(0, rowWidth * 38 / 100 - 6));
            detailWidth = Ui.textWidth(detail) + 14;
            Ui.labelRight(graphics, detail, x + rowWidth - 10, textY, secondaryText);
        }
        Ui.label(graphics, Ui.truncate(option.label(), rowWidth - 34 - detailWidth), x + 26, textY, primaryText);
    }

    private void clickOption(int index, int button) {
        if (button != 0) {
            return;
        }
        Option option = options.get(index);
        CompoundTag payload = new CompoundTag();
        payload.putString("npc", npcId);
        payload.putString("quest", option.questId());
        switch (option.action()) {
            case "accept" -> {
                send("npc_accept", payload);
                net.schwarz.rotasutils.client.ClientQuestTracker.track(option.questId());
                Sfx.commit();
            }
            case "turn_in" -> {
                send("npc_turn_in", payload);
                Sfx.reward();
            }
            case "board" -> {
                send("npc_board", payload);
                Sfx.page();
            }
            case "shop" -> {
                send("npc_shop", payload);
                Sfx.page();
            }
            case "journal" -> minecraft.setScreen(new MainMenuScreen(MainMenuScreen.Tab.JOURNAL));
            case "leave" -> onClose();
            default -> minecraft.setScreen(new QuestDetailScreen(option.questId(),
                    npc() == null ? "" : npc().boardId(), this));
        }
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0x56000000, 0x78000000);
        graphics.fillGradient(0, height * 40 / 100, width, height, 0x10000000, 0xB8000000);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ensureChoicesShown();
        renderDialogue(graphics);
        renderFeedback(graphics, (width - feedbackWidth()) / 2, 8,
                Ui.PANEL_ALT, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
    }

    private void renderDialogue(GuiGraphics graphics) {
        int x = layout.dialogueX();
        int y = layout.dialogueY();
        int dialogWidth = layout.dialogueWidth();
        int dialogHeight = layout.dialogueHeight();

        PixelUi.fill(graphics, x + 3, y + 4, dialogWidth, dialogHeight, 0, SHADOW);
        PixelUi.fill(graphics, x, y, dialogWidth, dialogHeight, 0, DIALOGUE_EDGE);
        PixelUi.fill(graphics, x + 1, y + 1, dialogWidth - 2, dialogHeight - 2, 0, DIALOGUE_FILL);
        PixelUi.fill(graphics, x + 10, y + 10, 3, dialogHeight - 20, 0, DIALOGUE_EDGE);

        int contentX = x + 20;
        int contentRight = x + dialogWidth - 16;
        NpcDef npc = npc();
        String name = Ui.truncate(header, Math.max(24, dialogWidth * 38 / 100));
        Ui.scaledLabel(graphics, name, contentX, y + 8, 1.12f, DIALOGUE_TEXT);
        if (npc != null && !npc.title().isBlank()) {
            int titleX = contentX + (int) Ui.scaledWidth(name, 1.12f) + 9;
            int titleRoom = contentRight - titleX;
            if (titleRoom > 16) {
                Ui.label(graphics, Ui.truncate(npc.title(), titleRoom), titleX, y + 10, DIALOGUE_TEXT_MUTED);
            }
        }

        int ruleEnd = Math.min(contentRight, contentX + Math.max(72, dialogWidth / 4));
        graphics.fill(contentX, y + 23, ruleEnd, y + 24, DIALOGUE_EDGE_SOFT);
        renderSpeech(graphics, contentX, y + 31, y + dialogHeight - 10);
        if (fullyRevealed()) {
            Ui.labelRight(graphics, "▼", contentRight, y + dialogHeight - 13, DIALOGUE_EDGE);
        }
    }

    private void renderSpeech(GuiGraphics graphics, int x, int y, int maxY) {
        int remaining = revealedChars();
        int lineY = y;
        for (String line : speechLines) {
            if (remaining <= 0 || lineY + 9 > maxY) {
                break;
            }
            String shown = line.length() <= remaining ? line : line.substring(0, Math.max(0, remaining));
            if (!shown.isEmpty()) {
                Ui.label(graphics, shown, x, lineY, DIALOGUE_TEXT);
            }
            remaining -= line.length() + 1;
            lineY += 10;
        }
    }

    private int revealedChars() {
        if (revealAll || speechChars == 0) {
            return Integer.MAX_VALUE;
        }
        long elapsed = (System.nanoTime() - revealStart) / 1_000_000L;
        return (int) Math.max(0, elapsed / REVEAL_MS_PER_CHAR);
    }

    private boolean fullyRevealed() {
        return revealAll || speechChars == 0 || revealedChars() >= speechChars;
    }

    private void finishReveal() {
        revealAll = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!fullyRevealed()) {
            if (button == 0) {
                finishReveal();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        ensureChoicesShown();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!fullyRevealed() && (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE)) {
            finishReveal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        NpcDef npc = npc();
        if (npc != null && !npc.farewell().isBlank() && minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal(npc.name() + ": " + npc.farewell()), false);
            var voice = npc.voiceSound("farewell");
            if (voice != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(voice, npc.voicePitch() / 100f));
            }
        }
        super.onClose();
    }
}
