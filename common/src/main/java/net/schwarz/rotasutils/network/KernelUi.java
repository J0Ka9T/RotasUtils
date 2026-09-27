package net.schwarz.rotasutils.network;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ItemDefinitions;
import net.schwarz.rotasutils.core.MerchantDefinitions;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ItemFactory;
import net.schwarz.rotasutils.server.LootService;
import net.schwarz.rotasutils.server.MerchantService;
import net.schwarz.rotasutils.server.QuestKernelService;
import net.schwarz.rotasutils.server.RotasPermissions;
import net.schwarz.rotasutils.server.RpgKernel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server side of the kernel control screens.
 *
 * <p>The client receives a bounded description of the live content plus the player's own state, and
 * sends back only an operation name and the ID it selected. Every operation is re-checked here
 * against the same services and permissions the commands use, so a modified client gains nothing by
 * sending a packet the screen would not have offered.</p>
 */
public final class KernelUi {
    /** Per-kind entry caps. A pack larger than this is listed up to the cap and flagged truncated. */
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_TRADES = 32;
    private static final double REACH = 24.0;
    /** Section names the console understands; the command suggests these. */
    public static final List<String> SECTIONS = List.of("character", "quests", "merchants",
            "items", "loot", "monsters", "bosses");

    private KernelUi() {
    }

    // Snapshot -------------------------------------------------------------

    public static CompoundTag snapshot(ServerPlayer player) {
        RotasData data = RotasData.get(player.server);
        CompoundTag tag = new CompoundTag();
        RpgKernel kernel = data.kernel();
        if (kernel == null) {
            tag.putBoolean("ready", false);
            return tag;
        }
        tag.putBoolean("ready", true);
        tag.putString("hash", kernel.content().hash());
        tag.putLong("revision", kernel.revision());
        tag.put("catalog", catalog(kernel.content(),
                RotasPermissions.allowed(source(player), RotasPermissions.Capability.VIEW)));
        tag.put("state", state(player, data, kernel));
        return tag;
    }

    /** Describes live content for the screens. Public so the round trip can be tested directly. */
    public static CompoundTag catalog(net.schwarz.rotasutils.core.ContentRegistry.Snapshot content) {
        return catalog(content, true);
    }

    /**
     * Describes live content for the screens.
     *
     * <p>Player screens (quests, merchants, mail) share this snapshot with the admin screens, so the
     * authoring side of the catalogue is filtered out unless the viewer may see it. Monster profiles,
     * boss internals and loot tables are how an encounter is built; handing them to every client that
     * opens a quest board spoils the content and leaks the raw definition JSON.</p>
     */
    public static CompoundTag catalog(net.schwarz.rotasutils.core.ContentRegistry.Snapshot content, boolean admin) {
        CompoundTag catalog = new CompoundTag();
        boolean[] truncated = {false};
        catalog.putBoolean("admin", admin);

        ListTag monsters = new ListTag();
        if (admin) {
            content.monsters().profiles().forEach((id, profile) -> {
                if (monsters.size() >= MAX_ENTRIES) { truncated[0] = true; return; }
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.value());
                entry.putString("name", profile.name());
                entry.putInt("level_min", profile.level().min());
                entry.putInt("level_max", profile.level().max());
                entry.putBoolean("manual_only", profile.manualOnly());
                entry.putInt("tiers", profile.tiers().size());
                entry.putInt("affixes", profile.affixes().size());
                entry.putString("boss", profile.boss() == null ? "" : profile.boss().value());
                entry.putString("loot", profile.loot() == null ? "" : profile.loot().value());
                // The body lets the Mob Setup screen reopen a profile for editing.
                var definition = content.definitions().get(id);
                entry.putString("body", definition == null ? "{}" : definition.source().document().getAsJsonObject("body").toString());
                monsters.add(entry);
            });
        }
        catalog.put("monsters", monsters);

