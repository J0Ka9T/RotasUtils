package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HouseAdminValidatorTest {
    private static final HouseBounds FIRST_BOUNDS = HouseBounds.between("minecraft:overworld",
            new BlockPos(0, 64, 0), new BlockPos(3, 67, 3));

    @Test
    void rejectsInvalidDefinitionFieldsAndUnknownTier() {
        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                "Bad ID", " ", "missing", FIRST_BOUNDS, true, 0L, List.of(), HouseConfig.defaults());

        assertFieldsContain(errors, "id", "name", "tier");
    }

    @Test
    void rejectsLongDefinitionName() {
        String longName = "x".repeat(97);

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                "cottage", longName, "starter", FIRST_BOUNDS, true, 0L, List.of(), HouseConfig.defaults());

        assertTrue(errors.stream().anyMatch(error -> error.field().equals("name")));
    }

    @Test
    void rejectsOverlappingEnabledDefinitions() {
        HouseDefinition existing = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 0L);
        HouseBounds overlap = HouseBounds.between("minecraft:overworld", new BlockPos(3, 64, 3), new BlockPos(5, 67, 5));

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                "villa", "Villa", "starter", overlap, true, 0L, List.of(existing), HouseConfig.defaults());

        assertTrue(errors.stream().anyMatch(error -> error.field().equals("bounds")));
    }

    @Test
    void allowsDisabledDefinitionToOverlap() {
        HouseDefinition existing = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 0L);

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                "villa", "Villa", "starter", FIRST_BOUNDS, false, 0L, List.of(existing), HouseConfig.defaults());

        assertFalse(errors.stream().anyMatch(error -> error.field().equals("bounds")));
    }

    @Test
    void occupiedTenancyCannotBeDisabledOrRemoved() {
        HouseTenancy activeTenancy = new HouseTenancy(HouseStatus.ACTIVE, UUID.randomUUID(), Set.of(), 1L, 0L, 0L, 0, 0L);

        assertEquals("House is occupied", HouseAdminValidator.canDisableOrRemove(activeTenancy));
        assertNull(HouseAdminValidator.canDisableOrRemove(HouseTenancy.available()));
    }

    @Test
    void disabledDefinitionIsRejectedWhileItsTenancyIsOccupied() {
        HouseDefinition disabled = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, false, 0L);
        HouseTenancy activeTenancy = new HouseTenancy(HouseStatus.ACTIVE, UUID.randomUUID(), Set.of(), 1L, 0L, 0L, 0, 0L);

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateDefinition(
                disabled, List.of(), HouseConfig.defaults(), activeTenancy);

        assertTrue(errors.stream().anyMatch(error -> error.field().equals("enabled")));
    }

    @Test
    void rejectsEmptyAndDuplicateTierCollections() {
        List<HouseAdminValidator.Error> emptyErrors = HouseAdminValidator.validateConfig(
                "rotas:gold", 100_000L, 10_000L, 10_000L, 10, 5, 0L, 0,
                List.of(), 0L, List.of());
        List<HouseAdminValidator.Error> duplicateErrors = HouseAdminValidator.validateConfig(
                "rotas:gold", 100_000L, 10_000L, 10_000L, 10, 5, 0L, 0,
                List.of(new HouseTier("starter", 100L, 50L), new HouseTier("starter", 200L, 75L)), 0L, List.of());

        assertTrue(emptyErrors.stream().anyMatch(error -> error.field().equals("tiers")));
        assertTrue(duplicateErrors.stream().anyMatch(error -> error.field().equals("tiers.starter")));
    }

    @Test
    void rejectsRemovalOfTierReferencedByHouse() {
        HouseDefinition house = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 0L);

        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateConfig(
                "rotas:gold", 100_000L, 10_000L, 10_000L, 10, 5, 0L, 0,
                List.of(new HouseTier("premium", 200L, 100L)), 0L, List.of(house));

        assertTrue(errors.stream().anyMatch(error -> error.field().equals("tiers.starter")));
    }

    @Test
    void rejectsInvalidCurrencyNegativeValuesAndReminderRelationship() {
        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateConfig(
                "gold", -1L, 100L, -1L, -1, -1, -1L, -1,
                List.of(new HouseTier("starter", 100L, 50L)), 0L, List.of());

        assertFieldsContain(errors, "currency", "paymentIntervalMillis", "graceMillis",
                "buyoutMultiplier", "baseMemberLimit", "memberSlotPrice", "maxPurchasedMemberSlots");
    }

    @Test
    void rejectsReminderAtOrAfterPaymentAndArithmeticOverflow() {
        List<HouseAdminValidator.Error> relationshipErrors = HouseAdminValidator.validateConfig(
                "rotas:gold", 100_000L, 100_000L, 1L, 10, 5, 0L, 0,
                List.of(new HouseTier("starter", 100L, 50L)), 0L, List.of());
        List<HouseAdminValidator.Error> overflowErrors = HouseAdminValidator.validateConfig(
                "rotas:gold", Long.MAX_VALUE, 0L, Long.MAX_VALUE, 10, 5, 0L, 0,
                List.of(new HouseTier("starter", Long.MAX_VALUE, 0L)), 0L, List.of());

        assertTrue(relationshipErrors.stream().anyMatch(error -> error.field().equals("reminderLeadMillis")));
        assertTrue(overflowErrors.stream().anyMatch(error -> error.field().equals("graceMillis")));
    }

    @Test
    void rejectsBuyoutProductOverflowWithTierPriceField() {
        List<HouseAdminValidator.Error> errors = HouseAdminValidator.validateConfig(
                "rotas:gold", 100_000L, 0L, 1L, 2, 5, 0L, 0,
                List.of(new HouseTier("starter", Long.MAX_VALUE, 0L)), 0L, List.of());

        assertTrue(errors.stream().anyMatch(error -> error.field().equals("tiers.starter.deposit")
                        && error.message().contains("overflows")),
                () -> "Missing tier deposit overflow error: " + errors);
    }

    @Test
    void objectValidationAcceptsAValidDefinitionAndConfig() {
        HouseDefinition house = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 2L);

        assertTrue(HouseAdminValidator.validateDefinition(house, List.of(), HouseConfig.defaults()).isEmpty());
        assertTrue(HouseAdminValidator.validateConfig(HouseConfig.defaults(), List.of(house)).isEmpty());
    }

    private static void assertFieldsContain(List<HouseAdminValidator.Error> errors, String... fields) {
        for (String field : fields) {
            assertTrue(errors.stream().anyMatch(error -> error.field().equals(field)),
                    () -> "Missing validation error for " + field + ": " + errors);
        }
    }
}
