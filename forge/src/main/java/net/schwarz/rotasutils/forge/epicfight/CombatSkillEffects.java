package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.schwarz.rotasutils.core.CombatSkillVisuals;

import java.util.Optional;

public final class CombatSkillEffects {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("rotasutils", "combat_skill_fx"), () -> "1",
            version -> "1".equals(version) || NetworkRegistry.ABSENT.equals(version),
            version -> "1".equals(version) || NetworkRegistry.ABSENT.equals(version));
    private static boolean initialized;
    private CombatSkillEffects() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(0, Cue.class, Cue::encode, Cue::decode, (cue, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> CombatSkillEffectClient.accept(cue)));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CombatSkillEffectClient::init);
    }

    public static void emit(Entity source, Entity target, String id, int stage, float scale) {
        CombatSkillVisuals.Skill skill = CombatSkillVisuals.skill(id);
        if (skill != null) emit(source, target, id, stage, scale, CombatSkillVisuals.life(skill, stage));
    }

    public static void emit(Entity source, Entity target, String id, int stage, float scale, int duration) {
        emit(source, target, id, stage, scale, duration, 0);
    }

    public static void emit(Entity source, Entity target, String id, int stage, float scale, int duration,
                            float radius) {
        CombatSkillVisuals.Skill skill = CombatSkillVisuals.skill(id);
        if (skill == null || !(source.level() instanceof ServerLevel level) || source.level() != target.level()) return;
        Cue cue = new Cue(level.dimension().location().toString(), source.getId(), target.getId(), id,
                Math.max(-1, Math.min(6, stage)), CombatSkillVisuals.scale(scale), source.position(), target.position(),
                source.getYRot(), Math.min(4, target.getBbHeight()), CombatSkillVisuals.duration(duration),
                Math.max(0, Math.min(128, radius)));
        double reach = Math.max(48, radius + 24);
        for (var viewer : level.players()) {
            if (viewer.distanceToSqr(target) <= reach * reach && CHANNEL.isRemotePresent(viewer.connection.connection)) {
                CHANNEL.sendTo(cue, viewer.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }
    }

    public static void emitAt(Entity source, Vec3 target, float height, String id, int stage, float scale, int duration,
                              float radius) {
        CombatSkillVisuals.Skill skill = CombatSkillVisuals.skill(id);
        if (skill == null || !(source.level() instanceof ServerLevel level)) return;
        Cue cue = new Cue(level.dimension().location().toString(), source.getId(), source.getId(), id,
                Math.max(-1, Math.min(6, stage)), CombatSkillVisuals.scale(scale), source.position(), target,
                source.getYRot(), Math.max(.5f, Math.min(4, height)), CombatSkillVisuals.duration(duration),
                Math.max(0, Math.min(128, radius)));
        double reach = Math.max(48, radius + 32);
        for (var viewer : level.players()) {
            boolean near = viewer.distanceToSqr(source.position()) <= reach * reach
                    || viewer.distanceToSqr(target) <= reach * reach;
            if (near && CHANNEL.isRemotePresent(viewer.connection.connection)) {
                CHANNEL.sendTo(cue, viewer.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }
    }

    public static void clear(Entity source, String id) { emit(source, source, id, -1, 1); }

    public record Cue(String dimension, int sourceId, int targetId, String skill, int stage, float scale,
                      Vec3 source, Vec3 target, float yaw, float height, int life, float radius) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, 128).writeVarInt(sourceId).writeVarInt(targetId).writeUtf(skill, 32)
                    .writeByte(stage).writeFloat(scale);
            buf.writeDouble(source.x).writeDouble(source.y).writeDouble(source.z);
            buf.writeDouble(target.x).writeDouble(target.y).writeDouble(target.z);
            buf.writeFloat(yaw).writeFloat(height);
            buf.writeVarInt(life);
            buf.writeFloat(radius);
        }
        private static Cue decode(FriendlyByteBuf buf) {
            return new Cue(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readByte(),
                    buf.readFloat(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readFloat());
        }
    }
}
