package net.schwarz.rotasutils.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseStatus;
import net.schwarz.rotasutils.house.HouseTenancy;
import net.schwarz.rotasutils.house.HouseTier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bounded, identity-free housing data an authenticated administrator may
 * edit. This is presentation state only; mutations still go through the server.
 */
public final class ClientHouseAdminState {
    public static final int MAX_DEFINITIONS = 4096;
    public static final int MAX_STATUSES = 4096;
    public static final int MAX_TIERS = 64;
    public static final int MAX_STRING = 128;

    private final List<HouseDefinition> definitions;
    private final HouseConfig config;
    private final List<HouseTier> tiers;
    private final List<StatusSummary> statuses;
    private final SelectionSummary selection;

    private ClientHouseAdminState(Collection<HouseDefinition> definitions, HouseConfig config,
                                  Collection<HouseTier> tiers, Collection<StatusSummary> statuses,
                                  SelectionSummary selection) {
        if (definitions == null || definitions.size() > MAX_DEFINITIONS || config == null
                || tiers == null || tiers.isEmpty() || tiers.size() > MAX_TIERS
                || statuses == null || statuses.size() > MAX_STATUSES) {
            throw new IllegalArgumentException("Invalid housing admin snapshot");
        }
        this.definitions = List.copyOf(definitions);
        this.config = config;
        this.tiers = List.copyOf(tiers);
        this.statuses = List.copyOf(statuses);
        this.selection = selection;
        validateIdentityFreeShape();
    }

    /** Builds a snapshot from server-owned values and the requesting actor's wand selection. */
    public static ClientHouseAdminState of(Collection<HouseDefinition> definitions, HouseConfig config,
                                           Map<String, HouseTenancy> tenancies,
                                           HouseAdminService.Selection selection) {
        if (definitions == null || config == null) {
            throw new IllegalArgumentException("Housing admin values are required");
        }
        List<HouseDefinition> orderedDefinitions = new ArrayList<>(definitions);
        orderedDefinitions.sort(Comparator.comparing(HouseDefinition::id));
        List<HouseTier> orderedTiers = new ArrayList<>(config.tiers().values());
        orderedTiers.sort(Comparator.comparing(HouseTier::id));
        List<StatusSummary> summaries = new ArrayList<>(orderedDefinitions.size());
        for (HouseDefinition definition : orderedDefinitions) {
            HouseTenancy tenancy = tenancies == null ? null : tenancies.get(definition.id());
            summaries.add(StatusSummary.from(definition.id(), tenancy == null ? HouseTenancy.available() : tenancy));
        }
        return new ClientHouseAdminState(orderedDefinitions, config, orderedTiers, summaries,
                SelectionSummary.from(selection));
    }

    public static ClientHouseAdminState fromServer(Collection<HouseDefinition> definitions, HouseConfig config,
                                                   Map<String, HouseTenancy> tenancies,
                                                   HouseAdminService.Selection selection) {
        return of(definitions, config, tenancies, selection);
    }

    public List<HouseDefinition> definitions() { return definitions; }
    public List<HouseDefinition> houses() { return definitions; }
    public HouseDefinition definition(String id) {
        for (HouseDefinition definition : definitions) if (definition.id().equals(id)) return definition;
        return null;
    }
    public long definitionRevision(String id) {
        HouseDefinition definition = definition(id);
        return definition == null ? -1L : definition.revision();
    }
    public HouseConfig config() { return config; }
    public long configRevision() { return config.revision(); }
    public List<HouseTier> tiers() { return tiers; }
    public List<StatusSummary> statuses() { return statuses; }
    public SelectionSummary selection() { return selection; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag definitionsTag = new ListTag();
        ListTag revisionsTag = new ListTag();
        for (HouseDefinition definition : definitions) {
            definitionsTag.add(definition.save());
            CompoundTag revision = new CompoundTag();
            revision.putString("house_id", definition.id());
            revision.putLong("revision", definition.revision());
            revisionsTag.add(revision);
        }
        tag.put("definitions", definitionsTag);
        tag.put("definition_revisions", revisionsTag);
        tag.put("config", config.save());
        tag.putLong("config_revision", config.revision());
        ListTag tiersTag = new ListTag();
        for (HouseTier tier : tiers) tiersTag.add(tier.save());
        tag.put("tiers", tiersTag);
        ListTag statusesTag = new ListTag();
        for (StatusSummary status : statuses) statusesTag.add(status.save());
        tag.put("statuses", statusesTag);
        if (selection != null) tag.put("selection", selection.save());
        return tag;
    }

