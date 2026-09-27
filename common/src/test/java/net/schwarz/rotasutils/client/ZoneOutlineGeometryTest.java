package net.schwarz.rotasutils.client;

import net.schwarz.rotasutils.core.ZoneArea;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneOutlineGeometryTest {
    private static final double EPSILON = 0.0001;

    @Test
    void sphereOutlineVerticesStayOnTheActualRadius() {
        ZoneArea.Sphere sphere = new ZoneArea.Sphere(10, 20, -5, 8);
        List<Line> lines = trace(sphere);

        assertTrue(lines.size() >= 144, "sphere should have enough arcs to read as round");
        for (Line line : lines) {
            assertEquals(8.0, distance(line.ax, line.ay, line.az, 10, 20, -5), EPSILON);
            assertEquals(8.0, distance(line.bx, line.by, line.bz, 10, 20, -5), EPSILON);
        }
    }

    @Test
    void boxOutlineUsesInclusiveBlockOuterFaces() {
        List<Line> lines = trace(new ZoneArea.Box(2, 4, 6, 5, 8, 10));

        assertEquals(12, lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.matches(2, 4, 6, 6, 4, 6)));
        assertTrue(lines.stream().anyMatch(line -> line.matches(6, 9, 11, 2, 9, 11)));
    }

    @Test
    void polygonOutlineFollowsItsPerimeterInsteadOfItsBounds() {
        ZoneArea.Polygon triangle = new ZoneArea.Polygon(List.of(
                new ZoneArea.Polygon.Point(0, 0),
                new ZoneArea.Polygon.Point(6, 0),
                new ZoneArea.Polygon.Point(2, 5)), 60, 63);
        List<Line> lines = trace(triangle);

        assertEquals(9, lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.matches(6, 60, 0, 2, 60, 5)));
        assertTrue(lines.stream().anyMatch(line -> line.matches(2, 64, 5, 0, 64, 0)));
        assertFalse(lines.stream().anyMatch(line -> line.matches(6, 60, 5, 0, 60, 5)),
                "the bounding-box edge is outside the triangle");
    }

    private static List<Line> trace(ZoneArea area) {
        List<Line> lines = new ArrayList<>();
        ZoneOutlineGeometry.trace(area, (ax, ay, az, bx, by, bz) ->
                lines.add(new Line(ax, ay, az, bx, by, bz)));
        return lines;
    }

    private static double distance(double x, double y, double z, double cx, double cy, double cz) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record Line(double ax, double ay, double az, double bx, double by, double bz) {
        boolean matches(double x1, double y1, double z1, double x2, double y2, double z2) {
            return (point(ax, ay, az, x1, y1, z1) && point(bx, by, bz, x2, y2, z2))
                    || (point(ax, ay, az, x2, y2, z2) && point(bx, by, bz, x1, y1, z1));
        }

        private static boolean point(double ax, double ay, double az, double bx, double by, double bz) {
            return Math.abs(ax - bx) < EPSILON && Math.abs(ay - by) < EPSILON && Math.abs(az - bz) < EPSILON;
        }
    }
}
