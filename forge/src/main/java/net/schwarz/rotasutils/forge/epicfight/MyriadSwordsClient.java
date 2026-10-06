package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.core.SwordConvergenceTimeline;
import org.lwjgl.glfw.GLFW;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.skill.SkillSlots;

public final class MyriadSwordsClient {
    public static final KeyMapping CAST = new KeyMapping("key.rotasutils.myriad_swords_return",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.rotasutils");

    private MyriadSwordsClient() {
    }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> e.register(CAST));
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("myriad_swords",
                (gui, g, partial, w, h) -> renderHud(g, w, h)));
        MinecraftForge.EVENT_BUS.addListener(MyriadSwordsClient::tick);
    }

    private static void renderHud(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        var patch = mc.player == null ? null
                : yesman.epicfight.world.capabilities.EpicFightCapabilities.getLocalPlayerPatch(mc.player);
        if (mc.options.hideGui || patch == null || !patch.isEpicFightMode()) {
            return;
        }
        var container = patch.getSkill(SkillSlots.IDENTITY);
        if (!(container.getSkill() instanceof MyriadSwordsSkill)) {
            return;
        }
        int size = 24;
        int x = w - 158;
        int y = h - 70;
        g.fill(x - 5, y - 5, x + size + 132, y + size + 5, RotasTheme.HUD_PANEL);
        g.fill(x - 5, y - 5, x + size + 132, y - 4, RotasTheme.HUD_PANEL_EDGE);
        g.fill(x - 5, y + size + 4, x + size + 132, y + size + 5, RotasTheme.HUD_PANEL_EDGE);
        g.fill(x - 5, y - 5, x - 4, y + size + 5, RotasTheme.HUD_PANEL_EDGE);
        g.fill(x + size + 131, y - 5, x + size + 132, y + size + 5, RotasTheme.HUD_PANEL_EDGE);
        g.blit(container.getSkill().getSkillTexture(), x, y, size, size, 0, 0, 64, 64, 64, 64);

        float progress;
        int ring;
        if (container.isActivated()) {
            int age = SwordConvergenceTimeline.LIFE - container.getRemainDuration();
            progress = Mth.clamp(age / (float) SwordConvergenceTimeline.LIFE, 0, 1);
            ring = RotasTheme.HUD_ACCENT;
        } else {
            float max = Math.max(1, container.getMaxResource());
            progress = Mth.clamp(1 - (max - container.getResource()) / max, 0, 1);
            ring = container.getStack() > 0 ? RotasTheme.HUD_ACCENT : RotasTheme.HUD_ACCENT_DIM;
        }
        arc(g, x + size / 2, y + size / 2, size / 2f + 3, 210, 300, RotasTheme.HUD_TRACK);
        arc(g, x + size / 2, y + size / 2, size / 2f + 3, 210, 300 * progress, ring);

        String status;
        if (container.isActivated()) {
            int age = SwordConvergenceTimeline.LIFE - container.getRemainDuration();
            status = Component.translatable(age < SwordConvergenceTimeline.FREEZE
                    ? "skill.rotasutils.myriad_swords_return.summoning"
                    : age < SwordConvergenceTimeline.RELEASE
                    ? "skill.rotasutils.myriad_swords_return.silence"
                    : "skill.rotasutils.myriad_swords_return.release").getString();
        } else {
            status = container.getStack() > 0 ? CAST.getTranslatedKeyMessage().getString()
                    : String.format(java.util.Locale.ROOT, "%.1fs",
                    Math.max(0, container.getMaxResource() - container.getResource()));
        }
        g.drawString(mc.font, Component.translatable("skill.rotasutils.myriad_swords_return"), x + size + 6, y + 1,
                RotasTheme.HUD_TEXT, false);
        g.drawString(mc.font, status, x + size + 6, y + 13, RotasTheme.HUD_ACCENT, false);
    }

    private static void arc(GuiGraphics g, int cx, int cy, float radius, float start, float sweep, int colour) {
        int steps = Math.max(6, (int) (Math.abs(sweep) / 5));
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(start + sweep * i / steps);
            int px = Math.round(cx + (float) Math.cos(angle) * radius);
            int py = Math.round(cy + (float) Math.sin(angle) * radius);
            g.fill(px - 1, py - 1, px + 1, py + 1, colour);
        }
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        while (CAST.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || mc.isPaused()) {
                continue;
            }
            var engine = ClientEngine.getInstance();
            var patch = mc.player == null ? null
                    : yesman.epicfight.world.capabilities.EpicFightCapabilities.getLocalPlayerPatch(mc.player);
            if (patch == null || !patch.isEpicFightMode()) {
                continue;
            }
            var container = patch.getSkill(SkillSlots.IDENTITY);
            if (container.getSkill() instanceof MyriadSwordsSkill) {
                container.sendCastRequest(patch, engine.controlEngine);
            }
        }
    }
}
