package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.mine.MiningSite;
import net.schwarz.rotasutils.server.MiningService;
import net.schwarz.rotasutils.util.ThaiText;

final class MineCommands {
    private MineCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("mine").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("create")
                        .then(Commands.argument("site", StringArgumentType.word()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            RotasData data = RotasData.get(player.server);
                            String id = StringArgumentType.getString(context, "site");
                            if (data.miningSites().containsKey(id)) {
                                return fail(context, "rotasutils.cmd.mine.exists", id);
                            }
                            try {
                                data.putMiningSite(new MiningSite(id, player.level().dimension().location().toString()));
                            } catch (IllegalArgumentException invalid) {
                                return fail(context, "rotasutils.cmd.mine.bad_id", id);
                            }
                            return ok(context, "rotasutils.cmd.mine.created", id);
                        })))
                .then(Commands.literal("delete")
                        .then(Commands.argument("site", StringArgumentType.word()).executes(context -> {
                            RotasData data = RotasData.get(context.getSource().getServer());
                            String id = StringArgumentType.getString(context, "site");
                            return data.removeMiningSite(id) ? ok(context, "rotasutils.cmd.mine.deleted", id)
                                    : fail(context, "rotasutils.cmd.mine.unknown", id);
                        })))
                .then(Commands.literal("add")
                        .then(Commands.argument("site", StringArgumentType.word()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            MiningSite site = site(context);
                            if (site == null) {
                                return 0;
                            }
                            BlockPos pos = lookedAt(player);
                            if (pos == null) {
                                return fail(context, "rotasutils.cmd.mine.look_at_block");
                            }
                            String block = String.valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()));
                            if (!site.addNode(pos.asLong(), block)) {
                                return fail(context, "rotasutils.cmd.mine.node_rejected");
                            }
                            RotasData.get(player.server).setDirty();
                            return ok(context, "rotasutils.cmd.mine.node_added", block, site.nodes().size());
                        })))
                .then(Commands.literal("unadd")
                        .then(Commands.argument("site", StringArgumentType.word()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            MiningSite site = site(context);
                            BlockPos pos = lookedAt(player);
                            if (site == null || pos == null || !site.removeNode(pos.asLong())) {
                                return fail(context, "rotasutils.cmd.mine.no_node");
                            }
                            RotasData.get(player.server).setDirty();
                            return ok(context, "rotasutils.cmd.mine.node_removed", site.nodes().size());
                        })))
                .then(Commands.literal("scan")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                        .then(Commands.argument("block", ResourceLocationArgument.id()).executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            MiningSite site = site(context);
                                            if (site == null) {
                                                return 0;
                                            }
                                            int added = MiningService.scan(player.serverLevel(), site, player.blockPosition(),
                                                    IntegerArgumentType.getInteger(context, "radius"),
                                                    ResourceLocationArgument.getId(context, "block").toString());
                                            RotasData.get(player.server).setDirty();
                                            return ok(context, "rotasutils.cmd.mine.scanned", added, site.nodes().size());
                                        })))))
                .then(Commands.literal("respawn")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 604800)).executes(context -> {
                                    MiningSite site = site(context);
                                    if (site == null) {
                                        return 0;
                                    }
                                    site.setRespawnSeconds(IntegerArgumentType.getInteger(context, "seconds"));
                                    RotasData.get(context.getSource().getServer()).setDirty();
                                    return ok(context, "rotasutils.cmd.mine.respawn_set", site.respawnSeconds());
                                }))))
                .then(Commands.literal("reward")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.argument("gold_min", LongArgumentType.longArg(0, 1_000_000))
                                        .then(Commands.argument("gold_max", LongArgumentType.longArg(0, 1_000_000))
                                                .then(Commands.argument("xp", LongArgumentType.longArg(0, 1_000_000)).executes(context -> {
                                                    MiningSite site = site(context);
                                                    if (site == null) {
                                                        return 0;
                                                    }
                                                    site.setGold(LongArgumentType.getLong(context, "gold_min"),
                                                            LongArgumentType.getLong(context, "gold_max"));
                                                    site.setXp(LongArgumentType.getLong(context, "xp"));
                                                    RotasData.get(context.getSource().getServer()).setDirty();
                                                    return ok(context, "rotasutils.cmd.mine.reward_set",
                                                            site.goldMin(), site.goldMax(), site.xp());
                                                }))))))
                .then(Commands.literal("loot")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.literal("add")
                                        .then(Commands.argument("line", StringArgumentType.greedyString()).executes(context -> {
                                            MiningSite site = site(context);
                                            if (site == null || !site.addLoot(StringArgumentType.getString(context, "line"))) {
                                                return fail(context, "rotasutils.cmd.mine.loot_rejected");
                                            }
                                            RotasData.get(context.getSource().getServer()).setDirty();
                                            return ok(context, "rotasutils.cmd.mine.loot_added", site.loot().size());
                                        })))
                                .then(Commands.literal("clear").executes(context -> {
                                    MiningSite site = site(context);
                                    if (site == null) {
                                        return 0;
                                    }
                                    site.loot().clear();
                                    RotasData.get(context.getSource().getServer()).setDirty();
                                    return ok(context, "rotasutils.cmd.mine.loot_cleared");
                                }))))
                .then(Commands.literal("depleted")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.argument("block", ResourceLocationArgument.id()).executes(context -> {
                                    MiningSite site = site(context);
                                    if (site == null) {
                                        return 0;
                                    }
                                    site.setDepletedBlock(ResourceLocationArgument.getId(context, "block").toString());
                                    RotasData.get(context.getSource().getServer()).setDirty();
                                    return ok(context, "rotasutils.cmd.mine.depleted_set", site.depletedBlock());
                                }))))
                .then(Commands.literal("limit")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.argument("per_day", IntegerArgumentType.integer(0, 100000)).executes(context -> {
                                    MiningSite site = site(context);
                                    if (site == null) {
                                        return 0;
                                    }
                                    site.setDailyLimit(IntegerArgumentType.getInteger(context, "per_day"));
                                    RotasData.get(context.getSource().getServer()).setDirty();
                                    return ok(context, "rotasutils.cmd.mine.limit_set", site.dailyLimit());
                                }))))
                .then(Commands.literal("refill")
                        .then(Commands.argument("site", StringArgumentType.word()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            MiningSite site = site(context);
                            if (site == null) {
                                return 0;
                            }
                            int restored = MiningService.refill(player.serverLevel(), site);
                            RotasData.get(player.server).setDirty();
                            return ok(context, "rotasutils.cmd.mine.refilled", restored);
                        }))));
    }

    private static MiningSite site(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "site");
        MiningSite site = RotasData.get(context.getSource().getServer()).miningSites().get(id);
        if (site == null) {
            context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.mine.unknown", id)));
        }
        return site;
    }

    private static BlockPos lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(6.0));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static int list(CommandSourceStack source) {
        RotasData data = RotasData.get(source.getServer());
        source.sendSuccess(() -> ThaiText.c("rotasutils.cmd.mine.count", data.miningSites().size()), false);
        data.miningSites().values().forEach(site ->
                source.sendSuccess(() -> Component.literal(MiningService.describe(site)), false));
        return 1;
    }

    private static int ok(CommandContext<CommandSourceStack> context, String key, Object... args) {
        RotasData.get(context.getSource().getServer()).audit("mine " + key);
        context.getSource().sendSuccess(() -> ThaiText.c(key, args), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.literal(ThaiText.t(key, args)));
        return 0;
    }
}
