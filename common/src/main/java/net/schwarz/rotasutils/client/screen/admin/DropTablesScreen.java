package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Every drop table in one list: what each monster rank leaves, what each loot grade holds, and the mobs
 * that have a drop of their own. Click a row to edit it; "+ Custom drop for a mob" gives one kind of mob
 * its own table.
 */
@Environment(EnvType.CLIENT)
public class DropTablesScreen extends RotasScreen {
    private static final int ROW_H = 26;
    private static final List<String> RANK_ORDER = List.of("NORMAL", "VETERAN", "ELITE", "CHAMPION", "MINIBOSS",
            "BOSS", "WORLD_BOSS");

    private record Row(String target, String title, String detail, ItemStack icon, boolean heading) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private String pendingMob;

    public DropTablesScreen(Screen parent) {
        super("Drop tables", parent);
    }

    private void load() {
        rows.clear();
        SeasonRules.DropRules drops = ClientState.levelConfig().season().drops;
        rows.add(new Row("", "Monster ranks", "", ItemStack.EMPTY, true));
        for (String rank : RANK_ORDER) {
            SeasonRules.RankDrop rule = drops.ranks.get(rank);
            rows.add(new Row("rank:" + rank, rank, rule == null ? "drops nothing" : summary(rule),
                    new ItemStack(Items.ZOMBIE_HEAD), false));
        }
        rows.add(new Row("", "Loot grades", "", ItemStack.EMPTY, true));
        for (String grade : List.of("common", "medium", "rare", "epic")) {
            SeasonRules.GradeLoot loot = drops.grades.get(grade);
            rows.add(new Row("grade:" + grade, grade, loot == null ? "empty"
                    : loot.minGold + "-" + loot.maxGold + " gold, " + loot.items.length + " item lines",
                    new ItemStack(Items.CHEST), false));
        }
        rows.add(new Row("", "Mobs with their own drop", "", ItemStack.EMPTY, true));
        drops.plain.byEntity.forEach((id, rule) -> rows.add(new Row("entity:" + id, MobModelCache.displayName(id),
                id + "  ·  " + summary(rule), MobModelCache.egg(id), false)));
    }

    private static String summary(SeasonRules.RankDrop rule) {
        return Math.round(rule.coinChance * 100) + "% coins " + rule.coinMin + "-" + rule.coinMax
                + (rule.coinMultiplier != 1 ? " x" + DropTableEditScreen.trim(rule.coinMultiplier) : "")
                + ", " + Math.round(rule.lootChance * 100) + "% loot"
                + (rule.items.length > 0 ? ", " + rule.items.length + " items" : "");
    }

    private int listTop() { return guiTop + 40; }
    private int visibleRows() { return Math.max(1, (guiTop + guiHeight - 44 - listTop()) / ROW_H); }

    @Override
    protected void buildContent() {
        load();
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 420);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
        addBackButton();
        addRenderableWidget(Ui.primaryButton(Ui.text("+ Custom drop for a mob"), b -> minecraft.setScreen(
                PickerScreen.open(ParamKind.ENTITY, this, id -> {
                    if (id != null && !id.isEmpty()) {
                        pendingMob = id;
                    }
                }, false))).bounds(guiLeft + guiWidth - 16 - 180, guiTop + guiHeight - 30, 180, 22).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingMob != null) {
            String id = pendingMob;
            pendingMob = null;
            minecraft.setScreen(new DropTableEditScreen(this, "entity:" + id, "Own drop: " + MobModelCache.displayName(id)));
        }
    }

    @Override
    public void onDataRefreshed() {
        clearWidgets();
        buildContent();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = listTop();
        for (int index = scroll; index < rows.size() && index < scroll + visibleRows(); index++) {
            Row row = rows.get(index);
            if (!row.heading() && Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, ROW_H - 2)) {
                Sfx.select();
                String kind = row.target().startsWith("rank:") ? "Rank drop: " : row.target().startsWith("grade:")
                        ? "Loot grade: " : "Own drop: ";
                minecraft.setScreen(new DropTableEditScreen(this, row.target(), kind + row.title()));
                return true;
            }
            rowY += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scroll - Math.signum(delta)));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Drop tables", guiLeft + 16, guiTop + 14, Ui.TEXT_BRIGHT);
        Ui.label(graphics, "A mob with its own drop uses it; every other monster uses its rank. Coins fall on the ground.",
                guiLeft + 16, guiTop + 26, Ui.TEXT_MUTED);
        int rowY = listTop();
        for (int index = scroll; index < rows.size() && index < scroll + visibleRows(); index++) {
            Row row = rows.get(index);
            if (row.heading()) {
                Ui.sectionHeading(graphics, row.title(), guiLeft + 16, rowY + 10, guiWidth - 32);
            } else {
                boolean hovered = Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, ROW_H - 2);
                Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, ROW_H - 2, hovered, false);
                Ui.icon(graphics, row.icon(), guiLeft + 20, rowY + 4);
                Ui.label(graphics, Ui.truncate(row.title(), 150), guiLeft + 42, rowY + 8, Ui.TEXT_BRIGHT);
                Ui.labelRight(graphics, Ui.truncate(row.detail(), guiWidth - 230), guiLeft + guiWidth - 24, rowY + 8,
                        Ui.TEXT_MUTED);
            }
            rowY += ROW_H;
        }
    }
}
