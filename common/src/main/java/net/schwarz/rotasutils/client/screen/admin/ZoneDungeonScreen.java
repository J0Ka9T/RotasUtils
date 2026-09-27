package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ZoneDungeon;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dungeon run of a zone: party size, key and fee to enter, waves, final boss, time limit, cooldown
 * and pay. Waves and the boss appear at the zone's spawn points (set under Bosses) or, without any,
 * around the party. Changes go to the zone editor's unsaved copy when leaving this screen.
 */
@Environment(EnvType.CLIENT)
public class ZoneDungeonScreen extends RotasScreen {
    private static final int ROW = 22;
    private final ZoneEditScreen editor;
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, String> typed = new HashMap<>();
    private final List<ZoneDungeon.Wave> waves = new ArrayList<>();
    private final List<String> rewards = new ArrayList<>();
    private boolean enabled;
    private String keyItem;
    private String boss;
    private String pendingWave;
    private String pendingBoss;
    private String pendingKey;
    private String pendingReward;

    public ZoneDungeonScreen(ZoneEditScreen editor) {
        super("Dungeon run", editor);
        this.editor = editor;
        ZoneDungeon dungeon = editor.features().dungeon();
        enabled = dungeon.enabled();
        keyItem = dungeon.keyItem();
        boss = dungeon.bossProfile();
        waves.addAll(dungeon.waves());
        rewards.addAll(dungeon.rewardItems());
        typed.put("players", String.valueOf(dungeon.maxPlayers()));
        typed.put("key_count", String.valueOf(dungeon.keyCount()));
        typed.put("fee", String.valueOf(dungeon.entryGold()));
        typed.put("minutes", String.valueOf(Math.max(1, dungeon.timeLimitSeconds() / 60)));
        typed.put("cooldown", String.valueOf(dungeon.cooldownSeconds() / 60));
        typed.put("gold", String.valueOf(dungeon.rewardGold()));
        typed.put("xp", String.valueOf(dungeon.rewardXp()));
        typed.put("reward_count", "1");
    }

    @Override
    protected int maxGuiWidth() {
        return 660;
    }

    @Override
    protected int maxGuiHeight() {
        return 460;
    }

    private int columnWidth() {
        return (guiWidth - Ui.PAD * 2 - Ui.GAP) / 2;
    }

    private int rightX() {
        return guiLeft + Ui.PAD + columnWidth() + Ui.GAP;
    }

