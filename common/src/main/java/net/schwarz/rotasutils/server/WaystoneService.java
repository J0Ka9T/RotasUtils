package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;
import net.schwarz.rotasutils.waystone.Waystone;

import java.util.ArrayList;
import java.util.List;

public final class WaystoneService {
    private static final long MAX_WARP_COST = 100_000;

    private WaystoneService() {
    }

public static void onPlaced(Level level, BlockPos pos, ServerPlayer placer) {
        if (level.isClientSide || level.getServer() == null) {
            return;
        }
        RotasData data = RotasData.get(level.getServer());
        if (!data.serverSettings().waystonesEnabled()) {
            return;
        }
        String id = Waystone.idOf(level, pos);
        if (data.waystone(id) != null) {
            return;
        }
        try {
            data.putWaystone(new Waystone(id, nextName(data), level.dimension().location().toString(), pos));
        } catch (IllegalStateException full) {
            if (placer != null) {
                RotasNetwork.feedback(placer, false, ThaiText.t("rotasutils.msg.waystone.limit"));
            }
            return;
        }
        if (placer != null) {
            RotasNetwork.feedback(placer, true, ThaiText.t("rotasutils.msg.waystone.placed",
                    data.waystone(id).name()));
        }
    }

    private static String nextName(RotasData data) {
        for (int number = 1; number <= RotasData.WAYSTONE_LIMIT + 1; number++) {
            String candidate = ThaiText.t("rotasutils.waystone.default_name", number);
            boolean taken = false;
            for (Waystone existing : data.waystones().values()) {
                if (existing.name().equals(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
        return ThaiText.t("rotasutils.waystone.default_name", data.waystones().size() + 1);
    }

    public static void onBroken(Level level, BlockPos pos) {
        if (level.isClientSide || level.getServer() == null) {
            return;
        }
        RotasData.get(level.getServer()).removeWaystone(Waystone.idOf(level, pos));
    }

public static void onUse(ServerPlayer player, Level level, BlockPos pos) {
        RotasData data = RotasData.get(player.server);
        if (!data.serverSettings().waystonesEnabled()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.disabled"));
            return;
        }
        String id = Waystone.idOf(level, pos);
        Waystone waystone = data.waystone(id);
        if (waystone == null) {
            onPlaced(level, pos, null);
            waystone = data.waystone(id);
            if (waystone == null) {
                RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.limit"));
                return;
            }
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (!progress.knowsWaystone(id)) {
            long cost = data.serverSettings().waystoneDiscoverCost();
            if (!charge(player, data, progress, cost)) {
                RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.poor_record", cost));
                return;
            }
            progress.discoverWaystone(id);
            data.setDirty();
            ExplorationService.onWaystoneDiscovered(player, data, waystone.name());
            RotasNetwork.syncProgress(player);
            RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.waystone.recorded", waystone.name()));
        }
        open(player, id);
    }

    public static void open(ServerPlayer player, String standingOn) {
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        CompoundTag payload = new CompoundTag();
        payload.putString("here", standingOn == null ? "" : standingOn);
        payload.putLong("gold", progress.rpg().currency(GoldCoinService.CURRENCY));
        payload.putLong("record_cost", data.serverSettings().waystoneDiscoverCost());
        payload.putBoolean("admin", BoardService.isAdmin(player, data));
        ListTag rows = new ListTag();
        for (String id : known(data, progress)) {
            Waystone waystone = data.waystone(id);
            CompoundTag row = new CompoundTag();
            row.putString("id", waystone.id());
            row.putString("name", waystone.name());
            row.putString("dimension", waystone.dimension());
            boolean sameDimension = waystone.dimension().equals(player.level().dimension().location().toString());
            row.putBoolean("same_dimension", sameDimension);
            row.putInt("distance", sameDimension
                    ? (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(
                            player.blockPosition().distSqr(waystone.pos())))) : -1);
            row.putLong("cost", warpCost(data, player, waystone));
            row.putBoolean("here", waystone.id().equals(standingOn));
            rows.add(row);
        }
        payload.put("rows", rows);
        RotasNetwork.openScreen(player, "waystone", payload);
    }

    private static List<String> known(RotasData data, PlayerProgress progress) {
        List<String> ids = new ArrayList<>();
        for (String id : List.copyOf(progress.waystones())) {
            if (data.waystone(id) == null) {
                progress.forgetWaystone(id);
            } else {
                ids.add(id);
            }
        }
        ids.sort((left, right) -> data.waystone(left).name().compareTo(data.waystone(right).name()));
        return ids;
    }

    public static long warpCost(RotasData data, ServerPlayer player, Waystone target) {
        long flat = data.serverSettings().waystoneWarpCost();
        if (!target.dimension().equals(player.level().dimension().location().toString())) {
            return flat;
        }
        double blocks = Math.sqrt(player.blockPosition().distSqr(target.pos()));
        long distance = Math.round(blocks / 1000.0 * data.serverSettings().waystoneWarpCostPerThousandBlocks());
        return Math.min(MAX_WARP_COST, flat + Math.max(0, distance));
    }

public static void warp(ServerPlayer player, String id) {
        RotasData data = RotasData.get(player.server);
        if (!data.serverSettings().waystonesEnabled()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.disabled"));
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        Waystone target = data.waystone(id);
        if (target == null || !progress.knowsWaystone(id)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.unknown"));
            return;
        }
        ResourceLocation dimensionId = target.dimensionId();
        ServerLevel level = dimensionId == null ? null : player.server.getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
        if (level == null) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.no_world"));
            return;
        }
        if (!level.getBlockState(target.pos()).is(net.schwarz.rotasutils.registry.RotasRegistry.WAYSTONE.get())) {
            data.removeWaystone(id);
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.gone"));
            return;
        }
        if (target.dimension().equals(player.level().dimension().location().toString())
                && player.blockPosition().distSqr(target.pos()) <= 9) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.already_here"));
            return;
        }
        long cost = warpCost(data, player, target);
        if (!charge(player, data, progress, cost)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.poor_warp", cost));
            return;
        }
        BlockPos landing = landing(level, target.pos());
        player.teleportTo(level, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.playSound(null, landing, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
        RotasNetwork.syncProgress(player);
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.waystone.warped", target.name()));
        open(player, target.id());
    }

    private static BlockPos landing(ServerLevel level, BlockPos pillar) {
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos candidate = pillar.relative(side);
            if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        return pillar.above(2);
    }

public static void rename(ServerPlayer player, String id, String name) {
        RotasData data = RotasData.get(player.server);
        if (!BoardService.isAdmin(player, data)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.admin_only"));
            return;
        }
        Waystone waystone = data.waystone(id);
        String trimmed = name == null ? "" : name.trim();
        if (waystone == null || trimmed.isEmpty() || trimmed.length() > Waystone.MAX_NAME) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.waystone.bad_name", Waystone.MAX_NAME));
            return;
        }
        data.putWaystone(waystone.renamed(trimmed));
        data.audit(player.getGameProfile().getName() + " renamed waystone " + id + " to " + trimmed);
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.waystone.renamed", trimmed));
        open(player, id);
    }

private static boolean charge(ServerPlayer player, RotasData data, PlayerProgress progress, long cost) {
        if (cost <= 0) {
            return true;
        }
        if (progress.rpg().currency(GoldCoinService.CURRENCY) < cost) {
            return false;
        }
        progress.rpg().currency(GoldCoinService.CURRENCY, -cost);
        progress.markDirty();
        data.setDirty();
        return true;
    }
}
