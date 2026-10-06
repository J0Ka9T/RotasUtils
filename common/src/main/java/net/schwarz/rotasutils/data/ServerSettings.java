package net.schwarz.rotasutils.data;

import net.minecraft.nbt.CompoundTag;

public final class ServerSettings {
    private int adminOpLevel = 2;
    private boolean allowQuestCommands = true;
    private boolean requireBoardForAccept = true;
    private int maxActiveQuests = 10;
    private boolean partySystemEnabled = true;
    private int maxPartySize = 10;
    private double partyNearbyRadius = 64.0;
    private boolean pvpQuestsEnabled = true;
    private boolean antiFarmEnabled = true;
    private int antiFarmMemorySeconds = 3600;
    private int clientRequestCooldownMillis = 200;
    private int autosaveIntervalSeconds = 120;
    private boolean auditLogEnabled = true;
    private boolean validateOnPublish = true;
    private long jobChangeCooldownSeconds = 86_400;
    private boolean waystonesEnabled = true;
    private long waystoneDiscoverCost = 100;
    private long waystoneWarpCost = 50;
    private long waystoneWarpCostPerThousandBlocks = 25;
    private boolean monsterDropsEnabled = true;

    public long jobChangeCooldownSeconds() {
        return jobChangeCooldownSeconds;
    }

    public void setJobChangeCooldownSeconds(long value) {
        this.jobChangeCooldownSeconds = Math.max(0, value);
    }

    public boolean waystonesEnabled() {
        return waystonesEnabled;
    }

    public void setWaystonesEnabled(boolean value) {
        this.waystonesEnabled = value;
    }

    public long waystoneDiscoverCost() {
        return waystoneDiscoverCost;
    }

    public void setWaystoneDiscoverCost(long value) {
        this.waystoneDiscoverCost = clampCost(value);
    }

    public long waystoneWarpCost() {
        return waystoneWarpCost;
    }

    public void setWaystoneWarpCost(long value) {
        this.waystoneWarpCost = clampCost(value);
    }

    public long waystoneWarpCostPerThousandBlocks() {
        return waystoneWarpCostPerThousandBlocks;
    }

    public void setWaystoneWarpCostPerThousandBlocks(long value) {
        this.waystoneWarpCostPerThousandBlocks = clampCost(value);
    }

    public boolean monsterDropsEnabled() {
        return monsterDropsEnabled;
    }

    public void setMonsterDropsEnabled(boolean value) {
        this.monsterDropsEnabled = value;
    }

    private static long clampCost(long value) {
        return Math.max(0, Math.min(1_000_000, value));
    }

    public int adminOpLevel() {
        return adminOpLevel;
    }

    public void setAdminOpLevel(int adminOpLevel) {
        this.adminOpLevel = Math.max(2, Math.min(4, adminOpLevel));
    }

    public boolean allowQuestCommands() {
        return allowQuestCommands;
    }

    public void setAllowQuestCommands(boolean value) {
        this.allowQuestCommands = value;
    }

    public boolean requireBoardForAccept() {
        return requireBoardForAccept;
    }

    public void setRequireBoardForAccept(boolean value) {
        this.requireBoardForAccept = value;
    }

    public int maxActiveQuests() {
        return maxActiveQuests;
    }

    public void setMaxActiveQuests(int value) {
        this.maxActiveQuests = Math.max(1, value);
    }

    public boolean partySystemEnabled() {
        return partySystemEnabled;
    }

    public void setPartySystemEnabled(boolean value) {
        this.partySystemEnabled = value;
    }

    public int maxPartySize() {
        return maxPartySize;
    }

    public void setMaxPartySize(int value) {
        this.maxPartySize = Math.max(1, Math.min(64, value));
    }

    public double partyNearbyRadius() {
        return partyNearbyRadius;
    }

    public void setPartyNearbyRadius(double value) {
        this.partyNearbyRadius = Math.max(4, value);
    }

    public boolean pvpQuestsEnabled() {
        return pvpQuestsEnabled;
    }

    public void setPvpQuestsEnabled(boolean value) {
        this.pvpQuestsEnabled = value;
    }

    public boolean antiFarmEnabled() {
        return antiFarmEnabled;
    }

    public void setAntiFarmEnabled(boolean value) {
        this.antiFarmEnabled = value;
    }

    public int antiFarmMemorySeconds() {
        return antiFarmMemorySeconds;
    }

    public void setAntiFarmMemorySeconds(int value) {
        this.antiFarmMemorySeconds = Math.max(60, value);
    }

    public int clientRequestCooldownMillis() {
        return clientRequestCooldownMillis;
    }

    public void setClientRequestCooldownMillis(int value) {
        this.clientRequestCooldownMillis = Math.max(0, value);
    }

