package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.compat.PuffishSkillsCompat;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.item.HouseWandItem;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.Validation;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.util.Nbt;

import java.util.List;
import java.util.UUID;

/**
 * Client/server messaging.
 *
 * <p>Only two message shapes exist: the client sends {@link #ACTION} with an action
 * id and a parameter bag, and the server pushes state snapshots back. The client
 * never asserts an outcome; every action id is re-validated in {@link ServerActions}.
 */
public final class RotasNetwork {
    public static final ResourceLocation ACTION = Rotasutils.id("action");
    public static final ResourceLocation SYNC_PROGRESS = Rotasutils.id("sync_progress");
    public static final ResourceLocation SYNC_CONTENT = Rotasutils.id("sync_content");
    public static final ResourceLocation OPEN_SCREEN = Rotasutils.id("open_screen");
    public static final ResourceLocation FEEDBACK = Rotasutils.id("feedback");
    public static final ResourceLocation SEALED_CRAFT = Rotasutils.id("sealed_craft");
    public static final ResourceLocation PICK_RESULT = Rotasutils.id("pick_result");
    public static final ResourceLocation VALIDATION = Rotasutils.id("validation");
    public static final ResourceLocation SYNC_PARTY = Rotasutils.id("sync_party");
    public static final ResourceLocation SYNC_KERNEL_UI = Rotasutils.id("sync_kernel_ui");
    public static final ResourceLocation KERNEL_PREVIEW = Rotasutils.id("kernel_preview");
    /** What a player may see of the zones in their dimension, with their own lock state. */
    public static final ResourceLocation ZONE_VIEW = Rotasutils.id("zone_view");

    /** Per-player request throttle, measured on a monotonic clock. */
    private static final RequestThrottle THROTTLE = new RequestThrottle();

    /** The narrow player context consumed by the content encoder and its tests. */
    record ContentActor(UUID playerId, boolean admin, boolean operator,
                               net.minecraft.world.item.ItemStack mainHand,
                               net.minecraft.world.item.ItemStack offHand,
                               MinecraftServer server,
                               HouseAdminService.Selection heldSelection) {
        public ContentActor(UUID playerId, boolean admin, boolean operator,
                            net.minecraft.world.item.ItemStack mainHand,
                            net.minecraft.world.item.ItemStack offHand,
                            MinecraftServer server) {
            this(playerId, admin, operator, mainHand, offHand, server, null);
        }

        public ContentActor {
            if (playerId == null) throw new IllegalArgumentException("Content actor id is required");
            mainHand = mainHand == null ? net.minecraft.world.item.ItemStack.EMPTY : mainHand.copy();
            offHand = offHand == null ? net.minecraft.world.item.ItemStack.EMPTY : offHand.copy();
        }
    }

    private RotasNetwork() {
    }

