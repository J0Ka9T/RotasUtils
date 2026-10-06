package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.title.TitleCounters;
import net.schwarz.rotasutils.title.TitleDef;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleDefTest {
    @Test void aTitleRoundTripsThroughNbtWithItsConditionAndBonus() {
        TitleDef title = new TitleDef("rotas:dragon_slayer", "ผู้ล่ามังกร");
        title.setDescription("สังหารเอนเดอร์ดราก้อน");
        title.setCondition(TitleDef.Condition.KILL_ENTITY);
        title.setTarget("minecraft:ender_dragon");
        title.setAmount(1);
        title.setUnique(true);
        title.setColor(0x00B07CE8);
        title.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 0.02,
                CharacterStat.Operation.MULTIPLY_BASE, true, "พลังโจมตี", 0));

        TitleDef loaded = TitleDef.load(title.save());
        assertEquals("rotas:dragon_slayer", loaded.id());
        assertEquals("ผู้ล่ามังกร", loaded.name());
        assertSame(TitleDef.Condition.KILL_ENTITY, loaded.condition());
        assertEquals("minecraft:ender_dragon", loaded.target());
        assertTrue(loaded.unique());
        assertEquals(0xFFB07CE8, loaded.color(), "a stored colour is always opaque");
        assertEquals(1, loaded.effects().size());
        assertEquals(0.02, loaded.effects().get(0).perPoint(), 1e-9);
    }

    @Test void aTitleNobodyCanEarnByPlayingIsNeverAwardedOnItsOwn() {
        TitleDef manual = new TitleDef("rotas:staff", "ทีมงาน");
        assertSame(TitleDef.Condition.MANUAL, manual.condition(), "a title defaults to admin-only");
        assertFalse(manual.met(Long.MAX_VALUE), "a manual title is never met by progress");
        assertEquals("", manual.counterKey());
    }

    @Test void eachConditionNamesTheTallyItReads() {
        TitleDef kills = new TitleDef("rotas:hunter", "นักล่า");
        kills.setCondition(TitleDef.Condition.KILL_ANY);
        assertEquals(TitleCounters.KILL_ANY, kills.counterKey());

        TitleDef entity = new TitleDef("rotas:wither_bane", "ผู้สยบวิเธอร์");
        entity.setCondition(TitleDef.Condition.KILL_ENTITY);
        entity.setTarget("minecraft:wither");
        assertEquals(TitleCounters.KILL_PREFIX + "minecraft:wither", entity.counterKey());

        TitleDef smith = new TitleDef("rotas:smith", "ช่างตีเหล็ก");
        smith.setCondition(TitleDef.Condition.REFINE);
        assertEquals(TitleCounters.REFINE_BEST, smith.counterKey());

        TitleDef level = new TitleDef("rotas:veteran", "ผู้ช่ำชอง");
        level.setCondition(TitleDef.Condition.LEVEL);
        assertEquals("", level.counterKey(), "a level needs no tally of its own");
    }

    @Test void aTalliedConditionIsMetOnceTheCountReachesTheAmount() {
        TitleDef title = new TitleDef("rotas:hunter", "นักล่า");
        title.setCondition(TitleDef.Condition.KILL_ANY);
        title.setAmount(1000);
        assertFalse(title.met(999));
        assertTrue(title.met(1000));
        assertTrue(title.met(5000));
    }

    @Test void talliesSurviveGarbageAndOnlyEverClimb() {
        Map<String, String> variables = new HashMap<>();
        assertEquals(0, TitleCounters.read(variables, TitleCounters.KILL_ANY));
        assertEquals(3, TitleCounters.add(variables, TitleCounters.KILL_ANY, 3));
        assertEquals(4, TitleCounters.add(variables, TitleCounters.KILL_ANY, 1));

        variables.put(TitleCounters.REFINE_BEST, "not a number");
        assertEquals(0, TitleCounters.read(variables, TitleCounters.REFINE_BEST));
        assertEquals(7, TitleCounters.raise(variables, TitleCounters.REFINE_BEST, 7));
        assertEquals(7, TitleCounters.raise(variables, TitleCounters.REFINE_BEST, 5),
                "a worse result never lowers the best ever reached");
        assertEquals(10, TitleCounters.raise(variables, TitleCounters.REFINE_BEST, 10));
    }

    @Test void theStarterSetHasEarnableTitlesAndExactlyTheIntendedUniqueOnes() {
        var defaults = net.schwarz.rotasutils.server.TitleService.defaults();
        assertTrue(defaults.size() >= 10, "a new world starts with something to chase");
        long unique = defaults.stream().filter(TitleDef::unique).count();
        assertEquals(2, unique, "only the first to 100 and the first +10 are unique");
        for (TitleDef title : defaults) {
            assertFalse(title.name().isBlank(), title.id() + " needs a name");
            assertFalse(title.description().isBlank(), title.id() + " needs to say how it is earned");
            assertTrue(title.amount() >= 1, title.id() + " needs a target to reach");
            if (title.condition() == TitleDef.Condition.KILL_ENTITY) {
                assertFalse(title.target().isBlank(), title.id() + " must name the entity it counts");
            }
        }
    }

    @Test void aStoredTitleWithNoFieldsAtAllStillLoads() {
        TitleDef loaded = TitleDef.load(saveWithIdOnly());
        assertEquals("rotas:x", loaded.id());
        assertEquals("rotas:x", loaded.name(), "a title with no name falls back to its id");
        assertTrue(loaded.enabled());
        assertSame(TitleDef.Condition.MANUAL, loaded.condition());
    }

    private static CompoundTag saveWithIdOnly() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "rotas:x");
        return tag;
    }
}
