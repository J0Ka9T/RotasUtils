package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.network.RotasNetwork;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Window chrome shared by the console screens: title row, server feedback badge, scroll panel
 * plumbing and back navigation. Subclasses own everything inside the content area.
 */
@Environment(EnvType.CLIENT)
public abstract class RotasScreen extends Screen {
    protected int guiLeft;
    protected int guiTop;
    protected int guiWidth;
    protected int guiHeight;

    private final Screen parent;
    private final List<ScrollPanel> panels = new ArrayList<>();
    /** Header text drawn in the title bar; screens may change it as their model loads. */
    protected String header;
    private long openTransitionStartedAt;

    protected RotasScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.header = title;
        this.parent = parent;
    }

    protected void setHeader(String header) {
        this.header = header;
    }

    /** Identifies this screen for world-selection round trips. */
    public String screenKey() {
        return getClass().getSimpleName();
    }

    /** Called when a world selection this screen requested comes back. */
    public void onPick(String fieldKey, String value) {
    }

    /** What a screen does when the server pushes fresh data while it is open. */
    protected enum Refresh {
        /** Nothing: the screen reads live data while drawing, or refreshes itself. */
        NONE,
        /** Rebuild the widgets from the new data, keeping scroll positions and typed text. */
        REBUILD,
        /** Hold an unsaved draft: leave it alone and tell the admin the data changed underneath. */
        BANNER
    }

    private boolean dataChanged;
    private boolean sourceSeen;
    private Object lastSource;
    /** When this screen last sent an action; a change right after it is the screen's own save. */
    private long ownActionAt;
    private static final long OWN_ACTION_WINDOW_MS = 5000;

    /** How this screen reacts to content and kernel pushes; opt in per screen, nothing changes by default. */
    protected Refresh refreshMode() {
        return Refresh.NONE;
    }

    /**
     * The server data this screen shows or was opened on, compared with {@code equals} after each push
     * (a CompoundTag, a JSON string, a record). Null means "not tracked": a REBUILD screen then rebuilds
     * on every content or kernel push, a BANNER screen never warns.
     */
    protected Object watchedSource() {
        return null;
    }

    /** Called after a server sync so live screens refresh their model. Screens with their own refresh override it. */
    public void onDataRefreshed() {
    }

    /** Entry point for {@link ScreenRouter}: runs the screen's own refresh, then its {@link #refreshMode()}. */
    public final void dispatchRefresh(int kinds) {
        onDataRefreshed();
        if ((kinds & (ScreenRouter.CONTENT | ScreenRouter.KERNEL)) == 0 || minecraft == null) {
            return;
        }
        switch (refreshMode()) {
            case REBUILD -> {
                if (sourceChanged(true)) {
                    rebuildKeepingState();
                }
            }
            case BANNER -> {
                if (sourceChanged(false)) {
                    boolean own = Util.getMillis() - ownActionAt < OWN_ACTION_WINDOW_MS;
                    ownActionAt = 0;
                    dataChanged |= !own;
                }
            }
            default -> { }
        }
    }

    private boolean sourceChanged(boolean whenUntracked) {
        Object now = watchedSource();
        if (now == null && lastSource == null) {
            return whenUntracked;
        }
        boolean changed = !java.util.Objects.equals(now, lastSource);
        lastSource = now;
        return changed;
    }

    /**
     * Rebuilds every widget and puts back what the admin was in the middle of: scroll position of each
     * list, text and focus of each text box. Matching is by creation order, so it is skipped when the
     * new layout has a different number of lists or boxes.
     */
    protected final void rebuildKeepingState() {
        if (minecraft == null) {
            return;
        }
        int[] scrolls = panels.stream().mapToInt(ScrollPanel::scroll).toArray();
        List<EditBox> before = editBoxes();
        String[] texts = before.stream().map(EditBox::getValue).toArray(String[]::new);
        int focused = -1;
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).isFocused()) {
                focused = i;
            }
        }
        rebuildWidgets();
        if (panels.size() == scrolls.length) {
            for (int i = 0; i < scrolls.length; i++) {
                panels.get(i).setScroll(scrolls[i]);
            }
        }
        List<EditBox> after = editBoxes();
        if (after.size() == texts.length) {
            for (int i = 0; i < texts.length; i++) {
                if (!after.get(i).getValue().equals(texts[i])) {
                    after.get(i).setValue(texts[i]);
                }
            }
            if (focused >= 0) {
                setFocused(after.get(focused));
                after.get(focused).setFocused(true);
            }
        }
    }

    private List<EditBox> editBoxes() {
        List<EditBox> boxes = new ArrayList<>();
        for (var child : children()) {
            if (child instanceof EditBox box) {
                boxes.add(box);
            }
        }
        return boxes;
    }

    protected void registerPanel(ScrollPanel panel) {
        panels.add(panel);
    }

    protected void clearPanels() {
        panels.clear();
    }

    protected int maxGuiWidth() {
        return 620;
    }

    protected int maxGuiHeight() {
        return 380;
    }

    @Override
    protected void init() {
        openTransitionStartedAt = Util.getMillis();
        guiWidth = Ui.fill(width, maxGuiWidth());
        guiHeight = Ui.fill(height, maxGuiHeight());
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        panels.clear();
        buildContent();
        if (!sourceSeen) {
            sourceSeen = true;
            lastSource = watchedSource();
        }
    }

    protected abstract void buildContent();

    protected void addBackButton() {
        addBackButton(guiLeft + 6, 54);
    }

    protected void addBackButton(int x, int width) {
        addRenderableWidget(Ui.button(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(x, guiTop + guiHeight - 28, width, 22)
                .build());
    }

    protected void goBack() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            onClose();
        }
    }

    protected Screen parentScreen() {
        return parent;
    }

    protected void confirmLeavingDraft(CompoundTag draft, CompoundTag baseline, Runnable leave) {
        if (draft.equals(baseline)) { leave.run(); return; }
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) { leave.run(); } else { minecraft.setScreen(this); }
        }, L.c("rotasutils.admin.unsaved"), L.c("rotasutils.admin.unsaved_detail")));
    }

    protected void send(String action) {
        ownActionAt = Util.getMillis();
        RotasNetwork.sendAction(action);
    }

    protected void send(String action, CompoundTag payload) {
        ownActionAt = Util.getMillis();
        RotasNetwork.sendAction(action, payload);
    }

    /** Starts a world selection and closes the screen so the world is reachable. */
    protected void requestPick(String kind, String fieldKey) {
        CompoundTag payload = new CompoundTag();
        payload.putString("kind", kind);
        payload.putString("screen", screenKey());
        payload.putString("field", fieldKey);
        ScreenRouter.rememberPending(this);
        send("pick", payload);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(graphics);
        renderFrame(graphics);

        if (!renderPanelsAfterContent()) {
            renderPanels(graphics, mouseX, mouseY);
        }
        renderContent(graphics, mouseX, mouseY, partialTick);
        if (renderPanelsAfterContent()) {
            renderPanels(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderOpenTransition(graphics);
    }

    /**
     * Most screens paint panel contents before their own labels and overlays.
     * A screen whose panel is a transparent scrolling list can opt into painting
     * that list after its opaque frame instead of having the frame cover rows.
     */
    protected boolean renderPanelsAfterContent() {
        return false;
    }

    private void renderPanels(GuiGraphics graphics, int mouseX, int mouseY) {
        for (ScrollPanel panel : panels) {
            panel.render(graphics, mouseX, mouseY);
        }
    }

    private void renderOpenTransition(GuiGraphics graphics) {
        int alpha = ScreenOpenTransition.overlayAlpha(Util.getMillis() - openTransitionStartedAt);
        if (alpha > 0) {
            int veilColor = (alpha << 24) | (Ui.BACKGROUND & 0x00FFFFFF);
            graphics.fill(0, 0, width, height, veilColor);
        }
    }

    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Full-screen wash behind the window. */
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, Ui.SCRIM_TOP);
    }

    /** Title row: name on the left, the server's answer to the last action on the right. */
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        int feedbackWidth = feedbackWidth();
        int headerWidth = guiWidth - 2 * Ui.PAD - (feedbackWidth == 0 ? 0 : feedbackWidth + Ui.GAP);
        // Native font scale keeps glyphs aligned to the GUI pixel grid.
        Ui.scaledLabel(graphics, Ui.truncate(header, Math.max(24, headerWidth)),
                guiLeft + Ui.PAD, guiTop + 9, 1.0f, Ui.TEXT_BRIGHT);
        Ui.separator(graphics, guiLeft + Ui.PAD, guiTop + 26, guiWidth - 2 * Ui.PAD);
        renderFeedback(graphics, guiLeft + guiWidth - feedbackWidth - Ui.PAD, guiTop + 7,
                RotasTheme.SURFACE_HIGH, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
        if (dataChanged) {
            // Title row, left of the feedback badge, so it never covers the content or the footer buttons.
            String warning = Ui.truncate(L.t("rotasutils.common.data_changed"), guiWidth / 2 - Ui.PAD);
            Ui.labelRight(graphics, warning, guiLeft + guiWidth - feedbackWidth - Ui.PAD
                    - (feedbackWidth == 0 ? 0 : Ui.GAP), guiTop + 9, Ui.WARN);
        }
    }

    /** Width the feedback badge needs, or 0 when there is nothing to show. */
    protected int feedbackWidth() {
        String feedback = ClientState.feedbackMessage();
        return feedback.isEmpty() ? 0 : Math.min(guiWidth / 2, font.width(feedback) + 12);
    }

    /**
     * The last server response, as a pixel badge whose leading edge carries the outcome colour.
     * The message itself is the server's wording, so the screen never invents a result.
     */
    protected void renderFeedback(GuiGraphics graphics, int x, int y,
                                  int okBackground, int badBackground, int okText, int badText) {
        int badgeWidth = feedbackWidth();
        if (badgeWidth == 0) {
            return;
        }
        boolean ok = ClientState.feedbackOk();
        int fill = ok ? okBackground : badBackground;
        int edge = ok ? okText : badText;
        PixelUi.frame(graphics, x, y, badgeWidth, 15,
                7, edge, fill);
        graphics.fill(x + 3, y + 3, x + 5, y + 12, edge);
        Ui.label(graphics, Ui.truncate(ClientState.feedbackMessage(), badgeWidth - 12),
                x + 9, y + 4, ok ? okText : badText);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ScrollPanel panel : panels) {
            if (panel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (ScrollPanel panel : panels) {
            if (panel.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (ScrollPanel panel : panels) {
            if (panel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (ScrollPanel panel : panels) {
            handled |= panel.mouseReleased(mouseX, mouseY, button);
        }
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        for (ScrollPanel panel : panels) {
            if (panel.keyPressed(keyCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
