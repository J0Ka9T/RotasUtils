package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.MobSetupForm;
import net.schwarz.rotasutils.core.MobSpawnRules;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class MobSetupEditScreen extends RotasScreen {
    private static final int SIDE_W = 200;

    private enum Page {
        MOBS("1  Mobs & name"),
        LEVEL("2  Level"),
        STRENGTH("3  Strength"),
        REWARDS("4  Rewards"),
        SPAWN("5  Spawning"),
        DEFEAT("6  Defeat"),
        BODY("7  Body");

        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private record Label(String text, int x, int y, int color) {
    }

    private String id;
    private final MobSetupForm form;
    private String baseline;
    private Page page = Page.MOBS;
    private final MobModelCache models = new MobModelCache(2);
    private final List<Label> labels = new ArrayList<>();
    private final List<String> mobRows = new ArrayList<>();
    private final Map<String, String> strengthText = new HashMap<>();
    private final Set<String> invalidStrengthFields = new HashSet<>();
    private ScrollPanel mobList;
    private final List<String> zoneRows = new ArrayList<>();
    private Button saveButton;
    private double lastClickX;
    private int sideX;
    private int sideY;
    private int sideH;
    private int mainX;
    private int mainY;
    private int mainW;
    private int mainH;

    private boolean zoneScope;

    public MobSetupEditScreen(String id, MobSetupForm form, Screen parent) {
        this(id, form, parent, false);
    }

    public MobSetupEditScreen(String id, MobSetupForm form, Screen parent, boolean startZoneScoped) {
        super("Mob Setup", parent);
        this.id = id;
        this.form = new MobSetupForm(form.body());
        this.baseline = id == null ? "" : this.form.json();
        this.zoneScope = startZoneScoped || this.form.zoneScoped();
    }

    private String serverBody() {
        return id == null ? null : ClientKernelState.monsters().stream()
                .filter(entry -> entry.id().equals(id)).map(ClientKernelState.MonsterEntry::body).findFirst().orElse(null);
    }

    @Override
    protected Refresh refreshMode() {
        return Refresh.BANNER;
    }

    @Override
    protected Object watchedSource() {
        return serverBody();
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 520);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        labels.clear();
        mobList = null;
        setHeader("Mob Setup: " + title());

        int footerY = guiTop + guiHeight - 28;
        int tabsY = guiTop + 34;
        sideX = guiLeft + Ui.PAD;
        sideY = tabsY;
        sideH = footerY - Ui.GAP * 2 - tabsY;
        mainX = sideX + SIDE_W + Ui.GAP * 3;
        mainW = guiLeft + guiWidth - Ui.PAD - mainX;
        mainY = tabsY + 28;
        mainH = footerY - Ui.GAP * 2 - mainY;

        int tabW = (mainW - Ui.GAP * (Page.values().length - 1)) / Page.values().length;
        for (Page value : Page.values()) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(value.label, tabW - 10)), button -> {
                if (page == Page.STRENGTH && !invalidStrengthFields.isEmpty()) {
                    ClientState.feedback(false, "Fix or reset invalid stat values before leaving this page.");
                    return;
                }
                page = value;
                Sfx.page();
                rebuild();
            }).style(value == page ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                    .bounds(mainX + value.ordinal() * (tabW + Ui.GAP), tabsY, tabW, 22).build());
        }
        switch (page) {
            case MOBS -> buildMobs();
            case LEVEL -> buildLevel();
            case STRENGTH -> buildStrength();
            case REWARDS -> buildRewards();
            case SPAWN -> buildSpawn();
            case DEFEAT -> buildDefeat();
            case BODY -> buildBody();
        }

        addBackButton();
        if (id != null) {
            addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Delete"), button -> minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("id", id);
                    send("mob_setup_delete", payload);
                    Sfx.remove();
                    minecraft.setScreen(parentScreen());
                } else {
                    minecraft.setScreen(this);
                }
            }, net.schwarz.rotasutils.client.screen.Ui.text("Delete this mob setup?"),
                    net.schwarz.rotasutils.client.screen.Ui.text("New mobs spawn as normal again. Mobs already changed keep their level.")))).bounds(guiLeft + 64, footerY, 70, 22).build());
            if (!form.zoneScoped()) {
                addRenderableWidget(Ui.button(Ui.text("Copy for a zone"), button -> {
                    ClientState.feedback(true, "Copied. Pick the zones for this version, then save.");
                    minecraft.setScreen(new MobSetupEditScreen(null, form.copyForZone(), parentScreen(), true));
                }).bounds(guiLeft + 138, footerY, 130, 22).build());
            }
        }
        if (page == Page.STRENGTH) {
            int saveX = guiLeft + guiWidth - Ui.PAD - 110;
            int resetX = guiLeft + (id == null ? 64 : form.zoneScoped() ? 138 : 272);
            int resetWidth = Math.min(96, saveX - resetX - 8);
            if (resetWidth >= 60) {
                addRenderableWidget(Ui.button(Ui.text("Reset strength"), button -> resetStrength())
                        .bounds(resetX, footerY, resetWidth, 22).build());
            }
        }
        saveButton = addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Save"), button -> save())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footerY, 110, 22).build());
        changed();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private String title() {
        List<String> mobs = form.entities();
        String mob = mobs.isEmpty() ? "no mobs yet" : MobModelCache.displayName(mobs.get(0))
                + (mobs.size() > 1 ? " +" + (mobs.size() - 1) : "");
        List<String> zones = form.scopeZones();
        return zones.isEmpty() ? mob : mob + "  ·  " + zoneNames(zones);
    }

    static String zoneNames(List<String> zoneIds) {
        List<String> names = new ArrayList<>();
        for (String zoneId : zoneIds) {
            var zone = ClientState.zones().get(zoneId);
            names.add(zone == null ? zoneId : zone.name());
        }
        return String.join(", ", names);
    }

    private boolean dirty() {
        return !form.json().equals(baseline);
    }

    private void changed() {
        if (saveButton != null) {
            saveButton.active = dirty() && invalidStrengthFields.isEmpty();
            saveButton.setMessage(net.schwarz.rotasutils.client.screen.Ui.text(dirty() ? "Save" : "Saved"));
        }
        setHeader("Mob Setup: " + title());
    }

