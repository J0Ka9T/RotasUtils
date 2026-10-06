package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseAreaTest {
    private static final int MIN = -64, MAX = 320;

    @Test
    void aBoxRoundMeIsCentredAndTheSizeItsLabelSays() {
        HouseArea.Size medium = HouseArea.SIZES[1];
        int[] box = HouseArea.around(new BlockPos(100, 70, -20), medium);
        assertArrayEquals(new int[]{93, 69, -27, 107, 76, -13}, box);
        assertEquals("Medium 15x15x8", medium.text());
        assertEquals("15x8x15", HouseArea.describe(box), "x by height by z, as the wand's own message reads");
    }

    @Test
    void everySizeIsFarUnderTheVolumeLimitAndGetsBigger() {
        long last = 0;
        for (HouseArea.Size size : HouseArea.SIZES) {
            long volume = HouseArea.volume(HouseArea.around(BlockPos.ZERO, size));
            assertTrue(volume > last && volume < HouseBounds.MAX_VOLUME);
            last = volume;
        }
    }

    @Test
    void growAndShrinkMoveEverySideAndNeverBreakTheBox() {
        int[] box = {0, 64, 0, 9, 69, 9};
        assertArrayEquals(new int[]{-1, 63, -1, 10, 70, 10}, HouseArea.grow(box, 1, MIN, MAX));
        assertArrayEquals(new int[]{1, 65, 1, 8, 68, 8}, HouseArea.grow(box, -1, MIN, MAX));
        int[] thin = {0, 64, 0, 1, 64, 1};
        assertArrayEquals(thin, HouseArea.grow(thin, -1, MIN, MAX), "shrinking past a single block does nothing");
    }

    @Test
    void upAndDownMoveOnlyTheirOwnFaceAndStayInTheWorld() {
        int[] box = {0, 64, 0, 9, 69, 9};
        assertArrayEquals(new int[]{0, 64, 0, 9, 70, 9}, HouseArea.up(box, 1, MIN, MAX));
        assertArrayEquals(new int[]{0, 62, 0, 9, 69, 9}, HouseArea.down(box, 2, MIN, MAX));
        assertArrayEquals(box, HouseArea.up(box, 1000, MIN, MAX), "past the build limit does nothing");
        assertArrayEquals(box, HouseArea.down(box, 1000, MIN, MAX));
        assertArrayEquals(box, HouseArea.up(box, -6, MIN, MAX), "pulling the roof through the floor does nothing");
    }

    @Test
    void twoCornersMakeTheSameBoxWhicheverWayRound() {
        assertArrayEquals(HouseArea.between(new BlockPos(5, 70, -3), new BlockPos(-2, 64, 8)),
                HouseArea.between(new BlockPos(-2, 64, 8), new BlockPos(5, 70, -3)));
        assertArrayEquals(new int[]{-2, 64, -3, 5, 70, 8}, HouseArea.between(new BlockPos(5, 70, -3), new BlockPos(-2, 64, 8)));
    }
}
