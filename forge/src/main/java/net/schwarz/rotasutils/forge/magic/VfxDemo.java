package net.schwarz.rotasutils.forge.magic;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.schwarz.rotasutils.ability.AbilityDefinition;
import net.schwarz.rotasutils.ability.AbilityManager;
import net.schwarz.rotasutils.ability.RedReversalAbility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VfxDemo {
    public record Shot(String spell, int level, float pitch, String view, int[] ticks) {
    }

    public static int SLOT = 170;
    public static volatile List<Shot> shots = List.of();
    public static volatile long castAt = -1;
    public static volatile int current = -1;
    public static volatile boolean finished;

    private static Vec3 anchor;
    private static long startTick = -1;

    private VfxDemo() {
    }

    public static void init() {
        if (!"1".equals(System.getenv("ROTASUTILS_VFX_DEMO"))) return;
        Path file = FMLPaths.GAMEDIR.get().resolve("vfxdemo.txt");
        if (!Files.exists(file)) return;
        List<Shot> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                String[] t = p[4].split(",");
                int[] ticks = new int[t.length];
                for (int i = 0; i < t.length; i++) ticks[i] = Integer.parseInt(t[i]);
                out.add(new Shot(p[0], Integer.parseInt(p[1]), Float.parseFloat(p[2]), p[3], ticks));
            }
        } catch (Exception bad) {
            return;
        }
        shots = List.copyOf(out);
        for (Shot shot : shots) {
            SLOT = Math.max(SLOT, 30 + shot.ticks()[shot.ticks().length - 1] + 40);
        }
        MinecraftForge.EVENT_BUS.addListener(VfxDemo::onServerTick);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        long now = player.level().getGameTime();
        if (startTick < 0) {
            startTick = now + 60;
            anchor = new Vec3(Math.floor(player.getX()) + 0.5, Math.floor(player.getY()), Math.floor(player.getZ()) + 0.5);
        }
        long rel = now - startTick;
        if (rel < 0) return;
        int index = (int) (rel / SLOT);
        int phase = (int) (rel % SLOT);
        if (index >= shots.size()) {
            finished = true;
            return;
        }
        Shot shot = shots.get(index);
        CommandSourceStack src = server.createCommandSourceStack().withEntity(player).withPosition(player.position())
                .withPermission(4);
        if (phase == 0) {
            current = index;
            castAt = -1;
            run(server, src, "gamerule doDaylightCycle false");
            run(server, src, "time set noon");
            run(server, src, "weather clear");
            run(server, src, "gamemode creative");
            run(server, src, "kill @e[type=!player,distance=..80]");
            run(server, src, String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 0 %.1f", anchor.x, anchor.y, anchor.z, shot.pitch()));
            if (ability(shot) == net.schwarz.rotasutils.ability.StargunAbility.INSTANCE) {
                for (int tx = -96; tx < 96; tx += 32) {
                    for (int tz = shot.level() - 96; tz < shot.level() + 96; tz += 32) {
                        run(server, src, String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air replace minecraft:fire",
                                (int) anchor.x + tx, (int) anchor.y - 3, (int) anchor.z + tz, (int) anchor.x + tx + 31, (int) anchor.y + 6, (int) anchor.z + tz + 31));
                    }
                }
            }
            double[][] spots = ability(shot) == net.schwarz.rotasutils.ability.StargunAbility.INSTANCE ? new double[][]{{0.0, shot.level()}, {9, shot.level() - 6}, {-11, shot.level() - 14}, {16, shot.level() + 5}, {-18, shot.level() + 2}, {4, shot.level() - 30}, {-3, shot.level() - 50}}
                    : ability(shot) != null ? new double[][]{{0.0, shot.level() > 1 ? shot.level() : 40.0}} : new double[][]{{-2.2, 7.5}, {0.3, 9.0}, {2.4, 7.2}};
            for (double[] s : spots) {
                run(server, src, String.format(java.util.Locale.ROOT,
                        "summon minecraft:zombie %.2f %.2f %.2f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",Count:1b}],ActiveEffects:[{Id:12,Duration:99999,Amplifier:0b,ShowParticles:0b}]}",
                        anchor.x + s[0], anchor.y, anchor.z + s[1]));
            }
        } else if (phase == 30) {
            AbilityDefinition ability = ability(shot);
            if (ability != null) {
                run(server, src, "item replace entity @s weapon.mainhand with " + shot.spell());
                AbilityManager.start(player, ability);
            } else {
                run(server, src, "cast @s " + shot.spell() + " " + shot.level());
            }
            castAt = now;
        }
    }

    private static AbilityDefinition ability(Shot shot) {
        return switch (shot.spell()) {
            case "rotasutils:hollow_purple" -> RedReversalAbility.PURPLE;
            case "rotasutils:red_reversal" -> RedReversalAbility.INSTANCE;
            case "rotasutils:red_reversal_max" -> RedReversalAbility.MAX;
            case "rotasutils:projection_sorcery" -> net.schwarz.rotasutils.ability.ProjectionAbility.INSTANCE;
            case "rotasutils:annihilator_stargun" -> net.schwarz.rotasutils.ability.StargunAbility.INSTANCE;
            default -> null;
        };
    }

    private static void run(MinecraftServer server, CommandSourceStack src, String command) {
        server.getCommands().performPrefixedCommand(src, command);
    }
}
