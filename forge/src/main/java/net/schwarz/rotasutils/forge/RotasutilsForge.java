package net.schwarz.rotasutils.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.registry.RotasRegistry;

@Mod(Rotasutils.MOD_ID)
public final class RotasutilsForge {
    public RotasutilsForge(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Rotasutils.MOD_ID, modEventBus);

        net.schwarz.rotasutils.server.forge.MonsterStorageImpl.init(modEventBus);
        Rotasutils.init();
        MonsterForgeEvents.init();
        // The Celestial spell school needs Iron's Spells; its classes are never touched without it.
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            net.schwarz.rotasutils.forge.magic.CelestialMagic.init(modEventBus);
            net.schwarz.rotasutils.forge.magic.AbyssMagic.init(modEventBus);
            net.schwarz.rotasutils.forge.magic.VfxDemo.init();
        }
        // Extra skill-book skills; their classes are never touched without Epic Fight.
        if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) {
            net.schwarz.rotasutils.forge.epicfight.RotasEpicFight.init(modEventBus);
        }

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientOnly.init(modEventBus));
    }

    /**
     * Isolates physical-client class references so a dedicated server never loads
     * Minecraft client classes while verifying this entrypoint.
     */
    private static final class ClientOnly {
        private ClientOnly() {
        }

        private static void init(IEventBus modEventBus) {
            // Architectury collects key mappings before Forge fires its registration
            // event, so this bootstrap must not be deferred to FMLClientSetupEvent.
            net.schwarz.rotasutils.client.RotasClient.init();
            net.schwarz.rotasutils.forge.client.RotasForgeHudEvents.register();
            if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
                net.schwarz.rotasutils.forge.client.VfxDemoClient.init();
            }
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                            net.schwarz.rotasutils.client.RotasClientState.reset());
            modEventBus.addListener(ClientOnly::onClientSetup);
        }

        // Forge 1.20.1 has no non-deprecated replacement for ItemBlockRenderTypes.setRenderLayer.
        @SuppressWarnings("removal")
        private static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemBlockRenderTypes.setRenderLayer(RotasRegistry.QUEST_BOARD.get(), RenderType.cutout());
                // Registries are only populated by now; RotasClient.init runs far earlier than this.
                net.schwarz.rotasutils.client.RotasClient.initRenderers();
            });
        }
    }
}
