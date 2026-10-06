package net.schwarz.rotasutils.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SkillNode {
    private String id;
    private String categoryId;
    private String name = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.skill.name");
    private String description = "";
    private ItemStack icon = new ItemStack(Items.NETHER_STAR);
    private NodeType type = NodeType.PASSIVE;

    private int x;
    private int y;

    private int maxRank = 1;
    private int costPerRank = 1;
    private int costIncrement;
    private int minLevel = 1;
    private int levelIncrement;

    private boolean root;
    private boolean hidden;
    private boolean disabled;

    private final List<SkillEffect> effects = new ArrayList<>();
    private final List<Requirement> requirements = new ArrayList<>();
    private final List<SkillConnection> connections = new ArrayList<>();
    private final Set<String> exclusiveWith = new LinkedHashSet<>();
    private final List<String> rankDescriptions = new ArrayList<>();

    public SkillNode(String id, String categoryId) {
        this.id = id;
        this.categoryId = categoryId;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String categoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ItemStack icon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon.isEmpty() ? new ItemStack(Items.NETHER_STAR) : icon;
    }

    public NodeType type() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
        if (type != NodeType.RANKED && maxRank > 1) {
            maxRank = 1;
        }
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int maxRank() {
        return Math.max(1, maxRank);
    }

    public void setMaxRank(int maxRank) {
        this.maxRank = Math.max(1, Math.min(50, maxRank));
    }

    public int costPerRank() {
        return Math.max(0, costPerRank);
    }

    public void setCostPerRank(int costPerRank) {
        this.costPerRank = Math.max(0, costPerRank);
    }

    public int costIncrement() {
        return costIncrement;
    }

    public void setCostIncrement(int costIncrement) {
        this.costIncrement = Math.max(0, costIncrement);
    }

    public int costForRank(int currentRank) {
        return (int) Math.min(Integer.MAX_VALUE, (long) costPerRank() + (long) Math.max(0, costIncrement) * Math.max(0, currentRank));
    }

    public int minLevel() { return minLevel; }
    public void setMinLevel(int level) { minLevel = Math.max(1, Math.min(10000, level)); }
    public int levelIncrement() { return levelIncrement; }
    public void setLevelIncrement(int levels) { levelIncrement = Math.max(0, Math.min(10000, levels)); }
    public int requiredLevel(int currentRank) {
        return (int) Math.min(Integer.MAX_VALUE, (long) minLevel + (long) levelIncrement * Math.max(0, currentRank));
    }

    public boolean root() {
        return root;
    }

    public void setRoot(boolean root) {
        this.root = root;
    }

    public boolean hidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean disabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public List<SkillEffect> effects() {
        return effects;
    }

    public List<Requirement> requirements() {
        return requirements;
    }

    public List<SkillConnection> connections() {
        return connections;
    }

    public Set<String> exclusiveWith() {
        return exclusiveWith;
    }

    public List<String> rankDescriptions() {
        return rankDescriptions;
    }

    public String rankDescription(int rank) {
        int index = rank - 1;
        if (index >= 0 && index < rankDescriptions.size()) {
            return rankDescriptions.get(index);
        }
        return description;
    }

    public SkillNode copyAs(String newId) {
        SkillNode copy = load(save());
        copy.id = newId;
        copy.connections.clear();
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("category", categoryId);
        tag.putString("name", name);
        tag.putString("desc", description);
        tag.put("icon", Nbt.saveStack(icon));
        tag.putString("type", type.name());
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("max_rank", maxRank);
        tag.putInt("cost", costPerRank);
        tag.putInt("cost_inc", costIncrement);
        tag.putInt("min_level", minLevel);
        tag.putInt("level_inc", levelIncrement);
        tag.putBoolean("root", root);
        tag.putBoolean("hidden", hidden);
        tag.putBoolean("disabled", disabled);
        tag.put("effects", Nbt.saveList(effects, SkillEffect::save));
        tag.put("requirements", Nbt.saveList(requirements, Requirement::save));
        tag.put("connections", Nbt.saveList(connections, SkillConnection::save));
        tag.put("exclusive", Nbt.saveStrings(exclusiveWith));
        tag.put("rank_desc", Nbt.saveStrings(rankDescriptions));
        return tag;
    }

    public static SkillNode load(CompoundTag tag) {
        SkillNode node = new SkillNode(tag.getString("id"), tag.getString("category"));
        node.name = tag.getString("name");
        node.description = tag.getString("desc");
        node.icon = Nbt.loadStack(tag, "icon");
        node.type = Nbt.readEnum(tag, "type", NodeType.class, NodeType.PASSIVE);
        node.x = tag.getInt("x");
        node.y = tag.getInt("y");
        node.setMaxRank(tag.getInt("max_rank"));
        node.costPerRank = tag.getInt("cost");
        node.setCostIncrement(tag.getInt("cost_inc"));
        node.setMinLevel(tag.getInt("min_level"));
        node.setLevelIncrement(tag.getInt("level_inc"));
        node.root = tag.getBoolean("root");
        node.hidden = tag.getBoolean("hidden");
        node.disabled = tag.getBoolean("disabled");
        node.effects.addAll(Nbt.loadList(tag, "effects", SkillEffect::load));
        node.requirements.addAll(Nbt.loadList(tag, "requirements", Requirement::load));
        node.connections.addAll(Nbt.loadList(tag, "connections", SkillConnection::load));
        node.exclusiveWith.addAll(Nbt.loadStrings(tag, "exclusive"));
        node.rankDescriptions.addAll(Nbt.loadStrings(tag, "rank_desc"));
        return node;
    }

    public enum NodeType {
        PASSIVE("Passive"),
        RANKED("Ranked"),
        UNLOCK("Unlock"),
        TRIGGERED("Triggered passive"),
        CHOICE("Choice"),
        KEYSTONE("Keystone");

        public static final NodeType[] VALUES = values();
        private final String display;

        NodeType(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("node_type", this, display);
        }
    }
}
