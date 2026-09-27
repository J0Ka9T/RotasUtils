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

/**
 * Puts the worn title (ฉายา) in front of a player's name wherever the server writes it: chat, death
 * messages, {@code /msg} and the commands that name a player.
 *
 * <p>The name plate above a player's head is built on the client from its own copy of the player, so it
 * is handled separately; this is the server half, and it needs no packet of its own.</p>
 */
// getDisplayName is declared on Player, not on ServerPlayer, so that is what this targets; the body
// then does nothing unless the player is the server-side one.
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
        // peek: a name is asked for in places a fresh record must not be created, such as a tab list
        // refresh for a player who has not finished joining.
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
