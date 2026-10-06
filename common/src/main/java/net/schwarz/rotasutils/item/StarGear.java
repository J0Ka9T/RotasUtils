package net.schwarz.rotasutils.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.List;

public final class StarGear {
    private StarGear() {
    }

    public static ItemStack apply(ItemStack stack, int star) {
        if (star < 1 || star > 2) {
            return stack;
        }
        StarQuality.apply(stack, star);
        if (stack.getItem() instanceof PotionItem) {
            empowerPotion(stack, star);
        }
        return stack;
    }

    private static void empowerPotion(ItemStack stack, int star) {
        List<MobEffectInstance> boosted = new ArrayList<>();
        boolean instant = false;
        for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
            if (effect.getEffect().isInstantenous()) {
                instant = true;
                boosted.add(new MobEffectInstance(effect.getEffect(), 1, effect.getAmplifier() + (star == 2 ? 1 : 0)));
            } else {
                boosted.add(new MobEffectInstance(effect.getEffect(), (int) (effect.getDuration() * (star == 2 ? 1.5 : 1.25)),
                        effect.getAmplifier()));
            }
        }
        if (instant) {
            boosted.add(new MobEffectInstance(MobEffects.REGENERATION, star == 2 ? 240 : 160, 0));
        }
        if (!boosted.isEmpty()) {
            PotionUtils.setCustomEffects(stack, boosted);
        }
    }
}
