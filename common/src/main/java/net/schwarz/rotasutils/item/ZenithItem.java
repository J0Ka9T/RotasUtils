package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.ZenithBladeEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ZenithItem extends SwordItem {
    public static final int FIRE_INTERVAL = 1;
    public static final double REACH = 64.0;
    private static final double MIN_REACH = 5.0;
    public static final int EXCALIBUR_COOLDOWN = 120;

    private static List<Item> swords;

    public ZenithItem(Properties properties) {
        super(Tiers.NETHERITE, 36, -1.6f, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel server) {
                net.schwarz.rotasutils.entity.ExcaliburSlashEntity.unleash(server, player);
                player.getCooldowns().addCooldown(this, EXCALIBUR_COOLDOWN);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int held = getUseDuration(stack) - remaining;
        if (held % FIRE_INTERVAL != 0) {
            return;
        }
        Vec3 target = aim(user);
        int shot = held / FIRE_INTERVAL;
        float damage = (float) (user.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? user.getAttributeValue(Attributes.ATTACK_DAMAGE) : getDamage() + 1f);
        ZenithBladeEntity.loose(server, user, target, bladeFor(shot, user.getRandom().nextInt()), damage * 0.75f);
        user.swing(user.getUsedItemHand(), true);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.3f, 1.5f + user.getRandom().nextFloat() * 0.4f);
        if (shot % 6 == 0) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.9f, 1.2f + user.getRandom().nextFloat() * 0.5f);
        }
    }

    static Vec3 aim(LivingEntity user) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getViewVector(1f);
        Vec3 far = eye.add(look.scale(REACH));
        Level level = user.level();
        HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        AABB sweep = user.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, user, eye, end, sweep,
                e -> e.isPickable() && !e.isSpectator() && e instanceof LivingEntity, 0.3f);
        Vec3 target = hit != null ? hit.getEntity().getBoundingBox().getCenter() : end;
        if (target.distanceTo(eye) < MIN_REACH) {
            target = eye.add(look.scale(MIN_REACH));
        }
        return target;
    }

    public static ItemStack bladeFor(int shot, int salt) {
        List<Item> all = swords();
        if (all.isEmpty()) {
            return new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        }
        int start = Math.floorMod(salt, all.size());
        return new ItemStack(all.get(Math.floorMod(start + shot * 7, all.size())));
    }

    private static List<Item> swords() {
        if (swords == null) {
            List<Item> found = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof SwordItem && !(item instanceof ZenithItem)) {
                    found.add(item);
                }
            }
            swords = List.copyOf(found);
        }
        return swords;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rotasutils.zenith.desc").withStyle(ChatFormatting.LIGHT_PURPLE,
                ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.rotasutils.zenith.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rotasutils.zenith.excalibur").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
