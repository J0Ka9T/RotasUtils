package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseBounds;
import net.minecraft.core.BlockPos;
import net.schwarz.rotasutils.network.ClientAdminNetwork;
import net.schwarz.rotasutils.network.ClientProgressSync;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientResetTest {
    @Test void resetDropsServerDerivedClientState() {
        CompoundTag party = new CompoundTag();
        ListTag members = new ListTag();
        CompoundTag member = new CompoundTag();
        member.putUUID("id", UUID.randomUUID());
        member.putString("name", "Dev");
        members.add(member);
        party.put("members", members);
        party.putString("invite_from", "Someone");
        party.putInt("max_size", 8);
        party.putBoolean("enabled", true);
        party.putDouble("radius", 32);
        ClientState.applyParty(party);
        ClientHouseAdminState adminSnapshot = ClientHouseAdminState.of(
                java.util.List.of(new HouseDefinition("cottage", "Cottage", "starter",
                        HouseBounds.between("minecraft:overworld", new BlockPos(0, 64, 0),
                                new BlockPos(1, 65, 1)), true, 3L)),
                HouseConfig.defaults().withRevision(7L), java.util.Map.of(),
                new HouseAdminService.Selection("minecraft:overworld", new BlockPos(0, 64, 0), null));
        CompoundTag adminContent = new CompoundTag();
        adminContent.putBoolean("admin", true);
        adminContent.put("house_admin", adminSnapshot.save());
        ClientState.applyContent(adminContent);
        assertNotNull(ClientState.houseAdmin());
        ClientState.applyContent(new CompoundTag());
        assertNull(ClientState.houseAdmin());
        ClientState.applyContent(adminContent);
        assertNotNull(ClientState.houseAdmin());
        CompoundTag malformedAdmin = new CompoundTag();
        malformedAdmin.putBoolean("admin", true);
        malformedAdmin.putString("house_admin", "not a compound");
        ClientState.applyContent(malformedAdmin);
        assertNull(ClientState.houseAdmin());
        ClientState.applyContent(adminContent);
        ClientState.feedback(false, "boom");
        assertEquals(1, ClientState.party().size());
        assertEquals("Someone", ClientState.partyInviteFrom());
        assertEquals("boom", ClientState.feedbackMessage());

        CompoundTag kernel = new CompoundTag();
        kernel.putBoolean("ready", true);
        kernel.putString("hash", "abc");
        ClientKernelState.apply(kernel);
        assertTrue(ClientKernelState.ready());
        assertEquals("abc", ClientKernelState.contentHash());

        ClientState.reset();
        ClientKernelState.reset();
        ClientProgressSync.reset();
        ClientAdminNetwork.reset();

        assertTrue(ClientState.party().isEmpty());
        assertTrue(ClientState.partyInviteFrom().isEmpty());
        assertTrue(ClientState.partyEnabled());
        assertEquals(64.0, ClientState.partyRadius());
        assertEquals(10, ClientState.partyMaxSize());
        assertEquals("", ClientState.feedbackMessage());
        assertFalse(ClientState.admin());
        assertFalse(ClientState.operator());
        assertNull(ClientState.houseAdmin());
        assertFalse(ClientKernelState.ready());
        assertEquals("", ClientKernelState.contentHash());
        assertEquals(0L, ClientKernelState.revision());
        assertTrue(ClientKernelState.merchants().isEmpty());
        assertTrue(ClientKernelState.previewLines().isEmpty());
    }
}
