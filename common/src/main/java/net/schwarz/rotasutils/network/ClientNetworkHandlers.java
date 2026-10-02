package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.fx.FloatingXpText;
import net.schwarz.rotasutils.client.fx.LevelUpFx;
import net.schwarz.rotasutils.client.screen.ScreenRouter;
import net.schwarz.rotasutils.server.Validation;
import net.schwarz.rotasutils.util.Nbt;

/** Client-side receivers. Registered from the client entrypoint only. */
@Environment(EnvType.CLIENT)
public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void register() {
        ClientProgressSync.init();
        ClientAdminNetwork.init();
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.ZONE_VIEW, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> net.schwarz.rotasutils.client.ClientZoneView.apply(tag == null ? new CompoundTag() : tag));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.SYNC_CONTENT, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                ClientState.applyContent(tag == null ? new CompoundTag() : tag);
                // The server sends its feedback before the content, so without this the open
                // screen redraws from the old content and a deleted board or quest stays listed
                // until some later packet happens to refresh it.
                ScreenRouter.refresh(ScreenRouter.CONTENT);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.SYNC_PROGRESS, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                if (tag != null) {
                    ClientProgressSync.legacy(tag);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.OPEN_SCREEN, (buf, context) -> {
            String screenId = buf.readUtf(64);
            CompoundTag payload = buf.readNbt();
            context.queue(() -> ScreenRouter.open(screenId, payload == null ? new CompoundTag() : payload));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.FEEDBACK, (buf, context) -> {
            boolean success = buf.readBoolean();
            String message = buf.readUtf(512);
            context.queue(() -> {
                ClientState.feedback(success, message);
                ScreenRouter.refresh(ScreenRouter.FEEDBACK);
                if (Minecraft.getInstance().screen == null && !message.isEmpty()) {
                    Minecraft.getInstance().gui.setOverlayMessage(
                            net.minecraft.network.chat.Component.literal(message), false);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.SEALED_CRAFT, (buf, context) -> {
            String activity = buf.readUtf(16);
            net.minecraft.world.item.ItemStack stack = buf.readItem();
            String jobName = buf.readUtf(128);
            int level = buf.readVarInt();
            context.queue(() -> net.schwarz.rotasutils.client.hud.SealedCraftToast.show(activity, stack, jobName, level));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.PICK_RESULT, (buf, context) -> {
            String screenKey = buf.readUtf(64);
            String fieldKey = buf.readUtf(128);
            String value = buf.readUtf(256);
            context.queue(() -> ScreenRouter.deliverPick(screenKey, fieldKey, value));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.SYNC_PARTY, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                ClientState.applyParty(tag == null ? new CompoundTag() : tag);
                ScreenRouter.refresh(ScreenRouter.PARTY);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.SYNC_KERNEL_UI, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                net.schwarz.rotasutils.client.ClientKernelState.apply(tag == null ? new CompoundTag() : tag);
                ScreenRouter.refresh(ScreenRouter.KERNEL);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.KERNEL_PREVIEW, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                net.schwarz.rotasutils.client.ClientKernelState.applyPreview(tag == null ? new CompoundTag() : tag);
                ScreenRouter.refresh(ScreenRouter.PREVIEW);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.entity.TetrarchEntity.QUAKE, (buf, context) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float degrees = buf.readFloat();
            double falloff = buf.readDouble();
            context.queue(() -> net.schwarz.rotasutils.entity.ClientFx.quake(x, y, z, degrees, falloff));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.entity.RiftFx.ID, (buf, context) ->
                net.schwarz.rotasutils.entity.RiftFx.receive(buf, context::queue));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.ability.AbilityNet.START, (buf, context) ->
                net.schwarz.rotasutils.client.cinematic.ClientCasts.receiveStart(buf, context::queue));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.ability.AbilityNet.RELEASE, (buf, context) ->
                net.schwarz.rotasutils.client.cinematic.ClientCasts.receiveRelease(buf, context::queue));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.ability.AbilityNet.END, (buf, context) ->
                net.schwarz.rotasutils.client.cinematic.ClientCasts.receiveEnd(buf, context::queue));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.entity.CeroFx.ID, (buf, context) ->
                net.schwarz.rotasutils.entity.CeroFx.receive(buf, context::queue));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.sky.SkyClash.ID, (buf, context) -> {
            net.minecraft.resources.ResourceLocation dimension = buf.readResourceLocation();
            long start = buf.readLong();
            long seed = buf.readLong();
            context.queue(() -> net.schwarz.rotasutils.client.render.SkyClashRenderer.set(dimension, start, seed));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, net.schwarz.rotasutils.sky.SkySunder.ID, (buf, context) -> {
            net.minecraft.resources.ResourceLocation dimension = buf.readResourceLocation();
            long start = buf.readLong();
            long seed = buf.readLong();
            context.queue(() -> net.schwarz.rotasutils.client.render.SkySunderRenderer.set(dimension, start, seed));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, EldritchSkyPacket.ID, (buf, context) -> {
            net.minecraft.resources.ResourceLocation dimension = EldritchSkyPacket.readDimension(buf);
            net.schwarz.rotasutils.sky.EldritchSkyTransition.Snapshot snapshot = EldritchSkyPacket.readSnapshot(buf);
            context.queue(() -> net.schwarz.rotasutils.client.render.EldritchSkyClientState.set(dimension, snapshot));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RotasNetwork.VALIDATION, (buf, context) -> {
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                ClientState.applyIssues(Nbt.loadList(tag == null ? new CompoundTag() : tag,
                        "issues", Validation.Issue::load));
                ScreenRouter.open("validation", new CompoundTag());
            });
        });
    }
}
