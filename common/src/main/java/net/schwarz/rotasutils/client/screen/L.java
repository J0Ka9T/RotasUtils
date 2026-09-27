package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * UI text lookup.
 *
 * <p>Screens draw raw strings through {@link Ui}, so this resolves a translation key to a plain
 * string rather than handing back a {@link Component}. RotasUtils' interface is always Thai, even
 * when Minecraft is set to English, so keys resolve through {@link ThaiText}. A key that is not in
 * the mod's Thai file falls back to the normal game language lookup.</p>
 */
@Environment(EnvType.CLIENT)
public final class L {
    private L() {
    }

    /** Thai text for {@code key}, or the key itself when nothing matches. */
    public static String t(String key) {
        return ThaiText.has(key) ? ThaiText.t(key) : Component.translatable(key).getString();
    }

    public static String t(String key, Object... args) {
        return ThaiText.has(key) ? ThaiText.t(key, args) : Component.translatable(key, args).getString();
    }

    public static Component c(String key) {
        return ThaiText.has(key) ? ThaiText.c(key) : Component.translatable(key);
    }

    public static Component c(String key, Object... args) {
        return ThaiText.has(key) ? ThaiText.c(key, args) : Component.translatable(key, args);
    }
}
