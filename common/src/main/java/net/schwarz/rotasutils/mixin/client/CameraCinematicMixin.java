package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.SkyClashCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraCinematicMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void rotasutils$cinematic(BlockGetter level, Entity entity, boolean detachedView, boolean mirrored,
                                      float partialTick, CallbackInfo ci) {
        var director = net.schwarz.rotasutils.client.cinematic.CinematicDirector.camera((Camera) (Object) this, partialTick);
        if (director != null) {
            setRotation((float) director.yaw(), (float) director.pitch());
            setPosition(director.position().x, director.position().y, director.position().z);
            detached = detached || director.weight() > 0.02;
            return;
        }
        Vec3 eye = getPosition();
        double[] shot = SkyClashCinematic.camera(eye.x, eye.y, eye.z, getYRot(), getXRot(), partialTick);
        if (shot == null) {
            return;
        }
        setRotation((float) shot[3], (float) shot[4]);
        setPosition(shot[0], shot[1], shot[2]);
        if (SkyClashCinematic.detaches(partialTick)) {
            detached = true;
        }
    }
}
