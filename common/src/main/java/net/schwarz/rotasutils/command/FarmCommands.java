package net.schwarz.rotasutils.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.FarmingService;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * {@code /rotas book}, {@code /rotas salvage} and {@code /rotas luck}: the monster book, the salvage
 * bench, and what the player's luck and combo are worth right now.
 */
final class FarmCommands {
    private FarmCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("book")
                .executes(context -> {
                    RotasNetwork.openBestiary(context.getSource().getPlayerOrException(), "");
                    return 1;
                })
                .then(Commands.argument("entity", ResourceLocationArgument.id()).executes(context -> {
                    RotasNetwork.openBestiary(context.getSource().getPlayerOrException(),
                            ResourceLocationArgument.getId(context, "entity").toString());
                    return 1;
                })));
        root.then(Commands.literal("salvage").executes(context -> {
            RotasNetwork.openSalvage(context.getSource().getPlayerOrException());
            return 1;
        }));
        root.then(Commands.literal("luck").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            RotasData data = RotasData.get(player.server);
            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.luck",
                    String.format(java.util.Locale.ROOT, "%.1f", FarmingService.luck(player)),
                    Math.round((FarmingService.lootLuck(player, data) - 1) * 100),
                    Math.round((FarmingService.cardLuck(player, data) - 1) * 100),
                    FarmingService.combo(player, data)), false);
            return 1;
        }));
    }
}
