package net.schwarz.rotasutils.house;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record HouseTenancy(HouseStatus status, UUID owner, Set<UUID> members, long nextPaymentAt,
                           long graceEndsAt, long overdueCharge, int purchasedMemberSlots, long revision) {
    public static final int MAX_MEMBERS = 128;

    public HouseTenancy {
        members = Set.copyOf(members);
        if (status == null || members.size() > MAX_MEMBERS || nextPaymentAt < 0 || graceEndsAt < 0
                || overdueCharge < 0 || purchasedMemberSlots < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid house tenancy");
        }
        if ((status == HouseStatus.AVAILABLE) != (owner == null)) {
            throw new IllegalArgumentException("Available tenancy must have no owner");
        }
        if (owner != null && members.contains(owner)) throw new IllegalArgumentException("Owner cannot be a member");
    }

    public static HouseTenancy available() {
        return new HouseTenancy(HouseStatus.AVAILABLE, null, Set.of(), 0, 0, 0, 0, 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", status.name());
        if (owner != null) tag.putUUID("owner", owner);
        ListTag memberTags = new ListTag();
        members.forEach(member -> memberTags.add(StringTag.valueOf(member.toString())));
        tag.put("members", memberTags);
        tag.putLong("next_payment_at", nextPaymentAt);
        tag.putLong("grace_ends_at", graceEndsAt);
        tag.putLong("overdue_charge", overdueCharge);
        tag.putInt("purchased_member_slots", purchasedMemberSlots);
        tag.putLong("revision", revision);
        return tag;
    }

    public static HouseTenancy load(CompoundTag tag) {
        HouseStatus status;
        try { status = HouseStatus.valueOf(tag.getString("status")); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid tenancy status", invalid); }
        UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        Set<UUID> members = new LinkedHashSet<>();
        ListTag list = tag.getList("members", Tag.TAG_STRING);
        if (list.size() > MAX_MEMBERS) throw new IllegalArgumentException("Too many house members");
        try { for (Tag value : list) members.add(UUID.fromString(value.getAsString())); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid member UUID", invalid); }
        return new HouseTenancy(status, owner, members, tag.getLong("next_payment_at"), tag.getLong("grace_ends_at"),
                tag.getLong("overdue_charge"), tag.getInt("purchased_member_slots"), tag.getLong("revision"));
    }
}
