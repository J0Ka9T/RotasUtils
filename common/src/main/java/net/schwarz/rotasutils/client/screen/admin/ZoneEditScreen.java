package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ZoneCombatRules;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneFeatures;
import net.schwarz.rotasutils.core.ZonePresets;
import net.schwarz.rotasutils.core.ZoneType;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.util.Nbt;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ZoneEditScreen extends RotasScreen {
    private static final int FORM_ROWS = 6;
    private static final int MAX_PITCH = 38;
    private static final int MIN_PITCH = 31;
    private static final int PREVIEW_HEIGHT = 24;
    private static final int AREA_ROW = 24;
    private static final int LEFT_W = 272;

    private final String zoneId;
    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, String> typed = new HashMap<>();
    private boolean initialized;
    private boolean enabled = true;
    private ZoneDef.Danger danger = ZoneDef.Danger.NORMAL;
    private final List<Requirement> entryRequirements = new ArrayList<>();
    private ZoneFeatures features = ZoneFeatures.DEFAULT;
    private ZoneCombatRules rules = ZoneCombatRules.inherit();
    private int[] pendingCorner;
    private long pendingSaveRevision = -1;

    public ZoneEditScreen(String zoneId, Screen parent) {
        super("Zone", parent);
        this.zoneId = zoneId;
    }

    @Override
    protected int maxGuiWidth() {
        return 640;
    }

    @Override
    protected int maxGuiHeight() {
        return 400;
    }

    private int contentTop() {
        return guiTop + 36;
    }

    private int footerY() {
        return guiTop + guiHeight - 28;
    }

    private int formX() {
        return guiLeft + Ui.PAD;
    }

    private int rightX() {
        return formX() + LEFT_W + 14;
    }

    private int rightW() {
        return guiWidth - Ui.PAD * 2 - LEFT_W - 14;
    }

    private int pitch() {
        int available = footerY() - 8 - PREVIEW_HEIGHT - contentTop();
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, available / FORM_ROWS));
    }

    private int rowY(int index) {
        return contentTop() + index * pitch();
    }

    private int half() {
        return (LEFT_W - 8) / 2;
    }

    @Override
    protected void buildContent() {
        ZoneDef zone = ClientState.zone(zoneId);
        if (zone == null) {
            addBackButton();
            return;
        }
        ClientState.setSelectedZone(zoneId);
        if (!initialized) {
            enabled = zone.enabled();
            danger = zone.safe() ? ZoneDef.Danger.SAFE : zone.danger();
            entryRequirements.clear();
            entryRequirements.addAll(zone.entryRequirements());
            features = zone.features();
            rules = zone.combatRules();
            initialized = true;
        }
        fields.clear();
        int second = formX() + half() + 8;
        field("name", formX(), 0, LEFT_W, zone.name());
        field("level_min", formX(), 1, half(), Integer.toString(zone.levelMin()));
        field("level_max", second, 1, half(), Integer.toString(zone.levelMax()));
        field("recommended_min", formX(), 2, half(), Integer.toString(zone.recommendedMin()));
        field("recommended_max", second, 2, half(), Integer.toString(zone.recommendedMax()));
        field("priority", formX(), 3, half(), Integer.toString(zone.priority()));
        field("xp_multiplier", second, 3, half(), trimDouble(zone.xpMultiplier()));
        field("transition_blocks", formX(), 4, half(), Integer.toString(zone.transitionBlocks()));
        field("radius", second, 4, half(), "32");

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Danger: " + dangerText()), button -> {
            capture();
            danger = danger.next();
            rebuild();
        }).bounds(formX(), rowY(5) + 12, LEFT_W, 20).build());

        int halfRight = (rightW() - Ui.GAP) / 2;
        int secondRight = rightX() + halfRight + Ui.GAP;
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Add area at a block...", halfRight - 10)),
                button -> startAreaPick("sphere")).bounds(rightX(), contentTop() + 16, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(pendingCorner == null
                        ? "Add a box (2 corners)..." : "Box: pick the 2nd corner...", halfRight - 10)),
                button -> startAreaPick("box")).bounds(secondRight, contentTop() + 16, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Clear shapes (whole dimension)", halfRight - 10)), button -> {
            send("zone_clear_areas", payload());
            Sfx.commit();
        }).bounds(rightX(), contentTop() + 40, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(lockLabel(), halfRight - 10)), button -> openGateEditor())
                .bounds(secondRight, contentTop() + 40, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Type: " + features.type().label(), halfRight - 10)),
                button -> open(new ZoneTypeScreen(this))).bounds(rightX(), contentTop() + 64, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(rulesLabel(), halfRight - 10)),
                button -> open(new ZoneRulesScreen(this))).bounds(secondRight, contentTop() + 64, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(features.isolateMobs() ? "Mobs: kept separate" : "Mobs: shared with outside",
                        halfRight - 10)),
                button -> open(new ZoneMobsScreen(this))).bounds(rightX(), contentTop() + 88, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(bossLabel(), halfRight - 10)),
                button -> open(new ZoneBossScreen(this))).bounds(secondRight, contentTop() + 88, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(extrasLabel(), halfRight - 10)),
                button -> open(new ZoneExtrasScreen(this))).bounds(rightX(), contentTop() + 112, halfRight, 20).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(dungeonLabel(), halfRight - 10)),
                button -> open(new ZoneDungeonScreen(this))).bounds(secondRight, contentTop() + 112, halfRight, 20).build());

        int listTop = listTop();
        int visible = Math.max(0, (footerY() - 8 - listTop) / AREA_ROW);
        for (int i = 0; i < Math.min(visible, zone.areas().size()); i++) {
            int index = i;
            addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Remove"), button -> {
                capture();
                CompoundTag tag = new CompoundTag();
                tag.putString("zone", zoneId);
                tag.putInt("index", index);
                send("zone_remove_area", tag);
                Sfx.remove();
            }).bounds(rightX() + rightW() - 72, listTop + i * AREA_ROW, 72, 18).build());
        }

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Back"), button -> goBack())
                .bounds(formX(), footerY(), 76, 22).build());
        addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Delete zone"), button -> {
            send("delete_zone", payload());
            Sfx.commit();
        }).bounds(formX() + 82, footerY(), 96, 22).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(enabled ? "Zone ON" : "Zone OFF"), button -> {
            capture();
            enabled = !enabled;
            rebuild();
        }).bounds(formX() + 184, footerY(), 90, 22).build());
        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Save"), button -> saveZone())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footerY(), 110, 22).build());
    }

    private String dangerText() {
        return switch (danger) {
            case SAFE -> "Safe (new mobs are not leveled)";
            case NORMAL -> "Normal";
            case DANGEROUS -> "Dangerous";
            case DEADLY -> "Deadly";
        };
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void field(String key, int x, int row, int width, String saved) {
        EditBox box = new EditBox(font, x, rowY(row) + 12, width, 18, net.schwarz.rotasutils.client.screen.Ui.text(""));
        box.setMaxLength(64);
        box.setValue(typed.getOrDefault(key, saved));
        addRenderableWidget(box);
        fields.put(key, box);
    }

    private void capture() {
        fields.forEach((key, box) -> typed.put(key, box.getValue()));
    }

    private CompoundTag payload() {
        CompoundTag tag = new CompoundTag();
        tag.putString("zone", zoneId);
        ZoneDef live = ClientState.zone(zoneId);
        tag.putLong("base_revision", live == null ? 0 : live.revision());
        EditBox name = fields.get("name");
        tag.putString("name", name == null ? "" : name.getValue());
        int min = Math.max(1, parseInt("level_min", 1));
        int max = Math.max(min, parseInt("level_max", 100));
        tag.putInt("level_min", min);
        tag.putInt("level_max", max);
        int recMin = Math.max(1, parseInt("recommended_min", min));
        tag.putInt("recommended_min", recMin);
        tag.putInt("recommended_max", Math.max(recMin, parseInt("recommended_max", max)));
        tag.putInt("priority", parseInt("priority", 0));
        tag.putDouble("xp_multiplier", parseDouble("xp_multiplier", 1.0));
        tag.putInt("transition_blocks", parseInt("transition_blocks", 16));
        tag.putString("danger", danger.name());
        tag.putBoolean("safe", danger == ZoneDef.Danger.SAFE);
        tag.putBoolean("enabled", enabled);
        tag.put("entry_requirements", Nbt.saveList(entryRequirements, Requirement::save));
        tag.put("features", features.save());
        tag.put("combat_rules", rules.save());
        return tag;
    }

    private String lockLabel() {
        if (entryRequirements.isEmpty()) {
            return "Entry lock: open to everyone";
        }
        long blocking = entryRequirements.stream().filter(requirement -> !requirement.recommendationOnly()).count();
        if (blocking == 0) {
            return "Entry lock: " + entryRequirements.size() + " advisory only";
        }
        return "Entry lock: " + blocking + " rule" + (blocking == 1 ? "" : "s");
    }

    private int listTop() {
        return contentTop() + 140;
    }

    private void open(Screen screen) {
        capture();
        minecraft.setScreen(screen);
    }

    public String zoneId() {
        return zoneId;
    }

    public ZoneFeatures features() {
        return features;
    }

    public void setFeatures(ZoneFeatures next) {
        features = next;
    }

    public ZoneCombatRules rules() {
        return rules;
    }

    public void setRules(ZoneCombatRules next) {
        rules = next;
    }

    public void applyType(ZoneType type) {
        capture();
        ZoneDef live = ClientState.zone(zoneId);
        if (live == null) {
            return;
        }
        String draftName = typed.getOrDefault("name", live.name()).trim();
        ZoneDef candidate = live.withSettings(draftName.isEmpty() ? live.name() : draftName,
                        live.levelMin(), live.levelMax(), live.priority(), enabled,
                        danger, live.recommendedMin(), live.recommendedMax(), live.xpMultiplier(), live.transitionBlocks(),
                        danger == ZoneDef.Danger.SAFE)
                .withCombatRules(rules).withFeatures(features);
        ZoneDef applied = ZonePresets.apply(type, candidate);
        danger = applied.safe() ? ZoneDef.Danger.SAFE : applied.danger();
        rules = applied.combatRules();
        features = applied.features();
    }

    private String rulesLabel() {
        int set = 0;
        if (rules.pvpEnabled().overridden()) set++;
        if (rules.hostileSpawningEnabled().overridden()) set++;
        if (rules.playerDamageTakenMultiplier().overridden()) set++;
        if (rules.playerDamageDealtMultiplier().overridden()) set++;
        if (rules.healingMultiplier().overridden()) set++;
        if (rules.keepInventory().overridden()) set++;
        if (rules.rotasXpLossPercentage().overridden()) set++;
        return set == 0 ? "Rules: all global" : "Rules: " + set + " set here";
    }

    private String dungeonLabel() {
        var dungeon = features.dungeon();
        if (!dungeon.enabled()) {
            return "Dungeon run: off";
        }
        return "Dungeon: " + dungeon.waves().size() + " wave" + (dungeon.waves().size() == 1 ? "" : "s")
                + (dungeon.bossProfile().isEmpty() ? "" : " + boss");
    }

    private String bossLabel() {
        int points = features.spawnPoints().size();
        return points == 0 ? "Bosses: none" : "Bosses: " + points + " point" + (points == 1 ? "" : "s");
    }

    private String extrasLabel() {
        List<String> parts = new ArrayList<>();
        if (features.messages().hasEnter() || !features.messages().leaveTitle().isEmpty()) {
            parts.add(ThaiText.phrase("titles"));
        }
        if (!features.effects().isEmpty()) {
            parts.add(ThaiText.phrase(features.effects().size() + " effect"
                    + (features.effects().size() == 1 ? "" : "s")));
        }
        if (features.movement().any()) {
            parts.add(ThaiText.phrase("movement rules"));
        }
        return "Titles, effects & movement: " + (parts.isEmpty() ? "none" : String.join(", ", parts));
    }

    private void openGateEditor() {
        capture();
        minecraft.setScreen(new ZoneGateScreen(zoneId, entryRequirements, this));
    }

    private void startAreaPick(String shape) {
        capture();
        requestPick("POSITION", shape);
    }

    private void saveZone() {
        String error = settingsError();
        if (error != null) {
            ClientState.feedback(false, ThaiText.phrase(error));
            Sfx.error();
            return;
        }
        ZoneDef live = ClientState.zone(zoneId);
        pendingSaveRevision = live == null ? -1 : live.revision();
        send("save_zone", payload());
        Sfx.commit();
    }

    private static final int FIELD_TEXT = 0xFFE0E0E0;
    private static final int FIELD_ERROR = 0xFFFF6B6B;
    private String firstError;

    private String settingsError() {
        firstError = null;
        fields.values().forEach(box -> box.setTextColor(FIELD_TEXT));
        Integer min = whole("level_min", 1, 10000, "Mob level from");
        Integer max = whole("level_max", 1, 10000, "Mob level to");
        if (min != null && max != null && max < min) {
            fail("level_max", "Mob level to must be at least Mob level from (" + min + ")");
        }
        Integer recMin = whole("recommended_min", 1, 10000, "Recommended player Lv");
        Integer recMax = whole("recommended_max", 1, 10000, "Recommended ...up to Lv");
        if (recMin != null && recMax != null && recMax < recMin) {
            fail("recommended_max", "Recommended ...up to Lv must be at least " + recMin);
        }
        whole("priority", -10000, 10000, "Priority");
        whole("transition_blocks", 0, 256, "Edge blend");
        EditBox xp = fields.get("xp_multiplier");
        if (xp != null) {
            try {
                double value = Double.parseDouble(xp.getValue().trim());
                if (!Double.isFinite(value) || value < 0 || value > 10) {
                    fail("xp_multiplier", "Kill XP multiplier must be a number from 0 to 10");
                }
            } catch (NumberFormatException invalid) {
                fail("xp_multiplier", "Kill XP multiplier must be a number from 0 to 10");
            }
        }
        return firstError;
    }

    private Integer whole(String key, int lo, int hi, String label) {
        EditBox box = fields.get(key);
        if (box == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(box.getValue().trim());
            if (value >= lo && value <= hi) {
                return value;
            }
        } catch (NumberFormatException invalid) {
        }
        fail(key, label + " must be a whole number from " + lo + " to " + hi);
        return null;
    }

    private void fail(String key, String message) {
        EditBox box = fields.get(key);
        if (box != null) {
            box.setTextColor(FIELD_ERROR);
        }
        if (firstError == null) {
            firstError = message;
        }
    }

    private int parseInt(String key, int fallback) {
        EditBox box = fields.get(key);
        if (box == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private double parseDouble(String key, double fallback) {
        EditBox box = fields.get(key);
        if (box == null) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(box.getValue().trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static String trimDouble(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void onDataRefreshed() {
        capture();
        ZoneDef live = ClientState.zone(zoneId);
        if (live != null && pendingSaveRevision >= 0 && live.revision() > pendingSaveRevision) {
            typed.clear();
            initialized = false;
            pendingSaveRevision = -1;
        }
        rebuild();
    }

    @Override
    public void onPick(String fieldKey, String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 3) {
            return;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (NumberFormatException invalid) {
            return;
        }
        capture();
        if (fieldKey.equals("box")) {
            if (pendingCorner == null) {
                pendingCorner = new int[]{x, y, z};
                rebuild();
                return;
            }
            CompoundTag tag = payload();
            tag.putInt("x1", Math.min(pendingCorner[0], x));
            tag.putInt("y1", Math.min(pendingCorner[1], y));
            tag.putInt("z1", Math.min(pendingCorner[2], z));
            tag.putInt("x2", Math.max(pendingCorner[0], x));
            tag.putInt("y2", Math.max(pendingCorner[1], y));
            tag.putInt("z2", Math.max(pendingCorner[2], z));
            pendingCorner = null;
            send("zone_add_box", tag);
            return;
        }
        CompoundTag tag = payload();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putInt("radius", Math.max(1, parseInt("radius", 32)));
        send("zone_add_sphere", tag);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ZoneDef zone = ClientState.zone(zoneId);
        if (zone == null) {
            Ui.wrapped(graphics, "This zone no longer exists. Go back and pick another.",
                    formX(), contentTop(), guiWidth - Ui.PAD * 2, Ui.BAD);
            return;
        }
        int second = formX() + half() + 8;
        Ui.label(graphics, "Zone details   " + zone.dimension().replace("minecraft:", "") + "   " + features.type().label(),
                formX(), contentTop() - 12, Ui.TEXT_DIM);
        Ui.label(graphics, "Name", formX(), rowY(0), Ui.TEXT_DIM);
        Ui.label(graphics, "Mob level from", formX(), rowY(1), Ui.TEXT_DIM);
        Ui.label(graphics, "Mob level to", second, rowY(1), Ui.TEXT_DIM);
        Ui.label(graphics, "Recommended player Lv", formX(), rowY(2), Ui.TEXT_DIM);
        Ui.label(graphics, "...up to Lv", second, rowY(2), Ui.TEXT_DIM);
        Ui.label(graphics, "Priority (higher wins)", formX(), rowY(3), Ui.TEXT_DIM);
        Ui.label(graphics, "Kill XP multiplier", second, rowY(3), Ui.TEXT_DIM);
        Ui.label(graphics, "Edge blend, blocks", formX(), rowY(4), Ui.TEXT_DIM);
        Ui.label(graphics, "'Add area' radius", second, rowY(4), Ui.TEXT_DIM);
        Ui.label(graphics, "How players should read it", formX(), rowY(5), Ui.TEXT_DIM);

        int previewY = rowY(5) + 38;
        if (previewY + 10 < footerY()) {
            Ui.label(graphics, Ui.truncate(preview(), LEFT_W), formX(), previewY, previewColor());
        }
        if (pendingCorner != null && previewY + 22 < footerY()) {
            Ui.label(graphics, Ui.truncate("Corner 1 at " + pendingCorner[0] + " " + pendingCorner[1] + " "
                    + pendingCorner[2] + " - pick the opposite corner.", LEFT_W), formX(), previewY + 12, Ui.WARN);
        }

        Ui.label(graphics, "Shapes this zone covers", rightX(), contentTop() - 12, Ui.TEXT_DIM);
        int listTop = listTop();
        if (zone.areas().isEmpty()) {
            Ui.wrapped(graphics, "No shapes yet, so this zone covers the whole dimension. "
                    + "Add an area or a box, or trace it in the world with the Zone Wand "
                    + "(sneak + right-click the air switches Sphere, Box and Outline).",
                    rightX(), listTop, rightW(), Ui.TEXT_DIM);
        } else {
            int visible = Math.max(0, (footerY() - 8 - listTop) / AREA_ROW);
            for (int i = 0; i < Math.min(visible, zone.areas().size()); i++) {
                int rowY = listTop + i * AREA_ROW;
                boolean hovered = Ui.inside(mouseX, mouseY, rightX(), rowY, rightW(), AREA_ROW - 4);
                Ui.rowCard(graphics, rightX(), rowY, rightW(), AREA_ROW - 4, hovered, false);
                Ui.label(graphics, Ui.truncate(zone.areas().get(i).label(), rightW() - 86),
                        rightX() + 6, rowY + 5, Ui.TEXT);
            }
            if (zone.areas().size() > visible) {
                Ui.label(graphics, "+" + (zone.areas().size() - visible) + " more",
                        rightX() + 6, listTop + visible * AREA_ROW + 2, Ui.TEXT_MUTED);
            }
        }
    }

    private String preview() {
        CompoundTag values = payload();
        String lock = entryRequirements.isEmpty() ? "" : "  |  " + ThaiText.phrase("entry locked");
        if (danger == ZoneDef.Danger.SAFE) {
            return ThaiText.phrase("Safe: new mobs here stay unleveled. XP x"
                    + trimDouble(values.getDouble("xp_multiplier"))) + lock;
        }
        return ThaiText.phrase("Mobs Lv " + values.getInt("level_min") + "-" + values.getInt("level_max")
                + "  |  XP x" + trimDouble(Math.max(0, Math.min(10, values.getDouble("xp_multiplier"))))
                + "  |  for Lv " + values.getInt("recommended_min") + "-" + values.getInt("recommended_max")
                ) + lock;
    }

    private int previewColor() {
        return switch (danger) {
            case SAFE -> Ui.ACCENT;
            case NORMAL -> Ui.TEXT;
            case DANGEROUS -> Ui.WARN;
            case DEADLY -> Ui.BAD;
        };
    }
}
