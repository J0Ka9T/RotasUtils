package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.core.ZoneArea;

/**
 * Turns a zone area into a short curtain that stands on the terrain along the zone border.
 *
 * <p>Outline prisms and floor-plan boxes span the whole build height, so their wireframe is just a
 * few vertical threads into the sky with nothing tying them to the ground. The curtain instead walks
 * the border, looks up the surface at each step and emits a panel from the ground up
 * {@link #CURTAIN_HEIGHT} blocks, clipped to the area's own vertical range. The result reads like a
 * fence drawn on the world, the way land-claim borders are shown.</p>
 *
 * <p>Pure and allocation-free: the surface lookup and the output are callbacks, so it is unit
 * tested without a client and costs no garbage per frame.</p>
 */
@Environment(EnvType.CLIENT)
public final class ZoneBorderGeometry {
    public static final double CURTAIN_HEIGHT = 3.0;
    /** Returned by a {@link Surface} for a column that is not loaded. */
    public static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int MAX_STEPS_PER_EDGE = 512;
    private static final int RING_SEGMENTS = 96;

    private ZoneBorderGeometry() {
    }

    /** First air block above the ground at a column, or {@link #UNKNOWN}. */
    @FunctionalInterface
    public interface Surface {
        int top(int x, int z);
    }

    /** One curtain panel between two border points, each with its own bottom and top height. */
    @FunctionalInterface
    public interface CurtainSink {
        void panel(double ax, double az, double bottomA, double topA,
                   double bx, double bz, double bottomB, double topB);
    }

    /** Viewer position and how far borders are drawn. */
    public record View(double x, double z, double maxDistance) {
    }

    /**
     * True when the area covers the whole build height, so its wireframe would only be vertical
     * lines. Spheres never are; they keep their wireframe.
     */
    public static boolean isColumn(ZoneArea area, int minBuildY, int topBuildY) {
        if (area instanceof ZoneArea.Sphere) {
            return false;
        }
        ZoneArea.Bounds bounds = area.bounds();
        return bounds.minY() <= minBuildY && bounds.maxY() >= topBuildY;
    }

    public static void trace(ZoneArea area, Surface surface, View view, CurtainSink sink) {
        if (area instanceof ZoneArea.Box box) {
            double x1 = box.minX();
            double z1 = box.minZ();
            double x2 = box.maxX() + 1.0;
            double z2 = box.maxZ() + 1.0;
            double low = box.minY();
            double high = box.maxY() + 1.0;
            edge(x1, z1, x2, z1, low, high, surface, view, sink);
            edge(x2, z1, x2, z2, low, high, surface, view, sink);
            edge(x2, z2, x1, z2, low, high, surface, view, sink);
            edge(x1, z2, x1, z1, low, high, surface, view, sink);
        } else if (area instanceof ZoneArea.Polygon polygon) {
            double low = polygon.minY();
            double high = polygon.maxY() + 1.0;
            int size = polygon.points().size();
            for (int i = 0; i < size; i++) {
                ZoneArea.Polygon.Point a = polygon.points().get(i);
                ZoneArea.Polygon.Point b = polygon.points().get((i + 1) % size);
                edge(a.x(), a.z(), b.x(), b.z(), low, high, surface, view, sink);
            }
        } else if (area instanceof ZoneArea.Sphere sphere) {
            ring(sphere, surface, view, sink);
        }
    }

