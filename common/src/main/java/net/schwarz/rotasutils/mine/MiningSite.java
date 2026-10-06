package net.schwarz.rotasutils.mine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MiningSite {
    public static final int MAX_NODES = 256;
    public static final int MAX_LOOT_LINES = 12;

    public static final class Node {
        private final long pos;
        private final String block;
        private long respawnAt;
        private boolean rich;

        public Node(long pos, String block, long respawnAt) {
            this.pos = pos;
            this.block = block == null ? "" : block;
            this.respawnAt = Math.max(0, respawnAt);
        }

        public long pos() { return pos; }
        public String block() { return block; }
        public long respawnAt() { return respawnAt; }
        public boolean rich() { return rich; }
        public void setRich(boolean value) { rich = value; }

        public boolean depleted(long now) {
            return respawnAt > 0 && now < respawnAt;
        }

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
            if (rich) tag.putBoolean("rich", true);
            return tag;
        }

        static Node load(CompoundTag tag) {
            Node node = new Node(tag.getLong("pos"), tag.getString("block"), tag.getLong("respawn_at"));
            node.rich = tag.getBoolean("rich");
            return node;
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
    private int dailyLimit = 0;
    private int minTier = 0;
    private int richChance = 0;
    private int richMultiplier = 2;
    private int minerBonus = 25;
    private boolean sparkle = true;
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
    public int minTier() { return minTier; }
    public void setMinTier(int value) { minTier = Math.max(0, Math.min(4, value)); }
    public int richChance() { return richChance; }
    public void setRichChance(int value) { richChance = Math.max(0, Math.min(100, value)); }
    public int richMultiplier() { return richMultiplier; }
    public void setRichMultiplier(int value) { richMultiplier = Math.max(1, Math.min(10, value)); }
    public int minerBonus() { return minerBonus; }
    public void setMinerBonus(int value) { minerBonus = Math.max(0, Math.min(300, value)); }
    public boolean sparkle() { return sparkle; }
    public void setSparkle(boolean value) { sparkle = value; }

    public boolean rollRich(net.minecraft.util.RandomSource random) {
        return richChance > 0 && random.nextInt(100) < richChance;
    }

    public List<String> loot() { return loot; }
    public List<Node> nodes() { return nodes; }

    public boolean addLoot(String line) {
        if (line == null || line.isBlank() || loot.size() >= MAX_LOOT_LINES) {
            return false;
        }
        loot.add(line.trim());
        return true;
    }

    public Node node(long pos) {
        for (Node node : nodes) {
            if (node.pos() == pos) {
                return node;
            }
        }
        return null;
    }

    public boolean addNode(long pos, String block) {
        if (node(pos) != null || nodes.size() >= MAX_NODES || block == null || block.isBlank()) {
            return false;
        }
        Node node = new Node(pos, block, 0);
        node.setRich(rollRich(net.minecraft.util.RandomSource.create()));
        nodes.add(node);
        return true;
    }

    public boolean removeNode(long pos) {
        return nodes.removeIf(node -> node.pos() == pos);
    }

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
        tag.putInt("min_tier", minTier);
        tag.putInt("rich_chance", richChance);
        tag.putInt("rich_multiplier", richMultiplier);
        tag.putInt("miner_bonus", minerBonus);
        tag.putBoolean("sparkle", sparkle);
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
        site.setMinTier(tag.getInt("min_tier"));
        site.setRichChance(tag.getInt("rich_chance"));
        site.setRichMultiplier(tag.contains("rich_multiplier") ? tag.getInt("rich_multiplier") : 2);
        site.setMinerBonus(tag.contains("miner_bonus") ? tag.getInt("miner_bonus") : 25);
        site.setSparkle(!tag.contains("sparkle") || tag.getBoolean("sparkle"));
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
