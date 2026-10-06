package net.schwarz.rotasutils.ability;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.item.RedReversalItem;

public final class StargunAbility implements AbilityDefinition {
    public static final StargunAbility INSTANCE = new StargunAbility();
    public static final ResourceLocation ID = Rotasutils.id("annihilator_stargun");

    private final Timeline<AbilityContext> timeline = new Timeline<AbilityContext>()
            .at(0, "begin", StargunAbility::begin);

    private StargunAbility() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int cooldownTicks() {
        return StargunTimings.COOLDOWN_TICKS;
    }

    @Override
    public int durationTicks() {
        return StargunTimings.END_TICKS;
    }

    public static final String OWNER = "SchwarzX2";

    @Override
    public boolean canStart(ServerPlayer player) {
        if (!player.getGameProfile().getName().equalsIgnoreCase(OWNER)) {
            player.displayClientMessage(Component.translatable("rotasutils.stargun.denied").withStyle(ChatFormatting.RED), true);
            return false;
        }
        return player.getMainHandItem().getItem() instanceof RedReversalItem item && item.ability() == this;
    }

    @Override
    public Target retarget(ServerPlayer player, Target aimed) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 toAim = new Vec3(aimed.position().x - eye.x, 0, aimed.position().z - eye.z);
        Vec3 dir = toAim.lengthSqr() < 1.0e-4 ? flat : toAim.normalize();
        double distance = Mth.clamp(toAim.length(), StargunTimings.MIN_RANGE, StargunTimings.MAX_RANGE);
        double x = eye.x + dir.x * distance, z = eye.z + dir.z * distance;
        return new Target(-1, new Vec3(x, ground(player.serverLevel(), x, z, eye.y - 1.62), z), eye);
    }

    private static double ground(ServerLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        return level.hasChunk(bx >> 4, bz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : fallback;
    }

    @Override
    public Timeline<AbilityContext> timeline() {
        return timeline;
    }

    private static void begin(AbilityContext c) {
        c.player.setInvulnerable(true);
        Vec3 at = c.target.position();
        c.state = new StargunDissolver(c.level, BlockPos.containing(at.x, at.y, at.z), c.player, c.seed);
    }

    @Override
    public boolean tick(AbilityContext c) {
        if (c.state instanceof StargunDissolver dissolver) {
            dissolver.tick(c.seconds());
        }
        return true;
    }

    @Override
    public void end(AbilityContext context, boolean completed) {
        context.player.setInvulnerable(false);
        if (context.state instanceof StargunDissolver dissolver) {
            dissolver.finish(completed);
        }
    }
}
