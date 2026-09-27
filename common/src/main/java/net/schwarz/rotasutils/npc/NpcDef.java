package net.schwarz.rotasutils.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A configured NPC.
 *
 * <p>RotasUtils does not spawn or own entities: an NPC definition is a role bolted onto an
 * entity that already exists in the world, matched by uuid. That is deliberate, because it
 * makes every NPC mod work the same way. A plain villager, an armour stand, an Easy NPC
 * entity and a modded quest giver are all bound with the same admin tool click, and breaking
 * or replacing the entity never destroys the configuration - only the binding.</p>
 *
 * <p>What the definition adds is the part entity mods do not agree on: which quests this
 * character hands out and takes back, which board it opens, which merchant it trades from,
 * what it says, and who is allowed to talk to it.</p>
 */
public final class NpcDef {
    /** What talking to this NPC does. The first applicable action is the default one. */
    public enum Role {
        DIALOGUE("Dialogue only"),
        QUEST_GIVER("Quest giver"),
        BOARD_KEEPER("Opens a quest board"),
        MERCHANT("Opens a shop"),
        JOB_MASTER("Assigns configured jobs"),
        STABLE("Runs the stable"),
        /** A hidden artisan: makes its job's crafts from the player's materials for a steep fee. */
        CRAFTER("Crafts for a fee"),
        BLACKSMITH("Blacksmith: repair, refine, salvage"),
        ENCHANTER("Enchanter: disenchant, runes, sockets"),
        ALCHEMIST("Alchemist: sells brews"),
        INNKEEPER("Innkeeper: rest and respawn"),
        PRIEST("Priest: cleanse, bless, lift curses"),
        FORTUNE_TELLER("Fortune teller: daily fortune"),
        BANKER("Banker: deposit and withdraw gold"),
        BOUNTY_MASTER("Bounty master: hunting contracts"),
        GUARD("Guard: area report and directions"),
        TRAINER("Trainer: stats, skills, job"),
        CARTOGRAPHER("Cartographer: waystones and maps"),
        COLLECTOR("Collector: buys materials"),
        AUCTIONEER("Auctioneer: player auction house");

        private final String display;

        Role(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("npc_role", this, display);
        }
    }

    private String id;
    private String name = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.name");
    /** Short line under the name: "Guild Clerk", "Blacksmith". */
    private String title = "";
    private ItemStack icon = new ItemStack(Items.VILLAGER_SPAWN_EGG);
    private Role role = Role.QUEST_GIVER;
    private boolean enabled = true;

    /** The bound entity, or empty while the NPC is authored but not yet placed. */
    private String entityUuid = "";
    /** Recorded when the binding is made, for the admin list and for validation. */
    private String entityType = "";
    private String dimension = "";

    /** Spoken lines, chosen by the state the player is in. */
    private String greeting = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.greeting");
    private String questAvailableLine = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.quest_available");
    private String questActiveLine = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.quest_active");
    private String questReadyLine = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.quest_ready");
    private String blockedLine = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.blocked");
    private String farewell = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.npc.farewell");

    /** Quests offered and turned in here, in the order the dialogue lists them. */
    private final Set<String> questIds = new LinkedHashSet<>();
    /** Board opened by BOARD_KEEPER, and offered as an extra action by any other role. */
    private String boardId = "";
    /** Kernel merchant id opened by MERCHANT. */
    private String merchantId = "";

    private int requiredLevel;
    /** 0 keeps the server default; otherwise the reach in blocks. */
    private double interactionDistance = 6.0;
    /** Draw the "!" / "?" marker above the bound entity. */
    private boolean showMarker = true;
    /** Suppress the entity's own interaction (villager trades, for instance). */
    private boolean captureInteraction = true;
    private String interactionJson = "";
    private net.schwarz.rotasutils.core.NpcInteractions.Definition interactions;

    /** One villager-style trade: the player pays {@code costA} (and {@code costB}) and receives {@code result}. */
    public record Trade(ItemStack costA, ItemStack costB, ItemStack result) {
        public Trade {
            costA = sanitize(costA);
            costB = sanitize(costB);
            result = sanitize(result);
        }

        public boolean valid() {
            return !costA.isEmpty() && !result.isEmpty();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("pay", costA.save(new CompoundTag()));
            if (!costB.isEmpty()) {
                tag.put("pay2", costB.save(new CompoundTag()));
            }
            tag.put("get", result.save(new CompoundTag()));
            return tag;
        }

        public static Trade load(CompoundTag tag) {
            return new Trade(ItemStack.of(tag.getCompound("pay")), ItemStack.of(tag.getCompound("pay2")),
                    ItemStack.of(tag.getCompound("get")));
        }

        private static ItemStack sanitize(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack copy = stack.copy();
            copy.setCount(Math.max(1, Math.min(copy.getMaxStackSize(), copy.getCount())));
            return copy;
        }
    }

