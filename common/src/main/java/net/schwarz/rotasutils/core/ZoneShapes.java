package net.schwarz.rotasutils.core;

public final class ZoneShapes {
    private ZoneShapes() {
    }

    public enum Shape {
        SPHERE("Sphere"), BOX("Box"), DIMENSION("Whole dimension");

        private final String label;

        Shape(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Shape next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Shape parse(String value) {
            try {
                return value == null ? SPHERE : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                return SPHERE;
            }
        }
    }

    public static final int[] SPHERE_RADII = {16, 32, 64, 128, 256};
    public static final int[] BOX_SIDES = {32, 64, 128, 256, 512};

    public static int sizeCount(Shape shape) {
        return shape == Shape.SPHERE ? SPHERE_RADII.length : shape == Shape.BOX ? BOX_SIDES.length : 1;
    }

    public static String label(Shape shape, int size) {
        int i = clamp(size, shape);
        return switch (shape) {
            case SPHERE -> "Sphere r" + SPHERE_RADII[i];
            case BOX -> "Box " + BOX_SIDES[i] + " x " + BOX_SIDES[i];
            case DIMENSION -> "Whole dimension";
        };
    }

    public static ZoneArea area(Shape shape, int size, int x, int y, int z, int minY, int maxY) {
        int i = clamp(size, shape);
        return switch (shape) {
            case SPHERE -> new ZoneArea.Sphere(x, y, z, SPHERE_RADII[i]);
            case BOX -> {
                int half = BOX_SIDES[i] / 2;
                yield new ZoneArea.Box(x - half, minY, z - half, x + half - 1, maxY, z + half - 1);
            }
            case DIMENSION -> null;
        };
    }

    private static int clamp(int size, Shape shape) {
        return Math.max(0, Math.min(sizeCount(shape) - 1, size));
    }
}
