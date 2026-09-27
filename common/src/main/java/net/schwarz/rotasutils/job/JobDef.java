package net.schwarz.rotasutils.job;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.util.Nbt;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * A job a player can take, such as Warrior or Mage.
 *
 * <p>A job is identity only: skill categories name the jobs that may use them, so the job
 * decides which trees a player sees without duplicating any tree data here.</p>
 */
public final class JobDef {
    private String id;
    private String name = "New Job";
    private String description = "";
    private ItemStack icon = new ItemStack(Items.IRON_SWORD);
    private int color = 0xFFC98B3D;
    private int minLevel = 1;
    private int order;
    private boolean enabled = true;
    private boolean mainAllowed = true;
    private boolean subAllowed = true;
    private double subJobXpRate = 0.25;
    private double subPassiveCap = 0.30;
    private int skillPointsPerMasteryLevel = 1;
    private JobMasteryCurve masteryCurve = JobMasteryCurve.defaults();
    private final Set<String> trainerNpcIds = new LinkedHashSet<>();
    /** Registry ids or #tags associated with this job. Association is informational until a rule consumes it. */
    private final Set<String> itemSelectors = new LinkedHashSet<>();
    /** Stable activity ids that may award mastery, e.g. mining, repair, or horse_care. */
    private final Set<String> masteryActivities = new LinkedHashSet<>();
    private final List<JobAttributeModifier> attributeModifiers = new ArrayList<>();
    /** Season sub-job EXP multiplier on the tier base (Chef 0.144, Blacksmith 1.5). 0 earns nothing. */
    private double productionXpRate;
    /** What this job produces and the sub-job level that unlocks each item (its tier). */
    private final List<ProductionEntry> production = new ArrayList<>();
    public static final int MAX_PRODUCTION = 256;

    /**
     * One row of a job's unlock table: an activity, an item or block id (or #tag), and the level it unlocks at.
     * The unlock level decides the tier, so the table doubles as the recipe unlock list.
     */
    public record ProductionEntry(Activity activity, String selector, int unlockLevel) {
        public enum Activity { CRAFT, SMELT, MINE, HARVEST, FISH, BREW }

        public ProductionEntry {
            if (activity == null) throw new IllegalArgumentException("Production entry needs an activity");
            selector = selector == null ? "" : selector.trim();
            if (!selector.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid production item or tag: " + selector);
            unlockLevel = Math.max(1, Math.min(10000, unlockLevel));
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("activity", activity.name());
            tag.putString("selector", selector);
            tag.putInt("level", unlockLevel);
            return tag;
        }

        public static ProductionEntry load(CompoundTag tag) {
            return new ProductionEntry(Nbt.readEnum(tag, "activity", Activity.class, Activity.CRAFT),
                    tag.getString("selector"), tag.getInt("level"));
        }

        /** "CRAFT minecraft:bread 1", the text form the job editor uses. */
        public String encode() {
            return activity.name() + " " + selector + " " + unlockLevel;
        }

        public static ProductionEntry decode(String text) {
            String[] parts = text.trim().split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("Write production as: ACTIVITY item level");
            return new ProductionEntry(Activity.valueOf(parts[0].toUpperCase(java.util.Locale.ROOT)), parts[1],
                    Integer.parseInt(parts[2]));
        }
    }

    public double productionXpRate() { return productionXpRate; }
    public void setProductionXpRate(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("Production EXP rate outside 0..100");
        productionXpRate = value;
    }
    public List<ProductionEntry> production() { return production; }

    public JobDef(String id) {
        this.id = id;
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
        this.name = name == null || name.isBlank() ? id : name;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public ItemStack icon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon == null || icon.isEmpty() ? new ItemStack(Items.IRON_SWORD) : icon;
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color | 0xFF000000;
    }

    public int minLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = Math.max(1, Math.min(10000, minLevel));
    }

