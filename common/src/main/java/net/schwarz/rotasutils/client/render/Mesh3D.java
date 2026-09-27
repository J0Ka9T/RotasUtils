package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Real 3D geometry for VFX: tubes, cones, spheres and partial spheres drawn as <b>solid, lit meshes</b>
 * (vanilla entity shading from their normals, depth-writing, back faces culled), plus an optional
 * <b>fresnel rim</b> pass (additive, only on camera-facing faces, strongest at the silhouette) that
 * makes dark forms glow at their edges. This is what separates volume from flat billboards.
 *
 * <p>All positions are world space; the owner renderer's origin is subtracted per vertex.</p>
 */
@Environment(EnvType.CLIENT)
public final class Mesh3D {
    private static final ResourceLocation PLAIN = new ResourceLocation("rotasutils", "textures/vfx/plain.png");

    private MultiBufferSource buffers;
    private Matrix4f m;
    private Matrix3f nm;
    private double ox, oy, oz;
    private Vec3 cam;

    void begin(MultiBufferSource buffers, Matrix4f m, Matrix3f nm, double ox, double oy, double oz, Vec3 cam) {
        this.buffers = buffers;
        this.m = m;
        this.nm = nm;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.cam = cam;
    }

    /** Surface style: base colour (lit), rim colour and strength, rim sharpness. */
    public record Style(float r, float g, float b, float rimR, float rimG, float rimB, float rim, float power) {
        public Style withRim(float strength) {
            return new Style(r, g, b, rimR, rimG, rimB, strength, power);
        }
    }

    // ---- Tube -----------------------------------------------------------------------------------

    /**
     * A smooth tube through {@code pts} with per-point radius (radius 0 = pointed tip). Frames are
     * parallel-transported so the tube never twists. Ends are left open (cap with a sphere if needed).
     */
    public void tube(Vec3[] pts, float[] radius, int sides, Style style) {
        int n = pts.length;
        if (n < 2) return;
        Vec3[] ringU = new Vec3[n];
        Vec3[] ringV = new Vec3[n];
        Vec3 t0 = pts[1].subtract(pts[0]).normalize();
        Vec3 u = anyPerp(t0);
        for (int i = 0; i < n; i++) {
            Vec3 t = i == n - 1 ? pts[i].subtract(pts[i - 1]) : pts[i + 1].subtract(pts[Math.max(0, i - 1)]);
            if (t.lengthSqr() < 1e-10) t = t0;
            t = t.normalize();
            u = u.subtract(t.scale(u.dot(t)));
            if (u.lengthSqr() < 1e-8) u = anyPerp(t);
            u = u.normalize();
            ringU[i] = u;
            ringV[i] = t.cross(u);
        }
        Vec3[][] p = new Vec3[n][sides];
        Vec3[][] nrm = new Vec3[n][sides];
        for (int i = 0; i < n; i++) {
            for (int s = 0; s < sides; s++) {
                float a = s * Mth.TWO_PI / sides;
                Vec3 d = ringU[i].scale(Mth.cos(a)).add(ringV[i].scale(Mth.sin(a)));
                nrm[i][s] = d;
                p[i][s] = pts[i].add(d.scale(radius[i]));
            }
        }
        grid(p, nrm, n, sides, true, style);
    }

    /** Sphere (or ellipsoid with axes a, b, c scaled by radii). */
    public void sphere(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, float ra, float rb, float rc, int lat, int lon, Style style) {
        sphereSection(c, ax, ay, az, ra, rb, rc, 0, Mth.PI, lat, lon, style);
    }

    /**
     * Part of an ellipsoid between polar angles {@code theta0..theta1} measured from {@code +ay}
     * (0 = top pole). Used for eyelids: a shell that closes by moving its edge angle.
     */
    public void sphereSection(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, float ra, float rb, float rc,
                              float theta0, float theta1, int lat, int lon, Style style) {
        Vec3[][] p = new Vec3[lat + 1][lon];
        Vec3[][] nrm = new Vec3[lat + 1][lon];
        for (int i = 0; i <= lat; i++) {
            float th = Mth.lerp(i / (float) lat, theta0, theta1);
            float st = Mth.sin(th), ct = Mth.cos(th);
            for (int j = 0; j < lon; j++) {
                float ph = j * Mth.TWO_PI / lon;
                float x = st * Mth.cos(ph), z = st * Mth.sin(ph), y = ct;
                p[i][j] = c.add(ax.scale(x * ra)).add(ay.scale(y * rb)).add(az.scale(z * rc));
                nrm[i][j] = ax.scale(x / ra).add(ay.scale(y / rb)).add(az.scale(z / rc)).normalize();
            }
        }
        grid(p, nrm, lat + 1, lon, false, style);
    }

    private void grid(Vec3[][] p, Vec3[][] nrm, int rows, int cols, boolean tube, Style s) {
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(PLAIN));
        for (int i = 0; i < rows - 1; i++) {
            for (int j = 0; j < cols; j++) {
                int k = (j + 1) % cols;
                // Order a, b, c, d consistent around the surface; entityCutoutNoCull draws both sides.
                solidVertex(solid, p[i][j], nrm[i][j], s);
                solidVertex(solid, p[i + 1][j], nrm[i + 1][j], s);
                solidVertex(solid, p[i + 1][k], nrm[i + 1][k], s);
                solidVertex(solid, p[i][k], nrm[i][k], s);
            }
        }
        if (s.rim() <= 0.003f) return;
        VertexConsumer rim = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        for (int i = 0; i < rows - 1; i++) {
            for (int j = 0; j < cols; j++) {
                int k = (j + 1) % cols;
                Vec3 mid = p[i][j].add(p[i + 1][k]).scale(0.5);
                Vec3 nMid = nrm[i][j].add(nrm[i + 1][k]);
                if (nMid.dot(cam.subtract(mid)) <= 0) continue; // only camera-facing faces glow / shine
                rimVertex(rim, p[i][j], nrm[i][j], s);
                rimVertex(rim, p[i + 1][j], nrm[i + 1][j], s);
                rimVertex(rim, p[i + 1][k], nrm[i + 1][k], s);
                rimVertex(rim, p[i][k], nrm[i][k], s);
            }
        }
    }

    private void solidVertex(VertexConsumer vc, Vec3 p, Vec3 n, Style s) {
        vc.vertex(m, (float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz))
                .color(s.r(), s.g(), s.b(), 1f).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    private static final Vec3 KEY_LIGHT = new Vec3(0.35, 0.85, 0.4).normalize();

    private void rimVertex(VertexConsumer vc, Vec3 p, Vec3 n, Style s) {
        Vec3 view = cam.subtract(p).normalize();
        float facing = (float) Math.abs(n.dot(view));
        float fres = (float) Math.pow(1 - facing, s.power()) * s.rim();
        Vec3 half = KEY_LIGHT.add(view).normalize();
        float spec = (float) Math.pow(Math.max(0, n.dot(half)), 28) * 0.55f;
        float r = s.rimR() * fres + spec, g = s.rimG() * fres + spec, b = s.rimB() * fres + spec;
        vc.vertex(m, (float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz))
                .color(Math.min(1, r), Math.min(1, g), Math.min(1, b), 1f).endVertex();
    }

    static Vec3 anyPerp(Vec3 t) {
        Vec3 u = t.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = t.cross(new Vec3(1, 0, 0));
        return u.normalize();
    }
}
