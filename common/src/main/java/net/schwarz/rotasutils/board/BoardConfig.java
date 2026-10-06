package net.schwarz.rotasutils.board;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BoardConfig {
    private String id;
    private String name = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.default_name");
    private String description = "";
    private BoardStyle style = BoardStyle.WOODEN_VILLAGE;
    private ItemStack icon = new ItemStack(Items.OAK_SIGN);
    private String faction = "";
    private double interactionDistance = 6.0;
    private String openSound = "minecraft:ui.button.click";
    private boolean visible = true;
    private boolean glow;
    private boolean particles;
    private int screenColor = 0xFF3BE8FF;
    private String featuredQuestId = "";

    private final List<String> questIds = new ArrayList<>();
    private final Set<String> categoryFilters = new LinkedHashSet<>();
    private final Set<DangerRank> rankFilters = new LinkedHashSet<>();
    private final Set<String> factionFilters = new LinkedHashSet<>();
    private final Set<String> dailyPool = new LinkedHashSet<>();
    private final Set<String> eventPool = new LinkedHashSet<>();
    private final Set<String> emergencyPool = new LinkedHashSet<>();

    private Rotation rotation = Rotation.NEVER;
    private int rotationSlots = 6;
    private long lastRotationEpochSeconds;
    private final List<String> rotatedSelection = new ArrayList<>();
    private long rotationSeed = 1;

    private int minPlayerLevel;
    private int maxPlayerLevel;
    private DangerRank requiredClearance;
    private String requiredDimension = "";
    private final List<Requirement> availability = new ArrayList<>();
    private int scheduleStartHour;
    private int scheduleEndHour;

    private boolean allowAccept = true;
    private boolean allowTurnIn = true;
    private boolean allowClaim = true;
    private boolean allowAbandon = true;
    private boolean allowPartyCreate = true;
    private boolean allowPartyJoin = true;
    private boolean showUnavailable = true;
    private boolean hideLocked;
    private boolean announceFeatured;
    private boolean announceEmergency = true;
    private boolean showQuestMarkers = true;

    public BoardConfig(String id) {
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
        this.name = name;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BoardStyle style() {
        return style;
    }

    public void setStyle(BoardStyle style) {
        this.style = style;
    }

    public ItemStack icon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon.isEmpty() ? new ItemStack(Items.OAK_SIGN) : icon;
    }

    public String faction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
    }

    public double interactionDistance() {
        return interactionDistance;
    }

    public void setInteractionDistance(double interactionDistance) {
        this.interactionDistance = Math.max(1.0, Math.min(64.0, interactionDistance));
    }

    public String openSound() {
        return openSound;
    }

    public void setOpenSound(String openSound) {
        this.openSound = openSound;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean glow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public boolean particles() {
        return particles;
    }

    public void setParticles(boolean particles) {
        this.particles = particles;
    }

    public int screenColor() {
        return screenColor;
    }

    public void setScreenColor(int screenColor) {
        this.screenColor = screenColor;
    }

    public String featuredQuestId() {
        return featuredQuestId;
    }

    public void setFeaturedQuestId(String featuredQuestId) {
        this.featuredQuestId = featuredQuestId == null ? "" : featuredQuestId;
    }

    public List<String> questIds() {
        return questIds;
    }

    public Set<String> categoryFilters() {
        return categoryFilters;
    }

    public Set<DangerRank> rankFilters() {
        return rankFilters;
    }

    public Set<String> factionFilters() {
        return factionFilters;
    }

    public Set<String> dailyPool() {
        return dailyPool;
    }

    public Set<String> eventPool() {
        return eventPool;
    }

    public Set<String> emergencyPool() {
        return emergencyPool;
    }

    public Rotation rotation() {
        return rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public int rotationSlots() {
        return Math.max(1, rotationSlots);
    }

    public void setRotationSlots(int rotationSlots) {
        this.rotationSlots = Math.max(1, Math.min(64, rotationSlots));
    }

    public long lastRotation() {
        return lastRotationEpochSeconds;
    }

    public void setLastRotation(long value) {
        this.lastRotationEpochSeconds = value;
    }

    public List<String> rotatedSelection() {
        return rotatedSelection;
    }

    public long rotationSeed() {
        return rotationSeed;
    }

    public void setRotationSeed(long rotationSeed) {
        this.rotationSeed = rotationSeed;
    }

    public int minPlayerLevel() {
        return minPlayerLevel;
    }

    public void setMinPlayerLevel(int minPlayerLevel) {
        this.minPlayerLevel = Math.max(0, minPlayerLevel);
    }

    public int maxPlayerLevel() {
        return maxPlayerLevel;
    }

    public void setMaxPlayerLevel(int maxPlayerLevel) {
        this.maxPlayerLevel = Math.max(0, maxPlayerLevel);
    }

    public DangerRank requiredClearance() {
        return requiredClearance;
    }

    public void setRequiredClearance(DangerRank requiredClearance) {
        this.requiredClearance = requiredClearance;
    }

    public String requiredDimension() {
        return requiredDimension;
    }

    public void setRequiredDimension(String requiredDimension) {
        this.requiredDimension = requiredDimension == null ? "" : requiredDimension;
    }

    public List<Requirement> availability() {
        return availability;
    }

    public int scheduleStartHour() {
        return scheduleStartHour;
    }

    public void setScheduleStartHour(int value) {
        this.scheduleStartHour = Math.max(0, Math.min(24, value));
    }

    public int scheduleEndHour() {
        return scheduleEndHour;
    }

    public void setScheduleEndHour(int value) {
        this.scheduleEndHour = Math.max(0, Math.min(24, value));
    }

    public boolean allowAccept() {
        return allowAccept;
    }

    public void setAllowAccept(boolean value) {
        this.allowAccept = value;
    }

    public boolean allowTurnIn() {
        return allowTurnIn;
    }

    public void setAllowTurnIn(boolean value) {
        this.allowTurnIn = value;
    }

    public boolean allowClaim() {
        return allowClaim;
    }

    public void setAllowClaim(boolean value) {
        this.allowClaim = value;
    }

    public boolean allowAbandon() {
        return allowAbandon;
    }

    public void setAllowAbandon(boolean value) {
        this.allowAbandon = value;
    }

    public boolean allowPartyCreate() {
        return allowPartyCreate;
    }

    public void setAllowPartyCreate(boolean value) {
        this.allowPartyCreate = value;
    }

    public boolean allowPartyJoin() {
        return allowPartyJoin;
    }

    public void setAllowPartyJoin(boolean value) {
        this.allowPartyJoin = value;
    }

    public boolean showUnavailable() {
        return showUnavailable;
    }

    public void setShowUnavailable(boolean value) {
        this.showUnavailable = value;
    }

    public boolean hideLocked() {
        return hideLocked;
    }

    public void setHideLocked(boolean value) {
        this.hideLocked = value;
    }

    public boolean announceFeatured() {
        return announceFeatured;
    }

    public void setAnnounceFeatured(boolean value) {
        this.announceFeatured = value;
    }

    public boolean announceEmergency() {
        return announceEmergency;
    }

    public void setAnnounceEmergency(boolean value) {
        this.announceEmergency = value;
    }

    public boolean showQuestMarkers() {
        return showQuestMarkers;
    }

    public void setShowQuestMarkers(boolean value) {
        this.showQuestMarkers = value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("desc", description);
        tag.putString("style", style.name());
        tag.put("icon", Nbt.saveStack(icon));
        tag.putString("faction", faction);
        tag.putDouble("distance", interactionDistance);
        tag.putString("sound", openSound);
        tag.putBoolean("visible", visible);
        tag.putBoolean("glow", glow);
        tag.putBoolean("particles", particles);
        tag.putInt("screen_color", screenColor);
        tag.putString("featured", featuredQuestId);

        tag.put("quests", Nbt.saveStrings(questIds));
        tag.put("categories", Nbt.saveStrings(categoryFilters));
        List<String> ranks = new ArrayList<>();
        for (DangerRank rank : rankFilters) {
            ranks.add(rank.name());
        }
        tag.put("ranks", Nbt.saveStrings(ranks));
        tag.put("factions", Nbt.saveStrings(factionFilters));
        tag.put("daily", Nbt.saveStrings(dailyPool));
        tag.put("event", Nbt.saveStrings(eventPool));
        tag.put("emergency", Nbt.saveStrings(emergencyPool));

        tag.putString("rotation", rotation.name());
        tag.putInt("rotation_slots", rotationSlots);
        tag.putLong("last_rotation", lastRotationEpochSeconds);
        tag.put("rotated", Nbt.saveStrings(rotatedSelection));
        tag.putLong("rotation_seed", rotationSeed);

        tag.putInt("min_level", minPlayerLevel);
        tag.putInt("max_level", maxPlayerLevel);
        if (requiredClearance != null) {
            tag.putString("clearance", requiredClearance.name());
        }
        tag.putString("dimension", requiredDimension);
        tag.put("availability", Nbt.saveList(availability, Requirement::save));
        tag.putInt("schedule_start", scheduleStartHour);
        tag.putInt("schedule_end", scheduleEndHour);

        tag.putBoolean("allow_accept", allowAccept);
        tag.putBoolean("allow_turn_in", allowTurnIn);
        tag.putBoolean("allow_claim", allowClaim);
        tag.putBoolean("allow_abandon", allowAbandon);
        tag.putBoolean("allow_party_create", allowPartyCreate);
        tag.putBoolean("allow_party_join", allowPartyJoin);
        tag.putBoolean("show_unavailable", showUnavailable);
        tag.putBoolean("hide_locked", hideLocked);
        tag.putBoolean("announce_featured", announceFeatured);
        tag.putBoolean("announce_emergency", announceEmergency);
        tag.putBoolean("show_markers", showQuestMarkers);
        return tag;
    }

    public BoardConfig copyAs(String newId) {
        CompoundTag tag = save();
        tag.putString("id", newId);
        return load(tag);
    }

    public static BoardConfig load(CompoundTag tag) {
        BoardConfig board = new BoardConfig(tag.getString("id"));
        board.name = tag.getString("name");
        board.description = tag.getString("desc");
        board.style = Nbt.readEnum(tag, "style", BoardStyle.class, BoardStyle.WOODEN_VILLAGE);
        board.icon = Nbt.loadStack(tag, "icon");
        board.faction = tag.getString("faction");
        board.interactionDistance = tag.contains("distance") ? tag.getDouble("distance") : 6.0;
        board.openSound = tag.getString("sound");
        board.visible = !tag.contains("visible") || tag.getBoolean("visible");
        board.glow = tag.getBoolean("glow");
        board.particles = tag.getBoolean("particles");
        board.screenColor = tag.contains("screen_color") ? tag.getInt("screen_color") : 0xFF3BE8FF;
        board.featuredQuestId = tag.getString("featured");

        board.questIds.addAll(Nbt.loadStrings(tag, "quests"));
        board.categoryFilters.addAll(Nbt.loadStringSet(tag, "categories"));
        for (String name : Nbt.loadStrings(tag, "ranks")) {
            board.rankFilters.add(DangerRank.byName(name, DangerRank.F));
        }
        board.factionFilters.addAll(Nbt.loadStringSet(tag, "factions"));
        board.dailyPool.addAll(Nbt.loadStringSet(tag, "daily"));
        board.eventPool.addAll(Nbt.loadStringSet(tag, "event"));
        board.emergencyPool.addAll(Nbt.loadStringSet(tag, "emergency"));

        board.rotation = Nbt.readEnum(tag, "rotation", Rotation.class, Rotation.NEVER);
        board.rotationSlots = Math.max(1, tag.getInt("rotation_slots"));
        board.lastRotationEpochSeconds = tag.getLong("last_rotation");
        board.rotatedSelection.addAll(Nbt.loadStrings(tag, "rotated"));
        board.rotationSeed = tag.contains("rotation_seed") ? tag.getLong("rotation_seed") : 1;

        board.minPlayerLevel = tag.getInt("min_level");
        board.maxPlayerLevel = tag.getInt("max_level");
        board.requiredClearance = tag.contains("clearance") ? DangerRank.byName(tag.getString("clearance"), null) : null;
        board.requiredDimension = tag.getString("dimension");
        board.availability.addAll(Nbt.loadList(tag, "availability", Requirement::load));
        board.scheduleStartHour = tag.getInt("schedule_start");
        board.scheduleEndHour = tag.getInt("schedule_end");

        board.allowAccept = !tag.contains("allow_accept") || tag.getBoolean("allow_accept");
        board.allowTurnIn = !tag.contains("allow_turn_in") || tag.getBoolean("allow_turn_in");
        board.allowClaim = !tag.contains("allow_claim") || tag.getBoolean("allow_claim");
        board.allowAbandon = !tag.contains("allow_abandon") || tag.getBoolean("allow_abandon");
        board.allowPartyCreate = !tag.contains("allow_party_create") || tag.getBoolean("allow_party_create");
        board.allowPartyJoin = !tag.contains("allow_party_join") || tag.getBoolean("allow_party_join");
        board.showUnavailable = !tag.contains("show_unavailable") || tag.getBoolean("show_unavailable");
        board.hideLocked = tag.getBoolean("hide_locked");
        board.announceFeatured = tag.getBoolean("announce_featured");
        board.announceEmergency = !tag.contains("announce_emergency") || tag.getBoolean("announce_emergency");
        board.showQuestMarkers = !tag.contains("show_markers") || tag.getBoolean("show_markers");
        return board;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static BoardConfig read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }

    public enum Rotation {
        NEVER("Never rotate", 0),
        HOURLY("Hourly", 3600),
        DAILY("Daily", 86400),
        WEEKLY("Weekly", 604800),
        RANDOM("Random on every open", -1),
        BY_CATEGORY("Category rotation", 86400),
        BY_RANK("Rank rotation", 86400),
        EVENT("Event based", 0),
        BY_PLAYER_LEVEL("Player level based", -1);

        public static final Rotation[] VALUES = values();

        private final String display;
        private final int periodSeconds;

        Rotation(String display, int periodSeconds) {
            this.display = display;
            this.periodSeconds = periodSeconds;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("board_rotation", this, display);
        }

        public int periodSeconds() {
            return periodSeconds;
        }
    }
}
