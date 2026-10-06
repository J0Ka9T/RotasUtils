package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

public final class ProductionService {
    private static final String FRACTION = "rpg.season.prodfrac";
    private static final long PLACED_MEMORY_MILLIS = 6 * 60 * 60 * 1000L;
    private static final int PLACED_LIMIT = 8192;
    private static final int STATION_LIMIT = 4096;
    private static final Map<Long, Long> PLACED = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > PLACED_LIMIT;
        }
    };
    private static final Map<Long, UUID> STATION_OWNERS = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, UUID> eldest) {
            return size() > STATION_LIMIT;
        }
    };
    private static final Map<UUID, Long> LOCK_MESSAGES = new java.util.HashMap<>();

    private ProductionService() {
    }

    public interface Target {
        String id();

        boolean inTag(ResourceLocation tag);
    }

    public record Access(State state, String jobName, int level) {
        public enum State { FREE, GRANTED, LOCKED }

        public static final Access FREE = new Access(State.FREE, "", 0);
        public static final Access GRANTED = new Access(State.GRANTED, "", 0);

        public boolean locked() {
            return state == State.LOCKED;
        }
    }

    public static Target item(ItemStack stack) {
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return new Target() {
            @Override public String id() { return id; }
            @Override public boolean inTag(ResourceLocation tag) { return stack.is(TagKey.create(Registries.ITEM, tag)); }
        };
    }

    public static Target block(BlockState state) {
        String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return new Target() {
            @Override public String id() { return id; }
            @Override public boolean inTag(ResourceLocation tag) { return state.is(TagKey.create(Registries.BLOCK, tag)); }
        };
    }

    public static JobDef.ProductionEntry match(JobDef job, Activity activity, Target target) {
        JobDef.ProductionEntry best = null;
        for (JobDef.ProductionEntry entry : job.production()) {
            if (entry.activity() != activity || !matches(entry.selector(), target)) {
                continue;
            }
            boolean exact = !entry.selector().startsWith("#");
            boolean bestExact = best != null && !best.selector().startsWith("#");
            if (best == null || (exact && !bestExact) || (exact == bestExact && entry.unlockLevel() > best.unlockLevel())) {
                best = entry;
            }
        }
        return best;
    }

    private static final java.util.Map<String, java.util.Optional<ResourceLocation>> TAGS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean matches(String selector, Target target) {
        if (selector.startsWith("#")) {
            ResourceLocation tag = TAGS.computeIfAbsent(selector, s -> java.util.Optional.ofNullable(ResourceLocation.tryParse(s.substring(1)))).orElse(null);
            return tag != null && target.inTag(tag);
        }
        return selector.equals(target.id());
    }

    public static int subLevel(PlayerProgress progress, JobDef job) {
        return job.masteryCurve().levelAt(progress.rpg().masteryXp(job.id()));
    }

    public static Access access(Iterable<JobDef> jobs, String subJob, ToIntFunction<JobDef> levelOf,
                                Activity activity, Target target, int minLockedLevel) {
        JobDef lockedBy = null;
        int lockedLevel = Integer.MAX_VALUE;
        for (JobDef job : jobs) {
            if (!job.enabled()) {
                continue;
            }
            JobDef.ProductionEntry entry = match(job, activity, target);
            if (entry == null || entry.unlockLevel() < minLockedLevel) {
                continue;
            }
            boolean own = job.id().equals(subJob);
            if (own && levelOf.applyAsInt(job) >= entry.unlockLevel()) {
                return Access.GRANTED;
            }
            boolean lockedByOwn = lockedBy != null && lockedBy.id().equals(subJob);
            if (own || (!lockedByOwn && entry.unlockLevel() < lockedLevel)) {
                lockedBy = job;
                lockedLevel = entry.unlockLevel();
            }
        }
        return lockedBy == null ? Access.FREE : new Access(Access.State.LOCKED, lockedBy.name(), lockedLevel);
    }

    public static Access access(RotasData data, PlayerProgress progress, Activity activity, Target target, int minLockedLevel) {
        return access(data.jobs().values(), progress.subJob(), job -> subLevel(progress, job), activity, target, minLockedLevel);
    }

    public static void produced(ServerPlayer player, Activity activity, Target target, int count) {
        RotasData data = RotasData.get(player.server);
        if (!SeasonService.active(data) || count <= 0) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        JobDef job = data.job(progress.subJob());
        if (job == null || !job.enabled() || job.productionXpRate() <= 0) {
            return;
        }
        JobDef.ProductionEntry entry = match(job, activity, target);
        if (entry == null) {
            return;
        }
        award(player, data, progress, job, entry.unlockLevel(), activity.name().toLowerCase(Locale.ROOT) + ":" + target.id(), count);
    }

    public static void producedAt(ServerPlayer player, String jobId, int unlockLevel, String firstKey, int count) {
        RotasData data = RotasData.get(player.server);
        if (!SeasonService.active(data) || count <= 0) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        JobDef job = data.job(progress.subJob());
        if (job == null || !job.enabled() || !job.id().equals(jobId) || job.productionXpRate() <= 0) {
            return;
        }
        award(player, data, progress, job, unlockLevel, firstKey, count);
    }

    private static void award(ServerPlayer player, RotasData data, PlayerProgress progress, JobDef job, int unlockLevel,
                              String firstKey, int count) {
        SeasonRules rules = SeasonService.rules(data);
        int level = subLevel(progress, job);
        if (level < unlockLevel) {
            return;
        }
        int tier = SeasonMath.tierIndex(unlockLevel, rules.tierMaxLevel);
        double unit = rules.tierXp[tier] * job.productionXpRate()
                * SeasonMath.craftMultiplier(level, rules.tierMaxLevel[tier], rules.craftGraceLevels,
                rules.craftPenaltyPerLevel, rules.craftMaxPenalty);
        double xp = unit * Math.min(64, count);
        if (SeasonService.claimFirstCraft(progress, firstKey)) {
            xp += unit * (rules.firstCraftMultiplier - 1);
        }
        long millis = Math.round(xp * 1000) + fraction(progress);
        long whole = millis / 1000;
        progress.questVariables().put(FRACTION, Long.toString(millis % 1000));
        progress.markDirty();
        data.setDirty();
        if (whole <= 0) {
            return;
        }
        if (level >= job.masteryCurve().maxLevel()) {
            SeasonService.overflowSubJob(data, progress, whole);
            return;
        }
        JobService.MasteryGrant grant = JobService.grantProduction(progress, job, whole);
        if (grant.levelsGained() > 0) {
            int now = subLevel(progress, job);
            int before = now - grant.levelsGained();
            long unlocked = job.production().stream()
                    .filter(row -> row.unlockLevel() > before && row.unlockLevel() <= now).count();
            var book = TradeConfig.byJob(job.id());
            if (book != null) {
                unlocked += book.recipes().stream().filter(r -> !r.secret() && r.level() > before && r.level() <= now).count();
            }
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.season.sub_level", job.name(), now, unlocked)
                    .withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
    }

    private static long fraction(PlayerProgress progress) {
        try {
            return Math.max(0, Math.min(999, Long.parseLong(progress.questVariables().getOrDefault(FRACTION, "0"))));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

private static boolean hardLockOn(SeasonRules rules, Activity activity) {
        return switch (activity) {
            case CRAFT -> rules.lockRecipes;
            case SMELT -> rules.lockSmelting;
            case BREW -> rules.lockBrewing;
            default -> false;
        };
    }

    private static int hardThreshold(SeasonRules rules) {
        return rules.lockStarterRows ? 1 : 2;
    }

    private static boolean plainWater(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    public static boolean takeLocked(ServerPlayer player, Activity activity, ItemStack result) {
        if (result.isEmpty() || (activity == Activity.BREW && plainWater(result))) {
            return false;
        }
        RotasData data = RotasData.instance();
        if (data == null || !SeasonService.active(data)) {
            return false;
        }
        if (stationOnly(activity, result)) {
            String trade = TradeConfig.sealedBy(String.valueOf(BuiltInRegistries.ITEM.getKey(result.getItem())));
            sealed(player, activity, result, new Access(Access.State.LOCKED,
                    ThaiText.t("rotasutils.trade.station." + (trade == null ? "chef" : trade)), 0));
            return true;
        }
        SeasonRules rules = SeasonService.rules(data);
        if (!hardLockOn(rules, activity)) {
            return false;
        }
        Access access = access(data, data.progress(player.getUUID()), activity, item(result), hardThreshold(rules));
        if (!access.locked()) {
            return false;
        }
        sealed(player, activity, result, access);
        return true;
    }

    private static boolean stationOnly(Activity activity, ItemStack result) {
        return (activity == Activity.CRAFT || activity == Activity.SMELT)
                && TradeConfig.sealed(String.valueOf(BuiltInRegistries.ITEM.getKey(result.getItem())));
    }

    public static void rememberStation(Level level, BlockPos pos, UUID player) {
        STATION_OWNERS.put(key(level, pos), player);
    }

    public static boolean automationMayTake(Level level, BlockPos pos, Activity activity, ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty() || (activity == Activity.BREW && plainWater(stack))) {
            return true;
        }
        RotasData data = RotasData.instance();
        if (data == null || !SeasonService.active(data)) {
            return true;
        }
        if (stationOnly(activity, stack)) {
            return false;
        }
        SeasonRules rules = SeasonService.rules(data);
        if (!hardLockOn(rules, activity)) {
            return true;
        }
        UUID owner = STATION_OWNERS.get(key(level, pos));
        PlayerProgress progress = owner == null ? null : data.progress(owner);
        Access access = progress == null
                ? access(data.jobs().values(), "", job -> 0, activity, item(stack), hardThreshold(rules))
                : access(data, progress, activity, item(stack), hardThreshold(rules));
        return !access.locked();
    }

private static Access gatherAccess(RotasData data, ServerPlayer player, Activity activity, Target target) {
        return access(data, data.progress(player.getUUID()), activity, target, SeasonService.rules(data).gatherFreeLevel + 1);
    }

    public static List<ItemStack> thinBlockDrops(ServerPlayer player, BlockState state, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty() || player.isCreative()) {
            return null;
        }
        RotasData data = RotasData.get(player.server);
        if (!SeasonService.active(data)) {
            return null;
        }
        Target target = block(state);
        Activity activity = Activity.MINE;
        Access access = gatherAccess(data, player, activity, target);
        if (!access.locked() && harvestable(state)) {
            activity = Activity.HARVEST;
            access = gatherAccess(data, player, activity, target);
        }
        if (!access.locked()) {
            return null;
        }
        SeasonRules rules = SeasonService.rules(data);
        List<ItemStack> kept = new ArrayList<>(drops.size());
        boolean lost = false;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            if (player.getRandom().nextDouble() >= rules.gatherDropChance) {
                lost = true;
                continue;
            }
            if (stack.getCount() > rules.gatherMaxCount) {
                stack = stack.copyWithCount(rules.gatherMaxCount);
                lost = true;
            }
            kept.add(stack);
        }
        if (lost) {
            sealed(player, activity, drops.get(0), access);
        }
        return kept;
    }

    public static ItemStack thinCatch(ServerPlayer player, ItemStack caught) {
        if (caught.isEmpty()) {
            return caught;
        }
        RotasData data = RotasData.get(player.server);
        if (!SeasonService.active(data)) {
            return caught;
        }
        Access access = gatherAccess(data, player, Activity.FISH, item(caught));
        if (!access.locked()) {
            return caught;
        }
        SeasonRules rules = SeasonService.rules(data);
        if (player.getRandom().nextDouble() < rules.gatherDropChance) {
            return caught.getCount() > rules.gatherMaxCount ? caught.copyWithCount(rules.gatherMaxCount) : caught;
        }
        sealed(player, Activity.FISH, caught, access);
        ResourceLocation fallback = ResourceLocation.tryParse(rules.gatherFishFallback);
        return fallback != null && BuiltInRegistries.ITEM.containsKey(fallback)
                ? new ItemStack(BuiltInRegistries.ITEM.get(fallback)) : ItemStack.EMPTY;
    }

    private static void sealed(ServerPlayer player, Activity activity, ItemStack stack, Access access) {
        long now = System.currentTimeMillis();
        Long last = LOCK_MESSAGES.get(player.getUUID());
        if (last != null && now - last < 2000) {
            return;
        }
        LOCK_MESSAGES.put(player.getUUID(), now);
        RotasNetwork.sealedCraft(player, activity.name(), stack, access.jobName(), access.level());
    }

    public static boolean harvestable(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }
        return state.getBlock() instanceof StemGrownBlock;
    }

    public static void rememberPlaced(Level level, BlockPos pos) {
        PLACED.put(key(level, pos), System.currentTimeMillis());
    }

    public static boolean recentlyPlaced(Level level, BlockPos pos) {
        Long placed = PLACED.remove(key(level, pos));
        return placed != null && System.currentTimeMillis() - placed < PLACED_MEMORY_MILLIS;
    }

    private static long key(Level level, BlockPos pos) {
        return pos.asLong() * 31 + level.dimension().location().hashCode();
    }

    public static void clear() {
        PLACED.clear();
        STATION_OWNERS.clear();
        LOCK_MESSAGES.clear();
    }
}
