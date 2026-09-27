package net.schwarz.rotasutils.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.item.ZoneWandItem;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.List;

/**
 * Shows where level zones are: every border to an admin holding the Zone Wand (or who pinned borders
 * on in the zone manager), and to players only the nearby borders they should know about - see
 * {@link #renderPlayerBorders}.
 *
 * <p>Every zone border in this dimension within render distance is drawn, so zones can be found
 * without first selecting one. The working zone - the one stored on the wand, which is the zone the
 * next click grows - is bright; the rest are dimmer. A border is a translucent curtain standing on
 * the terrain (see {@link ZoneBorderGeometry}), in the zone's own colour ({@link ZoneColors}: zones that
 * touch or nest never share one; the label still names the danger), fading upward and hidden by hills
 * like real geometry, with a faint copy of its ground line drawn through terrain. Each zone gets a
 * floating label at its border point nearest the camera. Spheres and exact-height boxes are 3D
 * volumes (arenas, caves), so they also keep a wireframe.</p>
 *
 * <p>An unfinished Box or Outline on the wand is previewed in light yellow. Buffers are reused, so
 * holding the wand allocates almost nothing per frame.</p>
 */
@Environment(EnvType.CLIENT)
public final class ZoneOutlineRenderer {
    private static final float CURTAIN_ALPHA = 0.32f;
    private static final float LINE_ALPHA = 0.95f;
    private static final float XRAY_ALPHA = 0.25f;
    private static final float OTHER_ZONE_ALPHA = 0.45f;
    private static final int MAX_LABELS = 16;
    private static final int[] PENDING = {255, 242, 115};
    private static final int[] EXCLUDED = {255, 70, 210};
    private static final PanelBuffer PANELS = new PanelBuffer();
    private static final RenderLineSink LINE_SINK = new RenderLineSink();
    private static final ZoneDef[] LABEL_ZONES = new ZoneDef[MAX_LABELS];
    private static final double[] LABEL_POSITIONS = new double[MAX_LABELS * 3];
    private static final boolean[] LABEL_FOCUS = new boolean[MAX_LABELS];
    private static int labelCount;

    private ZoneOutlineRenderer() {
    }

