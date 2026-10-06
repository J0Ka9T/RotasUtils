package net.schwarz.rotasutils.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeBookTest {
    private static final Set<String> ROLES = Set.of("chef", "miner", "blacksmith", "alchemy", "rancher", "fisher", "farmer");

    private static String text(String name) throws IOException {
        try (var in = TradeBookTest.class.getResourceAsStream("/data/rotasutils/trades/" + name + ".json")) {
            assertNotNull(in, name + ".json must ship in the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<TradeBook> baseBooks() throws IOException {
        List<TradeBook> books = new ArrayList<>();
        for (var id : JsonParser.parseString(text("index")).getAsJsonObject().getAsJsonArray("trades")) {
            books.add(TradeBook.parse(text(id.getAsString())));
        }
        return books;
    }

    private static List<TradeBundle> bundles() throws IOException {
        List<TradeBundle> out = new ArrayList<>();
        for (var id : JsonParser.parseString(text("compat/index")).getAsJsonObject().getAsJsonArray("bundles")) {
            out.add(TradeBundle.parse(text("compat/" + id.getAsString())));
        }
        return out;
    }

    private static TradeBundle.Merged world() throws IOException {
        return TradeBundle.apply(baseBooks(), StarRules.parse(text("stars")), bundles());
    }

    private static List<TradeBook> shipped() throws IOException {
        return world().books();
    }

    private static StarRules stars() throws IOException {
        return world().stars();
    }

@Test void everyRoleHasItsOwnWorkshopAndABook() throws IOException {
        List<TradeBook> books = shipped();
        assertEquals(ROLES, books.stream().map(TradeBook::trade).collect(java.util.stream.Collectors.toSet()));
        Set<String> stations = new HashSet<>();
        for (TradeBook book : books) {
            assertTrue(stations.add(book.station()), "two roles share " + book.station());
            assertEquals(book.trade(), book.job(), "the book id and the job it serves match by default");
            assertTrue(book.recipes().size() >= 5, book.trade() + " should have a real recipe list");
            assertEquals(1, book.slots(1));
            assertEquals(5, book.slots(16));
        }
    }

    @Test void everyBundleLayersCleanlyAndAddsRealContent() throws IOException {
        int base = baseBooks().stream().mapToInt(b -> b.recipes().size()).sum();
        int merged = shipped().stream().mapToInt(b -> b.recipes().size()).sum();
        assertTrue(merged >= base + 30, "the bundles should add substantial recipes: " + base + " -> " + merged);
        for (TradeBundle bundle : bundles()) {
            assertFalse(bundle.recipes().isEmpty(), bundle.name());
            bundle.recipes().values().forEach(list -> list.forEach(r ->
                    assertFalse(r.requires().isEmpty(), bundle.name() + "/" + r.id() + " must name the mod it needs")));
        }
        var bad = TradeBundle.parse("{\"bundle\":\"x\",\"trades\":{\"nobody\":[{\"id\":\"a\",\"ingredients\":[{\"item\":\"minecraft:egg\"}],\"outputs\":[{\"item\":\"minecraft:cake\"}]}]}}");
        assertThrows(IllegalArgumentException.class, () -> TradeBundle.apply(baseBooks(), StarRules.parse(text("stars")), List.of(bad)));
    }

    @Test void theChefSpecHoldsThreeStartersAndSecrets() throws IOException {
        TradeBook chef = shipped().stream().filter(b -> b.trade().equals("chef")).findFirst().orElseThrow();
        assertEquals(3, chef.recipes().stream().filter(r -> r.level() == 1).count());
        assertTrue(chef.recipes().stream().anyMatch(TradeBook.Recipe::secret));
    }

    @Test void everyGradedIngredientCanActuallyBeObtainedAsAStar() throws IOException {
        Set<String> supply = new HashSet<>();
        StarRules rules = stars();
        rules.sources().forEach(s -> supply.addAll(s.items()));
        for (TradeBook book : shipped()) {
            for (TradeBook.Recipe recipe : book.recipes()) {
                if (recipe.quality()) supply.add(recipe.outputs().get(0).item());
            }
        }
        for (TradeBook book : shipped()) {
            for (TradeBook.Recipe recipe : book.recipes()) {
                for (TradeBook.Ingredient need : recipe.ingredients()) {
                    if (need.graded() || need.minStar() > 0) {
                        assertTrue(need.options().stream().anyMatch(supply::contains),
                                book.trade() + "/" + recipe.id() + " asks for a star " + need.item() + " nobody can make");
                    }
                }
            }
        }
    }

    @Test void everyGatheringRoleIsNeededByAnotherRole() throws IOException {
        Map<String, Set<String>> customers = new HashMap<>();
        StarRules rules = stars();
        for (TradeBook book : shipped()) {
            for (TradeBook.Recipe recipe : book.recipes()) {
                for (TradeBook.Ingredient need : recipe.ingredients()) {
                    if (!need.graded() && need.minStar() == 0) continue;
                    for (String job : rules.jobsFor(need.options())) {
                        if (!job.equals(book.job())) customers.computeIfAbsent(job, k -> new HashSet<>()).add(book.trade());
                    }
                }
            }
        }
        for (var source : rules.sources()) {
            assertFalse(customers.getOrDefault(source.job(), Set.of()).isEmpty(), source.job() + " supplies stars nobody else uses");
        }
        Set<String> feeds = new HashSet<>();
        for (var entry : customers.entrySet()) {
            if (entry.getValue().contains("chef")) feeds.add(entry.getKey());
        }
        assertTrue(feeds.size() >= 3, "the Chef should depend on several roles: " + feeds);
    }

    @Test void onlyTheChefSealsVanillaCraftingAndLeftoversStayFree() throws IOException {
        Set<String> sealed = new HashSet<>();
        for (TradeBook book : shipped()) {
            Set<String> own = book.stationOutputs();
            if (!book.trade().equals("chef")) assertTrue(own.isEmpty(), book.trade() + " must not seal vanilla recipes");
            sealed.addAll(own);
        }
        assertTrue(sealed.contains("minecraft:cake"));
        assertFalse(sealed.contains("minecraft:bucket"), "leftover buckets from a cake must never be sealed");
        assertFalse(sealed.contains("minecraft:cookie"));
        assertFalse(sealed.contains("minecraft:bread"));
        assertFalse(sealed.contains("minecraft:iron_ingot"));
    }

    @Test void recipeIdsNeverRepeatInsideABook() throws IOException {
        for (TradeBook book : shipped()) {
            assertEquals(book.recipes().size(), book.recipes().stream().map(TradeBook.Recipe::id).distinct().count());
        }
    }

@Test void secretRecipesNeedTheScrollAndTheLevel() throws IOException {
        TradeBook chef = shipped().stream().filter(b -> b.trade().equals("chef")).findFirst().orElseThrow();
        var secret = chef.find("golden_apple");
        assertEquals(TradeBook.State.SECRET, TradeBook.state(secret, 1, false));
        assertEquals(TradeBook.State.NEED_SCROLL, TradeBook.state(secret, 8, false));
        assertEquals(TradeBook.State.NEED_LEVEL, TradeBook.state(secret, 1, true));
        assertEquals(TradeBook.State.AVAILABLE, TradeBook.state(secret, 8, true));
        assertEquals(TradeBook.State.NEED_LEVEL, TradeBook.state(chef.find("cake"), 1, false));
        assertEquals(TradeBook.State.AVAILABLE, TradeBook.state(chef.find("mushroom_stew"), 1, false));
    }

    @Test void badBooksAreRefusedWithAReason() {
        String ok = "{\"id\":\"a\",\"ingredients\":[{\"item\":\"minecraft:egg\"}],\"outputs\":[{\"item\":\"minecraft:cake\"}]}";
        String base = "{\"trade\":\"t\",\"station\":\"rotasutils:cooking_station\",\"slotLevels\":[1],\"recipes\":[%s]}";
        assertDoesNotThrow(() -> TradeBook.parse(base.formatted(ok)));
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.formatted(ok + "," + ok)), "duplicate id");
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.formatted(ok.replace("\"a\"", "\"Bad Id\""))));
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.formatted(ok.replace("minecraft:egg", "egg"))));
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.formatted(ok.replace("\"id\":\"a\",", "\"id\":\"a\",\"seconds\":1,"))));
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.replace("\"trade\":\"t\",", "").formatted(ok)), "no trade id");
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.formatted(ok.replace("\"id\":\"a\",", "\"id\":\"a\",\"quality\":true,"))));
        assertThrows(IllegalArgumentException.class, () -> TradeBook.parse(base.formatted(ok.replace("\"minecraft:egg\"", "\"minecraft:egg\",\"graded\":true"))));
    }

    @Test void theStarRollIsExclusiveCappedAndGrowsWithLevel() throws IOException {
        StarRules.Source farmer = stars().sourceFor("farmer", "minecraft:wheat");
        assertNotNull(farmer);
        assertEquals(0, farmer.roll(1, 0.99));
        assertEquals(1, farmer.roll(1, 0.01));
        assertEquals(1, farmer.roll(1, 0.0), "no gold before its level");
        assertEquals(2, farmer.roll(8, 0.0));
        int stars = 0;
        for (int i = 0; i < 1000; i++) {
            if (farmer.roll(100, i / 1000.0) > 0) stars++;
        }
        assertTrue(stars <= 501, "capped at 50%: " + stars);
        assertTrue(farmer.roll(20, 0.29) > 0, "high levels star more often than low ones");
        assertEquals(0, farmer.roll(1, 0.29));
        assertNull(stars().sourceFor("farmer", "minecraft:diamond"), "a Farmer cannot star diamonds");
        assertNotNull(stars().sourceFor("miner", "minecraft:diamond"));
    }

    @Test void starMealsGetBetterWithTheStar() throws IOException {
        StarRules rules = stars();
        assertTrue(rules.meal(0).isEmpty());
        assertFalse(rules.meal(1).isEmpty());
        assertTrue(rules.meal(2).size() > rules.meal(1).size());
    }

