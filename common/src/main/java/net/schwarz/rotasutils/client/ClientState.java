package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.data.ServerSettings;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.server.Validation;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillNode;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client's read-only mirror of server state.
 *
 * <p>Screens render from here and never mutate it; every change arrives as a fresh
 * snapshot from the server.
 */
@Environment(EnvType.CLIENT)
public final class ClientState {
    private static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();
    private static final Map<String, BoardConfig> BOARDS = new LinkedHashMap<>();
    private static final Map<String, net.schwarz.rotasutils.npc.NpcDef> NPCS = new LinkedHashMap<>();
    private static final Map<String, SkillCategory> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, net.schwarz.rotasutils.job.JobDef> JOBS = new LinkedHashMap<>();
    /** Title definitions, in display order. */
    private static final List<net.schwarz.rotasutils.title.TitleDef> TITLES = new ArrayList<>();
    /** Admin level zones, only sent to admins. */
    private static final Map<String, net.schwarz.rotasutils.core.ZoneDef> ZONES = new LinkedHashMap<>();
    /** Client-only: the zone whose outline the Zone Wand draws in the world. */
    private static String selectedZone = "";
    /** Every house's name, tier, area and status (no owners), drawn while the House Wand is held. */
    private static final List<ClientHouse> HOUSES = new ArrayList<>();
    /** Full house definitions/configuration, present only in an authenticated admin session. */
    private static ClientHouseAdminState houseAdmin;
    /** Origin id to translated name, as far as the client language covers it. */
    private static final Map<String, String> ORIGINS = new LinkedHashMap<>();
    /** The local player's origins across every layer. */
    private static final List<String> RACES = new ArrayList<>();
    private static long jobCooldownSeconds;
    private static final List<Validation.Issue> ISSUES = new ArrayList<>();
    private static final List<String> AUDIT = new ArrayList<>();

    private static PlayerProgress progress = new PlayerProgress(new UUID(0, 0));
    private static final List<SkillCategorySummary> PUFFISH_CATEGORIES = new ArrayList<>();
    private static boolean puffishAvailable;
    private static LevelConfig levelConfig = new LevelConfig();
    private static ServerSettings serverSettings = new ServerSettings();
    private static boolean admin;
    /** True only for full server operators; a stricter tier than {@link #admin}. */
    private static boolean operator;
    private static boolean zoneGateTesting;
    private static boolean zoneGateTestAvailable;
    /** The refine display numbers the server last sent, so a tooltip can price a "+7" correctly. */
    private static CompoundTag refineRules = new CompoundTag();
    private static CompoundTag weaponMemoryRules = new CompoundTag();
    /** The event catalogue, as far as an admin session has been told. */
    private static CompoundTag eventCatalogue = new CompoundTag();
    /** The drop filter, as far as an admin session has been told: what is off, and where. */
    private static CompoundTag dropFilter = new CompoundTag();
    /** The daily and season tracks, as the server last described them. */
    private static CompoundTag tracks = new CompoundTag();
    /** Title id -> how far this player has come towards it. */
    private static CompoundTag titleProgress = new CompoundTag();
    private static String lastFeedback = "";
    private static boolean lastFeedbackOk = true;
    private static long lastFeedbackAt;

    private ClientState() {
    }

