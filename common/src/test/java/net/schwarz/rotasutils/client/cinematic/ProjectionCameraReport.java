package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.ProjectionStage;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;

class ProjectionCameraReport {
    private static final Vec3 START = new Vec3(0, 64, 0), TARGET = new Vec3(2, 64, 14);

    private static Vec3 forward(CameraRig.Shot s) {
        double yaw = Math.toRadians(s.yaw()), pitch = Math.toRadians(s.pitch());
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static double[] screen(CameraRig.Shot s, Vec3 p) {
        Vec3 f = forward(s), r = f.cross(new Vec3(0, 1, 0)).normalize().scale(-1), u = r.cross(f).scale(-1);
        Vec3 d = p.subtract(s.position());
        double z = d.dot(f);
        if (z < 0.05) {
            return null;
        }
        double t = Math.tan(Math.toRadians(s.fov()) / 2);
        return new double[]{d.dot(r) / z / (t * 16 / 9), d.dot(u) / z / t};
    }

    private static final java.util.Set<Integer> WATCH_TARGET = java.util.Set.of(ProjectionCamera.S_STUDY, ProjectionCamera.S_BLOW,
            ProjectionCamera.S_UNDER, ProjectionCamera.S_OVER, ProjectionCamera.S_SIDE, ProjectionCamera.S_BAR_A, ProjectionCamera.S_BAR_B,
            ProjectionCamera.S_BAR_C, ProjectionCamera.S_BAR_D, ProjectionCamera.S_BAR_E, ProjectionCamera.S_UPPER, ProjectionCamera.S_STOMP,
            ProjectionCamera.S_BAR_LAST, ProjectionCamera.S_REB_A, ProjectionCamera.S_REB_B, ProjectionCamera.S_PASS_A, ProjectionCamera.S_PASS_B,
            ProjectionCamera.S_LAPS_A, ProjectionCamera.S_LAPS_B, ProjectionCamera.S_CHARGE, ProjectionCamera.S_RING, ProjectionCamera.S_CLOSE,
            ProjectionCamera.S_SHOW_1, ProjectionCamera.S_SHOW_3);

    private static void audit(String name, ProjectionStage s, Vec3 normal, Vec3 look) throws Exception {
        Path out = Path.of("build", "projection-track-" + name + ".csv");
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("t,x,y,z,yaw,pitch,fov,sx,sy,sz");
            for (double t = 0; t <= END; t += 1.0 / 60) {
                Vec3 home = t < RESUME ? normal : s.finalFeet().add(0, 1.62, 0);
                CameraRig.Shot c = ProjectionCamera.shot(t, s, (from, to) -> to, home, look, 70);
                int shot = ProjectionCamera.shotAt(t);
                Vec3 p = c.position();
                String subject = WATCH_TARGET.contains(shot) && c.weight() > 0.99 ? String.format("%.3f,%.3f,%.3f", s.centreOf(s.targetAt(t)).x, s.centreOf(s.targetAt(t)).y, s.centreOf(s.targetAt(t)).z) : ",,";
                w.printf("%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,%s%n", t, p.x, p.y, p.z, c.yaw(), c.pitch(), c.fov(), subject);
            }
        }
        StringBuilder cuts = new StringBuilder();
        for (int i = 0; i < ProjectionCamera.shots(); i++) {
            cuts.append(i == 0 ? "" : ",").append(String.format("%.3f", ProjectionCamera.startOf(i)));
        }
        Files.writeString(Path.of("build", "projection-cuts.txt"), cuts.toString());
    }

    @Test
    void report() throws Exception {
        String[] names = {"open", "wall", "giant"};
        ProjectionStage[] stages = {new ProjectionStage(START, TARGET, 0.6, 1.95, (f, d, m) -> m),
                new ProjectionStage(START, TARGET, 0.6, 1.95, (f, d, m) -> {
                    Vec3 n = new Vec3(TARGET.x - START.x, 0, TARGET.z - START.z).normalize();
                    double closing = d.dot(n), gap = 0.8 - f.subtract(TARGET).dot(n);
                    return closing > 1e-6 && gap / closing < m ? Math.max(0, gap / closing) : m;
                }), new ProjectionStage(START, TARGET, 6.0, 8.0, (f, d, m) -> m)};
        Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
        for (int k = 0; k < stages.length; k++) {
            ProjectionStage s = stages[k];
            audit(names[k], s, normal, look);
            Path out = Path.of("build", "projection-camera-" + names[k] + ".csv");
            Files.createDirectories(out.getParent());
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
                w.println("t,shot,dist_target,dist_attacker,tx,ty,ax,ay,turn_deg_s,speed_m_s,fov");
                CameraRig.Shot prev = null;
                for (double t = 0; t < END; t += 0.05) {
                    Vec3 home = t < RESUME ? normal : s.finalFeet().add(0, 1.62, 0);
                    CameraRig.Shot c = ProjectionCamera.shot(t, s, (from, to) -> to, home, look, 70);
                    Vec3 tc = s.centreOf(s.targetAt(t)), at = s.attackerAt(t).add(0, 1.0, 0);
                    double[] ts = screen(c, tc), as = screen(c, at);
                    double turn = 0, speed = 0;
                    if (prev != null) {
                        double dy = ((c.yaw() - prev.yaw() + 540) % 360) - 180;
                        turn = Math.hypot(dy, c.pitch() - prev.pitch()) / 0.05;
                        speed = c.position().distanceTo(prev.position()) / 0.05;
                    }
                    w.printf("%.2f,%d,%.2f,%.2f,%s,%s,%s,%s,%.0f,%.1f,%.0f%n", t, ProjectionCamera.shotAt(t), c.position().distanceTo(tc),
                            c.position().distanceTo(at), ts == null ? "" : String.format("%.2f", ts[0]), ts == null ? "" : String.format("%.2f", ts[1]),
                            as == null || !s.visible(t) ? "" : String.format("%.2f", as[0]), as == null || !s.visible(t) ? "" : String.format("%.2f", as[1]),
                            turn, speed, c.fov());
                    prev = c;
                }
            }
        }
    }
}
