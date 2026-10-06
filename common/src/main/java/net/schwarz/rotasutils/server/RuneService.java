package net.schwarz.rotasutils.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.RuneType;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.item.ItemRunes;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.Map;

public final class RuneService {
    private RuneService() {
    }

    public static int slots(SeasonRules.RuneRules rules, ItemStack stack) {
        if (rules == null || !rules.enabled || ItemRefine.categoryOf(stack) != ItemRefine.Category.WEAPON) {
            return 0;
        }
        return RuneType.slots(ItemRefine.level(stack), rules.slotLevels);
    }

    public static String stationRefusal(ServerPlayer player, RotasData data) {
        SeasonRules.RuneRules rules = SeasonService.rules(data).runes;
        if (rules == null || !rules.enabled) {
            return ThaiText.t("rotasutils.msg.rune.disabled");
        }
        if (!StationService.near(player, RotasRegistry.RUNE_ALTAR.get())) {
            return ThaiText.t("rotasutils.msg.rune.need_altar");
        }
        return "";
    }

    public static String refusal(ServerPlayer player, RotasData data) {
        String station = stationRefusal(player, data);
        if (!station.isEmpty()) {
            return station;
        }
        SeasonRules.RuneRules rules = SeasonService.rules(data).runes;
        ItemStack held = player.getMainHandItem();
        if (ItemRefine.categoryOf(held) != ItemRefine.Category.WEAPON) {
            return ThaiText.t("rotasutils.msg.rune.not_weapon");
        }
        if (slots(rules, held) <= 0) {
            int first = rules.slotLevels.length == 0 ? 0 : rules.slotLevels[0];
            return ThaiText.t("rotasutils.msg.rune.no_slots", first);
        }
        return "";
    }

    public static boolean inscribe(ServerPlayer player, RotasData data, int slot, String runeId, int tier) {
        String refusal = refusal(player, data);
        if (!refusal.isEmpty()) {
            RotasNetwork.feedback(player, false, refusal);
            return false;
        }
        SeasonRules rules = SeasonService.rules(data);
        ItemStack weapon = player.getItemInHand(InteractionHand.MAIN_HAND);
        RuneType rune = RuneType.byId(runeId);
        if (rune == null || slot < 0 || slot >= slots(rules.runes, weapon)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.bad_slot"));
            return false;
        }
        Item runeItem = RotasRegistry.RUNES.get(rune).get();
        tier = ItemRunes.clampTier(tier);
        if (count(player, runeItem, tier) <= 0) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.need_rune",
                    runeItem.getDescription().getString() + " " + ItemRunes.numeral(tier)));
            return false;
        }
        if (ItemRunes.get(weapon, slot) == rune && ItemRunes.tier(weapon, slot) == tier) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.same", runeItem.getDescription().getString()));
            return false;
        }
        long cost = rules.runes.goldPerSlot[slot];
        PlayerProgress progress = data.progress(player.getUUID());
        if (cost > 0 && progress.rpg().currency(rules.currency) < cost) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.need_gold", cost,
                    progress.rpg().currency(rules.currency)));
            return false;
        }

        if (cost > 0) {
            progress.rpg().currency(rules.currency, -cost);
        }
        take(player, runeItem, tier, 1);
        RuneType replaced = ItemRunes.get(weapon, slot);
        int replacedTier = ItemRunes.tier(weapon, slot);
        ItemRunes.set(weapon, slot, rune, tier);
        if (replaced != null) {
            ItemStack back = ItemRunes.stack(replaced, replacedTier, 1);
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        data.setDirty();
        progress.markDirty();

        String weaponName = weapon.getHoverName().getString();
        String runeName = runeItem.getDescription().getString() + " " + ItemRunes.numeral(tier);
        String message = ThaiText.t("rotasutils.msg.rune.inscribed", runeName, weaponName, slot + 1);
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.rune.inscribed", runeName, weaponName, slot + 1));
        RotasNetwork.feedback(player, true, message);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(),
                40, 0.5, 0.6, 0.5, 0.6);
        net.minecraft.core.BlockPos altar = StationService.find(player, RotasRegistry.RUNE_ALTAR.get());
        if (altar != null) {
            net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(altar).add(0, 0.8, 0);
            net.schwarz.rotasutils.entity.RiftFx.send(player.serverLevel(), net.schwarz.rotasutils.entity.RiftFx.Kind.ALTAR_SUCCESS,
                    colourOf(rune), at, 0.9f + 0.25f * tier, 52);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 1.0f, 0.9f);
        RotasNetwork.syncProgress(player);
        data.audit(player.getGameProfile().getName() + " rune " + rune.id() + " t" + tier + " slot=" + (slot + 1)
                + (replaced == null ? "" : " replaced=" + replaced.id()) + " cost=" + cost);
        return true;
    }

    public static boolean fuse(ServerPlayer player, RotasData data, String runeId, int tier) {
        String refusal = stationRefusal(player, data);
        if (!refusal.isEmpty()) {
            RotasNetwork.feedback(player, false, refusal);
            return false;
        }
        RuneType rune = RuneType.byId(runeId);
        if (rune == null || tier < 1 || tier >= ItemRunes.MAX_TIER) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.max_tier"));
            return false;
        }
        Item runeItem = RotasRegistry.RUNES.get(rune).get();
        if (count(player, runeItem, tier) < RuneType.FUSE_COUNT) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.need_fuse", RuneType.FUSE_COUNT,
                    runeItem.getDescription().getString() + " " + ItemRunes.numeral(tier)));
            return false;
        }
        SeasonRules rules = SeasonService.rules(data);
        long cost = RuneType.fuseCost(tier);
        PlayerProgress progress = data.progress(player.getUUID());
        if (cost > 0 && progress.rpg().currency(rules.currency) < cost) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.need_gold", cost,
                    progress.rpg().currency(rules.currency)));
            return false;
        }
        if (cost > 0) {
            progress.rpg().currency(rules.currency, -cost);
        }
        take(player, runeItem, tier, RuneType.FUSE_COUNT);
        ItemStack fused = ItemRunes.stack(rune, tier + 1, 1);
        if (!player.getInventory().add(fused)) {
            player.drop(fused, false);
        }
        data.setDirty();
        progress.markDirty();
        String name = runeItem.getDescription().getString() + " " + ItemRunes.numeral(tier + 1);
        String message = ThaiText.t("rotasutils.msg.rune.fused", name);
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.rune.fused", name));
        RotasNetwork.feedback(player, true, message);
        net.minecraft.core.BlockPos altar = StationService.find(player, RotasRegistry.RUNE_ALTAR.get());
        if (altar != null) {
            net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(altar).add(0, 0.8, 0);
            net.schwarz.rotasutils.entity.RiftFx.send(player.serverLevel(), net.schwarz.rotasutils.entity.RiftFx.Kind.ALTAR_SUCCESS,
                    tier + 1 >= ItemRunes.MAX_TIER ? net.schwarz.rotasutils.entity.RiftFx.PRISM : colourOf(rune), at,
                    1.2f + 0.4f * tier, 60);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 1.0f, 1.3f);
        RotasNetwork.syncProgress(player);
        data.audit(player.getGameProfile().getName() + " rune fuse " + rune.id() + " t" + tier + "->t" + (tier + 1) + " cost=" + cost);
        return true;
    }

