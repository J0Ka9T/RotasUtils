package net.schwarz.rotasutils.level;

import net.minecraft.nbt.CompoundTag;

/** Per-source tuning: toggle, amount, multiplier, cooldown, caps and anti-farm. */
public final class XpSourceConfig {
    private final XpSource source;
    private boolean enabled;
    private int baseAmount;
    private double multiplier = 1.0;
    private int cooldownSeconds;
    private int dailyLimit;
    private int perTargetLimit;
    private boolean antiFarm;
    private String dimension = "";
    private int minLevel;
    private int maxLevel;
    /** Fraction of the award mirrored to nearby party members, 0 disables sharing. */
    private double partyShare;

    public XpSourceConfig(XpSource source) {
        this.source = source;
        this.enabled = source.enabledByDefault();
        this.baseAmount = source.defaultAmount();
        this.antiFarm = source.antiFarmByDefault();
        this.perTargetLimit = switch (source) {
            case MOB_KILL -> 120;
            case BOSS_KILL -> 12;
            default -> source.antiFarmByDefault() ? 24 : 0;
        };
    }

    public XpSource source() {
        return source;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int baseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(int baseAmount) {
        this.baseAmount = Math.max(0, baseAmount);
    }

    public double multiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = Math.max(0, multiplier);
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = Math.max(0, dailyLimit);
    }

    public int perTargetLimit() {
        return perTargetLimit;
    }

    public void setPerTargetLimit(int perTargetLimit) {
        this.perTargetLimit = Math.max(0, perTargetLimit);
    }

    public boolean antiFarm() {
        return antiFarm;
    }

    public void setAntiFarm(boolean antiFarm) {
        this.antiFarm = antiFarm;
    }

    public String dimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension == null ? "" : dimension;
    }

    public int minLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public double partyShare() {
        return partyShare;
    }

    public void setPartyShare(double partyShare) {
        this.partyShare = Math.max(0, Math.min(1, partyShare));
    }

    public long award() {
        return Math.round(baseAmount * multiplier);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("source", source.name());
        tag.putBoolean("enabled", enabled);
        tag.putInt("amount", baseAmount);
        tag.putDouble("mult", multiplier);
        tag.putInt("cooldown", cooldownSeconds);
        tag.putInt("daily", dailyLimit);
        tag.putInt("per_target", perTargetLimit);
        tag.putBoolean("anti_farm", antiFarm);
        tag.putString("dimension", dimension);
        tag.putInt("min_level", minLevel);
        tag.putInt("max_level", maxLevel);
        tag.putDouble("party_share", partyShare);
        return tag;
    }

    public static XpSourceConfig load(CompoundTag tag) {
        XpSource source = net.schwarz.rotasutils.util.Nbt.readEnum(tag, "source", XpSource.class, XpSource.MOB_KILL);
        XpSourceConfig config = new XpSourceConfig(source);
        config.enabled = tag.contains("enabled") ? tag.getBoolean("enabled") : source.enabledByDefault();
        config.baseAmount = tag.getInt("amount");
        config.multiplier = tag.contains("mult") ? tag.getDouble("mult") : 1.0;
        config.cooldownSeconds = tag.getInt("cooldown");
        config.dailyLimit = tag.getInt("daily");
        config.perTargetLimit = tag.getInt("per_target");
        config.antiFarm = tag.getBoolean("anti_farm");
        config.dimension = tag.getString("dimension");
        config.minLevel = tag.getInt("min_level");
        config.maxLevel = tag.getInt("max_level");
        config.partyShare = tag.getDouble("party_share");
        return config;
    }
}
