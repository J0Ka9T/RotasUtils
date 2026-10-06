package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

@Environment(EnvType.CLIENT)
public class BestiaryScreen extends RotasScreen {
    private static final int ROWS = 9;
    private final CompoundTag state;
    private int scroll;

    public BestiaryScreen(CompoundTag payload) {
        super(L.t("rotasutils.bestiary.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private ListTag entries() {
        return state.getList("entries", Tag.TAG_COMPOUND);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 540);
        guiHeight = Ui.fill(height, 340);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        addBackButton();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ListTag entries = entries();
        int half = guiWidth / 2;
        int rowY = guiTop + 46;
        for (int index = scroll; index < entries.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, half - 24, 26)) {
                CompoundTag payload = new CompoundTag();
                payload.putString("entity", entries.getCompound(index).getString("entity"));
                send("open_bestiary", payload);
                return true;
            }
            rowY += 28;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, entries().size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        int half = guiWidth / 2;
        Ui.parchment(graphics, guiLeft + 10, guiTop + 38, half - 14, guiHeight - 80, false);
        Ui.parchment(graphics, guiLeft + half + 4, guiTop + 38, half - 14, guiHeight - 80, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.scaledLabel(graphics, L.t("rotasutils.bestiary.title"), guiLeft + 16, guiTop + 14, 1.3f, Ui.PARCHMENT_ALT);
        ListTag entries = entries();
        long[] tiers = state.getLongArray("tiers");
        Ui.labelRight(graphics, L.t("rotasutils.bestiary.count", entries.size()),
                guiLeft + guiWidth - 16, guiTop + 20, Ui.PARCHMENT_ALT);

        int half = guiWidth / 2;
        if (entries.isEmpty()) {
            Ui.wrapped(graphics, L.t("rotasutils.bestiary.empty"), guiLeft + 20, guiTop + 50, half - 40, Ui.INK_FADE);
        }
        String selected = state.getString("selected");
        int rowY = guiTop + 46;
        for (int index = scroll; index < entries.size() && index < scroll + ROWS; index++) {
            CompoundTag row = entries.getCompound(index);
            String entity = row.getString("entity");
            boolean chosen = entity.equals(selected);
            Ui.rowCard(graphics, guiLeft + 16, rowY, half - 24, 26, chosen,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, half - 24, 26));
            Ui.icon(graphics, egg(entity), guiLeft + 20, rowY + 5);
            Ui.label(graphics, Ui.truncate(name(entity), half - 120), guiLeft + 40, rowY + 4, Ui.INK);
            Ui.label(graphics, stars(row.getInt("tier"), tiers.length), guiLeft + 40, rowY + 15, Ui.WAX);
            Ui.labelRight(graphics, String.valueOf(row.getLong("kills")), guiLeft + half - 14, rowY + 9, Ui.INK_SOFT);
            rowY += 28;
        }

        renderPage(graphics, selected, tiers);
    }

    private void renderPage(GuiGraphics graphics, String entity, long[] tiers) {
        int x = guiLeft + guiWidth / 2 + 12;
        int width = guiWidth / 2 - 30;
        if (entity.isBlank()) {
            Ui.wrapped(graphics, L.t("rotasutils.bestiary.pick"), x, guiTop + 50, width, Ui.INK_FADE);
            return;
        }
        CompoundTag row = find(entity);
        long kills = row == null ? 0 : row.getLong("kills");
        int tier = row == null ? 0 : row.getInt("tier");
        Ui.icon(graphics, egg(entity), x, guiTop + 46);
        Ui.scaledLabel(graphics, Ui.truncate(name(entity), width - 30), x + 22, guiTop + 49, 1.15f, Ui.INK);
        int y = guiTop + 70;
        Ui.label(graphics, L.t("rotasutils.bestiary.kills", kills), x, y, Ui.INK_SOFT);
        y += 12;
        Ui.label(graphics, L.t("rotasutils.bestiary.bonus",
                        Math.round(tier * state.getDouble("damage_per_tier") * 100),
                        Math.round(tier * state.getDouble("loot_per_tier") * 100)),
                x, y, tier > 0 ? Ui.INK_GOOD : Ui.INK_FADE);
        y += 12;
        if (tier < tiers.length) {
            Ui.label(graphics, L.t("rotasutils.bestiary.next", tiers[tier] - kills, tier + 1), x, y, Ui.INK_FADE);
        } else {
            Ui.label(graphics, L.t("rotasutils.bestiary.mastered"), x, y, Ui.WAX);
        }
        y += 18;

        ListTag drops = state.getList("drops", Tag.TAG_COMPOUND);
        if (!state.contains("drops")) {
            Ui.wrapped(graphics, L.t("rotasutils.bestiary.drops_hidden", Math.max(1, state.getInt("reveal_at"))),
                    x, y, width, Ui.INK_FADE);
            return;
        }
        Ui.label(graphics, L.t("rotasutils.bestiary.drops"), x, y, Ui.INK);
        y += 14;
        if (drops.isEmpty()) {
            Ui.label(graphics, L.t("rotasutils.drops.mob_empty"), x, y, Ui.INK_FADE);
            return;
        }
        for (int index = 0; index < drops.size() && y < guiTop + guiHeight - 60; index++) {
            CompoundTag drop = drops.getCompound(index);
            ItemStack stack = item(drop.getString("item"));
            Ui.icon(graphics, stack, x, y - 3);
            long percent = Math.round(drop.getDouble("chance") * 100);
            String line = Ui.truncate(stack.getHoverName().getString(), width - 80);
            Ui.label(graphics, line, x + 20, y + 2, drop.getBoolean("blocked") ? Ui.INK_FADE : Ui.INK);
            Ui.labelRight(graphics, (percent < 1 ? "<1" : String.valueOf(percent)) + "%",
                    x + width, y + 2, Ui.INK_SOFT);
            y += 18;
        }
    }

    private CompoundTag find(String entity) {
        ListTag entries = entries();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.getCompound(index).getString("entity").equals(entity)) {
                return entries.getCompound(index);
            }
        }
        return null;
    }

    private static String stars(int tier, int total) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < total; index++) {
            text.append(index < tier ? "★" : "☆");
        }
        return text.toString();
    }

    private static String name(String entity) {
        EntityType<?> type = type(entity);
        return type == null ? entity : type.getDescription().getString();
    }

    private static ItemStack egg(String entity) {
        EntityType<?> type = type(entity);
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        return new ItemStack(egg == null ? Items.BOOK : egg);
    }

    private static EntityType<?> type(String entity) {
        ResourceLocation id = ResourceLocation.tryParse(entity);
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    private static ItemStack item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(location));
    }
}
