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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.house.HouseBounds;
import net.schwarz.rotasutils.item.HouseWandItem;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows house areas while the House Wand is held: every house in this dimension within render distance is a
 * translucent box with bright edges (edges also show faintly through blocks), coloured by rental status and
 * labelled with its name, status, tier and size. The corners picked with the wand are marked, and the box
 * they make (up to the aimed block while only corner 1 is set) is previewed in yellow, or red when it
 * would overlap a house or is too large.
 */
@Environment(EnvType.CLIENT)
public final class HouseOutlineRenderer {
    private static final int[] AVAILABLE = {96, 224, 140};
    private static final int[] RENTED = {90, 160, 255};
    private static final int[] OVERDUE = {255, 166, 64};
    private static final int[] OWNED = {190, 120, 255};
    private static final int[] DISABLED = {150, 150, 150};
    private static final int[] SELECTION = {255, 242, 115};
    private static final int[] BLOCKED = {255, 70, 70};
    private static final float FACE_ALPHA = 0.10f;
    private static final float SELECTION_FACE_ALPHA = 0.16f;
    private static final float EDGE_ALPHA = 0.9f;
    private static final float XRAY_ALPHA = 0.3f;
    private static final double INFLATE = 0.004;

    private HouseOutlineRenderer() {
    }

    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int[] color, float faceAlpha) {
        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        String size() {
            return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
        }

        boolean overlaps(HouseBounds other) {
            return minX <= other.maxX() && maxX >= other.minX() && minY <= other.maxY() && maxY >= other.minY()
                    && minZ <= other.maxZ() && maxZ >= other.minZ();
        }
    }

    public static void render(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack wand = heldWand(minecraft);
        if (wand == null) {
            return;
        }
        String dimension = minecraft.level.dimension().location().toString();
        Vec3 cam = camera.getPosition();
        double reach = minecraft.options.getEffectiveRenderDistance() * 16.0;

        List<Box> boxes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<ClientHouse> nearby = new ArrayList<>();
        for (ClientHouse house : ClientState.houses()) {
            HouseBounds b = house.bounds();
            if (!b.dimension().equals(dimension) || distanceToBox(cam, b.minX(), b.minZ(), b.maxX(), b.maxZ()) > reach) {
                continue;
            }
            nearby.add(house);
            boxes.add(new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), color(house), FACE_ALPHA));
            labels.add(house.name() + "  " + statusLabel(house) + "  " + house.tier() + "  " + house.sizeLabel());
        }

        BlockPos first = HouseWandItem.first(wand);
        BlockPos second = HouseWandItem.second(wand);
        boolean sameDimension = dimension.equals(HouseWandItem.dimension(wand));
        List<BlockPos> corners = new ArrayList<>();
        if (first != null && sameDimension) {
            corners.add(first);
            BlockPos other = second != null ? second : aimedBlock(minecraft);
            if (second != null) {
                corners.add(second);
            }
            if (other != null) {
                Box selection = new Box(Math.min(first.getX(), other.getX()), Math.min(first.getY(), other.getY()),
                        Math.min(first.getZ(), other.getZ()), Math.max(first.getX(), other.getX()),
                        Math.max(first.getY(), other.getY()), Math.max(first.getZ(), other.getZ()), SELECTION, SELECTION_FACE_ALPHA);
                String overlap = null;
                for (ClientHouse house : nearby) {
                    if (house.enabled() && selection.overlaps(house.bounds())) {
                        overlap = house.name();
                        break;
                    }
                }
                boolean tooBig = selection.volume() > HouseBounds.MAX_VOLUME;
                boolean blocked = overlap != null || tooBig;
                boxes.add(new Box(selection.minX(), selection.minY(), selection.minZ(), selection.maxX(), selection.maxY(),
                        selection.maxZ(), blocked ? BLOCKED : SELECTION, SELECTION_FACE_ALPHA));
                labels.add(selection.size() + " = " + selection.volume() + " blocks"
                        + (overlap != null ? "  overlaps " + overlap : "") + (tooBig ? "  too big" : ""));
            }
        }
        if (boxes.isEmpty() && corners.isEmpty()) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            drawFaces(pose, cam, boxes);
            drawEdges(pose, cam, boxes, corners, 1.0f);
            RenderSystem.disableDepthTest();
            drawEdges(pose, cam, boxes, corners, XRAY_ALPHA);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            drawLabel(minecraft, poseStack, camera, labels.get(i), (box.minX() + box.maxX() + 1) / 2.0,
                    box.maxY() + 1.6, (box.minZ() + box.maxZ() + 1) / 2.0, box.color());
        }
    }

    private static void drawFaces(PoseStack.Pose pose, Vec3 cam, List<Box> boxes) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Box box : boxes) {
            double x1 = box.minX() - INFLATE, y1 = box.minY() - INFLATE, z1 = box.minZ() - INFLATE;
            double x2 = box.maxX() + 1 + INFLATE, y2 = box.maxY() + 1 + INFLATE, z2 = box.maxZ() + 1 + INFLATE;
            int[] c = box.color();
            int a = (int) (box.faceAlpha() * 255);
            quad(buffer, pose, cam, c, a, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
            quad(buffer, pose, cam, c, a, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
            quad(buffer, pose, cam, c, a, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
            quad(buffer, pose, cam, c, a, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2);
            quad(buffer, pose, cam, c, a, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2);
            quad(buffer, pose, cam, c, a, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawEdges(PoseStack.Pose pose, Vec3 cam, List<Box> boxes, List<BlockPos> corners, float alphaScale) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (Box box : boxes) {
            edges(buffer, pose, cam, box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1,
                    box.color(), (int) (EDGE_ALPHA * alphaScale * 255));
        }
        for (BlockPos corner : corners) {
            edges(buffer, pose, cam, corner.getX() - 0.02, corner.getY() - 0.02, corner.getZ() - 0.02,
                    corner.getX() + 1.02, corner.getY() + 1.02, corner.getZ() + 1.02, SELECTION, (int) (255 * alphaScale));
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void edges(BufferBuilder buffer, PoseStack.Pose pose, Vec3 cam, double x1, double y1, double z1,
                              double x2, double y2, double z2, int[] c, int a) {
        for (double y : new double[]{y1, y2}) {
            line(buffer, pose, cam, c, a, x1, y, z1, x2, y, z1);
            line(buffer, pose, cam, c, a, x2, y, z1, x2, y, z2);
            line(buffer, pose, cam, c, a, x2, y, z2, x1, y, z2);
            line(buffer, pose, cam, c, a, x1, y, z2, x1, y, z1);
        }
        line(buffer, pose, cam, c, a, x1, y1, z1, x1, y2, z1);
        line(buffer, pose, cam, c, a, x2, y1, z1, x2, y2, z1);
        line(buffer, pose, cam, c, a, x2, y1, z2, x2, y2, z2);
        line(buffer, pose, cam, c, a, x1, y1, z2, x1, y2, z2);
    }

    private static void quad(BufferBuilder buffer, PoseStack.Pose pose, Vec3 cam, int[] c, int a,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz) {
        vertex(buffer, pose, cam, ax, ay, az, c, a);
        vertex(buffer, pose, cam, bx, by, bz, c, a);
        vertex(buffer, pose, cam, cx, cy, cz, c, a);
        vertex(buffer, pose, cam, dx, dy, dz, c, a);
    }

    private static void line(BufferBuilder buffer, PoseStack.Pose pose, Vec3 cam, int[] c, int a,
                             double ax, double ay, double az, double bx, double by, double bz) {
        vertex(buffer, pose, cam, ax, ay, az, c, a);
        vertex(buffer, pose, cam, bx, by, bz, c, a);
    }

    private static void vertex(BufferBuilder buffer, PoseStack.Pose pose, Vec3 cam, double x, double y, double z, int[] c, int a) {
        buffer.vertex(pose.pose(), (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color(c[0], c[1], c[2], a).endVertex();
    }

    private static void drawLabel(Minecraft minecraft, PoseStack poseStack, Camera camera, String text,
                                  double x, double y, double z, int[] color) {
        Vec3 cam = camera.getPosition();
        double distance = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
        float scale = 0.025f * (float) Math.max(1.0, Math.min(6.0, distance / 10.0));
        Font font = minecraft.font;
        poseStack.pushPose();
        try {
            poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-scale, -scale, scale);
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            int background = (int) (minecraft.options.getBackgroundOpacity(0.35f) * 255.0f) << 24;
            int argb = 0xFF000000 | color[0] << 16 | color[1] << 8 | color[2];
            font.drawInBatch(text, -font.width(text) / 2.0f, 0, argb, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        } finally {
            poseStack.popPose();
        }
    }

    private static int[] color(ClientHouse house) {
        if (!house.enabled()) {
            return DISABLED;
        }
        return switch (house.status()) {
            case "ACTIVE" -> RENTED;
            case "OVERDUE" -> OVERDUE;
            case "BOUGHT_OUT" -> OWNED;
            default -> AVAILABLE;
        };
    }

    private static String statusLabel(ClientHouse house) {
        if (!house.enabled()) {
            return "Disabled";
        }
        return switch (house.status()) {
            case "ACTIVE" -> "Rented";
            case "OVERDUE" -> "Overdue";
            case "BOUGHT_OUT" -> "Owned";
            default -> "Available";
        };
    }

    private static double distanceToBox(Vec3 cam, int minX, int minZ, int maxX, int maxZ) {
        double dx = Math.max(Math.max(minX - cam.x, 0), cam.x - (maxX + 1));
        double dz = Math.max(Math.max(minZ - cam.z, 0), cam.z - (maxZ + 1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static BlockPos aimedBlock(Minecraft minecraft) {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
    }

    private static ItemStack heldWand(Minecraft minecraft) {
        if (minecraft.player.getMainHandItem().is(RotasRegistry.HOUSE_WAND.get())) {
            return minecraft.player.getMainHandItem();
        }
        if (minecraft.player.getOffhandItem().is(RotasRegistry.HOUSE_WAND.get())) {
            return minecraft.player.getOffhandItem();
        }
        return null;
    }
}
