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

/**
 * Sub-job production: the EXP a sub job earns from crafting, smelting, mining, harvesting, fishing and brewing, and
 * the gates that make the sub-role matter.
 *
 * <p>Each job has an unlock table. The level an item unlocks at decides its tier (A 1-4, B 5-9, C 10-14,
 * D 15-19), the tier decides the base EXP, and the job's profession rate scales it. Outgrown tiers pay less,
 * the first craft of an item pays triple, and a maxed sub job turns its EXP into overflow currency.</p>
 *
 * <p>The same table decides access. A crafting, smelting or brewing result in any job's table can only be taken by
 * a player whose sub job is that job at the unlock level; hoppers under a station act for the last player who
 * opened it. Mining, harvesting and fishing stay open to everyone, but without the role most drops are lost.</p>
 */
public final class ProductionService {
    private static final String FRACTION = "rpg.season.prodfrac";
    private static final long PLACED_MEMORY_MILLIS = 6 * 60 * 60 * 1000L;
    private static final int PLACED_LIMIT = 8192;
    private static final int STATION_LIMIT = 4096;
    /** Blocks players placed recently; re-mining them pays nothing, so place-and-mine loops earn no EXP. */
    private static final Map<Long, Long> PLACED = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > PLACED_LIMIT;
        }
    };
    /**
     * The last player to open each furnace or brewing stand. Automation takes sealed results on their behalf. Kept in
     * memory only: after a restart a station refuses automation until someone opens it again.
     */
    private static final Map<Long, UUID> STATION_OWNERS = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, UUID> eldest) {
            return size() > STATION_LIMIT;
        }
    };
    private static final Map<UUID, Long> LOCK_MESSAGES = new java.util.HashMap<>();

    private ProductionService() {
    }

    /** An item or block as the unlock table sees it: a registry id plus a tag test. */
    public interface Target {
        String id();

        boolean inTag(ResourceLocation tag);
    }

    /** Whether a player may take, or fully gather, one production target. */
    public record Access(State state, String jobName, int level) {
        public enum State { FREE, GRANTED, LOCKED }

        /** No enabled job seals the target. */
        public static final Access FREE = new Access(State.FREE, "", 0);
        /** The player's sub job holds the target at a high enough level. */
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

    /** The table row for this target: an exact id beats a tag, and a higher unlock level beats a lower one. */
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

    private static boolean matches(String selector, Target target) {
        if (selector.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(selector.substring(1));
            return tag != null && target.inTag(tag);
        }
        return selector.equals(target.id());
    }

    public static int subLevel(PlayerProgress progress, JobDef job) {
        return job.masteryCurve().levelAt(progress.rpg().masteryXp(job.id()));
    }

    /**
     * Who may take or fully gather a target. Rows below {@code minLockedLevel} seal nothing. The target is granted
     * when the sub job has a sealing row the player has reached; otherwise it is locked by the player's own sub job
     * when that job has a row, or else by the easiest job that has one, so the message names the most useful role.
     */
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

    /** Pays production EXP to the player's sub job for {@code count} units of one activity. */
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
        SeasonRules rules = SeasonService.rules(data);
        int level = subLevel(progress, job);
        if (level < entry.unlockLevel()) {
            return;
        }
        int tier = SeasonMath.tierIndex(entry.unlockLevel(), rules.tierMaxLevel);
        double unit = rules.tierXp[tier] * job.productionXpRate()
                * SeasonMath.craftMultiplier(level, rules.tierMaxLevel[tier], rules.craftGraceLevels,
                rules.craftPenaltyPerLevel, rules.craftMaxPenalty);
        double xp = unit * Math.min(64, count);
        if (SeasonService.claimFirstCraft(progress, activity.name().toLowerCase(Locale.ROOT) + ":" + target.id())) {
            xp += unit * (rules.firstCraftMultiplier - 1);
        }
        // Keep the fraction: a Chef's tier A dish is 3.6 EXP, which would round away over many crafts.
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

    // Hard locks: crafting, smelting and brewing results ---------------------------------------------

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

    /** A plain water bottle is the brewing stand's input, so a player can always take their own back. */
    private static boolean plainWater(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    /**
     * True when a crafting, smelting or brewing result is sealed to a sub-role this player lacks. The player is shown
     * the sealed scroll saying which role holds it.
     */
    public static boolean takeLocked(ServerPlayer player, Activity activity, ItemStack result) {
        if (result.isEmpty() || (activity == Activity.BREW && plainWater(result))) {
            return false;
        }
        RotasData data = RotasData.instance();
        if (data == null || !SeasonService.active(data)) {
            return false;
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

    /** Remembers who opened a furnace or brewing stand, so its hopper can act for them. */
    public static void rememberStation(Level level, BlockPos pos, UUID player) {
        STATION_OWNERS.put(key(level, pos), player);
    }

    /**
     * Hopper and item-pipe extraction of a station result: allowed when the result is not sealed, or when the last
     * player to open the station holds the role. An unknown owner fails closed.
     */
    public static boolean automationMayTake(Level level, BlockPos pos, Activity activity, ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty() || (activity == Activity.BREW && plainWater(stack))) {
            return true;
        }
        RotasData data = RotasData.instance();
        if (data == null || !SeasonService.active(data)) {
            return true;
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

    // Soft gathering: mining, harvesting and fishing --------------------------------------------------

    private static Access gatherAccess(RotasData data, ServerPlayer player, Activity activity, Target target) {
        return access(data, data.progress(player.getUUID()), activity, target, SeasonService.rules(data).gatherFreeLevel + 1);
    }

    /**
     * The drops of a block broken by a player without the sub-role that gathers it: each stack survives only by
     * chance and keeps at most the capped count. Returns null when the drops stand as they are.
     */
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

    /** A catch for a player without the Fisher role: usually swapped for the fallback fish, never above the cap. */
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

    /** Sends the sealed scroll, at most once every two seconds per player. */
    private static void sealed(ServerPlayer player, Activity activity, ItemStack stack, Access access) {
        long now = System.currentTimeMillis();
        Long last = LOCK_MESSAGES.get(player.getUUID());
        if (last != null && now - last < 2000) {
            return;
        }
        LOCK_MESSAGES.put(player.getUUID(), now);
        RotasNetwork.sealedCraft(player, activity.name(), stack, access.jobName(), access.level());
    }

    /** Crops at full age, melons, pumpkins, cocoa and nether wart count as a harvest. */
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

    /** True when a player placed this block recently; mining it again pays no production EXP. */
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
