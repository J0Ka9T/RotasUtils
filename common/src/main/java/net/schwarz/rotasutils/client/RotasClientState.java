package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.network.ClientAdminNetwork;
import net.schwarz.rotasutils.network.ClientProgressSync;

/**
 * One place that clears everything the client learned from a server.
 *
 * <p>Both loaders call this on logout/disconnect, so switching servers - or returning to the title
 * screen - never leaves server A's content, admin flags or half-assembled packets behind for server
 * B to render. Local preferences (the tracked quest, UI settings) are deliberately untouched.</p>
 */
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
        // Weapon readouts and camera impulses are per-world: a stale gauge or punch must not follow
        // the player to the next server.
        net.schwarz.rotasutils.client.hud.ExoBeamHud.reset();
        net.schwarz.rotasutils.client.render.CameraQuake.resetPunch();
    }
}
