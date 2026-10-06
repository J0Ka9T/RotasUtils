package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.nemesis.Nemesis;
import net.schwarz.rotasutils.server.NemesisService;
import net.schwarz.rotasutils.server.SeasonService;
import net.schwarz.rotasutils.server.WorldEventService;
import net.schwarz.rotasutils.util.ThaiText;
import net.schwarz.rotasutils.worldevent.WorldEvent;

final class WorldCommands {
    private WorldCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("nemesis")
                .executes(context -> mine(context))
                .then(Commands.literal("list").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .executes(WorldCommands::all))
                .then(Commands.literal("remove").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            RotasData data = RotasData.get(context.getSource().getServer());
                            return NemesisService.remove(context.getSource().getServer(), data, id)
                                    ? ok(context, "rotasutils.cmd.nemesis.removed", id)
                                    : fail(context, "rotasutils.cmd.nemesis.unknown", id);
                        })))
                .then(Commands.literal("summon").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int id = IntegerArgumentType.getInteger(context, "id");
                            RotasData data = RotasData.get(player.server);
                            Nemesis nemesis = data.nemesis(id);
                            if (nemesis == null) {
                                return fail(context, "rotasutils.cmd.nemesis.unknown", id);
                            }
                            if (NemesisService.body(player.server, nemesis) != null) {
                                return fail(context, "rotasutils.cmd.nemesis.already_here", nemesis.displayName());
                            }
                            var mob = NemesisService.summon(player.serverLevel(), player.blockPosition(), nemesis, data,
                                    SeasonService.rules(data).nemesis);
                            return mob == null ? fail(context, "rotasutils.cmd.nemesis.no_room")
                                    : ok(context, "rotasutils.cmd.nemesis.summoned", nemesis.displayName());
                        }))));

        root.then(Commands.literal("worldevent")
                .executes(context -> events(context))
                .then(Commands.literal("types").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT)).executes(context -> {
                    var rules = SeasonService.rules(RotasData.get(context.getSource().getServer())).worldEvents;
                    rules.types.forEach((id, def) -> context.getSource().sendSuccess(() -> ThaiText.c(
                            "rotasutils.cmd.worldevent.type", id, def.name, def.goal, def.goalCount, def.weight,
                            def.nightOnly ? ThaiText.t("rotasutils.cmd.worldevent.night") : ""), false));
                    return rules.types.size();
                }))
                .then(Commands.literal("start").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .executes(context -> start(context, null, false))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(SeasonService.rules(
                                        RotasData.get(context.getSource().getServer())).worldEvents.types.keySet(), builder))
                                .executes(context -> start(context, StringArgumentType.getString(context, "type"), false))
                                .then(Commands.literal("here")
                                        .executes(context -> start(context, StringArgumentType.getString(context, "type"), true)))))
                .then(Commands.literal("stop").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            return WorldEventService.stop(context.getSource().getServer(),
                                    RotasData.get(context.getSource().getServer()), id)
                                    ? ok(context, "rotasutils.cmd.worldevent.stopped", id)
                                    : fail(context, "rotasutils.cmd.worldevent.unknown", id);
                        }))));
    }

    private static int mine(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RotasData data = RotasData.get(player.server);
        int shown = 0;
        for (Nemesis nemesis : data.nemeses().values()) {
            if (!nemesis.hasVictim(player.getUUID())) {
                continue;
            }
            shown++;
            String where = NemesisService.whereabouts(player.server, player, nemesis);
            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.nemesis.line", nemesis.id(),
                    nemesis.displayName(), nemesis.level(), nemesis.timesKilled(player.getUUID()), where), false);
        }
        if (shown == 0) {
            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.nemesis.none"), false);
        }
        return shown;
    }

    private static int all(CommandContext<CommandSourceStack> context) {
        RotasData data = RotasData.get(context.getSource().getServer());
        for (Nemesis nemesis : data.nemeses().values()) {
            boolean loaded = NemesisService.body(context.getSource().getServer(), nemesis) != null;
            String target = "";
            for (Nemesis.Victim victim : nemesis.victims()) {
                if (victim.id().equals(nemesis.target())) {
                    target = victim.name();
                }
            }
            String hunted = target;
            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.nemesis.admin_line", nemesis.id(),
                    nemesis.displayName(), nemesis.entityType(), nemesis.level(), nemesis.kills(), hunted,
                    ThaiText.t(loaded ? "rotasutils.cmd.nemesis.loaded" : "rotasutils.cmd.nemesis.dormant")), false);
        }
        if (data.nemeses().isEmpty()) {
            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.nemesis.none_server"), false);
        }
        return data.nemeses().size();
    }

    private static int events(CommandContext<CommandSourceStack> context) {
        RotasData data = RotasData.get(context.getSource().getServer());
        ServerPlayer viewer = context.getSource().getPlayer();
        var lines = WorldEventService.describe(viewer, data);
        if (lines.isEmpty()) {
            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.worldevent.none"), false);
        }
        for (Component line : lines) {
            context.getSource().sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    private static int start(CommandContext<CommandSourceStack> context, String type, boolean here) {
        RotasData data = RotasData.get(context.getSource().getServer());
        if (type != null && !SeasonService.rules(data).worldEvents.types.containsKey(type)) {
            return fail(context, "rotasutils.cmd.worldevent.unknown_type", type);
        }
        WorldEvent event = WorldEventService.start(context.getSource().getServer(), data, type,
                context.getSource().getPlayer(), here);
        return event == null ? fail(context, "rotasutils.cmd.worldevent.cannot_start")
                : ok(context, "rotasutils.cmd.worldevent.started", event.id(), WorldEventService.placeName(data, event));
    }

    private static int ok(CommandContext<CommandSourceStack> context, String key, Object... args) {
        RotasData.get(context.getSource().getServer()).audit("world " + key);
        context.getSource().sendSuccess(() -> ThaiText.c(key, args), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.literal(ThaiText.t(key, args)));
        return 0;
    }
}
