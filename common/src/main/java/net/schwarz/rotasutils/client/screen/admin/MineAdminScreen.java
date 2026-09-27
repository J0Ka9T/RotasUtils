package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ButtonRow;
import net.schwarz.rotasutils.data.ParamSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mining sites without commands. Left: every site. Right: the chosen site's settings and loot, plus
 * the in-world tools - add or remove the block you are looking at, scan an area around you for one
 * block type, refill now. The server re-sends this screen after every action.
 */
@Environment(EnvType.CLIENT)
public class MineAdminScreen extends RotasScreen {
    private static final int ROW = 24;
    private static final int LIST_W = 150;

    private final CompoundTag state;
    private String selected;
    private int scroll;
    /** Unsaved form text by field, so a rebuild (resize, picker) keeps what was typed. */
    private final Map<String, String> form = new LinkedHashMap<>();
    private String scanBlock = "minecraft:iron_ore";
    private String scanRadius = "6";

    public MineAdminScreen(CompoundTag payload) {
        super("Mining sites", new AdminMenuScreen("ADVANCED"));
        this.state = payload == null ? new CompoundTag() : payload;
        this.selected = state.getString("select");
        if (selected.isEmpty() && !sites().isEmpty()) selected = sites().getCompound(0).getString("id");
        loadForm();
    }

    private ListTag sites() {
        return state.getList("sites", Tag.TAG_COMPOUND);
    }

    private CompoundTag site() {
        for (int i = 0; i < sites().size(); i++) {
            if (sites().getCompound(i).getString("id").equals(selected)) return sites().getCompound(i);
        }
        return null;
    }

    private void loadForm() {
        form.clear();
        CompoundTag site = site();
        if (site == null) return;
        form.put("name", site.getString("name"));
        form.put("respawn", Integer.toString(site.getInt("respawn")));
        form.put("gold_min", Long.toString(site.getLong("gold_min")));
        form.put("gold_max", Long.toString(site.getLong("gold_max")));
        form.put("xp", Long.toString(site.getLong("xp")));
        form.put("limit", Integer.toString(site.getInt("limit")));
        form.put("depleted_block", site.getString("depleted_block"));
        StringBuilder loot = new StringBuilder();
        for (Tag line : site.getList("loot", Tag.TAG_STRING)) {
            if (loot.length() > 0) loot.append('\n');
            loot.append(line.getAsString());
        }
        form.put("loot", loot.toString());
    }

    private int listTop() {
        return guiTop + 44;
    }

    private int visibleRows() {
        return Math.max(1, (guiHeight - 44 - 64) / ROW);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 640);
        guiHeight = Ui.fill(height, 380);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int footer = guiTop + guiHeight - 30;
        boolean picked = site() != null;
        String pickFirst = "Click a site in the list first";

