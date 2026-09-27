package net.schwarz.rotasutils.client.screen.admin;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.house.HouseStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HouseScreenRoutingTest {
    @Test
    void overviewActionsRouteToTheExpectedHousingSurface() {
        assertEquals(HouseScreenActions.OverviewTarget.GET_WAND,
                HouseScreenActions.routeOverview("get_wand", "").target());
        assertEquals(HouseScreenActions.OverviewTarget.CREATE,
                HouseScreenActions.routeOverview("create", "").target());
        assertEquals(HouseScreenActions.OverviewTarget.RENTAL_SETTINGS,
                HouseScreenActions.routeOverview("rental_settings", "").target());
        HouseScreenActions.Route edit = HouseScreenActions.routeOverview("house:oak", "");
        assertEquals(HouseScreenActions.OverviewTarget.EDIT, edit.target());
        assertEquals("oak", edit.houseId());
    }

    @Test
    void createPayloadContainsOnlyTypedDefinitionFieldsAndRevision() {
        CompoundTag payload = HouseScreenActions.createPayload("oak", "Oak House", "starter", 12L);

        assertEquals("oak", payload.getString("id"));
        assertEquals("Oak House", payload.getString("name"));
        assertEquals("starter", payload.getString("tier"));
        assertEquals(12L, payload.getLong("config_revision"));
        assertNoCoordinates(payload);
    }

    @Test
    void editAndReplacePayloadsCarryExpectedRevisionWithoutCoordinates() {
        CompoundTag edit = HouseScreenActions.editPayload("oak", "New Oak", "starter", false, 7L);
        assertEquals(7L, edit.getLong("revision"));
        assertFalse(edit.getBoolean("enabled"));
        assertNoCoordinates(edit);

        CompoundTag replace = HouseScreenActions.replaceBoundsPayload("oak", 8L);
        assertEquals(8L, replace.getLong("revision"));
        assertEquals(2, replace.getAllKeys().size());
        assertNoCoordinates(replace);
    }

    @Test
    void removalPayloadRequiresConfirmedResultAndCancelKeepsItUnconstructible() {
        HouseScreenActions.RemovalResult cancelled = HouseScreenActions.cancelRemoval("oak", 9L);
        assertThrows(IllegalStateException.class, () -> HouseScreenActions.removePayload(cancelled));

        HouseScreenActions.RemovalResult confirmed = HouseScreenActions.confirmRemoval("oak", 9L);
        CompoundTag payload = HouseScreenActions.removePayload(confirmed);
        assertEquals("oak", payload.getString("id"));
        assertEquals(9L, payload.getLong("revision"));
        assertNoCoordinates(payload);
    }

    @Test
    void occupiedPresentationBlocksDisableAndRemoveLocallyWithReason() {
        HouseAdminPresentation.OverviewRow occupied = new HouseAdminPresentation.OverviewRow(
                "oak", "Oak House", "minecraft:overworld", "4x4x4", 4, 4, 4, 64,
                "starter", true, HouseStatus.ACTIVE, "ACTIVE",
                HouseAdminPresentation.StatusRole.ACCENT, 1, 3L);

        assertFalse(HouseScreenActions.canDisableOrRemove(occupied));
        assertEquals("House is occupied", HouseScreenActions.occupiedReason(occupied));
        assertTrue(HouseScreenActions.canDisableOrRemove(
                new HouseAdminPresentation.OverviewRow("empty", "Empty", "minecraft:overworld",
                        "4x4x4", 4, 4, 4, 64, "starter", true, HouseStatus.AVAILABLE,
                        "AVAILABLE", HouseAdminPresentation.StatusRole.GOOD, 0, 1L)));
    }

    @Test
    void editorContextPreservesParentHouseAndTypedDraft() {
        HouseAdminPresentation.EditDraft draft = new HouseAdminPresentation.EditDraft(
                "oak", "Typed name", "starter", true, 11L);
        HouseScreenActions.EditorContext context = HouseScreenActions.editorContext("oak", draft);

        assertEquals("oak", context.houseId());
        assertSame(draft, context.draft());
        assertEquals("Typed name", context.draft().name());
    }

    @Test
    void managerExposesRentalSettingsRoute() {
        assertTrue(HouseManagerScreen.rentalSettingsEnabled());
        assertEquals(HouseScreenActions.OverviewTarget.RENTAL_SETTINGS,
                HouseManagerScreen.routeOverviewAction("rental_settings").target());
    }

    @Test
    void tierChildCarriesTheSameDraftBackToSettings() {
        HouseConfigDraft draft = new HouseConfigDraft(net.schwarz.rotasutils.house.HouseConfig.defaults(), List.of());
        HouseSettingsScreen settings = new HouseSettingsScreen(null, draft);
        HouseTierEditScreen child = new HouseTierEditScreen(settings, draft, 0);

        assertSame(draft, settings.draft());
        assertSame(draft, child.draft());
        assertSame(settings, child.parentSettings());
    }

    private static void assertNoCoordinates(CompoundTag payload) {
        assertFalse(payload.contains("bounds"));
        assertFalse(payload.contains("min"));
        assertFalse(payload.contains("max"));
        assertFalse(payload.contains("first"));
        assertFalse(payload.contains("second"));
        assertFalse(payload.contains("x"));
        assertFalse(payload.contains("y"));
        assertFalse(payload.contains("z"));
        assertFalse(payload.contains("coordinates"));
    }
}
