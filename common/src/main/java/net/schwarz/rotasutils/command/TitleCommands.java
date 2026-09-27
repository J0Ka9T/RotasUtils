package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.TitleService;
import net.schwarz.rotasutils.title.TitleDef;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * {@code /rotas title}: see the titles (ฉายา) you have earned, wear one, and - for an operator -
 * hand one out or take it back.
 */
final class TitleCommands {
    private TitleCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("title")
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("clear").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    TitleService.wear(player, RotasData.get(player.server), "");
                    context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.title.cleared"), false);
                    return 1;
                }))
                .then(Commands.literal("use")
                        .then(Commands.argument("title", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        RotasData.get(player.server).progress(player.getUUID()).titles().forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    RotasData data = RotasData.get(player.server);
                                    String id = StringArgumentType.getString(context, "title");
                                    if (!TitleService.wear(player, data, id)) {
                                        context.getSource().sendFailure(Component.literal(
                                                ThaiText.t("rotasutils.cmd.title.not_earned", id)));
                                        return 0;
                                    }
                                    TitleDef title = data.title(id);
                                    context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.title.worn",
                                            title == null ? id : title.name()), false);
                                    return 1;
                                })))
                .then(Commands.literal("grant").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("title", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            RotasData.get(context.getSource().getServer()).titles().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            RotasData data = RotasData.get(target.server);
                                            String id = StringArgumentType.getString(context, "title");
                                            TitleDef title = data.title(id);
                                            if (title == null) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.cmd.title.unknown", id)));
                                                return 0;
                                            }
                                            if (!TitleService.award(target, data, title, true)) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.cmd.title.cannot_grant", title.name())));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.title.granted",
                                                    title.name(), target.getGameProfile().getName()), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("revoke").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("title", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            String id = StringArgumentType.getString(context, "title");
                                            if (!TitleService.revoke(target, RotasData.get(target.server), id)) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.cmd.title.not_earned", id)));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.title.revoked",
                                                    id, target.getGameProfile().getName()), true);
                                            return 1;
                                        })))));
    }

    /** Every title, with what the player has done towards it and who holds the unique ones. */
    private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        int earned = 0;
        for (TitleDef title : TitleService.sorted(data)) {
            boolean owned = progress.hasTitle(title.id());
            if (owned) {
                earned++;
            }
            if (!title.enabled() || (title.hidden() && !owned)) {
                continue;
            }
            long done = TitleService.progressOf(data, progress, title);
            String state = owned ? ThaiText.t("rotasutils.cmd.title.state_owned")
                    : title.unique() && data.uniqueTitleOwner(title.id()) != null
                    ? ThaiText.t("rotasutils.cmd.title.state_taken")
                    : ThaiText.t("rotasutils.cmd.title.state_progress", done, title.amount());
            source.sendSuccess(() -> Component.literal(
                    (title.id().equals(progress.activeTitle()) ? "* " : "  ") + title.name() + " - "
                            + title.description() + " [" + state + "]")
                    .withStyle(style -> style.withColor(title.color() & 0xFFFFFF)), false);
        }
        int total = earned;
        source.sendSuccess(() -> ThaiText.c("rotasutils.cmd.title.summary", total, data.titles().size()), false);
        return 1;
    }
}
