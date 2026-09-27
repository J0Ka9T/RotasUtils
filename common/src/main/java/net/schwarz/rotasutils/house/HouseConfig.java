package net.schwarz.rotasutils.house;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

public record HouseConfig(String currency, long paymentIntervalMillis, long reminderLeadMillis, long graceMillis,
                          int buyoutMultiplier, int baseMemberLimit, long memberSlotPrice,
                          int maxPurchasedMemberSlots, Map<String, HouseTier> tiers, long revision) {
        public HouseConfig {
        tiers = Map.copyOf(tiers);
        if (currency == null || currency.isBlank() || currency.length() > 128 || paymentIntervalMillis < 60_000
                || reminderLeadMillis < 0 || reminderLeadMillis >= paymentIntervalMillis || graceMillis < 0
                || buyoutMultiplier < 1 || baseMemberLimit < 0 || baseMemberLimit > HouseTenancy.MAX_MEMBERS
                || memberSlotPrice < 0 || maxPurchasedMemberSlots < 0 || tiers.isEmpty() || tiers.size() > 64
                || revision < 0) {
            throw new IllegalArgumentException("Invalid house configuration");
        }
    }

    public static HouseConfig defaults() {
        HouseTier starter = new HouseTier("starter", 1_000, 100);
        return new HouseConfig("rotas:gold", 259_200_000L, 86_400_000L, 3_600_000L, 10, 5, 0, 0,
                Map.of(starter.id(), starter), 0L);
    }

    public HouseTier tier(String id) { return tiers.get(id); }

    public HouseConfig withRevision(long revision) {
        return new HouseConfig(currency, paymentIntervalMillis, reminderLeadMillis, graceMillis, buyoutMultiplier,
                baseMemberLimit, memberSlotPrice, maxPurchasedMemberSlots, tiers, revision);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("currency", currency); tag.putLong("payment_interval", paymentIntervalMillis);
        tag.putLong("reminder_lead", reminderLeadMillis); tag.putLong("grace", graceMillis);
        tag.putInt("buyout_multiplier", buyoutMultiplier); tag.putInt("base_member_limit", baseMemberLimit);
        tag.putLong("member_slot_price", memberSlotPrice); tag.putInt("max_member_slots", maxPurchasedMemberSlots);
        tag.putLong("revision", revision);
        ListTag tierTags = new ListTag(); tiers.values().forEach(tier -> tierTags.add(tier.save())); tag.put("tiers", tierTags);
        return tag;
    }

    public static HouseConfig load(CompoundTag tag) {
        Map<String, HouseTier> tiers = new LinkedHashMap<>();
        ListTag list = tag.getList("tiers", Tag.TAG_COMPOUND);
        if (list.size() > 64) throw new IllegalArgumentException("Too many house tiers");
        for (Tag value : list) { HouseTier tier = HouseTier.load((CompoundTag) value); tiers.put(tier.id(), tier); }
        return new HouseConfig(tag.getString("currency"), tag.getLong("payment_interval"), tag.getLong("reminder_lead"),
                tag.getLong("grace"), tag.getInt("buyout_multiplier"), tag.getInt("base_member_limit"),
                tag.getLong("member_slot_price"), tag.getInt("max_member_slots"), tiers, tag.getLong("revision"));
    }
}
