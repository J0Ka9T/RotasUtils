package net.schwarz.rotasutils.client;

import net.schwarz.rotasutils.core.ZoneArea;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneBorderGeometryTest {
    private static final double EPSILON = 0.0001;
    private static final ZoneBorderGeometry.View NEAR = new ZoneBorderGeometry.View(0, 0, 512);

    @Test void aFullHeightBoxBecomesACurtainOnTheGroundAlongItsOuterFaces() {
        List<Panel> panels = trace(new ZoneArea.Box(0, -64, 0, 9, 319, 4), (x, z) -> 64, NEAR);
        double length = 0;
        for (Panel panel : panels) {
            assertEquals(64, panel.bottomA, EPSILON);
            assertEquals(64 + ZoneBorderGeometry.CURTAIN_HEIGHT, panel.topA, EPSILON);
            assertTrue(onRectangle(panel.ax, panel.az, 0, 0, 10, 5), "panel point off the box border");
            length += Math.hypot(panel.bx - panel.ax, panel.bz - panel.az);
        }
        assertEquals(30, length, EPSILON, "the curtain walks the whole perimeter");
    }

    @Test void theCurtainFollowsTheTerrainHeight() {
        List<Panel> panels = trace(new ZoneArea.Box(0, -64, 0, 9, 319, 9), (x, z) -> 60 + x, NEAR);
        assertTrue(panels.stream().anyMatch(panel -> panel.bottomA < 62));
        assertTrue(panels.stream().anyMatch(panel -> panel.bottomA > 68));
    }

    @Test void unloadedColumnsAndFarBordersDrawNothing() {
        assertTrue(trace(new ZoneArea.Box(0, -64, 0, 9, 319, 9), (x, z) -> ZoneBorderGeometry.UNKNOWN, NEAR).isEmpty());
        ZoneBorderGeometry.View far = new ZoneBorderGeometry.View(5000, 5000, 64);
        assertTrue(trace(new ZoneArea.Box(0, -64, 0, 9, 319, 9), (x, z) -> 64, far).isEmpty());
    }

    @Test void aCaveBoxBelowTheSurfaceIsClippedAway() {
        assertTrue(trace(new ZoneArea.Box(0, 10, 0, 9, 20, 9), (x, z) -> 64, NEAR).isEmpty());
    }

    @Test void aPolygonCurtainFollowsItsEdgesNotItsBounds() {
        ZoneArea.Polygon triangle = new ZoneArea.Polygon(List.of(new ZoneArea.Polygon.Point(0, 0),
                new ZoneArea.Polygon.Point(12, 0), new ZoneArea.Polygon.Point(0, 12)), -64, 319);
        List<Panel> panels = trace(triangle, (x, z) -> 70, NEAR);
        assertFalse(panels.isEmpty());
        for (Panel panel : panels) {
            boolean onEdge = Math.abs(panel.az) < EPSILON || Math.abs(panel.ax) < EPSILON
                    || Math.abs(panel.ax + panel.az - 12) < EPSILON;
            assertTrue(onEdge, "panel point off the triangle perimeter");
        }
    }

    @Test void aSphereRingSitsWhereTheSphereMeetsTheGround() {
        ZoneArea.Sphere sphere = new ZoneArea.Sphere(0, 64, 0, 10);
        List<Panel> level = trace(sphere, (x, z) -> 64, NEAR);
        assertFalse(level.isEmpty());
        level.forEach(panel -> assertEquals(10, Math.hypot(panel.ax, panel.az), EPSILON));
        List<Panel> raised = trace(sphere, (x, z) -> 70, NEAR);
        raised.forEach(panel -> assertEquals(8, Math.hypot(panel.ax, panel.az), EPSILON));
        assertTrue(trace(sphere, (x, z) -> 90, NEAR).isEmpty(), "ground above the sphere has no ring");
    }

    @Test void onlyFullHeightShapesCountAsColumns() {
        assertTrue(ZoneBorderGeometry.isColumn(new ZoneArea.Box(0, -64, 0, 4, 319, 4), -64, 319));
        assertFalse(ZoneBorderGeometry.isColumn(new ZoneArea.Box(0, 10, 0, 4, 40, 4), -64, 319));
        assertFalse(ZoneBorderGeometry.isColumn(new ZoneArea.Sphere(0, 64, 0, 5000), -64, 319));
    }

    private static boolean onRectangle(double x, double z, double x1, double z1, double x2, double z2) {
        boolean vertical = (Math.abs(x - x1) < EPSILON || Math.abs(x - x2) < EPSILON) && z >= z1 - EPSILON && z <= z2 + EPSILON;
        boolean horizontal = (Math.abs(z - z1) < EPSILON || Math.abs(z - z2) < EPSILON) && x >= x1 - EPSILON && x <= x2 + EPSILON;
        return vertical || horizontal;
    }

    private static List<Panel> trace(ZoneArea area, ZoneBorderGeometry.Surface surface, ZoneBorderGeometry.View view) {
        List<Panel> panels = new ArrayList<>();
        ZoneBorderGeometry.trace(area, surface, view, (ax, az, bottomA, topA, bx, bz, bottomB, topB) ->
                panels.add(new Panel(ax, az, bottomA, topA, bx, bz, bottomB, topB)));
        return panels;
    }

    private record Panel(double ax, double az, double bottomA, double topA,
                         double bx, double bz, double bottomB, double topB) {
    }
}
