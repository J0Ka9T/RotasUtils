package net.schwarz.rotasutils.command;

import net.schwarz.rotasutils.util.ThaiText;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.ProgressService;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.server.SkillService;
import net.schwarz.rotasutils.server.Validation;

import java.util.List;

/**
 * Optional command shortcuts.
 *
 * <p>Everything reachable here is also reachable from the UI; these exist so
 * administrators can bind a key or a command block to a menu.
 */
public final class RotasCommands {
    private RotasCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Rotasutils.MOD_ID);
            KernelCommands.attach(root);
            MonsterCommands.attach(root);
            ZoneCommands.attach(root);
            HouseCommands.attach(root);
            ItemCommands.attach(root);
            QuestKernelCommands.attach(root);
            ShopCommands.attach(root);
            ProgressionCommands.attach(root);
            AdminCommands.attach(root);
            SeasonCommands.attach(root);
            HorseCommands.attach(root);
            RefineCommands.attach(root);
            DropCommands.attach(root);
            TitleCommands.attach(root);
            CardCommands.attach(root);
            EventCommands.attach(root);
            JourneyCommands.attach(root);
            MineCommands.attach(root);
            FarmCommands.attach(root);
            WorldCommands.attach(root);
            dispatcher.register(HorseCommands.root());

            root.executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                RotasNetwork.openMainMenu(player);
                return 1;
            });
            root.then(Commands.literal("ui")
                    .executes(context -> {
                        RotasNetwork.openKernelConsole(context.getSource().getPlayerOrException(), "CHARACTER");
                        return 1;
                    })
                    .then(Commands.argument("section", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (var section : net.schwarz.rotasutils.network.KernelUi.SECTIONS) {
                                    builder.suggest(section.toLowerCase(java.util.Locale.ROOT));
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                RotasNetwork.openKernelConsole(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "section"));
                                return 1;
                            })));
            root.then(Commands.literal("menu").executes(context -> {
                RotasNetwork.openMainMenu(context.getSource().getPlayerOrException());
                return 1;
            }));
            root.then(Commands.literal("skills").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                RotasNetwork.openScreen(player, "skill_tree", new net.minecraft.nbt.CompoundTag());
                return 1;
            }));
            root.then(Commands.literal("journal").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                RotasNetwork.openScreen(player, "journal", new net.minecraft.nbt.CompoundTag());
                return 1;
            }));
            root.then(Commands.literal("validate")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        RotasData data = RotasData.get(source.getServer());
                        List<Validation.Issue> issues = Validation.validateAll(data);
                        if (source.getEntity() instanceof ServerPlayer player) {
                            RotasNetwork.sendValidation(player, issues);
                        }
                        context.getSource().sendSuccess(() ->
                                Component.literal(ThaiText.t("rotasutils.cmd.rotas.issues", issues.size())), false);
                        KernelCommands.reload(source, true);
                        return issues.isEmpty() ? 1 : 0;
                    }));

            root.then(Commands.literal("level")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.literal("set")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                                int level = IntegerArgumentType.getInteger(context, "level");
                                                RotasData data = RotasData.get(target.server);
                                                ProgressService.setLevel(target, data, level);
                                                RotasNetwork.syncProgress(target);
                                                context.getSource().sendSuccess(() -> Component.literal(
                                                        ThaiText.t("rotasutils.cmd.rotas.level_set", target.getGameProfile().getName(), level)), true);
                                                return 1;
                                            }))))
                    .then(Commands.literal("addxp")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                                RotasData data = RotasData.get(target.server);
                                                ProgressService.addExperience(target, data, amount, false);
                                                RotasNetwork.syncProgress(target);
                                                return 1;
                                            })))));

            root.then(Commands.literal("points")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(-999, 999))
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                        RotasData data = RotasData.get(target.server);
                                        data.progress(target.getUUID()).addSkillPoints(amount);
                                        RotasNetwork.syncProgress(target);
                                        return 1;
                                    }))));

            root.then(Commands.literal("clearance")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("rank", StringArgumentType.word())
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                        DangerRank rank = DangerRank.byName(
                                                StringArgumentType.getString(context, "rank"), null);
                                        if (rank == null) {
                                            context.getSource().sendFailure(
                                                    Component.literal(ThaiText.t("rotasutils.cmd.rotas.unknown_rank")));
                                            return 0;
                                        }
                                        RotasData data = RotasData.get(target.server);
                                        data.progress(target.getUUID()).grantClearance(rank);
                                        RotasNetwork.syncProgress(target);
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                ThaiText.t("rotasutils.msg.sa.clearance_granted", rank.display())), true);
                                        return 1;
                                    }))));

            root.then(Commands.literal("resetskills")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.argument("target", EntityArgument.player())
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                RotasData data = RotasData.get(target.server);
                                int refunded = SkillService.reset(target, data, null);
                                RotasNetwork.syncProgress(target);
                                context.getSource().sendSuccess(() -> Component.literal(
                                        ThaiText.t("rotasutils.msg.sa.refunded_points", refunded)), true);
                                return refunded;
                            })));

            root.then(Commands.literal("quest")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.literal("give")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("quest", StringArgumentType.string())
                                            .executes(context -> {
                                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                                String questId = StringArgumentType.getString(context, "quest");
                                                RotasData data = RotasData.get(target.server);
                                                QuestService.ActionResult result =
                                                        QuestService.accept(target, data, questId, "");
                                                context.getSource().sendSuccess(() ->
                                                        Component.literal(result.message()), false);
                                                return result.success() ? 1 : 0;
                                            }))))
                    .then(Commands.literal("complete")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("quest", StringArgumentType.string())
                                            .executes(context -> {
                                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                                String questId = StringArgumentType.getString(context, "quest");
                                                RotasData data = RotasData.get(target.server);
                                                QuestService.ActionResult result =
                                                        QuestService.turnIn(target, data, questId, "");
                                                context.getSource().sendSuccess(() ->
                                                        Component.literal(result.message()), false);
                                                return result.success() ? 1 : 0;
                                            })))));

            root.then(Commands.literal("job")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.literal("set")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("job", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                RotasData.get(context.getSource().getServer()).jobs().keySet()
                                                        .forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> setJob(context.getSource(),
                                                    EntityArgument.getPlayer(context, "target"),
                                                    StringArgumentType.getString(context, "job"))))))
                    .then(Commands.literal("clear")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(context -> setJob(context.getSource(),
                                            EntityArgument.getPlayer(context, "target"), "")))));

            root.then(Commands.literal("info").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                RotasData data = RotasData.get(player.server);
                PlayerProgress progress = data.progress(player.getUUID());
                context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.rotas.info",
                        progress.level(), progress.xp(), data.levelConfig().curve().xpToNext(progress.level()),
                        progress.skillPoints(), progress.highestClearance().display(), progress.activeQuests().size())), false);
                context.getSource().sendSuccess(() -> Component.literal(
                        ProgressService.nextClearanceSummary(data, progress)), false);
                return 1;
            }));

            root.then(Commands.literal("board")
                    .requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                    .then(Commands.literal("list").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        RotasData data = RotasData.get(player.server);
                        for (var board : data.boards().values()) {
                            context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.rotas.board_line",
                                    board.id(), board.name(), BoardService.pool(data, board).size())), false);
                        }
                        return data.boards().size();
                    })));

            com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
            dispatcher.register(Commands.literal("rotas").redirect(node));
        });
    }

    private static int setJob(CommandSourceStack source, ServerPlayer target, String jobId) {
        RotasData data = RotasData.get(target.server);
        if (!jobId.isEmpty() && data.job(jobId) == null) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.rotas.unknown_job", jobId)));
            return 0;
        }
        int refunded = net.schwarz.rotasutils.server.JobService.assign(target, data, data.progress(target.getUUID()), jobId);
        RotasNetwork.syncProgress(target);
        source.sendSuccess(() -> Component.literal((jobId.isEmpty()
                ? ThaiText.t("rotasutils.msg.sa.player_no_job", target.getGameProfile().getName())
                : ThaiText.t("rotasutils.msg.sa.player_job", target.getGameProfile().getName(), data.job(jobId).name()))
                + (refunded > 0 ? " " + ThaiText.t("rotasutils.msg.sa.refunded_suffix", refunded) : "")), true);
        return 1;
    }
}
