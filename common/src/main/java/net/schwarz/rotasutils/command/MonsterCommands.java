package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.util.ThaiText;

final class MonsterCommands {
    private MonsterCommands() { }
    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> monster = Commands.literal("monster")
                .requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.VIEW));
        monster.then(Commands.literal("inspect").then(Commands.argument("target", EntityArgument.entity())
                .executes(context -> execute(context, "inspect", null))));
        monster.then(Commands.literal("clear").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> execute(context, "clear", null))));
        LiteralArgumentBuilder<CommandSourceStack> assign = Commands.literal("assign")
                .requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT));
        assign.then(Commands.argument("target", EntityArgument.entity())
                .then(Commands.argument("profile", ResourceLocationArgument.id())
                        .executes(context -> execute(context, "assign", null))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10000))
                                .executes(context -> execute(context, "assign", IntegerArgumentType.getInteger(context, "level"))))));
        monster.then(assign);
        monster.then(Commands.literal("level").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                .then(Commands.argument("target", EntityArgument.entity())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10000))
                                .executes(context -> relevel(context, IntegerArgumentType.getInteger(context, "level"))))));
        root.then(monster);
    }

    private static int relevel(CommandContext<CommandSourceStack> context, int level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(EntityArgument.getEntity(context, "target") instanceof Mob mob)) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.monster.not_mob")));
            return 0;
        }
        RotasData data = RotasData.get(source.getServer());
        if (data.kernel() == null) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel_not_ready")));
            return 0;
        }
        var service = data.kernel().monsters();
        try {
            service.join(mob, "COMMAND", true);
            var state = service.relevel(mob, level, true);
            if (state == null) {
                source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.monster.no_level")));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(ThaiText.t("rotasutils.cmd.monster.level_set", state.level(), state.name())), false);
            data.audit(java.time.Instant.now() + " actor=" + RotasPermissions.actor(source)
                    + " action=monster_level level=" + state.level() + " uuid=" + mob.getUUID());
            return 1;
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.monster.relevel_rejected", failure.getMessage())));
            return 0;
        }
    }
    private static int execute(CommandContext<CommandSourceStack> context, String operation, Integer level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        if (!(EntityArgument.getEntity(context, "target") instanceof Mob mob)) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.monster.not_mob"))); return 0;
        }
        var data = RotasData.get(source.getServer());
        if (data.kernel() == null) { source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.kernel_not_ready"))); return 0; }
        var service = data.kernel().monsters();
        try {
            service.join(mob, "COMMAND", true);
            if (operation.equals("assign")) {
                if (service.peek(mob) != null) { service.clear(mob); }
                try {
                    service.assign(mob, new ContentId(ResourceLocationArgument.getId(context, "profile").toString()), level, "COMMAND");
                } catch (RuntimeException rejected) {
                    service.join(mob, "COMMAND", false);
                    throw rejected;
                }
            } else if (operation.equals("clear")) { service.clear(mob); }
            var state = service.peek(mob);
            var boss = state == null ? null : data.kernel().bosses().definition(state);
            String description = state == null ? ThaiText.t("rotasutils.cmd.monster.unassigned", mob.getUUID())
                    : ThaiText.t("rotasutils.cmd.monster.prefix", mob.getUUID()) + " profile=" + state.profile() + " level=" + state.level()
                    + " tier=" + state.tier() + " affixes=" + state.affixes() + " xp=" + state.xp()
                    + (boss == null ? "" : " boss=" + boss.id() + " phase=" + state.runtime().getInt("boss:phase")
                    + " contributors=" + data.kernel().bosses().contributions(state).size());
            source.sendSuccess(() -> Component.literal(description), false);
            if (!operation.equals("inspect")) {
                data.audit(java.time.Instant.now() + " actor=" + RotasPermissions.actor(source)
                        + " action=monster_" + operation + " " + description);
            }
            return 1;
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal(ThaiText.t("rotasutils.cmd.monster.rejected", failure.getMessage()))); return 0;
        }
    }
}
