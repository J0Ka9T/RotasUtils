package net.schwarz.rotasutils.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.item.RedReversalItem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ProjectionAbility implements AbilityDefinition {
    public static final ProjectionAbility INSTANCE = new ProjectionAbility();
    public static final ResourceLocation ID = Rotasutils.id("projection_sorcery");

    private static final class Hold {
        LivingEntity target;
        Vec3 at;
        float yaw, body, head;
        boolean hadNoAi;
        boolean wasInvulnerable;
        ProjectionStage stage;
        boolean released;
    }

    private static final Set<UUID> HELD = new HashSet<>();

    private final Timeline<AbilityContext> timeline = new Timeline<AbilityContext>()
            .at(0, "begin", ProjectionAbility::begin)
            .at(ProjectionTimings.RESUME_REAL, "resume", ProjectionAbility::resume);

    private ProjectionAbility() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int cooldownTicks() {
        return RedTimings.COOLDOWN_TICKS;
    }

    @Override
    public int durationTicks() {
        return ProjectionTimings.END_TICKS;
    }

    @Override
    public boolean canStart(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof RedReversalItem item && item.ability() == this;
    }

    @Override
    public boolean accepts(ServerPlayer player, Target target) {
        if (!target.isEntity() || !(player.serverLevel().getEntity(target.entityId()) instanceof LivingEntity victim)) {
            return false;
        }
        double distance = victim.distanceTo(player);
        return victim.isAlive() && !HELD.contains(victim.getUUID())
                && distance >= ProjectionTimings.MIN_RANGE && distance <= ProjectionTimings.RANGE;
    }

    @Override
    public Timeline<AbilityContext> timeline() {
        return timeline;
    }

    private static void begin(AbilityContext c) {
        if (!(c.level.getEntity(c.target.entityId()) instanceof LivingEntity victim)) {
            return;
        }
        ServerPlayer player = c.player;
        player.setInvulnerable(true);
        Vec3 to = victim.position().subtract(player.position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * 180.0 / Math.PI) - 90f;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, 0f);
        player.yBodyRot = yaw;
        player.yHeadRot = yaw;

        Hold hold = new Hold();
        hold.target = victim;
        hold.at = victim.position();
        hold.yaw = victim.getYRot();
        hold.body = victim.yBodyRot;
        hold.head = victim.yHeadRot;
        hold.wasInvulnerable = victim.isInvulnerable();
        hold.stage = ProjectionStage.of(player.position(), hold.at, victim, player);
        if (victim instanceof Mob mob) {
            hold.hadNoAi = mob.isNoAi();
            mob.setNoAi(true);
        }
        victim.setInvulnerable(true);
        HELD.add(victim.getUUID());
        c.state = hold;
    }

    @Override
    public boolean tick(AbilityContext c) {
        if (!(c.state instanceof Hold hold)) {
            return c.seconds() < 0.1;
        }
        if (hold.released) {
            return true;
        }
        LivingEntity victim = hold.target;
        if (!victim.isAlive() || victim.isRemoved() || victim.level() != c.level) {
            return false;
        }
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0;
        if (victim.position().distanceToSqr(hold.at) > 1.0e-4) {
            victim.teleportTo(hold.at.x, hold.at.y, hold.at.z);
        }
        victim.setYRot(hold.yaw);
        victim.yBodyRot = hold.body;
        victim.yHeadRot = hold.head;
        return true;
    }

    private static void release(Hold hold) {
        if (hold.released) {
            return;
        }
        hold.released = true;
        HELD.remove(hold.target.getUUID());
        hold.target.setInvulnerable(hold.wasInvulnerable);
        if (hold.target instanceof Mob mob) {
            mob.setNoAi(hold.hadNoAi);
        }
    }

    private static void resume(AbilityContext c) {
        if (!(c.state instanceof Hold hold) || hold.released) {
            return;
        }
        release(hold);
        ServerPlayer player = c.player;
        LivingEntity victim = hold.target;
        ProjectionStage stage = hold.stage;
        Vec3 away = stage.away.scale(-1);

        Vec3 feet = stage.finalFeet();
        if (c.level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) {
            player.connection.teleport(feet.x, feet.y, feet.z, (float) ProjectionStage.yawOf(feet.subtract(stage.centre)), 0f);
        }
        Vec3 rest = stage.finalTarget();
        if (c.level.noCollision(victim, victim.getBoundingBox().move(rest.subtract(victim.position())))) {
            victim.teleportTo(rest.x, rest.y, rest.z);
        }
        victim.invulnerableTime = 0;
        victim.hurt(c.level.damageSources().playerAttack(player), ProjectionTimings.DAMAGE);
        double resist = Math.max(0, 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        victim.setDeltaMovement(away.scale(1.7 * resist).add(0, 0.42 * resist, 0));
        victim.hurtMarked = true;
    }

    @Override
    public void end(AbilityContext context, boolean completed) {
        context.player.setInvulnerable(false);
        if (context.state instanceof Hold hold) {
            release(hold);
        }
    }
}
