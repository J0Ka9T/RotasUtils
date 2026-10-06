package net.schwarz.rotasutils.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.schwarz.lenlorui.ui.UiRenderQuality;
import com.schwarz.lenlorui.ui.UiRenderSettings;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.schwarz.rotasutils.network.RotasNetwork;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class RotasClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.rotasutils.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.rotasutils");
    private static boolean initialized;
    private static boolean renderersInitialized;

    private RotasClient() {
    }

    public static void initRenderers() {
        if (renderersInitialized) {
            return;
        }
        renderersInitialized = true;
        dev.architectury.registry.client.rendering.BlockEntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.WAYSTONE_BLOCK_ENTITY.get(),
                net.schwarz.rotasutils.client.render.WaystoneRenderer::new);
        dev.architectury.registry.client.rendering.BlockEntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.RUNE_ALTAR_BLOCK_ENTITY.get(),
                net.schwarz.rotasutils.client.render.RuneAltarRenderer::new);
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        UiRenderSettings.quality(UiRenderQuality.BALANCED);
        UiRenderSettings.adaptiveQuality(true);
        RotasNetwork.initClient();
        KeyMappingRegistry.register(OPEN_MENU);
        ClientTickEvent.CLIENT_POST.register(RotasClient::onClientTick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.screen.ScreenRouter::tick);
        ClientTickEvent.CLIENT_POST.register(QuestNavigator::tick);
        dev.architectury.event.events.client.ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(
                player -> net.schwarz.rotasutils.client.screen.ScreenRouter.clearDeferred());
        net.schwarz.rotasutils.entity.ClientFx.install((x, y, z, degrees, falloff) -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                net.schwarz.rotasutils.client.render.CameraQuake.impulse(degrees,
                        Math.sqrt(player.distanceToSqr(x, y, z)), falloff);
            }
        });
        net.schwarz.rotasutils.entity.ClientFx.installBeamState(
                net.schwarz.rotasutils.client.hud.ExoBeamHud::tick);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.RIFT_PORTAL,
                net.schwarz.rotasutils.client.render.RiftPortalRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.RIFT_WANDERER,
                net.schwarz.rotasutils.client.render.RiftWandererRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.RIFT_CONVERGENCE,
                net.schwarz.rotasutils.client.render.RiftConvergenceRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.CELESTIAL_FX,
                net.schwarz.rotasutils.client.render.AbyssFxRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.TETRARCH,
                net.schwarz.rotasutils.client.render.TetrarchRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.VOID_GRASP,
                net.schwarz.rotasutils.client.render.VoidGraspRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.ZENITH_BLADE,
                net.schwarz.rotasutils.client.render.ZenithBladeRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.EXO_BEAM,
                net.schwarz.rotasutils.client.render.ExoBeamRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.EXO_CERO_MUZZLE,
                net.schwarz.rotasutils.client.render.ExoCeroMuzzleRenderer::new);
        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.EXCALIBUR_SLASH,
                net.schwarz.rotasutils.client.render.ExcaliburSlashRenderer::new);
        net.schwarz.rotasutils.client.render.RiftFxRenderer.install();
        net.schwarz.rotasutils.client.render.CeroFxRenderer.install();
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.RiftFxRenderer::tick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.CeroFxRenderer::tick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.cinematic.ClientCasts::tick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.SkyClashRenderer::tick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.SkyClashCinematic::tick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.SkySunderRenderer::tick);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.EldritchSkyCinema::tickEmbers);
        ClientTickEvent.CLIENT_POST.register(net.schwarz.rotasutils.client.render.NightSkyMeteorRenderer::tick);
        dev.architectury.registry.client.particle.ParticleProviderRegistry.register(
                net.schwarz.rotasutils.registry.RotasRegistry.RIFT_EMBER,
                sprites -> new net.schwarz.rotasutils.client.render.RiftEmberParticle.Provider(sprites));
        RefineTooltip.register();
        net.schwarz.rotasutils.client.WorthTooltip.register();
        dev.architectury.event.events.client.ClientGuiEvent.RENDER_HUD.register((graphics, tickDelta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            net.schwarz.rotasutils.client.render.EldritchSkyLetterbox.render(graphics, tickDelta);
            net.schwarz.rotasutils.client.render.SkyClashCinematic.renderHud(graphics, tickDelta);
            net.schwarz.rotasutils.client.render.SkyClashRenderer.renderHud(graphics, tickDelta);
            net.schwarz.rotasutils.client.render.SkySunderRenderer.renderHud(graphics, tickDelta);
            if (minecraft.screen == null && !minecraft.options.hideGui) {
                net.schwarz.rotasutils.client.hud.RotasHudRenderer.renderMonsterTarget(graphics, minecraft);
                net.schwarz.rotasutils.client.hud.ExoBeamHud.render(graphics, minecraft);
                net.schwarz.rotasutils.client.hud.SealedCraftToast.render(graphics, graphics.guiWidth());
            }
        });
        dev.architectury.event.events.client.ClientGuiEvent.RENDER_POST.register((screen, graphics, mouseX, mouseY, delta) ->
                net.schwarz.rotasutils.client.hud.SealedCraftToast.render(graphics, screen.width));
    }

    private static void onClientTick(Minecraft minecraft) {
        net.schwarz.rotasutils.network.ClientProgressSync.tick();
        if (minecraft.player == null) {
            return;
        }
        while (OPEN_MENU.consumeClick()) {
            RotasNetwork.sendAction("open_menu");
        }
        wandHint(minecraft);
        zoneWandHint(minecraft);
        ClientZoneView.tick(minecraft);
    }

    private static int wandHintTicks;

    private static void wandHint(Minecraft minecraft) {
        if (!ClientState.admin() || minecraft.screen != null
                || !minecraft.player.getMainHandItem().is(net.schwarz.rotasutils.registry.RotasRegistry.NPC_WAND.get())) {
            wandHintTicks = 0;
            return;
        }
        if (wandHintTicks++ % 10 != 0) {
            return;
        }
        String hint;
        if (minecraft.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity target
                && !(target instanceof net.minecraft.world.entity.player.Player)) {
            String uuid = target.getUUID().toString();
            var npc = ClientState.npcs().values().stream().filter(def -> uuid.equals(def.entityUuid())).findFirst().orElse(null);
            hint = npc != null
                    ? net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.npc_edit", npc.name())
                    : net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.npc_make", target.getName().getString());
        } else {
            hint = net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.npc_idle");
        }
        minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(hint)
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
    }

    private static int zoneHintTicks;

    private static void zoneWandHint(Minecraft minecraft) {
        if (!ClientState.admin() || minecraft.screen != null
                || (!minecraft.player.getMainHandItem().is(net.schwarz.rotasutils.registry.RotasRegistry.ZONE_WAND.get())
                        && !minecraft.player.getOffhandItem().is(net.schwarz.rotasutils.registry.RotasRegistry.ZONE_WAND.get()))) {
            zoneHintTicks = 0;
            return;
        }
        if (zoneHintTicks++ % 10 != 0) {
            return;
        }
        net.minecraft.world.item.ItemStack wand = minecraft.player.getMainHandItem()
                .is(net.schwarz.rotasutils.registry.RotasRegistry.ZONE_WAND.get())
                ? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
        var mode = net.schwarz.rotasutils.item.ZoneWandItem.mode(wand);
        var points = net.schwarz.rotasutils.item.ZoneWandItem.points(wand);
        String hint;
        if (minecraft.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity target) {
            int level = net.schwarz.rotasutils.client.hud.MobLevelName.levelFrom(target.getName().getString(),
                    ClientState.levelConfig().mobLevel().nameFormat());
            hint = level > 0
                    ? net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_mob", target.getName().getString())
                    : net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_mob_none");
        } else if (!points.isEmpty() && mode == net.schwarz.rotasutils.item.ZoneWandItem.Mode.BOX) {
            var corner = points.get(0);
            hint = net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_box", corner.getX(), corner.getY(), corner.getZ());
        } else if (!points.isEmpty()) {
            var first = points.get(0);
            hint = points.size() >= 3
                    ? net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_outline_close", points.size(), first.getX(), first.getZ())
                    : net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_outline_more", points.size());
        } else {
            var zone = ClientState.zone(net.schwarz.rotasutils.item.ZoneWandItem.zone(wand));
            if (zone == null) {
                zone = ClientState.zone(ClientState.selectedZone());
            }
            String modeText = net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_mode", mode.label());
            hint = zone != null
                    ? net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_adding", zone.name(), zone.levelLabel(),
                            zone.danger().label(), zone.areaLabel(), modeText)
                    : net.schwarz.rotasutils.client.screen.L.t("rotasutils.hint.zone_idle", modeText);
        }
        minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(hint)
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
    }
}
