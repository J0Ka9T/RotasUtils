package net.schwarz.rotasutils.skill;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.server.Validation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSkillTreesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyJobTemplateHasABaseTree() {
        for (var job : JobArchetypes.all()) {
            SkillCategory tree = JobSkillTrees.create(job.id());
            assertNotNull(tree, job.id());
            assertEquals(Set.of(job.id()), tree.jobs());
            assertEquals(job.main() ? 23 : 10, tree.nodes().size(), job.id());
        }
        assertEquals(JobArchetypes.all().size(), JobSkillTrees.all().size());
    }

    @Test
    void treesAreValidAndPricedAboveTheirPointBudget() {
        RotasData data = new RotasData();
        Set<String> nodeIds = new HashSet<>();
        for (SkillCategory tree : JobSkillTrees.all()) {
            data.putCategory(tree);
        }
        for (SkillCategory tree : JobSkillTrees.all()) {
            var errors = Validation.validateCategory(data, tree, nodeIds).stream()
                    .filter(issue -> issue.severity() == Validation.Severity.ERROR).toList();
            assertTrue(errors.isEmpty(), tree.id() + " " + errors);
            long total = 0;
            for (SkillNode node : tree.nodes().values()) {
                assertTrue(!node.effects().isEmpty(), node.id() + " has no effect");
                for (int rank = 0; rank < node.maxRank(); rank++) {
                    total += node.costForRank(rank);
                }
            }
            boolean main = JobArchetypes.all().stream().filter(a -> a.id().equals(tree.jobs().iterator().next())).findFirst().get().main();
            long budget = main ? 99 : 19;
            assertTrue(total > budget && total < budget * 2, tree.id() + " costs " + total + " for a budget of " + budget);
        }
    }
}
