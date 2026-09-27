package net.schwarz.rotasutils.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.horse.HorseService;
import net.schwarz.rotasutils.util.ThaiText;

/** {@code /horse} (and {@code /rotas horse}): open the stable, the draw, the leaderboard, or get a whistle. */
final class HorseCommands {
    private HorseCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return build(Commands.literal("horse"));
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(build(Commands.literal("horse")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> node) {
        return node
                .executes(context -> {
                    HorseService.open(context.getSource().getPlayerOrException(), "", "STABLE", null);
                    return 1;
                })
                .then(Commands.literal("gacha").executes(context -> {
                    HorseService.open(context.getSource().getPlayerOrException(), "", "DRAW", null);
                    return 1;
                }))
                .then(Commands.literal("top").executes(context -> {
                    var lines = HorseService.leaderboard(context.getSource().getServer(), 10);
                    if (lines.isEmpty()) {
                        context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.msg.horse.top_empty")), false);
                    }
                    lines.forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    return 1;
                }))
                .then(Commands.literal("whistle").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (player.getInventory().contains(new ItemStack(RotasRegistry.HORSE_WHISTLE.get()))) {
                        context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.msg.horse.has_whistle")));
                        return 0;
                    }
                    player.getInventory().placeItemBackInInventory(new ItemStack(RotasRegistry.HORSE_WHISTLE.get()));
                    context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.msg.horse.whistle_given")), false);
                    return 1;
                }));
    }
}
