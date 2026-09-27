package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.EldritchSkyClientTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pulls the vanilla sky and cloud colours toward the eldritch palette while the event is active.
 *
 * <p>Sky colour is also the source of the fog colour in vanilla, so tinting it here keeps fog,
 * horizon and dome consistent. Both hooks return untouched vanilla values while no event is
 * active, because the tint facade answers null when there is nothing to do.</p>
 */
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

    /** Mojang-mapped 1.20.1 return hook consumed by LightTexture#updateLightTexture. */
    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void rotasutils$eldritchDaylight(float partialTick, CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValue();
        float eclipsed = EldritchSkyClientTint.daylight(vanilla, partialTick);
        if (Float.floatToIntBits(eclipsed) != Float.floatToIntBits(vanilla)) {
            cir.setReturnValue(eclipsed);
        }
    }
}
