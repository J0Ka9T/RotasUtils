package net.schwarz.rotasutils.smoke;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.core.*;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ConfigService;
import net.schwarz.rotasutils.server.Validation;
import java.util.Map;

final class UsabilitySmoke {
    private static final String ACTOR = "usability-smoke";
    static void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rotas_usability_smoke").requires(source -> source.hasPermission(4)).executes(context -> {
            try {
                var server = context.getSource().getServer(); var data = RotasData.get(server); var history = data.configHistory();
                var old = history.draft(ACTOR, "settings"); if (old != null) { history.discard(ACTOR, "settings", old.generation()); }
                var before = ConfigService.snapshot(data, "settings"); var edited = before.copy(); edited.putInt("max_active", before.getInt("max_active") + 1);
                history.stage(ACTOR, "settings", before, edited, -1);
                require(ConfigService.snapshot(data,"settings").equals(before), "save draft leaves live values unchanged");
                require(ConfigService.validate(data,"settings",edited).isEmpty(), "valid settings accepted");
                var invalid = edited.copy(); invalid.putDouble("party_radius", Double.NaN);
                require(ConfigService.validate(data,"settings",invalid).stream().anyMatch(i -> i.severity()==Validation.Severity.ERROR), "nonfinite input rejected");
                ConfigService.apply(server,ACTOR,"settings",0);
                require(data.serverSettings().maxActiveQuests()==edited.getInt("max_active"), "apply changed live settings");
                history.stage(ACTOR,"settings",ConfigService.snapshot(data,"settings"),before,-1); ConfigService.apply(server,ACTOR,"settings",0);
                require(ConfigService.snapshot(data,"settings").equals(before), "restore previous values");
                history.stage(ACTOR,"settings",before,edited,-1);
                var restored=ConfigHistory.load(history.save(),()->{});
                require(restored.draft(ACTOR,"settings").value().equals(edited),"draft survives serialization");
                var path=ConfigFiles.export(server.getServerDirectory().toPath().resolve("usability-export"),
                        data.kernel().content().definitions().values().stream().map(ContentRegistry.Definition::source).toList(),ConfigService.snapshots(data));
                var exported=ConfigFiles.readLegacy(path);
                require(exported.containsKey("settings")&&exported.containsKey("progression"),"legacy export domains roundtrip");
                require(ConfigService.validate(data,"progression",exported.get("progression")).stream().noneMatch(i->i.severity()==Validation.Severity.ERROR),"exported progression validates");
                var prepared=ContentPacks.read(path.resolve("packs"),new ContentRegistry(new ConditionEngine(net.schwarz.rotasutils.server.KernelPlayerContext.requirementAdapters()),new ActionEngine(Map.of())));
                require(prepared.valid(),"exported kernel content validates: "+prepared.issues());
                var source= context.getSource().withPermission(0);
                boolean denied;
                try { denied = dispatcher.execute("rotas admin configuration apply settings",source)==0; }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
                require(denied,"nonadmin cannot apply");
                NpcSmoke.run(server);
                net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_USABILITY_SMOKE_PASS"); return 1;
            } catch(Exception|AssertionError error) { net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_USABILITY_SMOKE_FAIL",error); return 0; }
        }));
        dispatcher.register(Commands.literal("rotas_usability_restart_smoke").requires(source->source.hasPermission(4)).executes(context->{
            var data=RotasData.get(context.getSource().getServer());
            require(data.configHistory().draft(ACTOR,"settings")!=null,"private configuration draft persisted across restart");
            require(!data.configHistory().revisions("settings").isEmpty(),"configuration revisions persisted");
            net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_USABILITY_RESTART_PASS");return 1;
        }));
    }
    private static void require(boolean condition,String message){if(!condition){throw new AssertionError(message);}}
}