        int[][] row = ButtonRow.fit(guiLeft + 16, guiWidth - 32, 54, 90, 70, 80, 90);
        place(Ui.button(net.schwarz.rotasutils.client.screen.L.c("rotasutils.common.back"), b -> goBack()), row[0], footer, true, "");
        place(Ui.primaryButton(Ui.text("+ New site"), b -> minecraft.setScreen(new KeyPromptScreen(this,
                "Site id (a-z 0-9 _) - it is made in the dimension you stand in", null, id -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("id", id.toLowerCase(java.util.Locale.ROOT));
                    send("mine_create", payload);
                }))), row[1], footer, true, "");
        place(Ui.dangerButton(Ui.text("Delete"), b -> confirmDelete()), row[2], footer, picked, pickFirst);
        place(Ui.button(Ui.text("Refill now"), b -> send("mine_refill", idTag())), row[3], footer, picked, pickFirst);
        place(Ui.primaryButton(Ui.text("Save"), b -> save()), row[4], footer, picked, pickFirst);
        if (!picked) return;

        // Form: two columns of short fields, then loot, then in-world tools.
        int formX = guiLeft + 16 + LIST_W + 12;
        int formW = guiLeft + guiWidth - 16 - formX;
        int half = (formW - 8) / 2;
        int y = guiTop + 44;
        field("name", "Name", formX, y, formW, 64);
        y += 30;
        field("respawn", "Respawn (sec)", formX, y, half, 7);
        field("limit", "Per player / day (0 = no limit)", formX + half + 8, y, half, 6);
        y += 30;
        int third = (formW - 16) / 3;
        field("gold_min", "Gold min", formX, y, third, 7);
        field("gold_max", "Gold max", formX + third + 8, y, third, 7);
        field("xp", "EXP", formX + 2 * (third + 8), y, third, 7);
        y += 30;
        field("depleted_block", "Block while empty", formX, y, formW - 24, 64);
        addRenderableWidget(Ui.button(Ui.text("…"), b -> minecraft.setScreen(new PickerScreen(ParamSpec.ParamKind.BLOCK, this,
                picked2 -> { if (picked2 != null && !picked2.isBlank()) form.put("depleted_block", picked2); })))
                .bounds(formX + formW - 20, y, 20, 18).build());
        y += 30;
        int lootH = Math.max(20, guiTop + guiHeight - 30 - 26 - 6 - (y + 10));
        MultiLineEditBox loot = new MultiLineEditBox(font, formX, y + 10, formW, lootH, Ui.text("minecraft:diamond 1-2 @0.1"),
                Ui.text("Loot"));
        loot.setCharacterLimit(1200);
        loot.setValue(form.getOrDefault("loot", ""));
        loot.setValueListener(value -> form.put("loot", value));
        addRenderableWidget(loot);

        // In-world tools: act on where the admin stands or looks.
        int toolsY = guiTop + guiHeight - 30 - 26;
        int[][] tools = ButtonRow.fit(formX, formW, 92, 92, 120, 40, 24, 64);
        place(Ui.button(Ui.text("+ Block I look at"), b -> send("mine_add_look", idTag())), tools[0], toolsY, true, "");
        place(Ui.button(Ui.text("- Block I look at"), b -> send("mine_unadd_look", idTag())), tools[1], toolsY, true, "");
        EditBox block = new EditBox(font, tools[2][0], toolsY + 1, tools[2][1], 20, Ui.text("Block"));
        block.setMaxLength(64);
        block.setValue(scanBlock);
        block.setResponder(value -> scanBlock = value);
        addRenderableWidget(block);
        EditBox radius = new EditBox(font, tools[3][0], toolsY + 1, tools[3][1], 20, Ui.text("Radius"));
        radius.setMaxLength(2);
        radius.setValue(scanRadius);
        radius.setResponder(value -> scanRadius = value);
        radius.setTooltip(Tooltip.create(Ui.text("Scan radius around you, 1-16 blocks")));
        addRenderableWidget(radius);
        place(Ui.button(Ui.text("…"), b -> minecraft.setScreen(new PickerScreen(ParamSpec.ParamKind.BLOCK, this,
                p -> { if (p != null && !p.isBlank()) scanBlock = p; }))), tools[4], toolsY, true, "");
        var scan = place(Ui.primaryButton(Ui.text("Scan"), b -> {
            CompoundTag payload = idTag();
            payload.putString("block", scanBlock);
            payload.putInt("radius", parse(scanRadius, 6));
            send("mine_scan", payload);
        }), tools[5], toolsY, true, "");
        scan.setTooltip(Tooltip.create(Ui.text("Add every block of this type within the radius around you")));
    }

    private void field(String key, String label, int x, int y, int w, int max) {
        EditBox box = new EditBox(font, x, y + 10, w, 18, Ui.text(label));
        box.setMaxLength(max);
        box.setValue(form.getOrDefault(key, ""));
        box.setResponder(value -> form.put(key, value));
        addRenderableWidget(box);
    }

    private net.schwarz.rotasutils.client.screen.RotasButton place(
            net.schwarz.rotasutils.client.screen.RotasButton.Builder builder, int[] slot, int y, boolean active, String whyNot) {
        var button = builder.bounds(slot[0], y, slot[1], 22).build();
        button.active = active;
        if (!active && !whyNot.isEmpty()) button.setTooltip(Tooltip.create(Ui.text(whyNot)));
        addRenderableWidget(button);
        return button;
    }

    private CompoundTag idTag() {
        CompoundTag payload = new CompoundTag();
        payload.putString("id", selected);
        return payload;
    }

    private void save() {
        CompoundTag payload = idTag();
        payload.putString("name", form.getOrDefault("name", ""));
        payload.putInt("respawn", parse(form.get("respawn"), 300));
        payload.putLong("gold_min", parse(form.get("gold_min"), 0));
        payload.putLong("gold_max", parse(form.get("gold_max"), 0));
        payload.putLong("xp", parse(form.get("xp"), 0));
        payload.putInt("limit", parse(form.get("limit"), 0));
        payload.putString("depleted_block", form.getOrDefault("depleted_block", ""));
        ListTag loot = new ListTag();
        for (String line : form.getOrDefault("loot", "").split("\n")) {
            if (!line.isBlank()) loot.add(StringTag.valueOf(line.trim()));
        }
        payload.put("loot", loot);
        send("mine_save", payload);
    }

    private void confirmDelete() {
        String id = selected;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                CompoundTag payload = new CompoundTag();
                payload.putString("id", id);
                send("mine_delete", payload);
            } else {
                minecraft.setScreen(this);
            }
        }, Ui.text("Delete mining site " + id + "?"), Ui.text("Its blocks stay in the world but stop respawning.")));
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ListTag sites = sites();
        int rowY = listTop();
        for (int index = scroll; index < sites.size() && index < scroll + visibleRows(); index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, LIST_W, ROW - 2)) {
                selected = sites.getCompound(index).getString("id");
                loadForm();
                rebuildWidgets();
                return true;
            }
            rowY += ROW;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < guiLeft + 16 + LIST_W) {
            scroll = (int) Math.max(0, Math.min(Math.max(0, sites().size() - visibleRows()), scroll - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Mining sites (" + sites().size() + ")", guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate("1. + New site  2. Look at ore blocks and press + Block, or Scan  3. Set gold / EXP / loot, Save",
                guiWidth - 32), guiLeft + 16, guiTop + 28, Ui.TEXT_MUTED);
        ListTag sites = sites();
        int rowY = listTop();
        for (int index = scroll; index < sites.size() && index < scroll + visibleRows(); index++) {
            CompoundTag site = sites.getCompound(index);
            boolean isSelected = site.getString("id").equals(selected);
            Ui.rowCard(graphics, guiLeft + 16, rowY, LIST_W, ROW - 2,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, LIST_W, ROW - 2), isSelected);
            Ui.label(graphics, Ui.truncate(site.getString("name"), LIST_W - 50), guiLeft + 22, rowY + 7,
                    isSelected ? Ui.ACCENT : Ui.TEXT_BRIGHT);
            Ui.labelRight(graphics, (site.getInt("nodes") - site.getInt("depleted")) + "/" + site.getInt("nodes"),
                    guiLeft + 16 + LIST_W - 6, rowY + 7, Ui.TEXT_MUTED);
            rowY += ROW;
        }
        if (sites.isEmpty()) {
            Ui.label(graphics, "No sites yet", guiLeft + 20, listTop() + 8, Ui.TEXT_MUTED);
        }
        CompoundTag site = site();
        if (site == null) {
            Ui.label(graphics, "Press + New site to make your first mine.", guiLeft + 16 + LIST_W + 12, listTop() + 8, Ui.TEXT_MUTED);
            return;
        }
        int formX = guiLeft + 16 + LIST_W + 12;
        int formW = guiLeft + guiWidth - 16 - formX;
        int half = (formW - 8) / 2;
        int third = (formW - 16) / 3;
        int y = guiTop + 44;
        Ui.label(graphics, "Name", formX, y, Ui.TEXT_DIM);
        Ui.labelRight(graphics, site.getString("id") + " · " + site.getString("dimension"), formX + formW, y, Ui.TEXT_FAINT);
        y += 30;
        Ui.label(graphics, "Respawn (seconds)", formX, y, Ui.TEXT_DIM);
        Ui.label(graphics, "Per player / day (0 = no limit)", formX + half + 8, y, Ui.TEXT_DIM);
        y += 30;
        Ui.label(graphics, "Gold min", formX, y, Ui.TEXT_DIM);
        Ui.label(graphics, "Gold max", formX + third + 8, y, Ui.TEXT_DIM);
        Ui.label(graphics, "EXP per block", formX + 2 * (third + 8), y, Ui.TEXT_DIM);
        y += 30;
        Ui.label(graphics, "Block shown while empty", formX, y, Ui.TEXT_DIM);
        y += 30;
        Ui.label(graphics, "Loot - one per line: minecraft:diamond 1-2 @0.1  (@ = chance, max 12)", formX, y, Ui.TEXT_DIM);
    }
}
