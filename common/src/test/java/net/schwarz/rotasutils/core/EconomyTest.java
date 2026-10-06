package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.server.SettingsPanels;
import net.schwarz.rotasutils.server.TradeConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EconomyTest {
    @Test void anItemIsWorthItsPriceAndStarsAndTheSellRateScaleIt() {
        WorthTable table = WorthTable.empty().with("minecraft:diamond", 100).withSettings(40, 150, 300);
        assertEquals(100, table.worth("minecraft:diamond", t -> false, 0));
        assertEquals(150, table.worth("minecraft:diamond", t -> false, 1));
        assertEquals(300, table.worth("minecraft:diamond", t -> false, 2));
        assertEquals(40 * 3, table.sellValue("minecraft:diamond", t -> false, 0, 3), "40% of 100, three of them");
        assertEquals(0, table.worth("minecraft:dirt", t -> false, 0), "no price means worthless");
        assertEquals(0, table.sellValue("minecraft:dirt", t -> false, 0, 64));
    }

    @Test void anExactPriceBeatsATagAndTheDearestTagWins() {
        WorthTable table = WorthTable.empty().with("minecraft:iron_ingot", 10).with("#forge:ingots", 3).with("#forge:metals", 5);
        assertEquals(10, table.base("minecraft:iron_ingot", t -> true));
        assertEquals(5, table.base("create:zinc_ingot", t -> true), "in both tags: the higher");
        assertEquals(3, table.base("create:zinc_ingot", t -> t.equals("forge:ingots")));
        assertEquals(0, table.base("create:zinc_ingot", t -> false));
    }

    @Test void pricesSurviveJsonAndBadOnesAreRefused() {
        WorthTable table = WorthTable.empty().with("minecraft:coal", 2).with("#forge:gems", 9).withSettings(60, 200, 400);
        assertEquals(table, WorthTable.parse(table.toJson()));
        assertThrows(IllegalArgumentException.class, () -> WorthTable.empty().with("coal", 1));
        assertThrows(IllegalArgumentException.class, () -> WorthTable.empty().withSettings(50, 150, 120), "gold below silver");
        assertEquals(WorthTable.MAX_PRICE, WorthTable.empty().with("minecraft:coal", WorthTable.MAX_PRICE * 5).prices().get("minecraft:coal"), "a price is capped, not refused");
        assertTrue(WorthTable.empty().with("minecraft:coal", 5).with("minecraft:coal", 0).prices().isEmpty(), "0 removes the price");
    }

    @Test void theShippedStarterPricesParseAndCoverTheBasics() throws IOException {
        try (var in = EconomyTest.class.getResourceAsStream("/data/rotasutils/trades/worth.json")) {
            WorthTable table = WorthTable.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(table.prices().size() > 60);
            assertTrue(table.base("minecraft:diamond", t -> false) > table.base("minecraft:iron_ingot", t -> false));
            assertTrue(table.base("minecraft:iron_sword", t -> false) > table.base("minecraft:iron_ingot", t -> false));
        }
    }

private static TradeOverrides.World base() {
        return TradeConfig.defaults();
    }

    private static String signature(TradeOverrides.World w) {
        StringBuilder out = new StringBuilder();
        w.books().forEach(b -> out.append(b.trade()).append(Arrays.toString(b.slotLevels())).append(b.recipes()));
        return out.append(w.perks()).append(w.stars()).toString();
    }

    @Test void anOverrideChangesOnlyWhatItNames() {
        TradeOverrides ov = TradeOverrides.empty().with("recipe.chef.cake.level", 9).with("slot.chef.1", 6)
                .with("perk.miner.prospect.base", 12).with("star.farmer.silverBase", 0.2);
        TradeOverrides.World w = ov.apply(base().books(), base().stars(), base().perks());
        var chef = w.books().stream().filter(b -> b.trade().equals("chef")).findFirst().orElseThrow();
        assertEquals(9, chef.find("cake").level());
        assertEquals(6, chef.slotLevels()[1]);
        assertEquals(base().books().get(0).find("mushroom_stew"), w.books().get(0).find("mushroom_stew"), "other recipes untouched");
        assertEquals(12, w.perks().perks().stream().filter(p -> p.type().equals("prospect")).findFirst().orElseThrow().base());
        assertEquals(0.2, w.stars().sources().stream().filter(s -> s.job().equals("farmer")).findFirst().orElseThrow().silverBase());
    }

    @Test void disablingARecipeHidesItAndNumbersAreClamped() {
        TradeOverrides ov = TradeOverrides.empty().with("recipe.chef.cake.disabled", 1).with("recipe.chef.bread.level", 999)
                .with("recipe.chef.mushroom_stew.level", 999).with("perk.miner.prospect.base", 20).with("perk.miner.prospect.max", 5);
        TradeOverrides.World w = ov.apply(base().books(), base().stars(), base().perks());
        var chef = w.books().stream().filter(b -> b.trade().equals("chef")).findFirst().orElseThrow();
        assertNull(chef.find("cake"));
        assertEquals(100, chef.find("mushroom_stew").level());
        var prospect = w.perks().perks().stream().filter(p -> p.type().equals("prospect")).findFirst().orElseThrow();
        assertEquals(20, prospect.max(), "the cap can never sit below the start");
    }

    @Test void overridesSurviveJson() {
        TradeOverrides ov = TradeOverrides.empty().with("slot.chef.0", 2).with("star.miner.maxChance", 0.4);
        assertEquals(ov, TradeOverrides.parse(ov.toJson()));
        assertTrue(ov.without("slot.chef.0").get("slot.chef.0") == null);
        assertEquals(1, ov.withoutPrefix("slot.").values().size());
    }

@Test void everyRowOfThePanelIsUnderstoodByTheEngine() {
        TradeOverrides.World world = base();
        String shipped = signature(world);
        for (String scope : SettingsPanels.scopes()) {
            if (scope.equals("worth")) continue;
            List<SettingsPanels.Field> fields = SettingsPanels.fields(scope);
            assertFalse(fields.isEmpty(), scope);
            for (SettingsPanels.Field f : fields) {
                String key = f.key().endsWith(".enabled") ? f.key().replace(".enabled", ".disabled") : f.key();
                double changed = f.key().endsWith(".enabled") ? 1 : f.format() == SettingsPanels.FLAG ? 1 - f.def() : Math.min(f.max(), f.def() + f.step());
                if (!f.key().endsWith(".enabled") && f.format() != SettingsPanels.FLAG && changed == f.def()) {
                    changed = Math.max(f.min(), f.def() - f.step());
                }
                String after = signature(TradeOverrides.empty().with(key, changed).apply(world.books(), world.stars(), world.perks()));
                assertNotEquals(shipped, after, f.key() + " has no effect");
            }
        }
    }

    @Test void panelKeysAreUniqueAndRangesAreSane() {
        Set<String> seen = new java.util.HashSet<>();
        for (String scope : SettingsPanels.scopes()) {
            for (SettingsPanels.Field f : SettingsPanels.fields(scope)) {
                assertTrue(seen.add(f.key()), "duplicate " + f.key());
                assertTrue(f.min() <= f.def() && f.def() <= f.max(), f.key() + " default outside its range");
                assertTrue(f.step() > 0, f.key());
            }
        }
        assertTrue(SettingsPanels.scopes().containsAll(List.of("worth", "slots", "perks", "stars", "recipes:chef", "recipes:miner")));
    }

    @Test void theTabsCoverEveryTunableNumber() {
        Map<String, Long> perScope = SettingsPanels.scopes().stream().collect(Collectors.toMap(s -> s, s -> (long) SettingsPanels.fields(s).size()));
        assertTrue(perScope.get("slots") >= 7 * 5, "five slots for each of seven roles");
        assertTrue(perScope.get("perks") >= 9 * 3);
        assertTrue(perScope.get("stars") >= 4 * 6);
    }
}
