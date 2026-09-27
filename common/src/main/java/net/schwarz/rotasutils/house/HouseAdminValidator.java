package net.schwarz.rotasutils.house;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure validation shared by the housing administrator UI and server actions. */
public final class HouseAdminValidator {
    private static final String ID_PATTERN = "[a-z0-9_.-]{1,64}";
    private static final String CURRENCY_PATTERN = "[a-z0-9_.-]{1,64}:[a-z0-9_.-]{1,64}";

    private HouseAdminValidator() {}

    public record Error(String field, String message) {
        public Error {
            if (field == null || field.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("Validation errors require a field and message");
            }
        }
    }

    public static List<Error> validateDefinition(HouseDefinition candidate,
                                                   Collection<HouseDefinition> houses,
                                                   HouseConfig config) {
        if (candidate == null) return List.of(new Error("definition", "House definition is required"));
        return validateDefinition(candidate.id(), candidate.name(), candidate.tier(), candidate.bounds(),
                candidate.enabled(), candidate.revision(), houses, config);
    }

    public static List<Error> validateDefinition(HouseDefinition candidate,
                                                   Collection<HouseDefinition> houses,
                                                   HouseConfig config, HouseTenancy tenancy) {
        List<Error> errors = validateDefinition(candidate, houses, config);
        if (candidate != null && !candidate.enabled()) {
            String reason = canDisableOrRemove(tenancy);
            if (reason != null) errors = append(errors, new Error("enabled", reason));
        }
        return errors;
    }

    public static List<Error> validateDefinition(String id, String name, String tier, HouseBounds bounds,
                                                   boolean enabled, Collection<HouseDefinition> houses,
                                                   HouseConfig config) {
        return validateDefinition(id, name, tier, bounds, enabled, 0L, houses, config);
    }

    public static List<Error> validateDefinition(String id, String name, String tier, HouseBounds bounds,
                                                   boolean enabled, long revision,
                                                   Collection<HouseDefinition> houses, HouseConfig config) {
        List<Error> errors = new ArrayList<>();
        if (id == null || !id.matches(ID_PATTERN)) errors.add(new Error("id", "Use 1-64 lowercase letters, digits, _, ., or -"));
        if (name == null || name.isBlank()) errors.add(new Error("name", "Name is required"));
        else if (name.length() > 96) errors.add(new Error("name", "Name must be at most 96 characters"));
        if (tier == null || !tier.matches(ID_PATTERN)) errors.add(new Error("tier", "Tier ID is invalid"));
        if (bounds == null) errors.add(new Error("bounds", "Bounds are required"));
        if (revision < 0) errors.add(new Error("revision", "Revision cannot be negative"));

        HouseDefinition validated = null;
        if (tier != null && tier.matches(ID_PATTERN) && (config == null || config.tier(tier) == null)) {
            errors.add(new Error("tier", "Unknown house tier"));
        }
        if (errors.stream().noneMatch(error -> Set.of("id", "name", "tier", "bounds", "revision").contains(error.field()))) {
            try {
                validated = new HouseDefinition(id, name, tier, bounds, enabled, revision);
            } catch (IllegalArgumentException invalid) {
                errors.add(new Error("definition", invalid.getMessage()));
            }
        }
        if (validated != null) {
            if (enabled && HouseRegistry.overlaps(nonNullHouses(houses), validated)) {
                errors.add(new Error("bounds", "House bounds overlap another enabled house"));
            }
        }
        return List.copyOf(errors);
    }

    public static List<Error> validateConfig(HouseConfig candidate, Collection<HouseDefinition> houses) {
        if (candidate == null) return List.of(new Error("config", "House configuration is required"));
        return validateConfig(candidate.currency(), candidate.paymentIntervalMillis(), candidate.reminderLeadMillis(),
                candidate.graceMillis(), candidate.buyoutMultiplier(), candidate.baseMemberLimit(),
                candidate.memberSlotPrice(), candidate.maxPurchasedMemberSlots(), candidate.tiers(),
                candidate.revision(), houses);
    }

    public static List<Error> validateConfig(String currency, long paymentIntervalMillis, long reminderLeadMillis,
                                              long graceMillis, int buyoutMultiplier, int baseMemberLimit,
                                              long memberSlotPrice, int maxPurchasedMemberSlots,
                                              Map<String, HouseTier> tiers, long revision,
                                              Collection<HouseDefinition> houses) {
        List<HouseTier> values = tiers == null ? List.of() : new ArrayList<>(tiers.values());
        List<Error> errors = validateConfig(currency, paymentIntervalMillis, reminderLeadMillis, graceMillis,
                buyoutMultiplier, baseMemberLimit, memberSlotPrice, maxPurchasedMemberSlots, values, revision, houses);
        if (tiers != null) {
            for (Map.Entry<String, HouseTier> entry : tiers.entrySet()) {
                HouseTier value = entry.getValue();
                if (value == null || !Objects.equals(entry.getKey(), value.id())) {
                    errors = append(errors, new Error("tiers." + entry.getKey(), "Tier ID does not match its map key"));
                }
                if (entry.getKey() == null || !entry.getKey().matches(ID_PATTERN)) {
                    errors = append(errors, new Error("tiers." + entry.getKey(), "Tier ID is invalid"));
                }
            }
        }
        return errors;
    }