    /** Returns null for any malformed, stale-shaped, or over-budget wire value. */
    public static ClientHouseAdminState load(CompoundTag tag) {
        try {
            if (tag == null || !tag.contains("definitions", Tag.TAG_LIST)
                    || !tag.contains("definition_revisions", Tag.TAG_LIST)
                    || !tag.contains("config", Tag.TAG_COMPOUND)
                    || !tag.contains("config_revision", Tag.TAG_LONG)
                    || !tag.contains("tiers", Tag.TAG_LIST)
                    || !tag.contains("statuses", Tag.TAG_LIST)) {
                return null;
            }
            if (!compoundList(tag, "definitions") || !compoundList(tag, "definition_revisions")
                    || !compoundList(tag, "tiers")
                    || !compoundList(tag, "statuses") || !compoundList(tag.getCompound("config"), "tiers")) {
                return null;
            }
            ListTag definitionTags = tag.getList("definitions", Tag.TAG_COMPOUND);
            ListTag revisionTags = tag.getList("definition_revisions", Tag.TAG_COMPOUND);
            ListTag tierTags = tag.getList("tiers", Tag.TAG_COMPOUND);
            ListTag statusTags = tag.getList("statuses", Tag.TAG_COMPOUND);
            if (definitionTags.size() > MAX_DEFINITIONS || tierTags.size() > MAX_TIERS
                    || statusTags.size() > MAX_STATUSES || tierTags.isEmpty()
                    || (definitionTags.size() > 0 && definitionTags.getElementType() != Tag.TAG_COMPOUND)
                    || (revisionTags.size() > 0 && revisionTags.getElementType() != Tag.TAG_COMPOUND)
                    || (tierTags.size() > 0 && tierTags.getElementType() != Tag.TAG_COMPOUND)
                    || (statusTags.size() > 0 && statusTags.getElementType() != Tag.TAG_COMPOUND)) return null;
            if (revisionTags.size() != definitionTags.size() || revisionTags.size() > MAX_DEFINITIONS) return null;

            List<HouseDefinition> definitions = new ArrayList<>(definitionTags.size());
            Map<String, HouseDefinition> byId = new LinkedHashMap<>();
            Map<String, Long> revisions = new LinkedHashMap<>();
            for (Tag raw : revisionTags) {
                CompoundTag revision = (CompoundTag) raw;
                if (!revision.contains("house_id", Tag.TAG_STRING) || !revision.contains("revision", Tag.TAG_LONG)
                        || revisions.put(revision.getString("house_id"), revision.getLong("revision")) != null) return null;
            }
            for (Tag raw : definitionTags) {
                CompoundTag definitionTag = (CompoundTag) raw;
                if (!definitionTag.contains("id", Tag.TAG_STRING) || !definitionTag.contains("name", Tag.TAG_STRING)
                        || !definitionTag.contains("tier", Tag.TAG_STRING)
                        || !definitionTag.contains("bounds", Tag.TAG_COMPOUND)
                        || !definitionTag.contains("enabled", Tag.TAG_BYTE)
                        || !definitionTag.contains("revision", Tag.TAG_LONG)) return null;
                HouseDefinition definition = HouseDefinition.load((CompoundTag) raw);
                if (byId.put(definition.id(), definition) != null) return null;
                if (!revisions.containsKey(definition.id()) || revisions.get(definition.id()) != definition.revision()) return null;
                definitions.add(definition);
            }
            CompoundTag configTag = tag.getCompound("config");
            if (!requiredConfigFields(configTag)) return null;
            HouseConfig decodedConfig = HouseConfig.load(configTag);
            if (decodedConfig.revision() != tag.getLong("config_revision")) return null;
            ListTag configTierTags = configTag.getList("tiers", Tag.TAG_COMPOUND);
            if (configTierTags.size() != decodedConfig.tiers().size()
                    || (configTierTags.size() > 0 && configTierTags.getElementType() != Tag.TAG_COMPOUND)) return null;

            List<HouseTier> tiers = new ArrayList<>(tierTags.size());
            Map<String, HouseTier> configTiers = new LinkedHashMap<>();
            for (Tag raw : tierTags) {
                CompoundTag tierTag = (CompoundTag) raw;
                if (!tierTag.contains("id", Tag.TAG_STRING) || !tierTag.contains("deposit", Tag.TAG_LONG)
                        || !tierTag.contains("maintenance", Tag.TAG_LONG)) return null;
                HouseTier tier = HouseTier.load((CompoundTag) raw);
                if (configTiers.put(tier.id(), tier) != null) return null;
                tiers.add(tier);
            }
            if (!configTiers.keySet().equals(decodedConfig.tiers().keySet())) return null;
            decodedConfig = new HouseConfig(decodedConfig.currency(), decodedConfig.paymentIntervalMillis(),
                    decodedConfig.reminderLeadMillis(), decodedConfig.graceMillis(), decodedConfig.buyoutMultiplier(),
                    decodedConfig.baseMemberLimit(), decodedConfig.memberSlotPrice(),
                    decodedConfig.maxPurchasedMemberSlots(), configTiers, decodedConfig.revision());

            List<StatusSummary> statuses = new ArrayList<>(statusTags.size());
            Map<String, StatusSummary> byStatus = new LinkedHashMap<>();
            for (Tag raw : statusTags) {
                CompoundTag statusTag = (CompoundTag) raw;
                if (!requiredStatusFields(statusTag)) return null;
                StatusSummary status = StatusSummary.load((CompoundTag) raw);
                if (!byId.containsKey(status.houseId()) || byStatus.put(status.houseId(), status) != null) return null;
                statuses.add(status);
            }
            if (statuses.size() != definitions.size() || !byStatus.keySet().equals(byId.keySet())) return null;
            SelectionSummary selection = tag.contains("selection", Tag.TAG_COMPOUND)
                    ? SelectionSummary.load(tag.getCompound("selection")) : null;
            if (tag.contains("selection") && selection == null) return null;
            return new ClientHouseAdminState(definitions, decodedConfig, tiers, statuses, selection);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static boolean compoundList(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)) return false;
        return list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND;
    }

