package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** The shipped sample packs must keep compiling through the same registry a server uses. */
class ExamplePacksTest {
    private static Path examples() {
        Path direct = Path.of("examples");
        return Files.isDirectory(direct) ? direct : Path.of("..", "examples");
    }

    private List<ContentRegistry.Source> load(String pack) throws IOException {
        Path root = examples().resolve(pack);
        assertTrue(Files.isDirectory(root), "missing sample pack: " + root.toAbsolutePath());
        List<ContentRegistry.Source> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                sources.add(new ContentRegistry.Source(file.getFileName().toString(), ContentRegistry.Layer.SERVER,
                        ContentPacks.parse(Files.readString(file))));
            }
        }
        assertFalse(sources.isEmpty(), "sample pack has no definitions: " + root);
        return sources;
    }

    @Test void everyShippedSamplePackCompiles() throws IOException {
        var registry = new ContentRegistry(new ConditionEngine(KernelAdaptersFixture.conditions()), new ActionEngine(Map.of()));
        List<ContentRegistry.Source> all = new ArrayList<>();
        for (String pack : List.of("rpg-packs", "rpg-progression", "rpg-monsters", "rpg-endgame")) {
            List<ContentRegistry.Source> sources = load(pack);
            var prepared = registry.prepare(sources);
            assertTrue(prepared.valid(), pack + ": " + prepared.issues());
            all.addAll(sources);
        }
        // The samples are also meant to coexist: installing all of them at once must still compile.
        var combined = registry.prepare(all);
        assertTrue(combined.valid(), combined.issues().toString());
        var snapshot = combined.snapshot();
        assertFalse(snapshot.items().profiles().isEmpty(), "endgame sample defines items");
        assertFalse(snapshot.monsters().bosses().isEmpty(), "endgame sample defines a boss");
        assertFalse(snapshot.quests().isEmpty(), "endgame sample defines a quest");
        assertFalse(snapshot.merchants().isEmpty(), "endgame sample defines a merchant");
        assertFalse(snapshot.monsters().profiles().isEmpty(), "monster sample defines profiles");
    }

    /** The requirement adapters a live server registers; the samples may reference them. */
    private static final class KernelAdaptersFixture {
        static Map<String, java.util.function.Function<com.google.gson.JsonObject, ConditionEngine.Condition>> conditions() {
            Map<String, java.util.function.Function<com.google.gson.JsonObject, ConditionEngine.Condition>> adapters = new java.util.HashMap<>();
            for (String name : List.of("MIN_LEVEL", "MAX_LEVEL", "HAS_ITEM", "DIMENSION", "PERMISSION", "PRESTIGE")) {
                adapters.put(name, json -> context -> true);
            }
            return Map.copyOf(adapters);
        }
    }
}
