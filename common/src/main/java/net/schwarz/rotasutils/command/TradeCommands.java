package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.TradeBook;
import net.schwarz.rotasutils.item.RecipeScrollItem;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.RewardService;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.server.TradeConfig;
import net.schwarz.rotasutils.server.TradeService;

final class TradeCommands {
    private TradeCommands() {
    }

    private static boolean admin(CommandSourceStack source) {
        return RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT);
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("chef").executes(context -> book(context.getSource(), "chef")));
        root.then(Commands.literal("sell").executes(context -> {
            net.schwarz.rotasutils.server.EconomyService.openSell(context.getSource().getPlayerOrException());
            return 1;
        }));
        root.then(Commands.literal("worth").requires(TradeCommands::admin).executes(context -> {
            net.schwarz.rotasutils.server.EconomyService.openWorth(context.getSource().getPlayerOrException(), "");
            return 1;
        }));
        root.then(Commands.literal("settings").requires(TradeCommands::admin).executes(context -> {
            net.schwarz.rotasutils.network.RotasNetwork.openScreen(context.getSource().getPlayerOrException(), "settings",
                    net.schwarz.rotasutils.server.SettingsPanels.payload(net.schwarz.rotasutils.data.RotasData.get(context.getSource().getServer()), "slots", 0));
            return 1;
        }));
        root.then(Commands.literal("trade")
                .executes(context -> book(context.getSource(), ""))
                .then(Commands.argument("trade", StringArgumentType.word()).suggests((context, builder) -> {
                    TradeConfig.all().forEach(b -> builder.suggest(b.trade()));
                    return builder.buildFuture();
                }).executes(context -> book(context.getSource(), StringArgumentType.getString(context, "trade"))))
                .then(Commands.literal("reload").requires(TradeCommands::admin).executes(context -> {
                    String error = TradeConfig.reload(context.getSource().getServer());
                    int count = TradeConfig.all().stream().mapToInt(b -> b.recipes().size()).sum();
                    if (error != null) {
                        context.getSource().sendFailure(Component.literal("Some trade files were not applied: " + error));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Trades loaded: " + TradeConfig.all().size()
                            + " roles, " + count + " recipes"), true);
                    return count;
                }))
                .then(Commands.literal("scroll").requires(TradeCommands::admin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("trade", StringArgumentType.word()).suggests((context, builder) -> {
                                    TradeConfig.all().forEach(b -> builder.suggest(b.trade()));
                                    return builder.buildFuture();
                                }).then(Commands.argument("recipe", StringArgumentType.word()).suggests((context, builder) -> {
                                    TradeBook b = TradeConfig.book(StringArgumentType.getString(context, "trade"));
                                    if (b != null) b.recipes().stream().filter(TradeBook.Recipe::secret).forEach(r -> builder.suggest(r.id()));
                                    return builder.buildFuture();
                                }).executes(context -> scroll(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                        StringArgumentType.getString(context, "trade"), StringArgumentType.getString(context, "recipe"))))))));
    }

    private static int book(CommandSourceStack source, String trade) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TradeService.openBook(source.getPlayerOrException(), trade);
        return 1;
    }

    private static int scroll(CommandSourceStack source, ServerPlayer target, String trade, String recipe) {
        TradeBook book = TradeConfig.book(trade);
        TradeBook.Recipe found = book == null ? null : book.find(recipe);
        if (found == null || !found.secret()) {
            source.sendFailure(Component.literal("Not a secret recipe: " + trade + "/" + recipe));
            return 0;
        }
        RewardService.give(target, RecipeScrollItem.of(RotasRegistry.RECIPE_SCROLL.get(), trade, recipe));
        source.sendSuccess(() -> Component.literal("Gave " + target.getGameProfile().getName()
                + " a Recipe Scroll for " + trade + "/" + recipe), true);
        return 1;
    }
}
