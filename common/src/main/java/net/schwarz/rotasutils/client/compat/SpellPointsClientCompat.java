package net.schwarz.rotasutils.client.compat;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public final class SpellPointsClientCompat {
    public static final String MOD_ID = "irons_spellbooks";
    private static final ResourceLocation MAX_MANA = new ResourceLocation(MOD_ID, "max_mana");

    public record Reading(float current, float max) {
        public float progress() {
            return max <= 0f ? 0f : Math.max(0f, Math.min(1f, current / max));
        }
    }

    private static boolean attempted;
    private static Attribute maxMana;
    private static Method playerMana;

    private SpellPointsClientCompat() {
    }

    public static Reading read(LocalPlayer player) {
        if (player == null || !prepare()) return null;
        AttributeInstance instance = player.getAttribute(maxMana);
        if (instance == null) return null;
        try {
            Object value = playerMana.invoke(null);
            float current = value instanceof Number number ? number.floatValue() : 0f;
            return new Reading(current, (float) Math.max(1.0, instance.getValue()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            playerMana = null;
            return null;
        }
    }

    private static boolean prepare() {
        if (!attempted) {
            attempted = true;
            if (Platform.isModLoaded(MOD_ID)) {
                maxMana = BuiltInRegistries.ATTRIBUTE.get(MAX_MANA);
                try {
                    playerMana = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData")
                            .getMethod("getPlayerMana");
                } catch (ReflectiveOperationException | LinkageError e) {
                    playerMana = null;
                }
            }
        }
        return maxMana != null && playerMana != null;
    }
}
