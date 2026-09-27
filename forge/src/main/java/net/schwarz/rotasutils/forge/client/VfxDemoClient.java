package net.schwarz.rotasutils.forge.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.schwarz.rotasutils.forge.magic.VfxDemo;

import java.io.File;

/** Client half of the dev VFX harness: hides the HUD, sets the camera, takes screenshots on cue. */
public final class VfxDemoClient {
    private static int lastIndex = -1;
    private static int nextShot;
    private static boolean active;
    private static boolean previousHideGui;
    private static CameraType previousCameraType;

    private VfxDemoClient() {
    }

    public static void init() {
        if (VfxDemo.shots.isEmpty()) return;
        MinecraftForge.EVENT_BUS.addListener(VfxDemoClient::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (VfxDemo.finished || mc.level == null || mc.player == null) {
            restore(mc);
            return;
        }
        int index = VfxDemo.current;
        if (index < 0 || index >= VfxDemo.shots.size()) return;
        if (!active) {
            previousHideGui = mc.options.hideGui;
            previousCameraType = mc.options.getCameraType();
            active = true;
        }
        VfxDemo.Shot shot = VfxDemo.shots.get(index);
        mc.options.hideGui = true;
        if (index != lastIndex) {
            lastIndex = index;
            nextShot = 0;
            mc.options.setCameraType(switch (shot.view()) {
                case "back" -> CameraType.THIRD_PERSON_BACK;
                case "front" -> CameraType.THIRD_PERSON_FRONT;
                default -> CameraType.FIRST_PERSON;
            });
        }
        long castAt = VfxDemo.castAt;
        if (castAt < 0 || nextShot >= shot.ticks().length) return;
        long since = mc.level.getGameTime() - castAt;
        if (since >= shot.ticks()[nextShot]) {
            String name = "vfx_" + shot.spell().replace(':', '_') + "_t" + shot.ticks()[nextShot] + ".png";
            Screenshot.grab(new File(mc.gameDirectory.getAbsolutePath()), name, mc.getMainRenderTarget(), message -> { });
            nextShot++;
        }
    }

    private static void restore(Minecraft mc) {
        if (!active) return;
        mc.options.hideGui = previousHideGui;
        mc.options.setCameraType(previousCameraType);
        active = false;
        lastIndex = -1;
        nextShot = 0;
    }
}
