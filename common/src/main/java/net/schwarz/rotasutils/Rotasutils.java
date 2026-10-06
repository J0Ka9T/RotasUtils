package net.schwarz.rotasutils;

import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.command.RotasCommands;
import net.schwarz.rotasutils.event.RotasEvents;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Rotasutils {
    public static final String MOD_ID = "rotasutils";
    public static final Logger LOG = LoggerFactory.getLogger("RotasUtils");

    private Rotasutils() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static void init() {
        if (!environmentWired) {
            environmentWired = true;
            net.schwarz.rotasutils.server.MonsterService.environment(net.schwarz.rotasutils.server.ZoneService::environment);
        }
        RotasRegistry.init();
        RotasNetwork.init();
        RotasEvents.init();
        RotasCommands.init();
        net.schwarz.rotasutils.ability.AbilityManager.init();
        LOG.info("RotasUtils common init complete");
    }

    private static boolean environmentWired;
}
