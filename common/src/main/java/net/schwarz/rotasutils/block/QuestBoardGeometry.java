package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class QuestBoardGeometry {
    public static final int COLUMNS = 3;
    public static final int ROWS = 2;
    public static final int MAIN_COLUMN = 1;
    public static final int MAIN_ROW = 0;

    static final double[][] NORTH = {
            {-14.8571, 0, 6.8457, -12.5714, 32, 10.5829},
            {28.5714, 0, 6.8457, 30.8571, 32, 10.5829},
            {-12.5714, 10.2857, 6.8457, 28.5714, 32, 10.5829}};

    private QuestBoardGeometry() {
    }

    public static double[][] boxes(Direction facing) {
        double[][] out = new double[NORTH.length][];
        for (int i = 0; i < NORTH.length; i++) {
            out[i] = rotate(NORTH[i], facing);
        }
        return out;
    }

    public static BlockPos cellOffset(Direction facing, int column, int row) {
        Direction along = facing.getClockWise();
        int step = column - MAIN_COLUMN;
        return new BlockPos(along.getStepX() * step, row - MAIN_ROW, along.getStepZ() * step);
    }

    public static boolean isMain(int column, int row) {
        return column == MAIN_COLUMN && row == MAIN_ROW;
    }

    static double[] rotate(double[] box, Direction facing) {
        double minX = box[0], minY = box[1], minZ = box[2], maxX = box[3], maxY = box[4], maxZ = box[5];
        return switch (facing) {
            case EAST -> new double[]{16 - maxZ, minY, minX, 16 - minZ, maxY, maxX};
            case SOUTH -> new double[]{16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ};
            case WEST -> new double[]{minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX};
            default -> box.clone();
        };
    }
}
