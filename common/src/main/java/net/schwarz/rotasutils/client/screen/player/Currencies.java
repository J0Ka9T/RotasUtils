package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public final class Currencies {
    private Currencies() {
    }

    public static String name(String id) {
        return switch (id) {
            case "rotas:gold" -> "เหรียญทอง";
            case "rotas:season_token" -> "เหรียญซีซั่น";
            default -> {
                String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
                yield path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        };
    }

    public static String amount(long value) {
        long abs = Math.abs(value);
        if (abs >= 10_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (abs >= 100_000) return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static int rarityColor(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> 0xFF86C05C;
            case "RARE" -> 0xFF5AA9E6;
            case "EPIC" -> 0xFFB07CE8;
            case "LEGENDARY" -> 0xFFE3A857;
            default -> 0xFFB6A17C;
        };
    }

    public static String rarityName(String rarity) {
        return switch (rarity) {
            case "COMMON" -> "ธรรมดา";
            case "UNCOMMON" -> "ไม่ธรรมดา";
            case "RARE" -> "หายาก";
            case "EPIC" -> "มหากาพย์";
            case "LEGENDARY" -> "ตำนาน";
            default -> "ไม่มีระดับ";
        };
    }
}