    public int autosaveIntervalSeconds() {
        return autosaveIntervalSeconds;
    }

    public void setAutosaveIntervalSeconds(int value) {
        this.autosaveIntervalSeconds = Math.max(10, value);
    }

    public boolean auditLogEnabled() {
        return auditLogEnabled;
    }

    public void setAuditLogEnabled(boolean value) {
        this.auditLogEnabled = value;
    }

    public boolean validateOnPublish() {
        return validateOnPublish;
    }

    public void setValidateOnPublish(boolean value) {
        this.validateOnPublish = value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("admin_op_level", adminOpLevel);
        tag.putBoolean("quest_commands", allowQuestCommands);
        tag.putBoolean("require_board", requireBoardForAccept);
        tag.putInt("max_active", maxActiveQuests);
        tag.putBoolean("party", partySystemEnabled);
        tag.putInt("max_party", maxPartySize);
        tag.putDouble("party_radius", partyNearbyRadius);
        tag.putBoolean("pvp_quests", pvpQuestsEnabled);
        tag.putBoolean("anti_farm", antiFarmEnabled);
        tag.putInt("anti_farm_memory", antiFarmMemorySeconds);
        tag.putInt("request_cooldown", clientRequestCooldownMillis);
        tag.putInt("autosave", autosaveIntervalSeconds);
        tag.putBoolean("audit", auditLogEnabled);
        tag.putBoolean("validate", validateOnPublish);
        tag.putLong("job_cooldown", jobChangeCooldownSeconds);
        tag.putBoolean("waystones", waystonesEnabled);
        tag.putLong("waystone_discover_cost", waystoneDiscoverCost);
        tag.putLong("waystone_warp_cost", waystoneWarpCost);
        tag.putLong("waystone_warp_distance_cost", waystoneWarpCostPerThousandBlocks);
        tag.putBoolean("monster_drops", monsterDropsEnabled);
        return tag;
    }

    public static ServerSettings load(CompoundTag tag) {
        ServerSettings settings = new ServerSettings();
        settings.adminOpLevel = tag.contains("admin_op_level")
                ? Math.max(2, Math.min(4, tag.getInt("admin_op_level"))) : 2;
        settings.allowQuestCommands = !tag.contains("quest_commands") || tag.getBoolean("quest_commands");
        settings.requireBoardForAccept = !tag.contains("require_board") || tag.getBoolean("require_board");
        settings.maxActiveQuests = tag.contains("max_active") ? Math.max(1, tag.getInt("max_active")) : 10;
        settings.partySystemEnabled = !tag.contains("party") || tag.getBoolean("party");
        settings.maxPartySize = tag.contains("max_party") ? Math.max(1, Math.min(64, tag.getInt("max_party"))) : 10;
        settings.partyNearbyRadius = tag.contains("party_radius") && Double.isFinite(tag.getDouble("party_radius"))
                ? Math.max(4, tag.getDouble("party_radius")) : 64.0;
        settings.pvpQuestsEnabled = !tag.contains("pvp_quests") || tag.getBoolean("pvp_quests");
        settings.antiFarmEnabled = !tag.contains("anti_farm") || tag.getBoolean("anti_farm");
        settings.antiFarmMemorySeconds = tag.contains("anti_farm_memory")
                ? Math.max(60, tag.getInt("anti_farm_memory")) : 3600;
        settings.clientRequestCooldownMillis = tag.contains("request_cooldown")
                ? Math.max(0, tag.getInt("request_cooldown")) : 200;
        settings.autosaveIntervalSeconds = tag.contains("autosave")
                ? Math.max(10, tag.getInt("autosave")) : 120;
        settings.auditLogEnabled = !tag.contains("audit") || tag.getBoolean("audit");
        settings.validateOnPublish = !tag.contains("validate") || tag.getBoolean("validate");
        settings.jobChangeCooldownSeconds = tag.contains("job_cooldown") ? Math.max(0, tag.getLong("job_cooldown")) : 86_400;
        settings.waystonesEnabled = !tag.contains("waystones") || tag.getBoolean("waystones");
        settings.waystoneDiscoverCost = tag.contains("waystone_discover_cost")
                ? clampCost(tag.getLong("waystone_discover_cost")) : 100;
        settings.waystoneWarpCost = tag.contains("waystone_warp_cost")
                ? clampCost(tag.getLong("waystone_warp_cost")) : 50;
        settings.waystoneWarpCostPerThousandBlocks = tag.contains("waystone_warp_distance_cost")
                ? clampCost(tag.getLong("waystone_warp_distance_cost")) : 25;
        settings.monsterDropsEnabled = !tag.contains("monster_drops") || tag.getBoolean("monster_drops");
        return settings;
    }
}
