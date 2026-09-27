package net.schwarz.rotasutils.fabric;

import net.schwarz.rotasutils.Rotasutils;
import net.fabricmc.api.ModInitializer;

public final class RotasutilsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Rotasutils.init();
        MonsterFabricEvents.init();
    }
}
