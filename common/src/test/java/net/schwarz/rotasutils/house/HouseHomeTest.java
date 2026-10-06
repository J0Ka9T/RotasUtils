package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseHomeTest {
    private static final HouseBounds BOX = new HouseBounds("minecraft:overworld", 0, 64, 0, 10, 72, 10);

    private static HouseHome.Blocks floorAt(int floorY) {
        return new HouseHome.Blocks() {
            public boolean solid(int x, int y, int z) {
                return y <= floorY;
            }

            public boolean passable(int x, int y, int z) {
                return y > floorY;
            }
        };
    }

    @Test
    void standsInTheMiddleOnTheFloorWithHeadroom() {
        assertEquals(new BlockPos(5, 65, 5), HouseHome.spot(BOX, floorAt(64)));
    }

    @Test
    void takesTheHighestFloorWhenThereAreTwoStoreys() {
        HouseHome.Blocks twoFloors = new HouseHome.Blocks() {
            public boolean solid(int x, int y, int z) {
                return y == 64 || y == 69;
            }

            public boolean passable(int x, int y, int z) {
                return y != 64 && y != 69;
            }
        };
        assertEquals(70, HouseHome.spot(BOX, twoFloors).getY());
    }

    @Test
    void walksPastAPillarInTheMiddle() {
        HouseHome.Blocks pillar = new HouseHome.Blocks() {
            public boolean solid(int x, int y, int z) {
                return y <= 64 || (x == 5 && z == 5);
            }

            public boolean passable(int x, int y, int z) {
                return y > 64 && !(x == 5 && z == 5);
            }
        };
        BlockPos spot = HouseHome.spot(BOX, pillar);
        assertTrue(!(spot.getX() == 5 && spot.getZ() == 5) && spot.getY() == 65, "next to it: " + spot);
    }

    @Test
    void fallsBackToTheMiddleOfTheBoxWhenThereIsNoFloor() {
        HouseHome.Blocks nothing = new HouseHome.Blocks() {
            public boolean solid(int x, int y, int z) {
                return false;
            }

            public boolean passable(int x, int y, int z) {
                return true;
            }
        };
        assertEquals(new BlockPos(5, 68, 5), HouseHome.spot(BOX, nothing));
    }
}
