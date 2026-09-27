package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.LevelCurve;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.level.XpSourceConfig;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.quest.reward.RewardType;

import java.util.ArrayList;
import java.util.List;

/** Level curve, rank requirements, experience sources and level-up rewards. */
@Environment(EnvType.CLIENT)
public class LevelManagerScreen extends RotasScreen {
    private CompoundTag editingBaseline;
    private enum Tab {
        CURVE("Progression"),
        RANKS("Ranks"),
        SOURCES("Combat XP"),
        MOB_LEVELS("Mob Levels"),
        REWARDS("Milestones");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Text fields that accept a decimal, and whole-number fields where zero is meaningful. */
    private static final java.util.Set<String> DECIMAL_FIELDS = java.util.Set.of(
            "basexp", "exponent", "mobscale", "bossmult", "moddedmult", "mobhp", "mobdmg", "mobxpperlevel",
            "xpwhealth", "xpwdamage", "xpwarmor", "xpwtoughness", "xpwspeed", "xpwspeedbase", "xpwknockback", "xpwtier1hp", "xpwtier2hp", "xpwtier3hp");
    private static final java.util.Set<String> ZERO_OK_FIELDS = java.util.Set.of(
            "points", "mobperblocks", "mobbasexp", "mobhp", "mobdmg", "mobxpperlevel", "moboffset",
            "xpwhealth", "xpwdamage", "xpwarmor", "xpwtoughness", "xpwspeed", "xpwspeedbase", "xpwknockback", "xpwtier1hp", "xpwtier2hp", "xpwtier3hp");

    private LevelConfig draft;
    private Tab tab = Tab.CURVE;
    private int rewardLevel = 5;

    private final List<String> labels = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private final List<String> helps = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private String editLabel = "";
    private ScrollPanel list;
    private EditBox textField;
    private String textTarget = "";
    private String rawText;
    private String validationError = "";

    public LevelManagerScreen(Screen parent) {
        super("Level System Manager", parent);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 760);
        guiHeight = Ui.fill(height, 440);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        if (draft == null) {
            draft = LevelConfig.load(ClientState.levelConfig().save());
            editingBaseline = draft.save();
        }

