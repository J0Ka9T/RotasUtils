package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.util.ThaiText;

final class ProgressionCommands {
    private ProgressionCommands() { }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("stats").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            var data = RotasData.get(player.server);
            if (data.kernel() == null) { return 0; }
            context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.stats.points",
                    data.progress(player.getUUID()).rpg().statPoints(), data.kernel().stats(player))), false);
            return 1;
        }).then(Commands.literal("allocate")
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .then(Commands.argument("points", IntegerArgumentType.integer(1, 10000)).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var data = RotasData.get(player.server);
                            var id = new ContentId(ResourceLocationArgument.getId(context, "id").toString());
                            if (data.kernel() == null || !data.kernel().content().stats().containsKey(id)) {
                                context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.stats.unknown", id))); return 0;
                            }
                            int points = IntegerArgumentType.getInteger(context, "points");
                            try { data.progress(player.getUUID()).rpg().allocate(id.value(), points); }
                            catch (IllegalArgumentException ex) {
                                context.getSource().sendFailure(Component.literal(ex.getMessage())); return 0;
                            }
                            data.setDirty();
                            RotasNetwork.syncProgress(player);
                            context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.stats.allocated", points, id)), false);
                            return 1;
                        }))))
                .then(Commands.literal("reset").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(context -> {
                            var target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                            int refunded = net.schwarz.rotasutils.server.CharacterStatService.reset(target, RotasData.get(target.server));
                            RotasNetwork.syncProgress(target);
                            context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.msg.sa.refunded_stat", refunded)), true);
                            return 1;
                        }))));
    }
}
