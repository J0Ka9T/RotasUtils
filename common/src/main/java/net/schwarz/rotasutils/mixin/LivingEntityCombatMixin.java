package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.server.CombatStats;
import net.schwarz.rotasutils.server.ZoneRuleService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Combat effects Minecraft has no attribute for.
 *
 * <p>Character stats: evasion (a dodged hit deals nothing), defense (damage times scale / (scale + defense))
 * and magic attack (flat bonus on magic damage). Zone rules: PvP off cancels player-versus-player damage,
 * and the zone's damage taken/dealt multipliers scale player damage.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCombatMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void rotasutils$evade(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (ZoneRuleService.blocksPvp(self, source)) {
            cir.setReturnValue(false);
            return;
        }
        if (self instanceof ServerPlayer player && CombatStats.evade(player, source)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float rotasutils$statDamage(float amount, DamageSource source, float originalAmount) {
        LivingEntity self = (LivingEntity) (Object) this;
        float scaled = CombatStats.modifyDamage(self, source, ZoneRuleService.scaleDamage(self, source, amount));
        return net.schwarz.rotasutils.server.MobAffixService.modifyIncoming(self, source, scaled);
    }

    /** Elite affix and weapon rune on-hit effects once a hit has really landed. */
    @Inject(method = "hurt", at = @At("RETURN"))
    private void rotasutils$affixHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            net.schwarz.rotasutils.server.MobAffixService.afterHurt((LivingEntity) (Object) this, source, amount);
            net.schwarz.rotasutils.server.RuneService.afterHurt((LivingEntity) (Object) this, source, amount);
        }
    }
}