    private static void edge(double ax, double az, double bx, double bz, double low, double high,
                             Surface surface, View view, CurtainSink sink) {
        double length = Math.hypot(bx - ax, bz - az);
        if (length == 0 || segmentDistance(view.x(), view.z(), ax, az, bx, bz) > view.maxDistance()) {
            return;
        }
        int steps = (int) Math.min(MAX_STEPS_PER_EDGE, Math.max(1, Math.ceil(length)));
        double previousX = ax;
        double previousZ = az;
        int previousGround = ground(surface, ax, az);
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            double x = ax + (bx - ax) * t;
            double z = az + (bz - az) * t;
            int groundHere = ground(surface, x, z);
            if (previousGround != UNKNOWN && groundHere != UNKNOWN
                    && Math.hypot((previousX + x) / 2 - view.x(), (previousZ + z) / 2 - view.z()) <= view.maxDistance()) {
                emit(sink, previousX, previousZ, previousGround, low, high, x, z, groundHere, low, high);
            }
            previousX = x;
            previousZ = z;
            previousGround = groundHere;
        }
    }

    /** Where a sphere meets the ground: each ring point is refined to the radius at its surface height. */
    private static void ring(ZoneArea.Sphere sphere, Surface surface, View view, CurtainSink sink) {
        double radius = sphere.radius();
        if (Math.hypot(sphere.x() - view.x(), sphere.z() - view.z()) - radius > view.maxDistance()) {
            return;
        }
        double firstX = 0, firstZ = 0, firstGround = 0, firstHigh = 0;
        double previousX = 0, previousZ = 0, previousGround = 0, previousHigh = 0;
        boolean firstValid = false;
        boolean previousValid = false;
        for (int step = 0; step < RING_SEGMENTS; step++) {
            double angle = Math.PI * 2.0 * step / RING_SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double horizontal = radius;
            int groundHere = UNKNOWN;
            boolean valid = false;
            // Two refinements: the surface height changes the horizontal radius, which moves the
            // sample to a column whose height is close enough for a border drawn on terrain.
            for (int pass = 0; pass < 2; pass++) {
                groundHere = ground(surface, sphere.x() + horizontal * cos, sphere.z() + horizontal * sin);
                if (groundHere == UNKNOWN) {
                    valid = false;
                    break;
                }
                double dy = groundHere - sphere.y();
                if (Math.abs(dy) >= radius) {
                    valid = false;
                    break;
                }
                horizontal = Math.sqrt(radius * radius - dy * dy);
                valid = true;
            }
            double x = sphere.x() + horizontal * cos;
            double z = sphere.z() + horizontal * sin;
            // The ring marks where the sphere meets the ground. Its rim has no height inside the
            // sphere, so the curtain is not clipped to the sphere surface or it would vanish there.
            double high = Double.MAX_VALUE;
            if (valid && previousValid
                    && Math.hypot((previousX + x) / 2 - view.x(), (previousZ + z) / 2 - view.z()) <= view.maxDistance()) {
                emit(sink, previousX, previousZ, previousGround, sphere.y() - radius, previousHigh,
                        x, z, groundHere, sphere.y() - radius, high);
            }
            if (step == 0) {
                firstValid = valid;
                firstX = x;
                firstZ = z;
                firstGround = groundHere;
                firstHigh = high;
            }
            previousValid = valid;
            previousX = x;
            previousZ = z;
            previousGround = groundHere;
            previousHigh = high;
        }
        if (previousValid && firstValid
                && Math.hypot((previousX + firstX) / 2 - view.x(), (previousZ + firstZ) / 2 - view.z()) <= view.maxDistance()) {
            emit(sink, previousX, previousZ, previousGround, sphere.y() - radius, previousHigh,
                    firstX, firstZ, firstGround, sphere.y() - radius, firstHigh);
        }
    }

    private static void emit(CurtainSink sink, double ax, double az, double groundA, double lowA, double highA,
                             double bx, double bz, double groundB, double lowB, double highB) {
        double bottomA = Math.max(groundA, lowA);
        double bottomB = Math.max(groundB, lowB);
        double topA = Math.min(groundA + CURTAIN_HEIGHT, highA);
        double topB = Math.min(groundB + CURTAIN_HEIGHT, highB);
        if (topA <= bottomA && topB <= bottomB) {
            return;
        }
        sink.panel(ax, az, bottomA, Math.max(bottomA, topA), bx, bz, bottomB, Math.max(bottomB, topB));
    }

    /**
     * Surface under a border point: the highest of the four columns touching it, so a border on a
     * block edge sits on the taller side instead of sinking into a cliff face.
     */
    static int ground(Surface surface, double x, double z) {
        int best = UNKNOWN;
        int lowX = (int) Math.floor(x - 0.5);
        int lowZ = (int) Math.floor(z - 0.5);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int height = surface.top(lowX + dx, lowZ + dz);
                if (height != UNKNOWN && height > best) {
                    best = height;
                }
            }
        }
        return best;
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double lengthSquared = vx * vx + vz * vz;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / lengthSquared));
        return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
    }
}
