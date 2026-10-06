package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.RiftPortalEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RiftSigilItem extends Item {
    private static final double REACH = 12.0;
    private static final double FALLBACK_DISTANCE = 4.0;
    private static final String DESTINATION = "RotasRiftDestination";

    private final RiftPortalEntity.Kind kind;

    public RiftSigilItem(Properties properties) {
        this(properties, RiftPortalEntity.Kind.ARRIVAL);
    }

    public RiftSigilItem(Properties properties, RiftPortalEntity.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(held);
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.admin_only"), true);
            return InteractionResultHolder.fail(held);
        }
        if (kind == RiftPortalEntity.Kind.TRAVEL && serverPlayer.isShiftKeyDown()) {
            bind(held, serverPlayer);
            return InteractionResultHolder.consume(held);
        }
        CompoundTag destination = held.getTagElement(DESTINATION);
        if (kind == RiftPortalEntity.Kind.TRAVEL && destination == null) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.rift_key.unbound"), true);
            return InteractionResultHolder.fail(held);
        }
        Vec3 ground = groundInFront(serverLevel, serverPlayer);
        if (ground == null) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.rift_sigil.no_room"), true);
            return InteractionResultHolder.fail(held);
        }
        Vec3 toPlayer = serverPlayer.position().subtract(ground);
        float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z));
        if (kind == RiftPortalEntity.Kind.TRAVEL) {
            RiftPortalEntity.openTravel(serverLevel, ground, yaw, new ResourceLocation(destination.getString("dim")),
                    new Vec3(destination.getDouble("x"), destination.getDouble("y"), destination.getDouble("z")),
                    destination.getFloat("yaw"));
        } else {
            RiftPortalEntity.open(serverLevel, ground, yaw, kind);
        }
        serverPlayer.getCooldowns().addCooldown(this, kind == RiftPortalEntity.Kind.TRAVEL ? 60 : kind.end());
        return InteractionResultHolder.consume(held);
    }

    private static void bind(ItemStack held, ServerPlayer player) {
        CompoundTag tag = held.getOrCreateTagElement(DESTINATION);
        tag.putString("dim", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        player.displayClientMessage(Component.translatable("rotasutils.msg.rift_key.bound",
                player.getBlockX(), player.getBlockY(), player.getBlockZ()).withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    private static Vec3 groundInFront(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        BlockPos base;
        if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
            base = hit.getBlockPos().above();
        } else {
            Vec3 ahead = eye.add(new Vec3(look.x, 0, look.z).normalize().scale(FALLBACK_DISTANCE));
            base = BlockPos.containing(ahead);
        }
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos down = base.below(dy);
            if (fits(level, down)) {
                return Vec3.atBottomCenterOf(down);
            }
            BlockPos up = base.above(dy);
            if (dy > 0 && dy <= 3 && fits(level, up)) {
                return Vec3.atBottomCenterOf(up);
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        return level.noCollision(new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 2.8, pos.getZ() + 0.8));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.rotasutils.rift." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".desc")
                .withStyle(ChatFormatting.GRAY));
        if (kind == RiftPortalEntity.Kind.TRAVEL) {
            CompoundTag destination = stack.getTagElement(DESTINATION);
            lines.add(destination == null
                    ? Component.translatable("rotasutils.tooltip.rift_key.unbound").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("rotasutils.tooltip.rift_key.bound", (int) Math.floor(destination.getDouble("x")),
                    (int) Math.floor(destination.getDouble("y")), (int) Math.floor(destination.getDouble("z")),
                    destination.getString("dim")).withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
