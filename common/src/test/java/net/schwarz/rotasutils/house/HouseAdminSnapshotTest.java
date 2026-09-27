package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


import static org.junit.jupiter.api.Assertions.*;

class HouseAdminSnapshotTest {
    private static final HouseBounds BOUNDS = HouseBounds.between("minecraft:overworld",
            new BlockPos(1, 64, 2), new BlockPos(4, 67, 5));

    @Test
    void snapshotCarriesFullDefinitionsConfigRevisionAndOrderedTiers() {
        HouseDefinition house = new HouseDefinition("cottage", "Cottage", "starter", BOUNDS, true, 9L);
        HouseConfig config = config(12L, List.of(new HouseTier("premium", 2_000L, 200L),
                new HouseTier("starter", 1_000L, 100L)));
        ClientHouseAdminState snapshot = ClientHouseAdminState.of(
                List.of(house), config, Map.of("cottage", occupied()),
                new HouseAdminService.Selection("minecraft:overworld", new BlockPos(1, 64, 2),
                        new BlockPos(4, 67, 5)));

        assertEquals(12L, snapshot.configRevision());
        assertEquals(9L, snapshot.definitions().get(0).revision());
        assertEquals(List.of("premium", "starter"), snapshot.tiers().stream().map(HouseTier::id).toList());
        assertEquals(HouseStatus.ACTIVE, snapshot.statuses().get(0).status());
        assertEquals(4L * 4L * 4L, snapshot.selection().volume());
    }

    @Test
    void snapshotNbtContainsNoOwnerOrMemberIdentity() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        ClientHouseAdminState snapshot = ClientHouseAdminState.of(
                List.of(new HouseDefinition("cottage", "Cottage", "starter", BOUNDS, true, 9L)),
                config(12L, List.of(new HouseTier("starter", 1_000L, 100L))),
                Map.of("cottage", new HouseTenancy(HouseStatus.ACTIVE, owner, Set.of(member),
                        10L, 20L, 30L, 1, 4L)), null);

