package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HouseDomainTest {
    @Test
    void boundsContainOnlyTheirOwnDimensionAndInclusiveVolume() {
        HouseBounds bounds = HouseBounds.between("minecraft:overworld", new BlockPos(10, 60, 20), new BlockPos(12, 62, 24));

        assertTrue(bounds.contains("minecraft:overworld", new BlockPos(10, 60, 20)));
        assertTrue(bounds.contains("minecraft:overworld", new BlockPos(12, 62, 24)));
        assertFalse(bounds.contains("minecraft:the_nether", new BlockPos(11, 61, 22)));
        assertFalse(bounds.contains("minecraft:overworld", new BlockPos(13, 61, 22)));
        assertEquals(45, bounds.volume());
    }

    @Test
    void tenancyRoundTripKeepsOwnerMembersAndDeadlines() {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID member = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        HouseTenancy tenancy = new HouseTenancy(HouseStatus.OVERDUE, owner, Set.of(member),
                1000L, 2000L, 75L, 2, 7L);

        HouseTenancy restored = HouseTenancy.load(tenancy.save());

        assertEquals(HouseStatus.OVERDUE, restored.status());
        assertEquals(owner, restored.owner());
        assertEquals(Set.of(member), restored.members());
        assertEquals(1000L, restored.nextPaymentAt());
        assertEquals(2000L, restored.graceEndsAt());
        assertEquals(75L, restored.overdueCharge());
        assertEquals(2, restored.purchasedMemberSlots());
        assertEquals(7L, restored.revision());
    }

    @Test
    void defaultConfigurationMatchesTheApprovedBrief() {
        HouseConfig config = HouseConfig.defaults();

        assertEquals(0L, config.revision());
        assertEquals("rotas:gold", config.currency());
        assertEquals(259_200_000L, config.paymentIntervalMillis());
        assertEquals(86_400_000L, config.reminderLeadMillis());
        assertEquals(3_600_000L, config.graceMillis());
        assertEquals(10, config.buyoutMultiplier());
        assertEquals(5, config.baseMemberLimit());
        assertNotNull(config.tier("starter"));
    }

    @Test
    void revisedConfigurationRoundTripsItsRevision() {
        HouseConfig original = HouseConfig.defaults();
        HouseConfig revised = original.withRevision(7L);

        assertEquals(7L, HouseConfig.load(revised.save()).revision());
        assertEquals(original, revised.withRevision(0L));
    }

    @Test
    void configurationWithoutRevisionDefaultsToZero() {
        CompoundTag oldTagWithoutRevision = HouseConfig.defaults().save();
        oldTagWithoutRevision.remove("revision");

        assertFalse(oldTagWithoutRevision.contains("revision"));
        assertEquals(0L, HouseConfig.load(oldTagWithoutRevision).revision());
    }

    @Test
    void configurationRejectsNegativeRevision() {
        assertThrows(IllegalArgumentException.class, () -> HouseConfig.defaults().withRevision(-1L));
    }

    @Test
    void corruptTenancyFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", "ACTIVE");
        assertThrows(IllegalArgumentException.class, () -> HouseTenancy.load(tag));
    }
}