    @Override
    protected void buildContent() {
        boxes.clear();
        int x = guiLeft + Ui.PAD;
        int w = columnWidth();
        int y = guiTop + 40;
        int small = (w - Ui.GAP * 2) / 3;

        addRenderableWidget((enabled ? Ui.primaryButton(Ui.text("Dungeon run: ON"), b -> toggle())
                : Ui.button(Ui.text("Dungeon run: OFF"), b -> toggle())).bounds(x, y, w, 20).build());

        // Numbers: three per row, labelled above.
        box("players", x, y + 40, small, 2);
        box("minutes", x + small + Ui.GAP, y + 40, small, 4);
        box("cooldown", x + (small + Ui.GAP) * 2, y + 40, small, 6);
        box("fee", x, y + 80, small, 9);
        box("gold", x + small + Ui.GAP, y + 80, small, 9);
        box("xp", x + (small + Ui.GAP) * 2, y + 80, small, 9);

        // Entry key.
        int keyY = y + 120;
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(keyItem.isEmpty() ? "Key: none (pick an item)"
                : "Key: " + keyItem.replace("minecraft:", ""), w - small - Ui.GAP - 40)), b -> {
            capture();
            minecraft.setScreen(PickerScreen.open(ParamKind.ITEM, this, id -> {
                if (!id.isEmpty()) {
                    pendingKey = id;
                }
            }, false));
        }).bounds(x, keyY, w - small - Ui.GAP - 28, 20).build());
        addRenderableWidget(Ui.dangerButton(Ui.text("x"), b -> {
            capture();
            keyItem = "";
            rebuild();
        }).bounds(x + w - small - Ui.GAP - 24, keyY, 22, 20).build());
        box("key_count", x + w - small, keyY, small, 2);

        // Final boss.
        int bossY = keyY + 40;
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(boss.isEmpty() ? "Final boss: none (pick a Mob Setup)"
                : "Final boss: " + setupName(boss), w - 40)), b -> {
            capture();
            minecraft.setScreen(new ZoneSetupChoiceScreen(this, editor.zoneId(), id -> pendingBoss = id));
        }).bounds(x, bossY, w - 26, 20).build());
        addRenderableWidget(Ui.dangerButton(Ui.text("x"), b -> {
            capture();
            boss = "";
            rebuild();
        }).bounds(x + w - 22, bossY, 22, 20).build());

        // Reward items.
        int rewardTop = bossY + 40;
        int footer = guiTop + guiHeight - 28;
        int rewardRows = Math.max(0, Math.min(rewards.size(), (footer - 30 - rewardTop) / ROW));
        for (int i = 0; i < rewardRows; i++) {
            int index = i;
            addRenderableWidget(Ui.dangerButton(Ui.text("Remove"), b -> {
                capture();
                rewards.remove(index);
                Sfx.remove();
                rebuild();
            }).bounds(x + w - 70, rewardTop + i * ROW, 70, 18).build());
        }
        if (rewards.size() < ZoneDungeon.MAX_REWARD_ITEMS) {
            int addY = rewardTop + rewardRows * ROW;
            addRenderableWidget(Ui.button(Ui.text("+ Reward item"), b -> {
                capture();
                minecraft.setScreen(PickerScreen.open(ParamKind.ITEM, this, id -> {
                    if (!id.isEmpty()) {
                        pendingReward = id;
                    }
                }, false));
            }).bounds(x, addY, w - small - Ui.GAP, 20).build());
            box("reward_count", x + w - small, addY, small, 4);
        }

        // Waves.
        int rx = rightX();
        int waveTop = y + 20;
        int waveRows = Math.max(0, Math.min(waves.size(), (footer - 34 - waveTop) / ROW));
        for (int i = 0; i < waveRows; i++) {
            int index = i;
            int rowY = waveTop + i * ROW;
            addRenderableWidget(Ui.button(Ui.text("-"), b -> changeCount(index, -1)).bounds(rx + w - 118, rowY, 22, 18).build());
            addRenderableWidget(Ui.button(Ui.text("+"), b -> changeCount(index, 1)).bounds(rx + w - 94, rowY, 22, 18).build());
            addRenderableWidget(Ui.dangerButton(Ui.text("Remove"), b -> {
                capture();
                waves.remove(index);
                Sfx.remove();
                rebuild();
            }).bounds(rx + w - 68, rowY, 68, 18).build());
        }
        if (waves.size() < ZoneDungeon.MAX_WAVES) {
            addRenderableWidget(Ui.button(Ui.text("+ Add wave"), b -> {
                capture();
                minecraft.setScreen(new ZoneSetupChoiceScreen(this, editor.zoneId(), id -> pendingWave = id));
            }).bounds(rx, waveTop + waveRows * ROW + 4, 130, 20).build());
        }

        addBackButton();
        addRenderableWidget(Ui.primaryButton(Ui.text("Done"), b -> goBack())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footer, 110, 22).build());
    }

    private void box(String key, int x, int y, int width, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, 18, Ui.text(""));
        box.setMaxLength(maxLength);
        box.setFilter(value -> value.chars().allMatch(Character::isDigit));
        box.setValue(typed.getOrDefault(key, ""));
        addRenderableWidget(box);
        boxes.put(key, box);
    }

    private void toggle() {
        capture();
        enabled = !enabled;
        Sfx.select();
        rebuild();
    }

    private void changeCount(int index, int delta) {
        capture();
        ZoneDungeon.Wave wave = waves.get(index);
        int count = Math.max(1, Math.min(ZoneDungeon.Wave.MAX_COUNT, wave.count() + delta));
        waves.set(index, new ZoneDungeon.Wave(wave.profile(), count));
        Sfx.select();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void capture() {
        boxes.forEach((key, box) -> typed.put(key, box.getValue()));
    }

    @Override
    public void tick() {
        super.tick();
        boolean changed = false;
        if (pendingWave != null) {
            if (!pendingWave.isEmpty() && waves.size() < ZoneDungeon.MAX_WAVES) {
                waves.add(new ZoneDungeon.Wave(pendingWave, 4));
                Sfx.add();
            }
            pendingWave = null;
            changed = true;
        }
        if (pendingBoss != null) {
            if (!pendingBoss.isEmpty()) {
                boss = pendingBoss;
            }
            pendingBoss = null;
            changed = true;
        }
        if (pendingKey != null) {
            keyItem = pendingKey;
            pendingKey = null;
            changed = true;
        }
        if (pendingReward != null) {
            int count = Math.max(1, number("reward_count", 1));
            rewards.add(pendingReward + " " + count);
            pendingReward = null;
            Sfx.add();
            changed = true;
        }
        if (changed) {
            rebuild();
        }
    }

    private long number(String key, long fallback) {
        String value = typed.getOrDefault(key, "");
        try {
            return value.isBlank() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private int number(String key, int fallback) {
        return (int) Math.min(Integer.MAX_VALUE, number(key, (long) fallback));
    }

    /** Writes everything to the zone editor; false (with feedback) when a value is invalid. */
    private boolean apply() {
        capture();
        try {
            ZoneDungeon dungeon = new ZoneDungeon(enabled, number("players", 4), keyItem, Math.max(1, number("key_count", 1)),
                    number("fee", 0L), number("minutes", 15) * 60, number("cooldown", 60) * 60, waves, boss,
                    number("gold", 0L), number("xp", 0L), rewards);
            editor.setFeatures(editor.features().withDungeon(dungeon));
            return true;
        } catch (IllegalArgumentException invalid) {
            ClientState.feedback(false, invalid.getMessage());
            return false;
        }
    }

    @Override
    protected void goBack() {
        if (apply()) {
            super.goBack();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int w = columnWidth();
        int y = guiTop + 40;
        int small = (w - Ui.GAP * 2) / 3;
        Ui.label(graphics, "Party size", x, y + 29, Ui.TEXT_DIM);
        Ui.label(graphics, "Time (minutes)", x + small + Ui.GAP, y + 29, Ui.TEXT_DIM);
        Ui.label(graphics, "Cooldown (min)", x + (small + Ui.GAP) * 2, y + 29, Ui.TEXT_DIM);
        Ui.label(graphics, "Entry fee (gold)", x, y + 69, Ui.TEXT_DIM);
        Ui.label(graphics, "Pay (gold)", x + small + Ui.GAP, y + 69, Ui.TEXT_DIM);
        Ui.label(graphics, "Pay (XP)", x + (small + Ui.GAP) * 2, y + 69, Ui.TEXT_DIM);
        Ui.label(graphics, "Key to enter (consumed per player)          count", x, y + 109, Ui.TEXT_DIM);
        int bossY = y + 160;
        Ui.label(graphics, "After the last wave", x, bossY - 11, Ui.TEXT_DIM);
        int rewardTop = bossY + 40;
        Ui.label(graphics, "Reward items for everyone who finishes", x, rewardTop - 11, Ui.TEXT_DIM);
        int footer = guiTop + guiHeight - 28;
        int rewardRows = Math.max(0, Math.min(rewards.size(), (footer - 30 - rewardTop) / ROW));
        for (int i = 0; i < rewardRows; i++) {
            Ui.label(graphics, Ui.truncate(rewards.get(i).replace("minecraft:", ""), w - 80), x, rewardTop + i * ROW + 5, Ui.TEXT);
        }

        int rx = rightX();
        Ui.label(graphics, "Waves, in order (monsters appear at the zone's spawn points)", rx, y + 5, Ui.TEXT_DIM);
        int waveTop = y + 20;
        int waveRows = Math.max(0, Math.min(waves.size(), (footer - 34 - waveTop) / ROW));
        if (waves.isEmpty()) {
            Ui.label(graphics, "No waves. Add one, or only set a final boss.", rx, waveTop + 5, Ui.TEXT_MUTED);
        }
        for (int i = 0; i < waveRows; i++) {
            ZoneDungeon.Wave wave = waves.get(i);
            Ui.label(graphics, Ui.truncate((i + 1) + ". " + setupName(wave.profile()), w - 170), rx, waveTop + i * ROW + 5, Ui.TEXT);
            Ui.labelRight(graphics, "x" + wave.count(), rx + w - 124, waveTop + i * ROW + 5, Ui.ACCENT);
        }
    }

    private static String setupName(String id) {
        for (ClientKernelState.MonsterEntry entry : ClientKernelState.monsters()) {
            if (entry.id().equals(id)) {
                return entry.name() == null || entry.name().isBlank() ? entry.id() : entry.name();
            }
        }
        return id + " (missing)";
    }
}
