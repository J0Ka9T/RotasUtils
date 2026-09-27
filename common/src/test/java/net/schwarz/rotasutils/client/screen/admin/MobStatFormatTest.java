package net.schwarz.rotasutils.client.screen.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobStatFormatTest {
    @Test
    void hugeStatsShortenSoTheyFitAColumn() {
        assertEquals("5M", MobStatFormat.fmt(5_000_002));
        assertEquals("5.04M", MobStatFormat.fmt(5_040_000));
        assertEquals("12.3K", MobStatFormat.fmt(12_345));
        assertEquals("1.5B", MobStatFormat.fmt(1_500_000_000));
        assertEquals("3000", MobStatFormat.fmt(3000));
        assertEquals("500", MobStatFormat.fmt(500));
    }

    @Test
    void smallStatsKeepTheirDecimalsWithoutTrailingZeros() {
        assertEquals("0.3", MobStatFormat.fmt(0.3));
        assertEquals("0.25", MobStatFormat.fmt(0.25));
        assertEquals("1", MobStatFormat.fmt(1.0));
        assertEquals("0", MobStatFormat.fmt(0));
        assertEquals("30", MobStatFormat.fmt(30));
    }
}
