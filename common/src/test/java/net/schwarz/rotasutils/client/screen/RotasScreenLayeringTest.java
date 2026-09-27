package net.schwarz.rotasutils.client.screen;

import net.schwarz.rotasutils.client.screen.admin.HouseManagerScreen;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotasScreenLayeringTest {
    @Test
    void ordinaryScreensKeepTheExistingPanelOrder() {
        assertFalse(new ProbeScreen().panelsAfterContent());
    }

    @Test
    void houseManagerPaintsRowsAfterItsOpaqueFrames() throws Exception {
        Method hook = HouseManagerScreen.class.getDeclaredMethod("renderPanelsAfterContent");
        hook.setAccessible(true);
        assertTrue((boolean) hook.invoke(new HouseManagerScreen()));
    }

    private static final class ProbeScreen extends RotasScreen {
        private ProbeScreen() {
            super("probe", null);
        }

        private boolean panelsAfterContent() {
            return renderPanelsAfterContent();
        }

        @Override
        protected void buildContent() {
        }

        @Override
        protected void renderContent(net.minecraft.client.gui.GuiGraphics graphics,
                                     int mouseX, int mouseY, float partialTick) {
        }
    }
}
