package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.resources.language.ClientLanguage;
import net.schwarz.rotasutils.util.ThaiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
