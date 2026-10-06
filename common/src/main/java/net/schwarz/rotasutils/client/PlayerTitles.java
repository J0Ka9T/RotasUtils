package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class PlayerTitles {
    private static final int MAX_LENGTH = 32;

    public record Tag(String name, int color) {
    }

    private static final Map<UUID, Tag> TITLES = new LinkedHashMap<>();

    private PlayerTitles() {
    }

    public static void apply(CompoundTag tag) {
        TITLES.clear();
        for (String key : tag.getAllKeys()) {
            try {
                CompoundTag entry = tag.getCompound(key);
                String name = entry.getString("name");
                if (!name.isBlank()) {
                    TITLES.put(UUID.fromString(key), new Tag(
                            name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name,
                            entry.getInt("color")));
                }
            } catch (IllegalArgumentException malformed) {
            }
        }
    }

    public static Tag of(UUID player) {
        return player == null ? null : TITLES.get(player);
    }

    public static Component decorate(Entity entity, Component name) {
        if (!(entity instanceof Player player) || name == null) {
            return name;
        }
        Tag tag = of(player.getUUID());
        if (tag == null) {
            return name;
        }
        return Component.literal("[" + tag.name() + "] ")
                .withStyle(style -> style.withColor(tag.color() & 0xFFFFFF))
                .append(name);
    }
}
