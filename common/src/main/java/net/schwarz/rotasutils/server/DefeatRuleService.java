package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.schwarz.rotasutils.core.DefeatRule;
import net.schwarz.rotasutils.data.RotasData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DefeatRuleService {
    private DefeatRuleService() {
    }

    private static final Map<UUID, Long> HINTED = new HashMap<>();

    public static double multiplier(LivingEntity victim, DamageSource source) {
        DefeatRule rule = rule(victim);
        if (rule == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { return 1.0; }
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        ItemStack held = attacker == null ? ItemStack.EMPTY : attacker.getMainHandItem();
        int level = attacker instanceof ServerPlayer player && victim.getServer() != null
                ? RotasData.get(victim.getServer()).progress(player.getUUID()).level() : -1;
        return rule.multiplier(attack(source, attacker), id -> matchesItem(held, id), id -> matchesDamage(source, id), level);
    }

    public static boolean blocks(LivingEntity victim, DamageSource source) {
        double multiplier = multiplier(victim, source);
        if (multiplier < 1.0 && source.getEntity() instanceof ServerPlayer player) {
            long now = victim.level().getGameTime();
            Long last = HINTED.get(player.getUUID());
            if (last == null || now - last >= 40 || now < last) {
                HINTED.put(player.getUUID(), now);
                DefeatRule rule = rule(victim);
                String text = rule.immuneTo(id -> matchesDamage(source, id)) ? "Immune to that damage." : rule.describe();
                player.displayClientMessage(Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
        }
        return multiplier <= 0;
    }

    private static DefeatRule rule(LivingEntity victim) {
        if (victim.level().isClientSide || !(victim instanceof Mob mob)) { return null; }
        var service = MonsterService.get(mob);
        var state = service == null ? null : service.peek(mob);
        if (state == null) { return null; }
        var profile = service.kernel().content().monsters().profiles().get(state.profile());
        return profile == null || !profile.defeat().active() ? null : profile.defeat();
    }

    static DefeatRule.Attack attack(DamageSource source, LivingEntity attacker) {
        if (attacker == null) { return null; }
        if (CombatStats.magic(source)) { return DefeatRule.Attack.MAGIC; }
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() != attacker) { return DefeatRule.Attack.RANGED; }
        return DefeatRule.Attack.MELEE;
    }

    private static boolean matchesItem(ItemStack held, String id) {
        if (held.isEmpty()) { return false; }
        if (id.startsWith("#")) { return held.is(TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1)))); }
        return BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(id);
    }

    private static boolean matchesDamage(DamageSource source, String id) {
        if (id.startsWith("#")) { return source.is(TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(id.substring(1)))); }
        return source.typeHolder().unwrapKey().map(key -> key.location().toString().equals(id)).orElse(false);
    }
}
