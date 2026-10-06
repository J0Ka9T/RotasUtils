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
                .then(Commands.literal("find").requires(SeasonCommands::editor)
                        .then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(context -> find(context.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "text")))))
                .then(Commands.literal("get").requires(SeasonCommands::editor)
                        .then(Commands.argument("path", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .suggests(SeasonCommands::suggestPaths)
                                .executes(context -> get(context.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path")))))
                .then(Commands.literal("set").requires(SeasonCommands::editor)
                        .then(Commands.argument("path", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .suggests(SeasonCommands::suggestPaths)
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(context -> set(context.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "value"), false)))))
                .then(Commands.literal("reset").requires(SeasonCommands::editor)
                        .then(Commands.argument("path", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .suggests(SeasonCommands::suggestPaths)
                                .executes(context -> set(context.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path"), "", true))))
                .then(Commands.literal("changed").requires(SeasonCommands::editor)
                        .executes(context -> changed(context.getSource())))
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

    private static boolean editor(CommandSourceStack source) {
        return net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT);
    }

    private static com.google.gson.JsonObject current(CommandSourceStack source) {
        return com.google.gson.JsonParser.parseString(SeasonService.rules(RotasData.get(source.getServer())).toJson()).getAsJsonObject();
    }

    private static java.util.List<String> path(String dotted) {
        return java.util.List.of(dotted.split("\\."));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPaths(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        int shown = 0;
        for (var row : net.schwarz.rotasutils.core.SettingsTree.rows(current(context.getSource()))) {
            if (row.kind() == net.schwarz.rotasutils.core.SettingsTree.Kind.GROUP) continue;
            if (!row.joined().toLowerCase(java.util.Locale.ROOT).startsWith(typed)) continue;
            builder.suggest(row.joined(), Component.literal(net.schwarz.rotasutils.core.SeasonSettingsCatalog.label(row.path())));
            if (++shown >= 200) break;
        }
        return builder.buildFuture();
    }

    private static String line(com.google.gson.JsonObject root, java.util.List<String> path) {
        var value = net.schwarz.rotasutils.core.SettingsTree.get(root, path);
        String label = net.schwarz.rotasutils.core.SeasonSettingsCatalog.label(path);
        return String.join(".", path) + " (" + label + ") = " + (value == null ? "-" : value.toString());
    }

    private static int find(CommandSourceStack source, String text) {
        String needle = text.toLowerCase(java.util.Locale.ROOT).trim();
        var root = current(source);
        int shown = 0;
        for (var row : net.schwarz.rotasutils.core.SettingsTree.rows(root)) {
            if (row.kind() == net.schwarz.rotasutils.core.SettingsTree.Kind.GROUP) continue;
            String haystack = (row.joined() + " " + net.schwarz.rotasutils.core.SeasonSettingsCatalog.label(row.path()) + " "
                    + net.schwarz.rotasutils.core.SeasonSettingsCatalog.help(row.path())).toLowerCase(java.util.Locale.ROOT);
            if (!haystack.contains(needle)) continue;
            String entry = line(root, row.path());
            source.sendSuccess(() -> Component.literal(entry), false);
            if (++shown >= 25) {
                source.sendSuccess(() -> Component.literal("... มีมากกว่านี้ พิมพ์ให้เจาะจงขึ้น"), false);
                break;
            }
        }
        if (shown == 0) source.sendFailure(Component.literal("ไม่พบค่าที่ตรงกับ \"" + text + "\""));
        return shown;
    }

    private static int get(CommandSourceStack source, String dotted) {
        var root = current(source);
        var value = net.schwarz.rotasutils.core.SettingsTree.get(root, path(dotted));
        if (value == null) {
            source.sendFailure(Component.literal("ไม่มีค่า " + dotted + " ใช้ /rotas season find เพื่อค้นหา"));
            return 0;
        }
        String entry = line(root, path(dotted));
        String help = net.schwarz.rotasutils.core.SeasonSettingsCatalog.help(path(dotted));
        var fallback = net.schwarz.rotasutils.core.SettingsTree.get(
                com.google.gson.JsonParser.parseString(new SeasonRules().toJson()), path(dotted));
        source.sendSuccess(() -> Component.literal(entry), false);
        if (!help.isBlank()) source.sendSuccess(() -> Component.literal("  " + help), false);
        if (fallback != null) source.sendSuccess(() -> Component.literal("  ค่าเริ่มต้น: " + fallback), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String dotted, String text, boolean reset) {
        var server = source.getServer();
        RotasData data = RotasData.get(server);
        var root = current(source);
        var at = path(dotted);
        var existing = net.schwarz.rotasutils.core.SettingsTree.get(root, at);
        if (existing == null) {
            source.sendFailure(Component.literal("ไม่มีค่า " + dotted));
            return 0;
        }
        com.google.gson.JsonElement next;
        try {
            if (reset) {
                next = net.schwarz.rotasutils.core.SettingsTree.get(
                        com.google.gson.JsonParser.parseString(new SeasonRules().toJson()), at);
                if (next == null) throw new IllegalArgumentException("no default");
            } else {
                var kind = net.schwarz.rotasutils.core.SettingsTree.kindOf(existing);
                next = kind == net.schwarz.rotasutils.core.SettingsTree.Kind.GROUP
                        || kind == net.schwarz.rotasutils.core.SettingsTree.Kind.JSON
                        ? com.google.gson.JsonParser.parseString(text)
                        : net.schwarz.rotasutils.core.SettingsTree.parse(kind, text, existing);
            }
        } catch (RuntimeException bad) {
            source.sendFailure(Component.literal("ค่าไม่ถูกต้อง: " + bad.getMessage()));
            return 0;
        }
        net.schwarz.rotasutils.core.SettingsTree.set(root, at, next);
        SeasonRules rules;
        try {
            rules = SeasonRules.fromJson(root.toString());
        } catch (RuntimeException bad) {
            source.sendFailure(Component.literal("ใช้ค่านี้ไม่ได้: " + bad.getMessage()));
            return 0;
        }
        data.levelConfig().setSeason(rules);
        data.setDirty();
        try {
            SeasonConfigFile.write(server, data.levelConfig().season());
        } catch (java.io.IOException failure) {
            source.sendFailure(Component.literal("ใช้แล้วแต่เขียน season.json ไม่ได้: " + failure.getMessage()));
        }
        refreshAll(data, server);
        String entry = line(current(source), at);
        data.audit(source.getTextName() + " season " + (reset ? "reset " : "set ") + entry);
        source.sendSuccess(() -> Component.literal((reset ? "คืนค่าเริ่มต้น: " : "ตั้งค่า: ") + entry), true);
        return 1;
    }

    private static int changed(CommandSourceStack source) {
        var root = current(source);
        var defaults = com.google.gson.JsonParser.parseString(new SeasonRules().toJson());
        int shown = 0;
        for (var row : net.schwarz.rotasutils.core.SettingsTree.rows(root)) {
            if (row.kind() == net.schwarz.rotasutils.core.SettingsTree.Kind.GROUP) continue;
            var fallback = net.schwarz.rotasutils.core.SettingsTree.get(defaults, row.path());
            var value = net.schwarz.rotasutils.core.SettingsTree.get(root, row.path());
            if (fallback != null && fallback.equals(value)) continue;
            String entry = line(root, row.path()) + (fallback == null ? "  (เพิ่มเอง)" : "  (เดิม " + fallback + ")");
            source.sendSuccess(() -> Component.literal(entry), false);
            if (++shown >= 60) break;
        }
        int total = shown;
        source.sendSuccess(() -> Component.literal(total == 0 ? "ทุกค่าเป็นค่าเริ่มต้น" : "ต่างจากค่าเริ่มต้น " + total + " ค่า"), false);
        return 1;
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
