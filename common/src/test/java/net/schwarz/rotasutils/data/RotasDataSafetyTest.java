package net.schwarz.rotasutils.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotasDataSafetyTest {
    @Test
    void storeThatFailedToLoadNeverReportsDirty() {
        RotasData data = new RotasData();
        data.blockSaves();
        data.progress(UUID.randomUUID());
        data.setDirty();

        assertTrue(data.saveBlocked());
        assertFalse(data.isDirty());
    }

    @Test
    void healthyStoreStillSaves() {
        RotasData data = new RotasData();
        data.setDirty();

        assertFalse(data.saveBlocked());
        assertTrue(data.isDirty());
    }

    @Test
    void disabledAuditLogRecordsNothing() {
        RotasData data = new RotasData();
        data.serverSettings().setAuditLogEnabled(false);
        data.audit("actor did something");

        assertTrue(data.auditLog().isEmpty());
    }

    @Test
    void storedSettingsAreClampedToTheirEditorRanges() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("admin_op_level", 9);
        tag.putInt("max_party", 500);
        tag.putDouble("party_radius", Double.NaN);

        ServerSettings settings = ServerSettings.load(tag);
        assertEquals(4, settings.adminOpLevel());
        assertEquals(64, settings.maxPartySize());
        assertEquals(64.0, settings.partyNearbyRadius());

        tag.putInt("admin_op_level", -3);
        assertEquals(2, ServerSettings.load(tag).adminOpLevel(), "admin level below 2 would make every player an admin");
    }
}
