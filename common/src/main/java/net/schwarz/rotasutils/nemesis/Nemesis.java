package net.schwarz.rotasutils.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.core.NemesisMath;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A nemesis (ศัตรูคู่แค้น): a monster that killed a player and is remembered for it.
 *
 * <p>The record is world data and outlives the body. The body is whichever living entity currently
 * carries the nemesis tag <em>and</em> whose UUID this record names, so a stale copy that reloads with an
 * old chunk is recognised and refused. Times are wall-clock seconds, so cooldowns count down across a
 * restart instead of freezing with the world.</p>
 */
public final class Nemesis {
    public static final int MAX_VICTIMS = 16;

    /** A player this nemesis killed, and how often. */
    public record Victim(UUID id, String name, int times) {
    }

    private final int id;
    private final String entityType;
    private final String name;
    private final NemesisMath.Style style;
    private int rank = 1;
    private int level = 1;
    private int kills;
    private String dimension = "minecraft:overworld";
    private long pos;
    private UUID body;
    private UUID target;
    private final Map<UUID, Victim> victims = new LinkedHashMap<>();
    private long createdAt;
    private long lastSeenAt;
    private long nextAmbushAt;

    public Nemesis(int id, String entityType, String name, NemesisMath.Style style, long now) {
        if (id <= 0 || entityType == null || entityType.isBlank() || entityType.length() > 256) {
            throw new IllegalArgumentException("A nemesis needs an id and an entity type");
        }
        this.id = id;
        this.entityType = entityType;
        this.name = name == null || name.isBlank() ? "???" : name.length() > 64 ? name.substring(0, 64) : name;
        this.style = style == null ? NemesisMath.Style.OTHER : style;
        this.createdAt = Math.max(0, now);
        this.lastSeenAt = this.createdAt;
    }

    public int id() { return id; }
    public String entityType() { return entityType; }
    public String name() { return name; }
    public NemesisMath.Style style() { return style; }
    public int rank() { return rank; }
    public int level() { return level; }
    public int kills() { return kills; }
    public String dimension() { return dimension; }
    public long pos() { return pos; }
    public UUID body() { return body; }
    public UUID target() { return target; }
    public long createdAt() { return createdAt; }
    public long lastSeenAt() { return lastSeenAt; }
    public long nextAmbushAt() { return nextAmbushAt; }
    public Collection<Victim> victims() { return Collections.unmodifiableCollection(victims.values()); }

    /** The name with its stars, as the name plate shows it. */
    public String displayName() {
        return name + " " + NemesisMath.stars(rank);
    }

    public void setRank(int value, int maxRank) { rank = Math.max(1, Math.min(Math.max(1, maxRank), value)); }
    public void setLevel(int value) { level = Math.max(1, value); }
    public void setBody(UUID value) { body = value; }
    public void setNextAmbushAt(long value) { nextAmbushAt = Math.max(0, value); }

    /** Where the body was last seen, and when. */
    public void seen(String dimensionId, long blockPos, long now) {
        if (dimensionId != null && !dimensionId.isBlank()) {
            dimension = dimensionId;
        }
        pos = blockPos;
        lastSeenAt = Math.max(lastSeenAt, now);
    }

    public boolean hasVictim(UUID player) {
        return player != null && victims.containsKey(player);
    }

    /** Records a kill; the newest victim becomes the one it hunts. The oldest victim is forgotten past the cap. */
    public void recordVictim(UUID player, String playerName) {
        if (player == null) {
            return;
        }
        Victim previous = victims.remove(player);
        if (previous == null && victims.size() >= MAX_VICTIMS) {
            UUID oldest = victims.keySet().iterator().next();
            victims.remove(oldest);
        }
        String shown = playerName == null ? "" : playerName.length() > 32 ? playerName.substring(0, 32) : playerName;
        victims.put(player, new Victim(player, shown, previous == null ? 1 : Math.min(1_000_000, previous.times() + 1)));
        kills = Math.min(1_000_000, kills + 1);
        target = player;
    }

    /** How many times it killed this player. */
    public int timesKilled(UUID player) {
        Victim victim = player == null ? null : victims.get(player);
        return victim == null ? 0 : victim.times();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putInt("id", id);
        tag.putString("entity", entityType);
        tag.putString("name", name);
        tag.putString("style", style.name());
        tag.putInt("rank", rank);
        tag.putInt("level", level);
        tag.putInt("kills", kills);
        tag.putString("dimension", dimension);
        tag.putLong("pos", pos);
        if (body != null) {
            tag.putUUID("body", body);
        }
        if (target != null) {
            tag.putUUID("target", target);
        }
        ListTag list = new ListTag();
        for (Victim victim : victims.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", victim.id());
            entry.putString("name", victim.name());
            entry.putInt("times", victim.times());
            list.add(entry);
        }
        tag.put("victims", list);
        tag.putLong("created_at", createdAt);
        tag.putLong("last_seen_at", lastSeenAt);
        tag.putLong("next_ambush_at", nextAmbushAt);
        return tag;
    }

    public static Nemesis load(CompoundTag tag) {
        NemesisMath.Style style;
        try {
            style = NemesisMath.Style.valueOf(tag.getString("style"));
        } catch (IllegalArgumentException unknown) {
            style = NemesisMath.Style.OTHER;
        }
        Nemesis nemesis = new Nemesis(tag.getInt("id"), tag.getString("entity"), tag.getString("name"), style,
                tag.getLong("created_at"));
        nemesis.rank = Math.max(1, Math.min(10, tag.getInt("rank")));
        nemesis.level = Math.max(1, tag.getInt("level"));
        nemesis.kills = Math.max(0, tag.getInt("kills"));
        nemesis.dimension = tag.getString("dimension").isBlank() ? "minecraft:overworld" : tag.getString("dimension");
        nemesis.pos = tag.getLong("pos");
        nemesis.body = tag.hasUUID("body") ? tag.getUUID("body") : null;
        nemesis.target = tag.hasUUID("target") ? tag.getUUID("target") : null;
        ListTag list = tag.getList("victims", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && nemesis.victims.size() < MAX_VICTIMS; index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("id")) {
                UUID player = entry.getUUID("id");
                nemesis.victims.put(player, new Victim(player, entry.getString("name"), Math.max(1, entry.getInt("times"))));
            }
        }
        nemesis.lastSeenAt = Math.max(nemesis.createdAt, tag.getLong("last_seen_at"));
        nemesis.nextAmbushAt = Math.max(0, tag.getLong("next_ambush_at"));
        return nemesis;
    }
}
