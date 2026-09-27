package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillConnection;
import net.schwarz.rotasutils.skill.SkillEffect;
import net.schwarz.rotasutils.skill.SkillNode;
import net.schwarz.rotasutils.util.Ids;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Visual skill tree editor, kept to a handful of actions: add a skill (under the selected one, already
 * linked), edit it, link it, delete it. Which skills are starting points is worked out from the links,
 * so there is no root flag or link type to manage here; those live in the skill's advanced settings.
 */
@Environment(EnvType.CLIENT)
public class SkillEditorScreen extends RotasScreen {
    private static final int NODE_SIZE = 26;
    private static final int GRID = 40;
    private static final long DOUBLE_CLICK_MS = 350;

    private final String categoryId;
    private SkillCategory draft;
    private CompoundTag editingBaseline;

    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean panning;
    private String selectedNodeId = "";
    private String draggingNodeId = "";
    /** While on, clicking another skill links it to (or unlinks it from) the selected skill. */
    private boolean linking;
    private String lastClickId = "";
    private long lastClickTime;

    private int canvasLeft;
    private int canvasTop;
    private int canvasWidth;
    private int canvasHeight;

    public SkillEditorScreen(String categoryId, Screen parent) {
        super("Skill Tree Editor", parent);
        this.categoryId = categoryId;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 720);
        guiHeight = Ui.fill(height, 440);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        if (draft == null) {
            SkillCategory source = ClientState.category(categoryId);
            draft = source == null ? new SkillCategory(categoryId, "New Category")
                    : SkillCategory.load(source.save());
            editingBaseline = draft.save();
        }
        setHeader(ThaiText.phrase("Skill tree: {}").replace("{}", draft.name()));

        int sideWidth = 132;
        canvasLeft = guiLeft + 6;
        canvasTop = guiTop + 32;
        canvasWidth = guiWidth - sideWidth - 18;
        canvasHeight = guiHeight - 96;

        int sideX = guiLeft + guiWidth - sideWidth - 6;
        int y = guiTop + 32;
        boolean hasSelection = selected() != null;

        addRenderableWidget(Ui.primaryButton(Ui.text(hasSelection ? "+ Add skill after this" : "+ Add skill"),
                button -> addNode()).bounds(sideX, y, sideWidth, 22).build());
        y += 26;
        addRenderableWidget(Ui.button(Ui.text("Edit skill"), button -> editNode())
                .bounds(sideX, y, sideWidth, 22).build()).active = hasSelection;
        y += 26;
        addRenderableWidget(Ui.button(Ui.text(linking ? "Stop linking" : "Link to another skill"), button -> {
            linking = !linking && selected() != null;
            rebuild();
        }).bounds(sideX, y, sideWidth, 22).build()).active = hasSelection;
        y += 26;
        addRenderableWidget(Ui.dangerButton(Ui.text("Delete skill"), button -> deleteNode())
                .bounds(sideX, y, sideWidth, 22).build()).active = hasSelection;
        y += 38;
        addRenderableWidget(Ui.button(Ui.text("Who can use this tree"),
                        button -> minecraft.setScreen(new SkillCategorySettingsScreen(draft, this)))
                .bounds(sideX, y, sideWidth, 22).build());
        y += 26;
        addRenderableWidget(Ui.button(Ui.text("Preview as player"), button ->
                        minecraft.setScreen(new net.schwarz.rotasutils.client.screen.player.SkillTreeScreen(this)))
                .bounds(sideX, y, sideWidth, 22).build());

        addRenderableWidget(Ui.primaryButton(Ui.text("Save"), button -> {
            updateStartingSkills();
            minecraft.setScreen(new ConfigReviewScreen(this, "save_category", categoryPayload(), editingBaseline));
        }).bounds(sideX, guiTop + guiHeight - 28, sideWidth, 22).build());

