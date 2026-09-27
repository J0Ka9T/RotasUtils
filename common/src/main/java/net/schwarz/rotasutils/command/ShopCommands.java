package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.MerchantService;
import net.schwarz.rotasutils.util.ThaiText;

/** Player-facing merchant commands. Stock and limits are enforced by the service, not the UI. */
final class ShopCommands {
    private ShopCommands() { }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("shop")
                .then(Commands.literal("list").then(Commands.argument("merchant", ResourceLocationArgument.id())
                        .executes(ShopCommands::list)))
                .then(Commands.literal("buy").then(Commands.argument("merchant", ResourceLocationArgument.id())
                        .then(Commands.argument("trade", StringArgumentType.word())
                                .executes(context -> buy(context, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> buy(context, IntegerArgumentType.getInteger(context, "count"))))))));
    }

    private static MerchantService service(CommandContext<CommandSourceStack> context) {
        var data = RotasData.get(context.getSource().getServer());
        if (data.kernel() == null) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.kernel_not_ready")); }
        return data.kernel().merchants();
    }

    private static int list(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ContentId id = new ContentId(ResourceLocationArgument.getId(context, "merchant").toString());
        try {
            var lines = service(context).list(player, id);
            context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.shop.list", id, lines.size())), false);
            lines.forEach(line -> context.getSource().sendSuccess(() -> Component.literal("  " + line), false));
            return lines.size();
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.shop.unavailable", failure.getMessage())));
            return 0;
        }
    }

    private static int buy(CommandContext<CommandSourceStack> context, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ContentId id = new ContentId(ResourceLocationArgument.getId(context, "merchant").toString());
        String trade = StringArgumentType.getString(context, "trade");
        try {
            MerchantService.Result result = service(context).buy(player, id, trade, count);
            context.getSource().sendSuccess(() -> Component.literal(id + " " + trade + ": " + describe(result)), false);
            return result == MerchantService.Result.TRADED ? 1 : 0;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.msg.kernel.trade_refused", failure.getMessage())));
            return 0;
        }
    }

    /** The same Thai wording the RPG console shop uses for each outcome. */
    private static String describe(MerchantService.Result result) {
        return ThaiText.t(switch (result) {
            case TRADED -> "rotasutils.msg.kernel.trade.traded";
            case INSUFFICIENT_FUNDS -> "rotasutils.msg.kernel.trade.funds";
            case MISSING_ITEMS -> "rotasutils.msg.kernel.trade.missing";
            case OUT_OF_STOCK -> "rotasutils.msg.kernel.trade.stock";
            case LIMIT_REACHED -> "rotasutils.msg.kernel.trade.limit";
            case UNAVAILABLE -> "rotasutils.msg.kernel.trade.unavailable";
            case UNKNOWN_TRADE -> "rotasutils.msg.kernel.trade.unknown";
        });
    }
}
