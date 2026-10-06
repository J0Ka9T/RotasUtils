package net.schwarz.rotasutils.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.title.TitleCounters;
import net.schwarz.rotasutils.title.TitleDef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleTitlesTest {
    private static JsonObject lang(String file) throws IOException {
        try (var in = RoleTitlesTest.class.getResourceAsStream("/assets/rotasutils/lang/" + file)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void thereAreTwentyOrMoreNewTitlesWithUniqueIds() {
        List<TitleDef> titles = TitleService.roleTitles();
        assertTrue(titles.size() >= 20, "asked for 20+: " + titles.size());
        Set<String> ids = new HashSet<>();
        for (TitleDef title : titles) {
            assertTrue(ids.add(title.id()), "duplicate " + title.id());
        }
        Set<String> existing = new HashSet<>();
        TitleService.defaults().forEach(t -> existing.add(t.id()));
        TitleService.nemesisTitles().forEach(t -> existing.add(t.id()));
        TitleService.expansionTitles().forEach(t -> existing.add(t.id()));
        for (TitleDef title : titles) {
            assertFalse(existing.contains(title.id()), "clashes with " + title.id());
        }
    }

    @Test void everyTitleIsReadableAndEarnable() {
        for (TitleDef title : TitleService.roleTitles()) {
            assertFalse(title.name().isBlank(), title.id());
            assertFalse(title.description().isBlank(), title.id());
            assertTrue(title.amount() > 0, title.id());
            assertFalse(title.unique(), "role titles are for everyone: " + title.id());
            if (title.condition() == TitleDef.Condition.SUB_LEVEL || title.condition() == TitleDef.Condition.STAT) {
                assertFalse(title.target().isBlank(), title.id() + " needs a target");
            }
            if (title.condition() == TitleDef.Condition.STAT) {
                assertEquals(TitleCounters.statKey(title.target()), title.counterKey());
            }
        }
    }

    @Test void everySubJobTitleNamesARealJob() {
        Set<String> jobs = new HashSet<>();
        JobArchetypes.all().forEach(a -> jobs.add(a.id()));
        long ranked = TitleService.roleTitles().stream().filter(t -> t.condition() == TitleDef.Condition.SUB_LEVEL).count();
        assertEquals(14, ranked, "two ranks for each of seven roles");
        for (TitleDef title : TitleService.roleTitles()) {
            if (title.condition() == TitleDef.Condition.SUB_LEVEL) {
                assertTrue(jobs.contains(title.target()), title.id() + " names " + title.target());
            }
        }
    }

    @Test void everyCounterATitleWatchesHasWordsInBothLanguages() throws IOException {
        JsonObject en = lang("en_us.json"), th = lang("th_th.json");
        for (TitleDef title : TitleService.roleTitles()) {
            if (title.condition() == TitleDef.Condition.STAT) {
                String key = "rotasutils.title.how.stat." + title.target().toLowerCase(Locale.ROOT).replace('.', '_');
                assertTrue(en.has(key), key);
                assertTrue(th.has(key), key);
            }
        }
        assertTrue(en.has("rotasutils.title.how.sub_level") && th.has("rotasutils.title.how.sub_level"));
    }

    @Test void rarerTitlesPayMore() {
        double last = 0;
        for (TitleDef.Rarity rarity : TitleDef.Rarity.values()) {
            double scale = CharacterStatService.rarityScale(rarity);
            assertTrue(scale > last, rarity.name());
            last = scale;
        }
        assertEquals(300, CharacterStatService.BASE_HEALTH);
    }

    @Test void masterRanksCostMoreThanProRanksAndAreRarer() {
        List<TitleDef> titles = TitleService.roleTitles();
        for (String job : List.of("farmer", "miner", "fisher", "rancher", "chef", "alchemy", "blacksmith")) {
            TitleDef pro = titles.stream().filter(t -> t.id().equals("rotas:" + job + "_pro")).findFirst().orElseThrow();
            TitleDef master = titles.stream().filter(t -> t.id().equals("rotas:" + job + "_master")).findFirst().orElseThrow();
            assertTrue(master.amount() > pro.amount(), job);
            assertTrue(master.rarity().ordinal() > pro.rarity().ordinal(), job);
        }
    }
}