    public static final int MAX_TRADES = 32;
    /** The simple shop: opened as the vanilla trading screen, no content pack needed. */
    private final List<Trade> trades = new ArrayList<>();
    /** Turns the mob's AI off so it stays where it was placed. */
    private boolean standStill;
    /** Players cannot hurt the mob. */
    private boolean invulnerable;
    /** Writes the NPC name above the mob's head. */
    private boolean showName;
    public static final int MAX_SERVICES=32;
    private final List<NpcServiceDef> services=new ArrayList<>();
    private NpcBuild build;

    public List<Trade> trades() { return trades; }
    public boolean hasShop() { return !trades.isEmpty() || !merchantId.isBlank(); }
    public boolean standStill() { return standStill; }
    public void setStandStill(boolean value) { standStill = value; }
    public boolean invulnerable() { return invulnerable; }
    public void setInvulnerable(boolean value) { invulnerable = value; }
    public boolean showName() { return showName; }
    public void setShowName(boolean value) { showName = value; }
    public List<NpcServiceDef> services() { return services; }
    public NpcBuild build() { return build; }
    public void setBuild(NpcBuild value) { build=value; }
    public boolean combatCapable() { return build!=null; }
    /** The artisan service (the job it stands in for and its highest level), or null when none is set. */
    public NpcServiceDef crafterService() {
        for (NpcServiceDef service : services) {
            if (service.type() == NpcServiceDef.Type.CRAFTER && !service.jobId().isBlank()) return service;
        }
        return null;
    }

    public String interactionJson() { return interactionJson; }
    public net.schwarz.rotasutils.core.NpcInteractions.Definition interactions() { return interactions; }
    public void setInteractionJson(String json) {
        if (json == null || json.isBlank()) { interactionJson = ""; interactions = null; return; }
        if (json.length() > 24_000) throw new IllegalArgumentException("NPC interaction exceeds 24,000 characters");
        var parsed = com.google.gson.JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("NPC interaction must be an object");
        var definition = net.schwarz.rotasutils.core.NpcInteractions.parse(parsed.getAsJsonObject());
        interactionJson = parsed.toString();
        interactions = definition;
    }

    public NpcDef(String id) {
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
        this.name = name == null ? "" : name;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public ItemStack icon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon == null ? ItemStack.EMPTY : icon;
    }

    public Role role() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role == null ? Role.DIALOGUE : role;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String entityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(String entityUuid) {
        this.entityUuid = entityUuid == null ? "" : entityUuid.trim();
    }

    public boolean bound() {
        return !entityUuid.isEmpty();
    }

