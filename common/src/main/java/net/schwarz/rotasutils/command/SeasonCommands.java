package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.CharacterStatService;
import net.schwarz.rotasutils.server.ProgressService;
import net.schwarz.rotasutils.server.SeasonConfigFile;
import net.schwarz.rotasutils.server.SeasonService;
import net.schwarz.rotasutils.util.ThaiText;

/** {@code /rotas season}: the season rules at a glance, reload of season.json, presets and a pacing check. */
final class SeasonCommands {
    private SeasonCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("season")
                .executes(context -> me(context.getSource()))
                .then(Commands.literal("reload").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT)).executes(context -> {
                    var server = context.getSource().getServer();
                    RotasData data = RotasData.get(server);
                    String error = SeasonConfigFile.reload(server, data);
                    if (error != null) {
                        context.getSource().sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.season.reload_failed", error)));
                        return 0;
                    }
                    refreshAll(data, server);
                    context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.reloaded",
                            SeasonConfigFile.path(server))), true);
                    return 1;
                }))
                .then(Commands.literal("enable").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(context -> {
                            var server = context.getSource().getServer();
                            RotasData data = RotasData.get(server);
                            data.levelConfig().season().enabled = BoolArgumentType.getBool(context, "on");
                            data.setDirty();
                            try { SeasonConfigFile.write(server, data.levelConfig().season()); } catch (java.io.IOException ignored) { }
                            refreshAll(data, server);
                            context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.enabled",
                                    data.levelConfig().season().enabled)), true);
                            return 1;
                        })))
                .then(Commands.literal("apply_jobs").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT)).executes(context -> {
                    var server = context.getSource().getServer();
                    RotasData data = RotasData.get(server);
                    SeasonRules rules = SeasonService.rules(data);
                    int changed = 0;
                    for (JobDef job : data.jobs().values()) {
                        if (JobArchetypes.applySeason(job, rules)) {
                            data.putJob(job);
                            changed++;
                        }
                    }
                    if (data.job("brawler") == null) {
                        JobDef brawler = JobArchetypes.create("brawler");
                        if (brawler != null) {
                            data.putJob(brawler);
                            changed++;
                        }
                    }
                    int total = changed;
                    refreshAll(data, server);
                    context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.jobs_applied", total)), true);
                    return 1;
                }))
                .then(Commands.literal("check").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT)).executes(context -> check(context.getSource())))
                .then(Commands.literal("pacing").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT)).executes(context -> pacing(context.getSource())))
                .then(Commands.literal("drops").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT)).executes(context -> drops(context.getSource())))
                .then(Commands.literal("rank").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("points", IntegerArgumentType.integer(0, 1_000_000)).executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    RotasData data = RotasData.get(target.server);
                                    int points = IntegerArgumentType.getInteger(context, "points");
                                    var progress = data.progress(target.getUUID());
                                    progress.setRankPoints(points);
                                    ProgressService.refreshClearance(target, data, progress);
                                    data.setDirty();
                                    RotasNetwork.syncProgress(target);
                                    data.audit(context.getSource().getTextName() + " set rank points of " + target.getGameProfile().getName() + " to " + points);
                                    context.getSource().sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.rank_set",
                                            target.getGameProfile().getName(), points)), true);
                                    return 1;
                                })))));
    }

    private static void refreshAll(RotasData data, net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            CharacterStatService.grantLevelPoints(data.progress(online.getUUID()), data);
            CharacterStatService.apply(online, data);
            ProgressService.refreshClearance(online, data, data.progress(online.getUUID()));
            RotasNetwork.syncContent(online);
            RotasNetwork.syncProgress(online);
        }
    }

    private static int me(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RotasData data = RotasData.get(player.server);
        var progress = data.progress(player.getUUID());
        SeasonRules rules = SeasonService.rules(data);
        if (!rules.enabled) {
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.off")), false);
            return 1;
        }
        String next = SeasonService.nextRank(progress, rules);
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.me",
                SeasonService.rankName(progress, rules), progress.rankPoints(),
                next == null ? "-" : next + " " + SeasonMath.rankThreshold(next, rules.seasonRankTotal, rules.rankThresholds))), false);
        return 1;
    }

    /**
     * Shows the drop rules as they are loaded right now: one line per monster rank, one per box
     * grade. An administrator edits these in {@code season.json} and sees the result here after
     * {@code /rotas season reload}, without having to kill anything to find out.
     */
    private static int drops(CommandSourceStack source) {
        RotasData data = RotasData.get(source.getServer());
        SeasonRules.DropRules rules = SeasonService.rules(data).drops;
        boolean on = data.serverSettings().monsterDropsEnabled() && rules != null && rules.enabled;
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.drops_header",
                ThaiText.t(on ? "rotasutils.cmd.season.drops_on" : "rotasutils.cmd.season.drops_off"))), false);
        if (rules == null) {
            return 1;
        }
        rules.ranks.forEach((rank, rule) -> source.sendSuccess(() -> Component.literal(
                ThaiText.t("rotasutils.cmd.season.drops_rank", rank,
                        Math.round(rule.coinChance * 100), rule.coinMultiplier,
                        Math.round(rule.lootChance * 100),
                        rule.grades.length == 0 ? "-" : String.join(", ", rule.grades))), false));
        rules.grades.forEach((grade, loot) -> source.sendSuccess(() -> Component.literal(
                ThaiText.t("rotasutils.cmd.season.drops_grade", grade, loot.minGold, loot.maxGold,
                        loot.items.length == 0 ? "-" : String.join(", ", loot.items))), false));
        return 1;
    }

    /** Admin sanity check: levels in each sub job's unlock table that unlock nothing. */
    private static int check(CommandSourceStack source) {
        RotasData data = RotasData.get(source.getServer());
        int problems = 0;
        for (JobDef job : data.jobs().values()) {
            if (job.productionXpRate() <= 0 || !job.subAllowed()) continue;
            java.util.Set<Integer> levels = new java.util.TreeSet<>();
            job.production().forEach(entry -> levels.add(entry.unlockLevel()));
            java.util.List<Integer> missing = new java.util.ArrayList<>();
            for (int level = 1; level <= job.masteryCurve().maxLevel() - 1; level++) {
                if (!levels.contains(level)) missing.add(level);
            }
            if (!missing.isEmpty()) {
                problems++;
                String line = ThaiText.t("rotasutils.cmd.season.check_missing", job.name(), missing);
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        int total = problems;
        source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.season.check_done", total)), false);
        return 1;
    }

    /**
     * Week-by-week rank a normal and a dedicated player reach, from the quest counts in the brief. Rank gates
     * on main quest chapters should sit below these lines, or players stall waiting for points.
     */
    private static int pacing(CommandSourceStack source) {
        SeasonRules rules = SeasonService.rules(RotasData.get(source.getServer()));
        int weeks = 9;
        for (int week = 1; week <= weeks; week++) {
            double share = week / (double) weeks;
            long normal = Math.round(25 * share * rules.rankExpFor("MAIN") + 50 * share * 0.5 * rules.rankExpFor("SIDE")
                    + week * 7 * 4 * 0.6 * rules.rankExpFor("DAILY") + week * 5 * 0.3 * rules.rankExpFor("WEEKLY"));
            long dedicated = Math.round(25 * share * rules.rankExpFor("MAIN") + 50 * share * 0.9 * rules.rankExpFor("SIDE")
                    + week * 7 * 4 * 0.95 * rules.rankExpFor("DAILY") + week * 5 * 0.9 * rules.rankExpFor("WEEKLY")
                    + week * 7L * rules.repeatableRankCapPerDay);
            String line = ThaiText.t("rotasutils.cmd.season.pacing_line", week, normal,
                    SeasonMath.rankFor(normal, rules.seasonRankTotal, rules.rankThresholds, SeasonService.RANK_ORDER),
                    dedicated, SeasonMath.rankFor(dedicated, rules.seasonRankTotal, rules.rankThresholds, SeasonService.RANK_ORDER));
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
