package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.title.TitleDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class TitleEditScreen extends SimpleFieldScreen {
    private record Stat(String attribute, String label, boolean percent) {
    }

    private static final Map<String, Stat> STATS = new LinkedHashMap<>();
    static {
        STATS.put("Attack %", new Stat("minecraft:generic.attack_damage", "Attack", true));
        STATS.put("Attack (flat)", new Stat("minecraft:generic.attack_damage", "Attack", false));
        STATS.put("Max health %", new Stat("minecraft:generic.max_health", "Max health", true));
        STATS.put("Max health (flat)", new Stat("minecraft:generic.max_health", "Max health", false));
        STATS.put("Armor (flat)", new Stat("minecraft:generic.armor", "Armor", false));
        STATS.put("Move speed %", new Stat("minecraft:generic.movement_speed", "Move speed", true));
        STATS.put("Attack speed %", new Stat("minecraft:generic.attack_speed", "Attack speed", true));
        STATS.put("Luck (flat)", new Stat("minecraft:generic.luck", "Luck", false));
        STATS.put("Defense (flat)", new Stat("rotas:defense", "Defense", false));
        STATS.put("Evasion (flat)", new Stat("rotas:evasion", "Evasion", false));
        STATS.put("Magic attack (flat)", new Stat("rotas:magic_attack", "Magic attack", false));
        STATS.put("Magic power (flat)", new Stat("rotas:magic_power", "Magic power", false));
        STATS.put("Armor pen %", new Stat("rotas:armor_pen", "Armor Pen", true));
        STATS.put("Cooldown reduction %", new Stat("rotas:cooldown_reduction", "CDR", true));
        STATS.put("Drop rate %", new Stat("rotas:drop_rate", "Drop Rate", true));
        STATS.put("Life steal %", new Stat("rotas:life_steal", "Life Steal", true));
        STATS.put("Damage reduction %", new Stat("rotas:damage_reduction", "Damage Red", true));
        STATS.put("Stamina regen %", new Stat("rotas:stamina_regen", "Stamina Regen", true));
    }

    private static final Map<String, Integer> COLOURS = new LinkedHashMap<>();
    static {
        COLOURS.put("Gold", 0xE0AC4C);
        COLOURS.put("Parchment", 0xB6A17C);
        COLOURS.put("Leaf green", 0x86C05C);
        COLOURS.put("Sky blue", 0x5AA9E6);
        COLOURS.put("Teal", 0x7FD1C7);
        COLOURS.put("Royal purple", 0xB07CE8);
        COLOURS.put("Blood red", 0xE2695C);
        COLOURS.put("Deep crimson", 0xB23A48);
        COLOURS.put("Steel", 0x9FB4C7);
        COLOURS.put("Shadow", 0x3F3F46);
        COLOURS.put("White", 0xFFFFFF);
    }

    private final CompoundTag tag;
    private final String previous;

    public TitleEditScreen(Screen parent, TitleDef title, boolean duplicate) {
        super(title == null ? "New title" : duplicate ? "Duplicate title" : "Title: " + title.name(), parent);
        this.tag = title == null ? new TitleDef("new_title", "").save() : title.save();
        if (title == null) tag.putString("name", "");
        if (duplicate) tag.putString("id", tag.getString("id") + "_copy");
        this.previous = title == null || duplicate ? "" : title.id();
    }

    @Override
    protected void collectFields(List<Field> target) {
        target.add(text("ID", () -> tag.getString("id"), value -> tag.putString("id", value.trim())));
        target.add(text("Name", () -> tag.getString("name"), value -> tag.putString("name", value)));
        target.add(new Field("Looks like", Field.Kind.ACTION,
                () -> "[" + (tag.getString("name").isBlank() ? "?" : tag.getString("name")) + "] Steve", value -> { }));
        target.add(text("Description", () -> tag.getString("desc"), value -> tag.putString("desc", value)));

        target.add(new Field("How to earn", Field.Kind.ACTION, () -> plain(condition()) + "  ▾", value -> {
            Map<String, String> options = new LinkedHashMap<>();
            for (TitleDef.Condition condition : TitleDef.Condition.values()) options.put(condition.name(), plain(condition));
            minecraft.setScreen(PickerScreen.choices("How to earn", options, this, picked -> tag.putString("condition", picked)));
        }));
        TitleDef.Condition condition = condition();
        if (condition == TitleDef.Condition.KILL_ENTITY) {
            target.add(new Field("Mob", Field.Kind.ACTION,
                    () -> (tag.getString("target").isBlank() ? "Pick a mob" : tag.getString("target")) + "  ▾",
                    value -> minecraft.setScreen(new PickerScreen(ParamSpec.ParamKind.ENTITY, this,
                            picked -> tag.putString("target", picked)))));
        } else if (condition == TitleDef.Condition.SUB_LEVEL) {
            target.add(text("Sub job id (miner, chef, farmer...)", () -> tag.getString("target"), value -> tag.putString("target", value.trim())));
        } else if (condition == TitleDef.Condition.STAT) {
            target.add(text("Counter (crafted, crafted.chef, stars, gold_stars, star_meals, nodes, rich_nodes, rich_veins)",
                    () -> tag.getString("target"), value -> tag.putString("target", value.trim())));
        } else if (condition == TitleDef.Condition.QUEST) {
            target.add(pickQuest("Quest", () -> tag.getString("target"), value -> tag.putString("target", value.trim())));
        }
        if (condition != TitleDef.Condition.MANUAL) {
            target.add(number(amountLabel(condition), () -> Long.toString(tag.getLong("amount")),
                    value -> tag.putLong("amount", Long.parseLong(value.trim()))));
        }

        target.add(new Field("Rarity", Field.Kind.ACTION, () -> rarityLabel() + "  ▾", value -> {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("", "Auto (" + TitleDef.load(tag).rarity().key() + ")");
            for (TitleDef.Rarity rarity : TitleDef.Rarity.values()) options.put(rarity.name(), cap(rarity.key()));
            minecraft.setScreen(PickerScreen.choices("Rarity", options, this, picked -> {
                if (picked.isBlank()) tag.remove("rarity");
                else tag.putString("rarity", picked);
            }));
        }));
        target.add(text("Colour (hex RRGGBB)", () -> String.format(Locale.ROOT, "%06X", tag.getInt("color") & 0xFFFFFF),
                value -> tag.putInt("color", 0xFF000000 | Integer.parseInt(value.trim().replace("#", ""), 16))));
        target.add(new Field("Colour preset", Field.Kind.ACTION, () -> "Pick a colour  ▾", value -> {
            Map<String, String> options = new LinkedHashMap<>();
            COLOURS.forEach((name, rgb) -> options.put(Integer.toString(rgb), name));
            options.put("rarity", "Match the rarity");
            minecraft.setScreen(PickerScreen.choices("Colour", options, this, picked -> tag.putInt("color",
                    0xFF000000 | (picked.equals("rarity") ? TitleDef.load(tag).rarity().color & 0xFFFFFF : Integer.parseInt(picked)))));
        }));

        ListTag effects = tag.getList("effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            int index = i;
            CompoundTag effect = effects.getCompound(i);
            target.add(new Field("Bonus " + (i + 1), Field.Kind.ACTION,
                    () -> net.schwarz.rotasutils.client.screen.player.TitleScreen.bonusText(CharacterStat.Effect.load(effect)) + "  ▾",
                    value -> pickStat(stat -> apply(effects.getCompound(index), stat, amountOf(effects.getCompound(index))))));
            target.add(decimal("Bonus " + (i + 1) + " amount" + (effect.getBoolean("percent") ? " (%)" : ""),
                    () -> trim(amountOf(effect)),
                    value -> apply(effects.getCompound(index), statOf(effects.getCompound(index)), Double.parseDouble(value.trim()))));
            target.add(new Field("Remove bonus " + (i + 1), Field.Kind.ACTION, () -> "Remove", value -> {
                effects.remove(index);
                rebuildWidgets();
            }));
        }
        if (effects.size() < TitleDef.MAX_EFFECTS) {
            target.add(new Field("Add a bonus", Field.Kind.ACTION, () -> "Add  ▾", value -> pickStat(stat -> {
                CompoundTag effect = new CompoundTag();
                apply(effect, stat, stat.percent() ? 2 : 1);
                ListTag list = tag.getList("effects", Tag.TAG_COMPOUND);
                list.add(effect);
                tag.put("effects", list);
            })));
        }

        target.add(toggle("Unique (one owner on the server)", () -> onOff("unique"), value -> flip("unique")));
        target.add(toggle("Hidden until earned", () -> onOff("hidden"), value -> flip("hidden")));
        target.add(toggle("Enabled", () -> onOff("enabled"), value -> flip("enabled")));
        target.add(number("List order", () -> Integer.toString(tag.getInt("order")), value -> tag.putInt("order", Integer.parseInt(value.trim()))));
    }

    private void pickStat(java.util.function.Consumer<Stat> then) {
        Map<String, String> options = new LinkedHashMap<>();
        STATS.keySet().forEach(name -> options.put(name, name));
        minecraft.setScreen(PickerScreen.choices("Bonus", options, this, picked -> then.accept(STATS.get(picked))));
    }

    private static void apply(CompoundTag effect, Stat stat, double amount) {
        if (stat == null) return;
        effect.putString("attribute", stat.attribute());
        effect.putString("label", stat.label());
        effect.putBoolean("percent", stat.percent());
        effect.putString("operation", (stat.percent() ? CharacterStat.Operation.MULTIPLY_BASE : CharacterStat.Operation.ADD).name());
        effect.putDouble("per_point", stat.percent() ? amount / 100.0 : amount);
    }

    private static double amountOf(CompoundTag effect) {
        double value = effect.getDouble("per_point");
        return effect.getBoolean("percent") ? value * 100 : value;
    }

    private static Stat statOf(CompoundTag effect) {
        for (Stat stat : STATS.values()) {
            if (stat.attribute().equals(effect.getString("attribute")) && stat.percent() == effect.getBoolean("percent")) return stat;
        }
        return new Stat(effect.getString("attribute"), effect.getString("label"), effect.getBoolean("percent"));
    }

    @Override
    protected String helpFor(Field field) {
        String label = field.label();
        if (label.startsWith("Bonus") && label.endsWith("amount (%)")) return "2 = +2% while this title is worn";
        if (label.startsWith("Bonus") && label.endsWith("amount")) return "Added while this title is worn";
        if (label.startsWith("Bonus")) return "Click to pick which stat this bonus raises";
        return switch (label) {
            case "ID" -> "Lowercase, e.g. rotas:dragon_slayer. Changing it renames the title.";
            case "Looks like" -> "How players will see it over their heads";
            case "How to earn" -> "Click to choose. \"Admin gives it\" = nobody earns it alone.";
            case "Mob" -> "Click to choose the mob to kill";
            case "Rarity" -> "Sets the frame colour and sort order in the title list";
            case "Colour (hex RRGGBB)" -> "e.g. E0C173 (gold), or use Colour preset below";
            case "Unique (one owner on the server)" -> "The first player to earn it keeps it; nobody else can";
            case "Hidden until earned" -> "Not listed for players until someone earns it - a secret";
            default -> super.helpFor(field);
        };
    }

    @Override
    protected String validateValue(Field field, String value) {
        if (field.label().startsWith("Colour (hex") && !value.trim().replace("#", "").matches("[0-9a-fA-F]{6}")) {
            return "Six hex digits, e.g. E0C173";
        }
        if (field.label().equals("ID") && !value.trim().matches("[a-z0-9_:./-]{1,48}")) {
            return "1-48 of a-z 0-9 _ : . / -";
        }
        return super.validateValue(field, value);
    }

    @Override
    protected void buildContent() {
        super.buildContent();
        addRenderableWidget(Ui.primaryButton(Ui.text("Save"), button -> {
            if (!validateFields()) return;
            CompoundTag payload = new CompoundTag();
            payload.put("title", tag.copy());
            payload.putString("previous", previous);
            send("title_admin_save", payload);
            super.goBack();
        }).bounds(guiLeft + guiWidth - 110, guiTop + guiHeight - 64, 98, 20).build());
    }

    private TitleDef.Condition condition() {
        return TitleDef.Condition.byName(tag.getString("condition"));
    }

    private String rarityLabel() {
        String set = tag.getString("rarity");
        return set.isBlank() ? "Auto (" + TitleDef.load(tag).rarity().key() + ")" : cap(set.toLowerCase(Locale.ROOT));
    }

    static String plain(TitleDef.Condition condition) {
        return switch (condition.name()) {
            case "LEVEL" -> "Reach a level";
            case "KILL_ENTITY" -> "Kill one kind of mob";
            case "KILL_ANY" -> "Kill any monsters";
            case "KILL_BOSS" -> "Kill bosses";
            case "QUEST" -> "Finish one quest";
            case "QUEST_COUNT" -> "Finish many quests";
            case "REFINE" -> "Refine gear to +N";
            case "NEMESIS" -> "Beat nemeses";
            case "GOLD" -> "Hold gold";
            case "MANUAL" -> "Admin gives it";
            case "BOUNTY" -> "Turn in bounties";
            case "BREED" -> "Breed horses";
            case "BESTIARY" -> "Fill the bestiary";
            case "WAYSTONE" -> "Discover waystones";
            case "TITLE_COUNT" -> "Collect titles";
            case "DEATH" -> "Die and get back up";
            case "TRADE" -> "Earn gold trading";
            case "SUB_LEVEL" -> "Level a sub job";
            case "STAT" -> "Reach a play count (crafts, stars, ore)";
            default -> condition.name();
        };
    }

    private static String amountLabel(TitleDef.Condition condition) {
        return switch (condition.name()) {
            case "LEVEL" -> "Level needed";
            case "REFINE" -> "Refine level needed";
            case "GOLD" -> "Gold needed";
            case "QUEST" -> "Times finished";
            case "KILL_ENTITY", "KILL_ANY", "KILL_BOSS", "NEMESIS" -> "Kills needed";
            case "QUEST_COUNT" -> "Quests needed";
            case "SUB_LEVEL" -> "Sub job level needed";
            default -> "How many";
        };
    }

    private static String cap(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private String onOff(String key) {
        return tag.getBoolean(key) ? "ON" : "OFF";
    }

    private void flip(String key) {
        tag.putBoolean(key, !tag.getBoolean(key));
    }
}