private void buildMobs() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        labels.add(new Label("Which mobs use this setup (" + form.entities().size() + ")", x, y + 4, Ui.TEXT_BRIGHT));
        addRenderableWidget(Ui.primaryButton(Ui.text("+ Add or remove mobs"), button ->
                minecraft.setScreen(EntityPickerScreen.many(this, form.entities(), ids -> {
                    form.setEntities(ids);
                    Sfx.commit();
                }))).bounds(x + w - 160, y, 160, 18).build());
        if (!form.entities().isEmpty()) {
            addRenderableWidget(Ui.dangerButton(Ui.text("Clear all"), button -> {
                form.setEntities(List.of());
                Sfx.remove();
                rebuild();
            }).bounds(x + w - 250, y, 86, 18).build());
        }
        y += 24;
        mobRows.clear();
        mobRows.addAll(form.entities());
        int scopeRoom = zoneScope ? 150 : 44;
        int listH = Math.max(46, Math.min(160, mainH - 176 - scopeRoom));
        mobList = new ScrollPanel(x, y, w, listH, 24).withoutBackground().rowHitInsets(0, 2);
        mobList.setRows(mobRows.size(), this::renderMobRow, this::clickMobRow);
        registerPanel(mobList);
        labels.add(new Label(form.usesOtherSelectors()
                ? "This setup also matches mobs by tag, mod or place (edit that in the advanced editor)."
                : "Right-click a mob or press Remove to take it out.",
                x, y + listH + 3, form.usesOtherSelectors() ? Ui.WARN : Ui.TEXT_MUTED));
        y += listH + 18;

        labels.add(new Label("Where these settings apply", x, y, Ui.TEXT_BRIGHT));
        int half = (w - Ui.GAP) / 2;
        spawnChoice(x, y + 12, half, "Everywhere (global)", zoneScope ? "zone" : "global", "global", value -> {
            zoneScope = false;
            form.setScopeZones(List.of());
        });
        spawnChoice(x + half + Ui.GAP, y + 12, half, "Only in chosen zones", zoneScope ? "zone" : "global", "zone",
                value -> zoneScope = true);
        y += 38;
        if (zoneScope) {
            zoneRows.clear();
            ClientState.zones().values().stream()
                    .sorted(java.util.Comparator.comparing(zone -> zone.name().toLowerCase(java.util.Locale.ROOT)))
                    .forEach(zone -> zoneRows.add(zone.id()));
            if (zoneRows.isEmpty()) {
                labels.add(new Label("No zones yet. Make one in Admin > Level Zones.", x, y, Ui.WARN));
                y += 16;
            } else {
                int zonesH = Math.min(18 * 4, Math.max(36, zoneRows.size() * 18));
                ScrollPanel scopeList = new ScrollPanel(x, y, w, zonesH, 18).withoutBackground().rowHitInsets(0, 1);
                scopeList.setRows(zoneRows.size(), this::renderScopeZoneRow, (index, button) -> {
                    form.toggleScopeZone(zoneRows.get(index));
                    Sfx.select();
                    rebuild();
                });
                registerPanel(scopeList);
                y += zonesH + 2;
            }
            labels.add(new Label("Inside these zones this setup wins over the global one for the same mob.", x, y, Ui.TEXT_MUTED));
            y += 16;
        }

        labels.add(new Label("Name above their head", x, y, Ui.TEXT_BRIGHT));
        labels.add(new Label("{name} = mob name    {level} = level    {tier} = Common or Elite", x, y + 11, Ui.TEXT_MUTED));
        EditBox name = new EditBox(font, x, y + 24, w, 18, Component.empty());
        name.setMaxLength(128);
        name.setValue(form.name());
        name.setResponder(value -> {
            form.setName(value);
            changed();
        });
        addRenderableWidget(name);
        y += 48;
        int presetW = (w - Ui.GAP * 2) / 3;
        preset(x, y, presetW, "Zombie [Lv 5]", "{name} [Lv {level}]");
        preset(x + presetW + Ui.GAP, y, presetW, "Zombie [Elite 5]", "{name} [{tier} {level}]");
        preset(x + (presetW + Ui.GAP) * 2, y, presetW, "Keep normal name", "");
    }

    private void preset(int x, int y, int w, String label, String template) {
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(label), button -> {
            form.setName(template);
            rebuild();
        }).bounds(x, y, w, 20).build());
    }

    private void buildLevel() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        labels.add(new Label("How their level is chosen", x, y, Ui.TEXT_BRIGHT));
        y += 14;
        int choiceW = (w - Ui.GAP * 2) / 3;
        choice(x, y, choiceW, "Match nearest player", "NEAREST_PLAYER", () -> {
            form.setStrategy("NEAREST_PLAYER");
            if (form.max() == form.min()) form.setMax(100);
        });
        choice(x + choiceW + Ui.GAP, y, choiceW, "Always the same", "FIXED", () -> form.setFixed(form.fixedLevel()));
        choice(x + (choiceW + Ui.GAP) * 2, y, choiceW, "Random between", "RANDOM", () -> {
            form.setStrategy("RANDOM");
            if (form.max() == form.min()) form.setMax(form.min() + 10);
        });
        y += 34;
        switch (form.strategy()) {
            case "NEAREST_PLAYER" -> {
                intStepper(x, y, w, "Levels above the player (use - for below)", form.offset(), 1, 5, form::setOffset);
                y += 40;
                intStepper(x, y, w, "Never lower than", form.min(), 1, 10, form::setMin);
                y += 40;
                intStepper(x, y, w, "Never higher than", form.max(), 1, 10, form::setMax);
            }
            case "FIXED" -> intStepper(x, y, w, "Level", form.fixedLevel(), 1, 10, form::setFixed);
            case "RANDOM" -> {
                intStepper(x, y, w, "Lowest level", form.min(), 1, 10, form::setMin);
                y += 40;
                intStepper(x, y, w, "Highest level", form.max(), 1, 10, form::setMax);
            }
            default -> labels.add(new Label("This setup uses a custom level rule (" + form.strategy()
                    + "). Pick an option above to replace it, or keep it and edit it in the advanced editor.", x, y, Ui.WARN));
        }
    }

    private void choice(int x, int y, int w, String label, String strategy, Runnable select) {
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(label), button -> {
            select.run();
            Sfx.select();
            rebuild();
        }).style(form.strategy().equals(strategy) ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                .bounds(x, y, w, 22).build());
    }

    private record StatRow(String name, String attribute, Attribute vanilla, boolean percentAdd) {
    }

    private static final List<StatRow> STAT_ROWS = List.of(
            new StatRow("Health", MobSetupForm.HEALTH, Attributes.MAX_HEALTH, false),
            new StatRow("Damage", MobSetupForm.DAMAGE, Attributes.ATTACK_DAMAGE, false),
            new StatRow("Armor", MobSetupForm.ARMOR, Attributes.ARMOR, false),
            new StatRow("Toughness", "minecraft:generic.armor_toughness", Attributes.ARMOR_TOUGHNESS, false),
            new StatRow("Speed", MobSetupForm.SPEED, Attributes.MOVEMENT_SPEED, false),
            new StatRow("Follow range", "minecraft:generic.follow_range", Attributes.FOLLOW_RANGE, false),
            new StatRow("Attack knockback", "minecraft:generic.attack_knockback", Attributes.ATTACK_KNOCKBACK, false),
            new StatRow("Knockback resist", "minecraft:generic.knockback_resistance", Attributes.KNOCKBACK_RESISTANCE, false));

    private int strengthRowHeight;
    private int strengthNameWidth;
    private int strengthInputWidth;
    private int strengthMultiplierX;
    private int strengthPerLevelX;
    private int strengthAddX;
    private int strengthPreviewX;
    private int strengthX;
    private boolean compactStrength;
    private int strengthTop;
    private int strengthHelpY;

    private void buildStrength() {
        int availableWidth = mainW - 24;
        compactStrength = availableWidth < 500 || mainH < 250;
        strengthRowHeight = mainH < 250 ? 16 : mainH < 310 ? 18 : 22;
        strengthNameWidth = compactStrength ? 68 : 104;
        strengthInputWidth = compactStrength ? 40 : 56;
        strengthX = mainX + 12;
        int gap = compactStrength ? 3 : 4;
        strengthMultiplierX = strengthX + strengthNameWidth + gap;
        strengthPerLevelX = strengthMultiplierX + strengthInputWidth + gap;
        strengthAddX = strengthPerLevelX + strengthInputWidth + gap;
        strengthPreviewX = strengthAddX + strengthInputWidth + (compactStrength ? 6 : 12);
        int y = mainY + (mainH < 250 ? 26 : 34);
        strengthTop = y;
        for (StatRow row : STAT_ROWS) {
            String key = row.attribute();
            numberBox(key + "/multiplier", strengthMultiplierX, y + 1, strengthInputWidth,
                    MobSetupScreen.trim(form.multiplier(key)), 0, 100,
                    value -> form.setScale(key, value, form.perLevel(key), form.add(key)));
            numberBox(key + "/per_level", strengthPerLevelX, y + 1, strengthInputWidth,
                    MobSetupScreen.trim(Math.round(form.perLevel(key) * 10000) / 100.0), -100, 1000,
                    value -> form.setScale(key, form.multiplier(key), value / 100.0, form.add(key)));
            numberBox(key + "/add", strengthAddX, y + 1, strengthInputWidth,
                    MobSetupScreen.trim(form.add(key)), -10_000, 10_000,
                    value -> form.setScale(key, form.multiplier(key), form.perLevel(key), value));
            y += strengthRowHeight;
        }
        y += compactStrength ? 2 : 6;
        if (form.customTiers()) {
            labels.add(new Label("This setup uses custom tiers; change them in the advanced editor.", strengthX, y + 4, Ui.WARN));
        } else {
            labels.add(new Label("Elite chance %", strengthX, y + 5, Ui.TEXT_BRIGHT));
            numberBox("elite_chance", strengthX + Math.max(96, availableWidth / 3), y + 1, 56,
                    String.valueOf(form.eliteChance()), 0, 100,
                    value -> form.setEliteChance((int) Math.round(value)));
        }
        strengthHelpY = y + (compactStrength ? 20 : 24);
    }

    private void resetStrength() {
        strengthText.clear();
        invalidStrengthFields.clear();
        for (StatRow row : STAT_ROWS) {
            form.setScale(row.attribute(), 1, 0, 0);
        }
        form.setEliteChance(0);
        Sfx.remove();
        rebuild();
    }

    private void numberBox(String fieldKey, int x, int y, int w, String value, double min, double max,
                           java.util.function.DoubleConsumer onChange) {
        EditBox box = new EditBox(font, x, y, w, 16, net.schwarz.rotasutils.client.screen.Ui.text(""));
        box.setMaxLength(10);
        box.setFilter(text -> text.matches("-?\\d*(\\.\\d*)?"));
        box.setTooltip(Tooltip.create(Ui.text("Allowed: " + min + " to " + max)));
        box.setValue(strengthText.getOrDefault(fieldKey, value));
        box.setTextColor(invalidStrengthFields.contains(fieldKey) ? Ui.BAD : Ui.TEXT_BRIGHT);
        box.setResponder(text -> {
            strengthText.put(fieldKey, text);
            try {
                double parsed = Double.parseDouble(text.trim());
                if (Double.isFinite(parsed) && parsed >= min && parsed <= max) {
                    invalidStrengthFields.remove(fieldKey);
                    box.setTextColor(Ui.TEXT_BRIGHT);
                    onChange.accept(parsed);
                    changed();
                } else {
                    invalidStrengthFields.add(fieldKey);
                    box.setTextColor(Ui.BAD);
                }
            } catch (NumberFormatException ignored) {
                invalidStrengthFields.add(fieldKey);
                box.setTextColor(Ui.BAD);
            }
            if (saveButton != null) {
                saveButton.active = dirty() && invalidStrengthFields.isEmpty();
            }
        });
        addRenderableWidget(box);
    }

    private void renderStrengthTable(GuiGraphics graphics) {
        int x = strengthX;
        int availableWidth = mainW - 24;
        int head = strengthTop - 14;
        Ui.label(graphics, "Stat", x, head, Ui.TEXT_MUTED);
        Ui.label(graphics, Ui.truncate(compactStrength ? "×" : "Multiplier", strengthInputWidth),
                strengthMultiplierX, head, Ui.TEXT_MUTED);
        Ui.label(graphics, Ui.truncate(compactStrength ? "%/lv" : "% per level", strengthInputWidth),
                strengthPerLevelX, head, Ui.TEXT_MUTED);
        Ui.label(graphics, Ui.truncate("Flat", strengthInputWidth), strengthAddX, head, Ui.TEXT_MUTED);
        int[] levels = previewLevels();
        int col = strengthPreviewX;
        int colW = Math.max(1, (availableWidth - (col - x)) / (levels.length + 1));
        Ui.label(graphics, Ui.truncate("Base", colW - 2), col, head, Ui.TEXT_MUTED);
        for (int i = 0; i < levels.length; i++) {
            Ui.label(graphics, Ui.truncate("Lv " + levels[i], colW - 2), col + colW * (i + 1), head, Ui.ACCENT);
        }
        List<String> mobs = form.entities();
        LivingEntity living = models.living(mobs.isEmpty() ? null : MobModelCache.type(mobs.get(0)));
        int y = strengthTop;
        boolean anyCapped = false;
        for (StatRow row : STAT_ROWS) {
            int textY = y + Math.max(3, (strengthRowHeight - 9) / 2);
            Ui.label(graphics, Ui.truncate(row.name(), strengthNameWidth - 4), x, textY, Ui.TEXT_BRIGHT);
            var instance = living == null ? null : living.getAttribute(row.vanilla());
            if (instance == null) {
                Ui.label(graphics, Ui.truncate(living == null ? "?" : "none", colW - 2), col, textY, Ui.WARN);
            } else {
                double base = instance.getBaseValue();
                boolean scaledNothing = Math.abs(base) < 1e-9 && Math.abs(form.add(row.attribute())) < 1e-9
                        && (Math.abs(form.multiplier(row.attribute()) - 1) > 1e-9 || Math.abs(form.perLevel(row.attribute())) > 1e-9);
                Ui.label(graphics, Ui.truncate(MobStatFormat.fmt(base), colW - 2), col, textY,
                        scaledNothing ? Ui.WARN : Ui.TEXT_MUTED);
                for (int i = 0; i < levels.length; i++) {
                    double raw = form.valueAt(row.attribute(), base, levels[i]);
                    double value = row.vanilla().sanitizeValue(raw);
                    boolean capped = Math.abs(value - raw) > 1e-6;
                    anyCapped |= capped;
                    String text = Ui.truncate(MobStatFormat.fmt(value) + (capped ? "*" : ""), colW - 2);
                    Ui.label(graphics, text, col + colW * (i + 1), textY, capped ? Ui.WARN
                            : value > base + 1e-6 ? Ui.GOOD : value < base - 1e-6 ? Ui.BAD : Ui.TEXT);
                }
            }
            y += strengthRowHeight;
        }
        if (!compactStrength) {
            Ui.label(graphics, "Multiplier: 1 = normal, 2 = twice the base stat.   Per level: added percent each level.",
                    x, strengthHelpY, Ui.TEXT_MUTED);
            Ui.label(graphics, "Flat add is applied after scaling. Preview values include Minecraft's attribute limits.",
                    x, strengthHelpY + 12, Ui.TEXT_MUTED);
            if (!invalidStrengthFields.isEmpty()) {
                Ui.label(graphics, "Fix highlighted values: × 0–100, level % −100–1000, flat −10000–10000.",
                        x, strengthHelpY + 24, Ui.BAD);
            }
            if (anyCapped) {
                Ui.label(graphics, "* Minecraft's maximum reached for this stat.", x,
                        strengthHelpY + (invalidStrengthFields.isEmpty() ? 24 : 36), Ui.WARN);
            }
        }
    }

    int[] previewLevels() {
        int low = Math.max(1, form.strategy().equals("FIXED") ? form.fixedLevel() : form.min());
        int high = Math.max(low, form.strategy().equals("FIXED") ? form.fixedLevel() : form.max());
        if (mainW < 520) {
            return new int[]{(low + high) / 2};
        }
        return java.util.stream.IntStream.of(low, (low + high) / 2, high).distinct().toArray();
    }

    private void buildRewards() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        stepper(x, y, w, "XP for a kill", String.valueOf(form.baseXp()),
                () -> form.setBaseXp(form.baseXp() - (hasShiftDown() ? 50 : 5)),
                () -> form.setBaseXp(form.baseXp() + (hasShiftDown() ? 50 : 5)));
        y += 38;
        stepper(x, y, w, "Extra XP for each level", "+" + MobSetupScreen.trim(form.xpPerLevel()),
                () -> form.setXpPerLevel(form.xpPerLevel() - (hasShiftDown() ? 10 : 1)),
                () -> form.setXpPerLevel(form.xpPerLevel() + (hasShiftDown() ? 10 : 1)));
        y += 44;
        labels.add(new Label("Extra loot when killed", x, y, Ui.TEXT_BRIGHT));
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(form.loot().isEmpty() ? "None   (click to choose a loot table)"
                : form.loot() + "   (click to change)", w - 12)), button -> {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("", "(no extra loot)");
            ClientKernelState.loot().forEach(table -> options.put(table.id(), table.id() + "  (" + table.entries() + " items)"));
            minecraft.setScreen(PickerScreen.choices("Loot table", options, this, form::setLoot));
        }).bounds(x, y + 12, w, 20).build());
        y += 44;
        toggle(x, y, w, "Mobs already in the world", form.applyExisting(),
                "Also change them when they load", "Only change newly spawned mobs",
                () -> form.setApplyExisting(!form.applyExisting()));
        y += 44;
        toggle(x, y, w, "How it is used", form.manualOnly(),
                "Only when an admin assigns it with a command", "Automatically on every matching mob",
                () -> form.setManualOnly(!form.manualOnly()));
    }

    private static final String[][] DAMAGE_PRESETS = {
            {"Fire", "#minecraft:is_fire"}, {"Explosion", "#minecraft:is_explosion"},
            {"Lightning", "#minecraft:is_lightning"}, {"Freezing", "#minecraft:is_freezing"},
            {"Projectile", "#minecraft:is_projectile"}, {"Magic", "minecraft:magic"}};

    private void buildDefeat() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        labels.add(new Label("Leave a part empty to allow anything. Every filled part must be met.", x, y, Ui.TEXT_MUTED));
        y += 16;

        labels.add(new Label("Attack type (any of)", x, y, Ui.TEXT_BRIGHT));
        int third = (w - Ui.GAP * 2) / 3;
        String[][] attacks = {{"Melee", "MELEE"}, {"Ranged", "RANGED"}, {"Magic", "MAGIC"}};
        for (int i = 0; i < attacks.length; i++) {
            defeatChip(x + i * (third + Ui.GAP), y + 12, third, attacks[i][0], "attacks", attacks[i][1]);
        }
        y += 40;

        labels.add(new Label("Weapon held (any of)  -  click one to remove", x, y, Ui.TEXT_BRIGHT));
        y += 12;
        int half = (w - Ui.GAP) / 2;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("+ Item"), button -> minecraft.setScreen(
                new PickerScreen(net.schwarz.rotasutils.data.ParamSpec.ParamKind.ITEM, this, picked -> addDefeat("items", picked, false))))
                .bounds(x, y, half, 20).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("+ Item tag (e.g. all swords)"), button -> minecraft.setScreen(
                new PickerScreen(net.schwarz.rotasutils.data.ParamSpec.ParamKind.ITEM_TAG, this, picked -> addDefeat("items", picked, true))))
                .bounds(x + half + Ui.GAP, y, half, 20).build());
        y += 24;
        y = defeatEntries(x, y, w, "items");

        labels.add(new Label("Damage type (any of)", x, y, Ui.TEXT_BRIGHT));
        y += 12;
        int sixth = (w - Ui.GAP * 2) / 3;
        for (int i = 0; i < DAMAGE_PRESETS.length; i++) {
            defeatChip(x + (i % 3) * (sixth + Ui.GAP), y + (i / 3) * 22, sixth, DAMAGE_PRESETS[i][0], "damage_types", DAMAGE_PRESETS[i][1]);
        }
        y += 46;
        java.util.List<String> customDamage = form.defeatList("damage_types").stream()
                .filter(id -> java.util.Arrays.stream(DAMAGE_PRESETS).noneMatch(preset -> preset[1].equals(id))).toList();
        if (!customDamage.isEmpty()) {
            labels.add(new Label("Also: " + String.join(", ", customDamage) + "  (edit in Content Studio)", x, y, Ui.TEXT_MUTED));
            y += 14;
        }

        int colW = (w - 16) / 2;
        stepper(x, y, colW, "Minimum player level", form.defeatMinLevel() == 0 ? "Any" : String.valueOf(form.defeatMinLevel()),
                () -> form.setDefeatMinLevel(form.defeatMinLevel() - (hasShiftDown() ? 10 : 1)),
                () -> form.setDefeatMinLevel(form.defeatMinLevel() + (hasShiftDown() ? 10 : 1)));
        stepper(x + colW + 16, y, colW, "Damage when not met", Math.round(form.defeatResisted() * 100) + "%",
                () -> form.setDefeatResisted(form.defeatResisted() - (hasShiftDown() ? 0.25 : 0.05)),
                () -> form.setDefeatResisted(form.defeatResisted() + (hasShiftDown() ? 0.25 : 0.05)));
        y += 40;

        labels.add(new Label("Message shown to the player (empty = says what is needed)", x, y, Ui.TEXT_BRIGHT));
        EditBox hint = new EditBox(font, x, y + 12, w, 18, Component.empty());
        hint.setMaxLength(128);
        hint.setValue(form.defeatHint());
        hint.setResponder(value -> {
            form.setDefeatHint(value);
            changed();
        });
        addRenderableWidget(hint);
    }

    private static final String[][] IMMUNE_PRESETS = {
            {"Fire & lava", "#minecraft:is_fire"}, {"Explosions", "#minecraft:is_explosion"},
            {"Projectiles", "#minecraft:is_projectile"}, {"Fall", "#minecraft:is_fall"},
            {"Drowning", "#minecraft:is_drowning"}, {"Freezing", "#minecraft:is_freezing"},
            {"Lightning", "#minecraft:is_lightning"}, {"Magic & poison", "minecraft:magic"},
            {"Wither effect", "minecraft:wither"}, {"Suffocation", "minecraft:in_wall"},
            {"Cactus & berries", "minecraft:cactus"}, {"Thorns", "minecraft:thorns"}};

    private void buildBody() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        int colW = (w - 16) / 2;
        stepper(x, y, colW, "Size", "x" + MobSetupScreen.trim(form.size()),
                () -> form.setSize(form.size() - (hasShiftDown() ? 0.5 : 0.1)),
                () -> form.setSize(form.size() + (hasShiftDown() ? 0.5 : 0.1)));
        stepper(x + colW + 16, y, colW, "Random size difference", "+-" + Math.round(form.sizeVariance() * 100) + "%",
                () -> form.setSizeVariance(form.sizeVariance() - 0.05),
                () -> form.setSizeVariance(form.sizeVariance() + 0.05));
        y += 34;
        double low = form.size() * (1 - form.sizeVariance()), high = form.size() * (1 + form.sizeVariance());
        labels.add(new Label(form.sizeVariance() == 0 ? "Every mob is x" + MobSetupScreen.trim(form.size()) + " size (hitbox too)."
                : "Each mob is between x" + MobSetupScreen.trim(Math.round(low * 100) / 100.0) + " and x"
                + MobSetupScreen.trim(Math.round(high * 100) / 100.0) + " size (hitbox too).", x, y, Ui.TEXT_MUTED));
        if (!net.schwarz.rotasutils.compat.PehkuiCompat.loaded()) {
            labels.add(new Label("Install Pehkui to apply size; without it mobs stay normal size.", x, y + 12, Ui.WARN));
        } else if (form.size() >= 2.5) {
            labels.add(new Label("Big mobs need room: they cannot fit through doors or caves.", x, y + 12, Ui.WARN));
        }
        y += 30;
        int third = (w - Ui.GAP * 2) / 3;
        int quarter = (w - Ui.GAP * 3) / 4;
        String[][] sizes = {{"Tiny x0.5", "0.5"}, {"Normal", "1"}, {"Big x2", "2"}, {"Giant x4", "4"}};
        for (int i = 0; i < sizes.length; i++) {
            double value = Double.parseDouble(sizes[i][1]);
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(sizes[i][0]), button -> {
                form.setSize(value);
                changed();
                rebuild();
            }).style(form.size() == value ? RotasButton.Style.PRIMARY : RotasButton.Style.DEFAULT)
                    .bounds(x + i * (quarter + Ui.GAP), y, quarter, 20).build());
        }
        y += 34;

        labels.add(new Label("Immune to (takes no damage from these, from anyone)", x, y, Ui.TEXT_BRIGHT));
        y += 12;
        for (int i = 0; i < IMMUNE_PRESETS.length; i++) {
            defeatChip(x + (i % 3) * (third + Ui.GAP), y + (i / 3) * 22, third, IMMUNE_PRESETS[i][0], "immune", IMMUNE_PRESETS[i][1]);
        }
        y += ((IMMUNE_PRESETS.length + 2) / 3) * 22 + 4;
        java.util.List<String> custom = form.defeatList("immune").stream()
                .filter(id -> java.util.Arrays.stream(IMMUNE_PRESETS).noneMatch(preset -> preset[1].equals(id))).toList();
        if (!custom.isEmpty()) {
            labels.add(new Label("Also: " + String.join(", ", custom) + "  (edit in Content Studio)", x, y, Ui.TEXT_MUTED));
        }
    }

    private void defeatChip(int x, int y, int w, String title, String list, String value) {
        boolean on = form.defeatList(list).contains(value);
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text((on ? "[x] " : "[ ] ") + title), button -> {
            form.toggleDefeat(list, value);
            changed();
            rebuild();
        }).style(on ? RotasButton.Style.PRIMARY : RotasButton.Style.DEFAULT).bounds(x, y, w, 20).build());
    }

    private void addDefeat(String list, String picked, boolean tag) {
        if (picked == null || picked.isBlank()) {
            return;
        }
        String value = tag && !picked.startsWith("#") ? "#" + picked : picked;
        if (!form.defeatList(list).contains(value)) {
            form.toggleDefeat(list, value);
            changed();
        }
    }

    private int defeatEntries(int x, int y, int w, String list) {
        java.util.List<String> values = form.defeatList(list);
        int third = (w - Ui.GAP * 2) / 3;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate("x " + value, third - 10)), button -> {
                form.toggleDefeat(list, value);
                changed();
                rebuild();
            }).bounds(x + (i % 3) * (third + Ui.GAP), y + (i / 3) * 22, third, 20).build());
        }
        return y + ((values.size() + 2) / 3) * 22 + 6;
    }

    private void buildSpawn() {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = mainY + 10;
        int colW = (w - 16) / 2;
        int rightX = x + colW + 16;

        toggle(x, y, colW, "Normal Minecraft spawning", form.naturalSpawning(),
                "Spawns naturally as usual", "Never spawns naturally",
                () -> form.setNaturalSpawning(!form.naturalSpawning()));
        int ly = y + 42;
        labels.add(new Label("Where it can spawn", x, ly, Ui.TEXT_BRIGHT));
        ly += 12;
        int third = (colW - Ui.GAP * 2) / 3;
        spawnChoice(x, ly, third, "Anywhere", "ANYWHERE", form.spawnWhere(), form::setSpawnWhere);
        spawnChoice(x + third + Ui.GAP, ly, third, "Only in zones", "ONLY_IN_ZONES", form.spawnWhere(), form::setSpawnWhere);
        spawnChoice(x + (third + Ui.GAP) * 2, ly, third, "Not in zones", "NOT_IN_ZONES", form.spawnWhere(), form::setSpawnWhere);
        ly += 26;
        if (!form.spawnWhere().equals("ANYWHERE")) {
            zoneRows.clear();
            ClientState.zones().values().stream()
                    .sorted(java.util.Comparator.comparing(zone -> zone.name().toLowerCase(java.util.Locale.ROOT)))
                    .forEach(zone -> zoneRows.add(zone.id()));
            if (zoneRows.isEmpty()) {
                labels.add(new Label("No zones yet. Make one in Admin > Level Zones.", x, ly + 2, Ui.WARN));
                ly += 18;
            } else {
                int listH = Math.max(40, Math.min(20 * 6, mainH - 250));
                ScrollPanel zoneList = new ScrollPanel(x, ly, colW, listH, 20).withoutBackground().rowHitInsets(0, 1);
                zoneList.setRows(zoneRows.size(), this::renderZoneRow, (index, button) -> {
                    form.toggleSpawnZone(zoneRows.get(index));
                    Sfx.select();
                    rebuild();
                });
                registerPanel(zoneList);
                ly += listH + 6;
            }
        }
        labels.add(new Label("Time of day", x, ly, Ui.TEXT_BRIGHT));
        ly += 12;
        spawnChoice(x, ly, third, "Any time", "ANY", form.spawnTime(), form::setSpawnTime);
        spawnChoice(x + third + Ui.GAP, ly, third, "Day only", "DAY", form.spawnTime(), form::setSpawnTime);
        spawnChoice(x + (third + Ui.GAP) * 2, ly, third, "Night only", "NIGHT", form.spawnTime(), form::setSpawnTime);

        int ry = y;
        labels.add(new Label("Dimension", rightX, ry, Ui.TEXT_BRIGHT));
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(form.spawnDimension().isEmpty()
                        ? "Any dimension   (click to choose)" : form.spawnDimension() + "   (click to change)", colW - 12)),
                button -> minecraft.setScreen(PickerScreen.open(ParamKind.DIMENSION, this, form::setSpawnDimension, true)))
                .bounds(rightX, ry + 12, colW, 20).build());
        ry += 38;
        stepper(rightX, ry, colW, "Lowest height (Y)", heightText(form.spawnMinY()),
                () -> form.setSpawnHeight(stepLowest(form.spawnMinY(), -1), form.spawnMaxY()),
                () -> form.setSpawnHeight(stepLowest(form.spawnMinY(), 1), form.spawnMaxY()));
        ry += 36;
        stepper(rightX, ry, colW, "Highest height (Y)", heightText(form.spawnMaxY()),
                () -> form.setSpawnHeight(form.spawnMinY(), stepHighest(form.spawnMaxY(), -1)),
                () -> form.setSpawnHeight(form.spawnMinY(), stepHighest(form.spawnMaxY(), 1)));
        ry += 36;
        stepper(rightX, ry, colW, "Most of these mobs nearby (0 = no limit)",
                form.maxNearby() == 0 ? "No limit" : String.valueOf(form.maxNearby()),
                () -> form.setMaxNearby(form.maxNearby() - (hasShiftDown() ? 5 : 1)),
                () -> form.setMaxNearby(form.maxNearby() + (hasShiftDown() ? 5 : 1)));
        ry += 40;
        toggle(rightX, ry, colW, "Extra spawns near players", form.extraSpawns(),
                "Adds extra spawns where the rules allow", "Off",
                () -> form.setExtraSpawns(!form.extraSpawns()));
        ry += 40;
        if (form.extraSpawns()) {
            stepper(rightX, ry, colW, "Tries per minute, per player", String.valueOf(form.extraPerMinute()),
                    () -> form.setExtraPerMinute(form.extraPerMinute() - (hasShiftDown() ? 5 : 1)),
                    () -> form.setExtraPerMinute(form.extraPerMinute() + (hasShiftDown() ? 5 : 1)));
            ry += 36;
            intStepper(rightX, ry, colW, "Up to this many at once", form.extraGroup(), 1, 2, form::setExtraGroup);
            ry += 38;
            toggle(rightX, ry, colW, "Light and block rules", form.extraVanillaRules(),
                    "Follow normal rules (monsters need darkness)", "Ignore light (can spawn in daylight)",
                    () -> form.setExtraVanillaRules(!form.extraVanillaRules()));
        }
    }

    private void spawnChoice(int x, int y, int w, String label, String value, String current,
                             java.util.function.Consumer<String> select) {
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate(label, w - 8)), button -> {
            select.accept(value);
            Sfx.select();
            rebuild();
        }).style(current.equals(value) ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                .bounds(x, y, w, 20).build());
    }

    private void renderZoneRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        drawZoneRow(graphics, zoneRows.get(index), form.spawnZones().contains(zoneRows.get(index)), x, y, rowWidth, rowHeight, hovered);
    }

    private void renderScopeZoneRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        drawZoneRow(graphics, zoneRows.get(index), form.scopeZones().contains(zoneRows.get(index)), x, y, rowWidth, rowHeight, hovered);
    }

    private void drawZoneRow(GuiGraphics graphics, String id, boolean ticked, int x, int y, int rowWidth, int rowHeight,
                             boolean hovered) {
        var zone = ClientState.zones().get(id);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 2, hovered, ticked);
        int box = y + (rowHeight - 2 - 9) / 2;
        Ui.border(graphics, x + 6, box, 9, 9, ticked ? Ui.GOOD : Ui.TEXT_MUTED);
        if (ticked) {
            graphics.fill(x + 8, box + 2, x + 13, box + 7, Ui.GOOD);
        }
        String name = zone == null ? id : zone.name();
        int textY = y + (rowHeight - 2 - 8) / 2;
        Ui.label(graphics, Ui.truncate(name, w - 90), x + 22, textY, ticked ? Ui.TEXT_BRIGHT : Ui.TEXT);
        if (zone != null) {
            Ui.labelRight(graphics, zone.levelLabel(), x + w - 6, textY, Ui.TEXT_MUTED);
        }
    }

    private static String heightText(int y) {
        return y <= MobSpawnRules.LOWEST_Y || y >= MobSpawnRules.HIGHEST_Y ? "Any" : String.valueOf(y);
    }

    private static int stepLowest(int value, int direction) {
        int step = hasShiftDown() ? 16 : 1;
        if (value <= MobSpawnRules.LOWEST_Y) {
            return direction > 0 ? -64 : MobSpawnRules.LOWEST_Y;
        }
        int next = value + direction * step;
        return next < -64 ? MobSpawnRules.LOWEST_Y : next;
    }

    private static int stepHighest(int value, int direction) {
        int step = hasShiftDown() ? 16 : 1;
        if (value >= MobSpawnRules.HIGHEST_Y) {
            return direction < 0 ? 320 : MobSpawnRules.HIGHEST_Y;
        }
        int next = value + direction * step;
        return next > 320 ? MobSpawnRules.HIGHEST_Y : next;
    }

