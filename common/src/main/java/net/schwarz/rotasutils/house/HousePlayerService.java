package net.schwarz.rotasutils.house;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class HousePlayerService {
    private static final double INVITE_RANGE = 24.0;
    public static final int FINDER_ROWS = 6;

    private HousePlayerService() {
    }

    public record Result(boolean success, String message) {
    }

public static HouseDefinition pick(ServerPlayer player, RotasData data, String requested) {
        if (requested != null && !requested.isBlank()) {
            return data.house(requested);
        }
        HouseDefinition here = HouseRegistry.at(data.houses().values(),
                player.level().dimension().location().toString(), player.blockPosition());
        if (here != null) {
            return here;
        }
        HouseDefinition member = null;
        for (HouseDefinition house : data.houses().values()) {
            HouseTenancy tenancy = data.houseTenancy(house.id());
            if (player.getUUID().equals(tenancy.owner())) {
                return house;
            }
            if (member == null && tenancy.members().contains(player.getUUID())) {
                member = house;
            }
        }
        return member;
    }

public static Result rent(ServerPlayer player, RotasData data, String id) {
        return money(player, data, id, (service, tenancy, house) ->
                service.rent(tenancy, player.getUUID(), data.houseTier(house), System.currentTimeMillis(), tenancy.revision()));
    }

    public static Result pay(ServerPlayer player, RotasData data, String id) {
        return money(player, data, id, (service, tenancy, house) ->
                service.payOverdue(tenancy, player.getUUID(), System.currentTimeMillis(), tenancy.revision()));
    }

    public static Result buyout(ServerPlayer player, RotasData data, String id) {
        return money(player, data, id, (service, tenancy, house) ->
                service.buyout(tenancy, player.getUUID(), data.houseTier(house), tenancy.revision()));
    }

    public static Result leave(ServerPlayer player, RotasData data, String id) {
        HouseDefinition house = data.house(id);
        if (house == null) {
            return tell(player, false, "rotasutils.msg.house.result.not_found");
        }
        HouseTenancy tenancy = data.houseTenancy(id);
        UUID self = player.getUUID();
        if (self.equals(tenancy.owner())) {
            data.putHouseTenancy(id, new HouseTenancy(HouseStatus.AVAILABLE, null, Set.of(), 0, 0, 0, 0,
                    tenancy.revision() + 1));
            data.audit(player.getGameProfile().getName() + " house_release " + id);
            return done(player, data, "rotasutils.msg.house.result.released", house.name());
        }
        if (tenancy.members().contains(self)) {
            Set<UUID> members = new LinkedHashSet<>(tenancy.members());
            members.remove(self);
            data.putHouseTenancy(id, withMembers(tenancy, members));
            return done(player, data, "rotasutils.msg.house.result.left", house.name());
        }
        return tell(player, false, "rotasutils.msg.house.result.not_owner");
    }

    public static Result addMember(ServerPlayer player, RotasData data, String id, UUID target) {
        HouseDefinition house = data.house(id);
        HouseTenancy tenancy = house == null ? null : data.houseTenancy(id);
        if (tenancy == null || !player.getUUID().equals(tenancy.owner())) {
            return tell(player, false, "rotasutils.msg.house.result.not_owner");
        }
        if (target == null || target.equals(tenancy.owner())) {
            return tell(player, false, "rotasutils.msg.house.result.bad_member");
        }
        if (tenancy.members().contains(target)) {
            return tell(player, false, "rotasutils.msg.house.result.already_member", name(player.server, target));
        }
        int limit = memberLimit(data.houseConfig(), tenancy);
        if (tenancy.members().size() >= limit) {
            return tell(player, false, "rotasutils.msg.house.result.member_limit", limit);
        }
        Set<UUID> members = new LinkedHashSet<>(tenancy.members());
        members.add(target);
        data.putHouseTenancy(id, withMembers(tenancy, members));
        ServerPlayer invited = player.server.getPlayerList().getPlayer(target);
        if (invited != null) {
            invited.sendSystemMessage(ThaiText.c("rotasutils.msg.house.result.invited", player.getGameProfile().getName(),
                    house.name()));
        }
        return done(player, data, "rotasutils.msg.house.result.member_added", name(player.server, target));
    }

    public static Result removeMember(ServerPlayer player, RotasData data, String id, UUID target) {
        HouseTenancy tenancy = data.house(id) == null ? null : data.houseTenancy(id);
        if (tenancy == null || !player.getUUID().equals(tenancy.owner())) {
            return tell(player, false, "rotasutils.msg.house.result.not_owner");
        }
        if (!tenancy.members().contains(target)) {
            return tell(player, false, "rotasutils.msg.house.result.bad_member");
        }
        Set<UUID> members = new LinkedHashSet<>(tenancy.members());
        members.remove(target);
        data.putHouseTenancy(id, withMembers(tenancy, members));
        return done(player, data, "rotasutils.msg.house.result.member_removed", name(player.server, target));
    }

    public static Result buySlot(ServerPlayer player, RotasData data, String id) {
        HouseConfig config = data.houseConfig();
        HouseTenancy tenancy = data.house(id) == null ? null : data.houseTenancy(id);
        if (tenancy == null || !player.getUUID().equals(tenancy.owner())) {
            return tell(player, false, "rotasutils.msg.house.result.not_owner");
        }
        if (tenancy.purchasedMemberSlots() >= config.maxPurchasedMemberSlots()
                || memberLimit(config, tenancy) >= HouseTenancy.MAX_MEMBERS) {
            return tell(player, false, "rotasutils.msg.house.result.no_more_slots");
        }
        var profile = data.progress(player.getUUID());
        if (profile.rpg().currency(config.currency()) < config.memberSlotPrice()) {
            return tell(player, false, "rotasutils.msg.house.result.insufficient_funds");
        }
        profile.rpg().currency(config.currency(), -config.memberSlotPrice());
        profile.markDirty();
        data.putHouseTenancy(id, new HouseTenancy(tenancy.status(), tenancy.owner(), tenancy.members(),
                tenancy.nextPaymentAt(), tenancy.graceEndsAt(), tenancy.overdueCharge(),
                tenancy.purchasedMemberSlots() + 1, tenancy.revision() + 1));
        data.audit(player.getGameProfile().getName() + " house_buy_slot " + id + " price=" + config.memberSlotPrice());
        return done(player, data, "rotasutils.msg.house.result.slot_bought", memberLimit(config, data.houseTenancy(id)));
    }

public static Result evict(ServerPlayer admin, RotasData data, String id) {
        HouseDefinition house = data.house(id);
        if (house == null) {
            return tell(admin, false, "rotasutils.msg.house.result.not_found");
        }
        HouseTenancy tenancy = data.houseTenancy(id);
        data.putHouseTenancy(id, new HouseTenancy(HouseStatus.AVAILABLE, null, Set.of(), 0, 0, 0, 0,
                tenancy.revision() + 1));
        data.audit(admin.getGameProfile().getName() + " house_evict " + id);
        return done(admin, data, "rotasutils.msg.house.result.evicted", house.name());
    }

    public static Result setOwner(ServerPlayer admin, RotasData data, String id, UUID owner) {
        HouseDefinition house = data.house(id);
        if (house == null) {
            return tell(admin, false, "rotasutils.msg.house.result.not_found");
        }
        HouseTenancy tenancy = data.houseTenancy(id);
        Set<UUID> members = new LinkedHashSet<>(tenancy.members());
        members.remove(owner);
        HouseStatus status = tenancy.status() == HouseStatus.BOUGHT_OUT ? HouseStatus.BOUGHT_OUT : HouseStatus.ACTIVE;
        long next = status == HouseStatus.BOUGHT_OUT ? 0
                : Math.addExact(System.currentTimeMillis(), data.houseConfig().paymentIntervalMillis());
        data.putHouseTenancy(id, new HouseTenancy(status, owner, members, next, 0, 0,
                tenancy.purchasedMemberSlots(), tenancy.revision() + 1));
        data.audit(admin.getGameProfile().getName() + " house_set_owner " + id + " " + owner);
        return done(admin, data, "rotasutils.msg.house.result.owner_set", house.name(), name(admin.server, owner));
    }

public static CompoundTag view(ServerPlayer player, RotasData data, String requested) {
        CompoundTag tag = new CompoundTag();
        HouseConfig config = data.houseConfig();
        tag.putLong("gold", data.progress(player.getUUID()).rpg().currency(config.currency()));
        tag.put("mine", mine(player, data));
        HouseDefinition house = pick(player, data, requested);
        if (house == null) {
            tag.putBoolean("found", false);
            tag.put("available", available(player, data));
            return tag;
        }
        HouseTenancy tenancy = data.houseTenancy(house.id());
        HouseTier tier = data.houseTier(house);
        long now = System.currentTimeMillis();
        tag.putBoolean("found", true);
        tag.putString("id", house.id());
        tag.putString("name", house.name());
        tag.putString("tier", house.tier());
        HouseBounds b = house.bounds();
        tag.putString("size", (b.maxX() - b.minX() + 1) + "x" + (b.maxY() - b.minY() + 1) + "x" + (b.maxZ() - b.minZ() + 1));
        tag.putString("where", b.minX() + " " + b.minY() + " " + b.minZ());
        tag.putBoolean("enabled", house.enabled());
        tag.putString("status", tenancy.status().name());
        tag.putBoolean("owner", player.getUUID().equals(tenancy.owner()));
        tag.putBoolean("member", tenancy.members().contains(player.getUUID()));
        tag.putString("owner_name", tenancy.owner() == null ? "" : name(player.server, tenancy.owner()));
        tag.putLong("deposit", tier == null ? 0 : tier.deposit());
        tag.putLong("maintenance", tier == null ? 0 : tier.maintenance());
        tag.putString("interval", QuestService.formatDuration(config.paymentIntervalMillis() / 1000).trim());
        tag.putLong("buyout", tier == null ? 0 : buyoutCost(config, tier, tenancy));
        tag.putString("next_payment", tenancy.nextPaymentAt() <= 0 ? ""
                : QuestService.formatDuration(Math.max(0, tenancy.nextPaymentAt() - now) / 1000).trim());
        tag.putString("grace_left", tenancy.graceEndsAt() <= 0 ? ""
                : QuestService.formatDuration(Math.max(0, tenancy.graceEndsAt() - now) / 1000).trim());
        tag.putLong("overdue_charge", tenancy.overdueCharge());
        tag.putInt("member_limit", memberLimit(config, tenancy));
        tag.putInt("slots_bought", tenancy.purchasedMemberSlots());
        tag.putInt("slots_max", config.maxPurchasedMemberSlots());
        tag.putLong("slot_price", config.memberSlotPrice());
        ListTag members = new ListTag();
        for (UUID member : tenancy.members()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", member);
            entry.putString("name", name(player.server, member));
            members.add(entry);
        }
        tag.put("members", members);
        ListTag nearby = new ListTag();
        if (player.getUUID().equals(tenancy.owner())) {
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other == player || tenancy.members().contains(other.getUUID())
                        || other.distanceToSqr(player) > INVITE_RANGE * INVITE_RANGE) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putUUID("uuid", other.getUUID());
                entry.putString("name", other.getGameProfile().getName());
                nearby.add(entry);
            }
        }
        tag.put("nearby", nearby);
        boolean admin = net.schwarz.rotasutils.server.BoardService.isAdmin(player, data);
        tag.putBoolean("admin", admin);
        if (admin) {
            HouseSettings settings = data.houseSettings(house.id());
            CompoundTag own = settings.save();
            HouseTier base = config.tier(house.tier());
            own.putLong("tier_deposit", base == null ? 0 : base.deposit());
            own.putLong("tier_rent", base == null ? 0 : base.maintenance());
            tag.put("settings", own);
        }
        return tag;
    }

    public static java.util.List<HouseFinder.Listing> finder(ServerPlayer player, RotasData data, int limit) {
        return HouseFinder.available(data.houses().values(), data::houseTenancy, data::houseTier,
                player.level().dimension().location().toString(), player.getX(), player.getZ(), limit);
    }

    private static ListTag available(ServerPlayer player, RotasData data) {
        ListTag list = new ListTag();
        for (HouseFinder.Listing listing : finder(player, data, FINDER_ROWS)) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", listing.id());
            entry.putString("name", listing.name());
            entry.putLong("deposit", listing.deposit());
            entry.putLong("maintenance", listing.maintenance());
            entry.putString("size", listing.size());
            entry.putInt("distance", listing.distance());
            list.add(entry);
        }
        return list;
    }

    public static Result saveSettings(ServerPlayer admin, RotasData data, String id, CompoundTag payload) {
        if (!net.schwarz.rotasutils.server.BoardService.isAdmin(admin, data)) {
            return tell(admin, false, "rotasutils.msg.admin_only");
        }
        HouseDefinition house = data.house(id);
        if (house == null) {
            return tell(admin, false, "rotasutils.msg.house.result.not_found");
        }
        HouseSettings settings;
        try {
            settings = HouseSettings.load(payload);
        } catch (IllegalArgumentException invalid) {
            RotasNetwork.feedback(admin, false, invalid.getMessage());
            return new Result(false, invalid.getMessage());
        }
        data.putHouseSettings(id, settings);
        data.audit(admin.getGameProfile().getName() + " house_settings " + id);
        return done(admin, data, "rotasutils.msg.house.result.settings_saved", house.name());
    }

    private static ListTag mine(ServerPlayer player, RotasData data) {
        ListTag list = new ListTag();
        for (HouseDefinition house : data.houses().values()) {
            HouseTenancy tenancy = data.houseTenancy(house.id());
            if (player.getUUID().equals(tenancy.owner()) || tenancy.members().contains(player.getUUID())) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", house.id());
                entry.putString("name", house.name());
                list.add(entry);
            }
        }
        return list;
    }