        int tabX = guiLeft + 10;
        int tabGap = 4;
        int tabWidth = (guiWidth - 20 - (Tab.values().length - 1) * tabGap) / Tab.values().length;
        for (Tab value : Tab.values()) {
            Tab target = value;
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(value.label, tabWidth - 14)), button -> {
                if (!commitText()) return;
                tab = target;
                rebuild();
            }).style(value == tab
                    ? RotasButton.Style.NAVIGATION_SELECTED
                    : RotasButton.Style.NAVIGATION)
                    .bounds(tabX, guiTop + 34, tabWidth, 24).build());
            tabX += tabWidth + tabGap;
        }

        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Review"), button -> {
            if (!commitText()) return;
            CompoundTag payload = new CompoundTag();
            payload.put("level_config", draft.save());
            minecraft.setScreen(new ConfigReviewScreen(this, "save_level_config", payload, editingBaseline));
        }).bounds(guiLeft + guiWidth - 124, guiTop + guiHeight - 32, 114, 24).build());

        boolean editing = !textTarget.isEmpty();
        int listTop = guiTop + 68;
        int listBottom = guiTop + guiHeight - 44 - (editing ? 46 : 0);
        int previewWidth = 222;
        int listWidth = guiWidth - previewWidth - 30;
        list = new ScrollPanel(guiLeft + 10, listTop, listWidth,
                Math.max(60, listBottom - listTop), 28)
                .withoutBackground()
                .rowHitInsets(0, 4);
        registerPanel(list);
        refreshRows();

        textField = new EditBox(font, guiLeft + 20, listBottom + 25, listWidth - 132, 16,
                net.schwarz.rotasutils.client.screen.Ui.text("Value"));
        textField.setBordered(false);
        textField.setMaxLength(128);
        textField.setVisible(editing);
        textField.setCanLoseFocus(false);
        addRenderableWidget(textField);
        if (editing) {
            textField.setValue(rawText == null ? currentValue(textTarget) : rawText);
            textField.setResponder(value -> rawText = value);
            setInitialFocus(textField);
            addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Apply"), button -> {
                commitText();
                rebuild();
            }).bounds(guiLeft + 10 + listWidth - 132, listBottom + 20, 62, 24).build());
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Cancel"), button -> {
                textTarget = "";
                rebuild();
            }).bounds(guiLeft + 10 + listWidth - 66, listBottom + 20, 62, 24).build());
        }

        addBackButton();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void row(String label, String action) {
        row(label, "", "", action);
    }

    /** A settings row: name on the left, current value on the right, hint underneath. */
    private void row(String label, String value, String help, String action) {
        labels.add(label);
        values.add(value);
        helps.add(help);
        actions.add(action);
    }

    private void section(String title) {
        row(title, "", "", "#section");
    }

    private void refreshRows() {
        labels.clear();
        values.clear();
        helps.clear();
        actions.clear();
        LevelCurve curve = draft.curve();
        switch (tab) {
            case CURVE -> {
                section("Core progression");
                row("Maximum level", String.valueOf(curve.maxLevel()),
                        "Highest Rotas level a player can reach.", "text:maxlevel");
                row("Starting level", String.valueOf(draft.startingLevel()),
                        "Level a brand new player begins at.", "text:startlevel");

                section("Skills and stats");
                row("Skill system", draft.pufferfishSkills() ? "Pufferfish Skills" : "RotasUtils trees",
                        "Click to switch. RotasUtils trees support jobs and races; Pufferfish keeps its own screen.",
                        "toggle:puffish");
                var stats = draft.season().stats;
                row("Stat points", stats.startPoints + " + " + stats.pointsPerLevel + "/level, max " + stats.maxPerStat,
                        "STR / VIT / INT / AGI numbers live in Season rules > Stats.", "#section");
                row("Points per grant", String.valueOf(draft.skillPointsPerLevel()),
                        "How many skill points a grant gives.", "text:points");
                row("Grant every", draft.skillPointInterval() + " level(s)",
                        "Example: 2 = one point grant every two Rotas levels.", "text:pointinterval");
                row("Announce level-ups", yesNo(draft.announceLevelUp()),
                        "Shows the player their new Rotas level and point grant.", "toggle:announce");

                section("EXP curve: base x level ^ exponent");
                row("Base experience", String.valueOf(curve.baseXp()),
                        "EXP from level 1 to 2. Every level costs base x level ^ exponent.", "text:basexp");
                row("Exponent", String.valueOf(curve.exponent()),
                        "How much steeper late levels get. 2.1 = about 13M EXP to level 100.", "text:exponent");
            }
            case RANKS -> {
                row("Rank requirements", yesNo(draft.rankRequirementsEnabled()),
                        "Off lets players take any rank regardless of level.", "toggle:rankreq");
                section("Per rank");
                for (DangerRank rank : DangerRank.VALUES) {
                    row(rank.display() + "-Rank",
                            "Lv " + draft.rankLevel(rank) + "   x" + draft.rankMultiplier(rank)
                                    + "   " + draft.rankQuestsRequired(rank) + " quests",
                            draft.rankAutoGrant(rank)
                                    ? "Granted automatically. Right-click to require manual granting."
                                    : "Granted manually by an admin. Right-click to automate.",
                            "rank:" + rank.name());
                }
            }
            case SOURCES -> {
                section("Dynamic monster rating");
                row("Combat model", "Live entity attributes",
                        "HP, damage, armour, toughness, speed and resistance determine XP.", "");
                row("Combat XP scale", String.valueOf(draft.monsterXpScale()),
                        "Global scale applied after the monster threat score.", "text:mobscale");
                row("Boss multiplier", String.valueOf(draft.bossXpMultiplier()),
                        "Extra multiplier for tagged/recognised bosses.", "text:bossmult");
                row("Modded monster multiplier", String.valueOf(draft.moddedMobXpMultiplier()),
                        "Compensates for custom attacks not represented by vanilla attributes.", "text:moddedmult");
                row("Minimum combat XP", String.valueOf(draft.minimumMonsterXp()),
                        "Floor for a valid hostile monster kill.", "text:mobmin");
                row("Maximum combat XP", String.valueOf(draft.maximumMonsterXp()),
                        "Safety cap for extreme modded bosses.", "text:mobmax");

                section("How a monster's XP is rated");
                var weights = draft.monsterXpWeights();
                row("Health weight", String.valueOf(weights.health()),
                        "How much each point of max health adds to the threat score.", "text:xpwhealth");
                row("Damage weight", String.valueOf(weights.damage()),
                        "Weighted high by default: a lethal, fast fight is as dangerous as a long one.",
                        "text:xpwdamage");
                row("Armour weight", String.valueOf(weights.armor()),
                        "How much each armour point adds.", "text:xpwarmor");
                row("Toughness weight", String.valueOf(weights.toughness()),
                        "How much each armour-toughness point adds.", "text:xpwtoughness");
                row("Speed weight", String.valueOf(weights.speed()),
                        "Applied to movement speed above the walking baseline below.", "text:xpwspeed");
                row("Speed baseline", String.valueOf(weights.speedBaseline()),
                        "Speed up to this counts as normal and adds nothing.", "text:xpwspeedbase");
                row("Knockback resist weight", String.valueOf(weights.knockbackResistance()),
                        "Unstaggerable mobs are harder, so this adds to the score.", "text:xpwknockback");
                row("Big-health tier 1", weights.tierOneHealth() + " hp  x" + weights.tierOneMultiplier(),
                        "Above this much health the score is multiplied - big modded pools usually come "
                                + "with attacks that attack damage does not describe.", "text:xpwtier1hp");
                row("Big-health tier 2", weights.tierTwoHealth() + " hp  x" + weights.tierTwoMultiplier(),
                        "Second multiplier, stacking with the first.", "text:xpwtier2hp");
                row("Big-health tier 3", weights.tierThreeHealth() + " hp  x" + weights.tierThreeMultiplier(),
                        "Third multiplier, for raid bosses.", "text:xpwtier3hp");

                section("Combat source policy");
                for (XpSource source : new XpSource[]{XpSource.MOB_KILL, XpSource.BOSS_KILL}) {
                    XpSourceConfig config = draft.source(source);
                    row(source.display(),
                            (config.enabled() ? "ON" : "OFF")
                                    + (config.multiplier() == 1.0 ? "" : "  x" + config.multiplier()),
                            (config.antiFarm() ? "Anti-farm on. " : "")
                                    + "Click for limits/party share; right-click toggles.",
                            "source:" + source.name());
                }

                List<XpSource> optionalEnabled = new ArrayList<>();
                for (XpSource source : XpSource.VALUES) {
                    if (source != XpSource.MOB_KILL && source != XpSource.BOSS_KILL
                            && draft.source(source).enabled()) {
                        optionalEnabled.add(source);
                    }
                }
                section("Optional routes");
                if (optionalEnabled.isEmpty()) {
                    row("No activity XP enabled", "Combat + quest rewards only",
                            "Mining, crafting, smelting and vanilla XP do not level players.", "#section");
                } else {
                    for (XpSource source : optionalEnabled) {
                        XpSourceConfig config = draft.source(source);
                        row(source.display(), "ON  " + config.baseAmount() + " xp",
                                "Legacy/explicit route. Right-click to disable.", "source:" + source.name());
                    }
                }
            }
            case MOB_LEVELS -> {
                var mob = draft.mobLevel();
                section("Every mob has a level");
                row("Mob leveling", yesNo(mob.enabled()),
                        "Levels every mob that has no monster profile. Authored profiles always win.", "toggle:moblevel");
                row("Only hostile mobs", mob.categories().contains("MONSTER") ? "Yes" : "No",
                        "No levels animals and villagers too. Turn on to limit it to hostile monsters.", "toggle:mobhostile");
                row("Skip summons and golems", yesNo(mob.skipMisc()),
                        "Skips the MISC category: summoned weapons, golems and marker entities. "
                                + "Only applies while no category list is set below.", "toggle:mobmisc");
                row("Nameplates show the level", yesNo(mob.nameVisible()),
                        "The mob's name becomes the format below, e.g. Zombie [Lv 12].", "toggle:mobnameplate");
                row("Colour nameplates by difficulty", yesNo(mob.colorByDelta()),
                        "Green when the mob is below your level, red when it is far above.", "toggle:mobcolor");
                row("Name format", mob.nameFormat(),
                        "Must contain {level}. {name} is the mob's own name.", "text:mobformat");

                section("Level band outside a zone");
                row("Level at spawn", String.valueOf(mob.spawnLevel()),
                        "Level a mob gets at the world spawn when no zone covers it.", "text:mobspawn");
                row("One level per N blocks", String.valueOf(mob.levelPerBlocks()),
                        "0 keeps the spawn level everywhere; e.g. 64 raises the floor every 64 blocks out.",
                        "text:mobperblocks");
                row("Wilderness level spread", String.valueOf(mob.bandSpread()),
                        "Mobs outside zones roll between the distance floor and this many levels above it.",
                        "text:mobspread");
                row("Maximum mob level", String.valueOf(mob.maxLevel()),
                        "Ceiling for zones and the fallback band.", "text:mobmaxlevel");

                section("What a level changes");
                row("Health per level", percent(mob.healthPerLevel()),
                        "Multiplies base health: +5% a level makes level 30 about 2.5x health.", "text:mobhp");
                row("Damage per level", percent(mob.damagePerLevel()),
                        "Multiplies base attack damage the same way.", "text:mobdmg");
                row("Base kill XP", String.valueOf(mob.baseXp()),
                        "Floor for a weak mob. Tough and boss monsters pay their threat-rated XP instead.",
                        "text:mobbasexp");
                row("XP per level", String.valueOf(mob.xpPerLevel()),
                        "Added to the kill XP for each level above 1.", "text:mobxpperlevel");

                section("Mobs that never level");
                row("Excluded mobs", mob.excludeEntities().size() + " mob(s)",
                        "Pick the mobs that keep their vanilla stats - shopkeepers, pets, boss-bar "
                                + "entities from other mods.", "pick:mobexclude");
                for (String excluded : mob.excludeEntities()) {
                    row("  " + excluded, "excluded", "Click to start leveling this mob again.",
                            "unexclude:" + excluded);
                }
                row("Excluded mod namespaces", String.join(", ", mob.excludeNamespaces()),
                        "Every entity from these mods is skipped. Click a namespace below to remove it.",
                        "#section");
                for (String namespace : mob.excludeNamespaces()) {
                    row("  " + namespace, "skipped", "Click to let this mod's entities level.",
                            "unnamespace:" + namespace);
                }
                if (!mob.excludeTags().isEmpty()) {
                    row("Excluded entity tags", String.valueOf(mob.excludeTags().size()),
                            "Mobs carrying one of these tags never level.", "#section");
                    for (String tag : mob.excludeTags()) {
                        row("  " + tag, "excluded", "Click to remove this tag exclusion.", "untag:" + tag);
                    }
                }

                section("Where the level bands come from");
                row("Level Zones", ClientState.zones().size() + " zone(s)",
                        "Draw zones under Admin > Level Zones with the Zone Wand. Outside them the band above applies.",
                        "");
            }
            case REWARDS -> {
                row("Editing level", String.valueOf(rewardLevel),
                        "Rewards below are granted on reaching this level.", "text:rewardlevel");
                section("Rewards at level " + rewardLevel);
                List<Reward> rewards = draft.levelRewards(rewardLevel);
                for (int i = 0; i < rewards.size(); i++) {
                    row(rewards.get(i).type().display(), "", "Click to edit or remove.", "reward:" + i);
                }
                if (rewards.isEmpty()) {
                    row("No rewards at this level yet.", "", "", "#section");
                }
                row("+ Add a reward here", "", "", "addreward");
                section("Levels that already grant something");
                for (var entry : draft.allLevelRewards().entrySet()) {
                    row("Level " + entry.getKey(), entry.getValue().size() + " reward(s)",
                            "", "gotolevel:" + entry.getKey());
                }
            }
        }
        list.setRows(labels.size(), this::renderRow, this::clickRow);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        String action = actions.get(index);
        String label = labels.get(index);
        int usable = rowWidth - 6;
        int right = x + usable;

        if (action.equals("#section")) {
            graphics.fill(x + 2, y + rowHeight - 2, right - 2, y + rowHeight - 1, Ui.BORDER_SUBTLE);
            Ui.label(graphics, label, x + 4, y + 4, Ui.ACCENT);
            return;
        }
        boolean actionable = !action.isEmpty();
        if (actionable) {
            Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, false);
        }

        String value = values.get(index);
        int valueWidth = 0;
        if (!value.isEmpty()) {
            valueWidth = Math.min(font.width(value), usable / 2);
            Ui.labelRight(graphics, Ui.truncate(value, valueWidth), right - 6, y + 4,
                    value.equals("selected") || value.equals("ON") ? Ui.GOOD
                            : value.equals("OFF") ? Ui.TEXT_MUTED : Ui.ACCENT);
        }
        int labelWidth = usable - valueWidth - 20;
        Ui.label(graphics, Ui.truncate(label, labelWidth), x + 6, y + 4,
                label.startsWith("+") ? Ui.GOOD : Ui.TEXT_BRIGHT);
        String help = helps.get(index);
        if (!help.isEmpty() && rowHeight >= 22) {
            Ui.label(graphics, Ui.truncate(help, usable - 12), x + 6, y + 14, Ui.TEXT_MUTED);
        }
    }

    private void clickRow(int index, int button) {
        if (!commitText()) return;
        String action = actions.get(index);
        if (action.isEmpty()) {
            return;
        }
        if (action.equals("#section")) {
            return;
        }
        if (action.startsWith("text:")) {
            openFields();
            return;
        }
        if (action.startsWith("toggle:")) {
            switch (action.substring(7)) {
                case "announce" -> draft.setAnnounceLevelUp(!draft.announceLevelUp());
                case "puffish" -> draft.setPufferfishSkills(!draft.pufferfishSkills());
                case "rankreq" -> draft.setRankRequirementsEnabled(!draft.rankRequirementsEnabled());
                case "moblevel" -> draft.mobLevel().setEnabled(!draft.mobLevel().enabled());
                case "mobhostile" -> draft.mobLevel().toggleCategory("MONSTER");
                case "mobnameplate" -> draft.mobLevel().setNameVisible(!draft.mobLevel().nameVisible());
                case "mobcolor" -> draft.mobLevel().setColorByDelta(!draft.mobLevel().colorByDelta());
                case "mobmisc" -> draft.mobLevel().setSkipMisc(!draft.mobLevel().skipMisc());
                default -> {
                }
            }
        } else if (action.equals("pick:mobexclude")) {
            // The picker starts with the current exclusions ticked, so unticking one removes it.
            minecraft.setScreen(EntityPickerScreen.many(this, draft.mobLevel().excludeEntities(), ids -> {
                var excluded = draft.mobLevel().excludeEntities();
                excluded.clear();
                excluded.addAll(ids);
                Sfx.commit();
                refreshRows();
            }));
            return;
        } else if (action.startsWith("unexclude:")) {
            draft.mobLevel().toggleExcludedEntity(action.substring(10));
        } else if (action.startsWith("unnamespace:")) {
            draft.mobLevel().toggleExcludedNamespace(action.substring(12));
        } else if (action.startsWith("untag:")) {
            draft.mobLevel().toggleExcludedTag(action.substring(6));
        } else if (action.equals("cycle:pointmode")) {
            LevelConfig.PointMode[] values = LevelConfig.PointMode.VALUES;
            draft.setPointMode(values[(draft.pointMode().ordinal() + 1) % values.length]);
        } else if (action.startsWith("rank:")) {
            DangerRank rank = DangerRank.valueOf(action.substring(5));
            if (button == 1) {
                draft.setRankAutoGrant(rank, !draft.rankAutoGrant(rank));
            } else {
                minecraft.setScreen(new RankSettingsScreen(draft, rank, this));
                return;
            }
        } else if (action.startsWith("source:")) {
            XpSource source = XpSource.valueOf(action.substring(7));
            if (button == 1) {
                XpSourceConfig config = draft.source(source);
                config.setEnabled(!config.enabled());
            } else {
                minecraft.setScreen(new XpSourceScreen(draft.source(source), this));
                return;
            }
        } else if (action.startsWith("reward:")) {
            int rewardIndex = Integer.parseInt(action.substring(7));
            List<Reward> rewards = new ArrayList<>(draft.levelRewards(rewardLevel));
            Reward reward = rewards.get(rewardIndex);
            minecraft.setScreen(new TypedEntryScreen<>("Level reward", this, RewardType.VALUES,
                    RewardType::display, reward.type(), reward::setType,
                    () -> new ParamEditorScreen("Reward fields", reward.params(),
                            reward.type().specs(), this, this::rebuild),
                    () -> {
                    },
                    () -> "Granted on reaching level " + rewardLevel,
                    () -> {
                        rewards.remove(rewardIndex);
                        draft.setLevelRewards(rewardLevel, rewards);
                    }));
            return;
        } else if (action.equals("addreward")) {
            List<Reward> rewards = new ArrayList<>(draft.levelRewards(rewardLevel));
            rewards.add(new Reward(RewardType.SKILL_POINT));
            draft.setLevelRewards(rewardLevel, rewards);
        } else if (action.startsWith("gotolevel:")) {
            rewardLevel = Integer.parseInt(action.substring(10));
        }
        refreshRows();
    }

    private String currentValue(String target) {
        return switch (target) {
            case "maxlevel" -> String.valueOf(draft.curve().maxLevel());
            case "basexp" -> String.valueOf(draft.curve().baseXp());
            case "exponent" -> String.valueOf(draft.curve().exponent());
            case "startlevel" -> String.valueOf(draft.startingLevel());
            case "points" -> String.valueOf(draft.skillPointsPerLevel());
            case "pointinterval" -> String.valueOf(draft.skillPointInterval());
            case "mobscale" -> String.valueOf(draft.monsterXpScale());
            case "bossmult" -> String.valueOf(draft.bossXpMultiplier());
            case "moddedmult" -> String.valueOf(draft.moddedMobXpMultiplier());
            case "mobmin" -> String.valueOf(draft.minimumMonsterXp());
            case "mobmax" -> String.valueOf(draft.maximumMonsterXp());
            case "mobformat" -> draft.mobLevel().nameFormat();
            case "mobspawn" -> String.valueOf(draft.mobLevel().spawnLevel());
            case "mobperblocks" -> String.valueOf(draft.mobLevel().levelPerBlocks());
            case "mobspread" -> String.valueOf(draft.mobLevel().bandSpread());
            case "mobmaxlevel" -> String.valueOf(draft.mobLevel().maxLevel());
            case "mobhp" -> String.valueOf(draft.mobLevel().healthPerLevel());
            case "mobdmg" -> String.valueOf(draft.mobLevel().damagePerLevel());
            case "mobbasexp" -> String.valueOf(draft.mobLevel().baseXp());
            case "mobxpperlevel" -> String.valueOf(draft.mobLevel().xpPerLevel());
            case "xpwhealth" -> String.valueOf(draft.monsterXpWeights().health());
            case "xpwdamage" -> String.valueOf(draft.monsterXpWeights().damage());
            case "xpwarmor" -> String.valueOf(draft.monsterXpWeights().armor());
            case "xpwtoughness" -> String.valueOf(draft.monsterXpWeights().toughness());
            case "xpwspeed" -> String.valueOf(draft.monsterXpWeights().speed());
            case "xpwspeedbase" -> String.valueOf(draft.monsterXpWeights().speedBaseline());
            case "xpwknockback" -> String.valueOf(draft.monsterXpWeights().knockbackResistance());
            case "xpwtier1hp" -> String.valueOf(draft.monsterXpWeights().tierOneHealth());
            case "xpwtier2hp" -> String.valueOf(draft.monsterXpWeights().tierTwoHealth());
            case "xpwtier3hp" -> String.valueOf(draft.monsterXpWeights().tierThreeHealth());
            case "rewardlevel" -> String.valueOf(rewardLevel);
            default -> "";
        };
    }

    private boolean commitText() {
        if (textTarget.isEmpty() || textField == null) {
            return true;
        }
        String value = textField.getValue();
        if (textTarget.equals("mobformat")) {
            String trimmed = value.trim();
            if (trimmed.isEmpty() || !trimmed.contains("{level}") || trimmed.length() > 128) {
                validationError = "The name format must contain {level} and fit 128 characters.";
                rawText = value;
                return false;
            }
        } else {
            try {
                boolean decimal = DECIMAL_FIELDS.contains(textTarget);
                double parsed = decimal ? Double.parseDouble(value.trim()) : Integer.parseInt(value.trim());
                boolean negativeAllowed = textTarget.equals("moboffset");
                if (!Double.isFinite(parsed) || (parsed < 0 && !negativeAllowed)
                        || (!decimal && parsed < 1 && !ZERO_OK_FIELDS.contains(textTarget))) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                validationError = "Enter a valid number for this field.";
                rawText = value;
                return false;
            }
        }
        applyTextValue(textTarget, value);
        textTarget = "";
        rawText = null;
        validationError = "";
        editLabel = "";
        net.schwarz.rotasutils.client.screen.Sfx.commit();
        refreshRows();
        return true;
    }

    private void applyTextValue(String target, String value) {
        switch (target) {
            case "maxlevel" -> {
                draft.season().mainMaxLevel = parseInt(value, 100);
                draft.setSeason(draft.season());
            }
            case "basexp" -> {
                draft.season().mainBaseXp = parseDouble(value, 25);
                draft.setSeason(draft.season());
            }
            case "exponent" -> {
                draft.season().mainExponent = parseDouble(value, 2.1);
                draft.setSeason(draft.season());
            }
            case "startlevel" -> draft.setStartingLevel(parseInt(value, 1));
            case "points" -> draft.setSkillPointsPerLevel(parseInt(value, 1));
            case "pointinterval" -> draft.setSkillPointInterval(parseInt(value, 2));
            case "mobscale" -> draft.setMonsterXpScale(parseDouble(value, 0.65));
            case "bossmult" -> draft.setBossXpMultiplier(parseDouble(value, 3.0));
            case "moddedmult" -> draft.setModdedMobXpMultiplier(parseDouble(value, 1.15));
            case "mobmin" -> draft.setMinimumMonsterXp(parseInt(value, 2));
            case "mobmax" -> draft.setMaximumMonsterXp(parseInt(value, 5000));
            case "mobformat" -> draft.mobLevel().setNameFormat(value.trim());
            case "mobspawn" -> draft.mobLevel().setSpawnLevel(parseInt(value, 1));
            case "mobperblocks" -> draft.mobLevel().setLevelPerBlocks(parseInt(value, 0));
            case "mobspread" -> draft.mobLevel().setBandSpread(parseInt(value, 3));
            case "mobmaxlevel" -> draft.mobLevel().setMaxLevel(parseInt(value, 100));
            case "mobhp" -> draft.mobLevel().setHealthPerLevel(parseDouble(value, 0.05));
            case "mobdmg" -> draft.mobLevel().setDamagePerLevel(parseDouble(value, 0.03));
            case "mobbasexp" -> draft.mobLevel().setBaseXp(parseInt(value, 20));
            case "mobxpperlevel" -> draft.mobLevel().setXpPerLevel(parseDouble(value, 2));
            case "xpwhealth" -> draft.monsterXpWeights().setHealth(parseDouble(value, 0.35));
            case "xpwdamage" -> draft.monsterXpWeights().setDamage(parseDouble(value, 4.0));
            case "xpwarmor" -> draft.monsterXpWeights().setArmor(parseDouble(value, 1.7));
            case "xpwtoughness" -> draft.monsterXpWeights().setToughness(parseDouble(value, 3.5));
            case "xpwspeed" -> draft.monsterXpWeights().setSpeed(parseDouble(value, 30.0));
            case "xpwspeedbase" -> draft.monsterXpWeights().setSpeedBaseline(parseDouble(value, 0.18));
            case "xpwknockback" -> draft.monsterXpWeights().setKnockbackResistance(parseDouble(value, 18.0));
            case "xpwtier1hp" -> draft.monsterXpWeights().setTierOneHealth(parseDouble(value, 80.0));
            case "xpwtier2hp" -> draft.monsterXpWeights().setTierTwoHealth(parseDouble(value, 250.0));
            case "xpwtier3hp" -> draft.monsterXpWeights().setTierThreeHealth(parseDouble(value, 750.0));
            case "rewardlevel" -> rewardLevel = Math.max(1, parseInt(value, 5));
            default -> {
            }
        }
    }

    private void openFields() {
        java.util.Map<String, String> editable = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> help = new java.util.HashMap<>();
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).startsWith("text:")) {
                editable.put(actions.get(i).substring(5), labels.get(i));
                help.put(labels.get(i), helps.get(i));
            }
        }
        java.util.Map<String, String> keyByLabel = new java.util.HashMap<>();
        editable.forEach((key, label) -> keyByLabel.put(label, key));
        minecraft.setScreen(new SimpleFieldScreen("Progression fields", this) {
            @Override protected void collectFields(List<Field> fields) {
                editable.forEach((key, label) -> fields.add(new Field(label,
                        key.equals("mobformat") ? Field.Kind.TEXT
                                : DECIMAL_FIELDS.contains(key) ? Field.Kind.DOUBLE : Field.Kind.INT,
                        () -> currentValue(key), value -> applyTextValue(key, value))));
            }
            @Override protected String helpFor(Field field) { return help.getOrDefault(field.label(), ""); }
            @Override protected String validateValue(Field field, String value) {
                String key = keyByLabel.get(field.label());
                if (key == null) {
                    return super.validateValue(field, value);
                }
                if (key.equals("mobformat")) {
                    return value.trim().contains("{level}") ? "" : "The name format must contain {level}";
                }
                try {
                    boolean decimal = DECIMAL_FIELDS.contains(key);
                    double parsed = decimal ? Double.parseDouble(value.trim()) : Integer.parseInt(value.trim());
                    if (!Double.isFinite(parsed)) return "Enter a finite number";
                    if (parsed < 0 && !key.equals("moboffset")) return "Use zero or a positive value";
                    if (!decimal && parsed < 1 && !ZERO_OK_FIELDS.contains(key)) return "Use a whole number of at least 1";
                    return "";
                } catch (NumberFormatException exception) {
                    return "Enter a number";
                }
            }
        });
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!textTarget.isEmpty()) {
            if (keyCode == 257 || keyCode == 335) {
                commitText();
                rebuild();
                return true;
            }
            if (keyCode == 256) {
                textTarget = "";
                rebuild();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int graphWidth = 212;
        int graphLeft = guiLeft + guiWidth - graphWidth - 10;
        int graphTop = guiTop + 68;
        int graphHeight = guiHeight - 112;
        Ui.modernPanel(graphics, graphLeft, graphTop, graphWidth, graphHeight);
        Ui.label(graphics, "PROGRESSION PREVIEW", graphLeft + 12, graphTop + 10, Ui.TEXT_BRIGHT);
        Ui.label(graphics, draft.curve().baseXp() + " x level ^ " + draft.curve().exponent(), graphLeft + 12, graphTop + 22, Ui.TEXT_MUTED);

        LevelCurve curve = draft.curve();
        int maxLevel = curve.maxLevel();
        long peak = Math.max(1L, curve.xpToNext(Math.max(1, maxLevel - 1)));

        int plotLeft = graphLeft + 12;
        int plotTop = graphTop + 42;
        int plotWidth = graphWidth - 24;
        int plotHeight = Math.min(150, Math.max(86, graphHeight / 2 - 18));
        Ui.roundedRect(graphics, plotLeft, plotTop, plotWidth, plotHeight, 7, 0xE8241C13);

        for (int i = 0; i < plotWidth - 4; i++) {
            int level = 1 + (int) ((i / (double) Math.max(1, plotWidth - 5)) * (maxLevel - 1));
            long xp = curve.xpToNext(Math.max(1, Math.min(maxLevel - 1, level)));
            int barHeight = Math.max(1, (int) Math.round((plotHeight - 16) * Math.min(1.0, xp / (double) peak)));
            int px = plotLeft + 2 + i;
            graphics.fill(px, plotTop + plotHeight - 10 - barHeight,
                    px + 1, plotTop + plotHeight - 10, Ui.ACCENT);
        }

        if (draft.rankRequirementsEnabled()) {
            for (DangerRank rank : DangerRank.VALUES) {
                int rankLevel = draft.rankLevel(rank);
                if (rankLevel <= 1 || rankLevel >= maxLevel) {
                    continue;
                }
                int markerX = plotLeft + 2
                        + (int) ((rankLevel - 1) / (double) (maxLevel - 1) * (plotWidth - 5));
                graphics.fill(markerX, plotTop + 5, markerX + 1, plotTop + plotHeight - 10,
                        (rank.argb() & 0x00FFFFFF) | 0x52000000);
            }
        }

        Ui.label(graphics, "Lv 1", plotLeft + 5, plotTop + plotHeight - 9, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, "Lv " + maxLevel, plotLeft + plotWidth - 5,
                plotTop + plotHeight - 9, Ui.TEXT_MUTED);

        int summaryY = plotTop + plotHeight + 13;
        Ui.label(graphics, "SAMPLE COSTS", graphLeft + 12, summaryY, Ui.TEXT_MUTED);
        summaryY += 13;
        int[] samples = new int[]{1, Math.max(2, maxLevel / 4), Math.max(2, maxLevel / 2), Math.max(1, maxLevel - 1)};
        java.util.HashSet<Integer> shown = new java.util.HashSet<>();
        for (int level : samples) {
            if (level < 1 || level >= maxLevel || !shown.add(level)) {
                continue;
            }
            Ui.label(graphics, "Lv " + level + " → " + (level + 1), graphLeft + 12, summaryY, Ui.TEXT_DIM);
            Ui.labelRight(graphics, format(curve.xpToNext(level)), graphLeft + graphWidth - 12,
                    summaryY, Ui.TEXT_BRIGHT);
            summaryY += 13;
        }

        int totalY = graphTop + graphHeight - 31;
        Ui.roundedRect(graphics, graphLeft + 10, totalY, graphWidth - 20, 21, 7, 0xD92C2218);
        Ui.label(graphics, "TOTAL TO MAX", graphLeft + 18, totalY + 7, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, format(curve.totalXpTo(maxLevel)), graphLeft + graphWidth - 18,
                totalY + 7, Ui.ACCENT);

        if (!textTarget.isEmpty()) {
            int stripY = list.y() + list.height() + 7;
            int stripW = list.width();
            Ui.roundedSurface(graphics, list.x(), stripY, stripW, 40, 8, 0xF5382C1F, 0x8FBE9E68);
            Ui.label(graphics, validationError.isEmpty() ? "Editing: " + editLabel : validationError, list.x() + 10, stripY + 6, Ui.ACCENT);
            Ui.labelRight(graphics, "Enter = apply  •  Esc = cancel",
                    list.x() + stripW - 10, stripY + 6, Ui.TEXT_MUTED);
            Ui.searchFrame(graphics, list.x() + 8, stripY + 18, stripW - 142, 18,
                    textField != null && textField.isFocused());
        }
    }

    /** Compact number so long experience totals stay readable in a narrow panel. */
    private static String format(long value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format("%.1fk", value / 1000.0);
        }
        return String.valueOf(value);
    }

    @Override protected void goBack() {
        if (draft == null || editingBaseline == null) { super.goBack(); return; }
        confirmLeavingDraft(draft.save(), editingBaseline, () -> {
            if (parentScreen() == null) { minecraft.setScreen(null); }
            else { minecraft.setScreen(parentScreen()); }
        });
    }
    @Override public void onClose() { goBack(); }
}
