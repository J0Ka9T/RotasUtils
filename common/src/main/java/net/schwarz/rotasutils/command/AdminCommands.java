package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.server.RotasPermissions.Capability;
import net.schwarz.rotasutils.server.RpgKernel;
import net.schwarz.rotasutils.util.ThaiText;
import java.io.IOException;
import java.util.function.ToIntFunction;

final class AdminCommands {
    private AdminCommands() { }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var admin = Commands.literal("admin").requires(source -> allowed(source, Capability.VIEW))
                .executes(context -> run(context.getSource(), source -> {
                    if (source.getEntity() instanceof ServerPlayer player) { RotasNetwork.openAdminMenu(player); }
                    status(source); return 1;
                }));
        admin.then(Commands.literal("status").executes(context -> run(context.getSource(), AdminCommands::status)));
        admin.then(Commands.literal("import").requires(source -> allowed(source, Capability.EDIT))
                .executes(context -> run(context.getSource(), source -> started(source, kernel(source).importDraft(RotasPermissions.actor(source),
                        () -> allowed(source, Capability.EDIT), result -> report(source, result, ThaiText.t("rotasutils.cmd.admin.op.import")))))));
        admin.then(Commands.literal("export").requires(source -> allowed(source, Capability.AUDIT))
                .executes(context -> run(context.getSource(), source -> {
                    try {
                        var path = net.schwarz.rotasutils.core.ConfigFiles.export(dev.architectury.platform.Platform.getConfigFolder().resolve("rotasutils"),
                                kernel(source).content().definitions().values().stream().map(ContentRegistry.Definition::source).toList(),
                                net.schwarz.rotasutils.server.ConfigService.snapshots(RotasData.get(source.getServer())));
                        say(source, ThaiText.t("rotasutils.cmd.admin.exported", path)); return 1;
                    } catch (IOException error) { throw new IllegalArgumentException(error.getMessage()); }
                })));
        var configuration = Commands.literal("configuration").requires(source -> allowed(source, Capability.EDIT));
        configuration.then(Commands.literal("import").executes(context -> run(context.getSource(), source -> {
            try { net.schwarz.rotasutils.server.ConfigService.importFiles(RotasData.get(source.getServer()), RotasPermissions.actor(source),
                    dev.architectury.platform.Platform.getConfigFolder().resolve("rotasutils")).forEach(line -> say(source, line)); return 1; }
            catch (IOException error) { throw new IllegalArgumentException(error.getMessage()); }
        })));
        for (String operation : java.util.List.of("review", "apply", "discard", "restore")) {
            configuration.then(Commands.literal(operation)
                    .requires(source -> allowed(source, operation.equals("apply") ? Capability.APPLY : operation.equals("restore") ? Capability.ROLLBACK : Capability.EDIT))
                    .then(Commands.argument("domain", StringArgumentType.string()).executes(context -> run(context.getSource(), source -> {
                        String domain = StringArgumentType.getString(context, "domain"), actor = RotasPermissions.actor(source);
                        var data = RotasData.get(source.getServer()); var history = data.configHistory();
                        net.schwarz.rotasutils.server.ConfigService.requireDomain(domain); var draft = history.draft(actor, domain);
                        if (operation.equals("restore")) {
                            if (draft != null) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.admin.draft_exists")); }
                            var revisions = history.revisions(domain); if (revisions.isEmpty()) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.admin.no_previous")); }
                            history.stage(actor, domain, net.schwarz.rotasutils.server.ConfigService.snapshot(data, domain), revisions.get(revisions.size()-1).getCompound("before"), -1);
                            say(source, ThaiText.t("rotasutils.cmd.admin.previous_staged")); return 1;
                        }
                        if (draft == null) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.admin.no_draft_for", domain)); }
                        if (operation.equals("apply")) { net.schwarz.rotasutils.server.ConfigService.checkBoardPermission(source, domain, draft.value()); net.schwarz.rotasutils.server.ConfigService.checkSettingsPermission(source, domain, draft.value()); net.schwarz.rotasutils.server.ConfigService.checkCommandPermission(source, domain, draft.value());net.schwarz.rotasutils.server.ConfigService.apply(source.getServer(), actor, domain, draft.generation()); say(source, ThaiText.t("rotasutils.cmd.admin.applied", domain)); }
                        else if (operation.equals("discard")) { history.discard(actor, domain, draft.generation()); say(source, ThaiText.t("rotasutils.cmd.admin.discarded", domain)); }
                        else {
                            var issues = net.schwarz.rotasutils.server.ConfigService.validate(data, domain, draft.value());
                            issues.forEach(issue -> say(source, issue.field() + ": " + issue.message()));
                            var lines = new net.minecraft.nbt.ListTag();
                            net.schwarz.rotasutils.network.ConfigNetwork.differences("", net.schwarz.rotasutils.server.ConfigService.snapshot(data, domain), draft.value(), lines);
                            for (int i=0;i<lines.size();i++) { say(source, lines.getString(i)); }
                            say(source, ThaiText.t(issues.isEmpty() ? "rotasutils.cmd.admin.review_done" : "rotasutils.cmd.admin.resolve_errors"));
                        }
                        return 1;
                    }))));
        }
        admin.then(configuration);
        admin.then(Commands.literal("history").requires(source -> allowed(source, Capability.AUDIT))
                .executes(context -> run(context.getSource(), source -> {
                    var history = kernel(source).history().revisions();
                    history.stream().skip(Math.max(0, history.size() - 16)).forEach(revision -> say(source,
                            "#" + revision.number() + " <- #" + revision.parent() + " " + revision.reason()
                                    + " actor=" + revision.actor() + " " + java.time.Instant.ofEpochMilli(revision.time())
                                    + " " + revision.hash().substring(0, 12)));
                    return 1;
                })));
        admin.then(Commands.literal("audit").requires(source -> allowed(source, Capability.AUDIT))
                .executes(context -> run(context.getSource(), source -> {
                    var audit = RotasData.get(source.getServer()).auditLog();
                    audit.stream().skip(Math.max(0, audit.size() - 16)).forEach(line -> say(source, line)); return 1;
                })));
        admin.then(Commands.literal("rollback").requires(source -> allowed(source, Capability.ROLLBACK))
                .then(Commands.argument("revision", LongArgumentType.longArg(1)).executes(context -> run(context.getSource(), source -> {
                    var kernel = kernel(source); long target = LongArgumentType.getLong(context, "revision");
                    return started(source, kernel.rollback(target, RotasPermissions.actor(source),
                            () -> allowed(source, Capability.ROLLBACK), result -> report(source, result, ThaiText.t("rotasutils.cmd.admin.op.rollback"))));
                }))));
        admin.then(Commands.literal("show").then(Commands.argument("id", ResourceLocationArgument.id())
                .executes(context -> run(context.getSource(), source -> {
                    var definition = kernel(source).content().definitions().get(new ContentId(ResourceLocationArgument.getId(context, "id").toString()));
                    if (definition == null) { throw new IllegalArgumentException(ThaiText.t("rotasutils.cmd.admin.unknown_definition")); }
                    String json = definition.source().document().toString();
                    say(source, json.length() > 2048 ? json.substring(0, 2048) + ThaiText.t("rotasutils.cmd.admin.truncated") : json);
                    return 1;
                }))));
        var draft = Commands.literal("draft").requires(source -> allowed(source, Capability.EDIT))
                .executes(context -> run(context.getSource(), AdminCommands::status));
        draft.then(Commands.literal("create").executes(context -> run(context.getSource(), source -> {
            var kernel = kernel(source); kernel.checkEditable(); kernel.history().begin(RotasPermissions.actor(source));
            audit(source, "draft_create"); say(source, ThaiText.t("rotasutils.cmd.admin.draft_created", kernel.revision())); return 1;
        })));
        draft.then(Commands.literal("put").then(Commands.argument("json", StringArgumentType.greedyString())
                .executes(context -> run(context.getSource(), source -> {
                    var kernel = kernel(source); kernel.checkEditable();
                    try { kernel.history().put(RotasPermissions.actor(source), ContentPacks.parse(StringArgumentType.getString(context, "json"))); }
                    catch (IOException error) { throw new IllegalArgumentException(error.getMessage()); }
                    audit(source, "draft_put"); say(source, ThaiText.t("rotasutils.cmd.admin.draft_updated")); return 1;
                }))));
        draft.then(Commands.literal("remove").then(Commands.argument("id", ResourceLocationArgument.id())
                .executes(context -> run(context.getSource(), source -> {
                    var kernel = kernel(source); kernel.checkEditable();
                    kernel.history().remove(RotasPermissions.actor(source), new ContentId(ResourceLocationArgument.getId(context, "id").toString()));
                    audit(source, "draft_remove"); say(source, ThaiText.t("rotasutils.cmd.admin.draft_removed")); return 1;
                }))));
        draft.then(Commands.literal("preview").executes(context -> run(context.getSource(), source -> {
            var preview = kernel(source).history().preview(RotasPermissions.actor(source));
            say(source, ThaiText.t("rotasutils.cmd.admin.preview", preview.added().size(), preview.changed().size(), preview.removed().size()));
            preview.added().stream().limit(12).forEach(id -> say(source, "+ " + id));
            preview.changed().stream().limit(12).forEach(id -> say(source, "~ " + id));
            preview.removed().stream().limit(12).forEach(id -> say(source, "- " + id)); return 1;
        })));
        draft.then(Commands.literal("validate").executes(context -> run(context.getSource(), source ->
                started(source, kernel(source).validateDraft(RotasPermissions.actor(source), false,
                        () -> allowed(source, Capability.EDIT), result -> report(source, result, ThaiText.t("rotasutils.cmd.admin.op.validation")))))));
        draft.then(Commands.literal("apply").requires(source -> allowed(source, Capability.APPLY))
                .executes(context -> run(context.getSource(), source ->
                        started(source, kernel(source).validateDraft(RotasPermissions.actor(source), true,
                                () -> allowed(source, Capability.APPLY), result -> report(source, result, ThaiText.t("rotasutils.cmd.admin.op.apply")))))));
        draft.then(Commands.literal("discard").executes(context -> run(context.getSource(), source -> {
            var kernel = kernel(source); kernel.checkEditable(); kernel.history().discard(RotasPermissions.actor(source));
            audit(source, "draft_discard"); say(source, ThaiText.t("rotasutils.cmd.admin.draft_discarded")); return 1;
        })));
        root.then(admin.then(draft));
    }

    private static int status(CommandSourceStack source) {
        var kernel = kernel(source); kernel.diagnostics().forEach(line -> say(source, line));
        var draft = kernel.history().draft(RotasPermissions.actor(source));
        say(source, draft == null ? ThaiText.t("rotasutils.cmd.admin.no_draft")
                : ThaiText.t("rotasutils.cmd.admin.draft_status", draft.base(), draft.generation(), draft.documents().size())
                + (draft.base() == kernel.revision() ? "" : ThaiText.t("rotasutils.cmd.admin.stale"))); return 1;
    }

    private static boolean allowed(CommandSourceStack source, Capability capability) { return RotasPermissions.allowed(source, capability); }
    private static RpgKernel kernel(CommandSourceStack source) {
        var kernel = RotasData.get(source.getServer()).kernel();
        if (kernel == null) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.kernel_not_ready")); }
        return kernel;
    }

    private static int run(CommandSourceStack source, ToIntFunction<CommandSourceStack> operation) {
        try { return operation.applyAsInt(source); }
        catch (IllegalArgumentException | IllegalStateException ex) { source.sendFailure(Component.literal(ex.getMessage())); return 0; }
    }

    private static void audit(CommandSourceStack source, String action) {
        RotasData.get(source.getServer()).audit(java.time.Instant.now() + " actor=" + RotasPermissions.actor(source) + " action=" + action);
    }

    private static void say(CommandSourceStack source, String line) { source.sendSuccess(() -> Component.literal(ThaiText.phrase(line)), false); }
    private static int started(CommandSourceStack source, boolean started) {
        if (!started) { throw new IllegalStateException(ThaiText.t("rotasutils.cmd.admin.op_running")); }
        say(source, ThaiText.t("rotasutils.cmd.admin.validating")); return 1;
    }

    private static void report(CommandSourceStack source, ContentRegistry.Prepared result, String operation) {
        if (result.valid()) { say(source, ThaiText.t("rotasutils.cmd.admin.op_succeeded", operation, kernel(source).revision())); }
        else {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.admin.op_rejected", operation)));
            result.issues().stream().limit(16).forEach(issue -> source.sendFailure(Component.literal(issue.toString())));
        }
    }
}
