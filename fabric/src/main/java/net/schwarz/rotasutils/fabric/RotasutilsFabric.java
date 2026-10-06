package net.schwarz.rotasutils.fabric;

import net.schwarz.rotasutils.Rotasutils;
import net.fabricmc.api.ModInitializer;

public final class RotasutilsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Rotasutils.init();
        MonsterFabricEvents.init();
    }
}