    public int order() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean mainAllowed() { return mainAllowed; }
    public void setMainAllowed(boolean value) { mainAllowed=value; }
    public boolean subAllowed() { return subAllowed; }
    public void setSubAllowed(boolean value) { subAllowed=value; }
    public double subJobXpRate() { return subJobXpRate; }
    public void setSubJobXpRate(double value) { if(!Double.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("Sub-job XP rate outside 0..1"); subJobXpRate=value; }
    public double subPassiveCap() { return subPassiveCap; }
    public void setSubPassiveCap(double value) { if(!Double.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("Sub-job passive cap outside 0..1"); subPassiveCap=value; }
    public int skillPointsPerMasteryLevel() { return skillPointsPerMasteryLevel; }
    public void setSkillPointsPerMasteryLevel(int value) { skillPointsPerMasteryLevel=Math.max(0,Math.min(1000,value)); }
    public JobMasteryCurve masteryCurve() { return masteryCurve; }
    public void setMasteryCurve(JobMasteryCurve value) { masteryCurve=java.util.Objects.requireNonNull(value); }
    public Set<String> trainerNpcIds() { return trainerNpcIds; }
    public Set<String> itemSelectors() { return itemSelectors; }
    public Set<String> masteryActivities() { return masteryActivities; }
    public List<JobAttributeModifier> attributeModifiers() { return attributeModifiers; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("desc", description);
        tag.put("icon", Nbt.saveStack(icon));
        tag.putInt("color", color);
        tag.putInt("min_level", minLevel);
        tag.putInt("order", order);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("main_allowed",mainAllowed); tag.putBoolean("sub_allowed",subAllowed);
        tag.putDouble("sub_xp_rate",subJobXpRate); tag.putDouble("sub_passive_cap",subPassiveCap);
        tag.putInt("mastery_points_per_level",skillPointsPerMasteryLevel); tag.put("mastery_curve",masteryCurve.save());
        tag.put("trainers",Nbt.saveStrings(trainerNpcIds));
        tag.put("item_selectors", Nbt.saveStrings(itemSelectors));
        tag.put("mastery_activities", Nbt.saveStrings(masteryActivities));
        tag.put("attribute_modifiers", Nbt.saveList(attributeModifiers, JobAttributeModifier::save));
        tag.putDouble("production_rate", productionXpRate);
        tag.put("production", Nbt.saveList(production, ProductionEntry::save));
        return tag;
    }

    public static JobDef load(CompoundTag tag) {
        JobDef job = new JobDef(tag.getString("id"));
        job.name = tag.contains("name") ? tag.getString("name") : job.id;
        job.description = tag.getString("desc");
        job.setIcon(Nbt.loadStack(tag, "icon"));
        job.color = tag.contains("color") ? tag.getInt("color") | 0xFF000000 : 0xFFC98B3D;
        job.setMinLevel(tag.contains("min_level") ? tag.getInt("min_level") : 1);
        job.order = tag.getInt("order");
        job.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        job.mainAllowed=!tag.contains("main_allowed")||tag.getBoolean("main_allowed");
        job.subAllowed=!tag.contains("sub_allowed")||tag.getBoolean("sub_allowed");
        job.setSubJobXpRate(tag.contains("sub_xp_rate")?tag.getDouble("sub_xp_rate"):0.25);
        job.setSubPassiveCap(tag.contains("sub_passive_cap")?tag.getDouble("sub_passive_cap"):0.30);
        job.setSkillPointsPerMasteryLevel(tag.contains("mastery_points_per_level")?tag.getInt("mastery_points_per_level"):1);
        job.masteryCurve=tag.contains("mastery_curve")?JobMasteryCurve.load(tag.getCompound("mastery_curve")):JobMasteryCurve.defaults();
        job.trainerNpcIds.addAll(Nbt.loadStringSet(tag,"trainers"));
        job.itemSelectors.addAll(Nbt.loadStringSet(tag, "item_selectors"));
        job.masteryActivities.addAll(Nbt.loadStringSet(tag, "mastery_activities"));
        job.attributeModifiers.addAll(Nbt.loadList(tag, "attribute_modifiers", JobAttributeModifier::load));
        job.setProductionXpRate(tag.contains("production_rate") ? tag.getDouble("production_rate") : 0);
        for (ProductionEntry entry : Nbt.loadList(tag, "production", ProductionEntry::load)) {
            if (job.production.size() < MAX_PRODUCTION) job.production.add(entry);
        }
        return job;
    }
}
