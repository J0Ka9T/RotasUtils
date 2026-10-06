package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.ProjectionStage;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;

class ProjectionPoseReport {
    @Test
    void report() throws Exception {
        ProjectionStage s = new ProjectionStage(new Vec3(0, 64, 0), new Vec3(2, 64, 14), 0.6, 1.95, (f, d, m) -> m);
        Path out = Path.of("build", "projection-pose.csv");
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("t,rArmX,lArmX,rLegX,lLegX,bodyYaw,bodyPitch,dy,x,z");
            for (double t = 0; t < END; t += 1.0 / 60) {
                RedPose.Pose p = ProjectionPose.sample(t, s);
                Vec3 a = s.attackerAt(t);
                w.printf("%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f%n", t, p.rArmX(), p.lArmX(), p.rLegX(), p.lLegX(), p.bodyYaw(), p.bodyPitch(), p.dy(), a.x, a.z);
            }
        }
    }
}
