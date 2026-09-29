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

/**
 * Rune inscribing at the Rune Altar, and what inscribed runes do in a fight.
 *
 * <p>A weapon gains a rune slot at each refine threshold in {@link SeasonRules.RuneRules#slotLevels}.
 * Inscribing consumes one rune item and the slot's gold price; a rune already in the slot comes back
 * to the player, and inscribing the rune a slot already holds is refused so nothing is wasted.
 * Runes act only on melee hits from the weapon in the main hand, so a bow or a spell never triggers
 * the sword's runes.</p>
 */
public final class RuneService {
    private RuneService() {
    }

    /** Rune slots the weapon has now; 0 for anything that is not a weapon. */
    public static int slots(SeasonRules.RuneRules rules, ItemStack stack) {
        if (rules == null || !rules.enabled || ItemRefine.categoryOf(stack) != ItemRefine.Category.WEAPON) {
            return 0;
        }
        return RuneType.slots(ItemRefine.level(stack), rules.slotLevels);
    }

    /** Why the held item cannot take runes here at all, or an empty string when it can. */
    public static String refusal(ServerPlayer player, RotasData data) {
        SeasonRules.RuneRules rules = SeasonService.rules(data).runes;
        if (rules == null || !rules.enabled) {
            return ThaiText.t("rotasutils.msg.rune.disabled");
        }
        if (!StationService.near(player, RotasRegistry.RUNE_ALTAR.get())) {
            return ThaiText.t("rotasutils.msg.rune.need_altar");
        }
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

    /** Inscribes {@code rune} into {@code slot} of the held weapon. True when it was written. */
    public static boolean inscribe(ServerPlayer player, RotasData data, int slot, String runeId) {
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
        if (count(player, runeItem) <= 0) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.rune.need_rune",
                    runeItem.getDescription().getString()));
            return false;
        }
        if (ItemRunes.get(weapon, slot) == rune) {
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
        take(player, runeItem);
        RuneType replaced = ItemRunes.get(weapon, slot);
        ItemRunes.set(weapon, slot, rune);
        if (replaced != null) {
            ItemStack back = new ItemStack(RotasRegistry.RUNES.get(replaced).get());
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        data.setDirty();
        progress.markDirty();

        String weaponName = weapon.getHoverName().getString();
        String runeName = runeItem.getDescription().getString();
        String message = ThaiText.t("rotasutils.msg.rune.inscribed", runeName, weaponName, slot + 1);
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.rune.inscribed", runeName, weaponName, slot + 1));
        RotasNetwork.feedback(player, true, message);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(),
                40, 0.5, 0.6, 0.5, 0.6);
        net.minecraft.core.BlockPos altar = StationService.find(player, RotasRegistry.RUNE_ALTAR.get());
        if (altar != null) {
            net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(altar).add(0, 1.0, 0);
            net.schwarz.rotasutils.entity.RiftFx.send(player.serverLevel(), net.schwarz.rotasutils.entity.RiftFx.Kind.IMPACT,
                    colourOf(rune), at, 1.5f, 14);
            net.schwarz.rotasutils.entity.RiftFx.send(player.serverLevel(), net.schwarz.rotasutils.entity.RiftFx.Kind.BURST,
                    colourOf(rune), at, 1.0f, 18);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 1.0f, 0.9f);
        RotasNetwork.syncProgress(player);
        data.audit(player.getGameProfile().getName() + " rune " + rune.id() + " slot=" + (slot + 1)
                + (replaced == null ? "" : " replaced=" + replaced.id()) + " cost=" + cost);
        return true;
    }

    // Combat ---------------------------------------------------------------------------------------

    /** The runes that count on the weapon that struck this hit, or an empty map for anything but melee. */
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

    /** Fury: sometimes a hit lands for more. Applied with the other outgoing multipliers. */
    public static double damageMultiplier(ServerPlayer attacker, DamageSource source, LivingEntity victim) {
        int fury = striking(attacker, source).getOrDefault(RuneType.FURY, 0);
        if (fury <= 0 || attacker.getRandom().nextDouble() >= RuneType.FURY.strength(fury)) {
            return 1.0;
        }
        attacker.serverLevel().sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(),
                12, 0.3, 0.3, 0.3, 0.3);
        proc(attacker, victim, RuneType.FURY);
        return RuneType.FURY_MULTIPLIER;
    }

    /** Fire, frost, venom and lifesteal once a melee hit has really landed. */
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
                        proc(attacker, victim, rune);
                    }
                }
                case FROST -> {
                    if (roll(attacker, strength)) {
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, rune.seconds() * 20, 1),
                                attacker);
                        proc(attacker, victim, rune);
                        attacker.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5),
                                victim.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                    }
                }
                case VENOM -> {
                    if (roll(attacker, strength)) {
                        victim.addEffect(new MobEffectInstance(MobEffects.POISON, rune.seconds() * 20, 0), attacker);
                        proc(attacker, victim, rune);
                    }
                }
                default -> {
                    // Fury acts on the damage itself, in damageMultiplier.
                }
            }
        }
    }

    /** The flash colour of a rune: fire and lifesteal red, fury gold, frost white, venom the rift's violet. */
    private static int colourOf(RuneType rune) {
        return switch (rune) {
            case FIRE, LIFESTEAL -> net.schwarz.rotasutils.entity.RiftFx.CRIMSON;
            case FURY -> net.schwarz.rotasutils.entity.RiftFx.GOLD;
            case FROST -> net.schwarz.rotasutils.entity.RiftFx.WHITE;
            case VENOM -> net.schwarz.rotasutils.entity.RiftFx.VIOLET;
        };
    }

    /** A small anime-style flash on the victim when a rune fires. */
    private static void proc(ServerPlayer attacker, LivingEntity victim, RuneType rune) {
        net.schwarz.rotasutils.entity.RiftFx.send(attacker.serverLevel(), net.schwarz.rotasutils.entity.RiftFx.Kind.IMPACT,
                colourOf(rune), victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0.7f, 9);
    }

    private static boolean roll(ServerPlayer attacker, double chance) {
        return chance > 0 && attacker.getRandom().nextDouble() < chance;
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Main inventory only, like the refine bench, so nothing is pulled out of an armour slot. */
    private static void take(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
    }

    /** How many of this rune the player carries, for the altar screen. */
    public static int carried(ServerPlayer player, RuneType rune) {
        return count(player, RotasRegistry.RUNES.get(rune).get());
    }
}
