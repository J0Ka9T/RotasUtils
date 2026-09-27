package net.schwarz.rotasutils.network;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.house.HouseAdminService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RotasNetworkContentTest {
    /**
     * Serializer-boundary coverage only; this is not authenticated ServerPlayer proof.
     * The public ServerPlayer entry recomputes permission and held-wand selection before
     * delegating to this package-private encoder context.
     */
    @Test
    void serializerBoundaryScopesAdminSnapshotAndUsesActorSelection() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RotasData data = new RotasData();
        HouseAdminService.Selection adminSelection = new HouseAdminService.Selection("minecraft:overworld",
                new BlockPos(7, 70, 8), new BlockPos(9, 72, 10));
        HouseAdminService.Selection otherSelection = new HouseAdminService.Selection("minecraft:the_nether",
                new BlockPos(100, 40, 100), null);

        RotasNetwork.ContentActor admin = new RotasNetwork.ContentActor(UUID.randomUUID(), true, false,
                ItemStack.EMPTY, ItemStack.EMPTY, null, adminSelection);
        RotasNetwork.ContentActor other = new RotasNetwork.ContentActor(UUID.randomUUID(), false, false,
                ItemStack.EMPTY, ItemStack.EMPTY, null, otherSelection);

        CompoundTag adminContent = RotasNetwork.contentTag(admin, data);
        assertTrue(adminContent.getBoolean("admin"));
        assertTrue(adminContent.contains("house_admin", Tag.TAG_COMPOUND));
        ClientHouseAdminState adminSnapshot = ClientHouseAdminState.load(adminContent.getCompound("house_admin"));
        assertNotNull(adminSnapshot);
        assertEquals(adminSelection.dimension(), adminSnapshot.selection().dimension());
        assertEquals(adminSelection.first(), adminSnapshot.selection().first());
        assertEquals(adminSelection.second(), adminSnapshot.selection().second());

        CompoundTag otherContent = RotasNetwork.contentTag(other, data);
        assertFalse(otherContent.getBoolean("admin"));
        assertFalse(otherContent.contains("house_admin"));
    }
}