    /** Called from the client level renderer once the world is drawn. */
    public static void render(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        HouseOutlineRenderer.render(poseStack, camera);
        ItemStack wand = heldWand(minecraft);
        boolean pinned = wand == null && ClientState.admin() && ClientState.zoneBordersPinned();
        if (wand == null && !pinned) {
            renderPlayerBorders(minecraft, poseStack, camera);
            return;
        }
        String dimension = minecraft.level.dimension().location().toString();
        ZoneDef working = wand != null ? workingZone(minecraft, wand, dimension) : ClientState.zone(ClientState.selectedZone());
        List<BlockPos> pending = wand != null ? ZoneWandItem.points(wand) : List.of();
        if (ClientState.zones().isEmpty() && pending.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        int bottom = minecraft.level.getMinBuildHeight();
        int top = minecraft.level.getMaxBuildHeight() - 1;
        ZoneBorderGeometry.View view = new ZoneBorderGeometry.View(cam.x, cam.z,
                minecraft.options.getEffectiveRenderDistance() * 16.0);
        ZoneBorderGeometry.Surface surface = (x, z) -> minecraft.level.hasChunk(x >> 4, z >> 4)
                ? minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : ZoneBorderGeometry.UNKNOWN;

        PANELS.reset(cam);
        labelCount = 0;
        for (ZoneDef zone : ClientState.zones().values()) {
            boolean focus = working != null && zone.id().equals(working.id());
            if (!drawable(zone, dimension, focus)) {
                continue;
            }
            PANELS.begin(zoneColor(zone), focus ? 1.0f : OTHER_ZONE_ALPHA);
            for (ZoneArea area : zone.areas()) {
                ZoneBorderGeometry.trace(area, surface, view, PANELS);
            }
            PANELS.begin(EXCLUDED, focus ? 1.0f : OTHER_ZONE_ALPHA);
            for (ZoneArea area : zone.excludedAreas()) {
                ZoneBorderGeometry.trace(area, surface, view, PANELS);
            }
            if (PANELS.hasNearest() && labelCount < MAX_LABELS) {
                LABEL_ZONES[labelCount] = zone;
                LABEL_POSITIONS[labelCount * 3] = PANELS.nearestX();
                LABEL_POSITIONS[labelCount * 3 + 1] = PANELS.nearestY();
                LABEL_POSITIONS[labelCount * 3 + 2] = PANELS.nearestZ();
                LABEL_FOCUS[labelCount] = focus;
                labelCount++;
            }
        }
        ZoneArea previewBox = wand == null ? null : previewBox(minecraft, wand, pending, bottom, top);
        if (previewBox != null) {
            PANELS.begin(PENDING, 1.0f);
            ZoneBorderGeometry.trace(previewBox, surface, view, PANELS);
        }

        PoseStack.Pose pose = poseStack.last();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            if (PANELS.size() > 0) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                drawCurtains(pose, cam);
                drawGroundLines(pose, cam, LINE_ALPHA);
                RenderSystem.disableDepthTest();
                drawGroundLines(pose, cam, XRAY_ALPHA);
            }
            RenderSystem.disableDepthTest();
            drawWireframes(minecraft, pose, cam, dimension, working, pending, wand, bottom, top);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        ZoneDef here = ZoneDef.select(ClientState.zones().values(), dimension, cam.x, cam.y, cam.z);
        for (int i = 0; i < labelCount; i++) {
            ZoneDef zone = LABEL_ZONES[i];
            int[] color = zoneColor(zone);
            int shade = LABEL_FOCUS[i] ? 255 : 170;
            int argb = 0xFF000000 | (color[0] * shade / 255) << 16 | (color[1] * shade / 255) << 8 | (color[2] * shade / 255);
            drawLabel(minecraft, poseStack, camera, adminLabel(zone, here != null && here.id().equals(zone.id())), argb,
                    LABEL_POSITIONS[i * 3], LABEL_POSITIONS[i * 3 + 1], LABEL_POSITIONS[i * 3 + 2],
                    LABEL_FOCUS[i] ? 1.0f : 0.8f);
            LABEL_ZONES[i] = null;
        }
    }

    /**
     * Admin label: "> Village  Lv 1-3  Safe  P2  [Locked]  (off)". The arrow marks the zone that governs
     * the camera position (the one whose band and rules apply there), P is its priority - which zone wins
     * where they overlap - and the lock, dungeon and off flags show at a glance which zones stop players.
     */
    private static String adminLabel(ZoneDef zone, boolean governsHere) {
        StringBuilder text = new StringBuilder();
        if (governsHere) {
            text.append("\u25B6 ");
        }
        text.append(zone.name().isEmpty() ? zone.id() : zone.name()).append("  ").append(zone.levelLabel()).append("  ")
                .append(zone.safe() ? net.schwarz.rotasutils.util.ThaiText.phrase("Safe") : zone.danger().label())
                .append("  P").append(zone.priority());
        if (zone.hasEntryLock()) {
            text.append("  ").append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.hud.zone.locked_tag"));
        }
        if (zone.features().dungeonRun()) {
            text.append("  ").append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.hud.zone.dungeon_tag"));
        }
        if (!zone.enabled()) {
            text.append("  ").append(net.schwarz.rotasutils.util.ThaiText.phrase("(off)"));
        }
        return text.toString();
    }

    // Player view ------------------------------------------------------------------------------------

    /** Beyond this many blocks a player sees no border at all. */
    private static final double PLAYER_NEAR = 28.0;
    /** Inside this many blocks the border is at full strength. */
    private static final double PLAYER_FULL = 6.0;
    /** Labels only for borders this close, so a busy area does not fill the sky with text. */
    private static final double PLAYER_LABEL = 18.0;

