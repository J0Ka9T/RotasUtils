package net.schwarz.rotasutils.command;

import net.minecraft.core.BlockPos;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.house.HouseBounds;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseStatus;
import net.schwarz.rotasutils.house.HouseTenancy;
import net.schwarz.rotasutils.house.HouseTier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HouseCommandsTest {
    private static final UUID ADMIN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final HouseBounds BOUNDS = HouseBounds.between("minecraft:overworld",
            new BlockPos(0, 64, 0), new BlockPos(3, 67, 3));

    @Test
    void legacyRemoveRouteDelegatesToAuthoritativeService() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/command/HouseCommands.java"));

        assertTrue(source.contains("removeThroughAuthority("),
                "legacy remove must go through the shared authoritative service helper");
        assertFalse(source.contains("data.removeHouse(id)"),
                "legacy remove must not mutate RotasData directly");
        assertFalse(source.contains("data.putHouse(def)"),
                "legacy create must not mutate RotasData directly");
    }

    @Test
    void legacyRemoveHelperRejectsOccupiedHouseWithoutMutation() {
        Store store = new Store();
        HouseDefinition definition = new HouseDefinition("cottage", "Cottage", "starter", BOUNDS, true, 6L);
        store.houses.put(definition.id(), definition);
        store.tenancies.put(definition.id(), new HouseTenancy(HouseStatus.ACTIVE, ADMIN, Set.of(MEMBER),
                10L, 0L, 0L, 0, 2L));

        HouseAdminService.Action action = HouseCommands.removeThroughAuthority(
                new HouseAdminService(store), ADMIN, definition);

        assertFalse(action.success());
        assertEquals(definition, store.houses.get(definition.id()));
        assertEquals(0, store.auditCount);
        assertEquals(0, store.resyncCount);
        assertEquals(0, store.dirtyCount);
    }

    private static final class Store implements HouseAdminService.Store {
        private final Map<String, HouseDefinition> houses = new LinkedHashMap<>();
        private final Map<String, HouseTenancy> tenancies = new LinkedHashMap<>();
        private HouseConfig config = HouseConfig.defaults();
        private int auditCount;
        private int resyncCount;
        private int dirtyCount;

        @Override
        public boolean isAdmin(UUID actor) {
            return ADMIN.equals(actor);
        }

        @Override
        public Map<String, HouseDefinition> houses() {
            return houses;
        }

        @Override
        public HouseConfig config() {
            return config;
        }

        @Override
        public HouseAdminService.Selection selection(UUID actor) {
            return null;
        }

        @Override
        public void clearSelection(UUID actor) {
        }

        @Override
        public void putHouse(HouseDefinition house) {
            houses.put(house.id(), house);
        }

        @Override
        public void removeHouse(String id) {
            houses.remove(id);
        }

        @Override
        public void setConfig(HouseConfig config) {
            this.config = config;
        }

        @Override
        public HouseTenancy tenancy(String id) {
            return tenancies.getOrDefault(id, HouseTenancy.available());
        }

        @Override
        public void markDirty() {
            dirtyCount++;
        }

        @Override
        public void rebuildHousing() {
        }

        @Override
        public void audit(String record) {
            auditCount++;
        }

        @Override
        public void resync() {
            resyncCount++;
        }
    }
}
