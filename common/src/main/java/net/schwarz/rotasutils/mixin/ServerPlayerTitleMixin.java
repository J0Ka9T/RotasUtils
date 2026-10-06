package net.schwarz.rotasutils.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.TitleService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class ServerPlayerTitleMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void rotasutils$title(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer self)) {
            return;
        }
        if (self.server == null || !self.server.isSameThread()) {
            return;
        }
        RotasData data = RotasData.instance();
        if (data == null) {
            return;
        }
        var progress = data.peek(self.getUUID());
        if (progress == null || progress.activeTitle().isBlank()) {
            return;
        }
        Component name = cir.getReturnValue();
        if (name == null) {
            return;
        }
        cir.setReturnValue(TitleService.decorate(data, progress, name));
    }
}
