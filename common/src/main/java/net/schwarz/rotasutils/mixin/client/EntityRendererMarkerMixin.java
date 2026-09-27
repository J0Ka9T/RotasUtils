package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.client.NpcMarkerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the RotasUtils quest marker over entities that are configured NPCs.
 *
 * <p>Injected on the shared {@link EntityRenderer} rather than on any particular renderer so
 * an NPC can be a villager, an armour stand or a modded entity without this having to know.
 * The renderer itself returns immediately for entities that are not NPCs.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMarkerMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void rotasutils$renderQuestMarker(Entity entity, float entityYaw, float partialTick,
                                              PoseStack poseStack, MultiBufferSource buffer,
                                              int packedLight, CallbackInfo ci) {
        NpcMarkerRenderer.render(entity, poseStack, buffer, packedLight);
    }
}
