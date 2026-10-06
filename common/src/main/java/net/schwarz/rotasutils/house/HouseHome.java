package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;

public final class HouseHome {
    private HouseHome() {
    }

    public interface Blocks {
        boolean solid(int x, int y, int z);

        boolean passable(int x, int y, int z);
    }

    public static BlockPos spot(HouseBounds b, Blocks blocks) {
        int cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
        int reach = Math.max(b.maxX() - b.minX(), b.maxZ() - b.minZ()) / 2;
        for (int ring = 0; ring <= reach; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = cx + dx, z = cz + dz;
                    if (x < b.minX() || x > b.maxX() || z < b.minZ() || z > b.maxZ()) {
                        continue;
                    }
                    for (int y = b.maxY() - 1; y > b.minY(); y--) {
                        if (blocks.solid(x, y - 1, z) && blocks.passable(x, y, z) && blocks.passable(x, y + 1, z)) {
                            return new BlockPos(x, y, z);
                        }
                    }
                }
            }
        }
        return new BlockPos(cx, (b.minY() + b.maxY()) / 2, cz);
    }
}
