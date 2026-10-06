package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.WorldPicker;
import net.schwarz.rotasutils.server.ZoneWandService;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ZoneWandItem extends Item {
    private static final String TAG = "RotasZoneWand";
    public static final int FLAT_BOX_HEIGHT = 8;
    public static final int CLOSE_DISTANCE = 1;

    public enum Mode {
        SPHERE("Sphere"), BOX("Box"), OUTLINE("Outline");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return net.schwarz.rotasutils.util.ThaiText.label("zone_mode", this, label);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public ZoneWandItem(Properties properties) {
        super(properties);
    }

    public static Mode mode(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return Mode.SPHERE;
        }
        try {
            return Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException unknown) {
            return Mode.SPHERE;
        }
    }

    public static void setMode(ItemStack stack, Mode mode) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putString("mode", mode.name());
        tag.remove("points");
    }

    public static String zone(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? "" : tag.getString("zone");
    }

    public static void setZone(ItemStack stack, String zoneId) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        if (zoneId == null || zoneId.isEmpty()) {
            tag.remove("zone");
        } else {
            tag.putString("zone", zoneId);
        }
    }

    public static List<BlockPos> points(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        List<BlockPos> points = new ArrayList<>();
        if (tag == null) {
            return points;
        }
        int[] raw = tag.getIntArray("points");
        for (int i = 0; i + 2 < raw.length && points.size() < ZoneArea.MAX_POLYGON_POINTS; i += 3) {
            points.add(new BlockPos(raw[i], raw[i + 1], raw[i + 2]));
        }
        return points;
    }

    public static void setPoints(ItemStack stack, List<BlockPos> points) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        if (points.isEmpty()) {
            tag.remove("points");
            return;
        }
        int[] raw = new int[Math.min(points.size(), ZoneArea.MAX_POLYGON_POINTS) * 3];
        for (int i = 0; i < raw.length / 3; i++) {
            raw[i * 3] = points.get(i).getX();
            raw[i * 3 + 1] = points.get(i).getY();
            raw[i * 3 + 2] = points.get(i).getZ();
        }
        tag.putIntArray("points", raw);
    }

    public static ZoneArea.Box box(BlockPos a, BlockPos b, int minBuildY, int maxBuildY) {
        boolean flat = Math.abs(a.getY() - b.getY()) < FLAT_BOX_HEIGHT;
        int lowY = flat ? minBuildY : Math.min(a.getY(), b.getY());
        int highY = flat ? maxBuildY : Math.max(a.getY(), b.getY());
        return new ZoneArea.Box(Math.min(a.getX(), b.getX()), lowY, Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), highY, Math.max(a.getZ(), b.getZ()));
    }

    public static ZoneArea.Polygon outline(List<BlockPos> points, int minBuildY, int maxBuildY) {
        List<ZoneArea.Polygon.Point> corners = new ArrayList<>();
        for (BlockPos pos : points) {
            ZoneArea.Polygon.Point point = new ZoneArea.Polygon.Point(pos.getX(), pos.getZ());
            if (corners.isEmpty() || !corners.get(corners.size() - 1).equals(point)) {
                corners.add(point);
            }
        }
        if (corners.size() > 1 && corners.get(0).equals(corners.get(corners.size() - 1))) {
            corners.remove(corners.size() - 1);
        }
        return corners.size() < 3 ? null : new ZoneArea.Polygon(corners, minBuildY, maxBuildY);
    }

    public static boolean closesOutline(List<BlockPos> points, BlockPos click) {
        if (points.size() < 3) {
            return false;
        }
        BlockPos first = points.get(0);
        return Math.abs(click.getX() - first.getX()) <= CLOSE_DISTANCE
                && Math.abs(click.getZ() - first.getZ()) <= CLOSE_DISTANCE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!BoardService.isAdmin(serverPlayer, RotasData.get(serverPlayer.server))) {
                serverPlayer.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.zone.admin_only")
                        .withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            if (player.isShiftKeyDown()) {
                ZoneWandService.cycleMode(serverPlayer, stack);
            } else if (WorldPicker.cancel(serverPlayer)) {
                serverPlayer.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.placing_cancelled").withStyle(ChatFormatting.YELLOW));
            } else if (!ZoneWandService.cancelShape(serverPlayer, stack)) {
                RotasNetwork.openAdminMenu(serverPlayer, "ZONES");
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            BlockPos pos = context.getClickedPos();
            if (WorldPicker.isPending(serverPlayer)) {
                WorldPicker.resolveBlock(serverPlayer, pos);
            } else {
                ZoneWandService.useBlock(serverPlayer, context.getItemInHand(), pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ZoneWandService.useEntity(serverPlayer, target);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.zone_wand.mode", mode(stack).label()).withStyle(ChatFormatting.AQUA));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.zone_wand.blocks").withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.zone_wand.outline").withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.zone_wand.switch").withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.zone_wand.mob").withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.zone_wand.air").withStyle(ChatFormatting.DARK_GRAY));
    }
}
