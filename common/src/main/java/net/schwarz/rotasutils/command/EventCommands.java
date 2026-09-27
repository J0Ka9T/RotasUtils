package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.event.EventType;
import net.schwarz.rotasutils.server.EventService;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * {@code /rotas events}: the same catalogue the screen shows, for a console or a command block.
 */
final class EventCommands {
    private EventCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("events").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("enable")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(context -> {
                            var server = context.getSource().getServer();
                            boolean on = BoolArgumentType.getBool(context, "on");
                            EventService.setEnabled(server, RotasData.get(server), on);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    ThaiText.t("rotasutils.cmd.events.enabled", on)), true);
                            return 1;
                        })))
                .then(Commands.literal("add")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (EventType type : EventType.values()) {
                                        builder.suggest(type.name());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("filter", StringArgumentType.string()).executes(context -> {
                                    var server = context.getSource().getServer();
                                    EventType type = EventType.byName(StringArgumentType.getString(context, "type"));
                                    String filter = StringArgumentType.getString(context, "filter");
                                    if (!EventService.add(server, RotasData.get(server), type, filter)) {
                                        context.getSource().sendFailure(Component.literal(
                                                ThaiText.t("rotasutils.msg.event.bad_rule")));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            ThaiText.t("rotasutils.msg.event.saved")), true);
                                    return 1;
                                }))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("filter", StringArgumentType.string()).executes(context -> {
                                    var server = context.getSource().getServer();
                                    EventType type = EventType.byName(StringArgumentType.getString(context, "type"));
                                    String filter = StringArgumentType.getString(context, "filter");
                                    if (!EventService.remove(server, RotasData.get(server), type, filter)) {
                                        context.getSource().sendFailure(Component.literal(
                                                ThaiText.t("rotasutils.msg.event.bad_rule")));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            ThaiText.t("rotasutils.msg.event.saved")), true);
                                    return 1;
                                }))))
                .then(Commands.literal("set")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("filter", StringArgumentType.string())
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .then(Commands.argument("xp_multiplier", DoubleArgumentType.doubleArg(0, 1000))
                                                        .executes(context -> {
                                                            var server = context.getSource().getServer();
                                                            var data = RotasData.get(server);
                                                            EventType type = EventType.byName(
                                                                    StringArgumentType.getString(context, "type"));
                                                            String filter = StringArgumentType.getString(context, "filter");
                                                            var existing = EventService.exact(data, type, filter);
                                                            if (existing == null || !EventService.edit(server, data, type, filter,
                                                                    BoolArgumentType.getBool(context, "enabled"),
                                                                    DoubleArgumentType.getDouble(context, "xp_multiplier"),
                                                                    existing.xpFlat, existing.gold,
                                                                    existing.announcement(), existing.cooldownSeconds)) {
                                                                context.getSource().sendFailure(Component.literal(
                                                                        ThaiText.t("rotasutils.msg.event.bad_rule")));
                                                                return 0;
                                                            }
                                                            context.getSource().sendSuccess(() -> Component.literal(
                                                                    ThaiText.t("rotasutils.msg.event.saved")), true);
                                                            return 1;
                                                        })))))));
    }

    private static int list(CommandSourceStack source) {
        RotasData data = RotasData.get(source.getServer());
        var rules = EventService.rules(data);
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.events.list",
                rules.size(), EventService.enabled(data))), false);
        for (var rule : rules) {
            // A rule that changes nothing is not worth a line; the screen shows those.
            if (rule.enabled && rule.xpMultiplier == 1.0 && rule.xpFlat == 0 && rule.gold == 0
                    && "OFF".equals(rule.announce) && rule.filter.isEmpty()) {
                continue;
            }
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.events.line",
                    rule.type, rule.filter.isEmpty() ? "*" : rule.filter,
                    (rule.enabled ? "x" + rule.xpMultiplier + " +" + rule.xpFlat + "xp +" + rule.gold + "g "
                            + rule.announce : "off"))), false);
        }
        return 1;
    }
}
