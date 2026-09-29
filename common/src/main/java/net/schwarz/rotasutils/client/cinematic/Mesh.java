package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A batch of coloured triangles in camera-relative space, drawn in one call: the small toolkit the Red
 * effects are built from (deformable spheres with per-vertex colour, camera-facing ribbons, glows,
 * billboards, ground fans and oriented boxes). One vertex format and one draw per pass keeps a whole
 * cast down to a handful of draw calls, however many layers it has.
 */
@Environment(EnvType.CLIENT)
final class Mesh {
    /** What a sphere looks like: how far out each direction reaches, and what colour it is there. */
    interface Surface {
        default double radius(double nx, double ny, double nz) {
            return 1.0;
        }

        /** {@code fresnel} is 0 facing the camera and 1 at the silhouette; write r, g, b, a into {@code out}. */
        void color(double nx, double ny, double nz, double fresnel, float[] out);
    }

    private final Matrix4f matrix;
    final Vec3 camera;
    final Vector3f left;
    final Vector3f up;
    private BufferBuilder buffer;

    Mesh(Matrix4f matrix, Vec3 camera, Vector3f left, Vector3f up) {
        this.matrix = matrix;
        this.camera = camera;
        this.left = left;
        this.up = up;
    }

    void begin() {
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    void draw() {
        BufferUploader.drawWithShader(buffer.end());
    }

    void v(double x, double y, double z, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
                .color(r, g, b, Math.max(0f, Math.min(1f, a))).endVertex();
    }

    private void v(double x, double y, double z, float[] c) {
        v(x, y, z, c[0], c[1], c[2], c[3]);
    }

    // Spheres ----------------------------------------------------------------------------------------

    /** A lat/lon sphere at (cx, cy, cz) whose radius and colour come from {@code surface}. */
    void sphere(double cx, double cy, double cz, double radius, int lat, int lon, Surface surface) {
        int cols = lon + 1;
        double[] px = new double[(lat + 1) * cols], py = new double[(lat + 1) * cols], pz = new double[(lat + 1) * cols];
        float[][] col = new float[(lat + 1) * cols][4];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            double sy = Math.cos(th), sr = Math.sin(th);
            for (int j = 0; j <= lon; j++) {
                double ph = Math.PI * 2 * j / lon;
                double nx = sr * Math.cos(ph), nz = sr * Math.sin(ph);
                int k = i * cols + j;
                double r = radius * surface.radius(nx, sy, nz);
                px[k] = cx + nx * r;
                py[k] = cy + sy * r;
                pz[k] = cz + nz * r;
                double vx = camera.x - px[k], vy = camera.y - py[k], vz = camera.z - pz[k];
                double vl = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-9;
                double facing = Math.abs((nx * vx + sy * vy + nz * vz) / vl);
                surface.color(nx, sy, nz, 1 - facing, col[k]);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int a = i * cols + j, b = a + 1, c = a + cols, d = c + 1;
                v(px[a], py[a], pz[a], col[a]);
                v(px[c], py[c], pz[c], col[c]);
                v(px[b], py[b], pz[b], col[b]);
                v(px[b], py[b], pz[b], col[b]);
                v(px[c], py[c], pz[c], col[c]);
                v(px[d], py[d], pz[d], col[d]);
            }
        }
    }

    // Lines and lights -------------------------------------------------------------------------------

