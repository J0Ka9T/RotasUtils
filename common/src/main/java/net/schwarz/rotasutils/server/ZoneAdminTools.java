package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneLint;
import net.schwarz.rotasutils.data.RotasData;

public final class ZoneAdminTools {
    private ZoneAdminTools() {
    }

    public record Result(boolean success, String message) {
    }

    public static Vec3 centreOf(ZoneDef zone) {
        if (zone.areas().isEmpty()) {
            return null;
        }
        var b = zone.areas().get(0).bounds();
        return new Vec3((b.minX() + b.maxX()) / 2.0 + 0.5, (b.minY() + b.maxY()) / 2.0, (b.minZ() + b.maxZ()) / 2.0 + 0.5);
    }

    public static Result goTo(ServerPlayer player, ZoneDef zone) {
        Vec3 centre = centreOf(zone);
        if (centre == null) {
            return new Result(false, "That zone covers the whole dimension; there is nowhere in particular to go.");
        }
        String dimension = zone.dimension().isEmpty() || "*".equals(zone.dimension()) ? player.level().dimension().location().toString() : zone.dimension();
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension)));
        if (level == null) {
            return new Result(false, "That zone's world is not loaded.");
        }
        int x = (int) Math.floor(centre.x), z = (int) Math.floor(centre.z);
        int y = level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : (int) centre.y;
        player.teleportTo(level, centre.x, y, centre.z, player.getYRot(), player.getXRot());
        return new Result(true, "Teleported to " + zone.name() + ".");
    }

    public static void sendLint(ServerPlayer player, ZoneDef zone, java.util.Collection<ZoneDef> all, int limit) {
        var findings = ZoneLint.check(zone, all);
        for (int i = 0; i < Math.min(limit, findings.size()); i++) {
            var finding = findings.get(i);
            ChatFormatting color = switch (finding.level()) {
                case ERROR -> ChatFormatting.RED;
                case WARNING -> ChatFormatting.GOLD;
                case INFO -> ChatFormatting.GRAY;
            };
            player.sendSystemMessage(Component.literal(finding.level().name().charAt(0) + " " + zone.id() + ": "
                    + finding.message()).withStyle(color));
        }
        if (findings.size() > limit) {
            player.sendSystemMessage(Component.literal("+" + (findings.size() - limit) + " more: /rotas zone inspect "
                    + zone.id()).withStyle(ChatFormatting.GRAY));
        }
    }

    public static Result setEnabled(RotasData data, ZoneDef zone, boolean on, String actor) {
        if (zone.enabled() == on) {
            return new Result(true, zone.name() + " is already " + (on ? "on" : "off") + ".");
        }
        data.putZone(zone.withSettings(zone.name(), zone.levelMin(), zone.levelMax(), zone.priority(), on, zone.danger(), zone.recommendedMin(),
                zone.recommendedMax(), zone.xpMultiplier(), zone.transitionBlocks(), zone.safe()));
        data.audit(java.time.Instant.now() + " actor=" + actor + " action=zone_" + (on ? "enable " : "disable ") + zone.id());
        return new Result(true, zone.name() + " is now " + (on ? "on" : "off") + ".");
    }
}
