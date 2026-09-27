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

/**
 * The titles (ฉายา) other players are wearing, as far as this client has been told.
 *
 * <p>A name plate is drawn from the client's own copy of a player, which knows nothing about this mod,
 * so the server sends the worn title of everyone online with the content snapshot and the plate reads
 * it from here. A player this client has not been told about simply shows their plain name.</p>
 */
@Environment(EnvType.CLIENT)
public final class PlayerTitles {
    /** Name plates are read at a glance; a longer title is cut rather than pushed off the screen. */
    private static final int MAX_LENGTH = 32;

    public record Tag(String name, int color) {
    }

    private static final Map<UUID, Tag> TITLES = new LinkedHashMap<>();

    private PlayerTitles() {
    }

    /** Replaces everything this client knows with the server's latest list. */
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
                // One unreadable entry must not cost every other player their title.
            }
        }
    }

    public static Tag of(UUID player) {
        return player == null ? null : TITLES.get(player);
    }

    /** The name plate text for an entity: a player's title in front of their name, or the name as it was. */
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
