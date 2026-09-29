package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.core.RefineMath;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.Locale;

/**
 * One refinement attempt on the item in the player's hand.
 *
 * <p>The server owns every step: it re-reads the held stack, the ore, the scrolls and the wallet, so a
 * screen or a command only ever asks for an attempt and is told what happened. Materials are taken only
 * once the attempt is certain to run, and a scroll is consumed only when it actually did something.</p>
 */
public final class RefineService {
    private RefineService() {
    }

    /** What the player chose to spend on this attempt, besides the plain ore. */
    public record Options(boolean enriched, boolean protection, boolean blessing, boolean certificate) {
        public static final Options PLAIN = new Options(false, false, false, false);
    }

    /**
     * The outcome of an attempt. {@code started} is false when nothing was spent because the attempt
     * could not be made at all, and {@code message} is then the reason the player is shown.
     */
    public record Outcome(boolean started, RefineMath.Result result, int level, long cost, String message) {
        static Outcome refused(String message) {
            return new Outcome(false, RefineMath.Result.UNCHANGED, 0, 0, message);
        }
    }

    /** Everything a screen needs to show before the player commits: chance, cost and what is missing. */
    public record Quote(boolean possible, ItemRefine.Category category, int level, int target, double chance,
                        long cost, String ore, String message) {
        static Quote no(String message) {
            return new Quote(false, ItemRefine.Category.NONE, 0, 0, 0, 0, "", message);
        }
    }

    /** What an attempt on the held item would cost and how likely it is, without spending anything. */
    public static Quote quote(ServerPlayer player, RotasData data, Options options) {
        return quote(player, data, options, 0);
    }

    /** As {@link #quote(ServerPlayer, RotasData, Options)}, with the chance the forge timing game earned added. */
    public static Quote quote(ServerPlayer player, RotasData data, Options options, double extraChance) {
        SeasonRules rules = SeasonService.rules(data);
        SeasonRules.RefineRules refine = rules.refine;
        if (refine == null || !refine.enabled) {
            return Quote.no(ThaiText.t("rotasutils.msg.refine.disabled"));
        }
        if (!StationService.near(player, RotasRegistry.REFINE_FORGE.get())) {
            return Quote.no(ThaiText.t("rotasutils.msg.refine.need_forge"));
        }
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemRefine.Category category = ItemRefine.categoryOf(stack);
        if (!category.refinable()) {
            return Quote.no(ThaiText.t("rotasutils.msg.refine.not_refinable"));
        }
        int level = ItemRefine.level(stack);
        if (level >= refine.maxLevel) {
            return Quote.no(ThaiText.t("rotasutils.msg.refine.at_max", refine.maxLevel));
        }
        int target = level + 1;
        double chance = options.certificate() ? 1.0
                : RefineMath.chance(refine.chances, refine.safeLevel, target, bonus(refine, options) + Math.max(0, extraChance));
        String ore = oreId(refine, category, options.enriched());
        return new Quote(true, category, level, target, chance,
                RefineMath.cost(refine.goldPerAttempt, refine.goldGrowth, target), ore, "");
    }

    /**
     * Runs one attempt. Returns what happened; the player is told and the item is written back here, so
     * the caller only has to report the outcome.
     */
    public static Outcome refine(ServerPlayer player, RotasData data, Options options) {
        return refine(player, data, options, 0);
    }

    /** One attempt with {@code extraChance} added to the odds (the forge timing game's bonus). */
    public static Outcome refine(ServerPlayer player, RotasData data, Options options, double extraChance) {
        Quote quote = quote(player, data, options, extraChance);
        if (!quote.possible()) {
            return Outcome.refused(quote.message());
        }
        SeasonRules rules = SeasonService.rules(data);
        SeasonRules.RefineRules refine = rules.refine;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        PlayerProgress progress = data.progress(player.getUUID());

        String lack = missing(player, data, options, quote);
        if (!lack.isEmpty()) {
            return Outcome.refused(lack);
        }
        Item ore = item(quote.ore());
        Item protection = options.protection() ? RotasRegistry.PROTECTION_SCROLL.get() : null;
        Item blessing = options.blessing() ? RotasRegistry.BLESSING_SCROLL.get() : null;
        Item certificate = options.certificate() ? RotasRegistry.CERTIFICATE_SCROLL.get() : null;
        long cost = quote.cost();
        net.minecraft.core.BlockPos forge = StationService.find(player, RotasRegistry.REFINE_FORGE.get());
        // Everything is in hand: take the price, then roll. Nothing above this line has changed the world.
        if (cost > 0) {
            progress.rpg().currency(rules.currency, -cost);
        }
        take(player, ore, 1);
        for (Item scroll : new Item[]{protection, blessing, certificate}) {
            if (scroll != null) {
                take(player, scroll, 1);
            }
        }

        boolean succeeded = options.certificate() || player.getRandom().nextDouble() < quote.chance();
        RefineMath.Result result = RefineMath.resolve(succeeded, refine.failMode(), options.protection());
        int next = RefineMath.nextLevel(quote.level(), result, refine.safeLevel);
        String itemName = stack.getHoverName().getString();
        if (result.destroyed()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            ItemRefine.setLevel(stack, next);
            itemName = stack.getHoverName().getString();
        }
        data.setDirty();
        progress.markDirty();

        String refinedId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        EventService.fire(player, data, result.success()
                ? net.schwarz.rotasutils.event.EventType.REFINE_SUCCESS
                : net.schwarz.rotasutils.event.EventType.REFINE_FAIL, refinedId);
        if (result.success()) {
            // The best refine anyone has reached is a title condition, including the unique first +10.
            TitleService.onRefine(player, data, next);
        }
        announce(player, data, refine, result, next, itemName);
        forgeEffects(player, forge, result, next, refine);
        report(player, result, next, itemName);
        RotasNetwork.syncProgress(player);
        data.audit(player.getGameProfile().getName() + " refine " + quote.category().key() + " "
                + quote.level() + "->" + next + " " + result.name().toLowerCase(Locale.ROOT) + " cost=" + cost);
        return new Outcome(true, result, next, cost, ThaiText.t(result.messageKey(), itemName, next));
    }

