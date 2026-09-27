package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Method;

/**
 * Whether an Iris/Oculus shader pack is drawing the world. Shader packs replace the sky programs with
 * their own blend mode and alpha cutoff, which turns the sky's soft, feathered layers into hard-edged
 * polygons. Looked up by reflection so the mod needs neither Iris nor Oculus to run.
 */
@Environment(EnvType.CLIENT)
public final class ShaderPackCompat {
    private static Object api;
    private static Method inUse;
    private static boolean resolved;

    private ShaderPackCompat() {
    }

    /** True while the sky is being drawn after the pack has finished its frame, where vanilla shaders apply. */
    private static boolean overlay;

    static void overlay(boolean drawing) {
        overlay = drawing;
    }

    static boolean overlay() {
        return overlay;
    }

    /**
     * Whether the pack's programs are in charge of what is being drawn right now: false during the
     * after-frame overlay, where the ordinary vanilla shaders are back.
     */
    public static boolean active() {
        return !overlay && packInUse();
    }

    /** Whether a shader pack is on at all. */
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
