package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.DailyService;
import net.schwarz.rotasutils.server.SeasonTrackService;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * {@code /rotas daily} and {@code /rotas pass}: the journey page, a claim without it, and an
 * operator's reset for testing a track.
 */
final class JourneyCommands {
    private JourneyCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("daily")
                .executes(context -> {
                    RotasNetwork.openJourney(context.getSource().getPlayerOrException(), "TODAY");
                    return 1;
                })
                .then(Commands.literal("claim")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 30)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var result = DailyService.claim(player, RotasData.get(player.server),
                                    IntegerArgumentType.getInteger(context, "tier") - 1);
                            return report(context.getSource(), result.success(), result.message());
                        })))
                .then(Commands.literal("reset").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", EntityArgument.player()).executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            RotasData data = RotasData.get(target.server);
                            DailyService.reset(data.progress(target.getUUID()));
                            data.setDirty();
                            RotasNetwork.syncProgress(target);
                            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.daily.reset",
                                    target.getGameProfile().getName()), true);
                            return 1;
                        }))));
        root.then(Commands.literal("pass")
                .executes(context -> {
                    RotasNetwork.openJourney(context.getSource().getPlayerOrException(), "SEASON");
                    return 1;
                })
                .then(Commands.literal("claim")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 30)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var result = SeasonTrackService.claim(player, RotasData.get(player.server),
                                    IntegerArgumentType.getInteger(context, "tier") - 1);
                            return report(context.getSource(), result.success(), result.message());
                        }))));
    }

    private static int report(CommandSourceStack source, boolean success, String message) {
        if (success) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
