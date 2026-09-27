package net.schwarz.rotasutils.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.server.KernelQuestAdapter;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.util.Nbt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single world-attached store for all RotasUtils content and player records.
 *
 * <p>Templates (quests, boards, skill categories, level config) and player progress
 * live in the same {@link SavedData} instance but in separate maps, so editing content
 * never rewrites player records. Minecraft's SavedData writes through a temp file and
 * renames, which gives the atomic save the spec asks for.
 */
public final class RotasData extends SavedData {
    public static final String FILE_ID = Rotasutils.MOD_ID + "_data";

    private final Map<String, QuestDef> quests = new LinkedHashMap<>();
    private final Map<String, BoardConfig> boards = new LinkedHashMap<>();
    private final Map<String, net.schwarz.rotasutils.npc.NpcDef> npcs = new LinkedHashMap<>();
    private final Map<String, SkillCategory> categories = new LinkedHashMap<>();
    private final Map<String, net.schwarz.rotasutils.job.JobDef> jobs = new LinkedHashMap<>();
    /** Admin level zones; mobs without a monster profile read their band from here. */
    private final Map<String, net.schwarz.rotasutils.core.ZoneDef> zones = new LinkedHashMap<>();
    private final Map<String, net.schwarz.rotasutils.house.HouseDefinition> houses = new LinkedHashMap<>();
    private final Map<String, net.schwarz.rotasutils.house.HouseTenancy> houseTenancies = new LinkedHashMap<>();
    private net.schwarz.rotasutils.house.HouseConfig houseConfig = net.schwarz.rotasutils.house.HouseConfig.defaults();
    private final Map<String, net.schwarz.rotasutils.house.HouseSettings> houseSettings = new LinkedHashMap<>();
    /** Warp pillars, keyed by {@link net.schwarz.rotasutils.waystone.Waystone#id()}. */
    private final Map<String, net.schwarz.rotasutils.waystone.Waystone> waystones = new LinkedHashMap<>();
    public static final int WAYSTONE_LIMIT = 512;
    /** Set once the starter stats were installed, so deleting them all does not bring them back. */
    /** Mining sites, with the timers of their spent nodes. */
    private final Map<String, net.schwarz.rotasutils.mine.MiningSite> miningSites = new LinkedHashMap<>();
    /** Title (ฉายา) definitions, and who holds each unique one. */
    private final Map<String, net.schwarz.rotasutils.title.TitleDef> titles = new LinkedHashMap<>();
    private final Map<String, UUID> uniqueTitleOwners = new LinkedHashMap<>();
    private boolean titlesSeeded;
    /** Starter-title batches added after a world's first seeding; each is installed once, then respected. */
    private final java.util.Set<String> seededTitleBatches = new java.util.LinkedHashSet<>();
    /** Nemeses by id, with their bodies, victims and ambush timers. */
    private final Map<Integer, net.schwarz.rotasutils.nemesis.Nemesis> nemeses = new LinkedHashMap<>();
    private int nextNemesisId;
    /** Running world events by id. */
    private final Map<Integer, net.schwarz.rotasutils.worldevent.WorldEvent> worldEvents = new LinkedHashMap<>();
    private int nextWorldEventId;
    /** Level/stat system revision every player record was last wiped to; see ProgressService.STAT_SYSTEM_VERSION. */
    private int statSystemVersion;
    /** Live zone spawn point state by "zoneId#pointId"; zone content lives in {@link #zones}. */
    private final Map<String, net.schwarz.rotasutils.core.ZoneEncounterState> zoneEncounters = new java.util.LinkedHashMap<>();
    public static final int ENCOUNTER_LIMIT = 4096;
    private final Map<UUID, PlayerProgress> players = new LinkedHashMap<>();
    private final java.util.Set<UUID> kernelTransactions = new java.util.HashSet<>();
    private net.schwarz.rotasutils.server.RpgKernel kernel;
    private LevelConfig levelConfig = new LevelConfig();
    private ServerSettings serverSettings = new ServerSettings();
    public void setServerSettings(ServerSettings settings) { this.serverSettings = settings; setDirty(); }
    private final List<String> auditLog = new ArrayList<>();
    /** World-scoped windowed counters: bounty limits and merchant stock share this bounded store. */
    private final Map<String, long[]> counters = new LinkedHashMap<>();
    private net.schwarz.rotasutils.core.ContentHistory contentHistory = new net.schwarz.rotasutils.core.ContentHistory(this::setDirty);
    private net.schwarz.rotasutils.core.ConfigHistory configHistory = new net.schwarz.rotasutils.core.ConfigHistory(this::setDirty);

