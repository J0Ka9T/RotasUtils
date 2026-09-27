package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.RpgKernel;
import net.schwarz.rotasutils.util.ThaiText;

final class KernelCommands {
    private KernelCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("reload").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source,
                        net.schwarz.rotasutils.server.RotasPermissions.Capability.RELOAD))
                .executes(context -> reload(context.getSource(), false)));
        root.then(Commands.literal("debug").requires(source -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source, net.schwarz.rotasutils.server.RotasPermissions.Capability.EDIT))
                .executes(context -> diagnostics(context.getSource()))
                .then(Commands.literal("content")
                        .then(Commands.argument("id", ResourceLocationArgument.id()).executes(context -> {
                            CommandSourceStack source = context.getSource();
                            RpgKernel kernel = kernel(source);
                            if (kernel == null) { return 0; }
                            try {
                                var id = new ContentId(ResourceLocationArgument.getId(context, "id").toString());
                                var definition = kernel.content().definitions().get(id);
                                if (definition == null) {
                                    source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel.unknown_definition", id)));
                                    return 0;
                                }
                                source.sendSuccess(() -> Component.literal(id + " " + definition.kind()
                                        + " enabled=" + definition.enabled() + " layer=" + definition.source().layer()
                                        + " source=" + definition.source().name()), false);
                                return 1;
                            } catch (IllegalArgumentException ex) {
                                source.sendFailure(Component.literal(ex.getMessage()));
                                return 0;
                            }
                        })))
                .then(Commands.literal("reward")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .then(Commands.argument("occurrence", StringArgumentType.word()).executes(context -> {
                                            var source = context.getSource();
                                            RpgKernel kernel = kernel(source);
                                            if (kernel == null) { return 0; }
                                            try {
                                                var target = EntityArgument.getPlayer(context, "target");
                                                var id = new ContentId(ResourceLocationArgument.getId(context, "id").toString());
                                                String occurrence = StringArgumentType.getString(context, "occurrence");
                                                var result = kernel.grant(target, id, "admin:" + occurrence);
                                                RotasData.get(source.getServer()).audit(java.time.Instant.now()
                                                        + " actor=" + source.getTextName() + " reward=" + id + " target="
                                                        + target.getUUID() + " occurrence=" + occurrence + " result=" + result);
                                                RotasNetwork.syncProgress(target);
                                                source.sendSuccess(() -> Component.literal(result.name()), false);
                                                return result == net.schwarz.rotasutils.core.RewardEngine.Result.GRANTED ? 1 : 0;
                                            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
                                                source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel.reward_rejected", ex.getMessage())));
                                                return 0;
                                            }
                                        }))))));
    }

    private static RpgKernel kernel(CommandSourceStack source) {
        RpgKernel kernel = RotasData.get(source.getServer()).kernel();
        if (kernel == null) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel_not_ready")));
        }
        return kernel;
    }

    static int reload(CommandSourceStack source, boolean validateOnly) {
        RpgKernel kernel = kernel(source);
        if (kernel == null) { return 0; }
        if (!kernel.reload(validateOnly, net.schwarz.rotasutils.server.RotasPermissions.actor(source),
                () -> net.schwarz.rotasutils.server.RotasPermissions.allowed(source,
                        net.schwarz.rotasutils.server.RotasPermissions.Capability.RELOAD),
                result -> report(source, kernel, result, validateOnly))) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel.load_running")));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(ThaiText.t(validateOnly ? "rotasutils.cmd.kernel.validating"
                : "rotasutils.cmd.kernel.reloading")), false);
        return 1;
    }

    private static void report(CommandSourceStack source, RpgKernel kernel, ContentRegistry.Prepared result,
                               boolean validateOnly) {
        if (result.valid()) {
            source.sendSuccess(() -> Component.literal(ThaiText.t(validateOnly ? "rotasutils.cmd.kernel.valid"
                    : "rotasutils.cmd.kernel.applied", result.snapshot().definitions().size(), kernel.revision())), false);
            if (!validateOnly) {
                RotasData.get(source.getServer()).audit(java.time.Instant.now() + " actor=" + source.getTextName()
                        + " action=kernel_reload revision=" + kernel.revision() + " hash=" + kernel.content().hash());
            }
        } else {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel.failed", result.issues().size())));
            result.issues().stream().limit(16).forEach(issue -> source.sendFailure(Component.literal(issue.toString())));
        }
    }

    private static int diagnostics(CommandSourceStack source) {
        RpgKernel kernel = kernel(source);
        if (kernel == null) { return 0; }
        kernel.diagnostics().forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
