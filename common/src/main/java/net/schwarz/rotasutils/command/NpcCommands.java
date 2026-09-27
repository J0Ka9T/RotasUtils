package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.server.NpcService;
import net.schwarz.rotasutils.server.RotasPermissions;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /rotas npc quick <role> [name]}: a ready service NPC where the admin stands, in one command. It is a
 * villager dressed for the job that stands still and cannot be hurt; the NPC editor (or the NPC wand) refines
 * it afterwards like any other NPC.
 */
final class NpcCommands {
    private NpcCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("npc").requires(source -> RotasPermissions.allowed(source, RotasPermissions.Capability.EDIT))
                .then(Commands.literal("quick")
                        .then(Commands.argument("role", StringArgumentType.word()).suggests(NpcCommands::roles)
                                .executes(context -> quick(context, ""))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> quick(context, StringArgumentType.getString(context, "name")))))));
    }

    private static CompletableFuture<Suggestions> roles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (NpcDef.Role role : NpcDef.Role.values()) builder.suggest(role.name().toLowerCase(Locale.ROOT));
        return builder.buildFuture();
    }

    private static int quick(CommandContext<CommandSourceStack> context, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NpcDef.Role role;
        try {
            role = NpcDef.Role.valueOf(StringArgumentType.getString(context, "role").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            context.getSource().sendFailure(Component.literal("ไม่รู้จักบทบาทนี้"));
            return 0;
        }
        RotasData data = RotasData.get(player.server);
        Villager villager = EntityType.VILLAGER.create(player.serverLevel());
        if (villager == null) return 0;
        villager.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession(role)).setLevel(5));
        villager.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(villager.blockPosition()),
                MobSpawnType.COMMAND, null, null);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession(role)).setLevel(5));
        villager.setYHeadRot(player.getYRot() + 180);
        if (!player.serverLevel().addFreshEntity(villager)) return 0;

        String id = role.name().toLowerCase(Locale.ROOT);
        for (int n = 2; data.npc(id) != null; n++) id = role.name().toLowerCase(Locale.ROOT) + "_" + n;
        NpcDef npc = new NpcDef(id);
        npc.setName(name.isBlank() ? role.display() : name);
        npc.setRole(role);
        npc.setEnabled(true);
        npc.setStandStill(true);
        npc.setInvulnerable(true);
        npc.setShowName(true);
        NpcService.bind(data, npc, villager);
        data.setDirty();
        RotasNetwork.syncContent(player.server);
        data.audit(context.getSource().getTextName() + " placed quick NPC " + id + " as " + role);
        String placed = id;
        context.getSource().sendSuccess(() -> Component.literal("วาง NPC " + npc.name() + " (" + placed
                + ") แล้ว คลิกขวาเพื่อลองได้เลย ปรับรายละเอียดด้วยไม้ NPC"), true);
        return 1;
    }

    private static VillagerProfession profession(NpcDef.Role role) {
        return switch (role) {
            case BLACKSMITH -> VillagerProfession.WEAPONSMITH;
            case ENCHANTER, PRIEST, FORTUNE_TELLER -> VillagerProfession.CLERIC;
            case ALCHEMIST -> VillagerProfession.CLERIC;
            case INNKEEPER -> VillagerProfession.BUTCHER;
            case BANKER, AUCTIONEER, MERCHANT -> VillagerProfession.LIBRARIAN;
            case BOUNTY_MASTER, GUARD -> VillagerProfession.ARMORER;
            case TRAINER, JOB_MASTER -> VillagerProfession.TOOLSMITH;
            case CARTOGRAPHER -> VillagerProfession.CARTOGRAPHER;
            case COLLECTOR -> VillagerProfession.FARMER;
            case STABLE -> VillagerProfession.LEATHERWORKER;
            case CRAFTER -> VillagerProfession.MASON;
            default -> VillagerProfession.NITWIT;
        };
    }
}
