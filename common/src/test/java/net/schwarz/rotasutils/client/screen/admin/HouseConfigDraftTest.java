package net.schwarz.rotasutils.client.screen.admin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseTier;
import net.schwarz.rotasutils.house.HouseBounds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HouseConfigDraftTest {
    private static final long PAYMENT = 3 * HouseTimeFields.MILLIS_PER_DAY
            + 2 * HouseTimeFields.MILLIS_PER_HOUR
            + 17 * HouseTimeFields.MILLIS_PER_MINUTE + 42_000L;
    private static final long REMINDER = HouseTimeFields.MILLIS_PER_DAY
            + 5 * HouseTimeFields.MILLIS_PER_HOUR + 13 * HouseTimeFields.MILLIS_PER_MINUTE + 7_000L;
    private static final long GRACE = 4 * HouseTimeFields.MILLIS_PER_HOUR
            + 9 * HouseTimeFields.MILLIS_PER_MINUTE + 3_000L;

    @Test
    void roundTripsEveryConfigValueAndPreservesSubMinuteRemainders() {
        HouseConfig config = config(12L, List.of(
                new HouseTier("starter", 1_000L, 100L),
                new HouseTier("premium", 2_500L, 225L)));
        HouseConfigDraft draft = new HouseConfigDraft(config, List.of(
                new HouseTier("starter", 1_000L, 100L),
                new HouseTier("premium", 2_500L, 225L)), List.of());

        assertEquals("rotas:gold", draft.currency());
        assertEquals("3", draft.paymentDays());
        assertEquals("2", draft.paymentHours());
        assertEquals("17", draft.paymentMinutes());
        assertEquals(42_000L, draft.paymentRemainderMillis());
        assertEquals(7_000L, draft.reminderRemainderMillis());
        assertEquals(3_000L, draft.graceRemainderMillis());

        CompoundTag payload = draft.toPayload();
        assertEquals("rotas:gold", payload.getString("currency"));
        assertEquals(PAYMENT, payload.getLong("payment_interval_millis"));
        assertEquals(REMINDER, payload.getLong("reminder_lead_millis"));
        assertEquals(GRACE, payload.getLong("grace_millis"));
        assertEquals(10, payload.getInt("buyout_multiplier"));
        assertEquals(5, payload.getInt("base_member_limit"));
        assertEquals(0L, payload.getLong("member_slot_price"));
        assertEquals(0, payload.getInt("max_purchased_member_slots"));
        assertEquals(12L, payload.getLong("base_revision"));

        ListTag tiers = payload.getList("tiers", Tag.TAG_COMPOUND);
        assertEquals(2, tiers.size());
        assertEquals("starter", tiers.getCompound(0).getString("id"));
        assertEquals(1_000L, tiers.getCompound(0).getLong("deposit"));
        assertEquals(100L, tiers.getCompound(0).getLong("maintenance"));
        assertEquals("premium", tiers.getCompound(1).getString("id"));
        assertEquals(2_500L, tiers.getCompound(1).getLong("deposit"));
        assertEquals(225L, tiers.getCompound(1).getLong("maintenance"));
    }

    @Test
    void invalidRawTextStaysInDraftAndBlocksPayload() {
        HouseConfigDraft draft = new HouseConfigDraft(config(1L, List.of(
                new HouseTier("starter", 1_000L, 100L))), List.of());

        draft.setBuyoutMultiplier("not-a-number");
        draft.setPaymentHours("still-invalid");

        assertEquals("not-a-number", draft.buyoutMultiplier());
        assertEquals("still-invalid", draft.paymentHours());
        assertTrue(draft.validationErrors().stream().anyMatch(error -> error.field().equals("buyoutMultiplier")));
        assertTrue(draft.validationErrors().stream().anyMatch(error -> error.field().equals("paymentIntervalMillis")));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, draft::toPayload);
        assertTrue(failure.getMessage().contains("buyoutMultiplier"));
    }

    @Test
    void tierInsertionOrderIsRetainedInDraftAndPayload() {
        HouseConfigDraft draft = new HouseConfigDraft(config(2L, List.of(
                new HouseTier("starter", 1_000L, 100L))), List.of());

        draft.addTier("premium", "2000", "200");
        draft.insertTier(1, "deluxe", "3000", "300");

        assertEquals(List.of("starter", "deluxe", "premium"), draft.tiers().stream()
                .map(HouseConfigDraft.TierDraft::id).toList());
        ListTag tiers = draft.toPayload().getList("tiers", Tag.TAG_COMPOUND);
        assertEquals(List.of("starter", "deluxe", "premium"), tiers.stream()
                .map(value -> ((CompoundTag) value).getString("id")).toList());
    }

    @Test
    void duplicateTierIdReportsTheAffectedTier() {
        HouseConfigDraft draft = new HouseConfigDraft(config(3L, List.of(
                new HouseTier("starter", 1_000L, 100L))), List.of());
        draft.addTier("starter", "2_000", "200");

        assertTrue(draft.validationErrors().stream().anyMatch(error -> error.field().equals("tiers.starter")));
        assertThrows(IllegalArgumentException.class, draft::toPayload);
    }

    @Test
    void referencedTierRemovalIsBlockedWithHouseListAndLastTierRemains() {
        HouseConfig config = config(4L, List.of(
                new HouseTier("starter", 1_000L, 100L),
                new HouseTier("premium", 2_000L, 200L)));
        HouseDefinition house = new HouseDefinition("oak", "Oak House", "starter",
                HouseBounds.between("minecraft:overworld", new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                true, 1L);
        HouseConfigDraft draft = new HouseConfigDraft(config, List.of(house));

        assertFalse(draft.removeTier("starter"));
        assertTrue(draft.tierRemovalError().contains("oak"));
        assertEquals(List.of("oak"), draft.referencedHouses("starter"));
        assertTrue(draft.removeTier("premium"));
        HouseConfigDraft lastTier = new HouseConfigDraft(config(4L,
                List.of(new HouseTier("starter", 1_000L, 100L))), List.of());
        assertFalse(lastTier.removeTier("starter"));
        assertTrue(lastTier.tierRemovalError().contains("At least one"));
        assertEquals(List.of("starter"), lastTier.tiers().stream()
                .map(HouseConfigDraft.TierDraft::id).toList());
    }

    @Test
    void changedSummaryNamesOnlyChangedFieldsAndBecomesCleanAfterRebase() {
        HouseConfig original = config(5L, List.of(new HouseTier("starter", 1_000L, 100L)));
        HouseConfigDraft draft = new HouseConfigDraft(original, List.of());
        assertFalse(draft.hasChanges());

        draft.setCurrency("rotas:silver");
        draft.setPaymentMinutes("18");
        draft.addTier("premium", "2000", "200");

        assertTrue(draft.hasChanges());
        String summary = draft.changedFieldsSummary();
        assertTrue(summary.contains("currency"));
        assertTrue(summary.contains("payment"));
        assertTrue(summary.contains("tier"));

        HouseConfig updated = new HouseConfig("rotas:silver", PAYMENT + HouseTimeFields.MILLIS_PER_MINUTE,
                REMINDER, GRACE, 10, 5, 0L, 0,
                Map.of("starter", new HouseTier("starter", 1_000L, 100L),
                        "premium", new HouseTier("premium", 2_000L, 200L)), 6L);
        draft.rebase(updated);
        assertEquals(6L, draft.baseRevision());
        assertEquals("rotas:silver", draft.currency());
        assertFalse(draft.hasChanges());
    }

    @Test
    void failedOrStaleResponseLeavesTypedValuesUntouched() {
        HouseConfigDraft draft = new HouseConfigDraft(config(7L, List.of(
                new HouseTier("starter", 1_000L, 100L))), List.of());
        draft.setCurrency("rotas:typed");
        draft.setMemberSlotPrice("12345");
        draft.markSaveFailed();

        assertEquals("rotas:typed", draft.currency());
        assertEquals("12345", draft.memberSlotPrice());
        assertEquals(7L, draft.baseRevision());
        draft.markStaleResponse();
        assertEquals("rotas:typed", draft.currency());
        assertEquals("12345", draft.memberSlotPrice());
    }

    @Test
    void staleSnapshotRebasesRevisionWithoutReplacingRawInputOrStableIds() {
        HouseConfigDraft draft = new HouseConfigDraft(config(8L, List.of(
                new HouseTier("starter", 1_000L, 100L))), List.of());
        draft.setCurrency("rotas:typed");
        draft.setMemberSlotPrice("12345");

        HouseConfig newer = config(9L, List.of(new HouseTier("starter", 1_250L, 125L)));
        draft.rebasePreservingInput(newer);

        assertSame(newer, draft.baseline());
        assertEquals(9L, draft.baseRevision());
        assertEquals("rotas:typed", draft.currency());
        assertEquals("12345", draft.memberSlotPrice());
        assertThrows(IllegalStateException.class, () -> draft.tiers().get(0).setId("renamed"));
    }

    @Test
    void submittedSnapshotRequiresAnAdvancedRevisionAndAnExactCandidate() {
        HouseConfig candidate = config(10L, List.of(new HouseTier("starter", 1_000L, 100L)));
        HouseConfig sameRevision = config(10L, List.of(new HouseTier("starter", 1_000L, 100L)));
        HouseConfig exactCandidate = config(11L, List.of(new HouseTier("starter", 1_000L, 100L)));
        HouseConfig externalUpdate = config(11L, List.of(new HouseTier("starter", 1_250L, 100L)));

        assertFalse(HouseSettingsScreen.shouldRebaseSubmittedDraft(sameRevision, 10L, candidate));
        assertTrue(HouseSettingsScreen.shouldRebaseSubmittedDraft(exactCandidate, 10L, candidate));
        assertFalse(HouseSettingsScreen.shouldRebaseSubmittedDraft(externalUpdate, 10L, candidate));
    }

    @Test
    void localFieldErrorsRemainAvailableWhenTheSaveActionIsDisabled() {
        HouseConfigDraft draft = new HouseConfigDraft(config(11L, List.of(
                new HouseTier("starter", 1_000L, 100L))), List.of());
        draft.setBuyoutMultiplier("not-a-number");

        assertTrue(HouseSettingsScreen.firstLocalError(draft).contains("buyoutMultiplier"));
    }

    private static HouseConfig config(long revision, List<HouseTier> tiers) {
        Map<String, HouseTier> ordered = new LinkedHashMap<>();
        for (HouseTier tier : tiers) {
            ordered.put(tier.id(), tier);
        }
        return new HouseConfig("rotas:gold", PAYMENT, REMINDER, GRACE, 10, 5,
                0L, 0, ordered, revision);
    }
}
