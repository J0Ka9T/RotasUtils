package net.schwarz.rotasutils.mine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A mining site (เหมือง): a set of ore blocks that pay extra when worked, then run dry for a while.
 *
 * <p>Every node remembers the block it holds when full. Mining a full node pays the site's extras on top
 * of the ore's own drop and leaves the depleted block behind; the node comes back on its own once the
 * site's respawn time has passed. That is the whole loop - worth a visit, not worth camping - and it is
 * the block equivalent of a zone's respawning spawn points.</p>
 *
 * <p>Timing is kept as wall-clock seconds, so a node counts down across a restart instead of freezing
 * with the world.</p>
 */
public final class MiningSite {
    public static final int MAX_NODES = 256;
    public static final int MAX_LOOT_LINES = 12;

    /** One minable block of a site. */
    public static final class Node {
        private final long pos;
        private final String block;
        private long respawnAt;

        public Node(long pos, String block, long respawnAt) {
            this.pos = pos;
            this.block = block == null ? "" : block;
            this.respawnAt = Math.max(0, respawnAt);
        }

        public long pos() { return pos; }
        public String block() { return block; }
        public long respawnAt() { return respawnAt; }

        /** True while the node is spent and waiting to come back. */
        public boolean depleted(long now) {
            return respawnAt > 0 && now < respawnAt;
        }

        /** True once a spent node's time is up and the block should be put back. */
        public boolean due(long now) {
            return respawnAt > 0 && now >= respawnAt;
        }

        public void deplete(long now, int respawnSeconds) {
            respawnAt = now + Math.max(1, respawnSeconds);
        }

        public void restore() {
            respawnAt = 0;
        }

        public long secondsLeft(long now) {
            return Math.max(0, respawnAt - now);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos);
            tag.putString("block", block);
            tag.putLong("respawn_at", respawnAt);
            return tag;
        }

        static Node load(CompoundTag tag) {
            return new Node(tag.getLong("pos"), tag.getString("block"), tag.getLong("respawn_at"));
        }
    }

    private final String id;
    private String name;
    private String dimension;
    private String depletedBlock = "minecraft:cobblestone";
    private int respawnSeconds = 300;
    private long goldMin = 0;
    private long goldMax = 3;
    private long xp = 10;
    /** Most full nodes one player may work here per day; 0 means no limit. */
    private int dailyLimit = 0;
    private final List<String> loot = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();

    public MiningSite(String id, String dimension) {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("A site id is 1-64 lowercase letters, digits, dot, dash or underscore");
        }
        this.id = id;
        this.name = id;
        this.dimension = dimension == null ? "minecraft:overworld" : dimension;
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String value) { name = value == null || value.isBlank() ? id : value; }
    public String dimension() { return dimension; }
    public String depletedBlock() { return depletedBlock; }
    public void setDepletedBlock(String value) { depletedBlock = value == null || value.isBlank() ? "minecraft:cobblestone" : value.toLowerCase(Locale.ROOT); }
    public int respawnSeconds() { return respawnSeconds; }
    public void setRespawnSeconds(int value) { respawnSeconds = Math.max(5, Math.min(604800, value)); }
    public long goldMin() { return goldMin; }
    public long goldMax() { return goldMax; }
    public void setGold(long min, long max) {
        goldMin = Math.max(0, Math.min(1_000_000, min));
        goldMax = Math.max(goldMin, Math.min(1_000_000, max));
    }
    public long xp() { return xp; }
    public void setXp(long value) { xp = Math.max(0, Math.min(1_000_000, value)); }
    public int dailyLimit() { return dailyLimit; }
    public void setDailyLimit(int value) { dailyLimit = Math.max(0, Math.min(100000, value)); }
    public List<String> loot() { return loot; }
    public List<Node> nodes() { return nodes; }

    public boolean addLoot(String line) {
        if (line == null || line.isBlank() || loot.size() >= MAX_LOOT_LINES) {
            return false;
        }
        loot.add(line.trim());
        return true;
    }

    /** The node at a position, or null. Sites are small, so a scan is cheaper than a second index. */
    public Node node(long pos) {
        for (Node node : nodes) {
            if (node.pos() == pos) {
                return node;
            }
        }
        return null;
    }

    /** Registers a block as a node. False when it already is one or the site is full. */
    public boolean addNode(long pos, String block) {
        if (node(pos) != null || nodes.size() >= MAX_NODES || block == null || block.isBlank()) {
            return false;
        }
        nodes.add(new Node(pos, block, 0));
        return true;
    }

    public boolean removeNode(long pos) {
        return nodes.removeIf(node -> node.pos() == pos);
    }

    /** How many nodes are spent right now. */
    public int depletedCount(long now) {
        int count = 0;
        for (Node node : nodes) {
            if (node.depleted(now)) {
                count++;
            }
        }
        return count;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.putString("depleted", depletedBlock);
        tag.putInt("respawn", respawnSeconds);
        tag.putLong("gold_min", goldMin);
        tag.putLong("gold_max", goldMax);
        tag.putLong("xp", xp);
        tag.putInt("daily_limit", dailyLimit);
        tag.put("loot", Nbt.saveStrings(loot));
        ListTag list = new ListTag();
        nodes.forEach(node -> list.add(node.save()));
        tag.put("nodes", list);
        return tag;
    }

    public static MiningSite load(CompoundTag tag) {
        MiningSite site = new MiningSite(tag.getString("id"), tag.getString("dimension"));
        site.setName(tag.getString("name"));
        site.setDepletedBlock(tag.getString("depleted"));
        site.setRespawnSeconds(tag.contains("respawn") ? tag.getInt("respawn") : 300);
        site.setGold(tag.getLong("gold_min"), tag.getLong("gold_max"));
        site.setXp(tag.contains("xp") ? tag.getLong("xp") : 10);
        site.setDailyLimit(tag.getInt("daily_limit"));
        for (String line : Nbt.loadStrings(tag, "loot")) {
            site.addLoot(line);
        }
        ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && site.nodes.size() < MAX_NODES; index++) {
            site.nodes.add(Node.load(list.getCompound(index)));
        }
        return site;
    }
}
