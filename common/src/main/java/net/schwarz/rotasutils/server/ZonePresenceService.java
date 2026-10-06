package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneEffect;
import net.schwarz.rotasutils.core.ZoneMessages;
import net.schwarz.rotasutils.core.ZoneMovement;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ZonePresenceService {
    private static final int CHECK_INTERVAL = 10;
    private static final int EFFECT_INTERVAL = 40;
    private static final int EFFECT_TICKS = 120;
    private static final int WARN_INTERVAL_TICKS = 40;

    private static final Map<UUID, String> currentZone = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastWarning = new ConcurrentHashMap<>();

    record Titles(String title, String subtitle, String sound) {
    }

    private ZonePresenceService() {
    }

    public static void forget(UUID player) {
        currentZone.remove(player);
        lastWarning.remove(player);
        ZoneVisibilityService.forget(player);
    }

    public static void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()
                || serverPlayer.connection == null || !serverPlayer.isAlive() || serverPlayer.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        RotasData data = RotasData.instance();
        if (data == null) {
            return;
        }
        ZoneVisibilityService.tick(serverPlayer);
        ZoneDef top = data.zones().isEmpty() ? null : ZoneService.select(data.zones().values(),
                serverPlayer.level().dimension().location().toString(), serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ());
        String topId = top == null ? "" : top.id();
        String previousId = currentZone.put(serverPlayer.getUUID(), topId);
        if (!topId.equals(previousId == null ? "" : previousId)) {
            ZoneDef previous = previousId == null || previousId.isEmpty() ? null : data.zone(previousId);
            Titles titles = titles(previous, top);
            if (titles != null) {
                show(serverPlayer, titles);
            }
            if (top != null) ExplorationService.onZoneEntered(serverPlayer, data, top);
        }
        if (top == null) {
            return;
        }
        if (serverPlayer.tickCount % EFFECT_INTERVAL == 0) {
            applyEffects(serverPlayer, top);
        }
        if (exempt(serverPlayer)) {
            return;
        }
        ZoneMovement movement = top.features().movement();
        if (movement.noElytra() && serverPlayer.isFallFlying()) {
            serverPlayer.stopFallFlying();
            warn(serverPlayer, "rotasutils.msg.zone.no_elytra", top);
        }
        if (movement.noFlight() && serverPlayer.getAbilities().flying && !serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
            serverPlayer.getAbilities().flying = false;
            serverPlayer.onUpdateAbilities();
            warn(serverPlayer, "rotasutils.msg.zone.no_flight", top);
        }
    }

    public static boolean pearlBlocked(ServerPlayer owner, Entity pearl) {
        RotasData data = RotasData.instance();
        if (data == null || data.zones().isEmpty() || exempt(owner)) {
            return false;
        }
        ZoneDef landing = ZoneService.select(data.zones().values(), pearl.level().dimension().location().toString(),
                pearl.getX(), pearl.getY(), pearl.getZ());
        ZoneDef from = ZoneService.select(data.zones().values(), owner.level().dimension().location().toString(),
                owner.getX(), owner.getY(), owner.getZ());
        if (!pearlRuleBlocks(landing, from)) {
            return false;
        }
        ZoneDef blocker = landing != null && landing.features().movement().noEnderPearlIn() ? landing : from;
        owner.displayClientMessage(ThaiText.c("rotasutils.msg.zone.pearl_blocked", name(blocker)), true);
        return true;
    }

static Titles titles(ZoneDef previous, ZoneDef current) {
        if (current != null && current.features().messages().hasEnter()) {
            ZoneMessages messages = current.features().messages();
            return new Titles(messages.enterTitle(), messages.enterSubtitle(), messages.sound());
        }
        if (previous != null && !previous.features().messages().leaveTitle().isEmpty()) {
            return new Titles(previous.features().messages().leaveTitle(), "", "");
        }
        return null;
    }

    static boolean pearlRuleBlocks(ZoneDef landing, ZoneDef from) {
        return (landing != null && landing.features().movement().noEnderPearlIn())
                || (from != null && from.features().movement().noEnderPearlIn());
    }

private static void show(ServerPlayer player, Titles titles) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(titles.subtitle())));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(titles.title())));
        if (!titles.sound().isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(titles.sound());
            if (id != null) {
                BuiltInRegistries.SOUND_EVENT.getOptional(id)
                        .ifPresent(sound -> player.playNotifySound(sound, SoundSource.AMBIENT, 1.0f, 1.0f));
            }
        }
    }

    private static void applyEffects(ServerPlayer player, ZoneDef zone) {
        for (ZoneEffect effect : zone.features().effects()) {
            ResourceLocation id = ResourceLocation.tryParse(effect.effect());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.MOB_EFFECT.getOptional(id).ifPresent(type -> player.addEffect(
                    new MobEffectInstance(type, EFFECT_TICKS, effect.amplifier(), true, false, true)));
        }
    }

    private static boolean exempt(ServerPlayer player) {
        return player.hasPermissions(2) && !ZoneGateService.adminTesting(player.getUUID());
    }

    private static void warn(ServerPlayer player, String key, ZoneDef zone) {
        long now = player.level().getGameTime();
        Long last = lastWarning.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < WARN_INTERVAL_TICKS) {
            return;
        }
        lastWarning.put(player.getUUID(), now);
        player.displayClientMessage(ThaiText.c(key, name(zone)), true);
    }

    private static String name(ZoneDef zone) {
        return zone.name().isEmpty() ? zone.id() : zone.name();
    }
}