    /** A ribbon from a to b that always faces the camera, tapering from width {@code w0} to {@code w1}. */
    void ribbon(Vec3 a, Vec3 b, double w0, double w1, float[] c0, float[] c1) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double mx = (a.x + b.x) * 0.5 - camera.x, my = (a.y + b.y) * 0.5 - camera.y, mz = (a.z + b.z) * 0.5 - camera.z;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0e-9) {
            return;
        }
        sx /= len;
        sy /= len;
        sz /= len;
        // Solid along the middle line, fading to nothing at both edges.
        float[] e0 = {c0[0], c0[1], c0[2], 0f}, e1 = {c1[0], c1[1], c1[2], 0f};
        double ax = a.x - sx * w0, ay = a.y - sy * w0, az = a.z - sz * w0;
        double ax2 = a.x + sx * w0, ay2 = a.y + sy * w0, az2 = a.z + sz * w0;
        double bx = b.x - sx * w1, by = b.y - sy * w1, bz = b.z - sz * w1;
        double bx2 = b.x + sx * w1, by2 = b.y + sy * w1, bz2 = b.z + sz * w1;
        v(ax, ay, az, e0);
        v(a.x, a.y, a.z, c0);
        v(b.x, b.y, b.z, c1);
        v(ax, ay, az, e0);
        v(b.x, b.y, b.z, c1);
        v(bx, by, bz, e1);
        v(a.x, a.y, a.z, c0);
        v(ax2, ay2, az2, e0);
        v(bx2, by2, bz2, e1);
        v(a.x, a.y, a.z, c0);
        v(bx2, by2, bz2, e1);
        v(b.x, b.y, b.z, c1);
    }

    /** Camera-facing quad of half-size {@code size}, rolled by {@code roll} radians. */
    void billboard(Vec3 c, double size, float[] rgba, double roll) {
        double cr = Math.cos(roll) * size, sr = Math.sin(roll) * size;
        double lx = left.x() * cr + up.x() * sr, ly = left.y() * cr + up.y() * sr, lz = left.z() * cr + up.z() * sr;
        double ux = -left.x() * sr + up.x() * cr, uy = -left.y() * sr + up.y() * cr, uz = -left.z() * sr + up.z() * cr;
        v(c.x + lx + ux, c.y + ly + uy, c.z + lz + uz, rgba);
        v(c.x - lx + ux, c.y - ly + uy, c.z - lz + uz, rgba);
        v(c.x - lx - ux, c.y - ly - uy, c.z - lz - uz, rgba);
        v(c.x + lx + ux, c.y + ly + uy, c.z + lz + uz, rgba);
        v(c.x - lx - ux, c.y - ly - uy, c.z - lz - uz, rgba);
        v(c.x + lx - ux, c.y + ly - uy, c.z + lz - uz, rgba);
    }

    /** A soft radial glow facing the camera: {@code rgba} at the centre, nothing at the rim. */
    void glow(Vec3 c, double radius, float[] rgba) {
        if (rgba[3] <= 0.004f || radius <= 0.001) {
            return;
        }
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            v(c.x, c.y, c.z, rgba);
            v(c.x + (left.x() * Math.cos(a0) + up.x() * Math.sin(a0)) * radius, c.y + (left.y() * Math.cos(a0) + up.y() * Math.sin(a0)) * radius,
                    c.z + (left.z() * Math.cos(a0) + up.z() * Math.sin(a0)) * radius, rgba[0], rgba[1], rgba[2], 0f);
            v(c.x + (left.x() * Math.cos(a1) + up.x() * Math.sin(a1)) * radius, c.y + (left.y() * Math.cos(a1) + up.y() * Math.sin(a1)) * radius,
                    c.z + (left.z() * Math.cos(a1) + up.z() * Math.sin(a1)) * radius, rgba[0], rgba[1], rgba[2], 0f);
        }
    }

    /** A flat radial fan lying on the ground plane. */
    void groundFan(double cx, double y, double cz, double radius, float[] rgba) {
        if (rgba[3] <= 0.004f) {
            return;
        }
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            v(cx, y, cz, rgba);
            v(cx + Math.cos(a0) * radius, y, cz + Math.sin(a0) * radius, rgba[0], rgba[1], rgba[2], 0f);
            v(cx + Math.cos(a1) * radius, y, cz + Math.sin(a1) * radius, rgba[0], rgba[1], rgba[2], 0f);
        }
    }

    /** A flat ring on the ground plane, {@code width} across, soft at both edges. */
    void groundRing(double cx, double y, double cz, double radius, double width, float[] rgba) {
        if (rgba[3] <= 0.004f || radius <= 0.01) {
            return;
        }
        int n = 56;
        double in = Math.max(0, radius - width), out = radius + width;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            float[] edge = {rgba[0], rgba[1], rgba[2], 0f};
            v(cx + c0 * in, y, cz + s0 * in, edge);
            v(cx + c0 * radius, y, cz + s0 * radius, rgba);
            v(cx + c1 * radius, y, cz + s1 * radius, rgba);
            v(cx + c0 * in, y, cz + s0 * in, edge);
            v(cx + c1 * radius, y, cz + s1 * radius, rgba);
            v(cx + c1 * in, y, cz + s1 * in, edge);
            v(cx + c0 * radius, y, cz + s0 * radius, rgba);
            v(cx + c0 * out, y, cz + s0 * out, edge);
            v(cx + c1 * out, y, cz + s1 * out, edge);
            v(cx + c0 * radius, y, cz + s0 * radius, rgba);
            v(cx + c1 * out, y, cz + s1 * out, edge);
            v(cx + c1 * radius, y, cz + s1 * radius, rgba);
        }
    }

    // Tubes and ground shapes -------------------------------------------------------------------------

    /** Colour of a tube at {@code u} along its length (0..1) and {@code v} round it (0..1). */
    interface TubeColor {
        void color(double u, double v, float[] out);
    }

    /**
     * A flared tube from {@code a} along {@code dir}: radius {@code r0} at the start growing to {@code r1} at the end
     * (shaped by {@code bell}), coloured per vertex. The body of a directional pulse.
     */
    void tube(Vec3 a, Vec3 dir, double length, double r0, double r1, double bell, int rings, int sides, TubeColor fn) {
        if (length <= 0.01) {
            return;
        }
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 bu = dir.cross(ref).normalize();
        Vec3 bw = dir.cross(bu).normalize();
        int cols = sides + 1;
        double[] px = new double[(rings + 1) * cols], py = new double[(rings + 1) * cols], pz = new double[(rings + 1) * cols];
        float[][] col = new float[(rings + 1) * cols][4];
        for (int i = 0; i <= rings; i++) {
            double u = (double) i / rings;
            double r = r0 + (r1 - r0) * Math.pow(u, bell);
            for (int j = 0; j <= sides; j++) {
                double phi = Math.PI * 2 * j / sides;
                int k = i * cols + j;
                px[k] = a.x + dir.x * u * length + (bu.x * Math.cos(phi) + bw.x * Math.sin(phi)) * r;
                py[k] = a.y + dir.y * u * length + (bu.y * Math.cos(phi) + bw.y * Math.sin(phi)) * r;
                pz[k] = a.z + dir.z * u * length + (bu.z * Math.cos(phi) + bw.z * Math.sin(phi)) * r;
                fn.color(u, (double) j / sides, col[k]);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sides; j++) {
                int p = i * cols + j, q = p + 1, r = p + cols, s = r + 1;
                v(px[p], py[p], pz[p], col[p]);
                v(px[r], py[r], pz[r], col[r]);
                v(px[q], py[q], pz[q], col[q]);
                v(px[q], py[q], pz[q], col[q]);
                v(px[r], py[r], pz[r], col[r]);
                v(px[s], py[s], pz[s], col[s]);
            }
        }
    }

    /** A wedge lying on the ground from a point, opening along {@code angle} (radians in x/z), fading toward its far edge. */
    void groundWedge(double cx, double y, double cz, double angle, double length, double halfAngle, float[] rgba) {
        if (rgba[3] <= 0.004f || length <= 0.05) {
            return;
        }
        int n = 14;
        float[] far = {rgba[0], rgba[1], rgba[2], 0f};
        for (int i = 0; i < n; i++) {
            double a0 = angle - halfAngle + 2 * halfAngle * i / n, a1 = angle - halfAngle + 2 * halfAngle * (i + 1) / n;
            v(cx, y, cz, rgba);
            v(cx + Math.cos(a0) * length, y, cz + Math.sin(a0) * length, far);
            v(cx + Math.cos(a1) * length, y, cz + Math.sin(a1) * length, far);
        }
    }

    /** A flat elliptical ring on the ground, long axis {@code ra} along {@code angle} and short axis {@code rb} across it. */
    void groundEllipseRing(double cx, double y, double cz, double angle, double ra, double rb, double width, float[] rgba) {
        if (rgba[3] <= 0.004f || ra <= 0.05) {
            return;
        }
        double ca = Math.cos(angle), sa = Math.sin(angle);
        double grow = width / Math.max(ra, rb);
        int n = 56;
        float[] edge = {rgba[0], rgba[1], rgba[2], 0f};
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            double[][] ring = new double[6][];
            double[] scales = {1 - grow, 1, 1 + grow};
            for (int k = 0; k < 3; k++) {
                ring[k] = ellipsePoint(cx, cz, ca, sa, ra * scales[k], rb * scales[k], t0);
                ring[k + 3] = ellipsePoint(cx, cz, ca, sa, ra * scales[k], rb * scales[k], t1);
            }
            // inner-to-middle and middle-to-outer bands, soft at the outer edges
            quadFlat(ring[0], ring[1], ring[4], ring[3], y, edge, rgba, rgba, edge);
            quadFlat(ring[1], ring[2], ring[5], ring[4], y, rgba, edge, edge, rgba);
        }
    }

    private static double[] ellipsePoint(double cx, double cz, double ca, double sa, double ra, double rb, double t) {
        double x = Math.cos(t) * ra, z = Math.sin(t) * rb;
        return new double[]{cx + x * ca - z * sa, cz + x * sa + z * ca};
    }

    private void quadFlat(double[] a, double[] b, double[] c2, double[] d, double y, float[] ca, float[] cb, float[] cc, float[] cd) {
        v(a[0], y, a[1], ca);
        v(b[0], y, b[1], cb);
        v(c2[0], y, c2[1], cc);
        v(a[0], y, a[1], ca);
        v(c2[0], y, c2[1], cc);
        v(d[0], y, d[1], cd);
    }

    // Boxes ------------------------------------------------------------------------------------------

    /**
     * A box from {@code p0} to {@code p1}, {@code hw} half-wide along {@code side} and {@code ht} half-thick along
     * {@code norm}, lit from {@code light} with {@code base} as the lit colour.
     */
    void box(Vec3 p0, Vec3 p1, Vec3 side, Vec3 norm, double hw, double ht, float[] base, Vec3 light) {
        Vec3 s = side.scale(hw), n = norm.scale(ht);
        Vec3[] a = {p0.subtract(s).subtract(n), p0.add(s).subtract(n), p0.add(s).add(n), p0.subtract(s).add(n)};
        Vec3[] b = {p1.subtract(s).subtract(n), p1.add(s).subtract(n), p1.add(s).add(n), p1.subtract(s).add(n)};
        Vec3 axis = p1.subtract(p0).normalize();
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            Vec3 normal = i == 0 ? norm.scale(-1) : i == 1 ? side : i == 2 ? norm : side.scale(-1);
            face(a[i], a[j], b[j], b[i], shade(base, normal, light));
        }
        face(a[0], a[1], a[2], a[3], shade(base, axis.scale(-1), light));
        face(b[3], b[2], b[1], b[0], shade(base, axis, light));
    }

    private static float[] shade(float[] base, Vec3 normal, Vec3 light) {
        double k = 0.5 + 0.5 * Math.max(0, normal.dot(light));
        return new float[]{(float) (base[0] * k), (float) (base[1] * k), (float) (base[2] * k), base[3]};
    }

    private void face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col) {
        v(a.x, a.y, a.z, col);
        v(b.x, b.y, b.z, col);
        v(c.x, c.y, c.z, col);
        v(a.x, a.y, a.z, col);
        v(c.x, c.y, c.z, col);
        v(d.x, d.y, d.z, col);
    }
}