private void intStepper(int x, int y, int w, String title, int value, int step, int bigStep,
                            java.util.function.IntConsumer setter) {
        stepper(x, y, w, title, String.valueOf(value),
                () -> setter.accept(value - (hasShiftDown() ? bigStep : step)),
                () -> setter.accept(value + (hasShiftDown() ? bigStep : step)));
    }

    private void stepper(int x, int y, int w, String title, String value, Runnable down, Runnable up) {
        labels.add(new Label(title, x, y + 6, Ui.TEXT_BRIGHT));
        labels.add(new Label("Shift = bigger steps", x, y + 18, Ui.TEXT_MUTED));
        int right = x + w;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("-"), button -> {
            down.run();
            rebuild();
        }).bounds(right - 120, y + 4, 24, 20).build());
        labels.add(new Label(value, right - 60 - font.width(value) / 2, y + 10, Ui.ACCENT));
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("+"), button -> {
            up.run();
            rebuild();
        }).bounds(right - 24, y + 4, 24, 20).build());
    }

    private void toggle(int x, int y, int w, String title, boolean on, String onText, String offText, Runnable flip) {
        labels.add(new Label(title, x, y, Ui.TEXT_BRIGHT));
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate((on ? "[ON]  " : "[OFF]  ") + (on ? onText : offText), w - 12)),
                button -> {
                    flip.run();
                    rebuild();
                }).style(on ? RotasButton.Style.PRIMARY : RotasButton.Style.DEFAULT).bounds(x, y + 12, w, 20).build());
    }

    private void renderMobRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        String mob = mobRows.get(index);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 3, hovered, false);
        var egg = MobModelCache.egg(mob);
        if (!egg.isEmpty()) {
            graphics.renderFakeItem(egg, x + 4, y + 3);
        }
        boolean installed = MobModelCache.type(mob) != null;
        String name = installed ? MobModelCache.displayName(mob) : mob + "  (not installed)";
        int nameRoom = w - REMOVE_W - 40;
        Ui.label(graphics, Ui.truncate(name, nameRoom), x + 26, y + 8, installed ? Ui.TEXT_BRIGHT : Ui.BAD);
        if (installed) {
            int nameW = Math.min(nameRoom, font.width(Ui.truncate(name, nameRoom)));
            Ui.label(graphics, Ui.truncate(mob, Math.max(0, nameRoom - nameW - 10)), x + 36 + nameW, y + 8, Ui.TEXT_MUTED);
        }
        int chipX = x + w - REMOVE_W - 4;
        Ui.roundedSurface(graphics, chipX, y + 3, REMOVE_W, rowHeight - 9, 3,
                hovered ? 0xC75A2A22 : 0x80382C1F, hovered ? Ui.BAD : 0x70BE9E68);
        Ui.labelCentered(graphics, "Remove", chipX + REMOVE_W / 2, y + 8, hovered ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
    }

    private static final int REMOVE_W = 64;

    private void clickMobRow(int index, int button) {
        boolean onChip = mobList != null && lastClickX >= mobList.x() + mobList.width() - REMOVE_W - 12;
        if (mobList == null || !(button == 1 || (button == 0 && onChip))) {
            return;
        }
        List<String> mobs = new ArrayList<>(form.entities());
        mobs.remove(mobRows.get(index));
        form.setEntities(mobs);
        Sfx.remove();
        rebuild();
    }

