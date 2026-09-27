package net.schwarz.rotasutils.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CeroDamageBatchTest {
    @Test
    void repeatedContributionsCollapseIntoOneTargetTotal() {
        CeroDamageBatch<String> batch = new CeroDamageBatch<>();
        batch.add("target", 5f);
        batch.add("target", 3f);
        assertEquals(1, batch.size());
        assertEquals(8f, batch.damageFor("target"), 1.0e-6f);
    }

    @Test
    void directAndSplashContributionsCanBeFoldedBeforeApplication() {
        CeroDamageBatch<String> batch = new CeroDamageBatch<>();
        batch.add("boss", 15f);
        batch.add("boss", 7f);
        batch.add("other", 4f);
        assertEquals(22f, batch.damageFor("boss"), 1.0e-6f);
        assertEquals(4f, batch.damageFor("other"), 1.0e-6f);
        batch.clear();
        assertEquals(0, batch.size());
    }
}