        CompoundTag tag = snapshot.save();
        assertNoIdentity(tag, owner, member);
        assertFalse(tag.getAllKeys().contains("owner"));
        assertFalse(tag.getAllKeys().contains("members"));
        ClientHouseAdminState restored = ClientHouseAdminState.load(tag);
        assertNotNull(restored);
        assertEquals(4L, restored.statuses().get(0).revision());
        assertEquals(1, restored.statuses().get(0).memberCount());
    }

    @Test
    void malformedAndOversizedSnapshotDataFailsClosed() {
        CompoundTag tooMany = validTag();
        ListTag definitions = tooMany.getList("definitions", Tag.TAG_COMPOUND);
        for (int i = 0; i < ClientHouseAdminState.MAX_DEFINITIONS + 1; i++) {
            definitions.add(definitions.getCompound(0).copy());
        }
        assertNull(ClientHouseAdminState.load(tooMany));

        CompoundTag malformed = validTag();
        malformed.getCompound("config").putString("currency", "x".repeat(129));
        assertNull(ClientHouseAdminState.load(malformed));

        CompoundTag malformedTier = validTag();
        malformedTier.getList("tiers", Tag.TAG_COMPOUND).add(new CompoundTag());
        assertNull(ClientHouseAdminState.load(malformedTier));
    }

    @Test
    void selectionSummaryUsesOnlyTheRequestingAdministratorsSelection() {
        ClientHouseAdminState snapshot = ClientHouseAdminState.of(List.of(), HouseConfig.defaults(), Map.of(),
                new HouseAdminService.Selection("minecraft:the_nether", new BlockPos(-2, 10, 3), null));

        assertNotNull(snapshot.selection());
        assertEquals("minecraft:the_nether", snapshot.selection().dimension());
        assertEquals(new BlockPos(-2, 10, 3), snapshot.selection().first());
        assertNull(snapshot.selection().second());
        assertEquals(0L, snapshot.selection().volume());
        assertFalse(snapshot.save().toString().contains("owner"));
    }

    @Test
    void malformedSelectionDimensionsFailClosed() {
        CompoundTag malformed = validTag();
        CompoundTag selection = new CompoundTag();
        selection.putString("dimension", "minecraft:overworld");
        selection.putLong("first", new BlockPos(0, 64, 0).asLong());
        selection.putLong("second", new BlockPos(1, 65, 1).asLong());
        selection.putLong("size_x", 99L);
        selection.putLong("size_y", 2L);
        selection.putLong("size_z", 2L);
        selection.putLong("volume", 396L);
        malformed.put("selection", selection);
        assertNull(ClientHouseAdminState.load(malformed));
    }

    @Test
    void wrongTypedSelectionCoordinatesFailClosedInsteadOfBecomingPartial() {
        CompoundTag malformed = validTagWithSelection();
        CompoundTag selection = malformed.getCompound("selection");
        selection.put("second", new CompoundTag());
        zeroSelectionSizes(selection);
        assertNull(ClientHouseAdminState.load(malformed));

        malformed = validTagWithSelection();
        selection = malformed.getCompound("selection");
        selection.putIntArray("first", new int[]{1, 2});
        zeroSelectionSizes(selection);
        assertNull(ClientHouseAdminState.load(malformed));
    }

    @Test
    void wrongTypedSelectionSizesAndIncompleteArraysFailClosed() {
        CompoundTag malformed = validPartialTag();
        CompoundTag selection = malformed.getCompound("selection");
        selection.putInt("size_x", 2);
        assertNull(ClientHouseAdminState.load(malformed));

        malformed = validTagWithSelection();
        selection = malformed.getCompound("selection");
        selection.putIntArray("second", new int[]{4, 67});
        zeroSelectionSizes(selection);
        assertNull(ClientHouseAdminState.load(malformed));
    }

    private static CompoundTag validTag() {
        return ClientHouseAdminState.of(
                List.of(new HouseDefinition("cottage", "Cottage", "starter", BOUNDS, true, 9L)),
                config(12L, List.of(new HouseTier("starter", 1_000L, 100L))), Map.of(), null).save();
    }

    private static CompoundTag validTagWithSelection() {
        return ClientHouseAdminState.of(
                List.of(new HouseDefinition("cottage", "Cottage", "starter", BOUNDS, true, 9L)),
                config(12L, List.of(new HouseTier("starter", 1_000L, 100L))), Map.of(),
                new HouseAdminService.Selection("minecraft:overworld", new BlockPos(1, 64, 2),
                new BlockPos(4, 67, 5))).save();
    }

    private static CompoundTag validPartialTag() {
        return ClientHouseAdminState.of(List.of(), HouseConfig.defaults(), Map.of(),
                new HouseAdminService.Selection("minecraft:overworld", new BlockPos(1, 64, 2), null)).save();
    }

    private static void zeroSelectionSizes(CompoundTag selection) {
        selection.putLong("size_x", 0L);
        selection.putLong("size_y", 0L);
        selection.putLong("size_z", 0L);
        selection.putLong("volume", 0L);
    }

    private static HouseConfig config(long revision, List<HouseTier> tiers) {
        Map<String, HouseTier> map = new LinkedHashMap<>();
        for (HouseTier tier : tiers) map.put(tier.id(), tier);
        return new HouseConfig("rotas:gold", 259_200_000L, 86_400_000L, 3_600_000L,
                10, 5, 0L, 0, map, revision);
    }

    private static HouseTenancy occupied() {
        return new HouseTenancy(HouseStatus.ACTIVE, UUID.randomUUID(), Set.of(), 10L, 20L, 0L, 0, 4L);
    }

    private static void assertNoIdentity(Tag tag, UUID... identities) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                assertNotEquals("owner", key);
                assertNotEquals("member", key);
                assertNotEquals("members", key);
                assertNoIdentity(compound.get(key), identities);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag value : list) assertNoIdentity(value, identities);
        } else if (tag instanceof StringTag string) {
            for (UUID identity : identities) assertNotEquals(identity.toString(), string.getAsString());
        }
    }
}
