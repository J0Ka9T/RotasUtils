package net.schwarz.rotasutils.compat;

import dev.architectury.platform.Platform;
import net.minecraft.world.entity.Entity;

public final class PehkuiCompat {
    public static final String MOD_ID = "pehkui";
    private static final boolean LOADED = Platform.isModLoaded(MOD_ID);

    private PehkuiCompat() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    public static void setSize(Entity entity, float size) {
        if (LOADED) {
            Api.setSize(entity, size);
        }
    }

    private static final class Api {
        static void setSize(Entity entity, float size) {
            var data = virtuoel.pehkui.api.ScaleTypes.BASE.getScaleData(entity);
            if (data.getTargetScale() != size || data.getBaseScale() != size) {
                data.setTargetScale(size);
                data.setScale(size);
            }
        }
    }
}
