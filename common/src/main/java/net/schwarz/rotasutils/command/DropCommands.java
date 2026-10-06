package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.server.SeasonConfigFile;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;

final class DropCommands {
    private DropCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("drops").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                .executes(context -> show(context.getSource()))
                .then(Commands.literal("show").executes(context -> show(context.getSource())))
                .then(Commands.literal("enable")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(context -> {
                            SeasonRules.PlainDrop plain = plain(context.getSource());
                            plain.enabled = BoolArgumentType.getBool(context, "on");
                            return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.enabled", plain.enabled));
                        })))
                .then(Commands.literal("hostile_only")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(context -> {
                            SeasonRules.PlainDrop plain = plain(context.getSource());
                            plain.hostileOnly = BoolArgumentType.getBool(context, "on");
                            return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.hostile_only", plain.hostileOnly));
                        })))
                .then(Commands.literal("rates")
                        .then(Commands.argument("coin_chance", DoubleArgumentType.doubleArg(0, 1))
                                .then(Commands.argument("coin_multiplier", DoubleArgumentType.doubleArg(0, 1000))
                                        .then(Commands.argument("loot_chance", DoubleArgumentType.doubleArg(0, 1))
                                                .executes(context -> rates(context.getSource(), plain(context.getSource()).rule,
                                                        DoubleArgumentType.getDouble(context, "coin_chance"),
                                                        DoubleArgumentType.getDouble(context, "coin_multiplier"),
                                                        DoubleArgumentType.getDouble(context, "loot_chance")))))))
                .then(Commands.literal("entity")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .then(Commands.literal("rates")
                                        .then(Commands.argument("coin_chance", DoubleArgumentType.doubleArg(0, 1))
                                                .then(Commands.argument("coin_multiplier", DoubleArgumentType.doubleArg(0, 1000))
                                                        .then(Commands.argument("loot_chance", DoubleArgumentType.doubleArg(0, 1))
                                                                .executes(context -> {
                                                                    var rule = forEntity(context.getSource(), entityId(context), true);
                                                                    return rule == null ? 0 : rates(context.getSource(), rule,
                                                                            DoubleArgumentType.getDouble(context, "coin_chance"),
                                                                            DoubleArgumentType.getDouble(context, "coin_multiplier"),
                                                                            DoubleArgumentType.getDouble(context, "loot_chance"));
                                                                })))))
                                .then(Commands.literal("grade")
                                        .then(Commands.argument("grades", StringArgumentType.greedyString()).executes(context -> {
                                            var rule = forEntity(context.getSource(), entityId(context), true);
                                            if (rule == null) {
                                                return 0;
                                            }
                                            rule.grades = StringArgumentType.getString(context, "grades").trim().split("\\s+");
                                            return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.entity_grades",
                                                    entityId(context), String.join(", ", rule.grades)));
                                        })))
                                .then(Commands.literal("add_item")
                                        .then(Commands.argument("line", StringArgumentType.greedyString()).executes(context -> {
                                            var rule = forEntity(context.getSource(), entityId(context), true);
                                            if (rule == null) {
                                                return 0;
                                            }
                                            String line = StringArgumentType.getString(context, "line").trim();
                                            if (!ResourceLocation.isValidResourceLocation(line.split("\\s+")[0])) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.cmd.drops.bad_line", line)));
                                                return 0;
                                            }
                                            List<String> items = new ArrayList<>(List.of(rule.items));
                                            if (items.size() >= 32) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.cmd.drops.too_many")));
                                                return 0;
                                            }
                                            items.add(line);
                                            rule.items = items.toArray(new String[0]);
                                            return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.item_added",
                                                    entityId(context), line));
                                        })))
                                .then(Commands.literal("clear_items").executes(context -> {
                                    var rule = forEntity(context.getSource(), entityId(context), false);
                                    if (rule == null) {
                                        return 0;
                                    }
                                    rule.items = new String[0];
                                    return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.items_cleared", entityId(context)));
                                }))
                                .then(Commands.literal("remove").executes(context -> {
                                    SeasonRules.PlainDrop plain = plain(context.getSource());
                                    if (plain.byEntity.remove(entityId(context)) == null) {
                                        context.getSource().sendFailure(Component.literal(
                                                ThaiText.t("rotasutils.cmd.drops.no_entity", entityId(context))));
                                        return 0;
                                    }
                                    return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.entity_removed", entityId(context)));
                                }))))
                .then(Commands.literal("filter")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(context -> {
                            boolean on = BoolArgumentType.getBool(context, "on");
                            net.schwarz.rotasutils.server.DropFilterService.setEnabled(
                                    context.getSource().getServer(), RotasData.get(context.getSource().getServer()), on);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    ThaiText.t("rotasutils.cmd.drops.filter_enabled", on)), true);
                            return 1;
                        })))
                .then(Commands.literal("block")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("drops", BoolArgumentType.bool()).executes(context -> {
                                    var server = context.getSource().getServer();
                                    String item = ResourceLocationArgument.getId(context, "item").toString();
                                    boolean drops = BoolArgumentType.getBool(context, "drops");
                                    if (!net.schwarz.rotasutils.server.DropFilterService.setGlobal(
                                            server, RotasData.get(server), item, drops)) {
                                        context.getSource().sendFailure(Component.literal(
                                                ThaiText.t("rotasutils.msg.drop_filter.full")));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            ThaiText.t("rotasutils.cmd.drops.blocked_global", item, drops)), true);
                                    return 1;
                                }))))
                .then(Commands.literal("block_for")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("drops", BoolArgumentType.bool()).executes(context -> {
                                            var server = context.getSource().getServer();
                                            String entity = ResourceLocationArgument.getId(context, "entity").toString();
                                            String item = ResourceLocationArgument.getId(context, "item").toString();
                                            boolean drops = BoolArgumentType.getBool(context, "drops");
                                            if (!net.schwarz.rotasutils.server.DropFilterService.setForEntity(
                                                    server, RotasData.get(server), entity, item, drops)) {
                                                context.getSource().sendFailure(Component.literal(
                                                        ThaiText.t("rotasutils.msg.drop_filter.full")));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    ThaiText.t("rotasutils.cmd.drops.blocked", item, entity, drops)), true);
                                            return 1;
                                        })))))
                .then(Commands.literal("ignore")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .then(Commands.argument("ignored", BoolArgumentType.bool()).executes(context -> {
                                    SeasonRules.PlainDrop plain = plain(context.getSource());
                                    String id = entityId(context);
                                    List<String> ignore = new ArrayList<>(List.of(plain.ignore));
                                    boolean wanted = BoolArgumentType.getBool(context, "ignored");
                                    if (wanted) {
                                        if (!ignore.contains(id)) {
                                            ignore.add(id);
                                        }
                                    } else {
                                        ignore.remove(id);
                                    }
                                    plain.ignore = ignore.toArray(new String[0]);
                                    return save(context.getSource(), ThaiText.t("rotasutils.cmd.drops.ignored", id, wanted));
                                })))));
    }

    private static String entityId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "entity").toString();
    }

    private static SeasonRules.PlainDrop plain(CommandSourceStack source) {
        SeasonRules rules = RotasData.get(source.getServer()).levelConfig().season();
        if (rules.drops.plain == null) {
            rules.drops.plain = new SeasonRules.PlainDrop();
        }
        return rules.drops.plain;
    }

    private static SeasonRules.RankDrop forEntity(CommandSourceStack source, String id, boolean create) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(id))) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.drops.unknown_entity", id)));
            return null;
        }
        SeasonRules.PlainDrop plain = plain(source);
        SeasonRules.RankDrop rule = plain.byEntity.get(id);
        if (rule == null) {
            if (!create) {
                source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.drops.no_entity", id)));
                return null;
            }
            if (plain.byEntity.size() >= 256) {
                source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.drops.too_many")));
                return null;
            }
            rule = new SeasonRules.RankDrop();
            plain.byEntity.put(id, rule);
        }
        return rule;
    }

    private static int rates(CommandSourceStack source, SeasonRules.RankDrop rule,
                             double coinChance, double coinMultiplier, double lootChance) {
        rule.coinChance = coinChance;
        rule.coinMultiplier = coinMultiplier;
        rule.lootChance = lootChance;
        return save(source, ThaiText.t("rotasutils.cmd.drops.rates", coinChance, coinMultiplier, lootChance));
    }

    private static int save(CommandSourceStack source, String message) {
        MinecraftServer server = source.getServer();
        RotasData data = RotasData.get(server);
        data.levelConfig().season().sanitize();
        data.setDirty();
        try {
            SeasonConfigFile.write(server, data.levelConfig().season());
        } catch (java.io.IOException failure) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.drops.write_failed", failure.getMessage())));
            return 0;
        }
        data.audit("drops " + message);
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int show(CommandSourceStack source) {
        SeasonRules.PlainDrop plain = plain(source);
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.drops.summary",
                plain.enabled, plain.hostileOnly, plain.rule.coinChance, plain.rule.lootChance,
                plain.byEntity.size(), plain.ignore.length)), false);
        plain.byEntity.forEach((id, rule) -> source.sendSuccess(() -> Component.literal(
                ThaiText.t("rotasutils.cmd.drops.entity_line", id, rule.coinChance, rule.lootChance,
                        rule.items.length)), false));
        return 1;
    }
}
