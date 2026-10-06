package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.network.AdminResponseReceiver;
import net.schwarz.rotasutils.network.ClientAdminNetwork;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class ConfigReviewScreen extends RotasScreen implements AdminResponseReceiver {
    private final UUID session = UUID.randomUUID();
    private final String action;
    private final CompoundTag payload;
    private String domain;
    private CompoundTag editingBaseline;
    private CompoundTag state = new CompoundTag();
    private long request, sentAt;
    private boolean loaded, pending, reviewed;
    private String message = "Loading current configuration...";

    public ConfigReviewScreen(Screen parent, String action, CompoundTag payload) {
        super("Review configuration", parent); this.action = action; this.payload = payload.copy();
        quick = !action.isEmpty();
    }
    public ConfigReviewScreen(Screen parent, String domain) {
        this(parent, "", new CompoundTag()); this.domain = domain;
    }
    public ConfigReviewScreen(Screen parent, String action, CompoundTag payload, CompoundTag baseline) {
        this(parent, action, payload); editingBaseline = baseline.copy();
    }
    private java.util.function.Consumer<CompoundTag> onApplied;
    private boolean quick;
    public ConfigReviewScreen quick(java.util.function.Consumer<CompoundTag> onApplied) {
        this.quick = true; this.onApplied = onApplied; message = "Saving..."; return this;
    }

    @Override protected void buildContent() {
        guiWidth = Ui.fill(width, 700); guiHeight = Ui.fill(height, 430);
        guiLeft = (width - guiWidth) / 2; guiTop = (height - guiHeight) / 2;
        boolean hasDraft = loaded && state.getLong("generation") >= 0;
        int buttons = 4 + (domain == null && hasDraft ? 1 : 0);
        int col = (guiWidth - 24 - (buttons - 1) * 4) / buttons;
        String[] labels = {domain == null ? "Save Draft" : "Discard Draft", "Review Changes", "Apply", "Back", "Discard Draft"};
        for (int i = 0; i < buttons; i++) {
            int index = i;
            var button = Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(labels[i]), ignored -> {
                if (index == 3) { goBack(); } else if (index == 0 && domain != null) {
                    minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) { request("discard"); } }, net.schwarz.rotasutils.client.screen.Ui.text("Discard saved draft?"), net.schwarz.rotasutils.client.screen.Ui.text("Live values stay unchanged.")));
                } else if (index == 4) {
                    minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) { request("discard"); } }, net.schwarz.rotasutils.client.screen.Ui.text("Discard the saved draft?"), net.schwarz.rotasutils.client.screen.Ui.text("Live values stay unchanged; your open editor keeps its edits.")));
                } else { request(index == 0 ? "stage" : index == 1 ? "review" : "apply"); }
            }).bounds(guiLeft + 12 + i * (col + 4), guiTop + guiHeight - 28, col, 20).build();
            button.active = !pending && (index == 3 || loaded && (index == 0 || state.getLong("generation") >= 0) && (index != 2 || reviewed && state.getBoolean("APPLY")));
            addRenderableWidget(button);
        }
        if (domain != null && loaded && state.getLong("generation") < 0 && state.getBoolean("ROLLBACK")) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Stage previous values"), ignored -> request("restore"))
                    .bounds(guiLeft + 12, guiTop + 55, Math.min(190, guiWidth - 24), 20).build()).active = !pending;
        }
        var lines = state.getList("lines", 8);
        ScrollPanel panel = new ScrollPanel(guiLeft + 12, guiTop + 80, guiWidth - 24, Math.max(24, guiHeight - 122), 22);
        panel.setRows(lines.size(), (g, index, x, y, w, h, hover) -> g.drawString(font,
                font.plainSubstrByWidth(lines.getString(index), w - 8), x + 4, y + 6, Ui.TEXT, false), (index, button) -> {});
        registerPanel(panel);
        if (!loaded && !pending) { request("status"); }
    }

    private void request(String operation) {
        if (pending) { return; }
        var tag = new CompoundTag(); tag.putUUID("ui", session); tag.putString("action", "config_" + operation);
        tag.putString("edit_action", action); tag.put("payload", payload.copy());
        if (domain != null) { tag.putString("domain", domain); }
        tag.putLong("generation", loaded ? state.getLong("generation") : -1);
        if (editingBaseline != null) { tag.put("baseline", editingBaseline.copy()); }
        else if (state.contains("live")) { tag.put("baseline", state.getCompound("live").copy()); }
        try { pending = true; sentAt = System.currentTimeMillis(); request = ClientAdminNetwork.send(tag); reviewed = false; }
        catch (RuntimeException error) { failed(error.getMessage()); }
    }

    @Override public void receive(long id, CompoundTag response) {
        if (id != request || !response.hasUUID("ui") || !session.equals(response.getUUID("ui"))) { return; }
        pending = false; loaded = true; state = response; message = response.getString("message");
        reviewed = response.getBoolean("reviewed");
        if (quick && !sentCommit) {
            sentCommit = true; request(response.getBoolean("APPLY") ? "commit" : "stage"); return;
        }
        if (quick) {
            boolean applied = response.getBoolean("applied");
            if (applied && onApplied != null && response.contains("live")) { onApplied.accept(response.getCompound("live")); }
            report(applied, response.getString("message"), applied ? new net.minecraft.nbt.ListTag() : response.getList("lines", 8));
            goBack();
            return;
        }
        rebuild();
    }
    private boolean sentCommit;
    @Override public void failed(String error) {
        pending = false; message = error;
        if (quick && minecraft != null) { report(false, error, new net.minecraft.nbt.ListTag()); goBack(); return; }
        if (minecraft != null) { rebuild(); }
    }

    private void report(boolean ok, String text, net.minecraft.nbt.ListTag lines) {
        var player = minecraft == null ? null : minecraft.player;
        if (player == null) { return; }
        var style = ok ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED;
        player.displayClientMessage(Ui.text(text == null || text.isBlank() ? (ok ? "Saved." : "Save failed.") : text).withStyle(style), false);
        for (int i = 0; i < Math.min(6, lines.size()); i++) {
            String line = lines.getString(i);
            if (!line.startsWith("WARNING") && !line.equals("No staged differences.")) {
                player.displayClientMessage(Ui.text(" - " + line).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
            }
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!quick || parentScreen() == null) { super.render(graphics, mouseX, mouseY, partialTick); return; }
        parentScreen().render(graphics, -1, -1, partialTick);
        graphics.drawCenteredString(font, Ui.text("Saving..."), width / 2, 8, 0xFFFFFFFF);
    }
    private void rebuild() { clearWidgets(); clearPanels(); buildContent(); }
    @Override public void tick() { if (pending && System.currentTimeMillis() - sentAt > 15000) { failed("Request timed out. Reopen review to check the saved draft before retrying."); } }
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, font.plainSubstrByWidth(message, guiWidth - 24), guiLeft + 12, guiTop + 34, Ui.TEXT, false);
    }
}
