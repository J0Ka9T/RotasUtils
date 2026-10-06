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
            String id=ids.get((cursor+offset)%ids.size());
            try {
                bill(server, data, now, id);
            } catch (RuntimeException failure) {
                net.schwarz.rotasutils.Rotasutils.LOG.error("Billing house {} failed: {}", id, failure.toString());
            }
        }
        cursor=(cursor+work)%ids.size();
    }

    private static void bill(MinecraftServer server, RotasData data, long now, String id) {
        HouseDefinition def=data.house(id); HouseTenancy tenancy=data.houseTenancy(id);
        if (def==null) return;
        if (!def.enabled() || tenancy.status()==HouseStatus.AVAILABLE || tenancy.status()==HouseStatus.BOUGHT_OUT) return;
        if (tenancy.status()==HouseStatus.OVERDUE) {
            HouseTenancy repossessed=HouseService.repossessIfDue(tenancy,now);
            if(repossessed!=tenancy){data.putHouseTenancy(id,repossessed); data.audit(java.time.Instant.now()+" action=house_repossess "+id);} return;
        }
        HouseTier tier=data.houseTier(def); if(tier==null) return;
        if (now < tenancy.nextPaymentAt()) {
            remindIfShort(server, data, def, tenancy, tier, now);
            return;
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

    public static void onJoin(net.minecraft.server.level.ServerPlayer player, RotasData data) {
        long now = System.currentTimeMillis();
        for (HouseDefinition def : data.houses().values()) {
            HouseTenancy tenancy = data.houseTenancy(def.id());
            if (!def.enabled() || !player.getUUID().equals(tenancy.owner())) continue;
            if (tenancy.status() == HouseStatus.OVERDUE) {
                player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.house.overdue",
                        def.name(), tenancy.overdueCharge(),
                        net.schwarz.rotasutils.server.QuestService.formatDuration(Math.max(0, tenancy.graceEndsAt() - now) / 1000).trim())
                        .withStyle(net.minecraft.ChatFormatting.RED));
            } else if (tenancy.status() == HouseStatus.ACTIVE) {
                HouseTier tier = data.houseTier(def);
                if (tier != null) remindIfShort(player.server, data, def, tenancy, tier, now);
            }
        }
    }

    private static final java.util.Set<String> reminded = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