    public String entityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType == null ? "" : entityType;
    }

    public String dimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension == null ? "" : dimension;
    }

    public String greeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting == null ? "" : greeting;
    }

    public String questAvailableLine() {
        return questAvailableLine;
    }

    public void setQuestAvailableLine(String line) {
        this.questAvailableLine = line == null ? "" : line;
    }

    public String questActiveLine() {
        return questActiveLine;
    }

    public void setQuestActiveLine(String line) {
        this.questActiveLine = line == null ? "" : line;
    }

    public String questReadyLine() {
        return questReadyLine;
    }

    public void setQuestReadyLine(String line) {
        this.questReadyLine = line == null ? "" : line;
    }

    public String blockedLine() {
        return blockedLine;
    }

    public void setBlockedLine(String line) {
        this.blockedLine = line == null ? "" : line;
    }

    public String farewell() {
        return farewell;
    }

    public void setFarewell(String farewell) {
        this.farewell = farewell == null ? "" : farewell;
    }

    public Set<String> questIds() {
        return questIds;
    }

    public String boardId() {
        return boardId;
    }

    public void setBoardId(String boardId) {
        this.boardId = boardId == null ? "" : boardId;
    }

    public String merchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId == null ? "" : merchantId;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public void setRequiredLevel(int requiredLevel) {
        this.requiredLevel = Math.max(0, requiredLevel);
    }

    public double interactionDistance() {
        return interactionDistance;
    }

    public void setInteractionDistance(double interactionDistance) {
        if (!Double.isFinite(interactionDistance)) throw new IllegalArgumentException("Interaction range must be finite");
        this.interactionDistance = Math.max(1.0, Math.min(64.0, interactionDistance));
    }

    public boolean showMarker() {
        return showMarker;
    }

    public void setShowMarker(boolean showMarker) {
        this.showMarker = showMarker;
    }

    public boolean captureInteraction() {
        return captureInteraction;
    }

    public void setCaptureInteraction(boolean captureInteraction) {
        this.captureInteraction = captureInteraction;
    }

    /** The line to speak for a resolved state, falling back to the greeting. */
    public String lineFor(NpcState state) {
        String line = switch (state) {
            case QUEST_AVAILABLE -> questAvailableLine;
            case QUEST_ACTIVE, OBJECTIVE_COMPLETE -> questActiveLine;
            case READY_TO_TURN_IN -> questReadyLine;
            case REQUIREMENTS_NOT_MET -> blockedLine;
            default -> greeting;
        };
        return line.isBlank() ? greeting : line;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("title", title);
        tag.put("icon", Nbt.saveStack(icon));
        tag.putString("role", role.name());
        tag.putBoolean("enabled", enabled);
        tag.putString("entity_uuid", entityUuid);
        tag.putString("entity_type", entityType);
        tag.putString("dimension", dimension);
        tag.putString("greeting", greeting);
        tag.putString("line_available", questAvailableLine);
        tag.putString("line_active", questActiveLine);
        tag.putString("line_ready", questReadyLine);
        tag.putString("line_blocked", blockedLine);
        tag.putString("farewell", farewell);
        tag.put("quests", Nbt.saveStrings(questIds));
        tag.putString("board", boardId);
        tag.putString("merchant", merchantId);
        tag.putInt("required_level", requiredLevel);
        tag.putDouble("distance", interactionDistance);
        tag.putBoolean("marker", showMarker);
        tag.putBoolean("capture", captureInteraction);
        tag.putString("interactions", interactionJson);
        tag.put("trades", Nbt.saveList(trades, Trade::save));
        tag.putBoolean("stand_still", standStill);
        tag.putBoolean("invulnerable", invulnerable);
        tag.putBoolean("show_name", showName);
        tag.put("services",Nbt.saveList(services,NpcServiceDef::save));
        if(build!=null) tag.put("build",build.save());
        return tag;
    }

    public static NpcDef load(CompoundTag tag) {
        NpcDef npc = new NpcDef(tag.getString("id"));
        npc.name = tag.getString("name");
        npc.title = tag.getString("title");
        npc.icon = Nbt.loadStack(tag, "icon");
        npc.role = Nbt.readEnum(tag, "role", Role.class, Role.QUEST_GIVER);
        npc.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        npc.entityUuid = tag.getString("entity_uuid");
        npc.entityType = tag.getString("entity_type");
        npc.dimension = tag.getString("dimension");
        npc.greeting = tag.getString("greeting");
        npc.questAvailableLine = tag.getString("line_available");
        npc.questActiveLine = tag.getString("line_active");
        npc.questReadyLine = tag.getString("line_ready");
        npc.blockedLine = tag.getString("line_blocked");
        npc.farewell = tag.getString("farewell");
        npc.questIds.addAll(Nbt.loadStrings(tag, "quests"));
        npc.boardId = tag.getString("board");
        npc.merchantId = tag.getString("merchant");
        npc.setRequiredLevel(tag.getInt("required_level"));
        double distance = tag.contains("distance") ? tag.getDouble("distance") : 6.0;
        npc.setInteractionDistance(Double.isFinite(distance) ? distance : 6.0);
        npc.showMarker = !tag.contains("marker") || tag.getBoolean("marker");
        npc.captureInteraction = !tag.contains("capture") || tag.getBoolean("capture");
        npc.setInteractionJson(tag.getString("interactions"));
        for (Trade trade : Nbt.loadList(tag, "trades", Trade::load)) {
            if (trade.valid() && npc.trades.size() < MAX_TRADES) {
                npc.trades.add(trade);
            }
        }
        npc.standStill = tag.getBoolean("stand_still");
        npc.invulnerable = tag.getBoolean("invulnerable");
        npc.showName = tag.getBoolean("show_name");
        for(NpcServiceDef service:Nbt.loadList(tag,"services",NpcServiceDef::load)) { if(npc.services.size()>=MAX_SERVICES) throw new IllegalArgumentException("NPC service count exceeds "+MAX_SERVICES); npc.services.add(service); }
        if(tag.contains("build")) npc.build=NpcBuild.load(tag.getCompound("build"));
        return npc;
    }

    /** Deep copy under a new id; round-tripped so added fields cannot be forgotten. */
    public NpcDef copyAs(String newId) {
        CompoundTag tag = save();
        tag.putString("id", newId);
        // A copy must not answer for the same entity, or two NPCs would fight over one click.
        tag.putString("entity_uuid", "");
        return load(tag);
    }

    /** Problems an admin should see before this NPC is expected to work. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (name.isBlank()) {
            problems.add(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc_problem.no_name"));
        }
        if (!bound()) {
            problems.add(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc_problem.not_placed"));
        }
        if (role == Role.QUEST_GIVER && questIds.isEmpty()) {
            problems.add(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc_problem.giver_no_quests"));
        }
        if (role == Role.BOARD_KEEPER && boardId.isBlank()) {
            problems.add(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc_problem.keeper_no_board"));
        }
        if (role == Role.MERCHANT && !hasShop()) {
            problems.add(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc_problem.shop_no_trade"));
        }
        return problems;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static NpcDef read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }
}
