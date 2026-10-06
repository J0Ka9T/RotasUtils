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

@Environment(EnvType.CLIENT)
public final class ClientState {
    private static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();
    private static final Map<String, BoardConfig> BOARDS = new LinkedHashMap<>();
    private static final Map<String, net.schwarz.rotasutils.npc.NpcDef> NPCS = new LinkedHashMap<>();
    private static final Map<java.util.UUID, net.schwarz.rotasutils.npc.NpcDef> NPC_BY_ENTITY = new java.util.HashMap<>();
    private static final Map<String, SkillCategory> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, net.schwarz.rotasutils.job.JobDef> JOBS = new LinkedHashMap<>();
    private static final List<net.schwarz.rotasutils.title.TitleDef> TITLES = new ArrayList<>();
    private static final Map<String, net.schwarz.rotasutils.core.ZoneDef> ZONES = new LinkedHashMap<>();
    private static String selectedZone = "";
    private static final List<ClientHouse> HOUSES = new ArrayList<>();
    private static ClientHouseAdminState houseAdmin;
    private static final Map<String, String> ORIGINS = new LinkedHashMap<>();
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
    private static boolean operator;
    private static boolean zoneGateTesting;
    private static boolean zoneGateTestAvailable;
    private static CompoundTag refineRules = new CompoundTag();
    private static CompoundTag weaponMemoryRules = new CompoundTag();
    private static CompoundTag eventCatalogue = new CompoundTag();
    private static CompoundTag dropFilter = new CompoundTag();
    private static CompoundTag tracks = new CompoundTag();
    private static CompoundTag titleProgress = new CompoundTag();
    private static String lastFeedback = "";
    private static boolean lastFeedbackOk = true;
    private static long lastFeedbackAt;

    private ClientState() {
    }

    private static net.schwarz.rotasutils.core.WorthTable worth = net.schwarz.rotasutils.core.WorthTable.empty();

    public static net.schwarz.rotasutils.core.WorthTable worth() {
        return worth;
    }

    private static void applyWorth(CompoundTag tag) {
        try {
            java.util.Map<String, Long> prices = new java.util.TreeMap<>();
            CompoundTag map = tag.getCompound("prices");
            for (String key : map.getAllKeys()) {
                prices.put(key, map.getLong(key));
            }
            worth = new net.schwarz.rotasutils.core.WorthTable(prices, tag.getInt("sell"), tag.getInt("silver"), tag.getInt("gold"));
        } catch (RuntimeException unreadable) {
            worth = net.schwarz.rotasutils.core.WorthTable.empty();
        }
    }

    public static void applyContent(CompoundTag tag) {
        if (tag.contains("worth")) {
            applyWorth(tag.getCompound("worth"));
        }
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
        NPC_BY_ENTITY.clear();
        for (net.schwarz.rotasutils.npc.NpcDef npc : NPCS.values()) {
            try {
                if (npc.bound()) NPC_BY_ENTITY.put(java.util.UUID.fromString(npc.entityUuid()), npc);
            } catch (IllegalArgumentException malformed) {
            }
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
            }
            ORIGINS.put(id, name);
        }
        jobCooldownSeconds = tag.getLong("job_cooldown");
        if (tag.contains("tier_max")) net.schwarz.rotasutils.job.JobUnlockTable.setTierMax(tag.getIntArray("tier_max"));
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

    public record EventRuleView(String type, String filter, boolean enabled, double xpMultiplier,
                                long xpFlat, long gold, String announce, int cooldownSeconds) {
        public boolean plain() {
            return filter == null || filter.isBlank();
        }
    }

    public static boolean eventsEnabled() {
        return eventCatalogue.getBoolean("enabled");
    }

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

    public static EventRuleView eventRule(String type, String filter) {
        for (EventRuleView rule : eventRules()) {
            if (rule.type().equals(type) && rule.filter().equals(filter == null ? "" : filter)) {
                return rule;
            }
        }
        return null;
    }

    public static boolean dropFilterEnabled() {
        return dropFilter.getBoolean("enabled");
    }

    public static boolean dropBlockedGlobally(String item) {
        return item != null && Nbt.loadStrings(dropFilter, "blocked").contains(item);
    }

    public static java.util.List<String> dropBlockedGlobally() {
        return Nbt.loadStrings(dropFilter, "blocked");
    }

    public static int dropBlockedCount(String entity) {
        return entity == null ? 0 : dropFilter.getCompound("by_entity").getInt(entity);
    }

    public static CompoundTag tracks() {
        return tracks;
    }

    private static CompoundTag titleHolders = new CompoundTag();

    public static String titleHolder(String id) {
        return id == null ? "" : titleHolders.getString(id);
    }

    public static long titleProgress(String id) {
        return id == null ? 0 : titleProgress.getLong(id);
    }

    public static List<net.schwarz.rotasutils.title.TitleDef> titles() {
        return TITLES;
    }

    public static net.schwarz.rotasutils.title.TitleDef title(String id) {
        for (net.schwarz.rotasutils.title.TitleDef title : TITLES) {
            if (title.id().equals(id)) {
                return title;
            }
        }
        return null;
    }

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

    public static Map<String, net.schwarz.rotasutils.core.ZoneDef> zones() {
        return ZONES;
    }

    public static List<ClientHouse> houses() {
        return java.util.Collections.unmodifiableList(HOUSES);
    }

    public static ClientHouseAdminState houseAdmin() {
        return houseAdmin;
    }

    public static net.schwarz.rotasutils.core.ZoneDef zone(String id) {
        return id == null ? null : ZONES.get(id);
    }

    private static boolean zoneBordersPinned;

    public static boolean zoneBordersPinned() {
        return zoneBordersPinned;
    }

    public static void setZoneBordersPinned(boolean pinned) {
        zoneBordersPinned = pinned;
    }

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

    public static boolean pufferfishSkills() {
        return dev.architectury.platform.Platform.isModLoaded("puffish_skills") && levelConfig.pufferfishSkills();
    }

    public static String audienceBlock(SkillCategory category) {
        return net.schwarz.rotasutils.skill.SkillRules.audienceBlock(category,
                java.util.stream.Stream.of(progress.mainJob(),progress.subJob()).filter(id->!id.isEmpty()).toList(), RACES,
                ClientState::jobName, ClientState::raceName);
    }

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

    public record PartyMember(UUID id, String name, int level, boolean leader,
                              boolean online, boolean nearby) {
    }

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

    public static List<InvitablePlayer> partyInvitable() {
        return PARTY_INVITABLE;
    }

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

    public static net.schwarz.rotasutils.npc.NpcDef npcByEntity(java.util.UUID entity) {
        return NPC_BY_ENTITY.get(entity);
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

    public static long xpForNextLevel() {
        return levelConfig.curve().xpToNext(progress.level());
    }

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
