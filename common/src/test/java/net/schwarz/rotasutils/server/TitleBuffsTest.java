package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.title.TitleDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TitleBuffsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<TitleDef> shipped() {
        List<TitleDef> all = new ArrayList<>(TitleService.defaults());
        all.addAll(TitleService.nemesisTitles());
        all.addAll(TitleService.expansionTitles());
        all.addAll(TitleService.roleTitles());
        return all;
    }

    @Test
    void everyShippedTitleCarriesABonusAndRarerOnesCarryTwo() {
        for (TitleDef title : shipped()) {
            assertFalse(title.effects().isEmpty(), title.id() + " has no bonus");
            if (title.rarity().ordinal() >= TitleDef.Rarity.RARE.ordinal()) {
                assertTrue(title.effects().size() >= 2, title.id() + " is " + title.rarity() + " with one bonus");
            }
            assertTrue(title.effects().size() <= TitleDef.MAX_EFFECTS, title.id());
        }
    }

    @Test
    void upgradingKeepsRarityAndRaisesExistingBonuses() {
        TitleDef plain = new TitleDef("test:plain", "Plain");
        plain.setCondition(TitleDef.Condition.KILL_ANY);
        TitleBuffs.upgrade(plain);
        assertEquals(TitleDef.Rarity.COMMON, plain.rarity(), "a bonus must not make a common title rare");
        assertEquals(1, plain.effects().size());

        TitleDef strong = new TitleDef("test:strong", "Strong");
        strong.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 0.04,
                CharacterStat.Operation.MULTIPLY_BASE, true, "x", 0));
        TitleBuffs.upgrade(strong);
        assertEquals(0.06, strong.effects().get(0).perPoint(), 1e-9);
        assertEquals(TitleDef.Rarity.RARE, strong.rarity());
    }

    @Test
    void dragonSlayerNeedsASaintsDragonNotTheEnderDragon() {
        TitleDef slayer = TitleService.defaults().stream().filter(t -> t.id().equals("rotas:dragon_slayer")).findFirst().orElseThrow();
        assertTrue(slayer.matchesEntity("saintsdragons:raevyx"));
        assertTrue(slayer.matchesEntity("saintsdragons:nulljaw"));
        assertFalse(slayer.matchesEntity("minecraft:ender_dragon"));
        assertFalse(slayer.matchesEntity("saintsdragons:moop"));
        assertFalse(slayer.matchesEntity("saintsdragons:ivy_oleander"));
        TitleDef bane = TitleService.defaults().stream().filter(t -> t.id().equals("rotas:ender_dragon_bane")).findFirst().orElseThrow();
        assertTrue(bane.matchesEntity("minecraft:ender_dragon"));
        assertNotEquals(slayer.counterKey(), bane.counterKey());
        RotasData data = new RotasData();
        data.putTitle(slayer);
        assertTrue(TitleService.watchedEntities(data).contains("saintsdragons:volitans"));
    }

    @Test
    void namespacePatternMatchesEveryEntityOfAMod() {
        TitleDef any = new TitleDef("test:any", "Any");
        any.setCondition(TitleDef.Condition.KILL_ENTITY);
        any.setTarget("mymod:*");
        assertTrue(any.matchesEntity("mymod:thing"));
        assertFalse(any.matchesEntity("othermod:thing"));
    }

    @Test
    void migrationRetargetsTheOldDragonTitleAndRaisesOnlyUntouchedBonuses() {
        RotasData data = new RotasData();
        data.markTitlesSeeded();
        TitleDef oldDragon = new TitleDef("rotas:dragon_slayer", "ผู้ล่ามังกร");
        oldDragon.setCondition(TitleDef.Condition.KILL_ENTITY);
        oldDragon.setTarget("minecraft:ender_dragon");
        data.putTitle(oldDragon);
        TitleDef tuned = new TitleDef("rotas:novice", "มือใหม่หัดเดิน");
        tuned.setCondition(TitleDef.Condition.LEVEL);
        tuned.setAmount(5);
        tuned.addEffect(new CharacterStat.Effect("minecraft:generic.luck", 7, CharacterStat.Operation.ADD, false, "โชค", 0));
        data.putTitle(tuned);

        TitleService.seedDefaults(data);

        TitleDef dragon = data.title("rotas:dragon_slayer");
        assertEquals(TitleService.SAINTS_DRAGONS, dragon.target());
        assertFalse(dragon.effects().isEmpty());
        assertNotNull(data.title("rotas:ender_dragon_bane"));
        assertEquals(1, data.title("rotas:novice").effects().size());
        assertEquals(7, data.title("rotas:novice").effects().get(0).perPoint(), 1e-9);

        int effects = dragon.effects().size();
        TitleService.seedDefaults(data);
        assertEquals(effects, data.title("rotas:dragon_slayer").effects().size());
    }
}