        ListTag bosses = new ListTag();
        if (admin) {
            content.monsters().bosses().forEach((id, boss) -> {
                if (bosses.size() >= MAX_ENTRIES) { truncated[0] = true; return; }
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.value());
                entry.putString("label", boss.label());
                entry.putInt("phases", boss.phases().size());
                entry.putInt("arena", boss.arenaRadius());
                entry.putInt("enrage", boss.enrageTicks() / 20);
                entry.putDouble("minimum_share", boss.minimumShare());
                bosses.add(entry);
            });
        }
        catalog.put("bosses", bosses);

        ListTag items = new ListTag();
        content.items().profiles().forEach((id, profile) -> {
            if (items.size() >= MAX_ENTRIES) { truncated[0] = true; return; }
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.value());
            entry.putString("item", profile.item());
            entry.putInt("level_min", profile.minLevel());
            entry.putInt("level_max", profile.maxLevel());
            entry.putInt("modifiers", profile.modifiers().size());
            entry.putInt("min_player_level", profile.requirement().minLevel());
            entry.putString("slot", profile.slot().isEmpty() ? profile.curiosSlot() : profile.slot());
            entry.putString("set", profile.set() == null ? "" : profile.set().value());
            items.add(entry);
        });
        catalog.put("items", items);

        ListTag loot = new ListTag();
        if (admin) {
            content.items().loot().forEach((id, table) -> {
                if (loot.size() >= MAX_ENTRIES) { truncated[0] = true; return; }
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.value());
                entry.putInt("rolls_min", table.minRolls());
                entry.putInt("rolls_max", table.maxRolls());
                entry.putInt("entries", table.entries().size());
                loot.add(entry);
            });
        }
        catalog.put("loot", loot);

        ListTag quests = new ListTag();
        content.quests().forEach((id, quest) -> {
            if (quests.size() >= MAX_ENTRIES) { truncated[0] = true; return; }
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.value());
            entry.putString("label", quest.label());
            entry.putInt("stages", quest.stages().size());
            entry.putString("reset", quest.reset().name());
            entry.putInt("bounty_limit", quest.bountyLimit());
            ListTag stages = new ListTag();
            quest.stages().forEach(stage -> {
                CompoundTag stageTag = new CompoundTag();
                stageTag.putString("label", stage.label());
                ListTag objectives = new ListTag();
                stage.objectives().forEach(objective -> {
                    CompoundTag objectiveTag = new CompoundTag();
                    objectiveTag.putString("key", objective.key());
                    objectiveTag.putString("label", objective.label().isEmpty()
                            ? objective.event().value() : objective.label());
                    objectiveTag.putInt("count", objective.count());
                    objectives.add(objectiveTag);
                });
                stageTag.put("objectives", objectives);
                stages.add(stageTag);
            });
            entry.put("stage_list", stages);
            quests.add(entry);
        });
        catalog.put("quests", quests);

        ListTag merchants = new ListTag();
        content.merchants().forEach((id, merchant) -> {
            if (merchants.size() >= MAX_ENTRIES) { truncated[0] = true; return; }
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.value());
            entry.putString("label", merchant.label());
            ListTag trades = new ListTag();
            for (MerchantDefinitions.Trade trade : merchant.trades()) {
                if (trades.size() >= MAX_TRADES) { truncated[0] = true; break; }
                CompoundTag tradeTag = new CompoundTag();
                tradeTag.putString("key", trade.key());
                tradeTag.putString("label", trade.label());
                tradeTag.putString("result", trade.profile() != null ? trade.profile().value() : trade.item());
                tradeTag.putInt("result_count", trade.count());
                tradeTag.putInt("item_level", trade.itemLevel());
                tradeTag.putInt("stock", trade.stock());
                tradeTag.putInt("per_player_limit", trade.perPlayerLimit());
                tradeTag.putString("cost", costText(trade));
                CompoundTag costs = new CompoundTag();
                for (var cost : trade.costs()) {
                    String key = (cost.currency() != null ? "currency:" + cost.currency().value() : "item:" + cost.item());
                    costs.putLong(key, Math.addExact(costs.getLong(key), cost.amount()));
                }
                tradeTag.put("costs", costs);
                trades.add(tradeTag);
            }
            entry.put("trades", trades);
            merchants.add(entry);
        });
        catalog.put("merchants", merchants);
        catalog.putBoolean("truncated", truncated[0]);
        return catalog;
    }

    private static String costText(MerchantDefinitions.Trade trade) {
        List<String> parts = new ArrayList<>();
        for (MerchantDefinitions.Cost cost : trade.costs()) {
            parts.add(cost.amount() + " " + shortName(cost.currency() != null ? cost.currency().value() : cost.item()));
        }
        return String.join(" + ", parts);
    }

    /** Drops the namespace and folder from an ID so a row can show the part that differs. */
    public static String shortName(String id) {
        int slash = id.lastIndexOf('/');
        if (slash >= 0) { return id.substring(slash + 1); }
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static CompoundTag state(ServerPlayer player, RotasData data, RpgKernel kernel) {
        CompoundTag state = new CompoundTag();
        var progress = data.progress(player.getUUID());
        state.putInt("level", progress.level());
        state.putLong("xp", progress.xp());
        state.putLong("total_xp", progress.totalXp());
        state.putInt("stat_points", progress.rpg().statPoints());
        state.putInt("mailbox", progress.mailbox().size());
        state.putBoolean("can_edit", RotasPermissions.allowed(source(player), RotasPermissions.Capability.EDIT));

        CompoundTag wallet = new CompoundTag();
        progress.rpg().save().getCompound("currencies").getAllKeys()
                .forEach(id -> wallet.putLong(id, progress.rpg().currency(id)));
        state.put("wallet", wallet);

        QuestKernelService quests = kernel.quests();
        List<ContentId> available = quests.available(player);
        ListTag questState = new ListTag();
        kernel.content().quests().forEach((id, quest) -> {
            int stage = quests.stage(player, id);
            long completed = quests.completedAt(player, id);
            boolean claimable = stage == -1 && completed > 0;
            if (stage < 0 && !claimable && !available.contains(id)) { return; }
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.value());
            entry.putInt("stage", stage);
            entry.putBoolean("available", available.contains(id));
            entry.putBoolean("claimable", claimable);
            entry.putLong("completed", completed);
            if (stage >= 0 && stage < quest.stages().size()) {
                CompoundTag counters = new CompoundTag();
                quest.stages().get(stage).objectives()
                        .forEach(objective -> counters.putInt(objective.key(), quests.progress(player, id, objective.key())));
                entry.put("objectives", counters);
            }
            questState.add(entry);
        });
        state.put("quests", questState);

        MerchantService merchants = kernel.merchants();
        ListTag merchantState = new ListTag();
        kernel.content().merchants().forEach((id, merchant) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.value());
            CompoundTag remaining = new CompoundTag();
            CompoundTag purchased = new CompoundTag();
            CompoundTag availableTrades = new CompoundTag();
            merchant.trades().forEach(trade -> {
                remaining.putInt(trade.key(), merchants.remaining(id, trade));
                purchased.putInt(trade.key(), merchants.purchased(player, id, trade));
                availableTrades.putBoolean(trade.key(), merchants.available(player, id, trade));
            });
            entry.put("remaining", remaining);
            entry.put("purchased", purchased);
            entry.put("available", availableTrades);
            merchantState.add(entry);
        });
        state.put("merchants", merchantState);
        return state;
    }

    private static CommandSourceStack source(ServerPlayer player) {
        return player.createCommandSourceStack();
    }

    // Actions --------------------------------------------------------------

    /** Handles one screen operation. Returns false when the action name is not ours. */
    public static boolean handle(ServerPlayer player, String action, CompoundTag payload) {
        RotasData data = RotasData.get(player.server);
        if (data.kernel() == null) {
            if (!action.startsWith("kernel_")) { return false; }
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.loading"));
            return true;
        }
        switch (action) {
            case "kernel_refresh" -> sync(player);
            case "kernel_quest" -> quest(player, data, payload);
            case "kernel_shop" -> shop(player, data, payload);
            case "kernel_mail" -> {
                int delivered = LootService.recover(player);
                int left = data.progress(player.getUUID()).mailbox().size();
                RotasNetwork.feedback(player, delivered > 0 || left == 0,
                        delivered == 0 && left > 0
                                ? ThaiText.t("rotasutils.msg.kernel.mail_no_room", left)
                                : ThaiText.t("rotasutils.msg.kernel.mail_collected", delivered, left));
                sync(player);
            }
            case "kernel_item_give" -> itemGive(player, data, payload);
            case "kernel_loot_preview" -> lootPreview(player, data, payload);
            case "kernel_monster" -> monster(player, data, payload);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void sync(ServerPlayer player) {
        RotasNetwork.syncKernelUi(player, snapshot(player));
    }

    private static void quest(ServerPlayer player, RotasData data, CompoundTag payload) {
        ContentId id = id(payload.getString("quest"));
        if (id == null) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.bad_quest_id")); return; }
        QuestKernelService service = data.kernel().quests();
        try {
            QuestKernelService.Result result = switch (payload.getString("op")) {
                case "accept" -> service.accept(player, id);
                case "abandon" -> service.abandon(player, id);
                case "claim" -> service.claim(player, id);
                default -> null;
            };
            if (result == null) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.unknown_quest_op")); return; }
            RotasNetwork.feedback(player, switch (result) {
                case ACCEPTED, CLAIMED, ADVANCED, COMPLETED -> true;
                default -> false;
            }, questMessage(result));
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.quest_refused", failure.getMessage()));
        }
        sync(player);
    }

    private static String questMessage(QuestKernelService.Result result) {
        return switch (result) {
            case ACCEPTED -> ThaiText.t("rotasutils.msg.kernel.quest.accepted");
            case CLAIMED -> ThaiText.t("rotasutils.msg.kernel.quest.claimed");
            case COMPLETED -> ThaiText.t("rotasutils.msg.kernel.quest.completed");
            case ADVANCED -> ThaiText.t("rotasutils.msg.kernel.quest.advanced");
            case ALREADY_ACTIVE -> ThaiText.t("rotasutils.msg.kernel.quest.already_active");
            case NOT_ACTIVE -> ThaiText.t("rotasutils.msg.kernel.quest.not_active");
            case LIMIT_REACHED -> ThaiText.t("rotasutils.msg.kernel.quest.limit");
            case UNAVAILABLE -> ThaiText.t("rotasutils.msg.kernel.quest.unavailable");
        };
    }

    private static void shop(ServerPlayer player, RotasData data, CompoundTag payload) {
        ContentId id = id(payload.getString("merchant"));
        String trade = payload.getString("trade");
        int count = payload.getInt("count");
        if (count < 1 || count > 64) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.quantity")); return; }
        if (id == null || trade.isEmpty()) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.bad_trade")); return; }
        try {
            MerchantService.Result result = data.kernel().merchants().buy(player, id, trade, count);
            RotasNetwork.feedback(player, result == MerchantService.Result.TRADED, switch (result) {
                case TRADED -> ThaiText.t("rotasutils.msg.kernel.trade.traded");
                case INSUFFICIENT_FUNDS -> ThaiText.t("rotasutils.msg.kernel.trade.funds");
                case MISSING_ITEMS -> ThaiText.t("rotasutils.msg.kernel.trade.missing");
                case OUT_OF_STOCK -> ThaiText.t("rotasutils.msg.kernel.trade.stock");
                case LIMIT_REACHED -> ThaiText.t("rotasutils.msg.kernel.trade.limit");
                case UNAVAILABLE -> ThaiText.t("rotasutils.msg.kernel.trade.unavailable");
                case UNKNOWN_TRADE -> ThaiText.t("rotasutils.msg.kernel.trade.unknown");
            });
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.trade_refused", failure.getMessage()));
        }
        sync(player);
    }

    private static void itemGive(ServerPlayer player, RotasData data, CompoundTag payload) {
        if (!RotasPermissions.allowed(source(player), RotasPermissions.Capability.EDIT)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.edit_perm"));
            return;
        }
        ContentId id = id(payload.getString("profile"));
        int level = Math.max(1, Math.min(10000, payload.getInt("level")));
        if (id == null) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.bad_item_id")); return; }
        try {
            var stack = ItemFactory.create(data.kernel().content().items(), id, level, 1,
                    LootService.random("ui|" + id + "|" + player.getUUID() + "|" + player.server.getTickCount()));
            int stored = LootService.deliver(player, List.of(stack));
            data.audit(java.time.Instant.now() + " actor=" + player.getGameProfile().getName()
                    + " action=ui_item_give profile=" + id + " level=" + level);
            RotasNetwork.feedback(player, true, ThaiText.t(stored > 0 ? "rotasutils.msg.kernel.item_waiting"
                    : "rotasutils.msg.kernel.item_added", stack.getHoverName().getString()));
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.item_refused", failure.getMessage()));
        }
        sync(player);
    }

    private static void lootPreview(ServerPlayer player, RotasData data, CompoundTag payload) {
        if (!RotasPermissions.allowed(source(player), RotasPermissions.Capability.VIEW)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.preview_perm"));
            return;
        }
        ContentId id = id(payload.getString("table"));
        int level = Math.max(1, Math.min(10000, payload.getInt("level")));
        double multiplier = payload.contains("multiplier") ? payload.getDouble("multiplier") : 1.0;
        if (id == null) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.bad_table_id")); return; }
        try {
            List<String> lines = LootService.preview(data.kernel().content().items(), id,
                    "preview|" + id + "|" + level + "|" + multiplier, level, multiplier);
            CompoundTag result = new CompoundTag();
            result.putString("table", id.value());
            result.put("lines", net.schwarz.rotasutils.util.Nbt.saveStrings(lines));
            RotasNetwork.syncKernelPreview(player, result);
            RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.kernel.rolled", lines.size()));
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.preview_refused", failure.getMessage()));
        }
    }

    private static void monster(ServerPlayer player, RotasData data, CompoundTag payload) {
        String op = payload.getString("op");
        boolean mutating = !op.equals("inspect");
        RotasPermissions.Capability needed = mutating
                ? RotasPermissions.Capability.EDIT : RotasPermissions.Capability.VIEW;
        if (!RotasPermissions.allowed(source(player), needed)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.op_perm"));
            return;
        }
        Mob target = lookedAt(player);
        if (target == null) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.look_at_mob", (int) REACH));
            return;
        }
        var monsters = data.kernel().monsters();
        try {
            monsters.join(target, "COMMAND", true);
            switch (op) {
                case "assign" -> {
                    ContentId id = id(payload.getString("profile"));
                    if (id == null) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.bad_profile_id")); return; }
                    Integer level = payload.contains("level") && payload.getInt("level") > 0 ? payload.getInt("level") : null;
                    monsters.assign(target, id, level, "COMMAND");
                }
                case "clear" -> monsters.clear(target);
                default -> { }
            }
            var state = monsters.peek(target);
            String name = target.getName().getString();
            RotasNetwork.feedback(player, true, state == null
                    ? ThaiText.t("rotasutils.msg.kernel.no_rpg_data", name)
                    : ThaiText.t("rotasutils.msg.kernel.monster_summary", name, shortName(state.profile().value()),
                    state.level(), shortName(state.tier().value()), state.affixes().size()));
            if (mutating) {
                data.audit(java.time.Instant.now() + " actor=" + player.getGameProfile().getName()
                        + " action=ui_monster_" + op + " target=" + target.getUUID());
            }
        } catch (RuntimeException failure) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.kernel.monster_refused", failure.getMessage()));
        }
    }

    /** The mob the player is looking at, so the screen never asks anyone to type a UUID. */
    public static Mob lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        Mob best = null;
        double bestDistance = REACH * REACH;
        for (var entity : player.level().getEntities(player, box, candidate -> candidate instanceof Mob && candidate.isAlive())) {
            var clip = entity.getBoundingBox().inflate(0.3).clip(eye, end);
            if (clip.isEmpty()) { continue; }
            double distance = eye.distanceToSqr(clip.get());
            if (distance < bestDistance) { bestDistance = distance; best = (Mob) entity; }
        }
        return best;
    }

    private static ContentId id(String value) {
        try { return value == null || value.isEmpty() ? null : new ContentId(value); }
        catch (IllegalArgumentException malformed) { return null; }
    }

    /** Item profile display name for the client; exposed so both sides format IDs identically. */
    public static String describe(ItemDefinitions.Profile profile) {
        return shortName(profile.id().value()) + " (" + shortName(profile.item()) + ")";
    }

    /** Quest reset wording shared by the screens. */
    public static String resetText(QuestDefinitions.Reset reset, Map<String, String> ignored) {
        return switch (reset) {
            case NONE -> "one time";
            case DAILY -> "daily";
            case WEEKLY -> "weekly";
            case COOLDOWN -> "on cooldown";
        };
    }
}
