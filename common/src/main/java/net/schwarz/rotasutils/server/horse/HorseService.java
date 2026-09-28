package net.schwarz.rotasutils.server.horse;

import dev.architectury.event.EventResult;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.core.HorseBreeding;
import net.schwarz.rotasutils.core.HorseGacha;
import net.schwarz.rotasutils.core.HorsePricing;
import net.schwarz.rotasutils.core.HorseTrait;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.SeasonService;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The horse system on top of SWEM: the draw, the stable, summoning with the whistle, knock-outs instead of
 * deaths, NPC sales priced on current levels, and a fee-free player market.
 *
 * <p>Horses live as saved entity snapshots in {@link StableData}. Only one generation of a horse may exist in
 * the world; summoning, storing, a knock-out or a sale moves the generation on, and an older copy is refused
 * when its chunk loads. Snapshots refresh every minute and whenever a horse leaves the world, so training
 * done while riding is kept.</p>
 */
public final class HorseService {
    public record Result(boolean ok, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result no(String message) { return new Result(false, message); }
    }

    private static final int SNAPSHOT_SECONDS = 60;
    private static int tickSeconds;

    private HorseService() {
    }

    // State ----------------------------------------------------------------------------------------

    static SeasonRules.HorseRules rules(RotasData data) {
        return SeasonService.rules(data).horse;
    }

    /** Null when the horse system can be used, otherwise why not. */
    public static String unavailable(RotasData data) {
        if (!SwemCompat.available()) return ThaiText.t("rotasutils.msg.horse.no_swem");
        if (!SeasonService.active(data) || !rules(data).enabled) return ThaiText.t("rotasutils.msg.horse.disabled");
        return null;
    }

    public static int slots(RotasData data, StableData stables, ServerPlayer player) {
        SeasonRules.HorseRules rules = rules(data);
        int perk = SeasonService.perk(data, data.progress(player.getUUID())).stableSlots;
        long total = (long) rules.baseStableSlots + stables.stable(player.getUUID()).purchasedSlots + Math.max(0, perk);
        return (int) Math.min(rules.maxStableSlots, total);
    }

    private static String currency(RotasData data) {
        return SeasonService.rules(data).currency;
    }

