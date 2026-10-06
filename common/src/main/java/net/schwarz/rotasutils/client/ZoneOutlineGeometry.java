package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.core.ZoneArea;

@Environment(EnvType.CLIENT)
public final class ZoneOutlineGeometry {
    private static final int CIRCLE_SEGMENTS = 48;
    private static final int MERIDIAN_COUNT = 4;
    private static final int LATITUDE_COUNT = 5;

    private ZoneOutlineGeometry() {
    }

    public static void trace(ZoneArea area, LineSink lines) {
        if (area instanceof ZoneArea.Sphere sphere) {
            sphere(sphere, lines);
        } else if (area instanceof ZoneArea.Box box) {
            box(box, lines);
        } else if (area instanceof ZoneArea.Polygon polygon) {
            polygon(polygon, lines);
        }
    }

    private static void sphere(ZoneArea.Sphere sphere, LineSink lines) {
        for (int meridian = 0; meridian < MERIDIAN_COUNT; meridian++) {
            double heading = Math.PI * meridian / MERIDIAN_COUNT;
            circle(sphere, lines, heading, true);
        }
        for (int latitude = 1; latitude <= LATITUDE_COUNT; latitude++) {
            double angle = -Math.PI / 2.0 + Math.PI * latitude / (LATITUDE_COUNT + 1.0);
            circle(sphere, lines, angle, false);
        }
    }

    private static void circle(ZoneArea.Sphere sphere, LineSink lines, double fixedAngle, boolean meridian) {
        double previousX = sphere.x();
        double previousY = sphere.y();
        double previousZ = sphere.z();
        for (int step = 0; step <= CIRCLE_SEGMENTS; step++) {
            double angle = Math.PI * 2.0 * step / CIRCLE_SEGMENTS;
            double x;
            double y;
            double z;
            if (meridian) {
                double horizontal = sphere.radius() * Math.cos(angle);
                x = sphere.x() + horizontal * Math.cos(fixedAngle);
                y = sphere.y() + sphere.radius() * Math.sin(angle);
                z = sphere.z() + horizontal * Math.sin(fixedAngle);
            } else {
                double ringRadius = sphere.radius() * Math.cos(fixedAngle);
                x = sphere.x() + ringRadius * Math.cos(angle);
                y = sphere.y() + sphere.radius() * Math.sin(fixedAngle);
                z = sphere.z() + ringRadius * Math.sin(angle);
            }
            if (step > 0) {
                lines.line(previousX, previousY, previousZ, x, y, z);
            }
            previousX = x;
            previousY = y;
            previousZ = z;
        }
    }

    private static void box(ZoneArea.Box box, LineSink lines) {
        double x1 = box.minX();
        double y1 = box.minY();
        double z1 = box.minZ();
        double x2 = box.maxX() + 1.0;
        double y2 = box.maxY() + 1.0;
        double z2 = box.maxZ() + 1.0;
        rectangle(lines, x1, y1, z1, x2, z2);
        rectangle(lines, x1, y2, z1, x2, z2);
        lines.line(x1, y1, z1, x1, y2, z1);
        lines.line(x2, y1, z1, x2, y2, z1);
        lines.line(x2, y1, z2, x2, y2, z2);
        lines.line(x1, y1, z2, x1, y2, z2);
    }

    private static void rectangle(LineSink lines, double x1, double y, double z1, double x2, double z2) {
        lines.line(x1, y, z1, x2, y, z1);
        lines.line(x2, y, z1, x2, y, z2);
        lines.line(x2, y, z2, x1, y, z2);
        lines.line(x1, y, z2, x1, y, z1);
    }

    private static void polygon(ZoneArea.Polygon polygon, LineSink lines) {
        double bottom = polygon.minY();
        double top = polygon.maxY() + 1.0;
        for (int index = 0; index < polygon.points().size(); index++) {
            ZoneArea.Polygon.Point point = polygon.points().get(index);
            ZoneArea.Polygon.Point next = polygon.points().get((index + 1) % polygon.points().size());
            lines.line(point.x(), bottom, point.z(), next.x(), bottom, next.z());
            lines.line(point.x(), top, point.z(), next.x(), top, next.z());
            lines.line(point.x(), bottom, point.z(), point.x(), top, point.z());
        }
    }

    @FunctionalInterface
    public interface LineSink {
        void line(double ax, double ay, double az, double bx, double by, double bz);
    }
}
