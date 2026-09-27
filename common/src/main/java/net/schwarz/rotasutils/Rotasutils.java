package net.schwarz.rotasutils;

import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.command.RotasCommands;
import net.schwarz.rotasutils.event.RotasEvents;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RotasUtils - quest board, level and skill tree RPG framework.
 *
 * <p>All progression state is server authoritative. The client only ever renders
 * snapshots pushed to it by {@link RotasNetwork} and asks the server to perform
 * actions; it never decides an outcome on its own.
 */
public final class Rotasutils {
    public static final String MOD_ID = "rotasutils";
    public static final Logger LOG = LoggerFactory.getLogger("RotasUtils");

    private Rotasutils() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static void init() {
        // The zone provider must be registered before the first MonsterService is constructed,
        // which happens at server start; init runs at mod load, so this is the only safe window.
        // Guarded so a second init (or a re-entrant loader call) cannot trip the frozen check.
        if (!environmentWired) {
            environmentWired = true;
            net.schwarz.rotasutils.server.MonsterService.environment(net.schwarz.rotasutils.server.ZoneService::environment);
        }
        RotasRegistry.init();
        RotasNetwork.init();
        RotasEvents.init();
        RotasCommands.init();
        LOG.info("RotasUtils common init complete");
    }

    private static boolean environmentWired;
}