    public static List<Error> validateConfig(String currency, long paymentIntervalMillis, long reminderLeadMillis,
                                              long graceMillis, int buyoutMultiplier, int baseMemberLimit,
                                              long memberSlotPrice, int maxPurchasedMemberSlots,
                                              Collection<HouseTier> tiers, long revision,
                                              Collection<HouseDefinition> houses) {
        List<Error> errors = new ArrayList<>();
        if (currency == null || !currency.matches(CURRENCY_PATTERN)) {
            errors.add(new Error("currency", "Currency must be a namespace:id value"));
        }
        if (paymentIntervalMillis < 60_000L) errors.add(new Error("paymentIntervalMillis", "Payment interval must be at least 60 seconds"));
        if (reminderLeadMillis < 0) errors.add(new Error("reminderLeadMillis", "Reminder lead cannot be negative"));
        if (paymentIntervalMillis >= 0 && reminderLeadMillis >= paymentIntervalMillis) {
            errors.add(new Error("reminderLeadMillis", "Reminder lead must be less than the payment interval"));
        }
        if (graceMillis < 0) errors.add(new Error("graceMillis", "Grace period cannot be negative"));
        if (buyoutMultiplier < 1) errors.add(new Error("buyoutMultiplier", "Buyout multiplier must be positive"));
        if (baseMemberLimit < 0 || baseMemberLimit > HouseTenancy.MAX_MEMBERS) {
            errors.add(new Error("baseMemberLimit", "Base member limit is outside the allowed range"));
        }
        if (memberSlotPrice < 0) errors.add(new Error("memberSlotPrice", "Member slot price cannot be negative"));
        if (maxPurchasedMemberSlots < 0) {
            errors.add(new Error("maxPurchasedMemberSlots", "Maximum purchased member slots cannot be negative"));
        }
        if (revision < 0) errors.add(new Error("revision", "Revision cannot be negative"));

        List<HouseTier> safeTiers = tiers == null ? List.of() : new ArrayList<>(tiers);
        if (safeTiers.isEmpty()) errors.add(new Error("tiers", "At least one house tier is required"));
        if (safeTiers.size() > 64) errors.add(new Error("tiers", "At most 64 house tiers are allowed"));
        Set<String> tierIds = new HashSet<>();
        for (HouseTier tier : safeTiers) {
            if (tier == null) {
                errors.add(new Error("tiers", "Tier is required"));
                continue;
            }
            if (!tierIds.add(tier.id())) errors.add(new Error("tiers." + tier.id(), "Tier ID is duplicated"));
            if (!tier.id().matches(ID_PATTERN)) errors.add(new Error("tiers." + tier.id(), "Tier ID is invalid"));
            try {
                Math.multiplyExact(tier.deposit(), buyoutMultiplier);
            } catch (ArithmeticException overflow) {
                errors.add(new Error("tiers." + tier.id() + ".deposit", "Buyout price overflows"));
            }
        }
        try {
            Math.addExact(paymentIntervalMillis, graceMillis);
        } catch (ArithmeticException overflow) {
            errors.add(new Error("graceMillis", "Payment interval and grace period overflow"));
        }
        try {
            Math.multiplyExact(memberSlotPrice, maxPurchasedMemberSlots);
        } catch (ArithmeticException overflow) {
            errors.add(new Error("memberSlotPrice", "Member slot total overflows"));
        }

        if (houses != null) {
            for (HouseDefinition house : houses) {
                if (house != null && !tierIds.contains(house.tier())) {
                    errors.add(new Error("tiers." + house.tier(), "Tier is referenced by house " + house.id()));
                }
            }
        }
        if (errors.isEmpty()) {
            Map<String, HouseTier> tierMap = new LinkedHashMap<>();
            for (HouseTier tier : safeTiers) tierMap.put(tier.id(), tier);
            try {
                new HouseConfig(currency, paymentIntervalMillis, reminderLeadMillis, graceMillis,
                        buyoutMultiplier, baseMemberLimit, memberSlotPrice, maxPurchasedMemberSlots,
                        tierMap, revision);
            } catch (IllegalArgumentException invalid) {
                errors.add(new Error("config", invalid.getMessage()));
            }
        }
        return List.copyOf(errors);
    }

    public static String canDisableOrRemove(HouseTenancy tenancy) {
        if (tenancy == null) return "Tenancy is required";
        return tenancy.owner() != null || !tenancy.members().isEmpty() ? "House is occupied" : null;
    }

    private static List<Error> append(List<Error> errors, Error error) {
        List<Error> result = new ArrayList<>(errors);
        result.add(error);
        return List.copyOf(result);
    }

    private static List<HouseDefinition> nonNullHouses(Collection<HouseDefinition> houses) {
        if (houses == null || houses.isEmpty()) return List.of();
        List<HouseDefinition> result = new ArrayList<>();
        for (HouseDefinition house : houses) if (house != null) result.add(house);
        return result;
    }
}
