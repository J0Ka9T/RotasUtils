package net.schwarz.rotasutils.client.screen.admin;

import net.minecraft.core.BlockPos;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.house.HouseBounds;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseStatus;
import net.schwarz.rotasutils.house.HouseTenancy;
import net.schwarz.rotasutils.house.HouseTier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HouseAdminPresentationTest {
    private static final HouseBounds SMALL = HouseBounds.between("minecraft:overworld",
            new BlockPos(0, 64, 0), new BlockPos(3, 67, 4));

    @Test
    void rowsAreSortedByEnabledStatusNameAndId() {
        HouseDefinition available = house("available", "Zeta", true, 1L);
        HouseDefinition active = house("active", "Alpha", true, 2L);
        HouseDefinition overdue = house("overdue", "Beta", true, 3L);
        HouseDefinition bought = house("bought", "Gamma", true, 4L);
        HouseDefinition disabled = house("disabled", "Aardvark", false, 5L);
        ClientHouseAdminState state = snapshot(List.of(disabled, bought, overdue, active, available),
                Map.of("active", tenancy(HouseStatus.ACTIVE), "overdue", tenancy(HouseStatus.OVERDUE),
                        "bought", tenancy(HouseStatus.BOUGHT_OUT)));

        assertEquals(List.of("available", "active", "overdue", "bought", "disabled"),
                HouseAdminPresentation.overviewRows(state, "").stream().map(HouseAdminPresentation.OverviewRow::id).toList());
    }

    @Test
    void searchMatchesIdAndDisplayNameWithoutCaseSensitivity() {
        ClientHouseAdminState state = snapshot(List.of(
                house("moonlit_cabin", "Quiet Retreat", true, 1L),
                house("river", "Moonlit Villa", true, 2L)), Map.of());

        assertEquals(List.of("moonlit_cabin"), ids(HouseAdminPresentation.overviewRows(state, "CABIN")));
        assertEquals(List.of("river"), ids(HouseAdminPresentation.overviewRows(state, "vIlLa")));
        assertEquals(2, HouseAdminPresentation.overviewRows(state, "").size());
    }

    @Test
    void tagsUseTheScreenSemanticVocabulary() {
        ClientHouseAdminState state = snapshot(List.of(
                house("available", "Available", true, 1L),
                house("active", "Active", true, 2L),
                house("overdue", "Overdue", true, 3L),
                house("bought", "Bought", true, 4L),
                house("disabled", "Disabled", false, 5L)),
                Map.of("active", tenancy(HouseStatus.ACTIVE), "overdue", tenancy(HouseStatus.OVERDUE),
                        "bought", tenancy(HouseStatus.BOUGHT_OUT)));

        assertEquals(Set.of("AVAILABLE", "ACTIVE", "OVERDUE", "OWNED", "DISABLED"),
                HouseAdminPresentation.overviewRows(state, "").stream()
                        .map(HouseAdminPresentation.OverviewRow::tag).collect(java.util.stream.Collectors.toSet()));
        assertEquals(HouseAdminPresentation.StatusRole.ACCENT,
                row(state, "active").statusRole());
        assertEquals(HouseAdminPresentation.StatusRole.MUTED,
                row(state, "disabled").statusRole());
    }

    @Test
    void selectionSummaryDistinguishesMissingPartialAndComplete() {
        ClientHouseAdminState missing = snapshot(List.of(), Map.of());
        ClientHouseAdminState partial = snapshot(List.of(), Map.of(),
                new HouseAdminService.Selection("minecraft:overworld", new BlockPos(1, 64, 2), null));
        ClientHouseAdminState complete = snapshot(List.of(), Map.of(),
                new HouseAdminService.Selection("minecraft:overworld", new BlockPos(1, 64, 2), new BlockPos(3, 66, 4)));

        assertEquals(HouseAdminPresentation.SelectionState.MISSING, HouseAdminPresentation.selection(missing).state());
        assertTrue(HouseAdminPresentation.selection(missing).text().toLowerCase().contains("wand"));
        assertEquals(HouseAdminPresentation.SelectionState.PARTIAL, HouseAdminPresentation.selection(partial).state());
        assertEquals(HouseAdminPresentation.SelectionState.COMPLETE, HouseAdminPresentation.selection(complete).state());
        assertEquals(3L * 3L * 3L, HouseAdminPresentation.selection(complete).volume());
    }

    @Test
    void emptyOverviewExplainsHowToMakeASelection() {
        ClientHouseAdminState state = snapshot(List.of(), Map.of());
        assertTrue(HouseAdminPresentation.overviewRows(state, "").isEmpty());
        assertTrue(HouseAdminPresentation.emptyOverviewText().toLowerCase().contains("two"));
        assertTrue(HouseAdminPresentation.emptyOverviewText().toLowerCase().contains("wand"));
    }

    @Test
    void draftsAreFieldReadyAndKeepServerRevisions() {
        HouseDefinition definition = house("cottage", "Cottage", true, 19L);
        ClientHouseAdminState state = snapshot(List.of(definition), Map.of(), null, 23L);

        HouseAdminPresentation.CreateDraft create = HouseAdminPresentation.createDraft(state);
        HouseAdminPresentation.EditDraft edit = HouseAdminPresentation.editDraft(state, "cottage");
        assertEquals("", create.id());
        assertEquals(state.configRevision(), create.configRevision());
        assertEquals("cottage", edit.id());
        assertEquals("Cottage", edit.name());
        assertTrue(edit.enabled());
        assertEquals(19L, edit.definitionRevision());
        assertEquals("starter", edit.tier());
    }

    private static List<String> ids(List<HouseAdminPresentation.OverviewRow> rows) {
        return rows.stream().map(HouseAdminPresentation.OverviewRow::id).toList();
    }

    private static HouseAdminPresentation.OverviewRow row(ClientHouseAdminState state, String id) {
        return HouseAdminPresentation.overviewRows(state, "").stream()
                .filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private static HouseDefinition house(String id, String name, boolean enabled, long revision) {
        return new HouseDefinition(id, name, "starter", SMALL, enabled, revision);
    }

    private static HouseTenancy tenancy(HouseStatus status) {
        return new HouseTenancy(status, UUID.randomUUID(), Set.of(), 1L, 2L, 0L, 0, 0L);
    }

    private static ClientHouseAdminState snapshot(List<HouseDefinition> houses,
                                                  Map<String, HouseTenancy> tenancies) {
        return snapshot(houses, tenancies, null, 7L);
    }

    private static ClientHouseAdminState snapshot(List<HouseDefinition> houses,
                                                  Map<String, HouseTenancy> tenancies,
                                                  HouseAdminService.Selection selection) {
        return snapshot(houses, tenancies, selection, 7L);
    }

    private static ClientHouseAdminState snapshot(List<HouseDefinition> houses,
                                                  Map<String, HouseTenancy> tenancies,
                                                  HouseAdminService.Selection selection, long revision) {
        Map<String, HouseTier> tiers = new LinkedHashMap<>();
        tiers.put("starter", new HouseTier("starter", 1_000L, 100L));
        HouseConfig config = new HouseConfig("rotas:gold", 259_200_000L, 86_400_000L, 3_600_000L,
                10, 5, 0L, 0, tiers, revision);
        return ClientHouseAdminState.of(houses, config, tenancies, selection);
    }
}
