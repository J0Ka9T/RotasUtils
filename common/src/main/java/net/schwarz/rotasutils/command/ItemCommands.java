package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ItemFactory;
import net.schwarz.rotasutils.server.LootService;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.util.ThaiText;
import java.util.List;

final class ItemCommands {
    private ItemCommands() { }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("item").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.VIEW))
                .then(Commands.literal("inspect").executes(context -> inspect(context)))
                .then(Commands.literal("give").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("profile", ResourceLocationArgument.id())
                                        .executes(context -> give(context, 1))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10000))
                                                .executes(context -> give(context, IntegerArgumentType.getInteger(context, "level"))))))));
        root.then(Commands.literal("loot").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.VIEW))
                .then(Commands.literal("preview")
                        .then(Commands.argument("table", ResourceLocationArgument.id())
                                .executes(context -> preview(context, 1, 1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10000))
                                        .executes(context -> preview(context, IntegerArgumentType.getInteger(context, "level"), 1))
                                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0, 1000))
                                                .executes(context -> preview(context, IntegerArgumentType.getInteger(context, "level"),
                                                        DoubleArgumentType.getDouble(context, "multiplier")))))))
                .then(Commands.literal("recover").executes(context -> recover(context))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var stack = player.getMainHandItem();
        if (!ItemFactory.isRpgItem(stack)) {
            context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.item.no_rpg")));
            return 0;
        }
        String description = stack.getHoverName().getString() + " profile=" + ItemFactory.profileId(stack)
                + " rarity=" + ItemFactory.rarity(stack) + " level=" + ItemFactory.level(stack)
                + " set=" + ItemFactory.set(stack);
        context.getSource().sendSuccess(() -> Component.literal(description), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context, int level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        var data = RotasData.get(source.getServer());
        if (data.kernel() == null) { source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel_not_ready"))); return 0; }
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        ContentId profile = new ContentId(ResourceLocationArgument.getId(context, "profile").toString());
        try {
            var stack = ItemFactory.create(data.kernel().content().items(), profile, level, 1,
                    LootService.random("command|" + profile + "|" + target.getUUID() + "|" + source.getServer().getTickCount()));
            int stored = LootService.deliver(target, List.of(stack));
            source.sendSuccess(() -> Component.literal(ThaiText.t(stored > 0 ? "rotasutils.cmd.item.granted_mail"
                    : "rotasutils.cmd.item.granted", stack.getHoverName().getString())), false);
            data.audit(java.time.Instant.now() + " actor=" + RotasPermissions.actor(source)
                    + " action=item_give target=" + target.getUUID() + " profile=" + profile + " level=" + level);
            return 1;
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.item.rejected", failure.getMessage())));
            return 0;
        }
    }

    private static int preview(CommandContext<CommandSourceStack> context, int level, double multiplier) {
        var source = context.getSource();
        var data = RotasData.get(source.getServer());
        if (data.kernel() == null) { source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel_not_ready"))); return 0; }
        ContentId table = new ContentId(ResourceLocationArgument.getId(context, "table").toString());
        try {
            List<String> lines = LootService.preview(data.kernel().content().items(), table,
                    "preview|" + table + "|" + level + "|" + multiplier, level, multiplier);
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.loot.preview", table, lines.size())), false);
            lines.forEach(line -> source.sendSuccess(() -> Component.literal("  " + line), false));
            return 1;
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.loot.rejected", failure.getMessage())));
            return 0;
        }
    }

    private static int recover(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int delivered = LootService.recover(player);
        int remaining = RotasData.get(player.server).progress(player.getUUID()).mailbox().size();
        context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.loot.recovered", delivered, remaining)), false);
        return 1;
    }
}
