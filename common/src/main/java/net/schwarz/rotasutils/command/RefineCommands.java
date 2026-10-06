package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.RefineService;
import net.schwarz.rotasutils.util.ThaiText;

final class RefineCommands {
    private RefineCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("refine")
                .executes(context -> open(context.getSource().getPlayerOrException()))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("try")
                        .executes(context -> attempt(context.getSource(), RefineService.Options.PLAIN))
                        .then(Commands.argument("enriched", BoolArgumentType.bool())
                                .then(Commands.argument("protection", BoolArgumentType.bool())
                                        .then(Commands.argument("blessing", BoolArgumentType.bool())
                                                .executes(context -> attempt(context.getSource(), new RefineService.Options(
                                                        BoolArgumentType.getBool(context, "enriched"),
                                                        BoolArgumentType.getBool(context, "protection"),
                                                        BoolArgumentType.getBool(context, "blessing"),
                                                        false)))))))
                .then(Commands.literal("set").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                .executes(context -> set(context.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"))))));
    }

    private static int open(ServerPlayer player) {
        RotasNetwork.openRefine(player);
        return 1;
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RefineService.Quote quote = RefineService.quote(player, RotasData.get(player.server), RefineService.Options.PLAIN);
        if (!quote.possible()) {
            source.sendFailure(Component.literal(quote.message()));
            return 0;
        }
        source.sendSuccess(() -> ThaiText.c("rotasutils.cmd.refine.info", quote.level(), quote.target(),
                Math.round(quote.chance() * 100), quote.cost()), false);
        return 1;
    }

    private static int attempt(CommandSourceStack source, RefineService.Options options)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RefineService.Outcome outcome = RefineService.refine(player, RotasData.get(player.server), options);
        if (!outcome.started()) {
            source.sendFailure(Component.literal(outcome.message()));
            return 0;
        }
        return outcome.result().success() ? 1 : 0;
    }

    private static int set(CommandSourceStack source, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var stack = player.getMainHandItem();
        if (!net.schwarz.rotasutils.item.ItemRefine.categoryOf(stack).refinable()) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.msg.refine.not_refinable")));
            return 0;
        }
        net.schwarz.rotasutils.item.ItemRefine.setLevel(stack, level);
        RotasData.get(player.server).audit(player.getGameProfile().getName() + " refine set " + level);
        source.sendSuccess(() -> ThaiText.c("rotasutils.cmd.refine.set", level), true);
        return 1;
    }
}
