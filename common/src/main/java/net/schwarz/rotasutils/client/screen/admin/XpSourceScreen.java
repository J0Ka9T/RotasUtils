package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.level.XpSourceConfig;

import java.util.List;

@Environment(EnvType.CLIENT)
public class XpSourceScreen extends SimpleFieldScreen {
    private final XpSourceConfig config;

    public XpSourceScreen(XpSourceConfig config, Screen parent) {
        super(config.source().display(), parent);
        this.config = config;
    }

    @Override
    protected void collectFields(List<Field> target) {
        target.add(toggle("Enabled",
                () -> config.enabled() ? "Yes" : "No",
                value -> config.setEnabled(!config.enabled())));
        if (config.source() != XpSource.MOB_KILL && config.source() != XpSource.BOSS_KILL) {
            target.add(number("Base amount",
                    () -> String.valueOf(config.baseAmount()),
                    value -> config.setBaseAmount(parseInt(value, 0))));
        }
        target.add(decimal("Multiplier",
                () -> String.valueOf(config.multiplier()),
                value -> config.setMultiplier(parseDouble(value, 1.0))));
        target.add(number("Cooldown (seconds)",
                () -> String.valueOf(config.cooldownSeconds()),
                value -> config.setCooldownSeconds(parseInt(value, 0))));
        target.add(number("Daily limit (0 = none)",
                () -> String.valueOf(config.dailyLimit()),
                value -> config.setDailyLimit(parseInt(value, 0))));
        target.add(number("Per-target limit (0 = none)",
                () -> String.valueOf(config.perTargetLimit()),
                value -> config.setPerTargetLimit(parseInt(value, 0))));
        target.add(toggle("Anti-farming",
                () -> config.antiFarm() ? "Yes" : "No",
                value -> config.setAntiFarm(!config.antiFarm())));
        target.add(new Field("Required dimension", Field.Kind.PICK_DIMENSION,
                () -> config.dimension().isEmpty() ? "(any)" : config.dimension(),
                config::setDimension));
        target.add(number("Minimum player level",
                () -> String.valueOf(config.minLevel()),
                value -> config.setMinLevel(parseInt(value, 0))));
        target.add(number("Maximum player level",
                () -> String.valueOf(config.maxLevel()),
                value -> config.setMaxLevel(parseInt(value, 0))));
        target.add(decimal("Party share (0 - 1)",
                () -> String.valueOf(config.partyShare()),
                value -> config.setPartyShare(parseDouble(value, 0))));
    }
}
