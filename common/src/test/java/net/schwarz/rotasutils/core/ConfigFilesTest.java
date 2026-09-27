package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.ServerSettings;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigFilesTest {
    @TempDir Path root;

    @Test void exportedSnapshotRoundTripsUnknownFieldsAndLayerWithoutOverwriting() throws Exception {
        var settings = new ServerSettings().save(); settings.putString("addon_note", "retained");
        var source = new ContentRegistry.Source("test", ContentRegistry.Layer.SEASON,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:test\",\"kind\":\"action\",\"body\":{\"type\":\"stat_points\",\"amount\":2}}"));
        Path first = ConfigFiles.export(root, List.of(source), Map.of("settings", settings));
        Path second = ConfigFiles.export(root, List.of(source), Map.of("settings", settings));
        assertNotEquals(first, second);
        var loaded = ConfigFiles.readLegacy(first).get("settings");
        assertEquals(settings.getString("addon_note"), loaded.getString("addon_note"));
        assertEquals(settings.getInt("max_active"), loaded.getInt("max_active"));
        var prepared = ContentPacks.read(first.resolve("packs"), new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of())));
        assertTrue(prepared.valid(), prepared.issues().toString());
        assertEquals(ContentRegistry.Layer.SEASON, prepared.snapshot().definitions().get(new ContentId("rotas:test")).source().layer());
    }

    @Test void duplicatesMalformedAndEscapingPathsReject() throws Exception {
        assertThrows(java.io.IOException.class, () -> ConfigFiles.safeDirectory(root, "../escape"));
        Path config = ConfigFiles.safeDirectory(root, "configuration");
        String json = ConfigFiles.document("settings", new ServerSettings().save()).toString();
        Files.writeString(config.resolve("one.json"), json); Files.writeString(config.resolve("two.json"), json);
        assertThrows(java.io.IOException.class, () -> ConfigFiles.readLegacy(root));
        Files.delete(config.resolve("two.json")); Files.writeString(config.resolve("one.json"), "{\"schema\":2,\"domain\":\"settings\",\"body\":{}}");
        assertThrows(IllegalArgumentException.class, () -> ConfigFiles.readLegacy(root));
    }

    @Test void invalidSettingsNeverSilentlyClampAndValidDefaultsPass() {
        var data = new RotasData(); var settings = data.serverSettings().save();
        assertTrue(ConfigService.validate(data, "settings", settings).isEmpty());
        settings.putDouble("party_radius", Double.NaN);
        assertFalse(ConfigService.validate(data, "settings", settings).isEmpty());
        settings = data.serverSettings().save(); settings.putInt("max_party", 65);
        assertFalse(ConfigService.validate(data, "settings", settings).isEmpty());
    }
}

