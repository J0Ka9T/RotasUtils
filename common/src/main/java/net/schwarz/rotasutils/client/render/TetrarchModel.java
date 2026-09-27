package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.schwarz.rotasutils.entity.TetrarchEntity;
import net.schwarz.rotasutils.entity.TetrarchPower;

/**
 * The Tetrarch on the player skeleton, with a pose for each power: arms raised to call judgement,
 * crossed for the aegis, flung wide for the well, one hand levelled down the lance, and so on. Each
 * pose eases in over the first ticks of a cast and back out over the last, on top of the ordinary
 * walk, so nothing snaps. Between casts it holds itself like a sovereign: arms a little out, slow
 * breathing sway.
 */
@Environment(EnvType.CLIENT)
public class TetrarchModel extends PlayerModel<TetrarchEntity> {
    private static final int EASE = 6;

    public TetrarchModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(TetrarchEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partial = ageInTicks - entity.tickCount;
        float breath = (float) Math.sin(ageInTicks * 0.08f);

        // A sovereign's stance between casts.
        rightArm.zRot += 0.14f + 0.03f * breath;
        leftArm.zRot -= 0.14f + 0.03f * breath;

        float arrival = entity.arrival(partial);
        if (arrival < 1f) {
            float k = 1f - arrival;
            blend(k, -0.4f, 0f, 1.2f, -0.4f, 0f, -1.2f, -0.35f, 0f);
        }
        if (entity.deathTime > 0) {
            float k = Math.min(1f, entity.deathTime / 20f);
            blend(k, -0.3f, 0.2f, 2.4f, -0.3f, -0.2f, -2.4f, -0.6f, 0f);
        }

        TetrarchPower power = entity.casting();
        if (power != null) {
            float t = entity.castTime(partial);
            float k = smooth(t / EASE) * (1f - smooth((t - power.length() + EASE) / EASE));
            boolean landed = t >= power.windup;
            switch (power) {
                case RIFT_STEP -> blend(k, 0.6f, 0f, 0.3f, 0.6f, 0f, -0.3f, 0.45f, 0f);
                case VIOLET_LANCE -> {
                    float recoil = landed ? 0.35f : 0f;
                    blend(k, -1.62f + recoil, head.yRot, 0.05f, 0.55f, 0f, -0.25f, head.xRot, head.yRot);
                }
                case JUDGEMENT -> {
                    if (landed) {
                        blend(k, -0.7f, 0f, 0.4f, -0.7f, 0f, -0.4f, 0.35f, 0f);
                    } else {
                        blend(k, -3.0f, 0f, 0.22f, -3.0f, 0f, -0.22f, -0.6f, 0f);
                    }
                }
                case GOLD_AEGIS -> blend(k, -1.25f, -0.55f, 0.1f, -1.25f, 0.55f, -0.1f, 0.2f, 0f);
                case CRIMSON_NOVA -> {
                    if (landed) {
                        blend(k, -0.25f, 0f, 0.5f, -0.25f, 0f, -0.5f, 0.45f, 0f);
                    } else {
                        blend(k, -2.9f, 0.1f, 0.1f, -2.9f, -0.1f, -0.1f, -0.4f, 0f);
                    }
                }
                case CRIMSON_BRAND -> blend(k, 0.1f, 0f, 0.2f, -1.62f, head.yRot, -0.05f, head.xRot, head.yRot);
                case VOID_GRASP -> blend(k, -0.65f, 0f, 0.35f, -0.65f, 0f, -0.35f, 0.55f, 0f);
                case GRAVITY_WELL -> blend(k, -0.2f, 0f, 1.5f, -0.2f, 0f, -1.5f, -0.25f, 0f);
                case SUMMON_ECHOES -> blend(k, -0.3f, 0f, 2.4f, -0.3f, 0f, -2.4f, -0.5f, 0f);
                case CONVERGENCE -> blend(k, -0.4f, 0f, 2.7f, -0.4f, 0f, -2.7f, -0.65f, 0f);
            }
        }
        hat.copyFrom(head);
        jacket.copyFrom(body);
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
    }

    /** Eases the arms and head toward a pose by {@code k}. */
    private void blend(float k, float rx, float ry, float rz, float lx, float ly, float lz, float hx, float hy) {
        if (k <= 0f) {
            return;
        }
        rightArm.xRot = lerp(rightArm.xRot, rx, k);
        rightArm.yRot = lerp(rightArm.yRot, ry, k);
        rightArm.zRot = lerp(rightArm.zRot, rz, k);
        leftArm.xRot = lerp(leftArm.xRot, lx, k);
        leftArm.yRot = lerp(leftArm.yRot, ly, k);
        leftArm.zRot = lerp(leftArm.zRot, lz, k);
        head.xRot = lerp(head.xRot, hx, k);
        head.yRot = lerp(head.yRot, hy, k);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
