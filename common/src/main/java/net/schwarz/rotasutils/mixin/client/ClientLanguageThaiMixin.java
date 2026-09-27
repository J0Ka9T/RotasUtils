package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.resources.language.ClientLanguage;
import net.schwarz.rotasutils.util.ThaiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes every RotasUtils translation key render in Thai, whatever language the game uses.
 *
 * <p>Item names, key bindings, tooltips and any {@code Component.translatable} the mod sends go
 * through the client language, so answering here covers text the mod does not draw itself. Only
 * keys present in RotasUtils' own Thai file are answered; vanilla and other mods keep the player's
 * chosen language. Signatures verified with javap against the loom-mapped 1.20.1 jar.</p>
 */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageThaiMixin {
    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true)
    private void rotasutils$thaiText(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String thai = ThaiText.get(key);
        if (thai != null) {
            cir.setReturnValue(thai);
        }
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void rotasutils$thaiHas(String key, CallbackInfoReturnable<Boolean> cir) {
        if (ThaiText.has(key)) {
            cir.setReturnValue(true);
        }
    }
}
