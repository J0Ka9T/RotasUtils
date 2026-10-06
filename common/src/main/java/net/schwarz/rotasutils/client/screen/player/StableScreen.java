package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class StableScreen extends RotasScreen {
    private static final int ROW = 40;
    private static final String[] SKILLS = {"ความเร็ว", "กระโดด", "พลังชีวิต", "ความผูกพัน"};
    private static final int[] SKILL_MAX = {5, 5, 5, 11};
    private static final int[] SKILL_COLORS = {0xFF7FD1C7, 0xFFE0AC4C, 0xFF86C05C, 0xFFE2A0C8};
    private static String lastSelected = "";

    private enum Tab {
        STABLE("คอกม้า"), DRAW("สุ่มม้า"), MARKET("ตลาดม้า"), BREED("ผสมพันธุ์"), TOP("อันดับ");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum Edit { NONE, RENAME, PRICE, STUD }

    private record Horse(String id, String name, String origin, String rarity, int[] levels, int[] start, String coat,
                         boolean secret, boolean rare, boolean active, long listed, long recover, long npcPrice,
                         String owner, CompoundTag snapshot, int lineage, String sireName, String damName,
                         String sireId, String damId, int breedsLeft, long breedReady, long studFee, long unborn,
                         List<String> traits) {
        int trained() {
            int total = 0;
            for (int i = 0; i < 4; i++) total += Math.max(0, levels[i] - start[i]);
            return total;
        }

        boolean born() {
            return unborn <= 0;
        }
    }

    private record TraitInfo(String name, String effect, int color) {
    }

    private static final int COMBAT = 0xFFE06A5A, ECONOMY = 0xFFE3A857, LIFE = 0xFF86C05C, MYTHIC = 0xFFC88CFF;
    private static final Map<String, TraitInfo> TRAITS = Map.ofEntries(
            Map.entry("WARHORSE", new TraitInfo("ม้าศึก", "ผู้ขี่โจมตีแรงขึ้น", COMBAT)),
            Map.entry("IRONHIDE", new TraitInfo("หนังเหล็ก", "ม้ามีเกราะเพิ่ม", COMBAT)),
            Map.entry("VALIANT", new TraitInfo("กล้าหาญ", "ผู้ขี่มีเกราะเพิ่ม", COMBAT)),
            Map.entry("WINDRUNNER", new TraitInfo("วิ่งลม", "ม้าวิ่งเร็วขึ้น", COMBAT)),
            Map.entry("STALWART", new TraitInfo("ทรหด", "พักฟื้นหลังสลบเร็วขึ้นครึ่งหนึ่ง", COMBAT)),
            Map.entry("GOLDEN_BLOOD", new TraitInfo("เลือดทอง", "NPC รับซื้อแพงขึ้น", ECONOMY)),
            Map.entry("SHOWSTOPPER", new TraitInfo("ดาวเด่น", "ขายในตลาดและค่าพ่อพันธุ์ไม่เสียค่าธรรมเนียม", ECONOMY)),
            Map.entry("PRIZED_LINE", new TraitInfo("สายเลือดเลิศ", "ลูกมีโอกาสกลายพันธุ์สูงขึ้น", ECONOMY)),
            Map.entry("FERTILE", new TraitInfo("เจริญพันธุ์", "ผสมพันธุ์ได้หลายครั้งขึ้น", ECONOMY)),
            Map.entry("FORAGER", new TraitInfo("นักหาของ", "ขี่แล้วได้ EXP ขุด ฟาร์ม ตกปลา คราฟต์เพิ่ม", LIFE)),
            Map.entry("TRAILBLAZER", new TraitInfo("นักบุกเบิก", "ขี่แล้วได้ EXP สำรวจเพิ่ม", LIFE)),
            Map.entry("SUREFOOT", new TraitInfo("เท้ามั่น", "ม้าและผู้ขี่ไม่รับดาเมจตกที่สูง", LIFE)),
            Map.entry("PEDDLER", new TraitInfo("พ่อค้าเร่", "ขี่แล้วได้ EXP ค้าขายเพิ่ม และขายได้แพงขึ้นเล็กน้อย", LIFE)),
            Map.entry("STARBORN", new TraitInfo("บุตรแห่งดารา", "หายากสุด: EXP ทุกอย่างเพิ่ม ราคาสูง และเปล่งแสง", MYTHIC)));

    private static TraitInfo trait(String id) {
        return TRAITS.getOrDefault(id, new TraitInfo(id, "", Ui.TEXT_MUTED));
    }

    private final CompoundTag payload;
    private final String npc;
    private final String blocked;
    private final List<Horse> horses = new ArrayList<>();
    private final List<Horse> market = new ArrayList<>();
    private final List<Horse> results = new ArrayList<>();
    private final List<Horse> studs = new ArrayList<>();
    private ScrollPanel partnerList;
    private Horse partner;
    private final List<String> top = new ArrayList<>();
    private final Map<String, LivingEntity> previews = new HashMap<>();
    private final Set<String> brokenPreviews = new HashSet<>();
    private Tab tab;
    private ScrollPanel list;
    private Horse selected;
    private Edit editing = Edit.NONE;
    private EditBox input;
    private String confirmKey = "";
    private long confirmUntil;
    private long resultsShownAt;

    public StableScreen(CompoundTag payload) {
        super("คอกม้า", null);
        this.payload = payload;
        this.npc = payload.getString("npc");
        this.blocked = payload.getString("blocked");
        read(payload.getList("horses", Tag.TAG_COMPOUND), horses);
        read(payload.getList("market", Tag.TAG_COMPOUND), market);
        read(payload.getList("results", Tag.TAG_COMPOUND), results);
        read(payload.getList("studs", Tag.TAG_COMPOUND), studs);
        ListTag lines = payload.getList("top", Tag.TAG_STRING);
        for (int i = 0; i < lines.size(); i++) top.add(lines.getString(i));
        Tab requested;
        try {
            requested = Tab.valueOf(payload.getString("tab"));
        } catch (IllegalArgumentException unknown) {
            requested = Tab.STABLE;
        }
        tab = !available(requested) ? Tab.STABLE : requested;
        if (!results.isEmpty()) {
            resultsShownAt = Util.getMillis();
            Sfx.reward();
        }
        if (!npc.isBlank()) setHeader("คอกม้า · ร้านม้า");
    }

    private static void read(ListTag tags, List<Horse> target) {
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.getCompound(i);
            int[] levels = tag.getIntArray("levels");
            int[] start = tag.getIntArray("start");
            ListTag traitTags = tag.getList("traits", Tag.TAG_STRING);
            List<String> traits = new ArrayList<>();
            for (int t = 0; t < traitTags.size(); t++) traits.add(traitTags.getString(t));
            target.add(new Horse(tag.getString("id"), tag.getString("name"), tag.getString("origin"), tag.getString("rarity"),
                    levels.length == 4 ? levels : new int[]{1, 1, 1, 1}, start.length == 4 ? start : new int[]{1, 1, 1, 1},
                    tag.getString("coat"), tag.getBoolean("secret"), tag.getBoolean("rare"), tag.getBoolean("active"),
                    tag.getLong("listed"), tag.getLong("recover"), tag.getLong("npc_price"), tag.getString("owner"),
                    tag.getCompound("snapshot"), tag.getInt("lineage"), tag.getString("sire_name"), tag.getString("dam_name"),
                    tag.getString("sire_id"), tag.getString("dam_id"), tag.getInt("breeds_left"), tag.getLong("breed_ready"),
                    tag.getLong("stud_fee"), tag.getLong("unborn"), List.copyOf(traits)));
        }
    }

    private boolean available(Tab value) {
        if (value == Tab.MARKET) return !npc.isBlank();
        if (value == Tab.BREED) return !npc.isBlank() && payload.getBoolean("breed_enabled");
        return true;
    }

    private List<Horse> rows() {
        if (tab == Tab.MARKET) return market;
        if (tab == Tab.BREED) return horses.stream().filter(Horse::born).toList();
        return horses;
    }

    private List<Horse> partners() {
        List<Horse> result = new ArrayList<>();
        for (Horse horse : horses) {
            if (horse.born() && selected != null && !horse.id().equals(selected.id())) result.add(horse);
        }
        result.addAll(studs);
        return result;
    }

    private boolean isStud(Horse horse) {
        return studs.contains(horse);
    }

    private static boolean kin(Horse a, Horse b) {
        if (a.id().equals(b.sireId()) || a.id().equals(b.damId()) || b.id().equals(a.sireId()) || b.id().equals(a.damId())) {
            return true;
        }
        return shares(a.sireId(), b) || shares(a.damId(), b);
    }

    private static boolean shares(String parent, Horse other) {
        return !parent.isEmpty() && (parent.equals(other.sireId()) || parent.equals(other.damId()));
    }

    private long breedCost(Horse dam, Horse sire) {
        return payload.getLong("breed_base") + Math.max(dam.lineage(), sire.lineage()) * payload.getLong("breed_per_lineage")
                + (isStud(sire) ? sire.studFee() : 0);
    }

    private String breedBlock(Horse dam, Horse sire) {
        if (dam.breedsLeft() <= 0) return dam.name() + " ผสมพันธุ์ครบแล้ว";
        if (sire.breedsLeft() <= 0) return sire.name() + " ผสมพันธุ์ครบแล้ว";
        if (dam.breedReady() > 0) return dam.name() + " ต้องพักอีก " + (dam.breedReady() / 60 + 1) + " นาที";
        if (sire.breedReady() > 0) return sire.name() + " ต้องพักอีก " + (sire.breedReady() / 60 + 1) + " นาที";
        if (dam.listed() > 0 || sire.listed() > 0) return "ม้าที่ลงขายอยู่ผสมพันธุ์ไม่ได้";
        if (kin(dam, sire)) return "เป็นญาติใกล้ชิด ผสมพันธุ์ไม่ได้";
        if (horses.size() >= payload.getInt("slots")) return "คอกเต็ม ต้องมีช่องว่างให้ลูกม้า";
        return null;
    }

@Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 860);
        guiHeight = Ui.fill(height, 480);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        addBackButton();
        if (!blocked.isBlank()) {
            return;
        }
        int tabX = guiLeft + Ui.PAD;
        for (Tab value : Tab.values()) {
            if (!available(value)) continue;
            int w = font.width(value.label) + 26;
            Button button = addRenderableWidget((value == tab ? Ui.primaryButton(Ui.text(value.label), b -> { })
                    : Ui.button(Ui.text(value.label), b -> switchTab(value))).bounds(tabX, guiTop + 32, w, 20).build());
            button.active = value != tab || true;
            tabX += w + Ui.GAP;
        }
        if (tab == Tab.STABLE || tab == Tab.MARKET) {
            List<Horse> rows = rows();
            if (selected == null || !rows.contains(selected)) {
                selected = rows.stream().filter(horse -> horse.id().equals(lastSelected)).findFirst()
                        .orElse(rows.isEmpty() ? null : rows.get(0));
            }
            int top = contentTop();
            list = new ScrollPanel(guiLeft + Ui.PAD, top, listW(), footerY() - Ui.GAP - top, ROW).withoutBackground().rowHitInsets(1, 3);
            list.setRows(rows.size(), this::renderRow, this::clickRow);
            registerPanel(list);
            buildActions();
        } else if (tab == Tab.BREED) {
            buildBreed();
        } else if (tab == Tab.DRAW) {
            int buttonW = 150;
            int y = footerY() - 32;
            int center = guiLeft + guiWidth / 2;
            addRenderableWidget(Ui.primaryButton(Ui.text("สุ่ม 1 ครั้ง · " + Currencies.amount(payload.getLong("pull_cost"))),
                    b -> pull(1)).bounds(center - buttonW - 4, y, buttonW, 24).build());
            addRenderableWidget(Ui.primaryButton(Ui.text("สุ่ม 10 ครั้ง · " + Currencies.amount(payload.getLong("ten_cost"))),
                    b -> pull(10)).bounds(center + 4, y, buttonW, 24).build());
        }
        if (tab == Tab.STABLE && payload.getInt("slots") < payload.getInt("max_slots")) {
            addRenderableWidget(Ui.button(Ui.text("ซื้อช่องคอกเพิ่ม · " + Currencies.amount(payload.getLong("slot_cost"))),
                    b -> confirm("slot", () -> act("horse_buy_slot", new CompoundTag(), false)))
                    .bounds(guiLeft + Ui.PAD + 64, footerY(), 170, 22).build())
                    .setMessage(Ui.text(confirming("slot") ? "กดอีกครั้งเพื่อซื้อ" : "ซื้อช่องคอกเพิ่ม · " + Currencies.amount(payload.getLong("slot_cost"))));
        }
    }

    private void buildBreed() {
        List<Horse> rows = rows();
        if (selected == null || !rows.contains(selected)) {
            selected = rows.stream().filter(horse -> horse.id().equals(lastSelected)).findFirst()
                    .orElse(rows.isEmpty() ? null : rows.get(0));
        }
        List<Horse> partners = partners();
        if (partner != null && !partners.contains(partner)) partner = null;
        int top = contentTop() + 12;
        int bottom = footerY() - Ui.GAP;
        int colW = breedColW();
        list = new ScrollPanel(guiLeft + Ui.PAD, top, colW, bottom - top, ROW).withoutBackground().rowHitInsets(1, 3);
        list.setRows(rows.size(), this::renderRow, this::clickRow);
        registerPanel(list);
        partnerList = new ScrollPanel(guiLeft + Ui.PAD + colW + Ui.GAP, top, colW, bottom - top, ROW)
                .withoutBackground().rowHitInsets(1, 3);
        partnerList.setRows(partners.size(), this::renderPartner, this::clickPartner);
        registerPanel(partnerList);
        if (selected == null || partner == null) return;
        String block = breedBlock(selected, partner);
        long cost = breedCost(selected, partner);
        int x = breedPanelX() + 10;
        int w = guiLeft + guiWidth - Ui.PAD - breedPanelX() - 20;
        Button button = addRenderableWidget(Ui.primaryButton(Ui.text(confirming("breed") ? "กดอีกครั้งเพื่อผสม"
                : "ผสมพันธุ์ · " + Currencies.amount(cost)), b -> confirm("breed", () -> {
            CompoundTag request = horsePayload();
            request.putString("sire", partner.id());
            request.putLong("cost", cost);
            act("horse_breed", request, false);
        })).bounds(x, bottom - 30, w, 24).build());
        button.active = block == null && payload.getLong("balance") >= cost;
    }

    private int breedColW() { return Math.max(150, (guiWidth - Ui.PAD * 2 - Ui.GAP * 4) * 3 / 10); }
    private int breedPanelX() { return guiLeft + Ui.PAD + (breedColW() + Ui.GAP) * 2 + Ui.GAP; }

    private void renderPartner(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        List<Horse> partners = partners();
        if (index >= partners.size()) return;
        Horse horse = partners.get(index);
        Ui.rowCard(graphics, x, y, w - 6, h - 4, hovered, horse == partner);
        graphics.fill(x + 4, y + 5, x + 7, y + h - 9, isStud(horse) ? 0xFFE3A857 : Currencies.rarityColor(horse.rarity()));
        graphics.renderFakeItem(new ItemStack(isStud(horse) ? Items.GOLDEN_CARROT : Items.SADDLE), x + 12, y + 10);
        Ui.label(graphics, Ui.truncate(horse.name(), w - 50), x + 34, y + 6, Ui.TEXT_BRIGHT);
        String second = isStud(horse) ? "พ่อพันธุ์ของ " + horse.owner() + " · " + Currencies.amount(horse.studFee())
                : "รุ่น " + horse.lineage() + " · ผสมได้อีก " + horse.breedsLeft();
        Ui.label(graphics, Ui.truncate(second, w - 50), x + 34, y + 18, Ui.TEXT_MUTED);
        renderTraitDots(graphics, horse, x + w - 16, y + 8);
    }

    private void clickPartner(int index, int button) {
        List<Horse> partners = partners();
        if (index < 0 || index >= partners.size()) return;
        partner = partners.get(index);
        confirmKey = "";
        Sfx.select();
        rebuild();
    }

    private void renderTraitDots(GuiGraphics graphics, Horse horse, int right, int y) {
        int dx = right;
        for (int i = horse.traits().size() - 1; i >= 0; i--) {
            dx -= 7;
            graphics.fill(dx, y, dx + 5, y + 5, trait(horse.traits().get(i)).color());
        }
    }

    private void renderBreed(GuiGraphics graphics) {
        int top = contentTop();
        int colW = breedColW();
        Ui.label(graphics, "ม้าตัวแรก", guiLeft + Ui.PAD + 4, top, Ui.TEXT_MUTED);
        Ui.label(graphics, "คู่ผสม (ม้าของคุณ / พ่อพันธุ์)", guiLeft + Ui.PAD + colW + Ui.GAP + 4, top, Ui.TEXT_MUTED);
        if (rows().isEmpty()) {
            Ui.label(graphics, "ยังไม่มีม้าที่ผสมพันธุ์ได้", guiLeft + Ui.PAD + 8, top + 24, Ui.TEXT_DIM);
        }
        int x = breedPanelX();
        int w = guiLeft + guiWidth - Ui.PAD - x;
        int h = footerY() - Ui.GAP - top;
        Ui.panel(graphics, x, top, w, h);
        int tx = x + 10;
        int tw = w - 20;
        int y = top + 10;
        if (selected == null || partner == null) {
            for (String line : Ui.wrap("เลือกม้าสองตัวเพื่อดูลูกที่อาจเกิด ลูกได้เลเวลบางส่วน นิสัยจากพ่อแม่ และอาจกลายพันธุ์ได้นิสัยใหม่", tw)) {
                Ui.label(graphics, line, tx, y, Ui.TEXT_MUTED);
                y += 11;
            }
            return;
        }
        Ui.sectionHeading(graphics, "คู่ผสม", tx, y, tw);
        y += 16;
        Ui.label(graphics, Ui.truncate(selected.name() + "  ×  " + partner.name(), tw), tx, y, Ui.TEXT_BRIGHT);
        y += 14;
        Ui.label(graphics, "ลูกจะเป็นสายเลือดรุ่นที่ " + (Math.max(selected.lineage(), partner.lineage()) + 1), tx, y, Ui.TEXT);
        y += 12;
        Ui.label(graphics, "ตั้งท้อง " + payload.getInt("gestation") + " นาที · ใช้ช่องคอก 1 ช่อง", tx, y, Ui.TEXT_MUTED);
        y += 16;
        Ui.sectionHeading(graphics, "นิสัยที่อาจสืบทอด", tx, y, tw);
        y += 16;
        Set<String> seen = new HashSet<>();
        for (Horse parent : List.of(selected, partner)) {
            for (String id : parent.traits()) {
                if (!seen.add(id) || id.equals("STARBORN") || y > footerY() - 70) continue;
                TraitInfo info = trait(id);
                Ui.label(graphics, Ui.truncate("• " + info.name() + " — " + info.effect(), tw), tx, y, info.color());
                y += 11;
            }
        }
        if (seen.isEmpty()) {
            Ui.label(graphics, "พ่อแม่ไม่มีนิสัย ลุ้นกลายพันธุ์เท่านั้น", tx, y, Ui.TEXT_DIM);
            y += 11;
        }
        if (selected.traits().size() >= 2 && partner.traits().size() >= 2) {
            Ui.label(graphics, Ui.truncate("★ พ่อแม่มีนิสัยครบ ลุ้น 'บุตรแห่งดารา' ได้!", tw), tx, y + 2, MYTHIC);
            y += 13;
        }
        String block = breedBlock(selected, partner);
        if (block != null) {
            for (String line : Ui.wrap(block, tw)) {
                Ui.label(graphics, line, tx, footerY() - Ui.GAP - 50, Ui.BAD);
            }
        } else if (isStud(partner)) {
            Ui.label(graphics, Ui.truncate("รวมค่าพ่อพันธุ์ " + Currencies.amount(partner.studFee()) + " จ่ายให้ " + partner.owner(), tw),
                    tx, footerY() - Ui.GAP - 50, 0xFFE3A857);
        }
    }

    private int contentTop() { return guiTop + 58; }
    private int footerY() { return guiTop + guiHeight - 28; }
    private int listW() { return Math.max(180, (int) (guiWidth * 0.42)); }
    private int detailX() { return guiLeft + Ui.PAD + listW() + Ui.GAP * 2; }
    private int detailW() { return guiLeft + guiWidth - Ui.PAD - detailX(); }

    private void buildActions() {
        if (selected == null) return;
        int x = detailX() + 10;
        int w = detailW() - 20;
        int bottom = footerY() - Ui.GAP - 8;
        if (editing != Edit.NONE) {
            input = new EditBox(font, x, bottom - 48, w, 20, Ui.text(editing == Edit.RENAME ? "ชื่อม้า" : "ราคา"));
            input.setMaxLength(editing == Edit.RENAME ? 24 : 12);
            input.setValue(editing == Edit.RENAME ? selected.name() : "");
            input.setHint(Ui.text(editing == Edit.RENAME ? "พิมพ์ชื่อใหม่"
                    : editing == Edit.STUD ? "ค่าพ่อพันธุ์ต่อครั้ง (สูงสุด " + Currencies.amount(payload.getLong("stud_max")) + ")"
                    : "ราคาที่ต้องการขาย"));
            if (editing != Edit.RENAME) input.setFilter(value -> value.chars().allMatch(Character::isDigit));
            addRenderableWidget(input);
            setInitialFocus(input);
            int half = (w - Ui.GAP) / 2;
            addRenderableWidget(Ui.primaryButton(Ui.text("ตกลง"), b -> submitEdit()).bounds(x, bottom - 22, half, 22).build());
            addRenderableWidget(Ui.button(Ui.text("ยกเลิก"), b -> { editing = Edit.NONE; rebuild(); })
                    .bounds(x + half + Ui.GAP, bottom - 22, half, 22).build());
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        if (tab == Tab.MARKET) {
            if (isMine(selected)) {
                actions.add(new ActionButton("ยกเลิกการขาย", true, () -> act("horse_unlist", horsePayload(), false)));
            } else {
                long price = selected.listed();
                boolean affordable = payload.getLong("balance") >= price;
                actions.add(new ActionButton(confirming("buy") ? "กดอีกครั้งเพื่อซื้อ" : "ซื้อ · " + Currencies.amount(price), affordable,
                        () -> confirm("buy", () -> {
                            CompoundTag request = horsePayload();
                            request.putLong("price", price);
                            act("horse_buy", request, false);
                        })));
            }
        } else if (!selected.born()) {
            actions.add(new ActionButton(confirming("release") ? "กดอีกครั้งเพื่อสละ" : "สละลูกม้า", true,
                    () -> confirm("release", () -> act("horse_release", horsePayload(), false))));
        } else {
            boolean listed = selected.listed() > 0;
            if (!npc.isBlank() && payload.getBoolean("breed_enabled") && !listed) {
                if (selected.studFee() > 0) {
                    actions.add(new ActionButton("เลิกเป็นพ่อพันธุ์", true, () -> {
                        CompoundTag request = horsePayload();
                        request.putLong("fee", 0);
                        act("horse_stud", request, false);
                    }));
                } else {
                    actions.add(new ActionButton("รับเป็นพ่อพันธุ์", selected.breedsLeft() > 0,
                            () -> { editing = Edit.STUD; rebuild(); }));
                }
            }
            if (selected.active()) {
                actions.add(new ActionButton("เก็บเข้าคอก", true, () -> act("horse_store", horsePayload(), false)));
            } else {
                actions.add(new ActionButton(selected.recover() > 0 ? "พักฟื้นอีก " + selected.recover() + " วิ" : "เรียกม้า",
                        !listed && selected.recover() <= 0, () -> {
                    act("horse_summon", horsePayload(), true);
                    onClose();
                }));
            }
            actions.add(new ActionButton("ตั้งชื่อ", true, () -> { editing = Edit.RENAME; rebuild(); }));
            if (listed) {
                actions.add(new ActionButton("ยกเลิกการขาย", true, () -> act("horse_unlist", horsePayload(), false)));
            } else if (!npc.isBlank()) {
                actions.add(new ActionButton("ลงขายในตลาด", true, () -> { editing = Edit.PRICE; rebuild(); }));
                actions.add(new ActionButton(confirming("sell") ? "กดอีกครั้งเพื่อขาย" : "ขายให้ NPC · " + Currencies.amount(selected.npcPrice()),
                        selected.npcPrice() > 0 && payload.getInt("sales_left") != 0,
                        () -> confirm("sell", () -> act("horse_sell", horsePayload(), false))));
            }
            actions.add(new ActionButton(confirming("release") ? "กดอีกครั้งเพื่อปล่อย" : "ปล่อยม้า", true,
                    () -> confirm("release", () -> act("horse_release", horsePayload(), false))));
        }
        int columns = 2;
        int bw = (w - Ui.GAP) / columns;
        int rowsNeeded = (actions.size() + columns - 1) / columns;
        for (int i = 0; i < actions.size(); i++) {
            ActionButton action = actions.get(i);
            int bx = x + (i % columns) * (bw + Ui.GAP);
            int by = bottom - (rowsNeeded - i / columns) * 26 + 4;
            Button button = addRenderableWidget((i == 0 ? Ui.primaryButton(Ui.text(action.label()), b -> action.run().run())
                    : Ui.button(Ui.text(action.label()), b -> action.run().run())).bounds(bx, by, bw, 22).build());
            button.active = action.enabled();
        }
    }

    private record ActionButton(String label, boolean enabled, Runnable run) {
    }

    private boolean isMine(Horse horse) {
        return horses.stream().anyMatch(own -> own.id().equals(horse.id()));
    }

    private CompoundTag horsePayload() {
        CompoundTag request = new CompoundTag();
        request.putString("id", selected == null ? "" : selected.id());
        return request;
    }

    private void act(String action, CompoundTag request, boolean close) {
        request.putString("npc", npc);
        request.putBoolean("close", close);
        send(action, request);
        Sfx.commit();
    }

    private void pull(int count) {
        CompoundTag request = new CompoundTag();
        request.putInt("count", count);
        act("horse_pull", request, false);
    }

    private void submitEdit() {
        if (selected == null || input == null) return;
        CompoundTag request = horsePayload();
        if (editing == Edit.RENAME) {
            request.putString("name", input.getValue());
            act("horse_rename", request, false);
        } else {
            long price;
            try {
                price = Long.parseLong(input.getValue().trim());
            } catch (NumberFormatException empty) {
                return;
            }
            if (editing == Edit.STUD) {
                request.putLong("fee", price);
                act("horse_stud", request, false);
            } else {
                request.putLong("price", price);
                act("horse_list", request, false);
            }
        }
        editing = Edit.NONE;
    }

    private boolean confirming(String key) {
        return confirmKey.equals(key) && Util.getMillis() < confirmUntil;
    }

    private void confirm(String key, Runnable action) {
        if (confirming(key)) {
            confirmKey = "";
            action.run();
            return;
        }
        confirmKey = key;
        confirmUntil = Util.getMillis() + 3000;
        Sfx.select();
        rebuild();
    }

    private void switchTab(Tab value) {
        tab = value;
        selected = null;
        partner = null;
        editing = Edit.NONE;
        Sfx.page();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void tick() {
        super.tick();
        if (!confirmKey.isEmpty() && Util.getMillis() >= confirmUntil) {
            confirmKey = "";
            rebuild();
        }
    }

private void renderRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        Horse horse = rows().get(index);
        Ui.rowCard(graphics, x, y, w - 6, h - 4, hovered, horse == selected);
        int color = Currencies.rarityColor(horse.rarity());
        graphics.fill(x + 4, y + 5, x + 7, y + h - 9, color);
        graphics.renderFakeItem(new ItemStack(horse.secret() ? Items.GOLDEN_CARROT : Items.SADDLE), x + 12, y + 10);
        int textX = x + 34;
        int right = x + w - 16;
        if (!horse.born()) {
            Ui.label(graphics, "ลูกม้า ???", textX, y + 6, Ui.TEXT_BRIGHT);
            Ui.label(graphics, Ui.truncate("ลูกของ " + horse.sireName() + " × " + horse.damName(), w - 50), textX, y + 18, Ui.TEXT_MUTED);
            Ui.labelRight(graphics, "เกิดใน " + (horse.unborn() / 60 + 1) + " นาที", right, y + 6, 0xFFC88CFF);
            return;
        }
        String status = tab == Tab.MARKET ? Currencies.amount(horse.listed())
                : horse.listed() > 0 ? "ลงขาย" : horse.active() ? "ออกนอกคอก" : horse.recover() > 0 ? "พักฟื้น"
                : horse.studFee() > 0 ? "พ่อพันธุ์" : "";
        int statusColor = tab == Tab.MARKET ? 0xFFE3A857 : horse.active() ? Ui.GOOD : horse.recover() > 0 ? Ui.BAD : Ui.WARN;
        renderTraitDots(graphics, horse, right, y + 22);
        int statusW = status.isEmpty() ? 0 : font.width(status) + 8;
        Ui.label(graphics, Ui.truncate(horse.name(), w - 50 - statusW), textX, y + 6, Ui.TEXT_BRIGHT);
        String levels = "S" + roman(horse.levels()[0]) + " J" + roman(horse.levels()[1]) + " H" + roman(horse.levels()[2])
                + " A" + roman(horse.levels()[3]);
        String second = tab == Tab.MARKET ? horse.owner() + " · " + levels : Currencies.rarityName(horse.rarity()) + " · " + levels;
        Ui.label(graphics, Ui.truncate(second, w - 50), textX, y + 18, Ui.TEXT_MUTED);
        if (!status.isEmpty()) Ui.labelRight(graphics, status, right, y + 6, statusColor);
    }

    private void clickRow(int index, int button) {
        List<Horse> rows = rows();
        if (index < 0 || index >= rows.size()) return;
        selected = rows.get(index);
        lastSelected = selected.id();
        if (partner != null && partner.id().equals(selected.id())) partner = null;
        editing = Edit.NONE;
        confirmKey = "";
        Sfx.select();
        rebuild();
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!blocked.isBlank()) {
            Ui.labelCentered(graphics, blocked, guiLeft + guiWidth / 2, guiTop + guiHeight / 2, Ui.WARN);
            return;
        }
        renderWallet(graphics);
        switch (tab) {
            case STABLE, MARKET -> {
                if (rows().isEmpty()) {
                    String empty = tab == Tab.MARKET ? "ยังไม่มีม้าลงขายในตลาด"
                            : "คอกยังว่าง ไปสุ่มม้าหรือใช้นกหวีดกับม้าของคุณเพื่อเก็บเข้าคอก";
                    for (String line : Ui.wrap(empty, listW() - 20)) {
                        Ui.label(graphics, line, guiLeft + Ui.PAD + 8, contentTop() + 12, Ui.TEXT_DIM);
                    }
                }
                renderDetail(graphics, mouseX, mouseY);
            }
            case DRAW -> renderDraw(graphics);
            case BREED -> renderBreed(graphics);
            case TOP -> renderTop(graphics);
        }
        if (!results.isEmpty()) {
            renderResults(graphics);
        }
    }

    private void renderWallet(GuiGraphics graphics) {
        String balance = Currencies.name(payload.getString("currency")) + " " + Currencies.amount(payload.getLong("balance"));
        String slots = "ช่องคอก " + horses.size() + " / " + payload.getInt("slots");
        int x = guiLeft + guiWidth - Ui.PAD;
        for (String text : new String[]{balance, slots}) {
            int w = font.width(text) + 16;
            x -= w;
            PixelUi.fill(graphics, x, guiTop + 34, w - 4, 16, 1, RotasTheme.SURFACE_HIGH);
            Ui.label(graphics, text, x + 6, guiTop + 38, Ui.TEXT_BRIGHT);
            x -= 4;
        }
    }

    private void renderDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = detailX();
        int y = contentTop();
        int w = detailW();
        int h = footerY() - Ui.GAP - y;
        Ui.panel(graphics, x, y, w, h);
        if (selected == null) {
            Ui.labelCentered(graphics, "เลือกม้าทางซ้าย", x + w / 2, y + h / 2, Ui.TEXT_MUTED);
            return;
        }
        if (!selected.born()) {
            Ui.labelCentered(graphics, "ลูกม้ากำลังจะเกิด", x + w / 2, y + 40, 0xFFC88CFF);
            Ui.labelCentered(graphics, Ui.truncate("พ่อ " + selected.sireName() + " · แม่ " + selected.damName(), w - 20),
                    x + w / 2, y + 56, Ui.TEXT);
            Ui.labelCentered(graphics, "เกิดในอีก " + (selected.unborn() / 60 + 1) + " นาที · สายเลือดรุ่นที่ " + selected.lineage(),
                    x + w / 2, y + 70, Ui.TEXT_MUTED);
            Ui.labelCentered(graphics, "นิสัย สีขน และเลเวลจะเปิดเผยตอนเกิด", x + w / 2, y + 84, Ui.TEXT_DIM);
            return;
        }
        int modelW = Math.min(150, w / 2 - 10);
        int modelH = Math.min(140, h - 150);
        PixelUi.fill(graphics, x + 8, y + 8, modelW, modelH, 1, RotasTheme.TRACK);
        LivingEntity preview = preview(selected);
        if (preview != null && modelH > 60) {
            try {
                int scale = (int) Math.max(12, Math.min(40, (modelH - 20) / Math.max(1f, preview.getBbHeight())));
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 8 + modelW / 2, y + 8 + modelH - 10, scale,
                        x + 8 + modelW / 2f - mouseX, y + 8 + modelH / 2f - mouseY, preview);
            } catch (RuntimeException | LinkageError failure) {
                brokenPreviews.add(selected.id());
            }
        } else {
            graphics.renderFakeItem(new ItemStack(Items.SADDLE), x + 8 + modelW / 2 - 8, y + 8 + modelH / 2 - 8);
        }

        int infoX = x + modelW + 18;
        int infoW = w - modelW - 26;
        int rarityColor = Currencies.rarityColor(selected.rarity());
        Ui.scaledLabel(graphics, Ui.truncate(selected.name(), (int) (infoW / 1.25f)), infoX, y + 10, 1.25f, Ui.TEXT_BRIGHT);
        int chipX = infoX;
        chipX += Ui.tag(graphics, chipX, y + 26, Currencies.rarityName(selected.rarity()), rarityColor) + 4;
        if (selected.secret()) chipX += Ui.tag(graphics, chipX, y + 26, "สีขนลับ", 0xFFE3A857) + 4;
        else if (selected.rare()) chipX += Ui.tag(graphics, chipX, y + 26, "สีขนหายาก", 0xFFB07CE8) + 4;
        Ui.label(graphics, Ui.truncate(originText(selected.origin()), infoW), infoX, y + 44, Ui.TEXT_MUTED);
        Ui.label(graphics, Ui.truncate("สีขน: " + coatName(selected.coat()), infoW), infoX, y + 56, Ui.TEXT_MUTED);
        String status = selected.listed() > 0 ? "ลงขายอยู่ " + Currencies.amount(selected.listed())
                : selected.active() ? "ออกนอกคอกอยู่" : selected.recover() > 0 ? "พักฟื้นอีก " + selected.recover() + " วินาที" : "อยู่ในคอก";
        Ui.label(graphics, Ui.truncate(status, infoW), infoX, y + 68, selected.active() ? Ui.GOOD : Ui.TEXT);
        if (tab == Tab.MARKET) {
            Ui.label(graphics, Ui.truncate("ผู้ขาย: " + selected.owner(), infoW), infoX, y + 80, Ui.TEXT);
        } else if (!npc.isBlank()) {
            String price = selected.npcPrice() > 0 ? "NPC รับซื้อ " + Currencies.amount(selected.npcPrice())
                    : "NPC ไม่รับซื้อม้าที่ไม่ได้มาจากการสุ่ม";
            Ui.label(graphics, Ui.truncate(price, infoW), infoX, y + 80, selected.npcPrice() > 0 ? 0xFFE3A857 : Ui.TEXT_MUTED);
        }

        int lineY = y + 94;
        String pedigree = selected.lineage() <= 0 ? "ม้าต้นสาย" : "สายเลือดรุ่นที่ " + selected.lineage()
                + " · พ่อ " + selected.sireName() + " · แม่ " + selected.damName();
        Ui.label(graphics, Ui.truncate(pedigree, infoW), infoX, lineY, Ui.TEXT_MUTED);
        lineY += 12;
        String breeding = "ผสมได้อีก " + selected.breedsLeft() + " ครั้ง"
                + (selected.breedReady() > 0 ? " · พัก " + (selected.breedReady() / 60 + 1) + " นาที" : "")
                + (selected.studFee() > 0 ? " · พ่อพันธุ์ " + Currencies.amount(selected.studFee()) : "");
        Ui.label(graphics, Ui.truncate(breeding, infoW), infoX, lineY, selected.studFee() > 0 ? 0xFFE3A857 : Ui.TEXT_MUTED);
        lineY += 14;
        for (String id : selected.traits()) {
            TraitInfo info = trait(id);
            int tagW = Ui.tag(graphics, infoX, lineY, info.name(), info.color());
            Ui.label(graphics, Ui.truncate(info.effect(), infoW - tagW - 6), infoX + tagW + 6, lineY + 2, Ui.TEXT_DIM);
            lineY += 14;
        }
        if (selected.traits().isEmpty()) Ui.label(graphics, "ไม่มีนิสัยพิเศษ", infoX, lineY, Ui.TEXT_DIM);

        int barsY = y + Math.max(modelH + 16, Math.max(96, lineY - y + 18));
        Ui.sectionHeading(graphics, "สกิลม้า · ฝึกเพิ่มแล้ว " + selected.trained() + " เลเวล", x + 8, barsY, w - 16);
        barsY += 14;
        for (int skill = 0; skill < 4; skill++) {
            renderSkill(graphics, x + 8, barsY + skill * 16, w - 16, skill);
        }
        if (tab == Tab.MARKET) {
            double fee = payload.getDouble("market_fee");
            Ui.label(graphics, fee <= 0 ? "ตลาดไม่หักค่าธรรมเนียม ผู้ขายได้เงินเต็มจำนวน"
                    : "ค่าธรรมเนียมผู้ขาย " + Math.round(fee * 100) + "%", x + 8, barsY + 68, Ui.TEXT_MUTED);
        }
        if (input != null && editing == Edit.PRICE) {
            Ui.label(graphics, "ม้าที่ลงขายจะถูกล็อกไว้ในคอกจนกว่าจะขายหรือยกเลิก", x + 10,
                    footerY() - Ui.GAP - 8 - 62, Ui.TEXT_MUTED);
        }
    }

    private void renderSkill(GuiGraphics graphics, int x, int y, int w, int skill) {
        int level = selected.levels()[skill];
        int start = selected.start()[skill];
        int max = SKILL_MAX[skill];
        Ui.label(graphics, SKILLS[skill], x, y + 3, Ui.TEXT);
        String value = skill == 3 && level >= 11 ? "ผูกพัน" : roman(level);
        Ui.labelRight(graphics, value, x + w, y + 3, level >= max ? 0xFFE3A857 : Ui.TEXT_BRIGHT);
        int barX = x + 64;
        int barW = w - 64 - 44;
        int gap = 2;
        int segment = Math.max(3, (barW - gap * (max - 1)) / max);
        for (int i = 0; i < max; i++) {
            int sx = barX + i * (segment + gap);
            int color = i < start ? darker(SKILL_COLORS[skill]) : i < level ? SKILL_COLORS[skill] : RotasTheme.TRACK;
            graphics.fill(sx, y + 3, sx + segment, y + 11, color);
        }
    }

    private void renderDraw(GuiGraphics graphics) {
        int top = contentTop();
        int bottom = footerY() - 40;
        int leftW = (guiWidth - Ui.PAD * 2 - Ui.GAP * 2) * 3 / 5;
        int x = guiLeft + Ui.PAD;
        Ui.panel(graphics, x, top, leftW, bottom - top);
        Ui.sectionHeading(graphics, "โอกาสที่จะได้", x + 10, top + 8, leftW - 20);
        String[] rarities = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
        String[] gives = {
                "ทุกสกิลเริ่มที่ I",
                "สุ่ม 1 สกิลเริ่มที่ II",
                "สุ่ม 2 สกิลเริ่มที่ III · ความผูกพัน II",
                "ทุกสกิล IV · ความผูกพัน III · ลุ้นสีขนหายาก",
                "ทุกสกิล V · ความผูกพัน V · การันตีสีขนลับ"};
        ListTag rates = payload.getList("rates", Tag.TAG_DOUBLE);
        double total = 0;
        for (int i = 0; i < rates.size(); i++) total += rates.getDouble(i);
        int rowY = top + 24;
        for (int i = 0; i < 5; i++) {
            int color = Currencies.rarityColor(rarities[i]);
            PixelUi.fill(graphics, x + 10, rowY, leftW - 20, 26, 1, RotasTheme.SURFACE_HIGH);
            graphics.fill(x + 10, rowY, x + 13, rowY + 26, color);
            Ui.label(graphics, Currencies.rarityName(rarities[i]), x + 20, rowY + 4, color);
            double rate = total <= 0 || i >= rates.size() ? 0 : rates.getDouble(i) / total;
            Ui.labelRight(graphics, String.format(java.util.Locale.ROOT, "%.1f%%", rate * 100), x + leftW - 18, rowY + 4, Ui.TEXT_BRIGHT);
            Ui.label(graphics, Ui.truncate(gives[i], leftW - 90), x + 20, rowY + 15, Ui.TEXT_MUTED);
            rowY += 30;
        }
        for (String line : Ui.wrap("ม้าทุกตัวฝึกได้ถึงเลเวล V เท่ากัน ระดับจากการสุ่มแค่ช่วยให้เริ่มก่อนและได้สีขนสะสม", leftW - 20)) {
            if (rowY > bottom - 12) break;
            Ui.label(graphics, line, x + 10, rowY + 4, Ui.TEXT_DIM);
            rowY += 10;
        }

        int px = x + leftW + Ui.GAP * 2;
        int pw = guiLeft + guiWidth - Ui.PAD - px;
        Ui.panel(graphics, px, top, pw, bottom - top);
        Ui.sectionHeading(graphics, "ระบบการันตี", px + 10, top + 8, pw - 20);
        int[] pity = payload.getIntArray("pity");
        int[] limits = payload.getIntArray("pity_limits");
        String[] names = {"หายากขึ้นไป", "มหากาพย์ขึ้นไป", "ตำนาน"};
        int[] colors = {Currencies.rarityColor("RARE"), Currencies.rarityColor("EPIC"), Currencies.rarityColor("LEGENDARY")};
        int barY = top + 26;
        for (int i = 0; i < 3 && pity.length == 3 && limits.length == 3; i++) {
            if (limits[i] <= 0) continue;
            int left = Math.max(0, limits[i] - pity[i]);
            Ui.label(graphics, Ui.truncate(names[i] + " การันตีในอีก " + (left + 1) + " ครั้ง", pw - 20), px + 10, barY, Ui.TEXT);
            int filled = (int) ((pw - 20) * Math.min(1f, pity[i] / (float) limits[i]));
            graphics.fill(px + 10, barY + 12, px + pw - 10, barY + 18, RotasTheme.TRACK);
            graphics.fill(px + 10, barY + 12, px + 10 + filled, barY + 18, colors[i]);
            barY += 30;
        }
        Ui.label(graphics, Ui.truncate("ม้าที่ได้จะเข้าคอกทันที", pw - 20), px + 10, barY + 4, Ui.TEXT_MUTED);
        int free = payload.getInt("slots") - horses.size();
        Ui.label(graphics, Ui.truncate("ช่องคอกว่าง " + Math.max(0, free) + " ช่อง", pw - 20), px + 10, barY + 16,
                free >= 10 ? Ui.GOOD : free > 0 ? Ui.WARN : Ui.BAD);
    }

    private void renderTop(GuiGraphics graphics) {
        int x = guiLeft + Ui.PAD;
        int top = contentTop();
        int w = guiWidth - Ui.PAD * 2;
        Ui.panel(graphics, x, top, w, footerY() - Ui.GAP - top);
        Ui.sectionHeading(graphics, "ม้าที่ฝึกเลเวลเพิ่มได้มากที่สุด", x + 10, top + 8, w - 20);
        if (this.top.isEmpty()) {
            Ui.label(graphics, "ยังไม่มีม้าที่ฝึกเพิ่ม", x + 10, top + 28, Ui.TEXT_DIM);
        }
        int y = top + 26;
        for (String line : this.top) {
            Ui.label(graphics, Ui.truncate(line, w - 20), x + 10, y, Ui.TEXT);
            y += 14;
        }
    }

    private void renderResults(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0xE0100B06);
        int count = results.size();
        int columns = Math.min(5, count);
        int cardW = Math.min(140, (guiWidth - 40 - (columns - 1) * 8) / columns);
        int cardH = 86;
        int rowsNeeded = (count + columns - 1) / columns;
        int startX = guiLeft + (guiWidth - (columns * cardW + (columns - 1) * 8)) / 2;
        int startY = guiTop + (guiHeight - (rowsNeeded * cardH + (rowsNeeded - 1) * 8)) / 2 - 10;
        long elapsed = Util.getMillis() - resultsShownAt;
        for (int i = 0; i < count; i++) {
            Horse horse = results.get(i);
            int cx = startX + (i % columns) * (cardW + 8);
            int cy = startY + (i / columns) * (cardH + 8);
            float reveal = Math.max(0, Math.min(1, (elapsed - i * 180L) / 260f));
            int color = Currencies.rarityColor(horse.rarity());
            if (reveal <= 0) {
                PixelUi.frame(graphics, cx, cy, cardW, cardH, 1, RotasTheme.PANEL_BORDER, RotasTheme.SURFACE);
                Ui.labelCentered(graphics, "?", cx + cardW / 2, cy + cardH / 2 - 4, Ui.TEXT_MUTED);
                continue;
            }
            int inset = (int) ((1 - reveal) * cardW / 2);
            if (horse.rarity().equals("EPIC") || horse.rarity().equals("LEGENDARY")) {
                int glow = (int) (60 + 40 * Math.sin(elapsed / 180.0)) << 24 | (color & 0xFFFFFF);
                graphics.fill(cx - 3 + inset, cy - 3, cx + cardW + 3 - inset, cy + cardH + 3, glow);
            }
            PixelUi.frame(graphics, cx + inset, cy, cardW - inset * 2, cardH, 2, color, RotasTheme.SURFACE_HIGH);
            if (reveal < 1) continue;
            Ui.labelCentered(graphics, Currencies.rarityName(horse.rarity()), cx + cardW / 2, cy + 8, color);
            graphics.renderFakeItem(new ItemStack(horse.secret() ? Items.GOLDEN_CARROT : Items.SADDLE), cx + cardW / 2 - 8, cy + 22);
            String levels = "S" + roman(horse.levels()[0]) + " J" + roman(horse.levels()[1]) + " H" + roman(horse.levels()[2]);
            Ui.labelCentered(graphics, levels, cx + cardW / 2, cy + 44, Ui.TEXT_BRIGHT);
            Ui.labelCentered(graphics, "ผูกพัน " + roman(horse.levels()[3]), cx + cardW / 2, cy + 56, Ui.TEXT_MUTED);
            if (horse.secret()) Ui.labelCentered(graphics, "สีขนลับ!", cx + cardW / 2, cy + 70, 0xFFE3A857);
            else if (horse.rare()) Ui.labelCentered(graphics, "สีขนหายาก", cx + cardW / 2, cy + 70, 0xFFB07CE8);
            renderTraitDots(graphics, horse, cx + cardW / 2 + horse.traits().size() * 7 / 2, cy + 79);
        }
        Ui.labelCentered(graphics, "คลิกเพื่อปิด", guiLeft + guiWidth / 2, startY + rowsNeeded * (cardH + 8) + 6, Ui.TEXT_MUTED);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!results.isEmpty()) {
            long elapsed = Util.getMillis() - resultsShownAt;
            if (elapsed < results.size() * 180L + 260) {
                resultsShownAt = Util.getMillis() - results.size() * 180L - 260;
            } else {
                results.clear();
                rebuild();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input != null && input.isFocused() && keyCode == 257) {
            submitEdit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private LivingEntity preview(Horse horse) {
        if (brokenPreviews.contains(horse.id()) || minecraft == null || minecraft.level == null || horse.snapshot().isEmpty()) {
            return null;
        }
        LivingEntity cached = previews.get(horse.id());
        if (cached != null) return cached;
        try {
            LivingEntity created = EntityType.create(horse.snapshot().copy(), minecraft.level)
                    .filter(entity -> entity instanceof LivingEntity).map(entity -> (LivingEntity) entity).orElse(null);
            if (created == null) {
                brokenPreviews.add(horse.id());
                return null;
            }
            previews.put(horse.id(), created);
            return created;
        } catch (RuntimeException | LinkageError failure) {
            brokenPreviews.add(horse.id());
            return null;
        }
    }

    private static String roman(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};
        return numerals[Math.max(0, Math.min(numerals.length - 1, level - 1))];
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 5;
        int g = (color >> 8 & 0xFF) * 3 / 5;
        int b = (color & 0xFF) * 3 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static String originText(String origin) {
        return switch (origin) {
            case "GACHA" -> "ที่มา: ได้จากการสุ่ม";
            case "BRED" -> "ที่มา: เกิดจากการผสมพันธุ์";
            case "WILD" -> "ที่มา: ม้าที่จับมาเอง";
            default -> "ที่มา: แอดมินมอบให้";
        };
    }

    private static String coatName(String coat) {
        if (coat.isBlank()) return "-";
        String path = coat.substring(coat.indexOf(':') + 1).replace('_', ' ');
        return path.isEmpty() ? coat : path;
    }

    @Override
    public void removed() {
        previews.clear();
        super.removed();
    }
}
