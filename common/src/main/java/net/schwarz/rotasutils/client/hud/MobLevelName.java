package net.schwarz.rotasutils.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.schwarz.rotasutils.client.ClientState;

@Environment(EnvType.CLIENT)
public final class MobLevelName {
    private MobLevelName() {
    }

    public static String displayName(Entity entity) {
        String name = entity.getType().getDescription().getString();
        String key = entity.getType().getDescriptionId();
        if (!name.equals(key)) {
            return name;
        }
        String path = key.substring(key.lastIndexOf('.') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? name : out.toString();
    }

    public static boolean isNonCombatant(Entity entity) {
        return entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MISC
                || (entity instanceof net.minecraft.world.entity.OwnableEntity ownable && ownable.getOwnerUUID() != null)
                || (entity instanceof net.minecraft.world.entity.TraceableEntity traceable && traceable.getOwner() != null);
    }

    public static int colorFor(int delta) {
        if (delta >= 30) {
            return 0xB56CFF;
        }
        if (delta >= 15) {
            return 0xE2695C;
        }
        if (delta >= 8) {
            return 0xF07A32;
        }
        if (delta >= 3) {
            return 0xFFAA00;
        }
        if (delta >= -2) {
            return 0x86C05C;
        }
        if (delta >= -7) {
            return 0xFFFFFF;
        }
        return 0x9E9E9E;
    }

    public static String compact(double value) {
        double safe = Double.isFinite(value) ? Math.max(0, value) : 0;
        if (safe < 1_000) return Long.toString(Math.round(safe));
        String suffix; double scaled;
        if (safe >= 1_000_000_000) { suffix = "B"; scaled = safe / 1_000_000_000d; }
        else if (safe >= 1_000_000) { suffix = "M"; scaled = safe / 1_000_000d; }
        else { suffix = "K"; scaled = safe / 1_000d; }
        String number = scaled >= 100 ? String.format(java.util.Locale.ROOT, "%.0f", scaled)
                : scaled >= 10 ? String.format(java.util.Locale.ROOT, "%.1f", scaled)
                : String.format(java.util.Locale.ROOT, "%.2f", scaled);
        return number.replaceAll("\\.0+$", "").replaceAll("(\\.[0-9]*?)0+$", "$1") + suffix;
    }

    public static String health(double current, double maximum) { return compact(current) + " / " + compact(maximum); }

    public static int levelFrom(String text, String format) {
        if (text == null || format == null || text.isEmpty() || format.isEmpty()) {
            return -1;
        }
        java.util.regex.Pattern pattern = pattern(format);
        var matcher = pattern.matcher(text);
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (RuntimeException invalid) {
            return -1;
        }
    }

    private static String cachedFormat;
    private static java.util.regex.Pattern cachedPattern;

    private static synchronized java.util.regex.Pattern pattern(String format) {
        if (!format.equals(cachedFormat)) {
            cachedPattern = java.util.regex.Pattern.compile(regex(format));
            cachedFormat = format;
        }
        return cachedPattern;
    }

    private static String regex(String format) {
        StringBuilder regex = new StringBuilder("^");
        int index = 0;
        while (index < format.length()) {
            if (format.startsWith("{name}", index) || format.startsWith("{tier}", index)) {
                regex.append(".*?");
                index += 6;
            } else if (format.startsWith("{level}", index)) {
                regex.append("(\\d{1,5})");
                index += 7;
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(format.charAt(index))));
                index++;
            }
        }
        regex.append('$');
        return regex.toString();
    }

    public static net.schwarz.rotasutils.core.MobAffix.Mark mark(Entity entity) {
        Component name = entity == null ? null : entity.getCustomName();
        return name == null ? net.schwarz.rotasutils.core.MobAffix.Mark.NONE
                : net.schwarz.rotasutils.core.MobAffix.decode(name.getStyle().getInsertion());
    }

    public static String stars(net.schwarz.rotasutils.core.MonsterRank rank) {
        return "★".repeat(Math.max(0, rank.stars()));
    }

    public static boolean replacedByPlate(Entity entity, Component name) {
        if (name == null || entity == null || entity instanceof Player) {
            return false;
        }
        String format = ClientState.levelConfig().mobLevel().nameFormat();
        return levelFrom(name.getString(), format) >= 1;
    }

    public static Component color(Component name, Entity entity) {
        if (name == null || entity == null || entity instanceof Player) {
            return name;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player == entity) {
            return name;
        }
        var config = ClientState.levelConfig().mobLevel();
        if (!config.colorByDelta()) {
            return name;
        }
        int level = levelFrom(name.getString(), config.nameFormat());
        if (level <= 0) {
            return name;
        }
        int delta = level - ClientState.progress().level();
        int rgb = colorFor(delta);
        return name.copy().withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
    }
}
