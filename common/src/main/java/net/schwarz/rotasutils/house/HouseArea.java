package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;

public final class HouseArea {
    private HouseArea() {
    }

    public record Size(String label, int radius, int below, int above) {
        public int width() {
            return radius * 2 + 1;
        }

        public int height() {
            return below + above + 1;
        }

        public String text() {
            return label + " " + width() + "x" + width() + "x" + height();
        }
    }

    public static final Size[] SIZES = {
            new Size("Small", 4, 1, 4),
            new Size("Medium", 7, 1, 6),
            new Size("Large", 12, 1, 8),
            new Size("Huge", 20, 1, 12),
    };

    public static int[] around(BlockPos feet, Size size) {
        return new int[]{feet.getX() - size.radius(), feet.getY() - size.below(), feet.getZ() - size.radius(),
                feet.getX() + size.radius(), feet.getY() + size.above(), feet.getZ() + size.radius()};
    }

    public static int[] between(BlockPos a, BlockPos b) {
        return new int[]{Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())};
    }

    public static int[] grow(int[] box, int n, int minY, int maxY) {
        int[] out = box.clone();
        out[0] -= n;
        out[2] -= n;
        out[3] += n;
        out[5] += n;
        out[1] -= n;
        out[4] += n;
        return fit(out, box, minY, maxY);
    }

    public static int[] up(int[] box, int n, int minY, int maxY) {
        int[] out = box.clone();
        out[4] += n;
        return fit(out, box, minY, maxY);
    }

    public static int[] down(int[] box, int n, int minY, int maxY) {
        int[] out = box.clone();
        out[1] -= n;
        return fit(out, box, minY, maxY);
    }

    private static int[] fit(int[] candidate, int[] previous, int minY, int maxY) {
        if (candidate[0] > candidate[3] || candidate[1] > candidate[4] || candidate[2] > candidate[5]
                || candidate[1] < minY || candidate[4] > maxY || volume(candidate) > HouseBounds.MAX_VOLUME) {
            return previous;
        }
        return candidate;
    }

    public static long volume(int[] box) {
        return (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
    }

    public static String describe(int[] box) {
        return (box[3] - box[0] + 1) + "x" + (box[4] - box[1] + 1) + "x" + (box[5] - box[2] + 1);
    }
}