@Test void gradedIngredientsFollowTheChosenQualityWhileFixedOnesDoNot() {
        var graded = new TradeBook.Ingredient("minecraft:wheat", 3, 0, true);
        var fixed = new TradeBook.Ingredient("minecraft:sugar", 2, 0, false);
        var hard = new TradeBook.Ingredient("minecraft:gold_ingot", 1, 1, false);
        assertEquals(0, graded.need(0));
        assertEquals(2, graded.need(2));
        assertEquals(0, fixed.need(2), "sugar is never graded");
        assertEquals(1, hard.need(0), "a hard minimum stays at any quality");
    }

    @Test void thePlannerSpendsTheCheapestQualifyingStarAndNeverOverdraws() {
        var need = List.of(new TradeBook.Ingredient("minecraft:carrot", 2, 1, false), new TradeBook.Ingredient("minecraft:gold_nugget", 8, 0, false));
        var bag = List.of(new TradeKit.Held("minecraft:carrot", 0, 64), new TradeKit.Held("minecraft:carrot", 2, 5),
                new TradeKit.Held("minecraft:carrot", 1, 1), new TradeKit.Held("minecraft:gold_nugget", 0, 5),
                new TradeKit.Held("minecraft:gold_nugget", 0, 9));
        int[] taken = TradeKit.plan(need, bag, 0);
        assertNotNull(taken);
        assertEquals(0, taken[0], "a plain carrot never pays for a star requirement");
        assertEquals(1, taken[2], "the silver carrot goes first");
        assertEquals(1, taken[1], "then gold covers the rest");
        assertEquals(8, taken[3] + taken[4]);
        assertNull(TradeKit.plan(List.of(new TradeBook.Ingredient("minecraft:carrot", 7, 1, false)), bag, 0), "only 6 star carrots");
        assertEquals(6, TradeKit.have(new TradeBook.Ingredient("minecraft:carrot", 1, 1, false), bag, 0));
    }

    @Test void cookingAtGoldNeedsGoldAndTheBestTargetIsFound() {
        var recipe = new TradeBook.Recipe("x", 1, false, false, true, 30,
                List.of(new TradeBook.Ingredient("minecraft:wheat", 3, 0, true), new TradeBook.Ingredient("minecraft:sugar", 1, 0, false)),
                List.of(new TradeBook.Output("minecraft:cake", 1)));
        var plainOnly = List.of(new TradeKit.Held("minecraft:wheat", 0, 9), new TradeKit.Held("minecraft:sugar", 0, 1));
        assertEquals(0, TradeKit.bestTarget(recipe, plainOnly));
        var withSilver = List.of(new TradeKit.Held("minecraft:wheat", 1, 3), new TradeKit.Held("minecraft:wheat", 0, 9),
                new TradeKit.Held("minecraft:sugar", 0, 1));
        assertEquals(1, TradeKit.bestTarget(recipe, withSilver));
        assertNull(TradeKit.plan(recipe.ingredients(), withSilver, 2));
        var withGold = List.of(new TradeKit.Held("minecraft:wheat", 2, 3), new TradeKit.Held("minecraft:sugar", 0, 1));
        assertEquals(2, TradeKit.bestTarget(recipe, withGold));
        assertEquals(-1, TradeKit.bestTarget(recipe, List.of(new TradeKit.Held("minecraft:wheat", 0, 9))), "no sugar at all");
        assertEquals(2, recipe.outputStar(2));
        assertEquals(0, new TradeBook.Recipe("y", 1, false, false, false, 30, recipe.ingredients().subList(1, 2), recipe.outputs()).outputStar(2));
    }

    @Test void theQueueSurvivesAStringRoundTripWithStarsAndDropsDamage() {
        var queue = List.of(new TradeKit.Cooking("cake", 1700000000000L, 2), new TradeKit.Cooking("rabbit_stew", 5L, 0));
        assertEquals(queue, TradeKit.parseQueue(TradeKit.formatQueue(queue)));
        assertEquals(List.of(new TradeKit.Cooking("cake", 9L)), TradeKit.parseQueue("cake@9,junk,@5,x@notanumber"));
        assertEquals(List.of("a", "b"), TradeKit.parseLearned("a, b,,a"));
        assertTrue(TradeKit.parseLearned(null).isEmpty());
        assertTrue(queue.get(1).done(5));
        assertFalse(queue.get(1).done(4));
    }