    public static void init() {
        ProgressSync.init();
        AdminNetwork.init();
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ACTION, (buf, context) -> {
            CompoundTag payload = buf.readNbt();
            String action = buf.readUtf(64);
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player)) {
                    return;
                }
                if (!rateLimit(player)) {
                    return;
                }
                ServerActions.handle(player, action, payload == null ? new CompoundTag() : payload);
            });
        });
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientNetworkHandlers.register();
    }

    private static boolean rateLimit(ServerPlayer player) {
        RotasData data = RotasData.get(player.server);
        return THROTTLE.allow(player.getUUID(), data.serverSettings().clientRequestCooldownMillis(),
                System.nanoTime());
    }

    public static void forget(ServerPlayer player) {
        THROTTLE.forget(player.getUUID());
        ProgressSync.forget(player.getUUID());
        AdminNetwork.forget(player.getUUID());
    }

    /** Drops every throttle entry; used when the server stops. */
    public static void clear() {
        THROTTLE.clear();
    }

    // Client -> server -----------------------------------------------------

    @Environment(EnvType.CLIENT)
    public static void sendAction(String action, CompoundTag payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(payload);
        buf.writeUtf(action, 64);
        NetworkManager.sendToServer(ACTION, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendAction(String action) {
        sendAction(action, new CompoundTag());
    }

    // Server -> client -----------------------------------------------------

    /** Content catalog plus this player's kernel state, for the control screens. */
    public static void syncKernelUi(ServerPlayer player, CompoundTag snapshot) {
        if (!connected(player)) { return; }
        // Record what this player now holds, so a later queued sync can tell when nothing changed.
        SyncQueue.changed(true, player.getUUID(), snapshot);
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(snapshot);
        NetworkManager.sendToPlayer(player, SYNC_KERNEL_UI, buf);
    }

    /** One loot roll the administrator asked to see. */
    public static void syncKernelPreview(ServerPlayer player, CompoundTag preview) {
        if (!connected(player)) { return; }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(preview);
        NetworkManager.sendToPlayer(player, KERNEL_PREVIEW, buf);
    }

    public static void syncProgress(ServerPlayer player) {
        if (!connected(player)) { return; }
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        CompoundTag snapshot = progress.clientSnapshot();
        if (data.kernel() != null) {
            CompoundTag stats = new CompoundTag();
            data.kernel().stats(player).forEach(stats::putDouble);
            snapshot.put("final_stats", stats);
        }
        // How far this player has come towards each title. The tallies themselves are server-only
        // (they live behind the rpg. prefix), so the screen is told the number, not the counter.
        CompoundTag titleProgress = new CompoundTag();
        for (var title : data.titles().values()) {
            titleProgress.putLong(title.id(),
                    net.schwarz.rotasutils.server.TitleService.progressOf(data, progress, title));
        }
        snapshot.put("title_progress", titleProgress);
        snapshot.put("tracks", trackState(player, data, progress));
        PuffishSkillsCompat.writeClientSummary(player, snapshot);
        snapshot.put("races", Nbt.saveStrings(net.schwarz.rotasutils.compat.OriginsCompat.origins(player)));
        if (ProgressSync.send(player, snapshot)) { progress.clearDirty(); return; }
        snapshot.putInt("delta_protocol", ProgressDelta.PROTOCOL);
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(snapshot);
        if (buf.readableBytes() > ProgressChunks.MAX_BYTES / 2) {
            buf.release();
            ProgressSync.oversized(player);
            progress.clearDirty();
            return;
        }
        NetworkManager.sendToPlayer(player, SYNC_PROGRESS, buf);
        progress.clearDirty();
    }

    /**
     * Sends the content snapshot the client needs to render.
     *
     * <p>Non-admins receive only published quests and the boards' player-visible
     * fields; unpublished drafts never leave the server for a normal player.
     */
    public static CompoundTag contentTag(ServerPlayer player, RotasData data) {
        CompoundTag tag = contentTag(new ContentActor(player.getUUID(), BoardService.isAdmin(player, data),
                BoardService.isOperator(player), player.getMainHandItem(), player.getOffhandItem(), player.server,
                houseSelection(player.getMainHandItem(), player.getOffhandItem())), data);
        if (tag.getBoolean("admin")) {
            tag.putBoolean("zone_gate_testing", net.schwarz.rotasutils.server.ZoneGateService.adminTesting(player.getUUID()));
            tag.putBoolean("zone_gate_test_available", player.hasPermissions(2));
        }
        return tag;
    }

    /** Builds content from an authenticated actor context without touching network state. */
    static CompoundTag contentTag(ContentActor actor, RotasData data) {
        boolean admin = actor.admin();

        CompoundTag tag = new CompoundTag();
        tag.putBoolean("admin", admin);
        tag.putBoolean("op", actor.operator());

        java.util.List<QuestDef> quests = new java.util.ArrayList<>();
        for (QuestDef quest : data.quests().values()) {
            if (admin || quest.published()) {
                quests.add(quest);
            }
        }
        tag.put("quests", Nbt.saveList(quests, QuestDef::save));

        java.util.List<BoardConfig> boards = new java.util.ArrayList<>();
        for (BoardConfig board : data.boards().values()) {
            if (admin || board.visible()) {
                boards.add(board);
            }
        }
        tag.put("boards", Nbt.saveList(boards, BoardConfig::save));
        // The few refine numbers a tooltip needs. Without them a client would have to guess what a
        // "+7" is worth, and a server that retuned refinement would read wrong in every tooltip.
        tag.put("titles", Nbt.saveList(net.schwarz.rotasutils.server.TitleService.sorted(data),
                net.schwarz.rotasutils.title.TitleDef::save));
        // The holder of every claimed unique title, by name: the list shows who got there first.
        CompoundTag holders = new CompoundTag();
        for (var title : data.titles().values()) {
            java.util.UUID owner = title.unique() ? data.uniqueTitleOwner(title.id()) : null;
            if (owner != null) {
                var record = data.peek(owner);
                holders.putString(title.id(), record == null || record.lastKnownName().isBlank() ? "?" : record.lastKnownName());
            }
        }
        tag.put("title_holders", holders);
        // Worn titles of everyone online, so a name plate can show them without a packet of its own.
        CompoundTag titles = new CompoundTag();
        if (actor.server() != null) {
            for (ServerPlayer online : actor.server().getPlayerList().getPlayers()) {
                var record = data.peek(online.getUUID());
                var worn = record == null ? null : net.schwarz.rotasutils.server.TitleService.worn(data, record);
                if (worn != null) {
                    CompoundTag entry = new CompoundTag();
                    entry.putString("name", worn.name());
                    entry.putInt("color", worn.color());
                    titles.put(online.getUUID().toString(), entry);
                }
            }
        }
        tag.put("player_titles", titles);
        if (admin) {
            // The whole event catalogue: small (one line per rule) and only ever sent to an admin.
            CompoundTag events = new CompoundTag();
            events.putBoolean("enabled", net.schwarz.rotasutils.server.EventService.enabled(data));
            net.minecraft.nbt.ListTag rules = new net.minecraft.nbt.ListTag();
            for (var rule : net.schwarz.rotasutils.server.EventService.rules(data)) {
                CompoundTag entry = new CompoundTag();
                entry.putString("type", rule.type);
                entry.putString("filter", rule.filter);
                entry.putBoolean("enabled", rule.enabled);
                entry.putDouble("xp_multiplier", rule.xpMultiplier);
                entry.putLong("xp_flat", rule.xpFlat);
                entry.putLong("gold", rule.gold);
                entry.putString("announce", rule.announce);
                entry.putInt("cooldown", rule.cooldownSeconds);
                rules.add(entry);
            }
            events.put("rules", rules);
            tag.put("events", events);
        }
        if (admin) {
            // The item browser needs to know what is switched off; the per-entity lists come with the
            // mob page instead, so an admin session never carries the whole filter at once.
            CompoundTag filter = new CompoundTag();
            filter.putBoolean("enabled", net.schwarz.rotasutils.server.DropFilterService.enabled(data));
            filter.put("blocked", Nbt.saveStrings(
                    net.schwarz.rotasutils.server.DropFilterService.blockedGlobally(data)));
            CompoundTag perEntity = new CompoundTag();
            var rules = net.schwarz.rotasutils.server.SeasonService.rules(data).drops;
            if (rules != null && rules.filter != null) {
                rules.filter.byEntity.forEach((entity, items) -> perEntity.putInt(entity, items.length));
            }
            filter.put("by_entity", perEntity);
            tag.put("drop_filter", filter);
        }
        // Cards are configuration, and a stack only carries an id, so the table travels with the content.
        tag.put("cards", net.schwarz.rotasutils.core.CardIndex.save());

        var refineRules = net.schwarz.rotasutils.server.SeasonService.rules(data).refine;
        if (refineRules != null) {
            CompoundTag refine = new CompoundTag();
            refine.putBoolean("enabled", refineRules.enabled);
            refine.putInt("safe_level", refineRules.safeLevel);
            refine.putInt("max_level", refineRules.maxLevel);
            refine.putDouble("attack_per_level", refineRules.attackPerLevel);
            refine.putDouble("attack_per_over_level", refineRules.attackPerOverLevel);
            refine.putDouble("defense_per_level", refineRules.defensePerLevel);
            refine.putDouble("defense_per_over_level", refineRules.defensePerOverLevel);
            tag.put("refine", refine);
        }
        var memoryRules = net.schwarz.rotasutils.server.SeasonService.rules(data).weaponMemory;
        if (memoryRules != null) {
            CompoundTag memory = new CompoundTag();
            memory.putBoolean("enabled", memoryRules.enabled);
            memory.putLongArray("milestones", memoryRules.milestones);
            memory.putDouble("damage_per_rank", memoryRules.damagePerRank);
            memory.putDouble("favored_bonus", memoryRules.favoredBonus);
            memory.putInt("favored_from_rank", memoryRules.favoredFromRank);
            tag.put("weapon_memory", memory);
        }
        // Players get the NPCs they can meet; the unbound drafts are an admin concern.
        java.util.List<net.schwarz.rotasutils.npc.NpcDef> npcs = new java.util.ArrayList<>();
        for (net.schwarz.rotasutils.npc.NpcDef npc : data.npcs().values()) {
            if (admin || (npc.enabled() && npc.bound())) {
                npcs.add(npc);
            }
        }
        tag.put("npcs", Nbt.saveList(npcs, net.schwarz.rotasutils.npc.NpcDef::save));
        tag.put("categories", Nbt.saveList(data.categories().values(), SkillCategory::save));
        tag.put("level_config", data.levelConfig().save());
        // Zones are an admin concern: only an admin editor needs them, and the nameplate colour
        // reads the level back from the entity's custom name instead.
        if (admin) {
            tag.put("zones", Nbt.saveList(data.zones().values(), net.schwarz.rotasutils.core.ZoneDef::save));
        }
        // Houses: name, tier, area and rental status (never owner ids), so the House Wand can show them;
        // "mine" marks houses this player owns or belongs to.
        net.minecraft.nbt.ListTag houses = new net.minecraft.nbt.ListTag();
        for (net.schwarz.rotasutils.house.HouseDefinition house : data.houses().values()) {
            net.schwarz.rotasutils.house.HouseTenancy tenancy = data.houseTenancy(house.id());
            CompoundTag entry = new CompoundTag();
            entry.putString("id", house.id());
            entry.putString("name", house.name());
            entry.putString("tier", house.tier());
            entry.put("bounds", house.bounds().save());
            entry.putBoolean("enabled", house.enabled());
            entry.putString("status", tenancy.status().name());
            entry.putBoolean("mine", actor.playerId().equals(tenancy.owner()) || tenancy.members().contains(actor.playerId()));
            houses.add(entry);
        }
        tag.put("houses", houses);
        if (admin) {
            HouseAdminService.Selection selection = actor.heldSelection() != null
                    ? actor.heldSelection() : houseSelection(actor);
            try {
                tag.put("house_admin", ClientHouseAdminState.of(data.houses().values(), data.houseConfig(),
                        data.houseTenancies(), selection).save());
            } catch (IllegalArgumentException malformedWand) {
                // A corrupt held stack must not prevent an administrator from receiving
                // current revision tokens; omit only the optional selection summary.
                tag.put("house_admin", ClientHouseAdminState.of(data.houses().values(), data.houseConfig(),
                        data.houseTenancies(), null).save());
            }
        }
        tag.put("jobs", Nbt.saveList(data.jobs().values(), net.schwarz.rotasutils.job.JobDef::save));
        tag.putLong("job_cooldown", data.serverSettings().jobChangeCooldownSeconds());
        CompoundTag origins = new CompoundTag();
        net.schwarz.rotasutils.compat.OriginsCompat.allOrigins(actor.server()).forEach(origins::putString);
        tag.put("origins", origins);
        if (admin) {
            tag.put("server_settings", data.serverSettings().save());
            tag.put("audit", Nbt.saveStrings(data.auditLog()));
        }

        return tag;
    }

    /**
     * Asks for fresh content after something changed. Content is shared, so every online player is
     * refreshed, this one first; {@link SyncQueue} coalesces the requests and only sends a player a
     * snapshot that actually differs from what they have.
     */
    public static void syncContent(ServerPlayer player) {
        if (player != null) {
            SyncQueue.content(player.server, player);
        }
    }

    /** Builds and sends the content snapshot now. Only {@link SyncQueue#flush} calls this. */
    static void sendContent(ServerPlayer player) {
        if (!connected(player)) { return; }
        CompoundTag tag = contentTag(player, RotasData.get(player.server));
        if (!SyncQueue.changed(false, player.getUUID(), tag)) { return; }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(tag);
        NetworkManager.sendToPlayer(player, SYNC_CONTENT, buf);
    }

    /** Builds and sends the kernel UI snapshot now, unless the player already has this exact one. */
    static void sendKernelUi(ServerPlayer player) {
        if (!connected(player)) { return; }
        CompoundTag snapshot = KernelUi.snapshot(player);
        if (SyncQueue.changed(true, player.getUUID(), snapshot)) {
            syncKernelUi(player, snapshot);
        }
    }

    /** Refreshes the content snapshot for every connected player after a server mutation. */
    public static void syncContent(MinecraftServer server) {
        SyncQueue.content(server, null);
    }

    /**
     * Sends the party roster: names, levels and who is online, which the client cannot
     * work out on its own because it only ever sees its own progress.
     */
    public static void syncParty(ServerPlayer player) {
        if (!connected(player)) { return; }
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        CompoundTag tag = new CompoundTag();

        String invite = net.schwarz.rotasutils.server.PartyService.pendingInviteFrom(player);
        tag.putString("invite_from", invite == null ? "" : invite);
        tag.putInt("max_size", data.serverSettings().maxPartySize());
        tag.putBoolean("enabled", data.serverSettings().partySystemEnabled());
        tag.putDouble("radius", data.serverSettings().partyNearbyRadius());

        net.minecraft.nbt.ListTag members = new net.minecraft.nbt.ListTag();
        if (progress.partyId() != null) {
            for (UUID memberId : net.schwarz.rotasutils.server.PartyService.members(data, progress.partyId())) {
                PlayerProgress member = data.progress(memberId);
                ServerPlayer online = player.server.getPlayerList().getPlayer(memberId);
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", memberId);
                entry.putString("name", online != null
                        ? online.getGameProfile().getName()
                        : member.lastKnownName());
                entry.putInt("level", member.level());
                entry.putBoolean("leader", member.partyLeader());
                entry.putBoolean("online", online != null);
                entry.putBoolean("nearby", online != null
                        && online.level() == player.level()
                        && online.distanceTo(player) <= data.serverSettings().partyNearbyRadius());
                members.add(entry);
            }
        }
        tag.put("members", members);

        // Players close enough to invite with one click; the screen never asks for a typed name.
        net.minecraft.nbt.ListTag nearby = new net.minecraft.nbt.ListTag();
        boolean canInvite = progress.partyId() == null || progress.partyLeader();
        if (canInvite) {
            for (ServerPlayer candidate
                    : net.schwarz.rotasutils.server.PartyService.invitableNearby(player, data)) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", candidate.getUUID());
                entry.putString("name", candidate.getGameProfile().getName());
                entry.putInt("level", data.progress(candidate.getUUID()).level());
                nearby.add(entry);
            }
        }
        tag.put("invitable", nearby);

        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(tag);
        NetworkManager.sendToPlayer(player, SYNC_PARTY, buf);
    }

    /** Pushes the roster to everyone in the party, so a change is seen by all at once. */
    public static void syncPartyAll(ServerPlayer actor) {
        RotasData data = RotasData.get(actor.server);
        UUID partyId = data.progress(actor.getUUID()).partyId();
        syncParty(actor);
        if (partyId == null) {
            return;
        }
        for (ServerPlayer member : net.schwarz.rotasutils.server.PartyService.online(
                actor.server, data, partyId)) {
            if (!member.getUUID().equals(actor.getUUID())) {
                syncParty(member);
                syncProgress(member);
            }
        }
    }

    public static void openScreen(ServerPlayer player, String screenId, CompoundTag payload) {
        if (!connected(player)) { return; }
        // Not queued: the screen is about to open on this snapshot, so it has to arrive first.
        sendContent(player);
        syncProgress(player);
        syncParty(player);
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(screenId, 64);
        buf.writeNbt(payload);
        NetworkManager.sendToPlayer(player, OPEN_SCREEN, buf);
    }

    /** Opens the kernel console on a section, pushing the snapshot it needs first. */
    public static void openKernelConsole(ServerPlayer player, String section) {
        openKernelConsole(player, section, "");
    }

    public static void openKernelConsole(ServerPlayer player, String section, String selected) {
        KernelUi.sync(player);
        CompoundTag payload = new CompoundTag();
        payload.putString("section", section == null ? "CHARACTER" : section);
        payload.putString("selected", selected);
        openScreen(player, "kernel_hub", payload);
    }

    public static void openMainMenu(ServerPlayer player) {
        openScreen(player, "main_menu", new CompoundTag());
    }

    /**
     * Opens the refinement bench on the item the player is holding. The payload is what the screen shows
     * before the first attempt; every later attempt re-reads the same numbers on the server.
     */
    public static void openRefine(ServerPlayer player) {
        openScreen(player, "refine", refineState(player));
    }

    /**
     * Opens the drop list of one kind of mob: what it normally drops, how often, and which of those an
     * administrator has switched off. The list is the real loot table, rolled, so it covers modded mobs
     * and data packs without knowing anything about either.
     */
    public static void openMobDrops(ServerPlayer player, String entityId) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        CompoundTag payload = new CompoundTag();
        payload.putString("entity", entityId);
        payload.putString("entity_name", entityName(entityId));
        payload.putBoolean("filter_enabled", net.schwarz.rotasutils.server.DropFilterService.enabled(data));

        java.util.List<String> blocked = net.schwarz.rotasutils.server.DropFilterService.blockedFor(data, entityId);
        java.util.Set<String> listed = new java.util.LinkedHashSet<>();
        net.minecraft.nbt.ListTag rows = new net.minecraft.nbt.ListTag();
        for (var drop : net.schwarz.rotasutils.server.VanillaDropScan.scan(player.serverLevel(), player, entityId)) {
            rows.add(dropRow(data, entityId, drop.item(), drop.maxCount(), drop.chance(), "vanilla"));
            listed.add(drop.item());
        }
        // Items an administrator added for this mob, and items they blocked that the roll never produced,
        // are listed too - otherwise a switched-off drop would vanish from the page that switched it off.
        var plain = net.schwarz.rotasutils.server.SeasonService.rules(data).drops.plain;
        if (plain != null && plain.byEntity != null && plain.byEntity.get(entityId) != null) {
            for (String line : plain.byEntity.get(entityId).items) {
                String item = line == null ? "" : line.trim().split("\s+")[0];
                if (!item.isBlank() && listed.add(item)) {
                    rows.add(dropRow(data, entityId, item, 1, -1, "added"));
                }
            }
        }
        for (String item : blocked) {
            if (listed.add(item)) {
                rows.add(dropRow(data, entityId, item, 1, -1, "vanilla"));
            }
        }
        payload.put("drops", rows);
        openScreen(player, "mob_drops", payload);
    }

    private static CompoundTag dropRow(net.schwarz.rotasutils.data.RotasData data, String entityId,
                                       String item, int maxCount, double chance, String origin) {
        CompoundTag row = new CompoundTag();
        row.putString("item", item);
        row.putInt("max", maxCount);
        row.putDouble("chance", chance);
        row.putString("origin", origin);
        row.putBoolean("blocked", net.schwarz.rotasutils.server.DropFilterService.blocked(data, entityId, item));
        row.putBoolean("blocked_everywhere",
                net.schwarz.rotasutils.server.DropFilterService.blocked(data, "", item));
        return row;
    }

    private static String entityName(String entityId) {
        var location = net.minecraft.resources.ResourceLocation.tryParse(entityId);
        var type = location == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(location);
        return type == null ? entityId : type.getDescription().getString();
    }

    /**
     * The daily and season tracks as the client needs them: counts, claim records, rungs and rewards.
     * Small enough to ride with every progress sync, so the menu row is always current.
     */
    static CompoundTag trackState(ServerPlayer player, net.schwarz.rotasutils.data.RotasData data, PlayerProgress progress) {
        var rules = net.schwarz.rotasutils.server.SeasonService.rules(data);
        CompoundTag tracks = new CompoundTag();

        CompoundTag daily = new CompoundTag();
        daily.putBoolean("enabled", rules.daily.enabled);
        daily.putInt("done", net.schwarz.rotasutils.server.DailyService.done(progress));
        daily.putInt("claimed", net.schwarz.rotasutils.server.DailyService.claimedMask(progress));
        daily.putLong("reset_in", net.schwarz.rotasutils.core.DailyTrack.secondsToReset(java.time.LocalDateTime.now()));
        net.minecraft.nbt.ListTag dailyTiers = new net.minecraft.nbt.ListTag();
        for (var tier : rules.daily.tiers) {
            dailyTiers.add(rewardTag(tier.completions, "", tier.reward));
        }
        daily.put("tiers", dailyTiers);
        tracks.put("daily", daily);

        CompoundTag season = new CompoundTag();
        season.putBoolean("enabled", rules.seasonTrack.enabled);
        season.putString("season", rules.seasonTrack.seasonId);
        season.putLong("points", progress.rankPoints());
        season.putInt("claimed", net.schwarz.rotasutils.server.SeasonTrackService.claimedMask(data, progress));
        net.minecraft.nbt.ListTag seasonTiers = new net.minecraft.nbt.ListTag();
        for (var tier : rules.seasonTrack.tiers) {
            seasonTiers.add(rewardTag(tier.points, tier.name, tier.reward));
        }
        season.put("tiers", seasonTiers);
        tracks.put("season", season);
        return tracks;
    }

    private static CompoundTag rewardTag(long needed, String name, net.schwarz.rotasutils.level.SeasonRules.TrackReward reward) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("needed", needed);
        tag.putString("name", name == null ? "" : name);
        tag.putLong("gold", reward.gold);
        tag.putLong("xp", reward.xp);
        tag.putLong("rank", reward.rankPoints);
        tag.put("items", Nbt.saveStrings(java.util.List.of(reward.items)));
        return tag;
    }

    /**
     * Opens the monster book. Every kind the player has killed, with its count and rung; for the selected
     * kind, once its drops are revealed, what it actually drops - rolled from its real loot table.
     */
    public static void openBestiary(ServerPlayer player, String selected) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        var progress = data.progress(player.getUUID());
        var book = net.schwarz.rotasutils.server.SeasonService.rules(data).bestiary;
        CompoundTag payload = new CompoundTag();
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        progress.bestiary().entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .forEach(entry -> {
                    CompoundTag row = new CompoundTag();
                    row.putString("entity", entry.getKey());
                    row.putLong("kills", entry.getValue());
                    row.putInt("tier", net.schwarz.rotasutils.server.BestiaryService.tier(data, progress, entry.getKey()));
                    entries.add(row);
                });
        payload.put("entries", entries);
        payload.putLongArray("tiers", book.tiers);
        payload.putDouble("damage_per_tier", book.damagePerTier);
        payload.putDouble("loot_per_tier", book.lootPerTier);
        payload.putInt("reveal_at", book.revealDropsAt);
        String chosen = selected == null ? "" : selected;
        payload.putString("selected", chosen);
        if (!chosen.isBlank() && progress.bestiaryKills(chosen) > 0
                && net.schwarz.rotasutils.server.BestiaryService.revealed(data, progress, chosen)) {
            net.minecraft.nbt.ListTag drops = new net.minecraft.nbt.ListTag();
            for (var drop : net.schwarz.rotasutils.server.VanillaDropScan.scan(player.serverLevel(), player, chosen)) {
                CompoundTag row = new CompoundTag();
                row.putString("item", drop.item());
                row.putInt("max", drop.maxCount());
                row.putDouble("chance", drop.chance());
                row.putBoolean("blocked", net.schwarz.rotasutils.server.DropFilterService.blocked(data, chosen, drop.item()));
                drops.add(row);
            }
            payload.put("drops", drops);
        }
        openScreen(player, "bestiary", payload);
    }

    /** Opens the salvage bench on the main inventory, with what each piece would give. */
    public static void openSalvage(ServerPlayer player) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        CompoundTag payload = new CompoundTag();
        net.minecraft.nbt.ListTag rows = new net.minecraft.nbt.ListTag();
        var items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            var stack = items.get(slot);
            var yield = net.schwarz.rotasutils.server.SalvageService.preview(data, stack);
            if (!yield.possible()) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putInt("slot", slot);
            row.put("item", stack.save(new CompoundTag()));
            row.putLong("gold", yield.gold());
            row.putString("ore", yield.ore());
            row.putInt("ore_min", yield.oreMin());
            row.putInt("ore_max", yield.oreMax());
            row.putInt("cards", yield.cards().size());
            rows.add(row);
        }
        payload.put("rows", rows);
        payload.putBoolean("enabled", net.schwarz.rotasutils.server.SeasonService.rules(data).salvage.enabled);
        openScreen(player, "salvage", payload);
    }

    /** Opens the admin world-event control: start, stop and switch automatic events. */
    public static void openWorldEventAdmin(ServerPlayer player) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        openScreen(player, "world_events", net.schwarz.rotasutils.server.WorldEventService.adminState(player, data));
    }

    /** Opens the journey page: today's missions and the season track. */
    public static void openJourney(ServerPlayer player, String tab) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        CompoundTag payload = new CompoundTag();
        payload.putString("tab", tab == null ? "TODAY" : tab);
        payload.put("missions", Nbt.saveStrings(net.schwarz.rotasutils.server.DailyService.missionsToday(player, data)));
        payload.putString("board", net.schwarz.rotasutils.server.SeasonService.rules(data).daily.boardId);
        openScreen(player, "journey", payload);
    }

    /**
     * Opens the player's house screen on {@code houseId}, or, when blank, on the house they stand in,
     * else the first one they own or belong to.
     */
    public static void openHouse(ServerPlayer player, String houseId) {
        openScreen(player, "house", net.schwarz.rotasutils.house.HousePlayerService.view(player,
                net.schwarz.rotasutils.data.RotasData.get(player.server), houseId));
    }

    /** Opens the Rune Altar on the weapon the player is holding. */
    public static void openRunes(ServerPlayer player) {
        openScreen(player, "runes", runeState(player));
    }

    /**
     * What the altar screen shows: the held weapon, each rune slot (open, locked, its rune and price),
     * the runes the player carries and their gold. The server checks all of it again on inscribing.
     */
    public static CompoundTag runeState(ServerPlayer player) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        var season = net.schwarz.rotasutils.server.SeasonService.rules(data);
        var rules = season.runes;
        CompoundTag payload = new CompoundTag();
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        payload.put("item", held.save(new CompoundTag()));
        String refusal = net.schwarz.rotasutils.server.RuneService.refusal(player, data);
        int open = net.schwarz.rotasutils.server.RuneService.slots(rules, held);
        payload.putString("refusal", refusal);
        // A weapon that is merely not refined far enough still shows its locked slots; anything else
        // (no altar, runes off, not a weapon) leaves only the reason on screen.
        boolean underRefined = rules.enabled && open == 0
                && net.schwarz.rotasutils.item.ItemRefine.categoryOf(held) == net.schwarz.rotasutils.item.ItemRefine.Category.WEAPON
                && net.schwarz.rotasutils.server.StationService.near(player,
                        net.schwarz.rotasutils.registry.RotasRegistry.RUNE_ALTAR.get());
        String station = net.schwarz.rotasutils.server.RuneService.stationRefusal(player, data);
        payload.putString("station_refusal", station);
        payload.putBoolean("station", station.isEmpty());
        payload.putBoolean("blocked", !refusal.isEmpty() && !underRefined);
        net.minecraft.nbt.ListTag slots = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < rules.slotLevels.length; i++) {
            CompoundTag slot = new CompoundTag();
            slot.putInt("unlock", rules.slotLevels[i]);
            slot.putBoolean("open", i < open);
            slot.putLong("cost", rules.goldPerSlot[i]);
            var rune = net.schwarz.rotasutils.item.ItemRunes.get(held, i);
            slot.putString("rune", rune == null ? "" : rune.id());
            slot.putInt("tier", net.schwarz.rotasutils.item.ItemRunes.tier(held, i));
            slots.add(slot);
        }
        payload.put("slots", slots);
        for (var rune : net.schwarz.rotasutils.core.RuneType.values()) {
            for (int tier = 1; tier <= net.schwarz.rotasutils.item.ItemRunes.MAX_TIER; tier++) {
                payload.putInt("have_" + rune.id() + "_" + tier,
                        net.schwarz.rotasutils.server.RuneService.carried(player, rune, tier));
            }
        }
        payload.putLong("gold", data.progress(player.getUUID()).rpg().currency(season.currency));
        return payload;
    }

    /** Opens the socket screen for the card the player is holding. */
    public static void openSockets(ServerPlayer player) {
        openScreen(player, "sockets", socketState(player));
    }

    /**
     * The player's own socketable gear, for the card screen: which items have sockets, what is already
     * in them, and whether the held card fits. The server checks all of it again before it writes.
     */
    public static CompoundTag socketState(ServerPlayer player) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        var rules = net.schwarz.rotasutils.server.SeasonService.rules(data).cards;
        CompoundTag payload = new CompoundTag();
        String cardId = net.schwarz.rotasutils.item.CardItem.idOf(player.getMainHandItem());
        payload.putString("card", cardId);
        payload.putString("card_name", net.schwarz.rotasutils.core.CardIndex.name(cardId));
        payload.putInt("max_sockets", rules == null ? 0 : rules.maxSockets);
        payload.putBoolean("punching", player.getMainHandItem().is(
                net.schwarz.rotasutils.registry.RotasRegistry.SOCKET_PUNCH.get()));

        net.minecraft.nbt.ListTag targets = new net.minecraft.nbt.ListTag();
        var items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            appendTarget(targets, data, items.get(slot), slot, cardId);
        }
        appendTarget(targets, data, player.getOffhandItem(), -1, cardId);
        payload.put("targets", targets);
        return payload;
    }

    private static void appendTarget(net.minecraft.nbt.ListTag targets,
                                     net.schwarz.rotasutils.data.RotasData data,
                                     net.minecraft.world.item.ItemStack stack, int slot, String cardId) {
        if (stack.isEmpty() || !net.schwarz.rotasutils.item.ItemRefine.categoryOf(stack).refinable()) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putInt("slot", slot);
        entry.put("item", stack.save(new CompoundTag()));
        entry.putInt("sockets", net.schwarz.rotasutils.item.ItemSockets.count(stack));
        entry.put("cards", net.schwarz.rotasutils.util.Nbt.saveStrings(
                net.schwarz.rotasutils.item.ItemSockets.cards(stack).stream()
                        .map(net.schwarz.rotasutils.core.CardIndex::name).toList()));
        entry.putString("refusal", cardId.isBlank() ? ""
                : net.schwarz.rotasutils.server.CardService.cannotInsert(data, stack, cardId));
        targets.add(entry);
    }

    /** The quote for the held item, shaped for the refinement screen. */
    public static CompoundTag refineState(ServerPlayer player) {
        var data = net.schwarz.rotasutils.data.RotasData.get(player.server);
        var rules = net.schwarz.rotasutils.server.SeasonService.rules(data).refine;
        CompoundTag payload = new CompoundTag();
        for (boolean enriched : new boolean[]{false, true}) {
            var quote = net.schwarz.rotasutils.server.RefineService.quote(player, data,
                    new net.schwarz.rotasutils.server.RefineService.Options(enriched, false, false, false));
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("possible", quote.possible());
            tag.putString("category", quote.category().key());
            tag.putInt("level", quote.level());
            tag.putInt("target", quote.target());
            tag.putDouble("chance", quote.chance());
            tag.putLong("cost", quote.cost());
            tag.putString("ore", quote.ore());
            tag.putString("message", quote.message());
            payload.put(enriched ? "enriched" : "plain", tag);
        }
        payload.merge(net.schwarz.rotasutils.server.ForgeSessions.view(player));
        payload.put("item", player.getMainHandItem().save(new CompoundTag()));
        payload.putInt("max_level", rules == null ? 0 : rules.maxLevel);
        payload.putInt("safe_level", rules == null ? 0 : rules.safeLevel);
        payload.putString("on_fail", rules == null ? "DOWNGRADE" : rules.onFail);
        payload.putDouble("blessing_bonus", rules == null ? 0 : rules.blessingBonus);
        payload.putLong("gold", data.progress(player.getUUID()).rpg()
                .currency(net.schwarz.rotasutils.server.SeasonService.rules(data).currency));
        for (var entry : java.util.Map.of(
                "oridecon", net.schwarz.rotasutils.registry.RotasRegistry.ORIDECON.get(),
                "elunium", net.schwarz.rotasutils.registry.RotasRegistry.ELUNIUM.get(),
                "enriched_oridecon", net.schwarz.rotasutils.registry.RotasRegistry.ENRICHED_ORIDECON.get(),
                "enriched_elunium", net.schwarz.rotasutils.registry.RotasRegistry.ENRICHED_ELUNIUM.get(),
                "protection_scroll", net.schwarz.rotasutils.registry.RotasRegistry.PROTECTION_SCROLL.get(),
                "blessing_scroll", net.schwarz.rotasutils.registry.RotasRegistry.BLESSING_SCROLL.get(),
                "certificate_scroll", net.schwarz.rotasutils.registry.RotasRegistry.CERTIFICATE_SCROLL.get()).entrySet()) {
            int held = 0;
            for (var stack : player.getInventory().items) {
                if (stack.is(entry.getValue())) {
                    held += stack.getCount();
                }
            }
            payload.putInt("have_" + entry.getKey(), held);
        }
        return payload;
    }

    public static void openAdminMenu(ServerPlayer player) {
        openScreen(player, "admin_menu", new CompoundTag());
    }

    /** Opens the admin menu on one section, e.g. "NPCS". */
    public static void openAdminMenu(ServerPlayer player, String section) {
        CompoundTag payload = new CompoundTag();
        payload.putString("section", section);
        openScreen(player, "admin_menu", payload);
    }

    public static void openBoardBrowser(ServerPlayer player, BoardConfig board) {
        openBoardBrowser(player, board, "");
    }

    public static void openBoardBrowser(ServerPlayer player, BoardConfig board, String npcId) {
        RotasData data = RotasData.get(player.server);
        CompoundTag payload = new CompoundTag();
        payload.putString("board_id", board.id());
        if (npcId != null && !npcId.isBlank()) payload.putString("npc", npcId);
        // RANDOM boards roll a new selection per open; the accept check then reuses that pick.
        BoardService.reroll(player, board);
        payload.put("visible", Nbt.saveStrings(BoardService.visibleQuests(player, data, board)));
        openScreen(player, "board_browser", payload);
    }

    public static void openNpcConfig(ServerPlayer player, net.schwarz.rotasutils.npc.NpcDef npc) {
        CompoundTag payload = new CompoundTag();
        payload.putString("npc_id", npc.id());
        openScreen(player, "npc_config", payload);
    }

    public static void openBoardConfig(ServerPlayer player, BoardConfig board) {
        CompoundTag payload = new CompoundTag();
        payload.putString("board_id", board.id());
        openScreen(player, "board_config", payload);
    }

    /** Opens one level zone for editing. */
    public static void openZoneEdit(ServerPlayer player, String zoneId) {
        CompoundTag payload = new CompoundTag();
        payload.putString("zone", zoneId);
        openScreen(player, "zone_edit", payload);
    }

    public static void openZoneManager(ServerPlayer player) {
        openScreen(player, "zone_manager", new CompoundTag());
    }

    public static void openQuestCreator(ServerPlayer player, String questId) {
        CompoundTag payload = new CompoundTag();
        payload.putString("quest_id", questId);
        openScreen(player, "quest_creator", payload);
    }

    public static void openSkillEditor(ServerPlayer player, String categoryId) {
        CompoundTag payload = new CompoundTag();
        payload.putString("category_id", categoryId);
        openScreen(player, "skill_editor", payload);
    }

    /**
     * Fake players (other mods' automation, and this project's server smoke) have no connection.
     * Sending to one would throw inside the network layer, so every push checks first.
     */
    private static boolean connected(ServerPlayer player) {
        return player != null && player.connection != null;
    }

    private static HouseAdminService.Selection houseSelection(ContentActor actor) {
        return houseSelection(actor.mainHand(), actor.offHand());
    }

    private static HouseAdminService.Selection houseSelection(net.minecraft.world.item.ItemStack mainHand,
                                                              net.minecraft.world.item.ItemStack offHand) {
        net.minecraft.world.item.ItemStack wand = mainHand;
        if (!(wand.getItem() instanceof HouseWandItem)) {
            wand = offHand;
        }
        if (!(wand.getItem() instanceof HouseWandItem)) {
            return null;
        }
        return new HouseAdminService.Selection(HouseWandItem.dimension(wand), HouseWandItem.first(wand),
                HouseWandItem.second(wand));
    }

    /** Unrolls the sealed scroll: a result or gathering is sealed to a sub-role the player lacks. */
    public static void sealedCraft(ServerPlayer player, String activity, net.minecraft.world.item.ItemStack stack,
                                   String jobName, int level) {
        if (!connected(player)) { return; }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(activity, 16);
        buf.writeItem(stack.isEmpty() ? stack : stack.copyWithCount(1));
        buf.writeUtf(jobName.length() > 128 ? jobName.substring(0, 128) : jobName, 128);
        buf.writeVarInt(level);
        NetworkManager.sendToPlayer(player, SEALED_CRAFT, buf);
    }

    public static void feedback(ServerPlayer player, boolean success, String message) {
        if (!connected(player)) { return; }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBoolean(success);
        buf.writeUtf(message, 512);
        NetworkManager.sendToPlayer(player, FEEDBACK, buf);
    }

    public static void sendPickResult(ServerPlayer player, String screenKey, String fieldKey, String value) {
        if (!connected(player)) { return; }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(screenKey, 64);
        buf.writeUtf(fieldKey, 128);
        buf.writeUtf(value, 256);
        NetworkManager.sendToPlayer(player, PICK_RESULT, buf);
    }

    public static void sendValidation(ServerPlayer player, List<Validation.Issue> issues) {
        if (!connected(player)) { return; }
        CompoundTag tag = new CompoundTag();
        tag.put("issues", Nbt.saveList(issues, Validation.Issue::save));
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(tag);
        NetworkManager.sendToPlayer(player, VALIDATION, buf);
    }
}