    public static void applyContent(CompoundTag tag) {
        refineRules = tag.getCompound("refine").copy();
        weaponMemoryRules = tag.getCompound("weapon_memory").copy();
        PlayerTitles.apply(tag.getCompound("player_titles"));
        net.schwarz.rotasutils.core.CardIndex.load(tag.getCompound("cards"));
        dropFilter = tag.getCompound("drop_filter").copy();
        eventCatalogue = tag.getCompound("events").copy();
        admin = tag.getBoolean("admin");
        operator = tag.getBoolean("op");
        zoneGateTesting = admin && tag.getBoolean("zone_gate_testing");
        zoneGateTestAvailable = admin && tag.getBoolean("zone_gate_test_available");
        houseAdmin = null;
        if (admin && tag.contains("house_admin", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            houseAdmin = ClientHouseAdminState.load(tag.getCompound("house_admin"));
        }
        QUESTS.clear();
        for (QuestDef quest : Nbt.loadList(tag, "quests", QuestDef::load)) {
            QUESTS.put(quest.id(), quest);
        }
        BOARDS.clear();
        for (BoardConfig board : Nbt.loadList(tag, "boards", BoardConfig::load)) {
            BOARDS.put(board.id(), board);
        }
        NPCS.clear();
        for (net.schwarz.rotasutils.npc.NpcDef npc
                : Nbt.loadList(tag, "npcs", net.schwarz.rotasutils.npc.NpcDef::load)) {
            NPCS.put(npc.id(), npc);
        }
        CATEGORIES.clear();
        for (SkillCategory category : Nbt.loadList(tag, "categories", SkillCategory::load)) {
            CATEGORIES.put(category.id(), category);
        }
        JOBS.clear();
        for (net.schwarz.rotasutils.job.JobDef job : Nbt.loadList(tag, "jobs", net.schwarz.rotasutils.job.JobDef::load)) {
            JOBS.put(job.id(), job);
        }
        TITLES.clear();
        TITLES.addAll(Nbt.loadList(tag, "titles", net.schwarz.rotasutils.title.TitleDef::load));
        titleHolders = tag.getCompound("title_holders").copy();
        ZONES.clear();
        for (net.schwarz.rotasutils.core.ZoneDef zone
                : Nbt.loadList(tag, "zones", net.schwarz.rotasutils.core.ZoneDef::load)) {
            ZONES.put(zone.id(), zone);
        }
        HOUSES.clear();
        for (ClientHouse house : Nbt.loadList(tag, "houses", ClientHouse::load)) {
            HOUSES.add(house);
        }
        ORIGINS.clear();
        CompoundTag origins = tag.getCompound("origins");
        for (String id : origins.getAllKeys()) {
            String name = id;
            try {
                var component = net.minecraft.network.chat.Component.Serializer.fromJson(origins.getString(id));
                if (component != null) {
                    name = component.getString();
                }
            } catch (RuntimeException malformed) {
                // Keep the id; a bad name must not hide the race from the tree settings.
            }
            ORIGINS.put(id, name);
        }
        jobCooldownSeconds = tag.getLong("job_cooldown");
        if (tag.contains("level_config")) {
            levelConfig = LevelConfig.load(tag.getCompound("level_config"));
        }
        if (tag.contains("server_settings")) {
            serverSettings = ServerSettings.load(tag.getCompound("server_settings"));
        }
        AUDIT.clear();
        AUDIT.addAll(Nbt.loadStrings(tag, "audit"));
    }

    public static void applyProgress(CompoundTag tag) {
        progress = PlayerProgress.load(tag);
        titleProgress = tag.getCompound("title_progress").copy();
        tracks = tag.getCompound("tracks").copy();
        PUFFISH_CATEGORIES.clear();
        net.minecraft.nbt.ListTag categories = tag.getList("puffish_categories", 10);
        for (int i = 0; i < categories.size(); i++) {
            CompoundTag entry = categories.getCompound(i);
            PUFFISH_CATEGORIES.add(new SkillCategorySummary(
                    entry.getString("id"),
                    entry.getInt("points_left"),
                    entry.getInt("points_total"),
                    entry.getInt("points_spent"),
                    entry.getInt("skills_unlocked"),
                    entry.getInt("skills_total")
            ));
        }
        puffishAvailable = tag.getBoolean("puffish_available");
        RACES.clear();
        RACES.addAll(Nbt.loadStrings(tag, "races"));
    }

    /** One line of the event catalogue, as the client sees it. */
    public record EventRuleView(String type, String filter, boolean enabled, double xpMultiplier,
                                long xpFlat, long gold, String announce, int cooldownSeconds) {
        /** True for the entry that answers for everything of its type. */
        public boolean plain() {
            return filter == null || filter.isBlank();
        }
    }

    /** True when the event catalogue is switched on at all. */
    public static boolean eventsEnabled() {
        return eventCatalogue.getBoolean("enabled");
    }

    /** Every rule the server sent, in order. */
    public static List<EventRuleView> eventRules() {
        List<EventRuleView> rules = new ArrayList<>();
        net.minecraft.nbt.ListTag list = eventCatalogue.getList("rules", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            rules.add(new EventRuleView(entry.getString("type"), entry.getString("filter"),
                    entry.getBoolean("enabled"), entry.getDouble("xp_multiplier"), entry.getLong("xp_flat"),
                    entry.getLong("gold"), entry.getString("announce"), entry.getInt("cooldown")));
        }
        return rules;
    }

    /** One rule by its type and filter, or null when the server has no such line. */
    public static EventRuleView eventRule(String type, String filter) {
        for (EventRuleView rule : eventRules()) {
            if (rule.type().equals(type) && rule.filter().equals(filter == null ? "" : filter)) {
                return rule;
            }
        }
        return null;
    }

    /** True when the drop filter is switched on at all. */
    public static boolean dropFilterEnabled() {
        return dropFilter.getBoolean("enabled");
    }

    /** True when this item is blocked for every mob. */
    public static boolean dropBlockedGlobally(String item) {
        return item != null && Nbt.loadStrings(dropFilter, "blocked").contains(item);
    }

    /** Every globally blocked item, in the order they were blocked. */
    public static java.util.List<String> dropBlockedGlobally() {
        return Nbt.loadStrings(dropFilter, "blocked");
    }

    /** How many items are switched off for one kind of mob. */
    public static int dropBlockedCount(String entity) {
        return entity == null ? 0 : dropFilter.getCompound("by_entity").getInt(entity);
    }

    /** The daily and season tracks; empty until the first progress sync. */
    public static CompoundTag tracks() {
        return tracks;
    }

    private static CompoundTag titleHolders = new CompoundTag();

    /** Who holds a claimed unique title, or an empty string while it is still up for grabs. */
    public static String titleHolder(String id) {
        return id == null ? "" : titleHolders.getString(id);
    }

    /** How far this player has come towards one title. */
    public static long titleProgress(String id) {
        return id == null ? 0 : titleProgress.getLong(id);
    }

    /** Title definitions, in display order. */
    public static List<net.schwarz.rotasutils.title.TitleDef> titles() {
        return TITLES;
    }

    /** One title by id, or null when the server does not have it. */
    public static net.schwarz.rotasutils.title.TitleDef title(String id) {
        for (net.schwarz.rotasutils.title.TitleDef title : TITLES) {
            if (title.id().equals(id)) {
                return title;
            }
        }
        return null;
    }

    /** The server's refine numbers; empty until the first content sync arrives. */
    public static CompoundTag refineRules() {
        return refineRules;
    }

    public static CompoundTag weaponMemoryRules() {
        return weaponMemoryRules;
    }

    public static Map<String, net.schwarz.rotasutils.job.JobDef> jobs() {
        return JOBS;
    }

    public static net.schwarz.rotasutils.job.JobDef job(String id) {
        return id == null ? null : JOBS.get(id);
    }

    /** Display name of a job id, falling back to the id for jobs that were removed. */
    public static String jobName(String id) {
        net.schwarz.rotasutils.job.JobDef job = job(id);
        return job != null ? job.name() : id == null ? "" : id;
    }

    public static Map<String, String> origins() {
        return ORIGINS;
    }

    public static String raceName(String id) {
        return ORIGINS.getOrDefault(id, id);
    }

    /** Admin level zones; empty for non-admins, who are not sent them. */
    public static Map<String, net.schwarz.rotasutils.core.ZoneDef> zones() {
        return ZONES;
    }

    /** Every house as synced by the server; read-only for renderers. */
    public static List<ClientHouse> houses() {
        return java.util.Collections.unmodifiableList(HOUSES);
    }

    /** Full housing editor data, or {@code null} for non-admin/missing/malformed content. */
    public static ClientHouseAdminState houseAdmin() {
        return houseAdmin;
    }

    public static net.schwarz.rotasutils.core.ZoneDef zone(String id) {
        return id == null ? null : ZONES.get(id);
    }

    /** Admin preference for this game session: draw every zone border even without the Zone Wand. */
    private static boolean zoneBordersPinned;

    public static boolean zoneBordersPinned() {
        return zoneBordersPinned;
    }

    public static void setZoneBordersPinned(boolean pinned) {
        zoneBordersPinned = pinned;
    }

    /** The zone highlighted in the world while the Zone Wand is held, or empty. */
    public static String selectedZone() {
        return selectedZone;
    }

    public static void setSelectedZone(String id) {
        selectedZone = id == null ? "" : id;
    }

    public static List<String> races() {
        return RACES;
    }

    public static long jobCooldownSeconds() {
        return jobCooldownSeconds;
    }

    /** True when the server routes skill points to Pufferfish instead of the built-in trees. */
    public static boolean pufferfishSkills() {
        return dev.architectury.platform.Platform.isModLoaded("puffish_skills") && levelConfig.pufferfishSkills();
    }

    /** Null when the local player's job and race may use the tree, otherwise the reason. */
    public static String audienceBlock(SkillCategory category) {
        return net.schwarz.rotasutils.skill.SkillRules.audienceBlock(category,
                java.util.stream.Stream.of(progress.mainJob(),progress.subJob()).filter(id->!id.isEmpty()).toList(), RACES,
                ClientState::jobName, ClientState::raceName);
    }

    /** Read-only Pufferfish category data sent with the Rotas player progress snapshot. */
    public record SkillCategorySummary(String id, int pointsLeft, int pointsTotal, int pointsSpent,
                                       int skillsUnlocked, int skillsTotal) {
        public String displayName() {
            String path = id == null ? "" : id.substring(Math.max(id.indexOf(':') + 1, 0));
            if (path.isBlank()) {
                return "Skills";
            }
            String[] words = path.replace('-', '_').split("_");
            StringBuilder out = new StringBuilder();
            for (String word : words) {
                if (word.isBlank()) continue;
                if (!out.isEmpty()) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return out.isEmpty() ? path : out.toString();
        }
    }

    public static List<SkillCategorySummary> puffishCategories() {
        return List.copyOf(PUFFISH_CATEGORIES);
    }

    public static boolean puffishAvailable() {
        return puffishAvailable;
    }

    /** One row of the party roster as the server described it. */
    public record PartyMember(UUID id, String name, int level, boolean leader,
                              boolean online, boolean nearby) {
    }

    /** One player near enough to invite with a click, as the server listed them. */
    public record InvitablePlayer(UUID id, String name, int level) {
    }

    private static final List<PartyMember> PARTY = new ArrayList<>();
    private static final List<InvitablePlayer> PARTY_INVITABLE = new ArrayList<>();
    private static String partyInviteFrom = "";
    private static int partyMaxSize = 10;
    private static boolean partyEnabled = true;
    private static double partyRadius = 64;

    public static void applyParty(CompoundTag tag) {
        PARTY.clear();
        PARTY_INVITABLE.clear();
        net.minecraft.nbt.ListTag members = tag.getList("members", 10);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag entry = members.getCompound(i);
            PARTY.add(new PartyMember(entry.getUUID("id"), entry.getString("name"),
                    entry.getInt("level"), entry.getBoolean("leader"),
                    entry.getBoolean("online"), entry.getBoolean("nearby")));
        }
        net.minecraft.nbt.ListTag invitable = tag.getList("invitable", 10);
        for (int i = 0; i < invitable.size(); i++) {
            CompoundTag entry = invitable.getCompound(i);
            PARTY_INVITABLE.add(new InvitablePlayer(entry.getUUID("id"), entry.getString("name"),
                    entry.getInt("level")));
        }
        partyInviteFrom = tag.getString("invite_from");
        partyMaxSize = Math.max(1, tag.getInt("max_size"));
        partyEnabled = tag.getBoolean("enabled");
        partyRadius = tag.getDouble("radius");
    }

    public static List<PartyMember> party() {
        return PARTY;
    }

    /** Players near enough to invite with one click. */
    public static List<InvitablePlayer> partyInvitable() {
        return PARTY_INVITABLE;
    }

    /** Name of the player whose invitation is waiting, or empty. */
    public static String partyInviteFrom() {
        return partyInviteFrom;
    }

    public static int partyMaxSize() {
        return partyMaxSize;
    }

    public static boolean partyEnabled() {
        return partyEnabled;
    }

    public static double partyRadius() {
        return partyRadius;
    }

    /** True when the local player leads the party they are in. */
    public static boolean isPartyLeader() {
        return progress.partyId() != null && progress.partyLeader();
    }

    public static void applyIssues(List<Validation.Issue> issues) {
        ISSUES.clear();
        ISSUES.addAll(issues);
    }

    public static void feedback(boolean ok, String message) {
        lastFeedbackOk = ok;
        lastFeedback = message;
        lastFeedbackAt = System.currentTimeMillis();
    }

    public static String feedbackMessage() {
        // Feedback fades after five seconds so old messages do not linger on a screen.
        return System.currentTimeMillis() - lastFeedbackAt > 5000 ? "" : lastFeedback;
    }

    public static boolean feedbackOk() {
        return lastFeedbackOk;
    }

    public static PlayerProgress progress() {
        return progress;
    }

    public static LevelConfig levelConfig() {
        return levelConfig;
    }

    public static ServerSettings serverSettings() {
        return serverSettings;
    }

    public static boolean admin() {
        return admin;
    }

    /**
     * True only for full server operators. Used to hide operator-only controls; the
     * server re-checks the same tier before applying any of them.
     */
    public static boolean operator() {
        return operator;
    }

    public static boolean zoneGateTesting() {
        return zoneGateTesting;
    }

    public static boolean zoneGateTestAvailable() {
        return zoneGateTestAvailable;
    }

    public static Map<String, QuestDef> quests() {
        return QUESTS;
    }

    public static QuestDef quest(String id) {
        return QUESTS.get(id);
    }

    public static Map<String, net.schwarz.rotasutils.npc.NpcDef> npcs() {
        return NPCS;
    }

    public static net.schwarz.rotasutils.npc.NpcDef npc(String id) {
        return NPCS.get(id);
    }

    public static Map<String, BoardConfig> boards() {
        return BOARDS;
    }

    public static BoardConfig board(String id) {
        return BOARDS.get(id);
    }

    public static Map<String, SkillCategory> categories() {
        return CATEGORIES;
    }

    public static SkillCategory category(String id) {
        return CATEGORIES.get(id);
    }

    public static SkillNode node(String nodeId) {
        for (SkillCategory category : CATEGORIES.values()) {
            SkillNode node = category.node(nodeId);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    public static List<Validation.Issue> issues() {
        return ISSUES;
    }

    public static List<String> audit() {
        return AUDIT;
    }

    /** Experience needed for the player's current level, for the progress bar. */
    public static long xpForNextLevel() {
        return levelConfig.curve().xpToNext(progress.level());
    }

    /**
     * Drops every server-derived value so a new connection starts from a deterministic empty state.
     *
     * <p>Content, player progress, party data, admin flags and transient feedback all belong to one
     * server session; keeping any of them would briefly show server A's data as server B's. Nothing
     * here is a local preference, so nothing a player configured is lost.</p>
     */
    public static void reset() {
        QUESTS.clear();
        BOARDS.clear();
        NPCS.clear();
        CATEGORIES.clear();
        JOBS.clear();
        ZONES.clear();
        selectedZone = "";
        ClientZoneView.clear();
        net.schwarz.rotasutils.client.hud.ZoneHud.clear();
        HOUSES.clear();
        houseAdmin = null;
        ORIGINS.clear();
        RACES.clear();
        ISSUES.clear();
        AUDIT.clear();
        PUFFISH_CATEGORIES.clear();
        PARTY.clear();
        jobCooldownSeconds = 0;
        puffishAvailable = false;
        levelConfig = new LevelConfig();
        serverSettings = new ServerSettings();
        admin = false;
        operator = false;
        zoneGateTesting = false;
        zoneGateTestAvailable = false;
        progress = new PlayerProgress(new UUID(0, 0));
        PARTY_INVITABLE.clear();
        partyInviteFrom = "";
        partyMaxSize = 10;
        partyEnabled = true;
        partyRadius = 64;
        lastFeedback = "";
        lastFeedbackOk = true;
        lastFeedbackAt = 0;
    }
}