@Test void anIngredientAcceptsAnyOfItsChoicesIncludingTags() {
        var need = new TradeBook.Ingredient("minecraft:carrot|#forge:crops/carrot", 2, 0, true);
        assertEquals(List.of("minecraft:carrot", "#forge:crops/carrot"), need.options());
        assertEquals("minecraft:carrot", need.icon());
        var bag = List.of(new TradeKit.Held("minecraft:carrot", 0, 1),
                new TradeKit.Held("farmersdelight:tomato", 0, 5, Set.of("forge:crops/carrot")),
                new TradeKit.Held("minecraft:stick", 0, 9));
        int[] taken = TradeKit.plan(List.of(need), bag, 0);
        assertNotNull(taken);
        assertEquals(2, taken[0] + taken[1], "a modded stack in the tag pays like a vanilla carrot");
        assertEquals(0, taken[2]);
        assertEquals(6, TradeKit.have(need, bag, 0));
    }

    @Test void universalCrossLoaderAndCommonTagMatching() {
        TradeBook.Ingredient needForge = new TradeBook.Ingredient("#forge:crops/carrot", 2, 0);
        var bagFabric = List.of(new TradeKit.Held("fabricmod:orange_carrot", 0, 5, Set.of("c:crops/carrot")));
        int[] takenForge = TradeKit.plan(List.of(needForge), bagFabric, 0);
        assertNotNull(takenForge, "Fabric c: tag must pay for #forge: tag");
        assertEquals(2, takenForge[0]);

        TradeBook.Ingredient needVanillaIngot = new TradeBook.Ingredient("minecraft:iron_ingot", 3, 0);
        var bagModded = List.of(new TradeKit.Held("createmod:refined_iron", 0, 4, Set.of("forge:ingots/iron")));
        int[] takenIngot = TradeKit.plan(List.of(needVanillaIngot), bagModded, 0);
        assertNotNull(takenIngot, "Modded ingot with forge:ingots/iron pays for vanilla iron ingot");
        assertEquals(3, takenIngot[0]);

        TradeBook.Ingredient needWheat = new TradeBook.Ingredient("minecraft:wheat", 1, 0);
        var bagWheat = List.of(new TradeKit.Held("somecropmod:golden_grain", 0, 2, Set.of("c:wheat")));
        int[] takenWheat = TradeKit.plan(List.of(needWheat), bagWheat, 0);
        assertNotNull(takenWheat, "Modded grain with c:wheat pays for vanilla wheat");
        assertEquals(1, takenWheat[0]);
    }

    @Test void everyRecipeUsingAnotherModNamesThatModAsARequirement() throws IOException {
        for (TradeBook book : shipped()) {
            for (TradeBook.Recipe recipe : book.recipes()) {
                Set<String> namespaces = new HashSet<>();
                recipe.outputs().forEach(o -> namespaces.add(o.item().substring(0, o.item().indexOf(':'))));
                recipe.ingredients().forEach(i -> i.options().stream().filter(o -> !o.startsWith("#"))
                        .forEach(o -> namespaces.add(o.substring(0, o.indexOf(':')))));
                namespaces.remove("minecraft");
                assertTrue(recipe.requires().containsAll(namespaces),
                        book.trade() + "/" + recipe.id() + " uses " + namespaces + " but requires " + recipe.requires());
            }
        }
    }

    @Test void starSourcesCanListTagsSoModdedCropsStarToo() throws IOException {
        StarRules rules = stars();
        assertNotNull(rules.sourceFor("farmer", "farmersdelight:tomato"), "named in the file");
        assertNotNull(rules.sourceFor("farmer", "somemod:turnip", tag -> tag.equals("forge:crops")), "found through the #forge:crops tag");
        assertNull(rules.sourceFor("farmer", "somemod:turnip", tag -> false));
    }
}
