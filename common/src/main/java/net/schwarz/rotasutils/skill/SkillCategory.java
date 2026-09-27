package net.schwarz.rotasutils.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A tab of the skill tree holding its own node graph. */
public final class SkillCategory {
    private String id;
    private String name;
    private String description = "";
    private ItemStack icon = new ItemStack(Items.IRON_SWORD);
    private int backgroundColor = 0xFF101018;
    private int accentColor = 0xFFFFAA00;
    private int order;
    private int version = 1;
    /** When true this category spends its own point pool instead of the global one. */
    private boolean usesCategoryPoints;
    private boolean lockedByDefault;
    private int minLevel = 1;

    private final List<Requirement> unlockRequirements = new ArrayList<>();
    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();
    /** Jobs that may use this tree; empty means every job. */
    private final Set<String> jobs = new LinkedHashSet<>();
    /** Origins (races) that may use this tree; empty means every race. */
    private final Set<String> races = new LinkedHashSet<>();

    public SkillCategory(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        this.icon = icon.isEmpty() ? new ItemStack(Items.IRON_SWORD) : icon;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int accentColor() {
        return accentColor;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    public int order() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int version() {
        return version;
    }

    public void bumpVersion() {
        this.version++;
    }

    public boolean usesCategoryPoints() {
        return usesCategoryPoints;
    }

    public void setUsesCategoryPoints(boolean usesCategoryPoints) {
        this.usesCategoryPoints = usesCategoryPoints;
    }

    public boolean lockedByDefault() {
        return lockedByDefault;
    }

    public void setLockedByDefault(boolean lockedByDefault) {
        this.lockedByDefault = lockedByDefault;
    }

    public int minLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = Math.max(1, minLevel);
    }

    public List<Requirement> unlockRequirements() {
        return unlockRequirements;
    }

    public Map<String, SkillNode> nodes() {
        return nodes;
    }

    public Set<String> jobs() {
        return jobs;
    }

    public Set<String> races() {
        return races;
    }

    public SkillNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    public void putNode(SkillNode node) {
        node.setCategoryId(id);
        nodes.put(node.id(), node);
    }

    /** Removes a node and every dangling connection that pointed at it. */
    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        for (SkillNode node : nodes.values()) {
            node.connections().removeIf(connection -> connection.fromId().equals(nodeId));
            node.exclusiveWith().remove(nodeId);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("desc", description);
        tag.put("icon", Nbt.saveStack(icon));
        tag.putInt("bg", backgroundColor);
        tag.putInt("accent", accentColor);
        tag.putInt("order", order);
        tag.putInt("version", version);
        tag.putBoolean("category_points", usesCategoryPoints);
        tag.putBoolean("locked", lockedByDefault);
        tag.putInt("min_level", minLevel);
        tag.put("unlock", Nbt.saveList(unlockRequirements, Requirement::save));
        tag.put("jobs", Nbt.saveStrings(jobs));
        tag.put("races", Nbt.saveStrings(races));
        tag.put("nodes", Nbt.saveList(nodes.values(), SkillNode::save));
        return tag;
    }

    public static SkillCategory load(CompoundTag tag) {
        SkillCategory category = new SkillCategory(tag.getString("id"), tag.getString("name"));
        category.description = tag.getString("desc");
        category.icon = Nbt.loadStack(tag, "icon");
        category.backgroundColor = tag.contains("bg") ? tag.getInt("bg") : 0xFF101018;
        category.accentColor = tag.contains("accent") ? tag.getInt("accent") : 0xFFFFAA00;
        category.order = tag.getInt("order");
        category.version = Math.max(1, tag.getInt("version"));
        category.usesCategoryPoints = tag.getBoolean("category_points");
        category.lockedByDefault = tag.getBoolean("locked");
        category.minLevel = Math.max(1, tag.getInt("min_level"));
        category.unlockRequirements.addAll(Nbt.loadList(tag, "unlock", Requirement::load));
        category.jobs.addAll(Nbt.loadStrings(tag, "jobs"));
        category.races.addAll(Nbt.loadStrings(tag, "races"));
        for (SkillNode node : Nbt.loadList(tag, "nodes", SkillNode::load)) {
            category.nodes.put(node.id(), node);
        }
        // Exclusivity declarations become EXCLUSIVE connections on the counterpart, so
        // either side of an exclusive branch blocks the other through the shared gate.
        SkillRules.mirrorExclusives(category.nodes);
        return category;
    }
}