private void save() {
        if (!invalidStrengthFields.isEmpty()) {
            ClientState.feedback(false, "Fix or reset invalid stat values before saving.");
            return;
        }
        if (form.entities().isEmpty() && !form.usesOtherSelectors()) {
            ClientState.feedback(false, "Add at least one mob first.");
            return;
        }
        if (zoneScope && form.scopeZones().isEmpty()) {
            ClientState.feedback(false, "Pick at least one zone where these settings apply.");
            return;
        }
        if (!form.spawnWhere().equals("ANYWHERE") && form.spawnZones().isEmpty()) {
            ClientState.feedback(false, "Pick at least one zone on the Spawning page.");
            return;
        }
        if (id == null) {
            String first = form.entities().isEmpty() ? "mob" : form.entities().get(0);
            id = MobSetupForm.newId(first, ClientKernelState.monsters().stream()
                    .map(ClientKernelState.MonsterEntry::id).collect(Collectors.toSet()));
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("id", id);
        payload.putString("body", form.json());
        send("mob_setup_save", payload);
        baseline = form.json();
        Sfx.save();
        rebuild();
    }

    @Override
    protected void goBack() {
        if (!dirty() && invalidStrengthFields.isEmpty()) {
            minecraft.setScreen(parentScreen());
            return;
        }
        minecraft.setScreen(new ConfirmScreen(leave -> minecraft.setScreen(leave ? parentScreen() : this),
                net.schwarz.rotasutils.client.screen.Ui.text("Leave without saving?"), net.schwarz.rotasutils.client.screen.Ui.text("Your changes to this mob setup will be lost.")));
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastClickX = mouseX;
        return super.mouseClicked(mouseX, mouseY, button);
    }

@Override
    protected void renderFrame(GuiGraphics graphics) {
        super.renderFrame(graphics);
        Ui.panel(graphics, mainX, mainY, mainW, mainH);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        models.beginFrame();
        renderSide(graphics, mouseX, mouseY);
        for (Label label : labels) {
            Ui.label(graphics, label.text(), label.x(), label.y(), label.color());
        }
        if (page == Page.STRENGTH) {
            renderStrengthTable(graphics);
        }
        if (page == Page.MOBS && mobRows.isEmpty() && mobList != null) {
            Ui.labelCentered(graphics, "No mobs yet. Press + Add or remove mobs.", mobList.x() + mobList.width() / 2,
                    mobList.y() + 8, Ui.TEXT_MUTED);
        }
        Ui.labelCentered(graphics, dirty() ? "You have unsaved changes" : "Everything is saved",
                (guiLeft + 272 + guiLeft + guiWidth - Ui.PAD - 110) / 2, guiTop + guiHeight - 21,
                dirty() ? Ui.WARN : Ui.GOOD);
    }

    private void renderSide(GuiGraphics graphics, int mouseX, int mouseY) {
        Ui.panel(graphics, sideX, sideY, SIDE_W, sideH);
        int previewX = sideX + 8;
        int previewY = sideY + 8;
        int previewW = SIDE_W - 16;
        int previewH = 140;
        Ui.inset(graphics, previewX, previewY, previewW, previewH);
        List<String> mobs = form.entities();
        EntityType<?> type = mobs.isEmpty() ? null : MobModelCache.type(mobs.get(0));
        int centerX = previewX + previewW / 2;
        if (type != null) {
            graphics.enableScissor(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + previewH - 1);
            models.draw(graphics, type, centerX, previewY + previewH - 12, 110, centerX - mouseX, previewY + 40f - mouseY);
            graphics.disableScissor();
        } else {
            Ui.labelCentered(graphics, "Add a mob to see it here", centerX, previewY + previewH / 2, Ui.TEXT_MUTED);
        }

        int y = previewY + previewH + 8;
        int level = exampleLevel();
        Ui.sectionHeading(graphics, "At level " + level, previewX, y, previewW);
        y += 18;
        LivingEntity living = models.living(type);
        y = stat(graphics, previewX, y, previewW, "Health", living, Attributes.MAX_HEALTH, MobSetupForm.HEALTH, level);
        y = stat(graphics, previewX, y, previewW, "Damage", living, Attributes.ATTACK_DAMAGE, MobSetupForm.DAMAGE, level);
        y = stat(graphics, previewX, y, previewW, "Armor", living, Attributes.ARMOR, MobSetupForm.ARMOR, level);
        long xp = Math.round(form.baseXp() + form.xpPerLevel() * (level - 1));
        Ui.label(graphics, "XP", previewX, y, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, String.valueOf(xp), previewX + previewW, y, Ui.TEXT);
        y += 16;
        if (!form.name().isBlank()) {
            String shown = form.name().replace("{name}", mobs.isEmpty() ? "Mob" : MobModelCache.displayName(mobs.get(0)))
                    .replace("{level}", String.valueOf(level)).replace("{tier}", "Common");
            Ui.label(graphics, "Name tag", previewX, y, Ui.TEXT_MUTED);
            y += 11;
            Ui.label(graphics, Ui.truncate(shown, previewW), previewX, y, Ui.ACCENT);
            y += 14;
        }
        if (form.eliteChance() > 0 && y < sideY + sideH - 12) {
            Ui.label(graphics, Ui.truncate(form.eliteChance() + "% are Elite (tougher)", previewW), previewX, y, Ui.WARN);
            y += 14;
        }
        List<String> spawning = new ArrayList<>();
        spawning.add(form.naturalSpawning() ? "Spawns naturally" : "No natural spawns");
        switch (form.spawnWhere()) {
            case "ONLY_IN_ZONES" -> spawning.add("Only in " + form.spawnZones().size() + " zone(s)");
            case "NOT_IN_ZONES" -> spawning.add("Not in " + form.spawnZones().size() + " zone(s)");
            default -> spawning.add("Anywhere it normally can");
        }
        if (form.spawnTime().equals("DAY")) spawning.add("Day only");
        if (form.spawnTime().equals("NIGHT")) spawning.add("Night only");
        if (form.extraSpawns()) spawning.add("Extra spawns: " + form.extraPerMinute() + " per minute");
        if (y + 18 + spawning.size() * 11 < sideY + sideH) {
            Ui.sectionHeading(graphics, "Spawning", previewX, y + 4, previewW);
            y += 22;
            for (String line : spawning) {
                Ui.label(graphics, Ui.truncate(line, previewW), previewX, y, Ui.TEXT);
                y += 11;
            }
        }
    }

    private int stat(GuiGraphics graphics, int x, int y, int w, String name, LivingEntity living, Attribute attribute,
                     String key, int level) {
        Ui.label(graphics, name, x, y, Ui.TEXT_MUTED);
        String text;
        if (living == null) {
            text = "?";
        } else if (living.getAttribute(attribute) == null) {
            text = "none";
        } else {
            text = MobSetupScreen.trim(Math.round(form.valueAt(key, living.getAttributeBaseValue(attribute), level) * 10) / 10.0);
        }
        Ui.labelRight(graphics, text, x + w, y, Ui.TEXT);
        return y + 12;
    }

    private int exampleLevel() {
        return switch (form.strategy()) {
            case "FIXED" -> form.fixedLevel();
            case "RANDOM" -> (form.min() + form.max()) / 2;
            default -> Math.max(form.min(), Math.min(form.max(), 10 + form.offset()));
        };
    }

    @Override
    public void removed() {
        models.clear();
        super.removed();
    }
}