    private static boolean pay(RotasData data, ServerPlayer player, long cost) {
        if (cost <= 0) return true;
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.rpg().currency(currency(data)) < cost) return false;
        progress.rpg().currency(currency(data), -cost);
        data.setDirty();
        return true;
    }

    private static String fundsMessage(RotasData data, ServerPlayer player, long cost) {
        return ThaiText.t("rotasutils.msg.horse.funds", cost, data.progress(player.getUUID()).rpg().currency(currency(data)));
    }

    public static String rarityName(HorseGacha.Rarity rarity) {
        if (rarity == null) return ThaiText.t("rotasutils.msg.horse.no_rarity");
        return ThaiText.label("horse_rarity", rarity, rarity.name());
    }

    static String displayName(StableData.Horse horse) {
        return horse.name.isBlank() ? ThaiText.t("rotasutils.msg.horse.unnamed", horse.id.substring(0, 4)) : horse.name;
    }

    public static long npcPrice(RotasData data, StableData.Horse horse) {
        int[] levels = horse.levels();
        SeasonRules.HorseRules rules = rules(data);
        long base = HorsePricing.npcPrice(rules, horse.origin == StableData.Origin.GACHA ? horse.rarity : null,
                rules.bredHorsesSellToNpc, levels[0], levels[1], levels[2], levels[3], horse.secretCoat, horse.rareCoat);
        if (base <= 0) return 0;
        // A proven bloodline and a trade trait are what make a bred horse worth more than a drawn one.
        double priced = (base + Math.min(10, horse.lineage) * rules.sellPerLineage)
                * HorseBreeding.priceMultiplier(rules, horse.traits);
        return Math.max(0, Math.round(Math.min(priced, 9e15)));
    }

    static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Gives legacy horses their traits and breeding count once, the first time the rules see them. */
    static void settle(SeasonRules.HorseRules rules, StableData stables, StableData.Horse horse) {
        boolean changed = false;
        if (!horse.traitsRolled) {
            if (horse.origin == StableData.Origin.GACHA && horse.rarity != null && horse.traits.isEmpty()) {
                horse.traits = new ArrayList<>(HorseBreeding.drawTraits(rules, horse.rarity, new java.util.SplittableRandom()));
            }
            horse.traitsRolled = true;
            changed = true;
        }
        if (horse.breedsLeft < 0) {
            horse.breedsLeft = HorseBreeding.breedings(rules, horse.traits);
            changed = true;
        }
        if (changed) stables.setDirty();
    }

    private static Entity find(MinecraftServer server, UUID uuid) {
        if (uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void capture(StableData.Horse horse, Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putString("id", SwemCompat.HORSE.toString());
        if (entity instanceof LivingEntity living && tag.getFloat("Health") <= 0) {
            tag.putFloat("Health", living.getMaxHealth());
        }
        tag.remove("DeathTime");
        tag.remove("HurtTime");
        horse.snapshot = tag;
    }

    /** Takes a horse out of the world into its snapshot and moves its generation on. */
    private static void retire(StableData stables, StableData.Horse horse, MinecraftServer server) {
        Entity entity = find(server, horse.active);
        if (entity != null && entity.isAlive()) {
            capture(horse, entity);
            entity.ejectPassengers();
            entity.discard();
        }
        horse.active = null;
        horse.generation++;
        stables.setDirty();
    }

    // Draw -----------------------------------------------------------------------------------------

    public static Result pull(ServerPlayer player, int count, ListTag results) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        if (count != 1 && count != 10) return Result.no(ThaiText.t("rotasutils.msg.horse.pull_count"));
        StableData stables = StableData.get(player.server);
        SeasonRules.HorseRules rules = rules(data);
        int used = stables.owned(player.getUUID()).size();
        int slots = slots(data, stables, player);
        if (used + count > slots) return Result.no(ThaiText.t("rotasutils.msg.horse.slots_full", used, slots));
        long cost = count == 10 ? rules.tenPullCost : rules.pullCost;
        if (!pay(data, player, cost)) return Result.no(fundsMessage(data, player, cost));
        StableData.Stable stable = stables.stable(player.getUUID());
        java.util.random.RandomGenerator random = new java.util.SplittableRandom(player.getRandom().nextLong());
        int made = 0;
        HorseGacha.Rarity best = HorseGacha.Rarity.COMMON;
        for (int i = 0; i < count; i++) {
            HorseGacha.Pull pull = HorseGacha.pull(random, stable.pity(), rules);
            StableData.Horse horse = create(player, data, stables, pull, random);
            if (horse == null) {
                break;
            }
            stable.pity(pull.pity());
            stable.totalPulls++;
            made++;
            if (pull.rarity().ordinal() > best.ordinal()) best = pull.rarity();
            CompoundTag line = describe(data, horse, player.server);
            line.putBoolean("guaranteed", pull.guaranteed());
            results.add(line);
        }
        if (made < count) {
            // Refund the undelivered part; the rest of the draw stands.
            long refund = count == 10 ? cost * (count - made) / count : cost;
            data.progress(player.getUUID()).rpg().currency(currency(data), refund);
            if (made == 0) return Result.no(ThaiText.t("rotasutils.msg.horse.create_failed"));
        }
        stables.setDirty();
        data.setDirty();
        player.level().playSound(null, player.blockPosition(),
                best.ordinal() >= HorseGacha.Rarity.EPIC.ordinal() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        if (best == HorseGacha.Rarity.LEGENDARY) {
            player.server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.horse.legendary_broadcast",
                    player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD), false);
        }
        data.audit(player.getGameProfile().getName() + " horse draw x" + made + " cost=" + cost);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.pulled", made, rarityName(best)));
    }

    /** Builds a real SWEM horse off-world, applies the draw and keeps only its snapshot. */
    private static StableData.Horse create(ServerPlayer player, RotasData data, StableData stables, HorseGacha.Pull pull,
                                           java.util.random.RandomGenerator random) {
        EntityType<?> type = SwemCompat.horseType();
        Entity entity = type == null ? null : type.create(player.serverLevel());
        if (entity == null) return null;
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        SwemCompat.tame(entity, player);
        int[] levels = {pull.speed(), pull.jump(), pull.health(), pull.affinity()};
        SwemCompat.setLevels(entity, levels);
        CoatChoice coat = pickCoat(rules(data), pull.coat(), random);
        if (!coat.id().isEmpty()) SwemCompat.setCoat(entity, coat.id());
        StableData.Horse horse = new StableData.Horse();
        horse.id = stables.newId();
        horse.owner = player.getUUID();
        horse.origin = StableData.Origin.GACHA;
        horse.rarity = pull.rarity();
        horse.startLevels = levels;
        horse.coat = SwemCompat.coat(entity);
        horse.secretCoat = coat.secret();
        horse.rareCoat = coat.rare();
        horse.created = System.currentTimeMillis() / 1000L;
        horse.traits = new ArrayList<>(HorseBreeding.drawTraits(rules(data), pull.rarity(), random));
        horse.traitsRolled = true;
        horse.breedsLeft = HorseBreeding.breedings(rules(data), horse.traits);
        capture(horse, entity);
        entity.discard();
        stables.put(horse);
        return horse;
    }

    private record CoatChoice(String id, boolean secret, boolean rare) {
    }

    private static CoatChoice pickCoat(SeasonRules.HorseRules rules, HorseGacha.CoatPool pool, java.util.random.RandomGenerator random) {
        List<SwemCompat.Coat> coats = SwemCompat.coats().stream().filter(coat -> !coat.blacklisted()).toList();
        if (coats.isEmpty()) return new CoatChoice("", false, false);
        Set<String> secret = new HashSet<>(List.of(rules.secretCoats));
        if (secret.isEmpty()) coats.stream().filter(coat -> !coat.breedable()).forEach(coat -> secret.add(coat.id()));
        Set<String> rare = new HashSet<>(List.of(rules.rareCoats));
        List<String> candidates = new ArrayList<>();
        for (SwemCompat.Coat coat : coats) {
            boolean isSecret = secret.contains(coat.id());
            boolean isRare = rare.contains(coat.id());
            switch (pool) {
                case SECRET -> { if (isSecret) candidates.add(coat.id()); }
                case RARE -> { if (isRare) candidates.add(coat.id()); }
                default -> { if (!isSecret && !isRare) candidates.add(coat.id()); }
            }
        }
        if (candidates.isEmpty()) {
            // No coat of that pool exists on this server: fall back to an ordinary coat.
            coats.stream().filter(coat -> !secret.contains(coat.id()) && !rare.contains(coat.id())).forEach(coat -> candidates.add(coat.id()));
            if (candidates.isEmpty()) coats.forEach(coat -> candidates.add(coat.id()));
            String id = candidates.get(random.nextInt(candidates.size()));
            return new CoatChoice(id, secret.contains(id), rare.contains(id));
        }
        String id = candidates.get(random.nextInt(candidates.size()));
        return new CoatChoice(id, secret.contains(id), rare.contains(id));
    }

    // Stable ---------------------------------------------------------------------------------------

    private static StableData.Horse ownedHorse(StableData stables, ServerPlayer player, String id) {
        StableData.Horse horse = stables.horse(id);
        return horse != null && horse.owner.equals(player.getUUID()) ? horse : null;
    }

    public static Result summon(ServerPlayer player, String id) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        if (horse.listedPrice > 0) return Result.no(ThaiText.t("rotasutils.msg.horse.listed_locked"));
        if (horse.unborn(nowSeconds())) return Result.no(ThaiText.t("rotasutils.msg.horse.unborn"));
        long now = System.currentTimeMillis();
        if (horse.recoverUntil * 1000L > now) {
            return Result.no(ThaiText.t("rotasutils.msg.horse.recovering", (horse.recoverUntil * 1000L - now) / 1000L + 1));
        }
        StableData.Stable stable = stables.stable(player.getUUID());
        long cooldown = rules(data).summonCooldownSeconds * 1000L;
        if (now - stable.lastSummonMillis < cooldown) {
            return Result.no(ThaiText.t("rotasutils.msg.horse.cooldown", (cooldown - (now - stable.lastSummonMillis)) / 1000L + 1));
        }
        // One horse out at a time: any other horse of this player returns to the stable first.
        for (StableData.Horse other : stables.owned(player.getUUID())) {
            if (other.active != null) retire(stables, other, player.server);
        }
        CompoundTag tag = horse.snapshot.copy();
        tag.remove("UUID");
        tag.remove("Passengers");
        tag.remove("Leash");
        tag.putString("id", SwemCompat.HORSE.toString());
        ServerLevel level = player.serverLevel();
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> {
            loaded.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
            return loaded;
        });
        if (entity == null || !SwemCompat.isHorse(entity)) return Result.no(ThaiText.t("rotasutils.msg.horse.summon_failed"));
        entity.setUUID(UUID.randomUUID());
        for (String old : new ArrayList<>(entity.getTags())) {
            if (old.startsWith("rotas_horse:")) entity.removeTag(old);
        }
        horse.generation++;
        entity.addTag(horse.tag());
        if (!horse.name.isBlank()) entity.setCustomName(Component.literal(horse.name));
        if (!level.addFreshEntity(entity)) return Result.no(ThaiText.t("rotasutils.msg.horse.summon_failed"));
        horse.active = entity.getUUID();
        stable.lastSummonMillis = now;
        stables.setDirty();
        level.playSound(null, player.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.summoned", displayName(horse)));
    }

    public static Result store(ServerPlayer player, String id) {
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        if (horse.active == null) return Result.no(ThaiText.t("rotasutils.msg.horse.already_stored"));
        retire(stables, horse, player.server);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.stored", displayName(horse)));
    }

    public static Result rename(ServerPlayer player, String id, String name) {
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        String clean = name == null ? "" : name.strip();
        if (clean.length() > 24 || clean.chars().anyMatch(Character::isISOControl)) {
            return Result.no(ThaiText.t("rotasutils.msg.horse.name_invalid"));
        }
        horse.name = clean;
        Entity entity = find(player.server, horse.active);
        if (entity != null) entity.setCustomName(clean.isEmpty() ? null : Component.literal(clean));
        stables.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.horse.renamed", displayName(horse)));
    }

    public static Result release(ServerPlayer player, String id) {
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        if (horse.active != null) retire(stables, horse, player.server);
        stables.remove(horse.id);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.released", displayName(horse)));
    }

    public static Result buySlot(ServerPlayer player) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        StableData stables = StableData.get(player.server);
        SeasonRules.HorseRules rules = rules(data);
        StableData.Stable stable = stables.stable(player.getUUID());
        if (slots(data, stables, player) >= rules.maxStableSlots) return Result.no(ThaiText.t("rotasutils.msg.horse.slot_max"));
        long cost = SeasonMath.slotCost(stable.purchasedSlots, rules.slotBaseCost, rules.slotCostGrowth);
        if (!pay(data, player, cost)) return Result.no(fundsMessage(data, player, cost));
        stable.purchasedSlots++;
        stables.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.horse.slot_bought", slots(data, stables, player), cost));
    }

    /** Puts a horse the player already owns in the world (wild tame or foal) into the stable. */
    public static Result adopt(ServerPlayer player, Entity entity) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        if (!SwemCompat.isHorse(entity) || !entity.isAlive()) return Result.no(ThaiText.t("rotasutils.msg.horse.not_horse"));
        StableData stables = StableData.get(player.server);
        if (stables.byActive(entity.getUUID()) != null || entity.getTags().stream().anyMatch(tag -> tag.startsWith("rotas_horse:"))) {
            return Result.no(ThaiText.t("rotasutils.msg.horse.adopt_tracked"));
        }
        if (!SwemCompat.ownedBy(entity, player)) return Result.no(ThaiText.t("rotasutils.msg.horse.adopt_owner"));
        int used = stables.owned(player.getUUID()).size();
        int slots = slots(data, stables, player);
        if (used >= slots) return Result.no(ThaiText.t("rotasutils.msg.horse.slots_full", used, slots));
        StableData.Horse horse = new StableData.Horse();
        horse.id = stables.newId();
        horse.owner = player.getUUID();
        horse.origin = SwemCompat.bred(entity) ? StableData.Origin.BRED : StableData.Origin.WILD;
        horse.startLevels = SwemCompat.levels(entity);
        horse.coat = SwemCompat.coat(entity);
        horse.name = entity.hasCustomName() ? entity.getCustomName().getString() : "";
        horse.created = System.currentTimeMillis() / 1000L;
        capture(horse, entity);
        entity.ejectPassengers();
        entity.discard();
        stables.put(horse);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.adopted", displayName(horse)));
    }

    // Selling --------------------------------------------------------------------------------------

    public static Result sell(ServerPlayer player, String id) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        if (horse.listedPrice > 0) return Result.no(ThaiText.t("rotasutils.msg.horse.listed_locked"));
        if (horse.unborn(nowSeconds())) return Result.no(ThaiText.t("rotasutils.msg.horse.unborn"));
        long price = npcPrice(data, horse);
        if (price <= 0) return Result.no(ThaiText.t("rotasutils.msg.horse.not_sellable"));
        StableData.Stable stable = stables.stable(player.getUUID());
        long today = SeasonService.today();
        if (stable.salesDay != today) {
            stable.salesDay = today;
            stable.salesToday = 0;
        }
        SeasonRules.HorseRules rules = rules(data);
        if (rules.npcSalesPerDay > 0 && stable.salesToday >= rules.npcSalesPerDay) {
            return Result.no(ThaiText.t("rotasutils.msg.horse.sales_limit", rules.npcSalesPerDay));
        }
        if (horse.active != null) retire(stables, horse, player.server);
        stables.remove(horse.id);
        stable.salesToday++;
        data.progress(player.getUUID()).rpg().currency(currency(data), price);
        data.setDirty();
        data.audit(player.getGameProfile().getName() + " sold horse " + horse.id + " to npc for " + price);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.sold", displayName(horse), price));
    }

    public static Result list(ServerPlayer player, String id, long price) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        if (horse.unborn(nowSeconds())) return Result.no(ThaiText.t("rotasutils.msg.horse.unborn"));
        if (price < 1 || price > rules(data).marketMaxPrice) {
            return Result.no(ThaiText.t("rotasutils.msg.horse.invalid_price", rules(data).marketMaxPrice));
        }
        // A listed horse is locked in the stable, so its levels cannot change under a buyer.
        if (horse.active != null) retire(stables, horse, player.server);
        horse.listedPrice = price;
        stables.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.horse.listed", displayName(horse), price));
    }

    public static Result unlist(ServerPlayer player, String id) {
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        horse.listedPrice = 0;
        stables.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.horse.unlisted", displayName(horse)));
    }

    public static Result buy(ServerPlayer player, String id, long expectedPrice) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = stables.horse(id);
        if (horse == null || horse.listedPrice <= 0) return Result.no(ThaiText.t("rotasutils.msg.horse.gone"));
        if (horse.owner.equals(player.getUUID())) return Result.no(ThaiText.t("rotasutils.msg.horse.own_listing"));
        // The price the buyer saw must still be the price: a seller cannot raise it under an open screen.
        if (horse.listedPrice != expectedPrice) return Result.no(ThaiText.t("rotasutils.msg.horse.price_changed"));
        int used = stables.owned(player.getUUID()).size();
        int slots = slots(data, stables, player);
        if (used >= slots) return Result.no(ThaiText.t("rotasutils.msg.horse.slots_full", used, slots));
        long price = horse.listedPrice;
        if (!pay(data, player, price)) return Result.no(fundsMessage(data, player, price));
        UUID seller = horse.owner;
        PlayerProgress sellerProgress = data.progress(seller);
        long fee = horse.traits.contains(HorseTrait.SHOWSTOPPER) ? 0 : marketFee(data, sellerProgress, price);
        sellerProgress.rpg().currency(currency(data), price - fee);
        horse.owner = player.getUUID();
        horse.listedPrice = 0;
        horse.studFee = 0;
        horse.generation++;
        stables.setDirty();
        data.setDirty();
        ServerPlayer online = player.server.getPlayerList().getPlayer(seller);
        if (online != null) {
            online.sendSystemMessage(ThaiText.c("rotasutils.msg.horse.sold_market", displayName(horse),
                    player.getGameProfile().getName(), price - fee).withStyle(ChatFormatting.GOLD));
            RotasNetwork.syncProgress(online);
        }
        data.audit(player.getGameProfile().getName() + " bought horse " + horse.id + " from " + seller + " for " + price + " fee=" + fee);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.bought", displayName(horse), price));
    }

    private static long marketFee(RotasData data, PlayerProgress seller, long price) {
        double feeRate = rules(data).marketFee * (1 - Math.max(0, Math.min(1, SeasonService.perk(data, seller).marketFeeDiscount)));
        return Math.max(0, Math.min(price, Math.round(price * feeRate)));
    }

    // Breeding -------------------------------------------------------------------------------------

    /** The whole fee to breed these two, stud fee included; what the screen shows and the server re-checks. */
    static long breedCost(SeasonRules.HorseRules rules, StableData.Horse dam, StableData.Horse sire, boolean stud) {
        return HorseBreeding.fee(rules, dam.parent(), sire.parent()) + (stud ? sire.studFee : 0);
    }

    /**
     * Breeds the player's {@code damId} with {@code sireId}: one of their own, or another player's stud for its fee.
     * The foal is rolled now and waits unborn in a stable slot until gestation ends.
     */
    public static Result breed(ServerPlayer player, String damId, String sireId, long expectedCost) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        SeasonRules.HorseRules rules = rules(data);
        if (!rules.breedEnabled) return Result.no(ThaiText.t("rotasutils.msg.horse.breed_disabled"));
        StableData stables = StableData.get(player.server);
        StableData.Horse dam = ownedHorse(stables, player, damId);
        StableData.Horse sire = stables.horse(sireId);
        if (dam == null || sire == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        boolean stud = !sire.owner.equals(player.getUUID());
        if (stud && sire.studFee <= 0) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        long now = nowSeconds();
        for (StableData.Horse parent : List.of(dam, sire)) {
            settle(rules, stables, parent);
            if (parent.unborn(now)) return Result.no(ThaiText.t("rotasutils.msg.horse.unborn"));
            if (parent.listedPrice > 0) return Result.no(ThaiText.t("rotasutils.msg.horse.listed_locked"));
            if (parent.breedsLeft <= 0) return Result.no(ThaiText.t("rotasutils.msg.horse.breed_spent", displayName(parent)));
            if (parent.breedReadyAt > now) {
                return Result.no(ThaiText.t("rotasutils.msg.horse.breed_resting", displayName(parent), (parent.breedReadyAt - now) / 60 + 1));
            }
        }
        // A stud out on its owner's ride is busy; the player's own horses are simply called home.
        if (stud && sire.active != null) return Result.no(ThaiText.t("rotasutils.msg.horse.stud_busy"));
        String kin = HorseBreeding.forbidden(dam.parent(), sire.parent());
        if (kin != null) return Result.no(ThaiText.t("rotasutils.msg.horse." + kin));
        int used = stables.owned(player.getUUID()).size();
        int slots = slots(data, stables, player);
        if (used >= slots) return Result.no(ThaiText.t("rotasutils.msg.horse.slots_full", used, slots));
        long cost = breedCost(rules, dam, sire, stud);
        if (cost != expectedCost) return Result.no(ThaiText.t("rotasutils.msg.horse.price_changed"));
        if (!pay(data, player, cost)) return Result.no(fundsMessage(data, player, cost));

        java.util.random.RandomGenerator random = new java.util.SplittableRandom(player.getRandom().nextLong());
        HorseBreeding.Foal genes = HorseBreeding.roll(rules, dam.parent(), sire.parent(), random);
        StableData.Horse foal = birth(player, data, stables, genes, random);
        if (foal == null) {
            data.progress(player.getUUID()).rpg().currency(currency(data), cost);
            return Result.no(ThaiText.t("rotasutils.msg.horse.create_failed"));
        }
        foal.sire = sire.id;
        foal.dam = dam.id;
        foal.sireName = displayName(sire);
        foal.damName = displayName(dam);
        foal.bornAt = now + rules.gestationMinutes * 60L;
        if (dam.active != null) retire(stables, dam, player.server);
        if (!stud && sire.active != null) retire(stables, sire, player.server);
        for (StableData.Horse parent : List.of(dam, sire)) {
            parent.breedsLeft--;
            parent.breedReadyAt = now + rules.breedCooldownMinutes * 60L;
            if (parent.breedsLeft <= 0) parent.studFee = 0;
        }
        if (stud) {
            PlayerProgress owner = data.progress(sire.owner);
            long studFee = cost - HorseBreeding.fee(rules, dam.parent(), sire.parent());
            long fee = sire.traits.contains(HorseTrait.SHOWSTOPPER) ? 0 : marketFee(data, owner, studFee);
            owner.rpg().currency(currency(data), studFee - fee);
            ServerPlayer online = player.server.getPlayerList().getPlayer(sire.owner);
            if (online != null) {
                online.sendSystemMessage(ThaiText.c("rotasutils.msg.horse.stud_paid", displayName(sire),
                        player.getGameProfile().getName(), studFee - fee).withStyle(ChatFormatting.GOLD));
                RotasNetwork.syncProgress(online);
            }
        }
        stables.setDirty();
        data.setDirty();
        player.level().playSound(null, player.blockPosition(), SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1f, 0.8f);
        net.schwarz.rotasutils.server.Fx.fountain(player, net.minecraft.core.particles.ParticleTypes.HEART, 7);
        data.audit(player.getGameProfile().getName() + " bred " + dam.id + " x " + sire.id + " -> " + foal.id + " cost=" + cost);
        net.schwarz.rotasutils.server.TitleService.count(player.server, data, player.getUUID(),
                net.schwarz.rotasutils.title.TitleCounters.BRED, 1);
        return Result.ok(ThaiText.t("rotasutils.msg.horse.bred", displayName(dam), displayName(sire), rules.gestationMinutes));
    }

    /** Builds the foal's SWEM body off-world from its genes and keeps only the snapshot. */
    private static StableData.Horse birth(ServerPlayer player, RotasData data, StableData stables, HorseBreeding.Foal genes,
                                          java.util.random.RandomGenerator random) {
        EntityType<?> type = SwemCompat.horseType();
        Entity entity = type == null ? null : type.create(player.serverLevel());
        if (entity == null) return null;
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        SwemCompat.tame(entity, player);
        SwemCompat.setLevels(entity, genes.startLevels());
        CoatChoice coat = pickCoat(rules(data), genes.coat(), random);
        if (!coat.id().isEmpty()) SwemCompat.setCoat(entity, coat.id());
        StableData.Horse horse = new StableData.Horse();
        horse.id = stables.newId();
        horse.owner = player.getUUID();
        horse.origin = StableData.Origin.BRED;
        horse.startLevels = genes.startLevels().clone();
        horse.coat = SwemCompat.coat(entity);
        horse.secretCoat = coat.secret();
        horse.rareCoat = coat.rare();
        horse.created = nowSeconds();
        horse.lineage = genes.lineage();
        horse.traits = new ArrayList<>(genes.traits());
        horse.traitsRolled = true;
        horse.breedsLeft = HorseBreeding.breedings(rules(data), horse.traits);
        capture(horse, entity);
        entity.discard();
        stables.put(horse);
        return horse;
    }

    /** Offers a horse at stud for {@code fee} (0 withdraws it). */
    public static Result setStud(ServerPlayer player, String id, long fee) {
        RotasData data = RotasData.get(player.server);
        String blocked = unavailable(data);
        if (blocked != null) return Result.no(blocked);
        SeasonRules.HorseRules rules = rules(data);
        StableData stables = StableData.get(player.server);
        StableData.Horse horse = ownedHorse(stables, player, id);
        if (horse == null) return Result.no(ThaiText.t("rotasutils.msg.horse.not_yours"));
        if (fee < 0 || fee > rules.studMaxFee) return Result.no(ThaiText.t("rotasutils.msg.horse.invalid_price", rules.studMaxFee));
        settle(rules, stables, horse);
        if (fee > 0 && horse.unborn(nowSeconds())) return Result.no(ThaiText.t("rotasutils.msg.horse.unborn"));
        if (fee > 0 && horse.breedsLeft <= 0) return Result.no(ThaiText.t("rotasutils.msg.horse.breed_spent", displayName(horse)));
        horse.studFee = fee;
        stables.setDirty();
        return Result.ok(ThaiText.t(fee > 0 ? "rotasutils.msg.horse.stud_on" : "rotasutils.msg.horse.stud_off", displayName(horse), fee));
    }

    /** Tells owners who are online that a foal was born; announces a starborn foal to everyone. */
    private static void announceBirths(MinecraftServer server, StableData stables) {
        long now = nowSeconds();
        for (StableData.Horse horse : stables.all()) {
            if (horse.bornAt <= 0 || horse.bornAt > now) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(horse.owner);
            if (owner == null) continue;
            horse.bornAt = 0;
            stables.setDirty();
            owner.sendSystemMessage(ThaiText.c("rotasutils.msg.horse.born", displayName(horse)).withStyle(ChatFormatting.GREEN));
            owner.level().playSound(null, owner.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
            net.schwarz.rotasutils.server.Fx.spiral(owner, horse.traits.contains(HorseTrait.STARBORN)
                    ? net.minecraft.core.particles.ParticleTypes.END_ROD : net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    22, 0.9, 2.0);
            if (horse.traits.contains(HorseTrait.STARBORN)) {
                net.schwarz.rotasutils.server.NpcSocial.rumor("คอกของ " + owner.getGameProfile().getName()
                        + " มีลูกม้าบุตรแห่งดาราเกิดใหม่ ชื่อ " + displayName(horse));
                server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.horse.starborn_broadcast",
                        owner.getGameProfile().getName(), displayName(horse)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        }
    }

    public static List<String> leaderboard(MinecraftServer server, int limit) {
        RotasData data = RotasData.get(server);
        List<StableData.Horse> horses = new ArrayList<>(StableData.get(server).all());
        horses.sort(Comparator.comparingInt(StableData.Horse::trainedLevels).reversed()
                .thenComparing(horse -> !horse.secretCoat));
        List<String> lines = new ArrayList<>();
        for (StableData.Horse horse : horses) {
            if (lines.size() >= limit) break;
            if (horse.trainedLevels() <= 0 && !horse.secretCoat) continue;
            lines.add(ThaiText.t("rotasutils.msg.horse.top_line", lines.size() + 1, displayName(horse),
                    data.progress(horse.owner).lastKnownName(), horse.trainedLevels(),
                    horse.secretCoat ? ThaiText.t("rotasutils.msg.horse.secret_coat") : ""));
        }
        return lines;
    }

    // World hooks ----------------------------------------------------------------------------------

    /** Refuses any stable horse copy that is not the current generation. */
    public static EventResult onAdd(Entity entity, Level level) {
        if (level.isClientSide || level.getServer() == null || !SwemCompat.isHorse(entity)) return EventResult.pass();
        for (String tag : entity.getTags()) {
            if (!tag.startsWith("rotas_horse:")) continue;
            String[] parts = tag.split(":");
            StableData.Horse horse = parts.length == 3 ? StableData.get(level.getServer()).horse(parts[1]) : null;
            boolean current = horse != null && parts[2].equals(Integer.toString(horse.generation))
                    && entity.getUUID().equals(horse.active);
            return current ? EventResult.pass() : EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    /** A stable horse is knocked out instead of dying: it goes home with full health and rests a while. */
    public static EventResult onDeath(LivingEntity entity) {
        if (entity.level().isClientSide || entity.getServer() == null || !SwemCompat.isHorse(entity)) return EventResult.pass();
        StableData stables = StableData.get(entity.getServer());
        StableData.Horse horse = stables.byActive(entity.getUUID());
        if (horse == null) return EventResult.pass();
        capture(horse, entity);
        horse.snapshot.putFloat("Health", entity.getMaxHealth());
        entity.ejectPassengers();
        entity.discard();
        horse.active = null;
        horse.generation++;
        int recovery = rules(RotasData.get(entity.getServer())).recoverySeconds;
        if (horse.traits.contains(HorseTrait.STALWART)) recovery /= 2;
        horse.recoverUntil = nowSeconds() + recovery;
        stables.setDirty();
        ServerPlayer owner = entity.getServer().getPlayerList().getPlayer(horse.owner);
        if (owner != null) {
            owner.sendSystemMessage(ThaiText.c("rotasutils.msg.horse.knocked_out", displayName(horse), recovery)
                    .withStyle(ChatFormatting.RED));
        }
        return EventResult.interruptFalse();
    }

    /** Caps SWEM XP potions per horse per day, so gold cannot skip the training the draw sells time against. */
    public static boolean blockPotion(ServerPlayer player, Entity entity, InteractionHand hand) {
        if (!SwemCompat.isHorse(entity) || !SwemCompat.isXpPotion(player.getItemInHand(hand))) return false;
        RotasData data = RotasData.get(player.server);
        if (!SeasonService.active(data)) return false;
        int limit = rules(data).potionUsesPerDay;
        if (limit <= 0) return false;
        long day = SeasonService.today();
        String key = "horse_potion|" + entity.getUUID();
        if (!data.addCounter(key, day, limit, 1)) {
            player.displayClientMessage(ThaiText.c("rotasutils.msg.horse.potion_limit", limit).withStyle(ChatFormatting.RED), true);
            return true;
        }
        return false;
    }

    /** Once a second: trait effects; every minute: births and fresh snapshots of horses in the world. */
    public static void tick(MinecraftServer server) {
        if (!SwemCompat.available()) return;
        RotasData data = RotasData.get(server);
        StableData stables = StableData.get(server);
        HorseTraitEffects.tick(server, stables, rules(data));
        if (++tickSeconds < SNAPSHOT_SECONDS) return;
        tickSeconds = 0;
        announceBirths(server, stables);
        for (StableData.Horse horse : stables.all()) {
            if (horse.active == null) continue;
            Entity entity = find(server, horse.active);
            if (entity != null && entity.isAlive()) {
                capture(horse, entity);
                stables.setDirty();
            }
        }
    }

    // Screen ---------------------------------------------------------------------------------------

    static CompoundTag describe(RotasData data, StableData.Horse horse, MinecraftServer server) {
        CompoundTag tag = new CompoundTag();
        long now = nowSeconds();
        boolean unborn = horse.unborn(now);
        settle(rules(data), StableData.get(server), horse);
        tag.putString("id", horse.id);
        tag.putString("name", displayName(horse));
        tag.putString("origin", horse.origin.name());
        tag.putString("rarity", horse.rarity == null ? "" : horse.rarity.name());
        tag.putInt("lineage", horse.lineage);
        tag.putString("sire_name", horse.sireName);
        tag.putString("dam_name", horse.damName);
        tag.putInt("breeds_left", Math.max(0, horse.breedsLeft));
        tag.putLong("breed_ready", Math.max(0, horse.breedReadyAt - now));
        tag.putLong("stud_fee", horse.studFee);
        tag.putString("sire_id", horse.sire);
        tag.putString("dam_id", horse.dam);
        if (unborn) {
            // What a foal is stays a surprise until it is born.
            tag.putLong("unborn", horse.bornAt - now);
            tag.putIntArray("levels", new int[]{1, 1, 1, 1});
            tag.putIntArray("start", new int[]{1, 1, 1, 1});
            tag.putString("owner", data.progress(horse.owner).lastKnownName());
            return tag;
        }
        ListTag traits = new ListTag();
        horse.traits.forEach(trait -> traits.add(net.minecraft.nbt.StringTag.valueOf(trait.name())));
        tag.put("traits", traits);
        tag.putIntArray("levels", horse.levels());
        tag.putIntArray("start", horse.startLevels);
        tag.putString("coat", horse.coat);
        tag.putBoolean("secret", horse.secretCoat);
        tag.putBoolean("rare", horse.rareCoat);
        tag.putBoolean("active", horse.active != null);
        tag.putLong("listed", horse.listedPrice);
        tag.putLong("recover", Math.max(0, horse.recoverUntil - System.currentTimeMillis() / 1000L));
        tag.putLong("npc_price", npcPrice(data, horse));
        tag.putString("owner", data.progress(horse.owner).lastKnownName());
        tag.put("snapshot", horse.snapshot.copy());
        return tag;
    }

    /** Everything the stable screen shows. Market and draw tabs only open at a stable NPC. */
    public static CompoundTag snapshot(ServerPlayer player, String npcId) {
        RotasData data = RotasData.get(player.server);
        CompoundTag tag = new CompoundTag();
        String blocked = unavailable(data);
        tag.putString("blocked", blocked == null ? "" : blocked);
        tag.putString("npc", npcId == null ? "" : npcId);
        if (blocked != null) return tag;
        SeasonRules.HorseRules rules = rules(data);
        StableData stables = StableData.get(player.server);
        StableData.Stable stable = stables.stable(player.getUUID());
        PlayerProgress progress = data.progress(player.getUUID());
        tag.putString("currency", currency(data));
        tag.putLong("balance", progress.rpg().currency(currency(data)));
        tag.putInt("slots", slots(data, stables, player));
        tag.putInt("max_slots", rules.maxStableSlots);
        tag.putLong("slot_cost", SeasonMath.slotCost(stable.purchasedSlots, rules.slotBaseCost, rules.slotCostGrowth));
        tag.putLong("pull_cost", rules.pullCost);
        tag.putLong("ten_cost", rules.tenPullCost);
        tag.putIntArray("pity", new int[]{stable.sinceRare, stable.sinceEpic, stable.sinceLegendary});
        tag.putIntArray("pity_limits", new int[]{rules.pityRare, rules.pityEpic, rules.pityLegendary});
        ListTag rates = new ListTag();
        for (double rate : rules.rates) rates.add(net.minecraft.nbt.DoubleTag.valueOf(rate));
        tag.put("rates", rates);
        tag.putInt("sales_left", rules.npcSalesPerDay <= 0 ? -1
                : stable.salesDay == SeasonService.today() ? Math.max(0, rules.npcSalesPerDay - stable.salesToday) : rules.npcSalesPerDay);
        tag.putDouble("market_fee", rules.marketFee * (1 - Math.max(0, Math.min(1, SeasonService.perk(data, progress).marketFeeDiscount))));
        ListTag horses = new ListTag();
        stables.owned(player.getUUID()).forEach(horse -> horses.add(describe(data, horse, player.server)));
        tag.put("horses", horses);
        if (npcId != null && !npcId.isBlank()) {
            ListTag market = new ListTag();
            for (StableData.Horse horse : stables.listed()) {
                if (market.size() >= 100) break;
                market.add(describe(data, horse, player.server));
            }
            tag.put("market", market);
        }
        tag.putBoolean("breed_enabled", rules.breedEnabled);
        tag.putLong("breed_base", rules.breedBaseCost);
        tag.putLong("breed_per_lineage", rules.breedCostPerLineage);
        tag.putInt("gestation", rules.gestationMinutes);
        tag.putLong("stud_max", rules.studMaxFee);
        ListTag studs = new ListTag();
        for (StableData.Horse horse : stables.studs()) {
            if (studs.size() >= 100) break;
            if (!horse.owner.equals(player.getUUID())) studs.add(describe(data, horse, player.server));
        }
        tag.put("studs", studs);
        ListTag top = new ListTag();
        leaderboard(player.server, 10).forEach(line -> top.add(net.minecraft.nbt.StringTag.valueOf(line)));
        tag.put("top", top);
        return tag;
    }

    public static void open(ServerPlayer player, String npcId, String tab, ListTag results) {
        CompoundTag payload = snapshot(player, npcId);
        payload.putString("tab", tab == null ? "" : tab);
        if (results != null && !results.isEmpty()) payload.put("results", results);
        RotasNetwork.openScreen(player, "stable", payload);
    }
}
