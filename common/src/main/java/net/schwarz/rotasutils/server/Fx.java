package net.schwarz.rotasutils.server;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

public final class Fx {
    private Fx() {
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
    }

    public static void spiral(Entity entity, ParticleOptions particle, int points, double radius, double height) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            for (int arm = 0; arm < 2; arm++) {
                double angle = t * Math.PI * 4 + arm * Math.PI;
                double r = radius * (1 - t * 0.35);
                level.sendParticles(particle, entity.getX() + Math.cos(angle) * r, entity.getY() + t * height,
                        entity.getZ() + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    public static void ring(Entity entity, ParticleOptions particle, int points, double radius) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, entity.getX() + Math.cos(angle) * radius, entity.getY() + 0.1,
                    entity.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void fountain(Entity entity, ParticleOptions particle, int count) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(particle, entity.getX(), entity.getY() + 1.1, entity.getZ(), count, 0.25, 0.35, 0.25, 0.12);
    }

    public static void service(Entity player, String service) {
        switch (service) {
            case "repair_hand", "repair_all" -> fountain(player, ParticleTypes.CRIT, 18);
            case "disenchant" -> spiral(player, ParticleTypes.ENCHANT, 24, 1.1, 2.0);
            case "brew" -> fountain(player, ParticleTypes.EFFECT, 20);
            case "rest" -> {
                fountain(player, ParticleTypes.HEART, 6);
                ring(player, dust(0xFFE9B0, 1.0f), 20, 0.9);
            }
            case "home" -> ring(player, ParticleTypes.PORTAL, 28, 1.0);
            case "pray", "bless" -> spiral(player, dust(0xFFF1B8, 1.2f), 26, 0.9, 2.4);
            case "cleanse" -> {
                spiral(player, ParticleTypes.SPLASH, 20, 0.8, 2.2);
                fountain(player, ParticleTypes.END_ROD, 8);
            }
            case "lift_curse" -> {
                fountain(player, ParticleTypes.SOUL, 12);
                ring(player, dust(0xB9F6FF, 1.1f), 24, 1.1);
            }
            case "fortune" -> spiral(player, ParticleTypes.WITCH, 26, 1.0, 2.2);
            case "deposit", "withdraw", "sell" -> fountain(player, dust(0xFFD34D, 0.9f), 14);
            case "bounty_turnin" -> {
                fountain(player, ParticleTypes.TOTEM_OF_UNDYING, 30);
                ring(player, dust(0xFFB347, 1.2f), 28, 1.2);
            }
            case "bounty_take" -> ring(player, dust(0xD84A4A, 1.0f), 20, 0.8);
            default -> {
            }
        }
    }
}
