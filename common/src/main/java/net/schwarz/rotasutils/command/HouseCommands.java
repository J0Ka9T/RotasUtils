package net.schwarz.rotasutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.house.*;
import net.schwarz.rotasutils.item.HouseWandItem;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.network.ServerActions;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.RewardService;

import java.util.UUID;

final class HouseCommands {
    private HouseCommands() {}
    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var house = Commands.literal("house")
                .executes(context -> menu(context.getSource()))
                .then(Commands.literal("menu").executes(context -> menu(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("leave").then(id().executes(context -> player(context.getSource(),
                        (p, d) -> HousePlayerService.leave(p, d, value(context))))))
                .then(Commands.literal("buyslot").then(id().executes(context -> player(context.getSource(),
                        (p, d) -> HousePlayerService.buySlot(p, d, value(context))))))
                .then(Commands.literal("member")
                        .then(Commands.literal("add").then(id().then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> { UUID target = profile(context); return player(context.getSource(), (p, d) -> HousePlayerService.addMember(p, d, value(context), target)); }))))
                        .then(Commands.literal("remove").then(id().then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> { UUID target = profile(context); return player(context.getSource(), (p, d) -> HousePlayerService.removeMember(p, d, value(context), target)); })))))
                .then(Commands.literal("evict").requires(HouseCommands::admin)
                        .then(id().executes(context -> player(context.getSource(),
                                (p, d) -> HousePlayerService.evict(p, d, value(context))))))
                .then(Commands.literal("setowner").requires(HouseCommands::admin)
                        .then(id().then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> { UUID target = profile(context); return player(context.getSource(), (p, d) -> HousePlayerService.setOwner(p, d, value(context), target)); }))))
                .then(Commands.literal("here").executes(context -> here(context.getSource())))
                .then(Commands.literal("info").then(id().executes(context -> info(context.getSource(), value(context)))))
                .then(Commands.literal("rent").then(id().executes(context -> rent(context.getSource(), value(context)))))
                .then(Commands.literal("pay").then(id().executes(context -> pay(context.getSource(), value(context)))))
                .then(Commands.literal("buyout").then(id().executes(context -> buyout(context.getSource(), value(context)))))
                .then(Commands.literal("wand").requires(HouseCommands::admin).executes(context -> wand(context.getSource())))
                .then(Commands.literal("remove").requires(HouseCommands::admin)
                        .then(id().executes(context -> remove(context.getSource(), value(context)))))
                .then(Commands.literal("create").requires(HouseCommands::admin)
                        .then(Commands.argument("house", StringArgumentType.word())
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((context, builder) -> { RotasData.get(context.getSource().getServer()).houseConfig().tiers().keySet().forEach(builder::suggest); return builder.buildFuture(); })
                                        .executes(context -> create(context.getSource(), value(context), StringArgumentType.getString(context, "tier"), value(context)))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> create(context.getSource(), value(context), StringArgumentType.getString(context, "tier"),
                                                        StringArgumentType.getString(context, "name")))))));
        root.then(house);
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> id() {
        return Commands.argument("house", StringArgumentType.word()).suggests((context,builder)->{ RotasData.get(context.getSource().getServer()).houses().keySet().forEach(builder::suggest); return builder.buildFuture(); });
    }
    private static String value(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) { return StringArgumentType.getString(context,"house"); }
    private static boolean admin(CommandSourceStack source) { return source.getEntity() instanceof ServerPlayer player && BoardService.isAdmin(player, RotasData.get(source.getServer())); }
    private static String size(HouseBounds b) { return (b.maxX()-b.minX()+1)+"x"+(b.maxY()-b.minY()+1)+"x"+(b.maxZ()-b.minZ()+1); }
    private static String describe(RotasData data, HouseDefinition def) {
        HouseBounds b = def.bounds();
        return def.name()+" ("+def.id()+") ["+data.houseTenancy(def.id()).status().name().toLowerCase()+"] tier="+def.tier()
                +" size="+size(b)+" at "+b.minX()+" "+b.minY()+" "+b.minZ()+(def.enabled()?"":" (disabled)");
    }
    private static int list(CommandSourceStack source) {
        RotasData data=RotasData.get(source.getServer());
        if (data.houses().isEmpty()) { source.sendSuccess(() -> Component.literal("No houses yet. Hold the House Wand, pick two corners, then /rotas house create <id> <tier> [name]."), false); return 0; }
        data.houses().values().forEach(def -> source.sendSuccess(() -> Component.literal(describe(data, def)),false));
        return data.houses().size();
    }
    private static int here(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException(); RotasData data=RotasData.get(player.server);
        HouseDefinition def=HouseRegistry.at(data.houses().values(), player.level().dimension().location().toString(), player.blockPosition());
        if (def==null) { source.sendSuccess(() -> Component.literal("No house here."), false); return 0; }
        source.sendSuccess(() -> Component.literal(describe(data, def)), false); return 1;
    }
    private static int info(CommandSourceStack source,String id) { RotasData data=RotasData.get(source.getServer()); var def=data.house(id); if(def==null){source.sendFailure(Component.literal("Unknown house: "+id));return 0;} var tenancy=data.houseTenancy(id); source.sendSuccess(()->Component.literal(describe(data, def)+" | members "+tenancy.members().size()),false); return 1; }
    private static int wand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException { ServerPlayer player=source.getPlayerOrException(); RewardService.give(player,new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.HOUSE_WAND.get())); return 1; }
    private static int create(CommandSourceStack source,String id,String tier,String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        RotasData data=RotasData.get(player.server);
        HouseAdminService.Action action = ServerActions.houseAdminService(player, data).create(player.getUUID(),
                new HouseAdminService.CreateRequest(id, name.trim(), tier, data.houseConfig().revision()));
        if (!action.success()) { source.sendFailure(Component.literal(action.message())); return 0; }
        HouseDefinition def = data.house(id);
        source.sendSuccess(() -> Component.literal("Created house "+describe(data, def)), true);
        return 1;
    }
    private static int remove(CommandSourceStack source,String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException(); RotasData data=RotasData.get(player.server); HouseDefinition def=data.house(id);
        if(def==null){source.sendFailure(Component.literal("Unknown house: "+id));return 0;}
        HouseAdminService.Action action = removeThroughAuthority(ServerActions.houseAdminService(player, data), player.getUUID(), def);
        if (!action.success()) { source.sendFailure(Component.literal(action.message())); return 0; }
        source.sendSuccess(() -> Component.literal("Removed house "+def.name()+" ("+id+")."), true);
        return 1;
    }
    static HouseAdminService.Action removeThroughAuthority(HouseAdminService service, UUID actor, HouseDefinition definition) {
        if (definition == null) return new HouseAdminService.Action(false, "house.not_found");
        return service.remove(actor, new HouseAdminService.RemoveRequest(definition.id(), definition.revision()));
    }
    private static int rent(CommandSourceStack source,String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return player(source,(p,d)->HousePlayerService.rent(p,d,id)); }
    private static int pay(CommandSourceStack source,String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return player(source,(p,d)->HousePlayerService.pay(p,d,id)); }
    private static int buyout(CommandSourceStack source,String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return player(source,(p,d)->HousePlayerService.buyout(p,d,id)); }
    private static int menu(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException { RotasNetwork.openHouse(source.getPlayerOrException(), ""); return 1; }
    private static UUID profile(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) throw net.minecraft.commands.arguments.EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        return profiles.iterator().next().getId();
    }
    private interface PlayerAction { HousePlayerService.Result run(ServerPlayer player, RotasData data); }
    /** Runs a house action as the calling player; the service already told them the outcome. */
    private static int player(CommandSourceStack source, PlayerAction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        HousePlayerService.Result result=action.run(player, RotasData.get(player.server));
        if(!result.success()){source.sendFailure(Component.literal(result.message()));return 0;}
        source.sendSuccess(()->Component.literal(result.message()),false); return 1;
    }
}
