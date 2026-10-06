package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.WorthTable;
import net.schwarz.rotasutils.item.StarQuality;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Predicate;

public final class WorthService {
    private static volatile WorthTable table;

    private WorthService() {
    }

    public static WorthTable table() {
        WorthTable current = table;
        if (current == null) {
            current = builtIn();
            table = current;
        }
        return current;
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve("config").resolve(Rotasutils.MOD_ID).resolve("trades").resolve("worth.json");
    }

    public static String load(MinecraftServer server) {
        Path file = path(server);
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                write(file, table().toJson());
            }
            table = WorthTable.parse(Files.readString(file, StandardCharsets.UTF_8));
            return null;
        } catch (IOException | RuntimeException failure) {
            return failure.getMessage() == null ? failure.toString() : failure.getMessage();
        }
    }

    public static void set(MinecraftServer server, String item, long gold) {
        store(server, table().with(item, gold));
    }

    public static void setSettings(MinecraftServer server, int sell, int silver, int gold) {
        store(server, table().withSettings(sell, silver, gold));
    }

    private static void store(MinecraftServer server, WorthTable next) {
        table = next;
        try {
            write(path(server), next.toJson());
        } catch (IOException failure) {
            Rotasutils.LOG.error("worth.json was not saved: {}", failure.getMessage());
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling("worth.json.tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static WorthTable builtIn() {
        try (InputStream in = WorthService.class.getResourceAsStream("/data/rotasutils/trades/worth.json")) {
            return in == null ? WorthTable.empty() : WorthTable.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            return WorthTable.empty();
        }
    }

public static Predicate<String> tags(ItemStack stack) {
        return tag -> {
            ResourceLocation location = ResourceLocation.tryParse(tag);
            return location != null && stack.is(TagKey.create(Registries.ITEM, location));
        };
    }

    private static String id(ItemStack stack) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static long worth(ItemStack stack) {
        return stack.isEmpty() ? 0 : table().worth(id(stack), tags(stack), StarQuality.of(stack));
    }

    public static long sellValue(ItemStack stack) {
        return stack.isEmpty() ? 0 : table().sellValue(id(stack), tags(stack), StarQuality.of(stack), stack.getCount());
    }

    public static CompoundTag toTag() {
        WorthTable t = table();
        CompoundTag tag = new CompoundTag();
        CompoundTag prices = new CompoundTag();
        t.prices().forEach(prices::putLong);
        tag.put("prices", prices);
        tag.putInt("sell", t.sellPercent());
        tag.putInt("silver", t.silverPercent());
        tag.putInt("gold", t.goldPercent());
        return tag;
    }
}
