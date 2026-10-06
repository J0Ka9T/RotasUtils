package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.CardIndex;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.CardItem;
import net.schwarz.rotasutils.item.ItemSockets;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.LootService;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.List;

final class CardCommands {
    private CardCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("card")
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("sockets").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack held = player.getMainHandItem();
                    List<String> cards = ItemSockets.cards(held);
                    context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.card.sockets",
                            cards.size(), ItemSockets.count(held)), false);
                    for (int index = 0; index < cards.size(); index++) {
                        int socket = index;
                        context.getSource().sendSuccess(() -> Component.literal("  " + socket + ": "
                                + CardIndex.name(cards.get(socket))), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("give").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("card", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            CardIndex.all().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            String id = StringArgumentType.getString(context, "card");
                                            if (CardIndex.get(id) == null) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.cmd.card.unknown", id)));
                                                return 0;
                                            }
                                            LootService.deliver(target,
                                                    List.of(CardItem.of(RotasRegistry.CARD.get(), id, 1)));
                                            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.card.given",
                                                    CardIndex.name(id), target.getGameProfile().getName()), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("remove").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("socket", IntegerArgumentType.integer(0, 7)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int socket = IntegerArgumentType.getInteger(context, "socket");
                            String removed = ItemSockets.remove(player.getMainHandItem(), socket);
                            if (removed == null) {
                                context.getSource().sendFailure(Component.literal(
                                        ThaiText.t("rotasutils.cmd.card.no_card", socket)));
                                return 0;
                            }
                            LootService.deliver(player, List.of(CardItem.of(RotasRegistry.CARD.get(), removed, 1)));
                            RotasData.get(player.server).audit(player.getGameProfile().getName()
                                    + " unsocketed " + removed);
                            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.card.removed",
                                    socket, CardIndex.name(removed)), true);
                            return 1;
                        })))
                .then(Commands.literal("set_sockets").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 8)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int wanted = IntegerArgumentType.getInteger(context, "count");
                            ItemStack held = player.getMainHandItem();
                            int count = ItemSockets.count(held);
                            while (count < wanted) {
                                int after = ItemSockets.punch(held, wanted);
                                if (after <= count) {
                                    break;
                                }
                                count = after;
                            }
                            int placed = count;
                            context.getSource().sendSuccess(() -> ThaiText.c("rotasutils.cmd.card.sockets_set",
                                    placed), true);
                            return placed == wanted ? 1 : 0;
                        }))));
    }

    private static int list(CommandSourceStack source) {
        var cards = CardIndex.all();
        source.sendSuccess(() -> ThaiText.c("rotasutils.cmd.card.list", cards.size()), false);
        cards.forEach((id, card) -> source.sendSuccess(() -> Component.literal(
                ThaiText.t("rotasutils.cmd.card.line", id, card.name(), card.fits()))
                .withStyle(style -> style.withColor(card.color() & 0xFFFFFF)), false));
        return 1;
    }
}
