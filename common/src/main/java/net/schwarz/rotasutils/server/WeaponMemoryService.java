package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.WeaponMemoryMath;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.item.WeaponMemory;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * Weapon memory (ความทรงจำอาวุธ): the weapon that made a kill counts it, and grows from it.
 *
 * <p>Only the weapon that actually struck counts - a melee swing, or a projectile fired by the player
 * holding it - so a sword kept in the off hand while a pet does the work learns nothing. The damage it
 * adds is read at hit time from the item and the season rules, the same way refinement is.</p>
 */
public final class WeaponMemoryService {
    private WeaponMemoryService() {
    }

    private static SeasonRules.WeaponMemoryRules rules(RotasData data) {
        return SeasonService.rules(data).weaponMemory;
    }

    private static boolean struckWithHeld(ServerPlayer player, DamageSource source) {
        if (source.getDirectEntity() == player) {
            return true;
        }
        return source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() == player;
    }

    /** One kill for the weapon in the killer's hand; announces a rank the moment it is reached. */
    public static void onKill(ServerPlayer killer, RotasData data, LivingEntity victim, String entityId,
                              boolean boss, DamageSource source) {
        SeasonRules.WeaponMemoryRules rules = rules(data);
        if (rules == null || !rules.enabled || !BestiaryService.recordable(victim) || !struckWithHeld(killer, source)) {
            return;
        }
        ItemStack weapon = killer.getMainHandItem();
        if (ItemRefine.categoryOf(weapon) != ItemRefine.Category.WEAPON) {
            return;
        }
        boolean nemesis = NemesisService.idOf(victim) > 0;
        WeaponMemoryMath.Result result = WeaponMemory.record(weapon, entityId, boss, nemesis, rules.milestones,
                SeasonRules.WeaponMemoryRules.MAX_KINDS);
        if (!result.rankedUp()) {
            return;
        }
        String rank = rankName(result.after());
        String name = weapon.getHoverName().getString();
        killer.sendSystemMessage(ThaiText.c("rotasutils.msg.memory.rank", name, rank, result.kills())
                .withStyle(ChatFormatting.AQUA));
        killer.level().playSound(null, killer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        if (rules.announceFrom > 0 && result.after() >= rules.announceFrom) {
            killer.server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.memory.announce",
                    killer.getGameProfile().getName(), name, rank).withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** The title of a memory rank; ranks past the last named one keep the last name. */
    public static String rankName(int rank) {
        return ThaiText.t("rotasutils.memory.rank." + Math.max(1, Math.min(5, rank)));
    }

    /** What the striking weapon's memory multiplies this hit by, against a non-player victim. */
    public static double damageMultiplier(SeasonRules rules, ServerPlayer attacker, DamageSource source, LivingEntity victim) {
        SeasonRules.WeaponMemoryRules memory = rules == null ? null : rules.weaponMemory;
        if (memory == null || !memory.enabled || !struckWithHeld(attacker, source)) {
            return 1.0;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (ItemRefine.categoryOf(weapon) != ItemRefine.Category.WEAPON) {
            return 1.0;
        }
        CompoundTag tag = WeaponMemory.read(weapon);
        int rank = WeaponMemoryMath.rank(tag.getLong(WeaponMemoryMath.KILLS), memory.milestones);
        if (rank <= 0) {
            return 1.0;
        }
        String favored = WeaponMemoryMath.favored(tag);
        boolean against = !favored.isEmpty()
                && favored.equals(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType())));
        return WeaponMemoryMath.damageMultiplier(rank, memory.damagePerRank, against, memory.favoredBonus,
                memory.favoredFromRank);
    }
}
