package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntityType;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.MobSetupForm;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Environment(EnvType.CLIENT)
public class MobSetupScreen extends RotasScreen {
    private static final String ALL = "\u0000all";
    private static final String NONE = "";
    private static final int RAIL_W = 180;
    private static final int RAIL_ROW = 24;
    private static final int CARD_W = 116;
    private static final int CARD_H = 124;

    private record Setup(String id, MobSetupForm form) {
    }

    private record Card(Setup setup, int x, int y) {
    }

    private record FolderRow(String folder, int y) {
    }

    private final MobModelCache models = new MobModelCache(4);
    private final List<Setup> setups = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<FolderRow> folderRows = new ArrayList<>();
    private static final Set<String> EMPTY_FOLDERS = new LinkedHashSet<>();
    private String folder = ALL;
    private int gridScroll;
    private int railScroll;
    private long seenRevision = Long.MIN_VALUE;
    private EditBox newFolderBox;
    private EditBox renameBox;
    private long deleteConfirmUntil;
    private String pendingNewMob;

    private Setup pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private int mouseXNow;
    private int mouseYNow;

    public MobSetupScreen(Screen parent) {
        super("Mob Setup", parent);
    }

    private void load() {
        setups.clear();
        for (ClientKernelState.MonsterEntry entry : ClientKernelState.monsters()) {
            MobSetupForm form;
            try {
                form = MobSetupForm.parse(entry.body());
            } catch (RuntimeException malformed) {
                form = new MobSetupForm(new com.google.gson.JsonObject());
            }
            setups.add(new Setup(entry.id(), form));
        }
        setups.sort((a, b) -> title(a).compareToIgnoreCase(title(b)));
        folders().forEach(name -> EMPTY_FOLDERS.remove(name));
    }

    private Set<String> folders() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Setup setup : setups) {
            if (!setup.form().category().isEmpty()) {
                names.add(setup.form().category());
            }
        }
        return names;
    }

    private List<String> railFolders() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        list.addAll(folders());
        EMPTY_FOLDERS.stream().filter(name -> !list.contains(name)).forEach(list::add);
        list.add(NONE);
        return list;
    }

    private List<Setup> shown() {
        if (folder.equals(ALL)) {
            return setups;
        }
        return setups.stream().filter(setup -> setup.form().category().equalsIgnoreCase(folder)).toList();
    }

    private int count(String name) {
        if (name.equals(ALL)) {
            return setups.size();
        }
        return (int) setups.stream().filter(setup -> setup.form().category().equalsIgnoreCase(name)).count();
    }

    private static String folderName(String name) {
        return name.equals(ALL) ? "All mobs" : name.isEmpty() ? "No folder" : name;
    }

    private static String title(Setup setup) {
        List<String> mobs = setup.form().entities();
        return mobs.isEmpty() ? setup.id() : MobModelCache.displayName(mobs.get(0));
    }