    private static boolean requiredConfigFields(CompoundTag tag) {
        return tag.contains("currency", Tag.TAG_STRING)
                && tag.contains("payment_interval", Tag.TAG_LONG)
                && tag.contains("reminder_lead", Tag.TAG_LONG)
                && tag.contains("grace", Tag.TAG_LONG)
                && tag.contains("buyout_multiplier", Tag.TAG_INT)
                && tag.contains("base_member_limit", Tag.TAG_INT)
                && tag.contains("member_slot_price", Tag.TAG_LONG)
                && tag.contains("max_member_slots", Tag.TAG_INT)
                && tag.contains("revision", Tag.TAG_LONG)
                && tag.contains("tiers", Tag.TAG_LIST);
    }

    private static boolean requiredStatusFields(CompoundTag tag) {
        return tag.contains("house_id", Tag.TAG_STRING)
                && tag.contains("status", Tag.TAG_STRING)
                && tag.contains("member_count", Tag.TAG_INT)
                && tag.contains("next_payment_at", Tag.TAG_LONG)
                && tag.contains("grace_ends_at", Tag.TAG_LONG)
                && tag.contains("overdue_charge", Tag.TAG_LONG)
                && tag.contains("purchased_member_slots", Tag.TAG_INT)
                && tag.contains("revision", Tag.TAG_LONG);
    }

