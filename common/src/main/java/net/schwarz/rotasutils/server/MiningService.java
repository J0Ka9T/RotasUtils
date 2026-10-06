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

public final class MiningService {
    public enum Break { NOT_A_NODE, WORKED, BLOCKED }

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, String depleted,
                           java.util.UUID player, String site, String block, boolean rich) {
    }

    private static final net.minecraft.tags.TagKey<Block> ORES =
            net.minecraft.tags.TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "ores"));

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
            return Break.NOT_A_NODE;
        }
        if (site.minTier() > 0 && toolTier(player) < site.minTier()) {
            player.displayClientMessage(ThaiText.c("rotasutils.msg.mine.tool", ThaiText.t("rotasutils.mine.tier." + site.minTier())), true);
            return Break.BLOCKED;
        }
        if (site.dailyLimit() > 0 && !takeDaily(data, player, site)) {
            player.displayClientMessage(ThaiText.c("rotasutils.msg.mine.daily_limit", site.dailyLimit()), true);
            return Break.BLOCKED;
        }
        node.deplete(now, site.respawnSeconds());
        PENDING.add(new Pending(level.dimension(), pos.immutable(), site.depletedBlock(),
                player.getUUID(), site.id(), blockId, node.rich()));
        data.setDirty();
        return Break.WORKED;
    }

    static int toolTier(ServerPlayer player) {
        var item = player.getMainHandItem().getItem();
        return item instanceof net.minecraft.world.item.TieredItem tiered ? tiered.getTier().getLevel() : 0;
    }

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
        variables.keySet().removeIf(existing -> existing.startsWith("rpg.mine." + site.id() + ".") && !existing.equals(key));
        variables.put(key, Integer.toString(used + 1));
        return true;
    }

    private static void pay(ServerPlayer player, RotasData data, MiningSite site, String blockId, boolean rich) {
        Random random = new Random(player.getRandom().nextLong());
        int times = rich ? site.richMultiplier() : 1;
        boolean miner = "miner".equals(data.progress(player.getUUID()).subJob());
        double factor = times * (miner ? 1 + site.minerBonus() / 100.0 : 1);
        List<ItemStack> extras = new ArrayList<>();
        long gold = site.goldMin() + (site.goldMax() > site.goldMin()
                ? random.nextLong(site.goldMax() - site.goldMin() + 1) : 0);
        gold = Math.round(gold * factor);
        if (gold > 0) {
            extras.add(net.schwarz.rotasutils.item.GoldCoins.stack(gold));
        }
        for (int roll = 0; roll < times; roll++) {
            for (String line : site.loot()) {
                ItemStack stack = DropLoot.parse(line, random);
                if (!stack.isEmpty()) {
                    extras.add(stack);
                }
            }
        }
        extras.removeIf(stack -> DropFilterService.blocked(data, "", DropFilterService.id(stack)));
        extras = TradeService.starAny(player, extras);
        if (!extras.isEmpty()) {
            LootService.deliver(player, extras);
        }
        if (site.xp() > 0) {
            ProgressService.awardFromSource(player, data, net.schwarz.rotasutils.level.XpSource.MINING,
                    blockId, Math.round(site.xp() * factor));
        }
        TitleService.stat(player, data, "nodes", 1);
        if (rich) {
            TitleService.stat(player, data, "rich_nodes", 1);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, rich ? 1.0f : 0.6f, rich ? 1.6f : 1.2f);
        if (rich) {
            player.displayClientMessage(ThaiText.c("rotasutils.msg.mine.rich", times), true);
        }
        RotasNetwork.syncProgress(player);
    }

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
                    if (node != null) {
                        node.restore();
                    }
                    continue;
                }
                level.setBlockAndUpdate(pending.pos(), block(pending.depleted(), Blocks.COBBLESTONE).defaultBlockState());
                ServerPlayer miner = server.getPlayerList().getPlayer(pending.player());
                if (miner != null) {
                    pay(miner, data, site, pending.block(), pending.rich());
                }
            }
            PENDING.clear();
            data.setDirty();
        }
        if (++tick % 20 != 0 || data.miningSites().isEmpty()) {
            return;
        }
        if (tick % 40 == 0) {
            sparkle(server, data);
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
                    continue;
                }
                BlockState current = level.getBlockState(pos);
                if (current.is(depleted) || current.isAir()) {
                    level.setBlockAndUpdate(pos, block(node.block(), Blocks.STONE).defaultBlockState());
                    level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
                }
                node.restore();
                node.setRich(site.rollRich(level.random));
                data.setDirty();
            }
        }
    }

    private static void sparkle(MinecraftServer server, RotasData data) {
        long now = now();
        for (MiningSite site : data.miningSites().values()) {
            if (!site.sparkle() || site.richChance() <= 0) {
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(site.dimension())));
            if (level == null || level.players().isEmpty()) {
                continue;
            }
            for (MiningSite.Node node : site.nodes()) {
                if (!node.rich() || node.depleted(now)) {
                    continue;
                }
                BlockPos pos = BlockPos.of(node.pos());
                if (level.isLoaded(pos) && level.hasNearbyAlivePlayer(pos.getX(), pos.getY(), pos.getZ(), 28)) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1,
                            pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
                }
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

    public static int scanOres(ServerLevel level, MiningSite site, BlockPos centre, int radius) {
        int added = 0;
        int r = Math.max(1, Math.min(24, radius));
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (site.nodes().size() >= MiningSite.MAX_NODES) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(ORES) && site.addNode(pos.asLong(), String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())))) {
                added++;
            }
        }
        return added;
    }

    public static MiningSite quick(ServerPlayer admin, RotasData data, int radius, String preset) {
        String id;
        int n = 1;
        do {
            id = "mine_" + n++;
        } while (data.miningSites().containsKey(id));
        MiningSite site = new MiningSite(id, admin.level().dimension().location().toString());
        site.setName("Mine " + (n - 1));
        net.schwarz.rotasutils.mine.MinePresets.apply(site, preset);
        int added = scanOres(admin.serverLevel(), site, admin.blockPosition(), radius);
        if (added == 0) {
            RotasNetwork.feedback(admin, false, ThaiText.t("rotasutils.mine.quick_none", radius));
            return null;
        }
        data.putMiningSite(site);
        data.setDirty();
        RotasNetwork.feedback(admin, true, ThaiText.t("rotasutils.mine.quick_done", site.name(), added));
        return site;
    }

    public static int refill(ServerLevel level, MiningSite site) {
        int restored = 0;
        for (MiningSite.Node node : site.nodes()) {
            if (node.respawnAt() > 0) {
                BlockPos pos = BlockPos.of(node.pos());
                if (level.isLoaded(pos)) {
                    level.setBlockAndUpdate(pos, block(node.block(), Blocks.STONE).defaultBlockState());
                }
                node.restore();
                node.setRich(site.rollRich(level.random));
                restored++;
            }
        }
        return restored;
    }

    public static String describe(MiningSite site) {
        long now = now();
        return ThaiText.t("rotasutils.cmd.mine.line", site.id(), site.nodes().size(),
                site.depletedCount(now), site.respawnSeconds(), site.dimension());
    }

    public static ChatFormatting colour(boolean ok) {
        return ok ? ChatFormatting.GREEN : ChatFormatting.RED;
    }
}
