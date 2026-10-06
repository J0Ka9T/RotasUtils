package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.EldritchSkyClientTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void rotasutils$eldritchSkyColor(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 tinted = EldritchSkyClientTint.tintSky(cir.getReturnValue(), partialTick);
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void rotasutils$eldritchCloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 tinted = EldritchSkyClientTint.tintCloud(cir.getReturnValue(), partialTick);
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void rotasutils$eldritchDaylight(float partialTick, CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValue();
        float eclipsed = EldritchSkyClientTint.daylight(vanilla, partialTick);
        if (Float.floatToIntBits(eclipsed) != Float.floatToIntBits(vanilla)) {
            cir.setReturnValue(eclipsed);
        }
    }
}
