package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.ability.AbilityNet;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class PurpleDissolve {
    private PurpleDissolve() {}

    private static final int MAX_GHOSTS = 12;
    private static final int MAX_VERTICES = 32768;
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(262144));
    private static ClientLevel world;
    private static boolean capturing;

    private record Ghost(int entityId, UUID uuid, Vec3 feet, double width, double height,
                         long startTick, long seed, List<Batch> batches) {
        double age(float pt) {
            return (world.getGameTime() - startTick + pt) / 20.0;
        }
    }

    private record Batch(RenderType type, List<Vertex[]> quads) {}

    public static boolean capturing() {
        return capturing;
    }

    public static boolean hides(Entity entity) {
        if (capturing || entity.level() != world || entity.isAlive()) {
            return false;
        }
        for (Ghost ghost : GHOSTS) {
            if (!ghost.batches().isEmpty() && ghost.entityId() == entity.getId() && ghost.uuid().equals(entity.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        GHOSTS.clear();
        world = null;
    }

    public static void tick(Minecraft mc) {
        if (world != mc.level) {
            clear();
            world = mc.level;
        }
        if (world != null && !mc.isPaused()) {
            GHOSTS.removeIf(ghost -> ghost.age(0) > PurpleDissolveProfile.LIFETIME);
        }
    }

    public static void receive(FriendlyByteBuf buf, Consumer<Runnable> queue) {
        int caster = buf.readVarInt(), victim = buf.readVarInt();
        UUID uuid = buf.readUUID();
        Vec3 feet = AbilityNet.readVec(buf);
        float width = buf.readFloat(), height = buf.readFloat();
        long seed = buf.readLong();
        if (caster < 0 || victim < 0 || !Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0 || width > 32 || height > 32) {
            return;
        }
        queue.accept(() -> start(caster, victim, uuid, feet, width, height, seed));
    }

    private static void start(int caster, int victimId, UUID uuid, Vec3 feet, float width, float height, long seed) {
        Minecraft mc = Minecraft.getInstance();
        ClientCast cast = ClientCasts.forCaster(caster);
        if (mc.level == null || mc.player == null || cast == null || !cast.purple() || !cast.released()
                || mc.player.position().distanceToSqr(feet) > 192 * 192) {
            return;
        }
        tick(mc);
        for (Ghost ghost : GHOSTS) {
            if (ghost.uuid().equals(uuid)) {
                return;
            }
        }
        Entity entity = world.getEntity(victimId);
        List<Batch> batches = List.of();
        if (entity instanceof LivingEntity victim && !(victim instanceof Player) && uuid.equals(victim.getUUID())) {
            Capture capture = new Capture();
            int death = victim.deathTime, hurt = victim.hurtTime;
            capturing = true;
            try {
                victim.deathTime = 0;
                victim.hurtTime = 0;
                mc.getEntityRenderDispatcher().getRenderer(victim).render(victim, victim.getYRot(), 0,
                        new PoseStack(), capture, LightTexture.FULL_BRIGHT);
                batches = capture.finish();
            } catch (RuntimeException failure) {
                Rotasutils.LOG.debug("Purple model capture failed for {}", victim.getType(), failure);
            } finally {
                victim.deathTime = death;
                victim.hurtTime = hurt;
                capturing = false;
            }
        }
        if (GHOSTS.size() >= MAX_GHOSTS) {
            GHOSTS.remove(0);
        }
        GHOSTS.add(new Ghost(victimId, uuid, feet, width, height, world.getGameTime(), seed, batches));
        if (victimId == cast.hitEntityId) {
            cast.dissolveFocus = feet.add(0, height * 0.5, 0);
        }
    }

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (world == null || world != mc.level || GHOSTS.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        int drawn = 0;
        try {
            for (Ghost ghost : GHOSTS) {
                double age = ghost.age(partialTick);
                if (age < 0 || age > PurpleDissolveProfile.END || cam.distanceToSqr(ghost.feet()) > 128 * 128 || drawn++ >= 6) {
                    continue;
                }
                pose.pushPose();
                try {
                    pose.translate(ghost.feet().x - cam.x, ghost.feet().y - cam.y, ghost.feet().z - cam.z);
                    Matrix4f matrix = pose.last().pose();
                    for (Batch batch : ghost.batches()) {
                        VertexConsumer out = BUFFERS.getBuffer(batch.type());
                        int index = 0;
                        for (Vertex[] quad : batch.quads()) {
                            double y = (quad[0].y + quad[1].y + quad[2].y + quad[3].y) * 0.25 / ghost.height();
                            double noise = RedVfxRenderer.rnd(ghost.seed(), index++, 91);
                            if (!PurpleDissolveProfile.visible(Curves.clamp01(y), noise, age)) {
                                continue;
                            }
                            double edge = Math.max(0, 1 - Math.abs(0.8 * y + 0.2 * noise - (1 - PurpleDissolveProfile.progress(age))) / 0.09);
                            for (Vertex vertex : quad) {
                                vertex.draw(out, matrix, pose.last().normal(), edge, PurpleDissolveProfile.progress(age));
                            }
                        }
                        BUFFERS.endBatch(batch.type());
                    }
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            BUFFERS.endBatch();
        }
    }

    static void fragments(Mesh mesh, Vec3 camera, float partialTick, int density) {
        if (world == null || world != Minecraft.getInstance().level) {
            return;
        }
        for (Ghost ghost : GHOSTS) {
            double age = ghost.age(partialTick);
            if (age < 0 || age > PurpleDissolveProfile.LIFETIME || camera.distanceToSqr(ghost.feet()) > 128 * 128) {
                continue;
            }
            int count = Math.max(12, Math.min(100, density));
            for (int i = 0; i < count; i++) {
                double height = RedVfxRenderer.rnd(ghost.seed(), i, 201);
                double birth = PurpleDissolveProfile.BEGIN + (1 - height) * 2.5;
                double dt = age - birth;
                if (dt < 0 || dt > 1.6) {
                    continue;
                }
                double angle = RedVfxRenderer.rnd(ghost.seed(), i, 202) * Math.PI * 2 + dt * 1.2;
                double radius = ghost.width() * (0.35 + 0.6 * RedVfxRenderer.rnd(ghost.seed(), i, 203)) + dt * 0.8;
                Vec3 at = ghost.feet().add(Math.cos(angle) * radius, height * ghost.height() + dt * dt * 0.65, Math.sin(angle) * radius);
                double alpha = Math.sin(Math.PI * dt / 1.6) * 0.75;
                mesh.glow(at, 0.025 + 0.04 * RedVfxRenderer.rnd(ghost.seed(), i, 204), new float[]{0.8f, 0.48f, 1f, (float) alpha});
                mesh.ribbon(at, at.add(0, 0.12 + dt * 0.18, 0), 0.012, 0,
                        new float[]{0.95f, 0.8f, 1f, (float) alpha}, new float[]{0.5f, 0.12f, 1f, 0});
            }
        }
    }

    private static final class Vertex {
        double x, y, z;
        int r = 255, g = 255, b = 255, a = 255, overlay, light;
        float u, v, nx, ny = 1, nz;

        void draw(VertexConsumer out, Matrix4f matrix, org.joml.Matrix3f normalMatrix, double edge, double progress) {
            double tint = Math.max(progress * 0.38, edge * 0.9);
            out.vertex(matrix, (float) x, (float) y, (float) z)
                    .color((int) (r * (1 - tint) + 220 * tint), (int) (g * (1 - tint) + (100 + edge * 130) * tint),
                            (int) (b * (1 - tint) + 255 * tint), a)
                    .uv(u, v).overlayCoords(overlay).uv2(edge > 0.05 ? LightTexture.FULL_BRIGHT : light)
                    .normal(normalMatrix, nx, ny, nz).endVertex();
        }

        static Vertex lerp(Vertex a, Vertex b, double t) {
            Vertex out = new Vertex();
            out.x = a.x + (b.x - a.x) * t; out.y = a.y + (b.y - a.y) * t; out.z = a.z + (b.z - a.z) * t;
            out.u = (float) (a.u + (b.u - a.u) * t); out.v = (float) (a.v + (b.v - a.v) * t);
            out.r = a.r; out.g = a.g; out.b = a.b; out.a = a.a;
            out.overlay = a.overlay; out.light = a.light;
            out.nx = a.nx; out.ny = a.ny; out.nz = a.nz;
            return out;
        }
    }

    private static final class Capture implements MultiBufferSource {
        final Map<RenderType, Collector> collectors = new LinkedHashMap<>();
        int vertices;

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return collectors.computeIfAbsent(type, key -> new Collector(this, key));
        }

        List<Batch> finish() {
            List<Batch> batches = new ArrayList<>();
            for (var entry : collectors.entrySet()) {
                if (!entry.getValue().quads.isEmpty()) {
                    batches.add(new Batch(entry.getKey(), entry.getValue().quads));
                }
            }
            return batches;
        }
    }

    private static final class Collector implements VertexConsumer {
        final Capture capture;
        final RenderType type;
        final List<Vertex[]> quads = new ArrayList<>();
        final Vertex[] face = new Vertex[4];
        Vertex next = new Vertex();
        int count;

        Collector(Capture capture, RenderType type) { this.capture = capture; this.type = type; }
        @Override public VertexConsumer vertex(double x, double y, double z) { next.x = x; next.y = y; next.z = z; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { next.r = r; next.g = g; next.b = b; next.a = a; return this; }
        @Override public VertexConsumer uv(float u, float v) { next.u = u; next.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { next.overlay = u | v << 16; return this; }
        @Override public VertexConsumer uv2(int u, int v) { next.light = u | v << 16; return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { next.nx = x; next.ny = y; next.nz = z; return this; }
        @Override public void defaultColor(int r, int g, int b, int a) { color(r, g, b, a); }
        @Override public void unsetDefaultColor() {}

        @Override
        public void endVertex() {
            face[count++] = next;
            next = new Vertex();
            if (count < 4) { return; }
            count = 0;
            if (type.mode() != VertexFormat.Mode.QUADS) { return; }
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5 && capture.vertices + 4 <= MAX_VERTICES; x++) {
                    quads.add(new Vertex[]{sample(x / 5.0, y / 5.0), sample(x / 5.0, (y + 1) / 5.0),
                            sample((x + 1) / 5.0, (y + 1) / 5.0), sample((x + 1) / 5.0, y / 5.0)});
                    capture.vertices += 4;
                }
            }
        }

        Vertex sample(double u, double v) {
            return Vertex.lerp(Vertex.lerp(face[0], face[1], v), Vertex.lerp(face[3], face[2], v), u);
        }
    }
}
