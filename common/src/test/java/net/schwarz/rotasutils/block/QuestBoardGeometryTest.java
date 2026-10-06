package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBoardGeometryTest {
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @Test
    void footprintIsSixDistinctCellsAroundTheMainBlock() {
        for (Direction facing : FACINGS) {
            Set<BlockPos> cells = new HashSet<>();
            for (int column = 0; column < QuestBoardGeometry.COLUMNS; column++) {
                for (int row = 0; row < QuestBoardGeometry.ROWS; row++) {
                    BlockPos offset = QuestBoardGeometry.cellOffset(facing, column, row);
                    assertTrue(Math.abs(offset.getX()) <= 1 && Math.abs(offset.getZ()) <= 1, facing + " " + offset);
                    assertTrue(offset.getY() == 0 || offset.getY() == 1, facing + " " + offset);
                    assertEquals(0, offset.getX() * facing.getStepX() + offset.getZ() * facing.getStepZ());
                    cells.add(offset);
                }
            }
            assertEquals(6, cells.size(), "six distinct cells for " + facing);
            assertEquals(BlockPos.ZERO, QuestBoardGeometry.cellOffset(facing, QuestBoardGeometry.MAIN_COLUMN,
                    QuestBoardGeometry.MAIN_ROW));
        }
    }

    @Test
    void hitboxStaysInsideTheFootprintAndReachesEveryCell() {
        for (Direction facing : FACINGS) {
            double[][] boxes = QuestBoardGeometry.boxes(facing);
            for (double[] box : boxes) {
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(box[axis] < box[axis + 3], "box min < max for " + facing);
                    assertTrue(box[axis] >= -16 && box[axis + 3] <= 32, "box inside footprint for " + facing);
                }
            }
            for (int column = 0; column < QuestBoardGeometry.COLUMNS; column++) {
                for (int row = 0; row < QuestBoardGeometry.ROWS; row++) {
                    BlockPos offset = QuestBoardGeometry.cellOffset(facing, column, row);
                    double[] cell = {offset.getX() * 16, offset.getY() * 16, offset.getZ() * 16,
                            offset.getX() * 16 + 16, offset.getY() * 16 + 16, offset.getZ() * 16 + 16};
                    boolean reached = false;
                    for (double[] box : boxes) {
                        reached |= box[0] < cell[3] && box[3] > cell[0]
                                && box[1] < cell[4] && box[4] > cell[1]
                                && box[2] < cell[5] && box[5] > cell[2];
                    }
                    assertTrue(reached, "hitbox reaches cell " + column + "," + row + " for " + facing);
                }
            }
        }
    }

    @Test
    void rotationMatchesTheBlockstateModelRotation() {
        double[] post = QuestBoardGeometry.NORTH[0];
        double[] east = QuestBoardGeometry.rotate(post, Direction.EAST);
        double[] south = QuestBoardGeometry.rotate(post, Direction.SOUTH);
        double[] back = QuestBoardGeometry.rotate(QuestBoardGeometry.rotate(south, Direction.SOUTH), Direction.NORTH);
        assertArrayEquals(post, back, 1e-9);
        assertTrue(east[2] < 0 && east[5] < 0);
        assertTrue(south[0] > 16);
    }
}
