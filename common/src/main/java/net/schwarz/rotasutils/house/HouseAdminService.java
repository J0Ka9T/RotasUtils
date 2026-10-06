package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HouseAdminService {
    private static final String ADMIN_ONLY = "rotasutils.msg.item.not_admin";
    private static final String STALE = "house.stale";
    private static final String HOUSE_LIMIT = "House limit reached";

    private static final long FIRST_DEFINITION_REVISION = 1L;

    public interface Store {
        boolean isAdmin(UUID actor);

        Map<String, HouseDefinition> houses();

        HouseConfig config();

        Selection selection(UUID actor);

        void clearSelection(UUID actor);

        void putHouse(HouseDefinition house);

        void removeHouse(String id);

        void setConfig(HouseConfig config);

        HouseTenancy tenancy(String id);

        void markDirty();

        void rebuildHousing();

        void audit(String record);

        void resync();

        default HouseDefinition house(String id) {
            Map<String, HouseDefinition> definitions = houses();
            return definitions == null ? null : definitions.get(id);
        }
    }

    public record Selection(String dimension, BlockPos first, BlockPos second) {
    }

    public record CreateRequest(String id, String name, String tier, long expectedConfigRevision) {
    }

    public record EditRequest(String id, String name, String tier, boolean enabled,
                              long expectedDefinitionRevision) {
    }

    public record ReplaceBoundsRequest(String id, long expectedDefinitionRevision) {
    }

    public record RemoveRequest(String id, long expectedDefinitionRevision) {
    }

    public record ConfigRequest(String currency, long paymentIntervalMillis, long reminderLeadMillis,
                                long graceMillis, int buyoutMultiplier, int baseMemberLimit,
                                long memberSlotPrice, int maxPurchasedMemberSlots,
                                List<HouseTier> tiers, long expectedConfigRevision) {
        public ConfigRequest {
            tiers = tiers == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(tiers));
        }
    }

    public record Action(boolean success, String message) {
        public Action {
            Objects.requireNonNull(message, "message");
        }
    }

    private final Store store;

    public HouseAdminService(Store store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public Action create(UUID actor, CreateRequest request) {
        Action permission = requireAdmin(actor);
        if (permission != null) {
            return permission;
        }
        if (request == null) {
            return failure("house.request_required");
        }

        HouseConfig config = store.config();
        Action revision = requireConfigRevision(config, request.expectedConfigRevision());
        if (revision != null) {
            return revision;
        }
        if (request.id() != null && store.house(request.id()) != null) {
            return failure("house.id_exists");
        }

        HouseBounds bounds;
        try {
            bounds = boundsFromSelection(store.selection(actor));
        } catch (IllegalArgumentException invalidSelection) {
            return failure("house.invalid_selection: " + invalidSelection.getMessage());
        }

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                request.id(), request.name(), request.tier(), bounds, true,
                FIRST_DEFINITION_REVISION, houseValues(), config);
        if (!errors.isEmpty()) {
            return failure(format(errors));
        }

        HouseDefinition candidate;
        try {
            candidate = new HouseDefinition(request.id(), request.name(), request.tier(), bounds, true,
                    FIRST_DEFINITION_REVISION);
        } catch (IllegalArgumentException invalid) {
            return failure("house.invalid_definition: " + invalid.getMessage());
        }

        try {
            store.putHouse(candidate);
        } catch (IllegalStateException limit) {
            if (HOUSE_LIMIT.equals(limit.getMessage())) {
                return failure("house.limit_reached");
            }
            throw limit;
        }
        store.clearSelection(actor);
        finish(actor, "house_create", candidate.id());
        return success("house.created");
    }

    public Action edit(UUID actor, EditRequest request) {
        Action permission = requireAdmin(actor);
        if (permission != null) {
            return permission;
        }
        if (request == null || request.id() == null) {
            return failure("house.request_required");
        }

        HouseDefinition current = store.house(request.id());
        if (current == null) {
            return failure("house.not_found");
        }
        Action revision = requireDefinitionRevision(current, request.expectedDefinitionRevision());
        if (revision != null) {
            return revision;
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(current.revision(), 1L);
        } catch (ArithmeticException overflow) {
            return failure("house.revision_overflow");
        }

        HouseConfig config = store.config();
        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                current.id(), request.name(), request.tier(), current.bounds(), request.enabled(),
                nextRevision, houseValues(), config);
        if (!errors.isEmpty()) {
            return failure(format(errors));
        }

        HouseDefinition candidate;
        try {
            candidate = new HouseDefinition(current.id(), request.name(), request.tier(), current.bounds(),
                    request.enabled(), nextRevision);
        } catch (IllegalArgumentException invalid) {
            return failure("house.invalid_definition: " + invalid.getMessage());
        }
        errors = HouseAdminValidator.validateDefinition(candidate, houseValues(), config, store.tenancy(current.id()));
        if (!errors.isEmpty()) {
            return failure(format(errors));
        }

        store.putHouse(candidate);
        finish(actor, "house_edit", candidate.id());
        return success("house.saved");
    }

    public Action replaceBounds(UUID actor, ReplaceBoundsRequest request) {
        Action permission = requireAdmin(actor);
        if (permission != null) {
            return permission;
        }
        if (request == null || request.id() == null) {
            return failure("house.request_required");
        }

        HouseDefinition current = store.house(request.id());
        if (current == null) {
            return failure("house.not_found");
        }
        Action revision = requireDefinitionRevision(current, request.expectedDefinitionRevision());
        if (revision != null) {
            return revision;
        }

        HouseBounds bounds;
        try {
            bounds = boundsFromSelection(store.selection(actor));
        } catch (IllegalArgumentException invalidSelection) {
            return failure("house.invalid_selection: " + invalidSelection.getMessage());
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(current.revision(), 1L);
        } catch (ArithmeticException overflow) {
            return failure("house.revision_overflow");
        }

        HouseDefinition candidate;
        try {
            candidate = new HouseDefinition(current.id(), current.name(), current.tier(), bounds,
                    current.enabled(), nextRevision);
        } catch (IllegalArgumentException invalid) {
            return failure("house.invalid_definition: " + invalid.getMessage());
        }
        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                candidate, houseValues(), store.config());
        if (!errors.isEmpty()) {
            return failure(format(errors));
        }

        store.putHouse(candidate);
        store.clearSelection(actor);
        finish(actor, "house_replace_bounds", candidate.id());
        return success("house.bounds_replaced");
    }

    public Action remove(UUID actor, RemoveRequest request) {
        Action permission = requireAdmin(actor);
        if (permission != null) {
            return permission;
        }
        if (request == null || request.id() == null) {
            return failure("house.request_required");
        }

        HouseDefinition current = store.house(request.id());
        if (current == null) {
            return failure("house.not_found");
        }
        Action revision = requireDefinitionRevision(current, request.expectedDefinitionRevision());
        if (revision != null) {
            return revision;
        }
        String occupied = HouseAdminValidator.canDisableOrRemove(store.tenancy(current.id()));
        if (occupied != null) {
            return failure("enabled: " + occupied);
        }

        store.removeHouse(current.id());
        finish(actor, "house_remove", current.id());
        return success("house.removed");
    }

    public Action saveConfig(UUID actor, ConfigRequest request) {
        Action permission = requireAdmin(actor);
        if (permission != null) {
            return permission;
        }
        if (request == null) {
            return failure("house.request_required");
        }

        HouseConfig current = store.config();
        Action revision = requireConfigRevision(current, request.expectedConfigRevision());
        if (revision != null) {
            return revision;
        }

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateConfig(
                request.currency(), request.paymentIntervalMillis(), request.reminderLeadMillis(),
                request.graceMillis(), request.buyoutMultiplier(), request.baseMemberLimit(),
                request.memberSlotPrice(), request.maxPurchasedMemberSlots(), request.tiers(),
                current.revision(), houseValues());
        if (!errors.isEmpty()) {
            return failure(format(errors));
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(current.revision(), 1L);
        } catch (ArithmeticException overflow) {
            return failure("house.revision_overflow");
        }

        Map<String, HouseTier> tiers = new LinkedHashMap<>();
        for (HouseTier tier : request.tiers()) {
            tiers.put(tier.id(), tier);
        }

        HouseConfig candidate;
        try {
            candidate = new HouseConfig(request.currency(), request.paymentIntervalMillis(),
                    request.reminderLeadMillis(), request.graceMillis(), request.buyoutMultiplier(),
                    request.baseMemberLimit(), request.memberSlotPrice(), request.maxPurchasedMemberSlots(),
                    tiers, nextRevision);
        } catch (IllegalArgumentException invalid) {
            return failure("house.invalid_config: " + invalid.getMessage());
        }

        store.setConfig(candidate);
        finish(actor, "house_save_config", "revision=" + nextRevision);
        return success("house.config_saved");
    }

    private Action requireAdmin(UUID actor) {
        return actor == null || !store.isAdmin(actor) ? failure(ADMIN_ONLY) : null;
    }

    private static Action requireConfigRevision(HouseConfig config, long expectedRevision) {
        if (config == null) {
            return failure("house.config_missing");
        }
        if (expectedRevision < 0 || config.revision() != expectedRevision) {
            return failure(STALE);
        }
        return null;
    }

    private static Action requireDefinitionRevision(HouseDefinition definition, long expectedRevision) {
        if (expectedRevision < 0 || definition.revision() != expectedRevision) {
            return failure(STALE);
        }
        return null;
    }

    private List<HouseDefinition> houseValues() {
        Collection<HouseDefinition> values = store.houses() == null ? List.of() : store.houses().values();
        List<HouseDefinition> copy = new ArrayList<>(values.size());
        for (HouseDefinition house : values) {
            if (house != null) {
                copy.add(house);
            }
        }
        return List.copyOf(copy);
    }

    private static HouseBounds boundsFromSelection(Selection selection) {
        if (selection == null || selection.dimension() == null || selection.dimension().isBlank()
                || selection.dimension().length() > 128 || selection.first() == null || selection.second() == null) {
            throw new IllegalArgumentException("A complete two-corner selection is required");
        }
        return HouseBounds.between(selection.dimension(), selection.first(), selection.second());
    }

    private void finish(UUID actor, String action, String subject) {
        store.markDirty();
        store.rebuildHousing();
        store.audit("actor=" + actor + " action=" + action + " " + subject);
        store.resync();
    }

    private static Action success(String message) {
        return new Action(true, message);
    }

    private static Action failure(String message) {
        return new Action(false, message);
    }

    private static String format(List<HouseAdminValidator.Error> errors) {
        HouseAdminValidator.Error first = errors.get(0);
        return first.field() + ": " + first.message();
    }
}
