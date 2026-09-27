package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HouseAdminServiceTest {
    private static final UUID ADMIN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final HouseBounds FIRST_BOUNDS = HouseBounds.between("minecraft:overworld",
            new BlockPos(0, 64, 0), new BlockPos(3, 67, 3));
    private static final HouseBounds SECOND_BOUNDS = HouseBounds.between("minecraft:overworld",
            new BlockPos(10, 64, 10), new BlockPos(13, 67, 13));

    @Test
    void permissionDenialLeavesStoreAndSelectionUntouched() {
        FakeStore store = new FakeStore();
        store.admin = false;
        store.selection = selection(FIRST_BOUNDS);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.create(ADMIN,
                new HouseAdminService.CreateRequest("cottage", "Cottage", "starter", 0L));

        assertFalse(action.success());
        assertTrue(store.houses.isEmpty());
        assertNotNull(store.selection);
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
        assertEquals(0, store.rebuildCount);
    }

    @Test
    void createUsesServerSelectionAndClearsOnlyAfterSuccess() {
        FakeStore store = new FakeStore();
        store.selection = selection(FIRST_BOUNDS);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.create(ADMIN,
                new HouseAdminService.CreateRequest("cottage", "Cottage", "starter", 0L));

        assertTrue(action.success(), action.message());
        HouseDefinition created = store.houses.get("cottage");
        assertNotNull(created);
        assertEquals(FIRST_BOUNDS, created.bounds());
        assertEquals(1L, created.revision());
        assertNull(store.selection);
        assertEquals(1, store.auditCount);
        assertEquals(1, store.resyncCount);
        assertEquals(1, store.dirtyCount);
        assertEquals(1, store.rebuildCount);
        assertTrue(store.audit.get(0).contains("house_create"));
    }

    @Test
    void invalidSelectionIsRetainedAndDoesNotCreate() {
        FakeStore store = new FakeStore();
        store.selection = new HouseAdminService.Selection("minecraft:overworld",
                new BlockPos(0, 64, 0), null);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.create(ADMIN,
                new HouseAdminService.CreateRequest("cottage", "Cottage", "starter", 0L));

        assertFalse(action.success());
        assertTrue(store.houses.isEmpty());
        assertNotNull(store.selection);
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
    }

    @Test
    void overlappingSelectionIsRetainedAndDoesNotCreate() {
        FakeStore store = new FakeStore();
        HouseDefinition existing = new HouseDefinition("existing", "Existing", "starter", FIRST_BOUNDS, true, 4L);
        store.houses.put(existing.id(), existing);
        store.selection = selection(HouseBounds.between("minecraft:overworld",
                new BlockPos(3, 64, 3), new BlockPos(8, 67, 8)));
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.create(ADMIN,
                new HouseAdminService.CreateRequest("cottage", "Cottage", "starter", 0L));

        assertFalse(action.success());
        assertEquals(existing, store.houses.get(existing.id()));
        assertNotNull(store.selection);
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
    }

    @Test
    void staleDefinitionRevisionDoesNotMutateHouseOrEmitSideEffects() {
        FakeStore store = new FakeStore();
        HouseDefinition original = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 3L);
        store.houses.put(original.id(), original);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.edit(ADMIN,
                new HouseAdminService.EditRequest("cottage", "Renamed", "starter", true, 2L));

        assertFalse(action.success());
        assertEquals(original, store.houses.get(original.id()));
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
    }

    @Test
    void occupiedHouseCannotBeDisabledOrRemoved() {
        FakeStore store = new FakeStore();
        HouseDefinition original = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 3L);
        store.houses.put(original.id(), original);
        store.tenancies.put(original.id(), new HouseTenancy(HouseStatus.ACTIVE, ADMIN,
                Set.of(MEMBER), 10L, 0L, 0L, 0, 1L));
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action disabled = service.edit(ADMIN,
                new HouseAdminService.EditRequest("cottage", "Cottage", "starter", false, 3L));
        HouseAdminService.Action removed = service.remove(ADMIN,
                new HouseAdminService.RemoveRequest("cottage", 3L));

        assertFalse(disabled.success());
        assertFalse(removed.success());
        assertEquals(original, store.houses.get(original.id()));
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
    }

    @Test
    void staleConfigRevisionDoesNotMutateConfiguration() {
        FakeStore store = new FakeStore();
        HouseConfig original = store.config;
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.saveConfig(ADMIN,
                configRequest(4L, original.paymentIntervalMillis() + 60_000L));

        assertFalse(action.success());
        assertEquals(original, store.config);
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
    }

    @Test
    void invalidConfigIsAtomicAndRejectsReferencedTierRemoval() {
        FakeStore store = new FakeStore();
        HouseDefinition house = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 2L);
        store.houses.put(house.id(), house);
        HouseConfig original = store.config;
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.saveConfig(ADMIN,
                new HouseAdminService.ConfigRequest("rotas:gold", original.paymentIntervalMillis(),
                        original.reminderLeadMillis(), original.graceMillis(), original.buyoutMultiplier(),
                        original.baseMemberLimit(), original.memberSlotPrice(), original.maxPurchasedMemberSlots(),
                        java.util.List.of(new HouseTier("premium", 2_000L, 200L)), original.revision()));

        assertFalse(action.success());
        assertEquals(original, store.config);
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
    }

    @Test
    void validConfigIncrementsRevisionOnceAndAuditsAndResyncs() {
        FakeStore store = new FakeStore();
        HouseConfig original = store.config;
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.saveConfig(ADMIN,
                configRequest(original.revision(), original.paymentIntervalMillis() + 60_000L));

        assertTrue(action.success(), action.message());
        assertEquals(original.revision() + 1L, store.config.revision());
        assertEquals(original.paymentIntervalMillis() + 60_000L, store.config.paymentIntervalMillis());
        assertEquals(1, store.auditCount);
        assertEquals(1, store.resyncCount);
        assertEquals(1, store.dirtyCount);
        assertEquals(1, store.rebuildCount);
        assertTrue(store.audit.get(0).contains("house_save_config"));
    }

    @Test
    void editIncrementsDefinitionRevisionOnce() {
        FakeStore store = new FakeStore();
        HouseDefinition original = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 7L);
        store.houses.put(original.id(), original);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.edit(ADMIN,
                new HouseAdminService.EditRequest("cottage", "New Cottage", "starter", true, 7L));

        assertTrue(action.success(), action.message());
        assertEquals(8L, store.houses.get(original.id()).revision());
        assertEquals(1, store.auditCount);
        assertEquals(1, store.resyncCount);
        assertEquals(1, store.dirtyCount);
    }

    @Test
    void replaceBoundsUsesServerSelectionAndIncrementsRevision() {
        FakeStore store = new FakeStore();
        HouseDefinition original = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 7L);
        store.houses.put(original.id(), original);
        store.selection = selection(SECOND_BOUNDS);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.replaceBounds(ADMIN,
                new HouseAdminService.ReplaceBoundsRequest("cottage", 7L));

        assertTrue(action.success(), action.message());
        assertEquals(SECOND_BOUNDS, store.houses.get(original.id()).bounds());
        assertEquals(8L, store.houses.get(original.id()).revision());
        assertNull(store.selection);
        assertEquals(1, store.auditCount);
        assertEquals(1, store.resyncCount);
    }

    @Test
    void unoccupiedHouseCanBeRemovedWithMatchingRevision() {
        FakeStore store = new FakeStore();
        HouseDefinition original = new HouseDefinition("cottage", "Cottage", "starter", FIRST_BOUNDS, true, 7L);
        store.houses.put(original.id(), original);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.remove(ADMIN,
                new HouseAdminService.RemoveRequest("cottage", 7L));

        assertTrue(action.success(), action.message());
        assertNull(store.houses.get(original.id()));
        assertEquals(1, store.auditCount);
        assertEquals(1, store.resyncCount);
        assertEquals(1, store.dirtyCount);
    }

    @Test
    void creationAtHouseLimitReturnsFailureWithoutMutationOrSideEffects() {
        FakeStore store = new FakeStore();
        for (int index = 0; index < 4096; index++) {
            String id = "house_" + index;
            store.houses.put(id, new HouseDefinition(id, "House " + index, "starter", FIRST_BOUNDS, false, 1L));
        }
        store.selection = selection(SECOND_BOUNDS);
        HouseAdminService service = new HouseAdminService(store);

        HouseAdminService.Action action = service.create(ADMIN,
                new HouseAdminService.CreateRequest("new_house", "New House", "starter", 0L));

        assertFalse(action.success());
        assertEquals("house.limit_reached", action.message());
        assertEquals(4096, store.houses.size());
        assertNotNull(store.selection);
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
        assertEquals(0, store.rebuildCount);
    }

    private static HouseAdminService.Selection selection(HouseBounds bounds) {
        return new HouseAdminService.Selection(bounds.dimension(),
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }

    private static HouseAdminService.ConfigRequest configRequest(long revision, long paymentInterval) {
        HouseConfig defaults = HouseConfig.defaults();
        return new HouseAdminService.ConfigRequest(defaults.currency(), paymentInterval,
                defaults.reminderLeadMillis(), defaults.graceMillis(), defaults.buyoutMultiplier(),
                defaults.baseMemberLimit(), defaults.memberSlotPrice(), defaults.maxPurchasedMemberSlots(),
                java.util.List.of(new HouseTier("starter", 1_000L, 100L)), revision);
    }

    private static final class FakeStore implements HouseAdminService.Store {
        private boolean admin = true;
        private final Map<String, HouseDefinition> houses = new LinkedHashMap<>();
        private final Map<String, HouseTenancy> tenancies = new LinkedHashMap<>();
        private HouseConfig config = HouseConfig.defaults();
        private HouseAdminService.Selection selection;
        private int dirtyCount;
        private int rebuildCount;
        private int auditCount;
        private int resyncCount;
        private final java.util.List<String> audit = new java.util.ArrayList<>();

        @Override
        public boolean isAdmin(UUID actor) {
            return admin && ADMIN.equals(actor);
        }

        @Override
        public Map<String, HouseDefinition> houses() {
            return houses;
        }

        @Override
        public HouseTenancy tenancy(String id) {
            return tenancies.getOrDefault(id, HouseTenancy.available());
        }

        @Override
        public HouseConfig config() {
            return config;
        }

        @Override
        public HouseAdminService.Selection selection(UUID actor) {
            return selection;
        }

        @Override
        public void clearSelection(UUID actor) {
            selection = null;
        }

        @Override
        public void putHouse(HouseDefinition house) {
            if (!houses.containsKey(house.id()) && houses.size() >= 4096) {
                throw new IllegalStateException("House limit reached");
            }
            houses.put(house.id(), house);
        }

        @Override
        public void removeHouse(String id) {
            houses.remove(id);
            tenancies.remove(id);
        }

        @Override
        public void setConfig(HouseConfig config) {
            this.config = config;
        }

        @Override
        public void markDirty() {
            dirtyCount++;
        }

        @Override
        public void rebuildHousing() {
            rebuildCount++;
        }

        @Override
        public void audit(String record) {
            auditCount++;
            audit.add(record);
        }

        @Override
        public void resync() {
            resyncCount++;
        }
    }
}
