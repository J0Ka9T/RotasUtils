package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MobDropScreen extends RotasScreen {
    private static final int ROWS = 9;
    private record Row(String item, int max, double chance, String origin, boolean blocked, boolean blockedEverywhere) {
    }

    private final CompoundTag state;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    public MobDropScreen(CompoundTag payload) {
        super(L.t("rotasutils.drops.mob_title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        ListTag list = state.getList("drops", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag row = list.getCompound(index);
            rows.add(new Row(row.getString("item"), row.getInt("max"), row.getDouble("chance"),
                    row.getString("origin"), row.getBoolean("blocked"), row.getBoolean("blocked_everywhere")));
        }
    }

    private String entity() {
        return state.getString("entity");
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 500);
        guiHeight = Ui.fill(height, 340);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        addRenderableWidget(Ui.button(L.c("rotasutils.drops.back_to_list"),
                button -> minecraft.setScreen(new MobDropListScreen(null)))
                .bounds(guiLeft + 16, guiTop + guiHeight - 34, 150, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("Own drop table..."), button -> minecraft.setScreen(
                new DropTableEditScreen(this, "entity:" + entity(), "Own drop: " + state.getString("entity_name"))))
                .bounds(guiLeft + guiWidth / 2 - 70, guiTop + guiHeight - 34, 140, 22).build());
        addRenderableWidget(Ui.button(L.c("rotasutils.drops.allow_all"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("entity", entity());
            send("drop_clear_entity", payload);
        }).bounds(guiLeft + guiWidth - 176, guiTop + guiHeight - 34, 160, 22).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = guiTop + 62;
        for (int index = scroll; index < rows.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, 24)) {
                Row row = rows.get(index);
                if (row.blockedEverywhere()) {
                    return true;
                }
                CompoundTag payload = new CompoundTag();
                payload.putString("entity", entity());
                payload.putString("item", row.item());
                payload.putBoolean("drops", row.blocked());
                send("drop_toggle_entity", payload);
                return true;
            }
            rowY += 26;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, rows.size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, L.t("rotasutils.drops.mob_header", state.getString("entity_name")),
                guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, L.t("rotasutils.drops.mob_hint"), guiLeft + 16, guiTop + 32, Ui.TEXT_MUTED);
        if (!state.getBoolean("filter_enabled")) {
            Ui.labelRight(graphics, L.t("rotasutils.drops.filter_off_warning"),
                    guiLeft + guiWidth - 16, guiTop + 16, Ui.WARN);
        }

        if (rows.isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.drops.mob_empty"),
                    guiLeft + guiWidth / 2, guiTop + 90, Ui.TEXT_MUTED);
            return;
        }
        int rowY = guiTop + 62;
        for (int index = scroll; index < rows.size() && index < scroll + ROWS; index++) {
            Row row = rows.get(index);
            boolean hovered = Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, 24);
            Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, 24, hovered, false);
            ItemStack stack = stackOf(row.item());
            Ui.icon(graphics, stack, guiLeft + 22, rowY + 4);
            int colour = row.blocked() || row.blockedEverywhere() ? Ui.TEXT_MUTED : Ui.TEXT_BRIGHT;
            Ui.label(graphics, Ui.truncate(stack.isEmpty() ? row.item() : stack.getHoverName().getString(), 150),
                    guiLeft + 44, rowY + 8, colour);
            Ui.label(graphics, chanceText(row), guiLeft + 210, rowY + 8, Ui.TEXT_MUTED);
            String state = row.blockedEverywhere() ? L.t("rotasutils.drops.state_global")
                    : row.blocked() ? L.t("rotasutils.drops.state_off") : L.t("rotasutils.drops.state_on");
            Ui.labelRight(graphics, state, guiLeft + guiWidth - 24, rowY + 8,
                    row.blockedEverywhere() ? Ui.WARN : row.blocked() ? Ui.BAD : Ui.GOOD);
            rowY += 26;
        }
    }

    private static String chanceText(Row row) {
        if (row.chance() < 0) {
            return L.t("rotasutils.drops.added_by_admin");
        }
        long percent = Math.round(row.chance() * 100);
        return (percent < 1 ? "<1" : String.valueOf(percent)) + "%  x" + Math.max(1, row.max());
    }

    private static ItemStack stackOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        var item = location == null ? null : BuiltInRegistries.ITEM.get(location);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
