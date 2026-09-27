package net.schwarz.rotasutils.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.QuestKernelService;
import net.schwarz.rotasutils.util.ThaiText;

/** Player-facing kernel quest commands; the legacy board quest commands are untouched. */
final class QuestKernelCommands {
    private QuestKernelCommands() { }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("quests")
                .executes(QuestKernelCommands::status)
                .then(Commands.literal("available").executes(QuestKernelCommands::available))
                .then(Commands.literal("accept").then(Commands.argument("quest", ResourceLocationArgument.id())
                        .executes(context -> act(context, QuestKernelService::accept))))
                .then(Commands.literal("abandon").then(Commands.argument("quest", ResourceLocationArgument.id())
                        .executes(context -> act(context, QuestKernelService::abandon))))
                .then(Commands.literal("claim").then(Commands.argument("quest", ResourceLocationArgument.id())
                        .executes(context -> act(context, QuestKernelService::claim)))));
    }

    private static QuestKernelService service(CommandContext<CommandSourceStack> context) {
        var data = RotasData.get(context.getSource().getServer());
        if (data.kernel() == null) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.kernel_not_ready")); }
        return data.kernel().quests();
    }

    private static int status(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var service = service(context);
        var active = service.active(player);
        context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.quests.active", active.size())), false);
        active.forEach((id, stage) -> context.getSource().sendSuccess(() -> Component.literal(
                ThaiText.t("rotasutils.cmd.quests.stage_line", id, stage)), false));
        return active.size();
    }

    private static int available(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var quests = service(context).available(player);
        context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.quests.available", quests.size())), false);
        quests.forEach(id -> context.getSource().sendSuccess(() -> Component.literal("  " + id), false));
        return quests.size();
    }

    private static int act(CommandContext<CommandSourceStack> context,
                           TriFunction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ContentId id = new ContentId(ResourceLocationArgument.getId(context, "quest").toString());
        try {
            QuestKernelService.Result result = action.apply(service(context), player, id);
            context.getSource().sendSuccess(() -> Component.literal(id + ": " + describe(result)), false);
            return result == QuestKernelService.Result.UNAVAILABLE || result == QuestKernelService.Result.LIMIT_REACHED ? 0 : 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.quests.rejected", failure.getMessage())));
            return 0;
        }
    }

    /** The same Thai wording the RPG console uses for each outcome. */
    private static String describe(QuestKernelService.Result result) {
        return ThaiText.t(switch (result) {
            case ACCEPTED -> "rotasutils.msg.kernel.quest.accepted";
            case CLAIMED -> "rotasutils.msg.kernel.quest.claimed";
            case COMPLETED -> "rotasutils.msg.kernel.quest.completed";
            case ADVANCED -> "rotasutils.msg.kernel.quest.advanced";
            case ALREADY_ACTIVE -> "rotasutils.msg.kernel.quest.already_active";
            case NOT_ACTIVE -> "rotasutils.msg.kernel.quest.not_active";
            case LIMIT_REACHED -> "rotasutils.msg.kernel.quest.limit";
            case UNAVAILABLE -> "rotasutils.msg.kernel.quest.unavailable";
        });
    }

    @FunctionalInterface
    private interface TriFunction {
        QuestKernelService.Result apply(QuestKernelService service, ServerPlayer player, ContentId quest);
    }
}