        addBackButton();
    }

    private CompoundTag categoryPayload() {
        CompoundTag payload = new CompoundTag();
        payload.put("category", draft.save());
        return payload;
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private SkillNode selected() {
        return selectedNodeId.isEmpty() ? null : draft.node(selectedNodeId);
    }

    /**
     * A skill with no prerequisite links is a starting point; everything else is reached through its
     * links. Deriving this keeps a tree valid without asking the admin to understand "root" nodes.
     */
    private void updateStartingSkills() {
        for (SkillNode node : draft.nodes().values()) {
            node.setRoot(prerequisites(node).isEmpty());
        }
    }

    /** Links that must be satisfied before {@code node} can be taken. */
    static List<SkillConnection> prerequisites(SkillNode node) {
        List<SkillConnection> result = new ArrayList<>();
        for (SkillConnection connection : node.connections()) {
            if (connection.type() != SkillConnection.Type.VISUAL_ONLY
                    && connection.type() != SkillConnection.Type.EXCLUSIVE) {
                result.add(connection);
            }
        }
        return result;
    }

    private void addNode() {
        Set<String> taken = new LinkedHashSet<>();
        for (SkillCategory category : ClientState.categories().values()) {
            taken.addAll(category.nodes().keySet());
        }
        taken.addAll(draft.nodes().keySet());
        SkillNode after = selected();
        SkillNode node = new SkillNode(Ids.unique("skill", taken), draft.id());
        int x = after == null ? snap((int) -panX) : after.x();
        int y = after == null ? snap((int) -panY) : after.y() + GRID;
        // Step sideways until the spot is free, so a new skill never hides under an existing one.
        for (int attempt = 0; attempt < 32 && occupied(x, y); attempt++) {
            x += GRID;
        }
        node.setPosition(x, y);
        if (after != null) {
            node.connections().add(new SkillConnection(after.id()));
        }
        draft.putNode(node);
        selectedNodeId = node.id();
        linking = false;
        updateStartingSkills();
        rebuild();
    }

    private boolean occupied(int x, int y) {
        for (SkillNode node : draft.nodes().values()) {
            if (node.x() == x && node.y() == y) {
                return true;
            }
        }
        return false;
    }

    /** Makes {@code target} require the selected skill, or removes that requirement if it exists. */
    private void toggleLink(SkillNode target) {
        SkillNode source = selected();
        if (source == null || source == target) {
            return;
        }
        boolean removed = target.connections().removeIf(connection -> connection.fromId().equals(source.id()));
        if (!removed) {
            // A two-way requirement would lock both skills forever.
            source.connections().removeIf(connection -> connection.fromId().equals(target.id()));
            target.connections().add(new SkillConnection(source.id()));
        }
        updateStartingSkills();
    }

    private void deleteNode() {
        SkillNode node = selected();
        if (node == null) {
            return;
        }
        draft.removeNode(node.id());
        for (SkillNode other : draft.nodes().values()) {
            other.connections().removeIf(connection -> connection.fromId().equals(node.id()));
            other.exclusiveWith().remove(node.id());
        }
        selectedNodeId = "";
        linking = false;
        updateStartingSkills();
        rebuild();
    }

    private void editNode() {
        SkillNode node = selected();
        if (node != null) {
            linking = false;
            minecraft.setScreen(new SkillNodeEditorScreen(draft, node, this));
        }
    }

    private static int snap(int value) {
        return Math.round(value / (float) GRID) * GRID;
    }

    // Rendering ------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(canvasLeft, canvasTop, canvasLeft + canvasWidth, canvasTop + canvasHeight,
                draft.backgroundColor());
        Ui.border(graphics, canvasLeft, canvasTop, canvasWidth, canvasHeight, Ui.BORDER);

        SkillNode hoveredNode = Ui.inside(mouseX, mouseY, canvasLeft, canvasTop, canvasWidth, canvasHeight)
                ? nodeAt(mouseX, mouseY) : null;
        graphics.enableScissor(canvasLeft + 1, canvasTop + 1,
                canvasLeft + canvasWidth - 1, canvasTop + canvasHeight - 1);
        drawGrid(graphics);
        for (SkillNode node : draft.nodes().values()) {
            for (SkillConnection connection : node.connections()) {
                SkillNode source = draft.node(connection.fromId());
                if (source == null) {
                    continue;
                }
                drawLink(graphics,
                        screenX(source.x()) + NODE_SIZE / 2, screenY(source.y()) + NODE_SIZE / 2,
                        screenX(node.x()) + NODE_SIZE / 2, screenY(node.y()) + NODE_SIZE / 2,
                        connection.type());
            }
        }
        for (SkillNode node : draft.nodes().values()) {
            int x = screenX(node.x());
            int y = screenY(node.y());
            boolean start = prerequisites(node).isEmpty();
            int fill = node.disabled() ? 0xFF2F2A22
                    : start ? 0xFF2C3826
                    : node.type() == SkillNode.NodeType.KEYSTONE ? 0xFF3E3220
                    : Ui.PANEL_ALT;
            graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, fill);
            boolean isSelected = node.id().equals(selectedNodeId);
            int outline = isSelected ? (linking ? Ui.ACCENT : Ui.TEXT)
                    : linking && node == hoveredNode ? Ui.GOOD
                    : Ui.BORDER;
            Ui.border(graphics, x, y, NODE_SIZE, NODE_SIZE, outline);
            if (isSelected) {
                Ui.border(graphics, x - 1, y - 1, NODE_SIZE + 2, NODE_SIZE + 2, outline);
            }
            Ui.icon(graphics, node.icon(), x + 5, y + 5);
            if (node.maxRank() > 1) {
                Ui.labelRight(graphics, "x" + node.maxRank(), x + NODE_SIZE - 2, y + NODE_SIZE - 9, Ui.TEXT_DIM);
            }
            if (isSelected || node == hoveredNode) {
                String name = Ui.truncate(node.name(), 90);
                Ui.labelCentered(graphics, name, x + NODE_SIZE / 2, y + NODE_SIZE + 3, Ui.TEXT_BRIGHT);
            }
        }
        graphics.disableScissor();

        if (draft.nodes().isEmpty()) {
            Ui.labelCentered(graphics, "This tree is empty. Press + Add skill to start.",
                    canvasLeft + canvasWidth / 2, canvasTop + canvasHeight / 2 - 4, Ui.TEXT_DIM);
        }

        int infoY = canvasTop + canvasHeight + 6;
        int infoWidth = canvasWidth;
        SkillNode node = selected();
        if (linking && node != null) {
            Ui.label(graphics, Ui.truncate(ThaiText.phrase("Click the skill that should need \"{}\" first. Click it again to remove the link.")
                    .replace("{}", node.name()), infoWidth), canvasLeft, infoY, Ui.WARN);
        } else if (node == null) {
            Ui.label(graphics, Ui.truncate("Click a skill to select it, double-click to edit it. Drag to move; drag empty space to look around; scroll to zoom.", infoWidth),
                    canvasLeft, infoY, Ui.TEXT_DIM);
        } else {
            Ui.label(graphics, Ui.truncate(node.name(), infoWidth), canvasLeft, infoY, Ui.ACCENT);
            Ui.label(graphics, Ui.truncate(SkillNodeEditorScreen.costLine(node), infoWidth), canvasLeft, infoY + 11, Ui.TEXT_DIM);
            List<String> gives = new ArrayList<>();
            for (SkillEffect effect : node.effects()) {
                gives.add(SkillNodeEditorScreen.describe(effect, node.maxRank()));
            }
            Ui.label(graphics, Ui.truncate(gives.isEmpty() ? ThaiText.phrase("Gives nothing yet - edit it to add a bonus.")
                    : String.join(",  ", gives), infoWidth), canvasLeft, infoY + 22, gives.isEmpty() ? Ui.WARN : Ui.TEXT);
        }
        Ui.label(graphics, "Green = starting skill (no links needed)", canvasLeft, infoY + 36, Ui.TEXT_MUTED);
    }

    private void drawGrid(GuiGraphics graphics) {
        int spacing = Math.max(8, (int) (GRID * zoom));
        int originX = screenX(0);
        int originY = screenY(0);
        for (int x = originX % spacing; x < canvasLeft + canvasWidth; x += spacing) {
            if (x >= canvasLeft) {
                graphics.fill(x, canvasTop, x + 1, canvasTop + canvasHeight, 0x18FFFFFF);
            }
        }
        for (int y = originY % spacing; y < canvasTop + canvasHeight; y += spacing) {
            if (y >= canvasTop) {
                graphics.fill(canvasLeft, y, canvasLeft + canvasWidth, y + 1, 0x18FFFFFF);
            }
        }
    }

    private void drawLink(GuiGraphics graphics, int x1, int y1, int x2, int y2, SkillConnection.Type type) {
        boolean dashed = type == SkillConnection.Type.REQUIRE_ANY || type == SkillConnection.Type.EXCLUSIVE;
        int step = dashed ? 6 : 1;
        int segment = dashed ? 3 : 1;
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        for (int x = minX; x < maxX; x += step) {
            graphics.fill(x, y1, Math.min(x + segment, maxX), y1 + 1, type.color());
        }
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        for (int y = minY; y < maxY; y += step) {
            graphics.fill(x2, y, x2 + 1, Math.min(y + segment, maxY), type.color());
        }
        if (type == SkillConnection.Type.REQUIRE_ALL) {
            graphics.fill(minX, y1 + 2, maxX, y1 + 3, type.color());
        }
    }

    private int screenX(int nodeX) {
        return canvasLeft + canvasWidth / 2 + (int) ((nodeX + panX) * zoom);
    }

    private int screenY(int nodeY) {
        return canvasTop + canvasHeight / 2 + (int) ((nodeY + panY) * zoom);
    }

    private SkillNode nodeAt(double mouseX, double mouseY) {
        for (SkillNode node : draft.nodes().values()) {
            if (Ui.inside((int) mouseX, (int) mouseY, screenX(node.x()), screenY(node.y()), NODE_SIZE, NODE_SIZE)) {
                return node;
            }
        }
        return null;
    }

    // Interaction ----------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Ui.inside((int) mouseX, (int) mouseY, canvasLeft, canvasTop, canvasWidth, canvasHeight)) {
            SkillNode node = nodeAt(mouseX, mouseY);
            if (button == 1 || node == null) {
                if (node == null && button == 0 && !selectedNodeId.isEmpty()) {
                    selectedNodeId = "";
                    linking = false;
                    rebuild();
                }
                panning = true;
                return true;
            }
            if (linking && !node.id().equals(selectedNodeId)) {
                toggleLink(node);
                linking = false;
                rebuild();
                return true;
            }
            long now = System.currentTimeMillis();
            boolean doubleClick = node.id().equals(lastClickId) && now - lastClickTime < DOUBLE_CLICK_MS;
            lastClickId = node.id();
            lastClickTime = now;
            selectedNodeId = node.id();
            if (doubleClick) {
                editNode();
                return true;
            }
            draggingNodeId = node.id();
            rebuild();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!draggingNodeId.isEmpty()) {
            SkillNode node = draft.node(draggingNodeId);
            if (node != null) {
                node.setPosition(snap(node.x()), snap(node.y()));
            }
        }
        draggingNodeId = "";
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingNodeId.isEmpty()) {
            SkillNode node = draft.node(draggingNodeId);
            if (node != null) {
                node.setPosition(node.x() + (int) (dragX / zoom), node.y() + (int) (dragY / zoom));
            }
            return true;
        }
        if (panning) {
            panX += dragX / zoom;
            panY += dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside((int) mouseX, (int) mouseY, canvasLeft, canvasTop, canvasWidth, canvasHeight)) {
            zoom = Math.max(0.5, Math.min(2.5, zoom + delta * 0.1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Sub-editors call this after mutating a node. */
    public void markChanged() {
        updateStartingSkills();
        rebuild();
    }

    @Override protected void goBack() {
        if (draft == null || editingBaseline == null) { super.goBack(); return; }
        confirmLeavingDraft(draft.save(), editingBaseline, () -> {
            if (parentScreen() == null) { minecraft.setScreen(null); }
            else { minecraft.setScreen(parentScreen()); }
        });
    }
    @Override public void onClose() { goBack(); }
}
