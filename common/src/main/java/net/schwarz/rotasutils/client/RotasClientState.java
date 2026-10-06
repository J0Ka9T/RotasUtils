package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.network.ClientAdminNetwork;
import net.schwarz.rotasutils.network.ClientProgressSync;

@Environment(EnvType.CLIENT)
public final class RotasClientState {
    private RotasClientState() {
    }

    public static void reset() {
        ClientState.reset();
        ClientKernelState.reset();
        ClientProgressSync.reset();
        ClientAdminNetwork.reset();
        net.schwarz.rotasutils.client.inventory.RotasInventoryRenderer.resetScrolls();
        net.schwarz.rotasutils.client.hud.ExoBeamHud.reset();
        net.schwarz.rotasutils.client.render.CameraQuake.resetPunch();
    }
}
