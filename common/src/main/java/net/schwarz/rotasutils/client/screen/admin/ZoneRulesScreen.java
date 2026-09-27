package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.RuleBool;
import net.schwarz.rotasutils.core.RuleDouble;
import net.schwarz.rotasutils.core.ZoneCombatRules;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The zone's play rules. Every rule is "Global" (this zone changes nothing) or a value set here; a zone
 * with higher priority inside this one can still override it. Changes go to the zone editor's unsaved
 * copy when leaving this screen and are saved with the zone.
 */
@Environment(EnvType.CLIENT)
public class ZoneRulesScreen extends RotasScreen {
    private static final String TAKEN = "damage_taken";
    private static final String DEALT = "damage_dealt";
    private static final String HEALING = "healing";
    private static final String XP_LOSS = "xp_loss";

    private final ZoneEditScreen editor;
    private RuleBool pvp;
    private RuleBool hostile;
    private RuleBool keep;
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, String> typed = new HashMap<>();

    public ZoneRulesScreen(ZoneEditScreen editor) {
        super("Zone rules", editor);
        this.editor = editor;
        ZoneCombatRules rules = editor.rules();
        pvp = rules.pvpEnabled();
        hostile = rules.hostileSpawningEnabled();
        keep = rules.keepInventory();
        typed.put(TAKEN, text(rules.playerDamageTakenMultiplier()));
        typed.put(DEALT, text(rules.playerDamageDealtMultiplier()));
        typed.put(HEALING, text(rules.healingMultiplier()));
        typed.put(XP_LOSS, text(rules.rotasXpLossPercentage()));
    }

    @Override
    protected int maxGuiWidth() {
        return 560;
    }

    @Override
    protected int maxGuiHeight() {
        return 320;
    }

    private int half() {
        return (guiWidth - Ui.PAD * 2 - 8) / 2;
    }

    private int second() {
        return guiLeft + Ui.PAD + half() + 8;
    }

    @Override
    protected void buildContent() {
        int x = guiLeft + Ui.PAD;
        int y = guiTop + 56;
        boxes.clear();
        toggle("PvP", pvp, value -> pvp = value, x, y);
        toggle("Hostile mob spawning", hostile, value -> hostile = value, second(), y);
        toggle("Keep inventory on death", keep, value -> keep = value, x, y + 26);
        box(TAKEN, x, y + 80);
        box(DEALT, second(), y + 80);
        box(HEALING, x, y + 124);
        box(XP_LOSS, second(), y + 124);

        int footer = guiTop + guiHeight - 28;
        addBackButton();
        addRenderableWidget(Ui.button(Ui.text("All global"), button -> {
            pvp = RuleBool.inherit();
            hostile = RuleBool.inherit();
            keep = RuleBool.inherit();
            typed.replaceAll((key, value) -> "");
            Sfx.select();
            rebuild(false);
        }).bounds(guiLeft + 66, footer, 96, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("Done"), button -> goBack())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footer, 110, 22).build());
    }

    private void toggle(String name, RuleBool rule, Consumer<RuleBool> set, int x, int y) {
        String state = !rule.overridden() ? "Global" : rule.value() ? "On" : "Off";
        addRenderableWidget(Ui.button(Ui.text(name + ": " + state), button -> {
            // Global -> On -> Off -> Global
            set.accept(!rule.overridden() ? RuleBool.of(true) : rule.value() ? RuleBool.of(false) : RuleBool.inherit());
            Sfx.select();
            rebuild(true);
        }).bounds(x, y, half(), 20).build());
    }

    private void box(String key, int x, int y) {
        EditBox box = new EditBox(font, x, y, half(), 18, Ui.text(""));
        box.setMaxLength(8);
        box.setValue(typed.getOrDefault(key, ""));
        addRenderableWidget(box);
        boxes.put(key, box);
    }

    private void rebuild(boolean keepTyped) {
        if (keepTyped) {
            boxes.forEach((key, box) -> typed.put(key, box.getValue()));
        }
        clearWidgets();
        clearPanels();
        buildContent();
    }

    /** Writes the rules to the zone editor; false (with feedback) when a number is invalid. */
    private boolean apply() {
        boxes.forEach((key, box) -> typed.put(key, box.getValue()));
        try {
            editor.setRules(new ZoneCombatRules(pvp, hostile, number(TAKEN), number(DEALT), number(HEALING), keep,
                    number(XP_LOSS), editor.rules().respawnTarget()));
            return true;
        } catch (IllegalArgumentException invalid) {
            ClientState.feedback(false, "Check the numbers: " + invalid.getMessage());
            return false;
        }
    }

    private RuleDouble number(String key) {
        String value = typed.getOrDefault(key, "").trim();
        if (value.isEmpty()) {
            return RuleDouble.inherit();
        }
        try {
            return RuleDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("'" + value + "' is not a number");
        }
    }

    @Override
    protected void goBack() {
        if (apply()) {
            super.goBack();
        }
    }

    private static String text(RuleDouble rule) {
        if (!rule.overridden()) {
            return "";
        }
        double value = rule.value();
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int y = guiTop + 56;
        Ui.wrapped(graphics, "Global = this zone changes nothing. A higher-priority zone inside this one can still "
                + "override a rule set here.", x, guiTop + 28, guiWidth - Ui.PAD * 2, Ui.TEXT_MUTED);
        Ui.label(graphics, "Damage players take (x, blank = global)", x, y + 68, Ui.TEXT_DIM);
        Ui.label(graphics, "Damage players deal (x, blank = global)", second(), y + 68, Ui.TEXT_DIM);
        Ui.label(graphics, "Healing (x, blank = global)", x, y + 112, Ui.TEXT_DIM);
        Ui.label(graphics, "Rotas XP lost on death (%)", second(), y + 112, Ui.TEXT_DIM);
        Ui.wrapped(graphics, "Multipliers go from 0 to 100, where 1 is normal. Keep inventory also keeps vanilla "
                + "experience. Mods that drop items from their own slots on death may still drop them.",
                x, y + 152, guiWidth - Ui.PAD * 2, Ui.TEXT_MUTED);
    }
}
