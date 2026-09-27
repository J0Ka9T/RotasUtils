package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pick the mob whose drops you want to look at.
 *
 * <p>The list is the client's own entity registry, so every mob in the pack is here whether or not this
 * mod has ever heard of it, and no packet is needed to browse. Choosing one asks the server for that
 * mob's real drops.</p>
 */
@Environment(EnvType.CLIENT)
public class MobDropListScreen extends RotasScreen {
    private static final int ROWS = 9;
    private record Mob(String id, String name, ItemStack icon) {
    }

    private final List<Mob> all = new ArrayList<>();
    private List<Mob> shown = List.of();
    private EditBox search;
    private String query = "";
    private int scroll;

    public MobDropListScreen(Screen parent) {
        super(L.t("rotasutils.drops.pick_title"), parent);
    }

    private void loadMobs() {
        if (!all.isEmpty()) {
            return;
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null) {
                continue;
            }
            // Only things that can die and leave something; a projectile or a boat never drops loot.
            if (type.getCategory() == net.minecraft.world.entity.MobCategory.MISC) {
                continue;
            }
            ItemStack egg = new ItemStack(SpawnEggItem.byId(type) == null ? Items.EGG : SpawnEggItem.byId(type));
            all.add(new Mob(key.toString(), type.getDescription().getString(), egg));
        }
        all.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
    }

    private void filter() {
        String text = query.toLowerCase(Locale.ROOT);
        List<Mob> matches = new ArrayList<>();
        for (Mob mob : all) {
            if (text.isBlank() || mob.name().toLowerCase(Locale.ROOT).contains(text)
                    || mob.id().toLowerCase(Locale.ROOT).contains(text)) {
                matches.add(mob);
            }
        }
        shown = matches;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - ROWS)));
    }

    @Override
    protected void buildContent() {
        loadMobs();
        filter();
        guiWidth = Ui.fill(width, 480);
        guiHeight = Ui.fill(height, 340);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        search = new EditBox(font, guiLeft + 24, guiTop + 44, guiWidth - 48, 14, Ui.text("Search"));
        search.setBordered(false);
        search.setHint(Ui.text(L.t("rotasutils.drops.pick_search")));
        search.setValue(query);
        search.setTextColor(Ui.TEXT_BRIGHT);
        search.setResponder(value -> {
            query = value;
            filter();
        });
        addRenderableWidget(search);
        addBackButton();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = guiTop + 70;
        for (int index = scroll; index < shown.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, 24)) {
                CompoundTag payload = new CompoundTag();
                payload.putString("entity", shown.get(index).id());
                send("drop_scan", payload);
                return true;
            }
            rowY += 26;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, shown.size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.searchFrame(graphics, guiLeft + 16, guiTop + 36, guiWidth - 32, 28, search != null && search.isFocused());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, L.t("rotasutils.drops.pick_title"), guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, L.t("rotasutils.drops.filter_state",
                        L.t(ClientState.dropFilterEnabled() ? "rotasutils.drops.on" : "rotasutils.drops.off")),
                guiLeft + guiWidth - 16, guiTop + 16, ClientState.dropFilterEnabled() ? Ui.GOOD : Ui.TEXT_MUTED);

        int rowY = guiTop + 70;
        for (int index = scroll; index < shown.size() && index < scroll + ROWS; index++) {
            Mob mob = shown.get(index);
            boolean hovered = Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, 24);
            Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, 24, hovered, false);
            Ui.icon(graphics, mob.icon(), guiLeft + 22, rowY + 4);
            Ui.label(graphics, Ui.truncate(mob.name(), 160), guiLeft + 44, rowY + 8, Ui.TEXT_BRIGHT);
            Ui.label(graphics, Ui.truncate(mob.id(), guiWidth - 320), guiLeft + 210, rowY + 8, Ui.TEXT_MUTED);
            int blocked = ClientState.dropBlockedCount(mob.id());
            if (blocked > 0) {
                Ui.labelRight(graphics, L.t("rotasutils.drops.blocked_count", blocked),
                        guiLeft + guiWidth - 24, rowY + 8, Ui.WARN);
            }
            rowY += 26;
        }
        Ui.labelRight(graphics, shown.size() + " / " + all.size(),
                guiLeft + guiWidth - 16, guiTop + guiHeight - 22, Ui.TEXT_MUTED);
    }
}
