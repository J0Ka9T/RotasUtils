package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One geometric area inside an admin zone.
 *
 * <p>A zone is the union of its areas, so an operator can trace a town with a box, drop a radius
 * over a boss arena and add a polygon for a winding valley under a single level band. An empty area
 * list on {@link ZoneDef} means "the whole dimension", which is how a zone with no shape at all is
 * expressed.</p>
 *
 * <p>All containers are validated on construction: coordinates within the usual world bounds,
 * radius/polygon sizes bounded, and the polygon needs at least three points to enclose anything.
 * The records are immutable and serialise through plain NBT, the same way every other RotasUtils
 * definition does.</p>
 */
public sealed interface ZoneArea {
    /** Axis-aligned world bounds shared by every shape. */
    int WORLD_LIMIT = 30_000_000;
    /** A polygon needs a triangle at minimum and is capped so a wand cannot grow world data forever. */
    int MAX_POLYGON_POINTS = 128;
    int MAX_RADIUS = 1_000_000;

    /** True when the world position falls inside this area. */
    boolean contains(double x, double y, double z);

    /**
     * Straight-line distance in blocks from the position to this area's surface; 0 when inside.
     * Zone transition rings use it to blend the wilderness level toward the zone band.
     */
    double distance(double x, double y, double z);

    CompoundTag save();

    /** Short human-readable description for admin lists, e.g. "Sphere r32 at 10 64 10". */
    String label();

    /** Axis-aligned envelope, used to draw the zone as a box in the world. */
    Bounds bounds();

    /** Inclusive block envelope of an area. */
    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    /** A sphere around a point. */
    record Sphere(int x, int y, int z, int radius) implements ZoneArea {
        public Sphere {
            coord(x, "sphere x"); coord(y, "sphere y"); coord(z, "sphere z");
            if (radius < 1 || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("Zone radius outside 1.." + MAX_RADIUS);
            }
        }

        @Override
        public boolean contains(double px, double py, double pz) {
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
        }

        @Override
        public double distance(double px, double py, double pz) {
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - radius);
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("shape", "sphere");
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("radius", radius);
            return tag;
        }

        @Override
        public String label() {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.zone.area.sphere", radius, x, y, z);
        }

        @Override
        public Bounds bounds() {
            return new Bounds(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        }
    }

    /** A box between two opposite corners. */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements ZoneArea {
        public Box {
            coord(minX, "box min x"); coord(minY, "box min y"); coord(minZ, "box min z");
            coord(maxX, "box max x"); coord(maxY, "box max y"); coord(maxZ, "box max z");
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Zone box corners are inverted");
            }
        }

        @Override
        public boolean contains(double px, double py, double pz) {
            return px >= minX && px <= maxX + 1 && py >= minY && py <= maxY + 1 && pz >= minZ && pz <= maxZ + 1;
        }

        @Override
        public double distance(double px, double py, double pz) {
            double dx = outside(px, minX, maxX + 1);
            double dy = outside(py, minY, maxY + 1);
            double dz = outside(pz, minZ, maxZ + 1);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("shape", "box");
            tag.putInt("min_x", minX); tag.putInt("min_y", minY); tag.putInt("min_z", minZ);
            tag.putInt("max_x", maxX); tag.putInt("max_y", maxY); tag.putInt("max_z", maxZ);
            return tag;
        }

