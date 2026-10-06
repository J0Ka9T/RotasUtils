package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.ZoneWandItem;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.util.Ids;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ZoneWandService {
    public static final int DEFAULT_RADIUS = 32;
    public static final int MIN_RADIUS = 4;
    public static final int MAX_RADIUS = 256;
    private static final int NEW_ZONE_WIDTH = 4;

    private static final Map<UUID, String> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> RADIUS = new HashMap<>();

    private ZoneWandService() {
    }

    public static void clear() {
        ACTIVE.clear();
        RADIUS.clear();
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);
        RADIUS.remove(player);
    }

    public static int radius(ServerPlayer player) {
        return RADIUS.getOrDefault(player.getUUID(), DEFAULT_RADIUS);
    }

    public static void setRadius(ServerPlayer player, int value) {
        RADIUS.put(player.getUUID(), Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, value)));
    }

    public static String activeZone(ServerPlayer player) {
        return ACTIVE.getOrDefault(player.getUUID(), "");
    }

    public static void setActive(ServerPlayer player, String zoneId) {
        if (zoneId == null || zoneId.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        } else {
            ACTIVE.put(player.getUUID(), zoneId);
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ZoneWandItem) {
                ZoneWandItem.setZone(stack, zoneId);
            }
        }
    }

    public static void clearActive(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void cycleMode(ServerPlayer player, ItemStack wand) {
        if (!authorized(player)) {
            return;
        }
        ZoneWandItem.Mode next = ZoneWandItem.mode(wand).next();
        ZoneWandItem.setMode(wand, next);
        String how = switch (next) {
            case SPHERE -> ThaiText.t("rotasutils.msg.zone.mode_sphere", radius(player));
            case BOX -> ThaiText.t("rotasutils.msg.zone.mode_box");
            case OUTLINE -> ThaiText.t("rotasutils.msg.zone.mode_outline");
        };
        message(player, ThaiText.t("rotasutils.msg.zone.mode_changed", next.label(), how), ChatFormatting.AQUA);
    }

    public static boolean cancelShape(ServerPlayer player, ItemStack wand) {
        if (ZoneWandItem.points(wand).isEmpty()) {
            return false;
        }
        ZoneWandItem.setPoints(wand, List.of());
        message(player, ThaiText.t("rotasutils.msg.zone.shape_cancelled",
                ZoneWandItem.mode(wand).label().toLowerCase(Locale.ROOT)), ChatFormatting.YELLOW);
        return true;
    }

    public static void useBlock(ServerPlayer player, ItemStack wand, BlockPos pos) {
        if (!authorized(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int bottom = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 1;
        List<BlockPos> points = ZoneWandItem.points(wand);
        switch (ZoneWandItem.mode(wand)) {
            case SPHERE -> commit(player, wand, level, new ZoneArea.Sphere(pos.getX(), pos.getY(), pos.getZ(), radius(player)));
            case BOX -> {
                if (points.isEmpty()) {
                    ZoneWandItem.setPoints(wand, List.of(pos));
                    message(player, ThaiText.t("rotasutils.msg.zone.box_corner", coords(pos)),
                            ChatFormatting.AQUA);
                    return;
                }
                ZoneWandItem.setPoints(wand, List.of());
                commit(player, wand, level, ZoneWandItem.box(points.get(0), pos, bottom, top));
            }
            case OUTLINE -> {
                boolean finish = ZoneWandItem.closesOutline(points, pos);
                if (!finish && player.isShiftKeyDown() && !points.isEmpty()) {
                    List<BlockPos> withLast = new java.util.ArrayList<>(points);
                    if (!points.get(points.size() - 1).equals(pos)) {
                        withLast.add(pos);
                    }
                    points = withLast;
                    finish = true;
                }
                if (finish) {
                    ZoneArea.Polygon outline = ZoneWandItem.outline(points, bottom, top);
                    if (outline == null) {
                        message(player, ThaiText.t("rotasutils.msg.zone.outline_min"),
                                ChatFormatting.RED);
                        return;
                    }
                    ZoneWandItem.setPoints(wand, List.of());
                    commit(player, wand, level, outline);
                    return;
                }
                if (points.size() >= ZoneArea.MAX_POLYGON_POINTS) {
                    message(player, ThaiText.t("rotasutils.msg.zone.outline_max", ZoneArea.MAX_POLYGON_POINTS),
                            ChatFormatting.RED);
                    return;
                }
                if (!points.isEmpty() && points.get(points.size() - 1).equals(pos)) {
                    return;
                }
                List<BlockPos> next = new java.util.ArrayList<>(points);
                next.add(pos);
                ZoneWandItem.setPoints(wand, next);
                BlockPos first = next.get(0);
                message(player, next.size() >= 3
                        ? ThaiText.t("rotasutils.msg.zone.outline_point_close", next.size(), coords(pos), first.getX(), first.getZ())
                        : ThaiText.t("rotasutils.msg.zone.outline_point", next.size(), coords(pos)), ChatFormatting.AQUA);
            }
        }
    }

    public static void useEntity(ServerPlayer player, LivingEntity target) {
        if (!authorized(player)) {
            return;
        }
        if (!(target instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            message(player, ThaiText.t("rotasutils.msg.zone.mobs_only"), ChatFormatting.RED);
            return;
        }
        MonsterService service = MonsterService.get(mob);
        if (service == null || service.peek(mob) == null) {
            message(player, ThaiText.t("rotasutils.msg.zone.no_level"),
                    ChatFormatting.RED);
            return;
        }
        RotasData data = RotasData.get(player.server);
        ZoneService.Region region = ZoneService.region(data, level, mob.getX(), mob.getY(), mob.getZ());
        var state = service.relevel(mob, ZoneService.levelFor(mob.getUUID(), region), false);
        if (state == null) {
            return;
        }
        message(player, ThaiText.t("rotasutils.msg.zone.releveled", mob.getType().getDescription().getString(),
                state.level(), describe(region)), ChatFormatting.GREEN);
    }

    public static ZoneDef createZone(RotasData data, ServerLevel level, BlockPos center, ZoneArea area) {
        String dimension = level.dimension().location().toString();
        ZoneService.Region here = ZoneService.region(data, level, center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        int min = here.min();
        int max = Math.min(10000, Math.max(here.max(), min + NEW_ZONE_WIDTH));
        ZoneDef parent = here.wilderness() ? null : data.zone(here.id());
        int priority = parent == null ? 0 : Math.min(10000, parent.priority() + 1);
        String biome = level.getBiome(center).unwrapKey().map(key -> key.location().getPath()).orElse("area");
        String name = titleCase(biome);
        String id = Ids.unique("zone_" + biome, data.zones().keySet());
        ZoneDef zone = new ZoneDef(id, name.length() > 64 ? name.substring(0, 64) : name, dimension,
                area == null ? List.of() : List.of(area), min, max, priority, true)
                .withFeatures(net.schwarz.rotasutils.core.ZoneFeatures.DEFAULT.withIsolateMobs(parent != null));
        data.putZone(zone);
        return zone;
    }

    private static void commit(ServerPlayer player, ItemStack wand, ServerLevel level, ZoneArea area) {
        RotasData data = RotasData.get(player.server);
        String dimension = level.dimension().location().toString();
        String activeId = ACTIVE.get(player.getUUID());
        ZoneDef zone = activeId == null ? null : data.zone(activeId);
        ZoneArea.Bounds bounds = area.bounds();
        long footprint = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1);
        if (footprint < 16) {
            message(player, ThaiText.t("rotasutils.msg.zone.tiny", footprint), ChatFormatting.YELLOW);
        }
        if (zone == null || !zone.dimension().equals(dimension)) {
            BlockPos center = new BlockPos((bounds.minX() + bounds.maxX()) / 2,
                    player.blockPosition().getY(), (bounds.minZ() + bounds.maxZ()) / 2);
            zone = createZone(data, level, center, area);
            setActive(player, zone.id());
            ZoneWandItem.setZone(wand, zone.id());
            data.audit(player.getGameProfile().getName() + " started zone " + zone.id() + " with " + area.label());
            syncZones(player);
            message(player, ThaiText.t("rotasutils.msg.zone.started", zone.name(), zone.levelLabel(), area.label()),
                    ChatFormatting.GOLD);
            return;
        }
        if (zone.areas().size() >= ZoneDef.MAX_AREAS) {
            message(player, ThaiText.t("rotasutils.msg.zone.full", zone.name(), ZoneDef.MAX_AREAS), ChatFormatting.RED);
            return;
        }
        ZoneDef updated = zone.withArea(area);
        data.putZone(updated);
        ZoneWandItem.setZone(wand, updated.id());
        data.audit(player.getGameProfile().getName() + " added " + area.label() + " to zone " + updated.id());
        syncZones(player);
        message(player, ThaiText.t("rotasutils.msg.zone.added", updated.name(), updated.levelLabel(), area.label(),
                updated.areas().size(), ZoneDef.MAX_AREAS), ChatFormatting.GREEN);
    }

    static String describe(ZoneService.Region region) {
        if (region.wilderness()) {
            return region.nearZone().isEmpty() ? ThaiText.t("rotasutils.msg.zone.describe_wild", region.bandLabel())
                    : ThaiText.t("rotasutils.msg.zone.describe_edge", region.nearZone(), region.bandLabel());
        }
        return ThaiText.t("rotasutils.msg.zone.describe_zone", region.name(), region.bandLabel(), region.danger().label());
    }

    private static String titleCase(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? ThaiText.t("rotasutils.msg.zone.default_name") : out.toString();
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static boolean authorized(ServerPlayer player) {
        RotasData data = RotasData.get(player.server);
        if (BoardService.isAdmin(player, data)) {
            return true;
        }
        message(player, ThaiText.t("rotasutils.msg.zone.admin_only"), ChatFormatting.RED);
        return false;
    }

    private static void syncZones(ServerPlayer source) {
        for (ServerPlayer online : source.server.getPlayerList().getPlayers()) {
            RotasNetwork.syncContent(online);
        }
    }

    private static void message(ServerPlayer player, String text, ChatFormatting style) {
        player.sendSystemMessage(Component.literal(text).withStyle(style));
    }
}
