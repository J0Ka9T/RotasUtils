package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneRuleResolver;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.RequirementChecker;
import net.schwarz.rotasutils.server.RewardService;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.server.ZoneGateService;
import net.schwarz.rotasutils.server.ZoneService;
import net.schwarz.rotasutils.server.ZoneWandService;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;

/** Command shortcuts for the level zones the Zone Wand and the admin screen author. */
final class ZoneCommands {
    private ZoneCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("zone")
                .requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.VIEW))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("here").executes(context -> here(context.getSource())))
                .then(Commands.literal("inspect").then(Commands.argument("zone", StringArgumentType.word())
                        .suggests((context,builder)->{ RotasData.get(context.getSource().getServer()).zones().keySet().forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(context -> inspect(context.getSource(), StringArgumentType.getString(context,"zone")))))
                .then(Commands.literal("validate").then(Commands.argument("zone", StringArgumentType.word())
                        .suggests((context,builder)->{ RotasData.get(context.getSource().getServer()).zones().keySet().forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(context -> inspect(context.getSource(), StringArgumentType.getString(context,"zone")))))
                .then(Commands.literal("wand").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                        .executes(context -> wand(context.getSource()))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(ZoneWandService.MIN_RADIUS, ZoneWandService.MAX_RADIUS))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ZoneWandService.setRadius(player, IntegerArgumentType.getInteger(context, "radius"));
                                    return wand(context.getSource());
                                })))
                .then(Commands.literal("points").then(Commands.argument("zone", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            RotasData.get(context.getSource().getServer()).zones().keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> points(context.getSource(), StringArgumentType.getString(context, "zone")))
                        .then(Commands.literal("respawn").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                                .then(Commands.argument("point", StringArgumentType.word())
                                        .executes(context -> respawnPoint(context.getSource(),
                                                StringArgumentType.getString(context, "zone"), StringArgumentType.getString(context, "point")))))))
                .then(Commands.literal("gate")
                        .then(Commands.literal("check").then(Commands.argument("zone", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    RotasData.get(context.getSource().getServer()).zones().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> gateCheck(context.getSource(), StringArgumentType.getString(context, "zone"),
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> gateCheck(context.getSource(), StringArgumentType.getString(context, "zone"),
                                                EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("test").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                                .executes(context -> gateTest(context.getSource()))))
                .then(Commands.literal("remove").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    RotasData.get(context.getSource().getServer()).zones().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "zone"))))));
    }

    private static int list(CommandSourceStack source) {
        RotasData data = RotasData.get(source.getServer());
        if (data.zones().isEmpty()) {
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.none")), false);
            return 0;
        }
        data.zones().values().stream().sorted(ZoneDef::compare).forEach(zone ->
                source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.list_line", zone.id(), zone.name(),
                        zone.dimension(), zone.levelLabel(), zone.danger().label(), zone.xpMultiplier(), zone.priority(), zone.areaLabel())
                        + (zone.safe() ? ThaiText.t("rotasutils.cmd.zone.safe_suffix") : "")
                        + (zone.hasEntryLock() ? ThaiText.t("rotasutils.cmd.zone.locked_suffix") : "")
                        + (zone.enabled() ? "" : ThaiText.t("rotasutils.cmd.zone.disabled_suffix"))), false));
        return data.zones().size();
    }

    private static int here(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RotasData data = RotasData.get(player.server);
        var region = ZoneService.region(data, (ServerLevel) player.level(), player.getX(), player.getY(), player.getZ());
        var rules = ZoneRuleResolver.resolve(data.zones().values(), player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ());
        String where;
        if (!region.wilderness()) {
            where = ThaiText.t("rotasutils.cmd.zone.where_zone", region.id(), region.name(), region.danger().label())
                    + (region.safe() ? ThaiText.t("rotasutils.cmd.zone.where_safe") : "")
                    + ThaiText.t("rotasutils.cmd.zone.where_recommended", region.recommendedMin(), region.recommendedMax(), region.xpMultiplier());
        } else if (!region.nearZone().isEmpty()) {
            where = ThaiText.t("rotasutils.cmd.zone.where_edge", region.nearZone(), Math.round(region.blend() * 100));
        } else {
            where = ThaiText.t("rotasutils.cmd.zone.where_wild");
        }
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.here", where, region.bandLabel(),
                Math.round(region.distance()))), false);
        String global = ThaiText.t("rotasutils.cmd.zone.global");
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.rules",
                rules.identityZoneId().isEmpty() ? global : rules.identityZoneId(),
                rules.pvpEnabled().map(Object::toString).orElse(global), rules.source(net.schwarz.rotasutils.core.ResolvedZoneRules.PVP),
                rules.hostileSpawningEnabled().map(Object::toString).orElse(global),
                rules.source(net.schwarz.rotasutils.core.ResolvedZoneRules.HOSTILE_SPAWNING))), false);
        ZoneDef top = ZoneService.select(data.zones().values(), player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ());
        if (top != null) {
            var features = top.features();
            List<String> moves = new ArrayList<>();
            if (features.movement().noElytra()) moves.add("no elytra");
            if (features.movement().noFlight()) moves.add("no flight");
            if (features.movement().noEnderPearlIn()) moves.add("no pearls");
            String movement = moves.isEmpty() ? "-" : String.join(", ", moves);
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.features", features.type().label(),
                    features.isolateMobs(), features.spawnPoints().size(), features.effects().size(), movement)), false);
        }
        return 1;
    }

    private static int inspect(CommandSourceStack source, String id) {
        ZoneDef zone=RotasData.get(source.getServer()).zone(id);
        if (zone==null) { source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.zone.unknown", id))); return 0; }
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.inspect", zone.id(), zone.revision(),
                zone.levelLabel(), zone.priority(), zone.areas().size(), zone.excludedAreas().size(), zone.combatRules())), false);
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.valid")
                + (zone.areas().isEmpty() ? ThaiText.t("rotasutils.cmd.zone.valid_warning") : "")), false);
        return 1;
    }

    private static int wand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack wand = new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.ZONE_WAND.get());
        if (player.getInventory().contains(wand)) {
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.has_wand",
                    ZoneWandService.radius(player))), false);
            return 1;
        }
        RewardService.give(player, wand);
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.wand_added",
                ZoneWandService.radius(player))), true);
        return 1;
    }

    /** Lists a zone's spawn points with whether their mob is alive or when it returns. */
    private static int points(CommandSourceStack source, String id) {
        RotasData data = RotasData.get(source.getServer());
        ZoneDef zone = data.zone(id);
        if (zone == null) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.zone.unknown", id)));
            return 0;
        }
        if (zone.features().spawnPoints().isEmpty()) {
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.points_none", zone.id())), false);
            return 0;
        }
        long now = source.getServer().overworld().getGameTime();
        for (var point : zone.features().spawnPoints()) {
            var state = data.zoneEncounter(net.schwarz.rotasutils.server.ZoneEncounterService.key(zone.id(), point.id()));
            String status;
            if (state != null && state.mob() != null) {
                status = ThaiText.t("rotasutils.cmd.zone.point_alive");
            } else if (state == null || state.nextSpawnAt() <= now) {
                status = ThaiText.t("rotasutils.cmd.zone.point_ready");
            } else {
                status = ThaiText.t("rotasutils.cmd.zone.point_cooldown", (state.nextSpawnAt() - now + 19) / 20);
            }
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.point_line", point.id(),
                    point.kind(), point.profile(), point.x(), point.y(), point.z(), status)), false);
        }
        return zone.features().spawnPoints().size();
    }

    private static int respawnPoint(CommandSourceStack source, String zoneId, String pointId) {
        RotasData data = RotasData.get(source.getServer());
        boolean ready = data.kernel() != null && data.kernel().encounters().respawnNow(zoneId, pointId);
        if (!ready) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.msg.sa.point_busy")));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.msg.sa.point_respawn")), true);
        return 1;
    }

    /** Lists every entry requirement of a zone with whether the player passes it. */
    private static int gateCheck(CommandSourceStack source, String id, ServerPlayer target) {
        RotasData data = RotasData.get(source.getServer());
        ZoneDef zone = data.zone(id);
        if (zone == null) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.zone.unknown", id)));
            return 0;
        }
        if (zone.entryRequirements().isEmpty()) {
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.gate_open", zone.id())), false);
            return 1;
        }
        var results = RequirementChecker.checkAll(target, data, zone.entryRequirements());
        boolean pass = RequirementChecker.allPass(results);
        String verdict = ThaiText.t(pass ? "rotasutils.cmd.zone.gate_pass" : "rotasutils.cmd.zone.gate_blocked");
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.gate_header", zone.id(),
                target.getGameProfile().getName(), verdict)), false);
        for (var result : results) {
            String mark = result.pass() ? "+" : result.blocking() ? "x" : "~";
            String advisory = result.blocking() ? "" : ThaiText.t("rotasutils.cmd.zone.gate_advisory");
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.gate_line", mark,
                    result.label(), advisory)), false);
        }
        if (target.hasPermissions(2) && !ZoneGateService.adminTesting(target.getUUID())) {
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.gate_admin_bypass")), false);
        }
        return pass ? 1 : 0;
    }

    private static int gateTest(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean testing = ZoneGateService.toggleAdminTest(player.getUUID());
        source.sendSuccess(() -> Component.literal(ThaiText.t(testing
                ? "rotasutils.cmd.zone.gate_test_on" : "rotasutils.cmd.zone.gate_test_off")), false);
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        RotasData data = RotasData.get(source.getServer());
        if (data.zone(id) == null) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.zone.unknown", id)));
            return 0;
        }
        data.removeZone(id);
        data.audit(java.time.Instant.now() + " actor=" + RotasPermissions.actor(source) + " action=zone_remove " + id);
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.zone.removed", id)), true);
        return 1;
    }
}
