package net.schwarz.rotasutils.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.schwarz.rotasutils.client.RotasClient;
import net.schwarz.rotasutils.registry.RotasRegistry;

public final class RotasutilsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RotasClient.init();
        // Fabric fills its registries before the client initializer runs, so renderers are safe here.
        RotasClient.initRenderers();
        BlockRenderLayerMap.INSTANCE.putBlock(RotasRegistry.QUEST_BOARD.get(), RenderType.cutout());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> net.schwarz.rotasutils.client.RotasClientState.reset());
    }
}