        @Override
        public String label() {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.zone.area.box", minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public Bounds bounds() {
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /**
     * A vertical prism over a horizontal polygon. The polygon is stored in the X/Z plane and
     * clamped between {@code minY} and {@code maxY} (inclusive), so a wand can trace any outline.
     */
    record Polygon(List<Point> points, int minY, int maxY) implements ZoneArea {
        public record Point(int x, int z) {
            public Point {
                coord(x, "polygon point x"); coord(z, "polygon point z");
            }
        }

        public Polygon {
            points = List.copyOf(points);
            if (points.size() < 3 || points.size() > MAX_POLYGON_POINTS) {
                throw new IllegalArgumentException("Zone polygon needs 3.." + MAX_POLYGON_POINTS + " points");
            }
            coord(minY, "polygon min y"); coord(maxY, "polygon max y");
            if (minY > maxY) {
                throw new IllegalArgumentException("Zone polygon y range is inverted");
            }
            validateOutline(points);
        }

        private static void validateOutline(List<Point> points) {
            if (new java.util.HashSet<>(points).size() != points.size()) throw new IllegalArgumentException("Zone polygon has duplicate points");
            long twiceArea = 0;
            for (int i=0;i<points.size();i++) { Point a=points.get(i), b=points.get((i+1)%points.size()); twiceArea += (long)a.x()*b.z()-(long)b.x()*a.z(); }
            if (twiceArea == 0) throw new IllegalArgumentException("Zone polygon is degenerate");
            for (int i=0;i<points.size();i++) for (int j=i+1;j<points.size();j++) {
                if (i==j || (i+1)%points.size()==j || i==(j+1)%points.size()) continue;
                Point a=points.get(i), b=points.get((i+1)%points.size()), c=points.get(j), d=points.get((j+1)%points.size());
                if (intersects(a,b,c,d)) throw new IllegalArgumentException("Zone polygon self-intersects");
            }
        }
        private static boolean intersects(Point a, Point b, Point c, Point d) {
            long abC=cross(a,b,c), abD=cross(a,b,d), cdA=cross(c,d,a), cdB=cross(c,d,b);
            return ((abC>0&&abD<0)||(abC<0&&abD>0)) && ((cdA>0&&cdB<0)||(cdA<0&&cdB>0));
        }
        private static long cross(Point a, Point b, Point c) { return (long)(b.x()-a.x())*(c.z()-a.z())-(long)(b.z()-a.z())*(c.x()-a.x()); }

        @Override
        public boolean contains(double px, double py, double pz) {
            if (py < minY || py > maxY + 1) {
                return false;
            }
            return insideOutline(px, pz);
        }

        @Override
        public double distance(double px, double py, double pz) {
            double horizontal = 0.0;
            if (!insideOutline(px, pz)) {
                horizontal = Double.MAX_VALUE;
                for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
                    horizontal = Math.min(horizontal, segmentDistance(px, pz,
                            points.get(j).x(), points.get(j).z(), points.get(i).x(), points.get(i).z()));
                }
            }
            double vertical = outside(py, minY, maxY + 1);
            return Math.sqrt(horizontal * horizontal + vertical * vertical);
        }

        private boolean insideOutline(double px, double pz) {
            boolean inside = false;
            for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
                double xi = points.get(i).x(), zi = points.get(i).z();
                double xj = points.get(j).x(), zj = points.get(j).z();
                boolean crosses = (zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi;
                if (crosses) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
            double vx = bx - ax;
            double vz = bz - az;
            double lengthSquared = vx * vx + vz * vz;
            double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / lengthSquared));
            double dx = px - (ax + t * vx);
            double dz = pz - (az + t * vz);
            return Math.sqrt(dx * dx + dz * dz);
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("shape", "polygon");
            tag.putInt("min_y", minY);
            tag.putInt("max_y", maxY);
            ListTag list = new ListTag();
            for (Point point : points) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("x", point.x());
                entry.putInt("z", point.z());
                list.add(entry);
            }
            tag.put("points", list);
            return tag;
        }

        @Override
        public String label() {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.zone.area.polygon", points.size(), minY, maxY);
        }

        @Override
        public Bounds bounds() {
            int lowX = Integer.MAX_VALUE;
            int lowZ = Integer.MAX_VALUE;
            int highX = Integer.MIN_VALUE;
            int highZ = Integer.MIN_VALUE;
            for (Point point : points) {
                lowX = Math.min(lowX, point.x());
                lowZ = Math.min(lowZ, point.z());
                highX = Math.max(highX, point.x());
                highZ = Math.max(highZ, point.z());
            }
            return new Bounds(lowX, minY, lowZ, highX, maxY, highZ);
        }
    }

    static ZoneArea load(CompoundTag tag) {
        String shape = tag.getString("shape");
        return switch (shape) {
            case "sphere" -> new Sphere(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag.getInt("radius"));
            case "box" -> new Box(tag.getInt("min_x"), tag.getInt("min_y"), tag.getInt("min_z"),
                    tag.getInt("max_x"), tag.getInt("max_y"), tag.getInt("max_z"));
            case "polygon" -> new Polygon(points(tag), tag.getInt("min_y"), tag.getInt("max_y"));
            default -> throw new IllegalArgumentException("Unknown zone shape: " + shape);
        };
    }

    private static List<Polygon.Point> points(CompoundTag tag) {
        ListTag list = tag.getList("points", Tag.TAG_COMPOUND);
        List<Polygon.Point> points = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            points.add(new Polygon.Point(entry.getInt("x"), entry.getInt("z")));
        }
        return points;
    }

    /** How far a coordinate lies outside [low, high]; 0 when within. */
    private static double outside(double value, double low, double high) {
        return value < low ? low - value : value > high ? value - high : 0.0;
    }

    private static void coord(int value, String label) {
        if (value < -WORLD_LIMIT || value > WORLD_LIMIT) {
            throw new IllegalArgumentException(label + " outside world bounds");
        }
    }
}
