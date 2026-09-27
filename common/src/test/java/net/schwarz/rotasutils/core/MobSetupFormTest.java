package net.schwarz.rotasutils.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobSetupFormTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void newSetupIsAValidMonsterProfile() {
        MobSetupForm form = MobSetupForm.create("minecraft:zombie");
        assertEquals(List.of("minecraft:zombie"), form.entities());
        assertEquals("NEAREST_PLAYER", form.strategy());
        assertEquals(List.of(MobSetupForm.NORMAL_TIER), form.tierIds());
        assertDoesNotThrow(() -> MonsterDefinitions.profile(new ContentId("rotas:monster/zombie"),
                JsonParser.parseString(form.json()).getAsJsonObject()));
    }

    @Test
    void standardTiersAreValidTierDefinitions() {
        for (String id : List.of(MobSetupForm.NORMAL_TIER, MobSetupForm.ELITE_TIER)) {
            var document = MobSetupForm.tierDefinition(id);
            assertNotNull(document);
            assertDoesNotThrow(() -> MonsterDefinitions.tier(new ContentId(id), document.getAsJsonObject("body")));
        }
        assertNull(MobSetupForm.tierDefinition("rotas:tier/unknown"));
    }

    @Test
    void parseKeepsFieldsTheEasyEditorDoesNotShow() {
        MobSetupForm form = MobSetupForm.parse("{\"priority\":7,\"selector\":{\"entities\":[\"minecraft:husk\"]}}");
        form.setBaseXp(40);
        var body = form.body();
        assertEquals(7, body.get("priority").getAsInt());
        assertEquals(40, body.get("base_xp").getAsLong());
        assertEquals(List.of("minecraft:husk"), form.entities());
    }

    @Test
    void eliteChanceMapsToTierWeights() {
        MobSetupForm form = MobSetupForm.create("minecraft:zombie");
        form.setEliteChance(25);
        assertEquals(25, form.eliteChance());
        assertEquals(Set.of(MobSetupForm.NORMAL_TIER, MobSetupForm.ELITE_TIER), Set.copyOf(form.tierIds()));
        form.setEliteChance(500);
        assertEquals(90, form.eliteChance());
        form.setEliteChance(0);
        assertEquals(List.of(MobSetupForm.NORMAL_TIER), form.tierIds());
    }

    @Test
    void customTiersAreLeftAlone() {
        MobSetupForm form = MobSetupForm.parse("{\"tiers\":{\"pack:tier/legend\":1}}");
        assertTrue(form.customTiers());
        form.setEliteChance(50);
        assertEquals(List.of("pack:tier/legend"), form.tierIds());
    }

    @Test
    void fixedLevelSetsEveryBound() {
        MobSetupForm form = MobSetupForm.create("minecraft:zombie");
        form.setFixed(12);
        assertEquals("FIXED", form.strategy());
        assertEquals(12, form.min());
        assertEquals(12, form.max());
        assertEquals(12, form.fixedLevel());
    }

    @Test
    void neutralScaleRemovesTheAttribute() {
        MobSetupForm form = MobSetupForm.create("minecraft:zombie");
        form.setScale(MobSetupForm.HEALTH, 2, 0.1, 0);
        assertEquals(60, form.valueAt(MobSetupForm.HEALTH, 20, 11), 1e-9);
        form.setScale(MobSetupForm.HEALTH, 1, 0, 0);
        assertFalse(form.body().has("attributes"));
        assertEquals(20, form.valueAt(MobSetupForm.HEALTH, 20, 50), 1e-9);
    }

    @Test
    void newIdsAreReadableAndUnique() {
        assertEquals("rotas:monster/zombie", MobSetupForm.newId("minecraft:zombie", Set.of()));
        assertEquals("rotas:monster/zombie_2", MobSetupForm.newId("minecraft:zombie", Set.of("rotas:monster/zombie")));
    }
}