public static int memberLimit(HouseConfig config, HouseTenancy tenancy) {
        return Math.min(HouseTenancy.MAX_MEMBERS, config.baseMemberLimit() + tenancy.purchasedMemberSlots());
    }

    static long buyoutCost(HouseConfig config, HouseTier tier, HouseTenancy tenancy) {
        try {
            return Math.addExact(Math.multiplyExact(tier.deposit(), config.buyoutMultiplier()), tenancy.overdueCharge());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    private static HouseTenancy withMembers(HouseTenancy tenancy, Set<UUID> members) {
        return new HouseTenancy(tenancy.status(), tenancy.owner(), new HashSet<>(members), tenancy.nextPaymentAt(),
                tenancy.graceEndsAt(), tenancy.overdueCharge(), tenancy.purchasedMemberSlots(), tenancy.revision() + 1);
    }

    private interface Money {
        HouseService.Action run(HouseService service, HouseTenancy tenancy, HouseDefinition house);
    }

    private static Result money(ServerPlayer player, RotasData data, String id, Money operation) {
        HouseDefinition house = data.house(id);
        if (house == null || !house.enabled()) {
            return tell(player, false, "rotasutils.msg.house.result.not_found");
        }
        HouseService.Action action = operation.run(service(data), data.houseTenancy(id), house);
        String key = "rotasutils.msg.house.result." + action.message().replace("house.", "");
        if (!action.success()) {
            return tell(player, false, key, house.name());
        }
        data.putHouseTenancy(id, action.tenancy());
        data.audit(player.getGameProfile().getName() + " " + action.message() + " " + id);
        return done(player, data, key, house.name());
    }

    private static HouseService service(RotasData data) {
        HouseConfig config = data.houseConfig();
        return new HouseService(config, new HouseService.Wallet() {
            @Override
            public long balance(UUID id) {
                return data.progress(id).rpg().currency(config.currency());
            }

            @Override
            public boolean debit(UUID id, long amount) {
                var profile = data.progress(id);
                if (amount < 0 || profile.rpg().currency(config.currency()) < amount) {
                    return false;
                }
                profile.rpg().currency(config.currency(), -amount);
                profile.markDirty();
                return true;
            }
        });
    }

    private static Result tell(ServerPlayer player, boolean success, String key, Object... args) {
        String message = ThaiText.t(key, args);
        RotasNetwork.feedback(player, success, message);
        return new Result(success, message);
    }

    private static Result done(ServerPlayer player, RotasData data, String key, Object... args) {
        data.setDirty();
        Result result = tell(player, true, key, args);
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            RotasNetwork.syncContent(online);
        }
        return result;
    }
}
