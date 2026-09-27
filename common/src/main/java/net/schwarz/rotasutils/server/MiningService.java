package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.mine.MiningSite;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mining sites at run time: a worked node pays, runs dry, and comes back.
 *
 * <p>The break itself stays vanilla - the ore drops what it always drops, with the player's own tool
 * and enchantments - and this only adds the site's extras and swaps the block for its depleted look on
 * the next tick, once vanilla has finished removing it. A spent node cannot be broken until it returns,
 * which is what turns a pit into a place worth coming back to.</p>
 *
 * <p>The respawn pass runs once a second and only looks at spent nodes in loaded chunks, so an idle
 * site costs a few long comparisons.</p>
 */
public final class MiningService {
    /** What a break at a position means for mining. */
    public enum Break { NOT_A_NODE, WORKED, BLOCKED }

    /**
     * A node a player has just started to break. The break event fires before the block is removed, and
     * another mod's claim protection may still cancel it, so nothing is paid until the next tick shows
     * the block really is gone.
     */
    private record Pending(ResourceKey<Level> dimension, BlockPos pos, String depleted,
                           java.util.UUID player, String site, String block) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static long tick;

    private MiningService() {
    }

    public static void clear() {
        PENDING.clear();
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /** The site and node at a position, or null. */
    private static Object[] find(RotasData data, Level level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        long key = pos.asLong();
        for (MiningSite site : data.miningSites().values()) {
            if (!site.dimension().equals(dimension)) {
                continue;
            }
            MiningSite.Node node = site.node(key);
            if (node != null) {
                return new Object[]{site, node};
            }
        }
        return null;
    }

    /**
     * Called from the block-break hook before anything else counts the break. A spent node refuses the
     * break outright; a full one pays the site's extras and is queued to turn into its depleted block.
     */
    public static Break onBreak(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        RotasData data = RotasData.get(player.server);
        if (data.miningSites().isEmpty() || player.isCreative()) {
            return Break.NOT_A_NODE;
        }
        Object[] found = find(data, level, pos);
        if (found == null) {
            return Break.NOT_A_NODE;
        }
        MiningSite site = (MiningSite) found[0];
        MiningSite.Node node = (MiningSite.Node) found[1];
        long now = now();
        if (node.depleted(now)) {
            player.displayClientMessage(ThaiText.c("rotasutils.msg.mine.depleted",
                    net.schwarz.rotasutils.core.DailyTrack.countdown(node.secondsLeft(now))), true);
            return Break.BLOCKED;
        }
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (!blockId.equals(node.block())) {
            // Someone swapped the block; only the ore the node was registered with pays.
            return Break.NOT_A_NODE;
        }
        if (site.dailyLimit() > 0 && !takeDaily(data, player, site)) {
            player.displayClientMessage(ThaiText.c("rotasutils.msg.mine.daily_limit", site.dailyLimit()), true);
            return Break.BLOCKED;
        }
        // Marked spent at once so a second break in the same tick cannot claim the same node twice.
        node.deplete(now, site.respawnSeconds());
        PENDING.add(new Pending(level.dimension(), pos.immutable(), site.depletedBlock(),
                player.getUUID(), site.id(), blockId));
        data.setDirty();
        return Break.WORKED;
    }

    /** Counts one worked node against the player's daily limit for the site. */
    private static boolean takeDaily(RotasData data, ServerPlayer player, MiningSite site) {
        var variables = data.progress(player.getUUID()).questVariables();
        String key = "rpg.mine." + site.id() + "." + SeasonService.today();
        int used;
        try {
            used = Integer.parseInt(variables.getOrDefault(key, "0"));
        } catch (NumberFormatException malformed) {
            used = 0;
        }
        if (used >= site.dailyLimit()) {
            return false;
        }
        // Yesterday's key is dropped as today's is written, so the map never grows with the calendar.
        variables.keySet().removeIf(existing -> existing.startsWith("rpg.mine." + site.id() + ".") && !existing.equals(key));
        variables.put(key, Integer.toString(used + 1));
        return true;
    }

    private static void pay(ServerPlayer player, RotasData data, MiningSite site, String blockId) {
        Random random = new Random(player.getRandom().nextLong());
        List<ItemStack> extras = new ArrayList<>();
        long gold = site.goldMin() + (site.goldMax() > site.goldMin()
                ? random.nextLong(site.goldMax() - site.goldMin() + 1) : 0);
        if (gold > 0) {
            extras.add(net.schwarz.rotasutils.item.GoldCoins.stack(gold));
        }
        for (String line : site.loot()) {
            ItemStack stack = DropLoot.parse(line, random);
            if (!stack.isEmpty()) {
                extras.add(stack);
            }
        }
        extras.removeIf(stack -> DropFilterService.blocked(data, "", DropFilterService.id(stack)));
        if (!extras.isEmpty()) {
            LootService.deliver(player, extras);
        }
        if (site.xp() > 0) {
            ProgressService.awardFromSource(player, data, net.schwarz.rotasutils.level.XpSource.MINING,
                    blockId, site.xp());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.6f, 1.2f);
        RotasNetwork.syncProgress(player);
    }

    /** Once a tick: swaps freshly mined nodes to their depleted look, and once a second, brings nodes back. */
    public static void tick(MinecraftServer server, RotasData data) {
        if (!PENDING.isEmpty()) {
            for (Pending pending : List.copyOf(PENDING)) {
                ServerLevel level = server.getLevel(pending.dimension());
                MiningSite site = data.miningSites().get(pending.site());
                if (level == null || site == null || !level.isLoaded(pending.pos())) {
                    continue;
                }
                MiningSite.Node node = site.node(pending.pos().asLong());
                if (!level.getBlockState(pending.pos()).isAir()) {
                    // Something cancelled the break after the event: the ore is still there, so the node
                    // is full again and nothing is paid.
                    if (node != null) {
                        node.restore();
                    }
                    continue;
                }
                level.setBlockAndUpdate(pending.pos(), block(pending.depleted(), Blocks.COBBLESTONE).defaultBlockState());
                ServerPlayer miner = server.getPlayerList().getPlayer(pending.player());
                if (miner != null) {
                    pay(miner, data, site, pending.block());
                }
            }
            PENDING.clear();
            data.setDirty();
        }
        if (++tick % 20 != 0 || data.miningSites().isEmpty()) {
            return;
        }
        long now = now();
        for (MiningSite site : data.miningSites().values()) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(site.dimension()));
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                continue;
            }
            Block depleted = block(site.depletedBlock(), Blocks.COBBLESTONE);
            for (MiningSite.Node node : site.nodes()) {
                if (!node.due(now)) {
                    continue;
                }
                BlockPos pos = BlockPos.of(node.pos());
                if (!level.isLoaded(pos)) {
                    // An unloaded node comes back the next time its chunk is here; the timer has already run.
                    continue;
                }
                BlockState current = level.getBlockState(pos);
                // Only put the ore back over the depleted block or empty air - never over something a
                // player built there in the meantime.
                if (current.is(depleted) || current.isAir()) {
                    level.setBlockAndUpdate(pos, block(node.block(), Blocks.STONE).defaultBlockState());
                    level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
                }
                node.restore();
                data.setDirty();
            }
        }
    }

    private static Block block(String id, Block fallback) {
        ResourceLocation location = ResourceLocation.tryParse(id == null ? "" : id);
        if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
            return fallback;
        }
        return BuiltInRegistries.BLOCK.get(location);
    }

    // Administration ---------------------------------------------------------------------------------

    /** Registers every block of one kind within a radius as nodes of a site. Returns how many were added. */
    public static int scan(ServerLevel level, MiningSite site, BlockPos centre, int radius, String blockId) {
        Block target = block(blockId, null);
        if (target == null) {
            return 0;
        }
        int added = 0;
        int r = Math.max(1, Math.min(16, radius));
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (site.nodes().size() >= MiningSite.MAX_NODES) {
                break;
            }
            if (level.getBlockState(pos).is(target) && site.addNode(pos.asLong(), blockId)) {
                added++;
            }
        }
        return added;
    }

    /** Puts every spent node of a site back at once, for an operator resetting a site. */
    public static int refill(ServerLevel level, MiningSite site) {
        int restored = 0;
        for (MiningSite.Node node : site.nodes()) {
            if (node.respawnAt() > 0) {
                BlockPos pos = BlockPos.of(node.pos());
                if (level.isLoaded(pos)) {
                    level.setBlockAndUpdate(pos, block(node.block(), Blocks.STONE).defaultBlockState());
                }
                node.restore();
                restored++;
            }
        }
        return restored;
    }

    /** One short status line per site, for the list command. */
    public static String describe(MiningSite site) {
        long now = now();
        return ThaiText.t("rotasutils.cmd.mine.line", site.id(), site.nodes().size(),
                site.depletedCount(now), site.respawnSeconds(), site.dimension());
    }

    /** A chat tint for the admin feedback. */
    public static ChatFormatting colour(boolean ok) {
        return ok ? ChatFormatting.GREEN : ChatFormatting.RED;
    }
}