    private void validateIdentityFreeShape() {
        Map<String, Boolean> definitionIds = new LinkedHashMap<>();
        for (HouseDefinition definition : definitions) {
            if (definition == null || definitionIds.put(definition.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate house definition");
            }
        }
        Map<String, Boolean> tierIds = new LinkedHashMap<>();
        for (HouseTier tier : tiers) {
            if (tier == null || tierIds.put(tier.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate house tier");
            }
        }
        if (!tierIds.keySet().equals(config.tiers().keySet())) {
            throw new IllegalArgumentException("Tier order does not describe config");
        }
        Map<String, Boolean> statusIds = new LinkedHashMap<>();
        for (StatusSummary status : statuses) {
            if (status == null || !definitionIds.containsKey(status.houseId())
                    || statusIds.put(status.houseId(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Invalid house status summary");
            }
        }
        if (!statusIds.keySet().equals(definitionIds.keySet())) {
            throw new IllegalArgumentException("Missing house status summary");
        }
    }

    public record StatusSummary(String houseId, HouseStatus status, int memberCount,
                                long nextPaymentAt, long graceEndsAt, long overdueCharge,
                                int purchasedMemberSlots, long revision) {
        public StatusSummary {
            if (houseId == null || !houseId.matches("[a-z0-9_.-]{1,64}") || status == null
                    || memberCount < 0 || memberCount > HouseTenancy.MAX_MEMBERS || nextPaymentAt < 0
                    || graceEndsAt < 0 || overdueCharge < 0 || purchasedMemberSlots < 0 || revision < 0) {
                throw new IllegalArgumentException("Invalid house status summary");
            }
            if (status == HouseStatus.AVAILABLE && memberCount != 0) {
                throw new IllegalArgumentException("Available house cannot have occupants");
            }
        }

        private static StatusSummary from(String houseId, HouseTenancy tenancy) {
            return new StatusSummary(houseId, tenancy.status(), tenancy.members().size(),
                    tenancy.nextPaymentAt(), tenancy.graceEndsAt(), tenancy.overdueCharge(),
                    tenancy.purchasedMemberSlots(), tenancy.revision());
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("house_id", houseId);
            tag.putString("status", status.name());
            tag.putInt("member_count", memberCount);
            tag.putLong("next_payment_at", nextPaymentAt);
            tag.putLong("grace_ends_at", graceEndsAt);
            tag.putLong("overdue_charge", overdueCharge);
            tag.putInt("purchased_member_slots", purchasedMemberSlots);
            tag.putLong("revision", revision);
            return tag;
        }

        private static StatusSummary load(CompoundTag tag) {
            if (!tag.contains("house_id", Tag.TAG_STRING) || !tag.contains("status", Tag.TAG_STRING)
                    || tag.getString("house_id").length() > MAX_STRING) return null;
            HouseStatus status;
            try { status = HouseStatus.valueOf(tag.getString("status")); }
            catch (RuntimeException invalid) { return null; }
            return new StatusSummary(tag.getString("house_id"), status,
                    tag.getInt("member_count"), tag.getLong("next_payment_at"), tag.getLong("grace_ends_at"),
                    tag.getLong("overdue_charge"), tag.getInt("purchased_member_slots"), tag.getLong("revision"));
        }
    }

    public record SelectionSummary(String dimension, BlockPos first, BlockPos second,
                                   long sizeX, long sizeY, long sizeZ, long volume) {
        public SelectionSummary {
            if (dimension == null || dimension.isBlank() || dimension.length() > MAX_STRING
                    || first == null && second == null || sizeX < 0 || sizeY < 0 || sizeZ < 0 || volume < 0) {
                throw new IllegalArgumentException("Invalid house wand selection");
            }
            boolean complete = first != null && second != null;
            if (!complete && (sizeX != 0 || sizeY != 0 || sizeZ != 0 || volume != 0)) {
                throw new IllegalArgumentException("Partial selection has dimensions");
            }
            if (complete) {
                long expectedX = Math.abs((long) first.getX() - second.getX()) + 1L;
                long expectedY = Math.abs((long) first.getY() - second.getY()) + 1L;
                long expectedZ = Math.abs((long) first.getZ() - second.getZ()) + 1L;
                long expectedVolume;
                try { expectedVolume = Math.multiplyExact(Math.multiplyExact(expectedX, expectedY), expectedZ); }
                catch (ArithmeticException overflow) { throw new IllegalArgumentException("Selection volume overflow", overflow); }
                if (sizeX != expectedX || sizeY != expectedY || sizeZ != expectedZ || volume != expectedVolume) {
                    throw new IllegalArgumentException("Selection dimensions do not match coordinates");
                }
            }
        }

        private static SelectionSummary from(HouseAdminService.Selection selection) {
            if (selection == null || selection.dimension() == null || selection.dimension().isBlank()) return null;
            BlockPos first = selection.first();
            BlockPos second = selection.second();
            if (first == null && second == null) return null;
            if (first == null || second == null) return new SelectionSummary(selection.dimension(), first, second, 0, 0, 0, 0);
            long x = Math.abs((long) first.getX() - second.getX()) + 1L;
            long y = Math.abs((long) first.getY() - second.getY()) + 1L;
            long z = Math.abs((long) first.getZ() - second.getZ()) + 1L;
            long volume;
            try { volume = Math.multiplyExact(Math.multiplyExact(x, y), z); }
            catch (ArithmeticException overflow) { throw new IllegalArgumentException("Selection volume overflow", overflow); }
            return new SelectionSummary(selection.dimension(), first, second, x, y, z, volume);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension);
            if (first != null) tag.putLong("first", first.asLong());
            if (second != null) tag.putLong("second", second.asLong());
            tag.putLong("size_x", sizeX); tag.putLong("size_y", sizeY); tag.putLong("size_z", sizeZ);
            tag.putLong("volume", volume);
            return tag;
        }

        private static SelectionSummary load(CompoundTag tag) {
            if (!tag.contains("dimension", Tag.TAG_STRING)
                    || !tag.contains("size_x", Tag.TAG_LONG) || !tag.contains("size_y", Tag.TAG_LONG)
                    || !tag.contains("size_z", Tag.TAG_LONG) || !tag.contains("volume", Tag.TAG_LONG)
                    || (tag.contains("first") && !tag.contains("first", Tag.TAG_LONG))
                    || (tag.contains("second") && !tag.contains("second", Tag.TAG_LONG))) return null;
            BlockPos first = tag.contains("first", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("first")) : null;
            BlockPos second = tag.contains("second", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("second")) : null;
            return new SelectionSummary(tag.getString("dimension"), first, second, tag.getLong("size_x"),
                    tag.getLong("size_y"), tag.getLong("size_z"), tag.getLong("volume"));
        }
    }
}