    /**
     * What a player sees: the borders of nearby zones they should know about before stepping in - locked
     * for them (red, with the first missing requirement), marked Dangerous or Deadly (danger colour), or
     * set to always show. A border fades in as the player approaches and is gone past {@link #PLAYER_NEAR}
     * blocks; the zone the player already stands in draws nothing (the zone chip covers it).
     */
    private static void renderPlayerBorders(Minecraft minecraft, PoseStack poseStack, Camera camera) {
        java.util.Collection<ZoneDef> zones = ClientZoneView.zones();
        if (zones.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        ZoneBorderGeometry.View view = new ZoneBorderGeometry.View(cam.x, cam.z, PLAYER_NEAR + 8.0);
        ZoneBorderGeometry.Surface surface = (x, z) -> minecraft.level.hasChunk(x >> 4, z >> 4)
                ? minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : ZoneBorderGeometry.UNKNOWN;
        PANELS.reset(cam);
        labelCount = 0;
        for (ZoneDef zone : zones) {
            if (zone.areas().isEmpty()) {
                continue;
            }
            boolean locked = ClientZoneView.locked(zone.id());
            if (!zone.features().display().borderVisible(locked, zone.danger())) {
                continue;
            }
            double gap = zone.distance(cam.x, cam.y, cam.z);
            if (gap <= 0.0 || gap > PLAYER_NEAR) {
                continue;
            }
            float strength = (float) Math.max(0.0, Math.min(1.0, (PLAYER_NEAR - gap) / (PLAYER_NEAR - PLAYER_FULL)));
            strength = strength * strength * (3f - 2f * strength);
            int color = locked ? net.schwarz.rotasutils.client.hud.ZoneHud.LOCKED_COLOR
                    : net.schwarz.rotasutils.client.hud.ZoneHud.dangerColor(zone.danger());
            PANELS.begin(new int[]{(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF}, 0.15f + 0.85f * strength);
            for (ZoneArea area : zone.areas()) {
                ZoneBorderGeometry.trace(area, surface, view, PANELS);
            }
            if (gap <= PLAYER_LABEL && PANELS.hasNearest() && labelCount < MAX_LABELS) {
                LABEL_ZONES[labelCount] = zone;
                LABEL_POSITIONS[labelCount * 3] = PANELS.nearestX();
                LABEL_POSITIONS[labelCount * 3 + 1] = PANELS.nearestY();
                LABEL_POSITIONS[labelCount * 3 + 2] = PANELS.nearestZ();
                LABEL_FOCUS[labelCount] = locked;
                labelCount++;
            }
        }
        if (PANELS.size() > 0) {
            PoseStack.Pose pose = poseStack.last();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            try {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                drawCurtains(pose, cam);
                drawGroundLines(pose, cam, LINE_ALPHA);
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
        }
        for (int i = 0; i < labelCount; i++) {
            ZoneDef zone = LABEL_ZONES[i];
            String lock = ClientZoneView.lock(zone.id());
            String name = zone.name().isEmpty() ? zone.id() : zone.name();
            String text = lock != null
                    ? net.schwarz.rotasutils.util.ThaiText.t("rotasutils.hud.zone.locked_label", name, lock)
                    : name + "  " + zone.levelLabel() + "  " + zone.danger().label();
            int argb = lock != null ? net.schwarz.rotasutils.client.hud.ZoneHud.LOCKED_COLOR
                    : net.schwarz.rotasutils.client.hud.ZoneHud.dangerColor(zone.danger());
            drawLabel(minecraft, poseStack, camera, text, argb, LABEL_POSITIONS[i * 3], LABEL_POSITIONS[i * 3 + 1],
                    LABEL_POSITIONS[i * 3 + 2], 0.85f);
            LABEL_ZONES[i] = null;
        }
    }

    /** Zones with a border here: enabled ones in this dimension, plus the working zone even when off. */
    private static boolean drawable(ZoneDef zone, String dimension, boolean focus) {
        if (zone.areas().isEmpty() && zone.excludedAreas().isEmpty()) {
            return false;
        }
        return focus ? zone.dimension().isEmpty() || zone.dimension().equals(dimension) : zone.appliesTo(dimension);
    }

    private static void drawCurtains(PoseStack.Pose pose, Vec3 cam) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double[] p = PANELS.data();
        for (int i = 0; i < PANELS.size(); i++) {
            int o = i * PanelBuffer.STRIDE;
            int r = (int) p[o + 8], g = (int) p[o + 9], b = (int) p[o + 10];
            int base = (int) (CURTAIN_ALPHA * p[o + 11] * 255);
            vertex(buffer, pose, cam, p[o], p[o + 2], p[o + 1], r, g, b, base);
            vertex(buffer, pose, cam, p[o + 4], p[o + 6], p[o + 5], r, g, b, base);
            vertex(buffer, pose, cam, p[o + 4], p[o + 7], p[o + 5], r, g, b, 0);
            vertex(buffer, pose, cam, p[o], p[o + 3], p[o + 1], r, g, b, 0);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawGroundLines(PoseStack.Pose pose, Vec3 cam, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        double[] p = PANELS.data();
        for (int i = 0; i < PANELS.size(); i++) {
            int o = i * PanelBuffer.STRIDE;
            int r = (int) p[o + 8], g = (int) p[o + 9], b = (int) p[o + 10];
            int a = (int) (alpha * p[o + 11] * 255);
            // A hair above the ground so the line does not z-fight with the grass it sits on.
            vertex(buffer, pose, cam, p[o], p[o + 2] + 0.03, p[o + 1], r, g, b, a);
            vertex(buffer, pose, cam, p[o + 4], p[o + 6] + 0.03, p[o + 5], r, g, b, a);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawWireframes(Minecraft minecraft, PoseStack.Pose pose, Vec3 cam, String dimension,
                                       ZoneDef working, List<BlockPos> pending, ItemStack wand, int bottom, int top) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        LINE_SINK.begin(buffer, pose, cam);
        try {
            for (ZoneDef zone : ClientState.zones().values()) {
                boolean focus = working != null && zone.id().equals(working.id());
                if (!drawable(zone, dimension, focus)) {
                    continue;
                }
                LINE_SINK.color(zoneColor(zone), focus ? 0.6f : 0.6f * OTHER_ZONE_ALPHA);
                for (ZoneArea area : zone.areas()) {
                    if (!ZoneBorderGeometry.isColumn(area, bottom, top) && near(area, cam, minecraft)) {
                        ZoneOutlineGeometry.trace(area, LINE_SINK);
                    }
                }
                LINE_SINK.color(EXCLUDED, focus ? 0.85f : 0.85f * OTHER_ZONE_ALPHA);
                for (ZoneArea area : zone.excludedAreas()) {
                    if (!ZoneBorderGeometry.isColumn(area, bottom, top) && near(area, cam, minecraft)) ZoneOutlineGeometry.trace(area, LINE_SINK);
                }
            }
            if (!pending.isEmpty()) {
                LINE_SINK.color(PENDING, 0.9f);
                for (BlockPos point : pending) {
                    ZoneOutlineGeometry.trace(new ZoneArea.Box(point.getX(), point.getY(), point.getZ(),
                            point.getX(), point.getY(), point.getZ()), LINE_SINK);
                }
                if (ZoneWandItem.mode(wand) == ZoneWandItem.Mode.OUTLINE) {
                    for (int i = 1; i < pending.size(); i++) {
                        centerLine(pending.get(i - 1), pending.get(i));
                    }
                    BlockPos aimed = aimedBlock(minecraft);
                    if (aimed != null) {
                        centerLine(pending.get(pending.size() - 1), aimed);
                        if (pending.size() >= 2) {
                            centerLine(aimed, pending.get(0));
                        }
                    }
                }
            }
        } finally {
            LINE_SINK.end();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static boolean near(ZoneArea area, Vec3 cam, Minecraft minecraft) {
        return area.distance(cam.x, cam.y, cam.z) <= minecraft.options.getEffectiveRenderDistance() * 16.0;
    }

    /** A line of text floating above a border point, readable through terrain, scaled with distance. */
    private static void drawLabel(Minecraft minecraft, PoseStack poseStack, Camera camera, String text, int argb,
                                  double x, double groundY, double z, float size) {
        Vec3 cam = camera.getPosition();
        double y = groundY + ZoneBorderGeometry.CURTAIN_HEIGHT + 0.8;
        double distance = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
        float scale = 0.025f * (float) Math.max(1.0, Math.min(6.0, distance / 10.0)) * size;
        Font font = minecraft.font;
        poseStack.pushPose();
        try {
            poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-scale, -scale, scale);
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            int background = (int) (minecraft.options.getBackgroundOpacity(0.35f) * 255.0f) << 24;
            font.drawInBatch(text, -font.width(text) / 2.0f, 0, argb, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        } finally {
            poseStack.popPose();
        }
    }

    /** The box an unfinished Box would make if the aimed block were the second corner. */
    private static ZoneArea previewBox(Minecraft minecraft, ItemStack wand, List<BlockPos> pending, int bottom, int top) {
        if (pending.isEmpty() || ZoneWandItem.mode(wand) != ZoneWandItem.Mode.BOX) {
            return null;
        }
        BlockPos aimed = aimedBlock(minecraft);
        return aimed == null ? null : ZoneWandItem.box(pending.get(0), aimed, bottom, top);
    }

    private static BlockPos aimedBlock(Minecraft minecraft) {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
    }

    private static java.util.Map<String, int[]> colours = java.util.Map.of();
    private static long coloursSignature = Long.MIN_VALUE;

    /** The zone's own colour; recomputed only when the synced zone set changes. */
    private static int[] zoneColor(ZoneDef zone) {
        long signature = ClientState.zones().size();
        for (ZoneDef each : ClientState.zones().values()) {
            // Zones are replaced with new instances on every sync, so identity tracks any change.
            signature = signature * 31 + System.identityHashCode(each);
        }
        if (signature != coloursSignature) {
            colours = ZoneColors.colours(ClientState.zones().values());
            coloursSignature = signature;
        }
        return colours.getOrDefault(zone.id(), ZoneColors.PALETTE[0]);
    }

    private static void vertex(BufferBuilder buffer, PoseStack.Pose pose, Vec3 cam, double x, double y, double z,
                               int r, int g, int b, int a) {
        buffer.vertex(pose.pose(), (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color(r, g, b, a).endVertex();
    }

    private static void centerLine(BlockPos a, BlockPos b) {
        LINE_SINK.line(a.getX() + 0.5, a.getY() + 1.02, a.getZ() + 0.5, b.getX() + 0.5, b.getY() + 1.02, b.getZ() + 0.5);
    }

    private static ItemStack heldWand(Minecraft minecraft) {
        if (minecraft.player.getMainHandItem().is(RotasRegistry.ZONE_WAND.get())) {
            return minecraft.player.getMainHandItem();
        }
        if (minecraft.player.getOffhandItem().is(RotasRegistry.ZONE_WAND.get())) {
            return minecraft.player.getOffhandItem();
        }
        return null;
    }

    /**
     * The zone to highlight: the one stamped on the wand by the server, else the one open in the
     * editor, else the highest-priority zone the player stands in.
     */
    private static ZoneDef workingZone(Minecraft minecraft, ItemStack wand, String dimension) {
        ZoneDef stamped = ClientState.zone(ZoneWandItem.zone(wand));
        if (stamped != null) {
            return stamped;
        }
        ZoneDef selected = ClientState.zone(ClientState.selectedZone());
        if (selected != null) {
            return selected;
        }
        ZoneDef here = null;
        for (ZoneDef zone : ClientState.zones().values()) {
            if (!zone.appliesTo(dimension) || zone.areas().isEmpty()
                    || !zone.contains(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ())) {
                continue;
            }
            if (here == null || zone.priority() > here.priority()) {
                here = zone;
            }
        }
        return here;
    }

    /** Reused flat store of curtain panels, tracking each zone's panel nearest the camera. */
    private static final class PanelBuffer implements ZoneBorderGeometry.CurtainSink {
        static final int STRIDE = 12;
        private static final int MAX_PANELS = 16384;
        private double[] data = new double[STRIDE * 512];
        private int size;
        private int[] color = PENDING;
        private float opacity = 1.0f;
        private double camX, camY, camZ;
        private double nearestDistance;
        private double nearestX, nearestY, nearestZ;

        void reset(Vec3 camera) {
            size = 0;
            camX = camera.x;
            camY = camera.y;
            camZ = camera.z;
        }

        /** Starts one zone: its colour, its opacity and a fresh nearest-point search. */
        void begin(int[] rgb, float alpha) {
            color = rgb;
            opacity = alpha;
            nearestDistance = Double.MAX_VALUE;
        }

        @Override
        public void panel(double ax, double az, double bottomA, double topA,
                          double bx, double bz, double bottomB, double topB) {
            if (size >= MAX_PANELS) {
                return;
            }
            if ((size + 1) * STRIDE > data.length) {
                data = java.util.Arrays.copyOf(data, Math.min(MAX_PANELS * STRIDE, data.length * 2));
            }
            int o = size * STRIDE;
            data[o] = ax; data[o + 1] = az; data[o + 2] = bottomA; data[o + 3] = topA;
            data[o + 4] = bx; data[o + 5] = bz; data[o + 6] = bottomB; data[o + 7] = topB;
            data[o + 8] = color[0]; data[o + 9] = color[1]; data[o + 10] = color[2]; data[o + 11] = opacity;
            size++;
            double mx = (ax + bx) / 2, my = (bottomA + bottomB) / 2, mz = (az + bz) / 2;
            double d = (mx - camX) * (mx - camX) + (my - camY) * (my - camY) + (mz - camZ) * (mz - camZ);
            if (d < nearestDistance) {
                nearestDistance = d;
                nearestX = mx;
                nearestY = my;
                nearestZ = mz;
            }
        }

        double[] data() { return data; }
        int size() { return size; }
        boolean hasNearest() { return nearestDistance != Double.MAX_VALUE; }
        double nearestX() { return nearestX; }
        double nearestY() { return nearestY; }
        double nearestZ() { return nearestZ; }
    }

    private static final class RenderLineSink implements ZoneOutlineGeometry.LineSink {
        private BufferBuilder buffer;
        private PoseStack.Pose pose;
        private Vec3 camera;
        private int red, green, blue, alpha;

        void begin(BufferBuilder buffer, PoseStack.Pose pose, Vec3 camera) {
            this.buffer = buffer;
            this.pose = pose;
            this.camera = camera;
        }

        void color(int[] rgb, float opacity) {
            red = rgb[0];
            green = rgb[1];
            blue = rgb[2];
            alpha = (int) (opacity * 255);
        }

        void end() {
            buffer = null;
            pose = null;
            camera = null;
        }

        @Override
        public void line(double ax, double ay, double az, double bx, double by, double bz) {
            vertex(buffer, pose, camera, ax, ay, az, red, green, blue, alpha);
            vertex(buffer, pose, camera, bx, by, bz, red, green, blue, alpha);
        }
    }
}