private static Map<RuneType, Integer> striking(ServerPlayer attacker, DamageSource source) {
        if (source.getDirectEntity() != attacker) {
            return Map.of();
        }
        RotasData data = RotasData.instance();
        if (data == null) {
            return Map.of();
        }
        ItemStack weapon = attacker.getMainHandItem();
        int slots = slots(SeasonService.rules(data).runes, weapon);
        return slots <= 0 ? Map.of() : ItemRunes.active(weapon, slots);
    }

    public static double damageMultiplier(ServerPlayer attacker, DamageSource source, LivingEntity victim) {
        int fury = striking(attacker, source).getOrDefault(RuneType.FURY, 0);
        if (fury <= 0 || attacker.getRandom().nextDouble() >= RuneType.FURY.strength(fury)) {
            return 1.0;
        }
        attacker.serverLevel().sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(),
                12, 0.3, 0.3, 0.3, 0.3);
        return RuneType.FURY_MULTIPLIER;
    }

    public static void afterHurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        Map<RuneType, Integer> runes = striking(attacker, source);
        if (runes.isEmpty()) {
            return;
        }
        for (Map.Entry<RuneType, Integer> entry : runes.entrySet()) {
            RuneType rune = entry.getKey();
            double strength = rune.strength(entry.getValue());
            switch (rune) {
                case LIFESTEAL -> {
                    float heal = (float) (amount * strength);
                    if (heal > 0 && attacker.getHealth() < attacker.getMaxHealth()) {
                        attacker.heal(heal);
                    }
                }
                case FIRE -> {
                    if (roll(attacker, strength)) {
                        victim.setSecondsOnFire(rune.seconds());
                    }
                }
                case FROST -> {
                    if (roll(attacker, strength)) {
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, rune.seconds() * 20, 1),
                                attacker);
                        attacker.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5),
                                victim.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                    }
                }
                case VENOM -> {
                    if (roll(attacker, strength)) {
                        victim.addEffect(new MobEffectInstance(MobEffects.POISON, rune.seconds() * 20, 0), attacker);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static int colourOf(RuneType rune) {
        return switch (rune) {
            case FIRE, LIFESTEAL -> net.schwarz.rotasutils.entity.RiftFx.CRIMSON;
            case FURY -> net.schwarz.rotasutils.entity.RiftFx.GOLD;
            case FROST -> net.schwarz.rotasutils.entity.RiftFx.WHITE;
            case VENOM -> net.schwarz.rotasutils.entity.RiftFx.VIOLET;
        };
    }

    private static boolean roll(ServerPlayer attacker, double chance) {
        return chance > 0 && attacker.getRandom().nextDouble() < chance;
    }

    private static int count(ServerPlayer player, Item item, int tier) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item) && ItemRunes.tierOf(stack) == tier) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(ServerPlayer player, Item item, int tier, int amount) {
        int left = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                return;
            }
            if (stack.is(item) && ItemRunes.tierOf(stack) == tier) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    public static int carried(ServerPlayer player, RuneType rune, int tier) {
        return count(player, RotasRegistry.RUNES.get(rune).get(), tier);
    }
}
