package net.schwarz.rotasutils.house;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a player whose house they just walked into. A house for rent says what it costs and how to
 * take it, so a player finds housing by walking around instead of being told a command; the player's
 * own house greets them; anyone else's names its owner, which also explains why they cannot build.
 */
public final class HousePresenceService {
    private static final int CHECK_INTERVAL = 10;
    private static final Map<UUID, String> current = new ConcurrentHashMap<>();

    private HousePresenceService() {
    }

    public static void forget(UUID player) {
        current.remove(player);
    }

    /** Event hook: runs after each player tick. */
    public static void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()
                || serverPlayer.connection == null || serverPlayer.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        RotasData data = RotasData.instance();
        if (data == null || data.houses().isEmpty()) {
            return;
        }
        HouseDefinition house = HouseRegistry.at(data.houses().values(),
                serverPlayer.level().dimension().location().toString(), serverPlayer.blockPosition());
        String id = house == null ? "" : house.id();
        String previous = current.put(serverPlayer.getUUID(), id);
        if (house == null || id.equals(previous)) {
            return;
        }
        HouseTenancy tenancy = data.houseTenancy(id);
        UUID self = serverPlayer.getUUID();
        String welcome = data.houseSettings(id).welcome();
        if (!welcome.isEmpty()) {
            // The house's own line goes to chat so the price or owner line still fits the action bar.
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(welcome)
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        }
        if (tenancy.status() == HouseStatus.AVAILABLE) {
            HouseConfig config = data.houseConfig();
            HouseTier tier = data.houseTier(house);
            if (tier == null) {
                return;
            }
            serverPlayer.displayClientMessage(ThaiText.c("rotasutils.msg.house.enter.for_rent", house.name(),
                    tier.deposit(), tier.maintenance(),
                    QuestService.formatDuration(config.paymentIntervalMillis() / 1000).trim())
                    .withStyle(ChatFormatting.GREEN), true);
        } else if (self.equals(tenancy.owner()) || tenancy.members().contains(self)) {
            serverPlayer.displayClientMessage(ThaiText.c(tenancy.status() == HouseStatus.OVERDUE
                    ? "rotasutils.msg.house.enter.home_overdue" : "rotasutils.msg.house.enter.home", house.name())
                    .withStyle(tenancy.status() == HouseStatus.OVERDUE ? ChatFormatting.RED : ChatFormatting.AQUA), true);
        } else {
            serverPlayer.displayClientMessage(ThaiText.c("rotasutils.msg.house.enter.other", house.name(),
                    HousePlayerService.name(serverPlayer.server, tenancy.owner())).withStyle(ChatFormatting.GRAY), true);
        }
    }
}