    /** What is missing for an attempt (ore, scrolls, gold), or an empty string when everything is in hand. */
    public static String missing(ServerPlayer player, RotasData data, Options options, Quote quote) {
        SeasonRules rules = SeasonService.rules(data);
        Item ore = item(quote.ore());
        if (ore == Items.AIR || count(player, ore) <= 0) {
            return ThaiText.t("rotasutils.msg.refine.need_ore", name(ore, quote.ore()));
        }
        Item protection = options.protection() ? RotasRegistry.PROTECTION_SCROLL.get() : null;
        Item blessing = options.blessing() ? RotasRegistry.BLESSING_SCROLL.get() : null;
        Item certificate = options.certificate() ? RotasRegistry.CERTIFICATE_SCROLL.get() : null;
        for (Item scroll : new Item[]{protection, blessing, certificate}) {
            if (scroll != null && count(player, scroll) <= 0) {
                return ThaiText.t("rotasutils.msg.refine.need_scroll",
                        Component.translatable(scroll.getDescriptionId()).getString());
            }
        }
        long have = data.progress(player.getUUID()).rpg().currency(rules.currency);
        if (quote.cost() > 0 && have < quote.cost()) {
            return ThaiText.t("rotasutils.msg.refine.need_gold", quote.cost(), have);
        }
        return "";
    }

    /** The strike at the anvil: a bright hit when it holds, a red one when it fails, a blast when the item shatters. */
    private static void forgeEffects(ServerPlayer player, net.minecraft.core.BlockPos forge, RefineMath.Result result,
                                     int level, SeasonRules.RefineRules refine) {
        if (forge == null) {
            return;
        }
        net.minecraft.server.level.ServerLevel world = player.serverLevel();
        net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(forge).add(0, 0.9, 0);
        if (result.success()) {
            boolean grand = refine.announceFrom > 0 && level >= refine.announceFrom;
            net.schwarz.rotasutils.entity.RiftFx.send(world, net.schwarz.rotasutils.entity.RiftFx.Kind.IMPACT,
                    grand ? net.schwarz.rotasutils.entity.RiftFx.PRISM : net.schwarz.rotasutils.entity.RiftFx.GOLD, at,
                    grand ? 2.4f : 1.4f, grand ? 18 : 12);
            if (grand) {
                net.schwarz.rotasutils.entity.RiftFx.send(world, net.schwarz.rotasutils.entity.RiftFx.Kind.SHOCKWAVE,
                        net.schwarz.rotasutils.entity.RiftFx.GOLD, at.subtract(0, 0.8, 0), 5f, 22);
            }
            world.playSound(null, forge, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9f, 1.3f);
        } else {
            net.schwarz.rotasutils.entity.RiftFx.send(world, net.schwarz.rotasutils.entity.RiftFx.Kind.IMPACT,
                    net.schwarz.rotasutils.entity.RiftFx.CRIMSON, at, result.destroyed() ? 2.2f : 1.1f, 12);
            world.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.3, 0.2, 0.3, 0.02);
            if (result.destroyed()) {
                net.schwarz.rotasutils.entity.RiftFx.send(world, net.schwarz.rotasutils.entity.RiftFx.Kind.SHOCKWAVE,
                        net.schwarz.rotasutils.entity.RiftFx.CRIMSON, at.subtract(0, 0.8, 0), 3.5f, 16);
                world.playSound(null, forge, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
            }
            world.playSound(null, forge, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 0.7f);
        }
    }

    /** The extra success chance the enriched ore and a blessing scroll are worth together. */
    private static double bonus(SeasonRules.RefineRules refine, Options options) {
        double bonus = 0;
        if (options.enriched()) {
            bonus += refine.enrichedBonus;
        }
        if (options.blessing()) {
            bonus += refine.blessingBonus;
        }
        return bonus;
    }

    private static String oreId(SeasonRules.RefineRules refine, ItemRefine.Category category, boolean enriched) {
        if (category == ItemRefine.Category.ARMOR) {
            return enriched ? refine.armorOreEnriched : refine.armorOre;
        }
        return enriched ? refine.weaponOreEnriched : refine.weaponOre;
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id == null ? "" : id);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }

    private static String name(Item item, String fallback) {
        return item == Items.AIR ? fallback : Component.translatable(item.getDescriptionId()).getString();
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

    /** Takes items out of the main inventory only, so nothing is quietly pulled from an armour slot. */
    private static void take(ServerPlayer player, Item item, int amount) {
        int left = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                return;
            }
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** A high refine is a server event in Ragnarok; the same line is what makes players chase one here. */
    private static void announce(ServerPlayer player, RotasData data, SeasonRules.RefineRules refine,
                                 RefineMath.Result result, int level, String itemName) {
        if (!result.success() || refine.announceFrom <= 0 || level < refine.announceFrom) {
            return;
        }
        player.server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.refine.announce",
                player.getGameProfile().getName(), itemName), false);
    }

    private static void report(ServerPlayer player, RefineMath.Result result, int level, String itemName) {
        player.sendSystemMessage(ThaiText.c(result.messageKey(), itemName, level));
        RotasNetwork.feedback(player, result.success(), ThaiText.t(result.messageKey(), itemName, level));
        player.level().playSound(null, player.blockPosition(),
                result.success() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.8f, result.success() ? 1.4f : 0.8f);
    }
}
