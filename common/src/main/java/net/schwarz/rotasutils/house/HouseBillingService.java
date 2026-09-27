package net.schwarz.rotasutils.house;

import net.minecraft.server.MinecraftServer;
import net.schwarz.rotasutils.data.RotasData;

import java.util.ArrayList;

public final class HouseBillingService {
    private static int cursor;
    private HouseBillingService() {}
    public static void tick(MinecraftServer server, RotasData data, long now) {
        var ids = new ArrayList<>(data.houses().keySet());
        if (ids.isEmpty()) return;
        int work = Math.min(64, ids.size());
        for (int offset=0; offset<work; offset++) {
            String id=ids.get((cursor+offset)%ids.size()); HouseDefinition def=data.house(id); HouseTenancy tenancy=data.houseTenancy(id);
            // A tenancy whose house was deleted would NPE here and abort the whole tick, which stops
            // billing for every other house too.
            if (def==null) continue;
            if (!def.enabled() || tenancy.status()==HouseStatus.AVAILABLE || tenancy.status()==HouseStatus.BOUGHT_OUT) continue;
            if (tenancy.status()==HouseStatus.OVERDUE) {
                HouseTenancy repossessed=HouseService.repossessIfDue(tenancy,now);
                if(repossessed!=tenancy){data.putHouseTenancy(id,repossessed); data.audit(java.time.Instant.now()+" action=house_repossess "+id);} continue;
            }
            HouseTier tier=data.houseTier(def); if(tier==null) continue;
            if (now < tenancy.nextPaymentAt()) {
                remindIfShort(server, data, def, tenancy, tier, now);
                continue;
            }
            var profile=data.progress(tenancy.owner()); long balance=profile.rpg().currency(data.houseConfig().currency());
            if(balance>=tier.maintenance()) {
                profile.rpg().currency(data.houseConfig().currency(),-tier.maintenance()); profile.markDirty();
                data.putHouseTenancy(id,new HouseTenancy(HouseStatus.ACTIVE,tenancy.owner(),tenancy.members(),
                        Math.addExact(now,data.houseConfig().paymentIntervalMillis()),0,0,tenancy.purchasedMemberSlots(),tenancy.revision()+1));
            } else {
                data.putHouseTenancy(id,new HouseTenancy(HouseStatus.OVERDUE,tenancy.owner(),tenancy.members(),tenancy.nextPaymentAt(),
                        Math.addExact(now,data.houseConfig().graceMillis()),tier.maintenance(),tenancy.purchasedMemberSlots(),tenancy.revision()+1));
                var online=server.getPlayerList().getPlayer(tenancy.owner());
                if(online!=null) online.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.house.overdue",
                        def.name(), tier.maintenance(),
                        net.schwarz.rotasutils.server.QuestService.formatDuration(data.houseConfig().graceMillis()/1000).trim())
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
        cursor=(cursor+work)%ids.size();
    }

    /** House id + due time already warned, so each coming payment is announced once per session. */
    private static final java.util.Set<String> reminded = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Rent is taken from the wallet automatically, so a reminder only matters when the wallet will not
     * cover it. Sent once per payment, inside the configured reminder lead, to the owner if online.
     */
    private static void remindIfShort(MinecraftServer server, RotasData data, HouseDefinition def, HouseTenancy tenancy,
                                      HouseTier tier, long now) {
        HouseConfig config = data.houseConfig();
        if (config.reminderLeadMillis() <= 0 || now < tenancy.nextPaymentAt() - config.reminderLeadMillis()) return;
        var online = server.getPlayerList().getPlayer(tenancy.owner());
        if (online == null) return;
        long balance = data.progress(tenancy.owner()).rpg().currency(config.currency());
        if (balance >= tier.maintenance() || !reminded.add(def.id() + "@" + tenancy.nextPaymentAt())) return;
        online.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.house.reminder", def.name(),
                tier.maintenance(), balance,
                net.schwarz.rotasutils.server.QuestService.formatDuration((tenancy.nextPaymentAt() - now) / 1000).trim())
                .withStyle(net.minecraft.ChatFormatting.GOLD));
    }
}