    public net.schwarz.rotasutils.core.ConfigHistory configHistory() { return configHistory; }

    private static RotasData instance;

    /** Set when the stored file exists but could not be read; see {@link #fresh(MinecraftServer)}. */
    private boolean saveBlocked;

    public static RotasData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("RotasUtils: overworld unavailable");
        }
        RotasData data = overworld.getDataStorage().computeIfAbsent(RotasData::load, () -> fresh(server), FILE_ID);
        // Logout events still fire while a stopping server disconnects everyone; pinning the
        // static then would keep the old world's store alive after clearInstance().
        if (server.isRunning()) {
            instance = data;
        }
        return data;
    }

    /**
     * Supplier for an absent store. DimensionDataStorage logs and swallows any load exception and
     * then asks for a new instance, which the next autosave would write over the real file, wiping
     * every player record. An existing file at this point therefore means the load failed: keep a
     * copy, refuse to save over it, and say so loudly.
     */
    private static RotasData fresh(MinecraftServer server) {
        RotasData data = new RotasData();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat");
        if (Files.exists(file)) {
            data.blockSaves();
            Path backup = file.resolveSibling(FILE_ID + ".dat.load-failed-" + System.currentTimeMillis());
            try {
                Files.copy(file, backup);
            } catch (IOException e) {
                Rotasutils.LOG.error("RotasUtils could not back up {}", file, e);
            }
            Rotasutils.LOG.error("RotasUtils could not load {}. Saving is disabled for this session so the file "
                    + "is not overwritten; a copy was written to {}. Repair or restore the file, then restart.",
                    file, backup);
        }
        return data;
    }

    /** Stops this store from ever being written; used when the real file failed to load. */
    void blockSaves() {
        saveBlocked = true;
    }

    public boolean saveBlocked() {
        return saveBlocked;
    }

    @Override
    public boolean isDirty() {
        return !saveBlocked && super.isDirty();
    }

    /** Server-side convenience accessor for code that already knows a server exists. */
    public static RotasData instance() {
        return instance;
    }

    public static void clearInstance() {
        instance = null;
    }

    public net.schwarz.rotasutils.server.RpgKernel kernel() {
        return kernel;
    }

    public net.schwarz.rotasutils.core.ContentHistory contentHistory() { return contentHistory; }

    public void setKernel(net.schwarz.rotasutils.server.RpgKernel kernel) {
        this.kernel = kernel;
    }

    // Quests ---------------------------------------------------------------

    public Map<String, QuestDef> quests() {
        return quests;
    }

    public QuestDef quest(String id) {
        return quests.get(id);
    }

    public void putQuest(QuestDef quest) {
        quests.put(quest.id(), quest);
        setDirty();
    }

    /** Publishes kernel-owned canonical quests without replacing independently authored definitions. */
    public void reconcileKernelQuests(Map<String, KernelQuestAdapter.Projection> projections) {
        java.util.Objects.requireNonNull(projections, "projections");
        for (Map.Entry<String, KernelQuestAdapter.Projection> entry : projections.entrySet()) {
            String id = entry.getKey();
            QuestDef projected = java.util.Objects.requireNonNull(entry.getValue(), "projection").quest();
            if (!id.equals(projected.id()) || !id.equals(projected.kernelOrigin())) {
                throw new IllegalArgumentException("Invalid kernel quest projection: " + id);
            }
            QuestDef existing = quests.get(id);
            if (existing != null && !id.equals(existing.kernelOrigin())) {
                throw new IllegalArgumentException("Quest ID collision: " + id);
            }
        }

        boolean changed = false;
        for (Map.Entry<String, KernelQuestAdapter.Projection> entry : projections.entrySet()) {
            QuestDef projected = entry.getValue().quest();
            QuestDef existing = quests.get(entry.getKey());
            if (existing == null || !existing.save().equals(projected.save())) {
                quests.put(entry.getKey(), projected);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public void removeQuest(String id) {
        if (quests.remove(id) != null) {
            for (BoardConfig board : boards.values()) {
                board.questIds().remove(id);
                board.dailyPool().remove(id);
                board.eventPool().remove(id);
                board.emergencyPool().remove(id);
                if (board.featuredQuestId().equals(id)) {
                    board.setFeaturedQuestId("");
                }
            }
            setDirty();
        }
    }

    // Boards ---------------------------------------------------------------

    public Map<String, BoardConfig> boards() {
        return boards;
    }

    public BoardConfig board(String id) {
        return boards.get(id);
    }

    public void putBoard(BoardConfig board) {
        boards.put(board.id(), board);
        setDirty();
    }

    /** Removes a board and every quest's link to it; false when no such board existed. */
    public boolean removeBoard(String id) {
        if (boards.remove(id) == null) {
            return false;
        }
        for (QuestDef quest : quests.values()) {
            quest.boardIds().remove(id);
        }
        setDirty();
        return true;
    }

    // NPCs -----------------------------------------------------------------

    public Map<String, net.schwarz.rotasutils.npc.NpcDef> npcs() {
        return npcs;
    }

    public net.schwarz.rotasutils.npc.NpcDef npc(String id) {
        return npcs.get(id);
    }

    public void putNpc(net.schwarz.rotasutils.npc.NpcDef npc) {
        npcs.put(npc.id(), npc);
        setDirty();
    }

    public void removeNpc(String id) {
        if (npcs.remove(id) != null) {
            setDirty();
        }
    }

    // Skill tree -----------------------------------------------------------

    public Map<String, SkillCategory> categories() {
        return categories;
    }

    public SkillCategory category(String id) {
        return categories.get(id);
    }

    public void putCategory(SkillCategory category) {
        categories.put(category.id(), category);
        setDirty();
    }

    public void removeCategory(String id) {
        if (categories.remove(id) != null) {
            setDirty();
        }
    }

    /** Finds a node across every category; node ids are unique server wide. */
    public net.schwarz.rotasutils.skill.SkillNode findNode(String nodeId) {
        for (SkillCategory category : categories.values()) {
            net.schwarz.rotasutils.skill.SkillNode node = category.node(nodeId);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    // Jobs and character stats ---------------------------------------------

    public Map<String, net.schwarz.rotasutils.job.JobDef> jobs() {
        return jobs;
    }

    public net.schwarz.rotasutils.job.JobDef job(String id) {
        return id == null ? null : jobs.get(id);
    }

    public void putJob(net.schwarz.rotasutils.job.JobDef job) {
        jobs.put(job.id(), job);
        setDirty();
    }

    public void removeJob(String id) {
        if (jobs.remove(id) != null) {
            for (SkillCategory category : categories.values()) {
                category.jobs().remove(id);
            }
            setDirty();
        }
    }


    // Mining sites ---------------------------------------------------------

    public Map<String, net.schwarz.rotasutils.mine.MiningSite> miningSites() {
        return miningSites;
    }

    public void putMiningSite(net.schwarz.rotasutils.mine.MiningSite site) {
        miningSites.put(site.id(), site);
        setDirty();
    }

    public boolean removeMiningSite(String id) {
        boolean removed = miningSites.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // Nemeses --------------------------------------------------------------

    public Map<Integer, net.schwarz.rotasutils.nemesis.Nemesis> nemeses() {
        return nemeses;
    }

    public net.schwarz.rotasutils.nemesis.Nemesis nemesis(int id) {
        return nemeses.get(id);
    }

    /** The next nemesis id; ids are never reused, so a stale body can never be mistaken for a new record. */
    public int nextNemesisId() {
        nextNemesisId = Math.max(1, nextNemesisId + 1);
        setDirty();
        return nextNemesisId;
    }

    public void putNemesis(net.schwarz.rotasutils.nemesis.Nemesis nemesis) {
        nemeses.put(nemesis.id(), nemesis);
        setDirty();
    }

    public net.schwarz.rotasutils.nemesis.Nemesis removeNemesis(int id) {
        var removed = nemeses.remove(id);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    // World events ---------------------------------------------------------

    public Map<Integer, net.schwarz.rotasutils.worldevent.WorldEvent> worldEvents() {
        return worldEvents;
    }

    public int nextWorldEventId() {
        nextWorldEventId = Math.max(1, nextWorldEventId + 1);
        setDirty();
        return nextWorldEventId;
    }

    public void putWorldEvent(net.schwarz.rotasutils.worldevent.WorldEvent event) {
        worldEvents.put(event.id(), event);
        setDirty();
    }

    public net.schwarz.rotasutils.worldevent.WorldEvent removeWorldEvent(int id) {
        var removed = worldEvents.remove(id);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    // Titles ---------------------------------------------------------------

    /** True once a batch of starter titles added after the first seeding has been installed in this world. */
    public boolean titleBatchSeeded(String batch) {
        return seededTitleBatches.contains(batch);
    }

    public void markTitleBatchSeeded(String batch) {
        if (seededTitleBatches.add(batch)) {
            setDirty();
        }
    }

    public Map<String, net.schwarz.rotasutils.title.TitleDef> titles() {
        return titles;
    }

    public net.schwarz.rotasutils.title.TitleDef title(String id) {
        return id == null ? null : titles.get(id);
    }

    public void putTitle(net.schwarz.rotasutils.title.TitleDef title) {
        if (titles.size() >= net.schwarz.rotasutils.title.TitleDef.MAX_TITLES && !titles.containsKey(title.id())) {
            throw new IllegalStateException("A world holds at most "
                    + net.schwarz.rotasutils.title.TitleDef.MAX_TITLES + " titles");
        }
        titles.put(title.id(), title);
        setDirty();
    }

    public void removeTitle(String id) {
        if (titles.remove(id) != null) {
            uniqueTitleOwners.remove(id);
            setDirty();
        }
    }

    public boolean titlesSeeded() {
        return titlesSeeded;
    }

    public void markTitlesSeeded() {
        titlesSeeded = true;
        setDirty();
    }

    /** Who holds a unique title, or null while nobody does. */
    public UUID uniqueTitleOwner(String id) {
        return id == null ? null : uniqueTitleOwners.get(id);
    }

    /**
     * Claims a unique title for one player. Returns false when somebody else already holds it, which is
     * what makes the title unique: the claim is decided here, on the server thread, once.
     */
    public boolean claimUniqueTitle(String id, UUID player) {
        if (id == null || player == null) {
            return false;
        }
        UUID owner = uniqueTitleOwners.get(id);
        if (owner != null) {
            return owner.equals(player);
        }
        uniqueTitleOwners.put(id, player);
        setDirty();
        return true;
    }

    /** Releases a unique title, so it can be earned again. Used when an admin revokes one. */
    public void releaseUniqueTitle(String id) {
        if (uniqueTitleOwners.remove(id) != null) {
            setDirty();
        }
    }

    public int statSystemVersion() {
        return statSystemVersion;
    }

    public void setStatSystemVersion(int version) {
        statSystemVersion = version;
        setDirty();
    }

    // Level zones ----------------------------------------------------------

    public Map<String, net.schwarz.rotasutils.core.ZoneDef> zones() {
        return zones;
    }

    public net.schwarz.rotasutils.core.ZoneDef zone(String id) {
        return id == null ? null : zones.get(id);
    }

    public void putZone(net.schwarz.rotasutils.core.ZoneDef zone) {
        zones.put(zone.id(), zone);
        setDirty();
    }

    public void removeZone(String id) {
        if (zones.remove(id) != null) {
            setDirty();
        }
    }

    // Houses ---------------------------------------------------------------

    public Map<String, net.schwarz.rotasutils.house.HouseDefinition> houses() { return houses; }
    public net.schwarz.rotasutils.house.HouseDefinition house(String id) { return houses.get(id); }
    public net.schwarz.rotasutils.house.HouseTenancy houseTenancy(String id) {
        return houseTenancies.getOrDefault(id, net.schwarz.rotasutils.house.HouseTenancy.available());
    }
    public Map<String, net.schwarz.rotasutils.house.HouseTenancy> houseTenancies() { return houseTenancies; }
    public net.schwarz.rotasutils.house.HouseConfig houseConfig() { return houseConfig; }
    public void putHouse(net.schwarz.rotasutils.house.HouseDefinition house) {
        if (!houses.containsKey(house.id()) && houses.size() >= 4096) throw new IllegalStateException("House limit reached");
        houses.put(house.id(), house); houseTenancies.putIfAbsent(house.id(), net.schwarz.rotasutils.house.HouseTenancy.available()); setDirty();
    }
    public void putHouseTenancy(String id, net.schwarz.rotasutils.house.HouseTenancy tenancy) {
        if (!houses.containsKey(id)) throw new IllegalArgumentException("Unknown house: " + id);
        houseTenancies.put(id, tenancy); setDirty();
    }
    public void removeHouse(String id) { houses.remove(id); houseTenancies.remove(id); houseSettings.remove(id); setDirty(); }
    /** This house's own price, guest access and welcome line; the default when none were set. */
    public net.schwarz.rotasutils.house.HouseSettings houseSettings(String id) {
        return houseSettings.getOrDefault(id, net.schwarz.rotasutils.house.HouseSettings.DEFAULT);
    }
    public void putHouseSettings(String id, net.schwarz.rotasutils.house.HouseSettings settings) {
        if (!houses.containsKey(id)) throw new IllegalArgumentException("Unknown house: " + id);
        if (settings == null || settings.isDefault()) houseSettings.remove(id); else houseSettings.put(id, settings);
        setDirty();
    }
    /** The house's tier with its own price laid over it; null when the tier is unknown. */
    public net.schwarz.rotasutils.house.HouseTier houseTier(net.schwarz.rotasutils.house.HouseDefinition house) {
        return houseSettings(house.id()).apply(houseConfig.tier(house.tier()));
    }
    public void setHouseConfig(net.schwarz.rotasutils.house.HouseConfig config) { houseConfig = config; setDirty(); }

    // Waystones ------------------------------------------------------------

    public Map<String, net.schwarz.rotasutils.waystone.Waystone> waystones() {
        return java.util.Collections.unmodifiableMap(waystones);
    }

    public net.schwarz.rotasutils.waystone.Waystone waystone(String id) {
        return id == null ? null : waystones.get(id);
    }

    /** Adds or replaces a pillar; the world limit keeps a griefed server from growing its save. */
    public void putWaystone(net.schwarz.rotasutils.waystone.Waystone waystone) {
        java.util.Objects.requireNonNull(waystone, "waystone");
        if (!waystones.containsKey(waystone.id()) && waystones.size() >= WAYSTONE_LIMIT) {
            throw new IllegalStateException("Waystone limit reached");
        }
        waystones.put(waystone.id(), waystone);
        setDirty();
    }

    public boolean removeWaystone(String id) {
        if (waystones.remove(id) == null) {
            return false;
        }
        // A pillar that is gone must not stay in anyone's list, or its row would warp nowhere.
        for (PlayerProgress progress : players.values()) {
            progress.forgetWaystone(id);
        }
        setDirty();
        return true;
    }

    // Level ----------------------------------------------------------------

    public LevelConfig levelConfig() {
        return levelConfig;
    }

    public void setLevelConfig(LevelConfig config) {
        this.levelConfig = config;
        setDirty();
    }

    public ServerSettings serverSettings() {
        return serverSettings;
    }

    // Players --------------------------------------------------------------

    public PlayerProgress progress(UUID playerId) {
        return players.computeIfAbsent(playerId, id -> {
            PlayerProgress created = new PlayerProgress(id);
            created.setLevel(levelConfig.startingLevel());
            setDirty();
            return created;
        });
    }

    public PlayerProgress peek(UUID playerId) {
        return players.get(playerId);
    }

    public void beginKernelTransaction(UUID playerId) {
        if (!kernelTransactions.add(playerId)) {
            throw new IllegalStateException("Nested player reward transaction: " + playerId);
        }
    }

    public void endKernelTransaction(UUID playerId) {
        kernelTransactions.remove(playerId);
    }

    public Collection<PlayerProgress> allPlayers() {
        return players.values();
    }

    // Windowed counters ------------------------------------------------------

    /** Bounded so packs cannot grow world data without limit. */
    public static final int COUNTER_LIMIT = 4096;

    /** Current value of a counter, or 0 when it is unset or its window has rolled over. */
    public long counter(String key, long window) {
        long[] entry = counters.get(key);
        return entry == null || entry[0] != window ? 0 : entry[1];
    }

    /**
     * Adds to a windowed counter without exceeding {@code limit}. Returns false and changes nothing
     * when the limit would be passed, so callers can gate a claim on the result.
     */
    public boolean addCounter(String key, long window, long limit, long amount) {
        if (key == null || key.isEmpty() || key.length() > 200 || amount < 0 || limit < 0) {
            throw new IllegalArgumentException("Invalid counter request");
        }
        long current = counter(key, window);
        if (current + amount > limit) { return false; }
        if (!counters.containsKey(key) && counters.size() >= COUNTER_LIMIT) {
            throw new IllegalStateException("World counter budget exceeded; admin retention review required");
        }
        counters.put(key, new long[]{window, current + amount});
        setDirty();
        return true;
    }

    /** Writes a counter back to a lower value, used to release a reservation a failed action made. */
    public void releaseCounter(String key, long window, long value) {
        if (key == null || key.isEmpty() || key.length() > 200 || value < 0) {
            throw new IllegalArgumentException("Invalid counter release");
        }
        long current = counter(key, window);
        if (value > current) { throw new IllegalArgumentException("A release cannot raise a counter"); }
        counters.put(key, new long[]{window, value});
        setDirty();
    }

    /** Drops counters whose window has rolled over, keeping the store from accumulating dead keys. */
    public int pruneCounters(long window) {
        int before = counters.size();
        counters.entrySet().removeIf(entry -> entry.getValue()[0] != window);
        if (before != counters.size()) { setDirty(); }
        return before - counters.size();
    }

    public Map<String, long[]> counters() { return java.util.Collections.unmodifiableMap(counters); }

    // Zone encounters ------------------------------------------------------

    public Map<String, net.schwarz.rotasutils.core.ZoneEncounterState> zoneEncounters() {
        return java.util.Collections.unmodifiableMap(zoneEncounters);
    }

    public net.schwarz.rotasutils.core.ZoneEncounterState zoneEncounter(String key) {
        return zoneEncounters.get(key);
    }

    /** Stores a spawn point's live state; an unchanged state does not dirty the save. */
    public void putZoneEncounter(String key, net.schwarz.rotasutils.core.ZoneEncounterState state) {
        if (key == null || key.isEmpty() || key.length() > 200 || state == null) {
            throw new IllegalArgumentException("Invalid zone encounter");
        }
        if (state.equals(zoneEncounters.get(key))) {
            return;
        }
        if (!zoneEncounters.containsKey(key) && zoneEncounters.size() >= ENCOUNTER_LIMIT) {
            throw new IllegalStateException("Zone encounter budget exceeded");
        }
        zoneEncounters.put(key, state);
        setDirty();
    }

    public void removeZoneEncounter(String key) {
        if (zoneEncounters.remove(key) != null) {
            setDirty();
        }
    }

    // Audit ----------------------------------------------------------------

    public List<String> auditLog() {
        return auditLog;
    }

    public void audit(String line) {
        if (!serverSettings.auditLogEnabled()) {
            return;
        }
        auditLog.add(line);
        while (auditLog.size() > 500) {
            auditLog.remove(0);
        }
        setDirty();
    }

    // Persistence ----------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("quests", Nbt.saveList(quests.values(), QuestDef::save));
        tag.put("boards", Nbt.saveList(boards.values(), BoardConfig::save));
        tag.put("npcs", Nbt.saveList(npcs.values(), net.schwarz.rotasutils.npc.NpcDef::save));
        tag.put("categories", Nbt.saveList(categories.values(), SkillCategory::save));
        tag.put("jobs", Nbt.saveList(jobs.values(), net.schwarz.rotasutils.job.JobDef::save));
        tag.put("zones", Nbt.saveList(zones.values(), net.schwarz.rotasutils.core.ZoneDef::save));
        tag.put("houses", Nbt.saveList(houses.values(), net.schwarz.rotasutils.house.HouseDefinition::save));
        net.minecraft.nbt.ListTag tenancyTags = new net.minecraft.nbt.ListTag();
        houseTenancies.forEach((id, tenancy) -> { CompoundTag entry = tenancy.save(); entry.putString("house", id); tenancyTags.add(entry); });
        tag.put("house_tenancies", tenancyTags);
        tag.put("house_config", houseConfig.save());
        net.minecraft.nbt.ListTag settingTags = new net.minecraft.nbt.ListTag();
        houseSettings.forEach((id, settings) -> { CompoundTag entry = settings.save(); entry.putString("house", id); settingTags.add(entry); });
        tag.put("house_settings", settingTags);
        tag.put("waystones", Nbt.saveList(waystones.values(), net.schwarz.rotasutils.waystone.Waystone::save));
        tag.put("mining_sites", Nbt.saveList(miningSites.values(), net.schwarz.rotasutils.mine.MiningSite::save));
        tag.put("titles", Nbt.saveList(titles.values(), net.schwarz.rotasutils.title.TitleDef::save));
        CompoundTag titleOwners = new CompoundTag();
        uniqueTitleOwners.forEach((id, owner) -> titleOwners.putUUID(id, owner));
        tag.put("title_owners", titleOwners);
        tag.putBoolean("titles_seeded", titlesSeeded);
        tag.put("title_batches", Nbt.saveStrings(seededTitleBatches));
        tag.put("nemeses", Nbt.saveList(nemeses.values(), net.schwarz.rotasutils.nemesis.Nemesis::save));
        tag.putInt("next_nemesis_id", nextNemesisId);
        tag.put("world_events", Nbt.saveList(worldEvents.values(), net.schwarz.rotasutils.worldevent.WorldEvent::save));
        tag.putInt("next_world_event_id", nextWorldEventId);
        tag.putInt("stat_system_version", statSystemVersion);
        tag.put("players", Nbt.saveList(players.values(), PlayerProgress::save));
        tag.put("level_config", levelConfig.save());
        tag.put("server_settings", serverSettings.save());
        tag.put("audit", Nbt.saveStrings(auditLog));
        tag.put("content_history", contentHistory.save());
        tag.put("config_history", configHistory.save());
        CompoundTag counterTag = new CompoundTag();
        counters.forEach((key, value) -> counterTag.putLongArray(key, value));
        tag.put("counters", counterTag);
        CompoundTag encounterTag = new CompoundTag();
        zoneEncounters.forEach((key, value) -> encounterTag.put(key, value.save()));
        tag.put("zone_encounters", encounterTag);
        return tag;
    }

    public static RotasData load(CompoundTag tag) {
        RotasData data = new RotasData();
        for (QuestDef quest : Nbt.loadList(tag, "quests", QuestDef::load)) {
            data.quests.put(quest.id(), quest);
        }
        for (BoardConfig board : Nbt.loadList(tag, "boards", BoardConfig::load)) {
            data.boards.put(board.id(), board);
        }
        for (net.schwarz.rotasutils.npc.NpcDef npc
                : Nbt.loadList(tag, "npcs", net.schwarz.rotasutils.npc.NpcDef::load)) {
            data.npcs.put(npc.id(), npc);
        }
        for (SkillCategory category : Nbt.loadList(tag, "categories", SkillCategory::load)) {
            data.categories.put(category.id(), category);
        }
        for (net.schwarz.rotasutils.job.JobDef job : Nbt.loadList(tag, "jobs", net.schwarz.rotasutils.job.JobDef::load)) {
            data.jobs.put(job.id(), job);
        }
        for (net.schwarz.rotasutils.mine.MiningSite site
                : Nbt.loadList(tag, "mining_sites", net.schwarz.rotasutils.mine.MiningSite::load)) {
            data.miningSites.put(site.id(), site);
        }
        for (net.schwarz.rotasutils.title.TitleDef title
                : Nbt.loadList(tag, "titles", net.schwarz.rotasutils.title.TitleDef::load)) {
            data.titles.put(title.id(), title);
        }
        CompoundTag titleOwners = tag.getCompound("title_owners");
        for (String id : titleOwners.getAllKeys()) {
            if (titleOwners.hasUUID(id)) {
                data.uniqueTitleOwners.put(id, titleOwners.getUUID(id));
            }
        }
        data.titlesSeeded = tag.getBoolean("titles_seeded");
        data.seededTitleBatches.addAll(Nbt.loadStrings(tag, "title_batches"));
        // One unreadable nemesis or event is dropped on its own; it must never block the whole world store.
        var nemesisTags = tag.getList("nemeses", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < nemesisTags.size() && data.nemeses.size() < 1024; index++) {
            try {
                var nemesis = net.schwarz.rotasutils.nemesis.Nemesis.load(nemesisTags.getCompound(index));
                data.nemeses.put(nemesis.id(), nemesis);
            } catch (RuntimeException corrupt) {
                Rotasutils.LOG.warn("RotasUtils dropped an unreadable nemesis: {}", corrupt.getMessage());
            }
        }
        data.nextNemesisId = Math.max(tag.getInt("next_nemesis_id"),
                data.nemeses.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
        var eventTags = tag.getList("world_events", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < eventTags.size() && data.worldEvents.size() < 64; index++) {
            try {
                var event = net.schwarz.rotasutils.worldevent.WorldEvent.load(eventTags.getCompound(index));
                data.worldEvents.put(event.id(), event);
            } catch (RuntimeException corrupt) {
                Rotasutils.LOG.warn("RotasUtils dropped an unreadable world event: {}", corrupt.getMessage());
            }
        }
        data.nextWorldEventId = Math.max(tag.getInt("next_world_event_id"),
                data.worldEvents.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
        for (net.schwarz.rotasutils.core.ZoneDef zone
                : Nbt.loadList(tag, "zones", net.schwarz.rotasutils.core.ZoneDef::load)) {
            data.zones.put(zone.id(), zone);
        }
        for (net.schwarz.rotasutils.house.HouseDefinition house : Nbt.loadList(tag, "houses", net.schwarz.rotasutils.house.HouseDefinition::load)) {
            if (data.houses.size() >= 4096) throw new IllegalArgumentException("Stored house limit exceeded");
            data.houses.put(house.id(), house);
        }
        net.minecraft.nbt.ListTag tenancyTags = tag.getList("house_tenancies", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (tenancyTags.size() > 4096) throw new IllegalArgumentException("Stored tenancy limit exceeded");
        for (net.minecraft.nbt.Tag raw : tenancyTags) {
            CompoundTag entry = (CompoundTag) raw; String id = entry.getString("house");
            if (!data.houses.containsKey(id)) throw new IllegalArgumentException("Tenancy references unknown house: " + id);
            data.houseTenancies.put(id, net.schwarz.rotasutils.house.HouseTenancy.load(entry));
        }
        data.houses.keySet().forEach(id -> data.houseTenancies.putIfAbsent(id, net.schwarz.rotasutils.house.HouseTenancy.available()));
        for (net.minecraft.nbt.Tag raw : tag.getList("house_settings", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String id = entry.getString("house");
            // Settings of a house that no longer exists are dropped rather than failing the load.
            if (data.houses.containsKey(id)) data.houseSettings.put(id, net.schwarz.rotasutils.house.HouseSettings.load(entry));
        }
        for (net.schwarz.rotasutils.waystone.Waystone waystone
                : Nbt.loadList(tag, "waystones", net.schwarz.rotasutils.waystone.Waystone::load)) {
            if (data.waystones.size() >= WAYSTONE_LIMIT) {
                throw new IllegalArgumentException("Stored waystone limit exceeded");
            }
            data.waystones.put(waystone.id(), waystone);
        }
        if (tag.contains("house_config")) data.houseConfig = net.schwarz.rotasutils.house.HouseConfig.load(tag.getCompound("house_config"));
        data.statSystemVersion = tag.getInt("stat_system_version");
        for (PlayerProgress progress : Nbt.loadList(tag, "players", PlayerProgress::load)) {
            data.players.put(progress.playerId(), progress);
        }
        if (tag.contains("level_config")) {
            data.levelConfig = LevelConfig.load(tag.getCompound("level_config"));
        }
        if (tag.contains("server_settings")) {
            data.serverSettings = ServerSettings.load(tag.getCompound("server_settings"));
        }
        data.auditLog.addAll(Nbt.loadStrings(tag, "audit"));
        CompoundTag counterTag = tag.getCompound("counters");
        if (counterTag.size() > COUNTER_LIMIT) {
            throw new IllegalArgumentException("Stored counter store exceeds " + COUNTER_LIMIT + " entries");
        }
        for (String key : counterTag.getAllKeys()) {
            long[] value = counterTag.getLongArray(key);
            if (value.length != 2) { throw new IllegalArgumentException("Corrupt world counter: " + key); }
            data.counters.put(key, new long[]{value[0], value[1]});
        }
        CompoundTag encounterTag = tag.getCompound("zone_encounters");
        if (encounterTag.size() > ENCOUNTER_LIMIT) {
            throw new IllegalArgumentException("Stored zone encounters exceed " + ENCOUNTER_LIMIT + " entries");
        }
        for (String key : encounterTag.getAllKeys()) {
            data.zoneEncounters.put(key, net.schwarz.rotasutils.core.ZoneEncounterState.load(encounterTag.getCompound(key)));
        }
        if (tag.contains("content_history")) {
            data.contentHistory = net.schwarz.rotasutils.core.ContentHistory.load(tag.getCompound("content_history"), data::setDirty);
        }
        if (tag.contains("config_history")) {
            data.configHistory = net.schwarz.rotasutils.core.ConfigHistory.load(tag.getCompound("config_history"), data::setDirty);
        }
        return data;
    }
}
