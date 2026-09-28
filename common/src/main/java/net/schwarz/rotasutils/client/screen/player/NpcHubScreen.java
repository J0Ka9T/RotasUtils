package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One screen for every service NPC (smith, alchemist, inn, priest, bank, bounties, collector...). The server sends
 * the entries with their price and whether they are open to this player right now; a paid entry takes two clicks.
 */
@Environment(EnvType.CLIENT)
public class NpcHubScreen extends RotasScreen {
    private static final int ROW = 34;
    /** Scroll kept per NPC, so buying ten things in a row does not jump back to the top each time. */
    private static final Map<String, Integer> SCROLL = new HashMap<>();

    private record Row(boolean header, String section, CompoundTag entry) {
    }

    private final CompoundTag payload;
    private final String npc;
    private final List<Row> rows = new ArrayList<>();
    private ScrollPanel list;
    private String confirmId = "";
    private long confirmUntil;

    public NpcHubScreen(CompoundTag payload) {
        super(payload.getString("name"), null);
        this.payload = payload;
        this.npc = payload.getString("npc");
        String section = null;
        ListTag entries = payload.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String entrySection = entry.getString("section");
            if (!entrySection.equals(section)) {
                section = entrySection;
                if (!section.isEmpty()) rows.add(new Row(true, section, entry));
            }
            rows.add(new Row(false, section, entry));
        }
        setHeader(payload.getString("name") + " · " + payload.getString("role_name"));
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 400);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        addBackButton();
        int top = listTop();
        list = new ScrollPanel(guiLeft + Ui.PAD, top, guiWidth - Ui.PAD * 2, guiTop + guiHeight - Ui.PAD - top, ROW)
                .withoutBackground().rowHitInsets(1, 2);
        list.setRows(rows.size(), this::renderRow, this::clickRow);
        list.setScroll(SCROLL.getOrDefault(npc, 0));
        registerPanel(list);
    }

    private int listTop() {
        int buffs = payload.getList("buffs", Tag.TAG_STRING).size();
        return guiTop + 70 + (buffs > 0 ? 12 : 0);
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        Row row = rows.get(index);
        if (row.header()) {
            Ui.sectionHeading(graphics, row.section(), x + 2, y + h - 14, w - 10);
            return;
        }
        CompoundTag entry = row.entry();
        String kind = entry.getString("kind");
        String blocked = entry.getString("blocked");
        boolean info = kind.equals("info");
        boolean open = !info && blocked.isEmpty();
        Ui.rowCard(graphics, x, y, w - 6, h - 3, hovered && open, confirming(entry));
        Ui.icon(graphics, ItemStack.of(entry.getCompound("icon")), x + 8, y + 8);
        long cost = entry.getLong("cost");
        String right = confirming(entry) ? "กดอีกครั้งเพื่อยืนยัน" : !blocked.isEmpty() ? blocked
                : cost > 0 ? Currencies.amount(cost) : info ? "" : kind.equals("service") ? "ฟรี" : "เปิด ›";
        int rightColor = confirming(entry) ? Ui.WARN : !blocked.isEmpty() ? Ui.BAD
                : cost > 0 ? (payload.getLong("balance") >= cost ? 0xFFE3A857 : Ui.BAD) : Ui.GOOD;
        int rightW = right.isEmpty() ? 0 : font.width(right) + 10;
        Ui.label(graphics, Ui.truncate(entry.getString("title"), w - 44 - rightW), x + 30, y + 6,
                open || info ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
        Ui.label(graphics, Ui.truncate(entry.getString("desc"), w - 44), x + 30, y + 18, Ui.TEXT_MUTED);
        if (!right.isEmpty()) Ui.labelRight(graphics, right, x + w - 14, y + 6, rightColor);
    }

    private boolean confirming(CompoundTag entry) {
        return !confirmId.isEmpty() && confirmId.equals(entry.getString("id")) && Util.getMillis() < confirmUntil;
    }

    private void clickRow(int index, int button) {
        if (index < 0 || index >= rows.size() || rows.get(index).header()) return;
        CompoundTag entry = rows.get(index).entry();
        String kind = entry.getString("kind");
        if (kind.equals("info") || !entry.getString("blocked").isEmpty()) {
            Sfx.select();
            return;
        }
        String id = entry.getString("id");
        SCROLL.put(npc, list.scroll());
        if (entry.getBoolean("confirm") && !confirming(entry)) {
            confirmId = id;
            confirmUntil = Util.getMillis() + 3000;
            Sfx.select();
            return;
        }
        switch (kind) {
            case "screen" -> {
                Sfx.page();
                net.schwarz.rotasutils.client.screen.ScreenRouter.open(id, new CompoundTag());
            }
            case "action" -> {
                CompoundTag request = new CompoundTag();
                request.putString("npc", npc);
                send(id, request);
                Sfx.page();
            }
            default -> {
                if (entry.getLong("cost") > 0 && !confirming(entry)) {
                    confirmId = id;
                    confirmUntil = Util.getMillis() + 3000;
                    Sfx.select();
                    return;
                }
                confirmId = "";
                CompoundTag request = new CompoundTag();
                request.putString("npc", npc);
                request.putString("service", id);
                send("npc_service", request);
                Sfx.commit();
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int y = guiTop + 32;
        for (String line : Ui.wrap("“" + payload.getString("greeting") + "”", w - 190)) {
            Ui.label(graphics, line, x, y, Ui.TEXT_MUTED);
            y += 11;
        }
        String balance = "ทอง " + Currencies.amount(payload.getLong("balance"));
        int bw = font.width(balance) + 12;
        PixelUi.fill(graphics, guiLeft + guiWidth - Ui.PAD - bw, guiTop + 30, bw, 16, 1, RotasTheme.SURFACE_HIGH);
        Ui.label(graphics, balance, guiLeft + guiWidth - Ui.PAD - bw + 6, guiTop + 34, 0xFFE3A857);
        String friendship = payload.getString("friendship");
        if (!friendship.isEmpty()) Ui.labelRight(graphics, friendship, guiLeft + guiWidth - Ui.PAD, guiTop + 50, 0xFFF08CC8);
        ListTag buffs = payload.getList("buffs", Tag.TAG_STRING);
        if (!buffs.isEmpty()) {
            StringBuilder text = new StringBuilder("สถานะ: ");
            for (int i = 0; i < buffs.size(); i++) text.append(i > 0 ? " · " : "").append(buffs.getString(i));
            Ui.label(graphics, Ui.truncate(text.toString(), w), x, listTop() - 14, Ui.GOOD);
        }
        if (rows.isEmpty()) Ui.labelCentered(graphics, "วันนี้ไม่มีบริการ", guiLeft + guiWidth / 2, listTop() + 30, Ui.TEXT_MUTED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!confirmId.isEmpty() && Util.getMillis() >= confirmUntil) confirmId = "";
    }

    @Override
    public void removed() {
        if (list != null) SCROLL.put(npc, list.scroll());
        super.removed();
    }
}
