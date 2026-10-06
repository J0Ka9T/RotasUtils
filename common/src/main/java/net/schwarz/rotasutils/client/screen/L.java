package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.util.ThaiText;

@Environment(EnvType.CLIENT)
public final class L {
    private L() {
    }
    public static boolean has(String key) {
        return ThaiText.has(key);
    }

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
