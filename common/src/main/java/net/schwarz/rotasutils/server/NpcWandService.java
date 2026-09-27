package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.util.Ids;

import java.util.Locale;

/** What an NPC Wand click on a mob does. */
public final class NpcWandService {
    private NpcWandService() {
    }

    /**
     * Opens the editor for the clicked mob, turning it into an NPC first when it is not one yet.
     * Shift-clicking an NPC previews its conversation instead. A pending "place on a mob" request
     * from the editor takes priority, so the wand also answers that.
     */
    public static void use(ServerPlayer player, LivingEntity target) {
        RotasData data = RotasData.get(player.server);
        if (!BoardService.isAdmin(player, data)) {
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.npc_admin_only")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (WorldPicker.isPending(player) && WorldPicker.resolveEntity(player, target)) {
            return;
        }
        if (target instanceof Player) {
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.npcwand.players")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        NpcDef npc = boundTo(data, target);
        if (player.isShiftKeyDown()) {
            if (npc == null) {
                player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.npcwand.not_npc")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                NpcConversations.preview(player, data, npc);
            }
            return;
        }
        if (npc == null) {
            npc = create(player, data, target);
        }
        RotasNetwork.openNpcConfig(player, npc);
    }

    /** The NPC bound to this entity, including disabled ones, so the wand can switch them back on. */
    public static NpcDef boundTo(RotasData data, Entity entity) {
        String uuid = entity.getUUID().toString();
        for (NpcDef npc : data.npcs().values()) {
            if (uuid.equals(npc.entityUuid())) {
                return npc;
            }
        }
        return null;
    }

    private static NpcDef create(ServerPlayer player, RotasData data, LivingEntity target) {
        // Entity type names are translation keys a dedicated server cannot resolve, so an unnamed
        // mob is named from its id ("wandering_trader" becomes "Wandering Trader").
        String name = target.hasCustomName() ? target.getCustomName().getString()
                : pretty(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath());
        NpcDef npc = new NpcDef(Ids.unique(name, data.npcs().keySet()));
        npc.setName(name);
        SpawnEggItem egg = SpawnEggItem.byId(target.getType());
        if (egg != null) {
            npc.setIcon(new ItemStack(egg));
        }
        NpcService.bind(data, npc, target);
        data.audit(player.getGameProfile().getName() + " made NPC " + npc.id() + " from " + target.getUUID()
                + " with the NPC wand");
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            RotasNetwork.syncContent(online);
        }
        player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.npcwand.created", name)
                .withStyle(ChatFormatting.GREEN));
        return npc;
    }

    static String pretty(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_./-]+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.isEmpty() ? "NPC" : out.toString();
    }
}