private int railX() { return guiLeft + Ui.PAD; }
    private int railY() { return guiTop + 50; }
    private int railBottom() { return guiTop + guiHeight - 96; }
    private int gridX() { return railX() + RAIL_W + Ui.GAP; }
    private int gridY() { return guiTop + 78; }
    private int gridW() { return guiLeft + guiWidth - Ui.PAD - gridX(); }
    private int gridH() { return guiTop + guiHeight - 40 - gridY(); }
    private int columns() { return Math.max(1, (gridW() + Ui.GAP) / (CARD_W + Ui.GAP)); }
    private int visibleRows() { return Math.max(1, (gridH() + Ui.GAP) / (CARD_H + Ui.GAP)); }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 940);
        guiHeight = Ui.fill(height, 540);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        if (seenRevision == Long.MIN_VALUE) {
            send("kernel_refresh");
        }
        seenRevision = ClientKernelState.revision();
        load();
        if (!railFolders().contains(folder)) {
            folder = ALL;
        }

        int footerY = guiTop + guiHeight - 28;
        addBackButton();
        addRenderableWidget(Ui.button(Ui.text("Advanced (JSON)"),
                        button -> minecraft.setScreen(new AdminStudioScreen(this, "monster")))
                .bounds(guiLeft + 64, footerY, 130, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("+ New mob setup"), button -> minecraft.setScreen(
                        PickerScreen.open(ParamKind.ENTITY, this, id -> {
                            if (!id.isEmpty()) {
                                pendingNewMob = id;
                            }
                        }, false)))
                .bounds(guiLeft + guiWidth - Ui.PAD - 150, footerY, 150, 22).build());

        int y = railBottom() + 8;
        newFolderBox = new EditBox(font, railX(), y, RAIL_W - 64, 18, Ui.text("Folder name"));
        newFolderBox.setMaxLength(64);
        newFolderBox.setHint(Ui.text("new folder name"));
        addRenderableWidget(newFolderBox);
        addRenderableWidget(Ui.button(Ui.text("Create"), b -> {
            String name = newFolderBox.getValue().trim();
            if (!name.isEmpty()) {
                EMPTY_FOLDERS.add(name);
                folder = name;
                Sfx.select();
                rebuild();
            }
        }).bounds(railX() + RAIL_W - 60, y - 1, 60, 20).build());

        renameBox = null;
        if (!folder.equals(ALL) && !folder.isEmpty()) {
            renameBox = new EditBox(font, gridX(), guiTop + 50, 180, 18, Ui.text("Rename"));
            renameBox.setMaxLength(64);
            renameBox.setValue(folder);
            addRenderableWidget(renameBox);
            addRenderableWidget(Ui.button(Ui.text("Rename"), b -> renameFolder())
                    .bounds(gridX() + 186, guiTop + 49, 70, 20).build());
            boolean confirming = Util.getMillis() < deleteConfirmUntil;
            addRenderableWidget(Ui.dangerButton(Ui.text(confirming ? "Click again" : "Delete folder"), b -> {
                if (Util.getMillis() < deleteConfirmUntil) {
                    setFolder(shown(), NONE);
                    EMPTY_FOLDERS.remove(folder);
                    folder = ALL;
                    deleteConfirmUntil = 0;
                } else {
                    deleteConfirmUntil = Util.getMillis() + 3000;
                }
                rebuild();
            }).bounds(gridX() + 262, guiTop + 49, 110, 20).build());
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

private void renameFolder() {
        if (renameBox == null) {
            return;
        }
        String name = renameBox.getValue().trim();
        if (name.isEmpty() || name.equals(folder)) {
            return;
        }
        List<Setup> inside = shown();
        if (inside.isEmpty()) {
            EMPTY_FOLDERS.remove(folder);
            EMPTY_FOLDERS.add(name);
        } else {
            setFolder(inside, name);
        }
        folder = name;
        rebuild();
    }

    private void setFolder(List<Setup> moving, String target) {
        if (moving.isEmpty()) {
            return;
        }
        ListTag ids = new ListTag();
        moving.forEach(setup -> ids.add(StringTag.valueOf(setup.id())));
        CompoundTag payload = new CompoundTag();
        payload.put("ids", ids);
        payload.putString("folder", target);
        send("mob_folder_set", payload);
        Sfx.save();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingNewMob != null) {
            String id = pendingNewMob;
            pendingNewMob = null;
            MobSetupForm form = MobSetupForm.create(id);
            if (!folder.equals(ALL)) {
                form.setCategory(folder);
            }
            minecraft.setScreen(new MobSetupEditScreen(null, form, this));
            return;
        }
        if (deleteConfirmUntil != 0 && Util.getMillis() >= deleteConfirmUntil) {
            deleteConfirmUntil = 0;
            rebuild();
        }
        if (seenRevision != ClientKernelState.revision()) {
            onDataRefreshed();
        }
    }

    @Override
    public void onDataRefreshed() {
        rebuild();
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        mouseXNow = mouseX;
        mouseYNow = mouseY;
        models.beginFrame();
        cards.clear();
        Ui.label(graphics, "Folders only group mobs; each keeps its own settings. Drag a card onto a folder to move it.",
                railX(), guiTop + 34, Ui.TEXT_MUTED);
        if (!ClientKernelState.ready()) {
            Ui.labelCentered(graphics, "The RPG content system is still loading.", guiLeft + guiWidth / 2,
                    guiTop + guiHeight / 2, Ui.TEXT_DIM);
            return;
        }
        renderRail(graphics, mouseX, mouseY);
        if (folder.equals(ALL) || folder.isEmpty()) {
            Ui.label(graphics, folderName(folder) + "  (" + count(folder) + ")", gridX(), guiTop + 55, Ui.TEXT_BRIGHT);
        }
        renderGrid(graphics, mouseX, mouseY);
        if (dragging && pressed != null) {
            renderDragGhost(graphics, mouseX, mouseY);
        }
    }

    private void renderRail(GuiGraphics graphics, int mouseX, int mouseY) {
        folderRows.clear();
        Ui.panel(graphics, railX(), railY(), RAIL_W, railBottom() - railY());
        List<String> list = railFolders();
        int rows = Math.max(1, (railBottom() - railY() - 4) / RAIL_ROW);
        railScroll = Math.max(0, Math.min(railScroll, Math.max(0, list.size() - rows)));
        int y = railY() + 2;
        for (int index = railScroll; index < list.size() && index < railScroll + rows; index++) {
            String name = list.get(index);
            boolean active = name.equals(folder);
            boolean hovered = Ui.inside(mouseX, mouseY, railX() + 2, y, RAIL_W - 4, RAIL_ROW - 2);
            boolean dropTarget = dragging && hovered && !name.equals(ALL);
            Ui.rowCard(graphics, railX() + 2, y, RAIL_W - 4, RAIL_ROW - 2, hovered, active || dropTarget);
            String icon = name.equals(ALL) ? "*" : name.isEmpty() ? "-" : "#";
            Ui.label(graphics, icon, railX() + 8, y + 7, Ui.TEXT_MUTED);
            Ui.label(graphics, Ui.truncate(folderName(name), RAIL_W - 50), railX() + 20, y + 7,
                    dropTarget ? Ui.GOOD : active ? Ui.ACCENT : Ui.TEXT_BRIGHT);
            Ui.labelRight(graphics, String.valueOf(count(name)), railX() + RAIL_W - 8, y + 7, Ui.TEXT_MUTED);
            folderRows.add(new FolderRow(name, y));
            y += RAIL_ROW;
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Setup> list = shown();
        if (list.isEmpty()) {
            Ui.labelCentered(graphics, folder.equals(ALL) ? "No mob setups yet. Press + New mob setup."
                            : "Empty folder. Drag cards here from All mobs, or press + New mob setup.",
                    gridX() + gridW() / 2, gridY() + gridH() / 2, Ui.TEXT_MUTED);
            return;
        }
        int columns = columns();
        int maxScroll = Math.max(0, (list.size() + columns - 1) / columns - visibleRows());
        gridScroll = Math.min(gridScroll, maxScroll);
        graphics.enableScissor(gridX(), gridY(), gridX() + gridW(), gridY() + gridH());
        int start = gridScroll * columns;
        for (int index = start; index < Math.min(list.size(), start + visibleRows() * columns); index++) {
            Setup setup = list.get(index);
            int local = index - start;
            int x = gridX() + (local % columns) * (CARD_W + Ui.GAP);
            int y = gridY() + (local / columns) * (CARD_H + Ui.GAP);
            cards.add(new Card(setup, x, y));
            if (dragging && setup == pressed) {
                Ui.rowCard(graphics, x, y, CARD_W, CARD_H, false, false);
                continue;
            }
            renderCard(graphics, setup, x, y, mouseX, mouseY);
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            PixelUi.scrollbar(graphics, gridX() + gridW() - 3, gridY(), 3, gridH(),
                    gridScroll / (float) maxScroll, visibleRows() / (float) (visibleRows() + maxScroll));
        }
    }

    private void renderCard(GuiGraphics graphics, Setup setup, int x, int y, int mouseX, int mouseY) {
        MobSetupForm form = setup.form();
        boolean hovered = !dragging && Ui.inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
        Ui.rowCard(graphics, x, y, CARD_W, CARD_H, hovered, false);
        List<String> mobs = form.entities();
        EntityType<?> type = mobs.isEmpty() ? null : MobModelCache.type(mobs.get(0));
        if (type != null) {
            models.draw(graphics, type, x + CARD_W / 2, y + 70, 56, x + CARD_W / 2f - mouseX, y + 30f - mouseY);
        } else {
            Ui.labelCentered(graphics, "?", x + CARD_W / 2, y + 40, Ui.TEXT_MUTED);
        }
        if (mobs.size() > 1) {
            Ui.tag(graphics, x + CARD_W - 32, y + 5, "+" + (mobs.size() - 1), Ui.ACCENT);
        }
        List<String> zones = form.scopeZones();
        String scope = zones.isEmpty() ? "GLOBAL"
                : zones.size() == 1 ? "ZONE: " + Ui.truncate(MobSetupEditScreen.zoneNames(zones), 52)
                : zones.size() + " ZONES";
        Ui.tag(graphics, x + 5, y + 5, scope, zones.isEmpty() ? Ui.TEXT_MUTED : Ui.GOOD);
        if (folder.equals(ALL) && !form.category().isEmpty()) {
            Ui.labelCentered(graphics, Ui.truncate("# " + form.category(), CARD_W - 8), x + CARD_W / 2, y + 24, Ui.ACCENT);
        }
        Ui.labelCentered(graphics, Ui.truncate(title(setup), CARD_W - 8), x + CARD_W / 2, y + 78, Ui.TEXT_BRIGHT);
        String level = switch (form.strategy()) {
            case "NEAREST_PLAYER" -> "Lv follows player";
            case "FIXED" -> "Lv " + form.fixedLevel();
            default -> "Lv " + form.min() + "-" + form.max();
        };
        Ui.labelCentered(graphics, Ui.truncate(level, CARD_W - 8), x + CARD_W / 2, y + 90, Ui.TEXT_MUTED);
        Ui.labelCentered(graphics, Ui.truncate("HP x" + trim(form.multiplier(MobSetupForm.HEALTH)) + "  XP " + form.baseXp(),
                CARD_W - 8), x + CARD_W / 2, y + 102, Ui.TEXT_MUTED);
        if (hovered) {
            Ui.labelCentered(graphics, "click edit · drag move", x + CARD_W / 2, y + 113, Ui.ACCENT);
        }
    }

    private void renderDragGhost(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        int w = 140;
        Ui.rowCard(graphics, mouseX + 8, mouseY + 4, w, 22, true, true);
        List<String> mobs = pressed.form().entities();
        if (!mobs.isEmpty()) {
            Ui.icon(graphics, MobModelCache.egg(mobs.get(0)), mouseX + 11, mouseY + 7);
        }
        Ui.label(graphics, Ui.truncate(title(pressed), w - 30), mouseX + 30, mouseY + 11, Ui.TEXT_BRIGHT);
        graphics.pose().popPose();
    }

    static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (button == 0) {
            for (FolderRow row : folderRows) {
                if (Ui.inside(mx, my, railX() + 2, row.y(), RAIL_W - 4, RAIL_ROW - 2)) {
                    folder = row.folder();
                    gridScroll = 0;
                    deleteConfirmUntil = 0;
                    Sfx.select();
                    rebuild();
                    return true;
                }
            }
            if (Ui.inside(mx, my, gridX(), gridY(), gridW(), gridH())) {
                for (Card card : cards) {
                    if (Ui.inside(mx, my, card.x(), card.y(), CARD_W, CARD_H)) {
                        pressed = card.setup();
                        pressX = mouseX;
                        pressY = mouseY;
                        dragging = false;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressed != null && button == 0) {
            if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 5) {
                dragging = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressed != null && button == 0) {
            Setup setup = pressed;
            boolean wasDragging = dragging;
            pressed = null;
            dragging = false;
            if (!wasDragging) {
                Sfx.select();
                minecraft.setScreen(new MobSetupEditScreen(setup.id(), setup.form(), this));
                return true;
            }
            for (FolderRow row : folderRows) {
                if (!row.folder().equals(ALL)
                        && Ui.inside((int) mouseX, (int) mouseY, railX() + 2, row.y(), RAIL_W - 4, RAIL_ROW - 2)) {
                    if (!row.folder().equalsIgnoreCase(setup.form().category())) {
                        setFolder(List.of(setup), row.folder());
                    }
                    return true;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside((int) mouseX, (int) mouseY, railX(), railY(), RAIL_W, railBottom() - railY())) {
            railScroll = Math.max(0, railScroll - (int) Math.signum(delta));
            return true;
        }
        if (Ui.inside((int) mouseX, (int) mouseY, gridX(), gridY(), gridW(), gridH())) {
            gridScroll = Math.max(0, gridScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        models.clear();
        super.removed();
    }
}
