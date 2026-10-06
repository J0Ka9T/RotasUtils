package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public final class ShaderPackCompat {
    private static Object api;
    private static Method inUse;
    private static boolean resolved;

    private ShaderPackCompat() {
    }

    private static boolean overlay;

    static void overlay(boolean drawing) {
        overlay = drawing;
    }

    static boolean overlay() {
        return overlay;
    }

    public static boolean active() {
        return !overlay && packInUse();
    }

    public static boolean packInUse() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                api = null;
            }
        }
        if (api == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
