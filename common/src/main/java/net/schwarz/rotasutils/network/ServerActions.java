package net.schwarz.rotasutils.network;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.compat.CuriosServerCompat;
import net.schwarz.rotasutils.compat.PuffishSkillsCompat;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.item.HouseWandItem;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.ObjectiveEngine;
import net.schwarz.rotasutils.server.PartyService;
import net.schwarz.rotasutils.server.ProgressService;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.server.RewardService;
import net.schwarz.rotasutils.server.SeasonService;
import net.schwarz.rotasutils.server.SkillService;
import net.schwarz.rotasutils.server.TitleService;
import net.schwarz.rotasutils.server.Validation;
import net.schwarz.rotasutils.server.WorldPicker;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillNode;
import net.schwarz.rotasutils.util.Ids;

import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.UUID;

/**
 * The single entry point for every client request.
 *
 * <p>Admin-only actions are gated here, not on the client, so a modified client
 * cannot reach them by sending the packet directly.
 */
public final class ServerActions {
    /** Upper bound on stored menu preferences per player. */
    private static final int MAX_PREFERENCES = 32;

    private ServerActions() {
    }

    public static void handle(ServerPlayer player, String action, CompoundTag payload) {
        RotasData data = RotasData.get(player.server);
        boolean admin = BoardService.isAdmin(player, data);

        // Kernel screens carry their own permission checks per operation.
        if (KernelUi.handle(player, action, payload)) {
            return;
        }

        if (AdminWorldActions.ACTIONS.contains(action)) {
            if (admin) {
                AdminWorldActions.handle(player, data, action, payload);
            }
            return;
        }
        if (AdminContentActions.ACTIONS.contains(action)) {
            if (admin) {
                AdminContentActions.handle(player, data, action, payload);
            }
            return;
        }

        switch (action) {
            case "npc_response" -> net.schwarz.rotasutils.server.NpcConversations.respond(player, data, payload);
            case "npc_reopen" -> withNpc(player, data, payload, npc -> net.schwarz.rotasutils.server.NpcService.openDialogue(player, data, npc));
            // Player -------------------------------------------------------
            case "request_sync" -> {
                RotasNetwork.syncContent(player);
                RotasNetwork.syncProgress(player);
                RotasNetwork.syncParty(player);
            }
            case "wallet_withdraw" -> net.schwarz.rotasutils.server.GoldCoinService.withdraw(player, payload.getInt("amount"));
            // Sneak + left-click with the Disintegrator: the Cero Metralleta barrage, re-validated here.
            case "exo_cero" -> net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity.unleash(player);
            case "curio_click" -> CuriosServerCompat.click(player,
                    payload.getString("slot_type"), payload.getInt("slot_index"), payload.getInt("button"));
            case "open_menu" -> RotasNetwork.openMainMenu(player);
            case "open_refine" -> RotasNetwork.openRefine(player);
            case "open_journey" -> RotasNetwork.openJourney(player, payload.getString("tab"));
            case "open_bestiary" -> RotasNetwork.openBestiary(player, payload.getString("entity"));
            case "open_salvage" -> RotasNetwork.openSalvage(player);
            case "salvage" -> {
                net.schwarz.rotasutils.server.SalvageService.salvage(player, data, payload.getInt("slot"));
                RotasNetwork.openSalvage(player);
            }
            case "daily_claim" -> {
                var result = net.schwarz.rotasutils.server.DailyService.claim(player, data, payload.getInt("tier"));
                RotasNetwork.feedback(player, result.success(), result.message());
                if (result.success()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message())
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
            case "season_claim" -> {
                var result = net.schwarz.rotasutils.server.SeasonTrackService.claim(player, data, payload.getInt("tier"));
                RotasNetwork.feedback(player, result.success(), result.message());
                if (result.success()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message())
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                }
            }
            // Event catalogue (admin) --------------------------------------
            case "event_enable" -> {
                if (admin) {
                    net.schwarz.rotasutils.server.EventService.setEnabled(player.server, data, payload.getBoolean("on"));
                    RotasNetwork.syncContent(player);
                }
            }
            case "event_rule_add" -> {
                if (admin) {
                    boolean done = net.schwarz.rotasutils.server.EventService.add(player.server, data,
                            net.schwarz.rotasutils.event.EventType.byName(payload.getString("type")),
                            payload.getString("filter"));
                    RotasNetwork.feedback(player, done, net.schwarz.rotasutils.util.ThaiText.t(done
                            ? "rotasutils.msg.event.saved" : "rotasutils.msg.event.bad_rule"));
                    RotasNetwork.syncContent(player);
                }
            }
            case "event_rule_remove" -> {
                if (admin) {
                    boolean done = net.schwarz.rotasutils.server.EventService.remove(player.server, data,
                            net.schwarz.rotasutils.event.EventType.byName(payload.getString("type")),
                            payload.getString("filter"));
                    RotasNetwork.feedback(player, done, net.schwarz.rotasutils.util.ThaiText.t(done
                            ? "rotasutils.msg.event.saved" : "rotasutils.msg.event.bad_rule"));
                    RotasNetwork.syncContent(player);
                }
            }
            case "event_rule_edit" -> {
                if (admin) {
                    boolean done = net.schwarz.rotasutils.server.EventService.edit(player.server, data,
                            net.schwarz.rotasutils.event.EventType.byName(payload.getString("type")),
                            payload.getString("filter"), payload.getBoolean("enabled"),
                            payload.getDouble("xp_multiplier"), payload.getLong("xp_flat"),
                            payload.getLong("gold"),
                            net.schwarz.rotasutils.event.EventRules.Announce.byName(payload.getString("announce")),
                            payload.getInt("cooldown"));
                    RotasNetwork.feedback(player, done, net.schwarz.rotasutils.util.ThaiText.t(done
                            ? "rotasutils.msg.event.saved" : "rotasutils.msg.event.bad_rule"));
                    RotasNetwork.syncContent(player);
                }
            }
            // World events (admin) ----------------------------------------
            case "worldevent_open" -> {
                if (admin) {
                    RotasNetwork.openWorldEventAdmin(player);
                }
            }
            case "worldevent_enable" -> {
                if (admin) {
                    boolean on = payload.getBoolean("on");
                    net.schwarz.rotasutils.server.WorldEventService.setEnabled(player.server, data, on);
                    data.audit(player.getGameProfile().getName() + " turned world events " + (on ? "on" : "off"));
                    RotasNetwork.openWorldEventAdmin(player);
                }
            }
            case "worldevent_start" -> {
                if (admin) {
                    String type = payload.getString("type");
                    var types = net.schwarz.rotasutils.server.SeasonService.rules(data).worldEvents.types;
                    var event = type.isEmpty() || types.containsKey(type)
                            ? net.schwarz.rotasutils.server.WorldEventService.start(player.server, data,
                                    type.isEmpty() ? null : type, player, payload.getBoolean("here"))
                            : null;
                    RotasNetwork.feedback(player, event != null, event == null
                            ? net.schwarz.rotasutils.util.ThaiText.t("rotasutils.cmd.worldevent.cannot_start")
                            : net.schwarz.rotasutils.util.ThaiText.t("rotasutils.cmd.worldevent.started", event.id(),
                                    net.schwarz.rotasutils.server.WorldEventService.placeName(data, event)));
                    if (event != null) {
                        data.audit(player.getGameProfile().getName() + " started world event " + event.type());
                    }
                    RotasNetwork.openWorldEventAdmin(player);
                }
            }
            case "worldevent_stop" -> {
                if (admin) {
                    int id = payload.getInt("id");
                    boolean done = net.schwarz.rotasutils.server.WorldEventService.stop(player.server, data, id);
                    RotasNetwork.feedback(player, done, net.schwarz.rotasutils.util.ThaiText.t(done
                            ? "rotasutils.cmd.worldevent.stopped" : "rotasutils.cmd.worldevent.unknown", id));
                    if (done) {
                        data.audit(player.getGameProfile().getName() + " stopped world event " + id);
                    }
                    RotasNetwork.openWorldEventAdmin(player);
                }
            }
            // Drop filter (admin) ------------------------------------------
            case "drop_scan" -> {
                if (admin) {
                    RotasNetwork.openMobDrops(player, payload.getString("entity"));
                }
            }
            case "drop_toggle_entity" -> {
                if (admin) {
                    boolean done = net.schwarz.rotasutils.server.DropFilterService.setForEntity(player.server, data,
                            payload.getString("entity"), payload.getString("item"), payload.getBoolean("drops"));
                    RotasNetwork.feedback(player, done, net.schwarz.rotasutils.util.ThaiText.t(done
                            ? "rotasutils.msg.drop_filter.saved" : "rotasutils.msg.drop_filter.full"));
                    RotasNetwork.openMobDrops(player, payload.getString("entity"));
                }
            }
            case "drop_clear_entity" -> {
                if (admin) {
                    net.schwarz.rotasutils.server.DropFilterService.clearEntity(player.server, data,
                            payload.getString("entity"));
                    RotasNetwork.openMobDrops(player, payload.getString("entity"));
                }
            }
            case "drop_toggle_global" -> {
                if (admin) {
                    boolean done = net.schwarz.rotasutils.server.DropFilterService.setGlobal(player.server, data,
                            payload.getString("item"), payload.getBoolean("drops"));
                    RotasNetwork.feedback(player, done, net.schwarz.rotasutils.util.ThaiText.t(done
                            ? "rotasutils.msg.drop_filter.saved" : "rotasutils.msg.drop_filter.full"));
                    RotasNetwork.syncContent(player);
                }
            }
            case "drop_table_save" -> {
                if (admin) {
                    String error = net.schwarz.rotasutils.server.DropFilterService.saveTable(player.server, data,
                            payload.getString("target"), payload.getString("json"));
                    RotasNetwork.feedback(player, error == null, error == null ? "Drop table saved." : error);
                    for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                        RotasNetwork.syncContent(online);
                    }
                }
            }
            case "drop_filter_enable" -> {
                if (admin) {
                    net.schwarz.rotasutils.server.DropFilterService.setEnabled(player.server, data,
                            payload.getBoolean("on"));
                    RotasNetwork.syncContent(player);
                }
            }
            case "open_sockets" -> RotasNetwork.openSockets(player);
            case "open_runes" -> RotasNetwork.openRunes(player);
            case "open_house" -> RotasNetwork.openHouse(player, payload.getString("house"));
            case "house_settings" -> {
                net.schwarz.rotasutils.house.HousePlayerService.saveSettings(player, data, payload.getString("house"),
                        payload.getCompound("settings"));
                // From the house screen, reopen it on the new values; the admin editor stays where it is.
                if (!"edit".equals(payload.getString("from"))) {
                    RotasNetwork.openHouse(player, payload.getString("house"));
                }
            }
            case "house_settings_open" -> {
                if (net.schwarz.rotasutils.server.BoardService.isAdmin(player, data)) {
                    var house = data.house(payload.getString("house"));
                    if (house != null) {
                        CompoundTag screen = new CompoundTag();
                        screen.putString("house", house.id());
                        screen.putString("name", house.name());
                        CompoundTag own = data.houseSettings(house.id()).save();
                        var base = data.houseConfig().tier(house.tier());
                        own.putLong("tier_deposit", base == null ? 0 : base.deposit());
                        own.putLong("tier_rent", base == null ? 0 : base.maintenance());
                        screen.put("settings", own);
                        RotasNetwork.openScreen(player, "house_quick", screen);
                    }
                }
            }
            case "house_evict" -> {
                if (net.schwarz.rotasutils.server.BoardService.isAdmin(player, data)) {
                    net.schwarz.rotasutils.house.HousePlayerService.evict(player, data, payload.getString("house"));
                }
                RotasNetwork.openHouse(player, payload.getString("house"));
            }
            case "house_rent", "house_pay", "house_buyout", "house_leave", "house_buy_slot",
                 "house_add_member", "house_remove_member" -> {
                String house = payload.getString("house");
                java.util.UUID target = payload.hasUUID("player") ? payload.getUUID("player") : null;
                switch (action) {
                    case "house_rent" -> net.schwarz.rotasutils.house.HousePlayerService.rent(player, data, house);
                    case "house_pay" -> net.schwarz.rotasutils.house.HousePlayerService.pay(player, data, house);
                    case "house_buyout" -> net.schwarz.rotasutils.house.HousePlayerService.buyout(player, data, house);
                    case "house_leave" -> net.schwarz.rotasutils.house.HousePlayerService.leave(player, data, house);
                    case "house_buy_slot" -> net.schwarz.rotasutils.house.HousePlayerService.buySlot(player, data, house);
                    case "house_add_member" -> net.schwarz.rotasutils.house.HousePlayerService.addMember(player, data, house, target);
                    default -> net.schwarz.rotasutils.house.HousePlayerService.removeMember(player, data, house, target);
                }
                // A player who gave the house back no longer sees it; reopen on whatever is left.
                RotasNetwork.openHouse(player, action.equals("house_leave") ? "" : house);
            }
            case "rune_inscribe" -> {
                net.schwarz.rotasutils.server.RuneService.inscribe(player, data, payload.getInt("slot"),
                        payload.getString("rune"));
                RotasNetwork.openRunes(player);
            }
            case "card_insert" -> {
                net.schwarz.rotasutils.server.CardService.insert(player, data, payload.getInt("slot"));
                RotasNetwork.openSockets(player);
            }
            case "card_punch" -> {
                net.schwarz.rotasutils.server.CardService.punch(player, data, payload.getInt("slot"));
                RotasNetwork.openSockets(player);
            }
            case "title_wear" -> {
                boolean worn = net.schwarz.rotasutils.server.TitleService.wear(player, data, payload.getString("title"));
                RotasNetwork.feedback(player, worn, net.schwarz.rotasutils.util.ThaiText.t(worn
                        ? "rotasutils.cmd.title.worn" : "rotasutils.cmd.title.not_earned", payload.getString("title")));
                // Everyone's name plate carries the title, so the whole server refreshes its copy.
                RotasNetwork.syncContent(player.server);
            }
            case "refine_attempt" -> {
                var outcome = net.schwarz.rotasutils.server.RefineService.refine(player, data,
                        new net.schwarz.rotasutils.server.RefineService.Options(
                                payload.getBoolean("enriched"), payload.getBoolean("protection"),
                                payload.getBoolean("blessing"), payload.getBoolean("certificate")));
                if (!outcome.started()) {
                    RotasNetwork.feedback(player, false, outcome.message());
                }
                // The bench always reopens on the fresh numbers, so a broken or levelled item is visible at once.
                RotasNetwork.openRefine(player);
            }
            case "open_skills" -> {
                if (!PuffishSkillsCompat.active(data) || !PuffishSkillsCompat.openScreen(player)) {
                    RotasNetwork.openScreen(player, "skill_tree", new CompoundTag());
                }
            }
            case "accept_quest" -> {
                QuestService.ActionResult result = QuestService.accept(player, data,
                        payload.getString("quest"), payload.getString("board"));
                RotasNetwork.feedback(player, result.success(), result.message());
            }
            case "abandon_quest" -> {
                QuestService.ActionResult result = QuestService.abandon(player, data, payload.getString("quest"));
                RotasNetwork.feedback(player, result.success(), result.message());
            }
            case "turn_in" -> {
                QuestService.ActionResult result = QuestService.turnIn(player, data,
                        payload.getString("quest"), payload.getString("board"));
                RotasNetwork.feedback(player, result.success(), result.message());
            }
            case "deliver_item" -> deliverItem(player, data, payload);
            case "claim_choice" -> claimChoice(player, data, payload);
            case "unlock_skill" -> {
                SkillService.UnlockResult result = SkillService.unlock(player, data, payload.getString("node"));
                RotasNetwork.feedback(player, result.success(), result.message());
                RotasNetwork.syncProgress(player);
            }
            case "reset_skills" -> {
                String category = payload.contains("category") && !payload.getString("category").isEmpty()
                        ? payload.getString("category") : null;
                int refunded = SkillService.reset(player, data, category);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.refunded_skill", refunded));
                RotasNetwork.syncProgress(player);
            }
            case "party_create" -> party(player, PartyService.create(player, data));
            case "party_invite" -> party(player, PartyService.invite(player, data, payload.getString("player")));
            case "party_accept" -> party(player, PartyService.accept(player, data));
            case "party_decline" -> party(player, PartyService.decline(player));
            case "party_leave" -> party(player, PartyService.leave(player, data));
            case "party_kick" -> {
                if (!payload.hasUUID("target")) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.no_member"));
                    return;
                }
                java.util.UUID targetId = payload.getUUID("target");
                party(player, PartyService.kick(player, data, targetId));
                // The removed player is no longer in the roster, so refresh them by hand.
                notifyRemoved(player, targetId);
            }
            case "party_promote" -> party(player, payload.hasUUID("target")
                    ? PartyService.promote(player, data, payload.getUUID("target"))
                    : new PartyService.Result(false, ThaiText.t("rotasutils.msg.sa.no_member")));
            case "party_disband" -> {
                // Capture the roster first: after disbanding there is nobody left to sync.
                java.util.List<java.util.UUID> formerMembers =
                        PartyService.members(data, data.progress(player.getUUID()).partyId());
                party(player, PartyService.disband(player, data));
                for (java.util.UUID memberId : formerMembers) {
                    notifyRemoved(player, memberId);
                }
            }
            case "party_refresh" -> RotasNetwork.syncParty(player);
            // Waystones ----------------------------------------------------
            case "waystone_open" -> net.schwarz.rotasutils.server.WaystoneService.open(player, payload.getString("here"));
            case "waystone_warp" -> net.schwarz.rotasutils.server.WaystoneService.warp(player, payload.getString("id"));
            case "waystone_rename" -> net.schwarz.rotasutils.server.WaystoneService.rename(player,
                    payload.getString("id"), payload.getString("name"));
            case "npc_board" -> withNpc(player, data, payload, npc ->
                    net.schwarz.rotasutils.server.NpcService.openBoard(player, data, npc));
            case "npc_shop" -> withNpc(player, data, payload, npc ->
                    net.schwarz.rotasutils.server.NpcService.openRoleTarget(player, data, npc));
            case "npc_accept" -> withNpc(player, data, payload, npc -> {
                String questId = payload.getString("quest");
                // Only quests this NPC is actually offering right now may be accepted here.
                if (!net.schwarz.rotasutils.server.NpcService.offers(player, data, npc).contains(questId)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.npc_not_offering", npc.name()));
                    return;
                }
                QuestService.ActionResult result = QuestService.accept(player, data, questId,
                        npc.boardId(), true);
                RotasNetwork.feedback(player, result.success(), result.message());
                net.schwarz.rotasutils.server.NpcService.openDialogue(player, data, npc);
            });
            case "npc_turn_in" -> withNpc(player, data, payload, npc -> {
                String questId = payload.getString("quest");
                if (!npc.questIds().contains(questId)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.npc_not_given", npc.name()));
                    return;
                }
                QuestService.ActionResult result = QuestService.turnIn(player, data, questId, npc.boardId());
                RotasNetwork.feedback(player, result.success(), result.message());
                net.schwarz.rotasutils.server.NpcService.openDialogue(player, data, npc);
            });
            case "choose_job" -> {
                net.schwarz.rotasutils.server.JobService.Result result =
                        net.schwarz.rotasutils.server.JobService.choose(player, data, payload.getString("job"),
                                payload.getString("slot").equals("sub") ? net.schwarz.rotasutils.job.JobSlot.SUB : net.schwarz.rotasutils.job.JobSlot.MAIN);
                RotasNetwork.feedback(player, result.success(), result.message());
                RotasNetwork.syncProgress(player);
            }
            case "allocate_stats" -> {
                net.schwarz.rotasutils.server.CharacterStatService.Result result =
                        net.schwarz.rotasutils.server.CharacterStatService.allocate(player, data, payload.getCompound("stats"));
                RotasNetwork.feedback(player, result.success(), result.message());
                RotasNetwork.syncProgress(player);
            }
            case "respec_stats" -> {
                net.schwarz.rotasutils.server.CharacterStatService.Result result =
                        net.schwarz.rotasutils.server.CharacterStatService.respec(player, data);
                RotasNetwork.feedback(player, result.success(), result.message());
                RotasNetwork.syncProgress(player);
            }
            case "horse_open" -> net.schwarz.rotasutils.server.horse.HorseService.open(player,
                    stableNpc(data, payload), payload.getString("tab"), null);
            case "horse_pull" -> {
                net.minecraft.nbt.ListTag results = new net.minecraft.nbt.ListTag();
                var result = net.schwarz.rotasutils.server.horse.HorseService.pull(player, payload.getInt("count"), results);
                RotasNetwork.feedback(player, result.ok(), result.message());
                net.schwarz.rotasutils.server.horse.HorseService.open(player, stableNpc(data, payload), "DRAW", results);
            }
            case "horse_summon" -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.summon(player, payload.getString("id")), "STABLE");
            case "horse_store" -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.store(player, payload.getString("id")), "STABLE");
            case "horse_rename" -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.rename(player, payload.getString("id"), payload.getString("name")), "STABLE");
            case "horse_release" -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.release(player, payload.getString("id")), "STABLE");
            case "horse_buy_slot" -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.buySlot(player), "STABLE");
            case "horse_sell" -> atStable(player, data, payload, () -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.sell(player, payload.getString("id")), "STABLE"));
            case "horse_list" -> atStable(player, data, payload, () -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.list(player, payload.getString("id"), payload.getLong("price")), "MARKET"));
            case "horse_unlist" -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.unlist(player, payload.getString("id")), "MARKET");
            case "horse_buy" -> atStable(player, data, payload, () -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.buy(player, payload.getString("id"), payload.getLong("price")), "MARKET"));
            // Service NPCs: every entry is re-checked by the hub against the NPC the player is standing at.
            case "npc_service" -> withNpc(player, data, payload, npc ->
                    net.schwarz.rotasutils.server.NpcHub.perform(player, data, npc, payload.getString("service")));
            case "auction_list", "auction_buy", "auction_cancel", "auction_claim" -> withNpc(player, data, payload, npc ->
                    net.schwarz.rotasutils.server.AuctionService.handle(player, data, npc, action, payload));
            // Breeding happens at a stable NPC, like any other trade with the stable.
            case "horse_breed" -> atStable(player, data, payload, () -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.breed(player, payload.getString("id"),
                            payload.getString("sire"), payload.getLong("cost")), "BREED"));
            case "horse_stud" -> atStable(player, data, payload, () -> horseResult(player, data, payload,
                    net.schwarz.rotasutils.server.horse.HorseService.setStud(player, payload.getString("id"), payload.getLong("fee")), "STABLE"));
            case "shop_buy" -> withNpc(player, data, payload, npc -> {
                var result = net.schwarz.rotasutils.server.ShopService.buy(player, data, npc, payload.getString("source"),
                        payload.getString("key"), Math.max(1, Math.min(64, payload.getInt("count"))));
                RotasNetwork.feedback(player, result.ok(), result.message());
                RotasNetwork.syncProgress(player);
                if (!payload.getBoolean("close")) net.schwarz.rotasutils.server.ShopService.open(player, data, npc);
            });
            case "crafter_commission" -> withNpc(player, data, payload, npc -> {
                var result = net.schwarz.rotasutils.server.CrafterService.commission(player, data, npc,
                        payload.getString("key"), Math.max(1, Math.min(64, payload.getInt("count"))));
                RotasNetwork.feedback(player, result.ok(), result.message());
                RotasNetwork.syncProgress(player);
                net.schwarz.rotasutils.server.CrafterService.open(player, data, npc);
            });
            case "save_season" -> {
                if (!admin) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.season.admin_only"));
                    return;
                }
                try {
                    String json = payload.contains("json_z", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)
                            ? net.schwarz.rotasutils.core.CompressedText.decompress(payload.getByteArray("json_z"))
                            : payload.getString("json");
                    var rules = net.schwarz.rotasutils.level.SeasonRules.fromJson(json);
                    data.levelConfig().setSeason(rules);
                    net.schwarz.rotasutils.server.SeasonConfigFile.write(player.server, data.levelConfig().season());
                    data.setDirty();
                    data.audit(player.getGameProfile().getName() + " saved season rules");
                    for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                        // Raised point rules reach everyone online at once; the rest top up on login.
                        net.schwarz.rotasutils.server.CharacterStatService.grantLevelPoints(data.progress(online.getUUID()), data);
                        net.schwarz.rotasutils.server.CharacterStatService.apply(online, data);
                        RotasNetwork.syncContent(online);
                        RotasNetwork.syncProgress(online);
                    }
                    RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.season.saved"));
                } catch (java.io.IOException | RuntimeException failure) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.season.save_failed", String.valueOf(failure.getMessage())));
                }
            }
            case "set_variable" -> {
                // Player preferences only: a short key, a boolean value and a small budget, so this
                // packet can neither bloat the saved record nor write outside the pref. namespace.
                String key = payload.getString("key");
                String value = payload.getString("value");
                PlayerProgress progress = data.progress(player.getUUID());
                if (!key.matches("[a-z0-9_]{1,32}") || !(value.equals("true") || value.equals("false"))) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.unknown_pref"));
                    return;
                }
                if (!progress.questVariables().containsKey("pref." + key) && progress.questVariables().keySet()
                        .stream().filter(existing -> existing.startsWith("pref.")).count() >= MAX_PREFERENCES) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.too_many_prefs"));
                    return;
                }
                progress.questVariables().put("pref." + key, value);
                progress.markDirty();
                data.setDirty();
                RotasNetwork.syncProgress(player);
            }

            // Admin --------------------------------------------------------
            default -> {
                if (!admin) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.item.not_admin"));
                    return;
                }
                handleAdmin(player, data, action, payload);
            }
        }
    }

    private static void handleAdmin(ServerPlayer player, RotasData data, String action, CompoundTag payload) {
        switch (action) {
            case "admin_menu" -> RotasNetwork.openAdminMenu(player);
            case "zone_gate_test_toggle" -> {
                if (!player.hasPermissions(2)) {
                    return;
                }
                boolean testing = net.schwarz.rotasutils.server.ZoneGateService.toggleAdminTest(player.getUUID());
                RotasNetwork.feedback(player, true, ThaiText.t(testing
                        ? "rotasutils.cmd.zone.gate_test_on" : "rotasutils.cmd.zone.gate_test_off"));
                RotasNetwork.syncContent(player);
            }
            case "give_house_wand" -> {
                ItemStack wand = new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.HOUSE_WAND.get());
                if (player.getInventory().contains(wand)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.has_house_wand"));
                    return;
                }
                RewardService.give(player, wand);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.house_wand_added"));
            }
            case "house_create" -> houseCreate(player, data, payload);
            case "house_edit" -> houseEdit(player, data, payload);
            case "house_replace_bounds" -> houseReplaceBounds(player, data, payload);
            case "house_remove" -> houseRemove(player, data, payload);
            case "house_save_config" -> houseSaveConfig(player, data, payload);
            case "new_quest" -> {
                QuestDef quest = new QuestDef(Ids.unique("quest", data.quests().keySet()));
                if (data.boards().size() == 1) {
                    quest.boardIds().add(data.boards().values().iterator().next().id());
                }
                data.putQuest(quest);
                RotasNetwork.openQuestCreator(player, quest.id());
            }
            case "new_quest_for_board" -> {
                BoardConfig board = data.board(payload.getString("board"));
                if (board == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.board_gone"));
                    return;
                }
                QuestDef quest = new QuestDef(Ids.unique("quest", data.quests().keySet()));
                quest.boardIds().add(board.id());
                data.putQuest(quest);
                RotasNetwork.openQuestCreator(player, quest.id());
            }
            case "open_quest" -> RotasNetwork.openQuestCreator(player, payload.getString("quest"));
            case "save_quest" -> saveQuest(player, data, payload, false);
            case "publish_quest" -> saveQuest(player, data, payload, true);
            case "publish_quest_open" -> {
                QuestDef quest = saveQuest(player, data, payload, true);
                if (quest == null) {
                    return;
                }
                BoardConfig board = firstAssignedBoard(data, quest);
                if (board == null) {
                    RotasNetwork.feedback(player, false,
                            ThaiText.t("rotasutils.msg.sa.publish_no_board"));
                    return;
                }
                RotasNetwork.openBoardBrowser(player, board);
            }
            case "duplicate_quest" -> {
                QuestDef source = data.quest(payload.getString("quest"));
                if (source == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.quest.gone"));
                    return;
                }
                QuestDef copy = source.copyAs(Ids.unique(source.name() + " copy", data.quests().keySet()));
                copy.setName(ThaiText.t("rotasutils.msg.sa.copy_name", source.name()));
                data.putQuest(copy);
                RotasNetwork.openQuestCreator(player, copy.id());
            }
            case "delete_quest" -> {
                String questId = payload.getString("quest");
                data.removeQuest(questId);
                // Active copies stay valid until turn-in; they simply stop being offered.
                data.audit(player.getGameProfile().getName() + " deleted quest " + questId);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.quest_deleted"));
                RotasNetwork.syncContent(player);
            }
            case "new_board" -> {
                // Boards exist independently of the block: an admin can author a pool first
                // and bind it to a billboard later, and a board whose block was broken
                // keeps working for NPCs and commands.
                BoardConfig board = new BoardConfig(Ids.unique("board", data.boards().keySet()));
                board.setName(ThaiText.t("rotasutils.msg.board.default_name"));
                data.putBoard(board);
                data.audit(player.getGameProfile().getName() + " created board " + board.id());
                RotasNetwork.openBoardConfig(player, board);
            }
            case "duplicate_board" -> {
                BoardConfig source = data.board(payload.getString("board"));
                if (source == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.board_gone"));
                    return;
                }
                BoardConfig copy = source.copyAs(Ids.unique(source.name() + " copy", data.boards().keySet()));
                copy.setName(ThaiText.t("rotasutils.msg.sa.copy_name", source.name()));
                data.putBoard(copy);
                data.audit(player.getGameProfile().getName() + " duplicated board " + source.id());
                RotasNetwork.openBoardConfig(player, copy);
            }
            case "save_board" -> saveBoard(player, data, payload);
            case "save_board_open" -> {
                BoardConfig board = saveBoard(player, data, payload);
                RotasNetwork.openBoardBrowser(player, board);
            }
            case "delete_board" -> {
                String boardId = payload.getString("board");
                // RotasData.removeBoard also drops the board from every quest that
                // listed it, so no dangling reference survives in the creator.
                // A second click (or a stale list) must not report a deletion that did not happen.
                if (!data.removeBoard(boardId)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.board_gone"));
                    RotasNetwork.syncContent(player);
                    return;
                }
                data.audit(player.getGameProfile().getName() + " deleted board " + boardId);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.board_removed"));
                RotasNetwork.syncContent(player);
            }
            case "rotate_board" -> {
                BoardConfig board = data.board(payload.getString("board"));
                if (board != null) {
                    BoardService.forceRotation(data, board);
                    RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.board_rotated"));
                    RotasNetwork.syncContent(player);
                }
            }
            case "new_npc" -> {
                NpcDef npc = new NpcDef(Ids.unique("npc", data.npcs().keySet()));
                data.putNpc(npc);
                data.audit(player.getGameProfile().getName() + " created NPC " + npc.id());
                RotasNetwork.openNpcConfig(player, npc);
            }
            case "open_npc" -> {
                NpcDef npc = data.npc(payload.getString("npc"));
                if (npc == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.npc_gone"));
                    return;
                }
                RotasNetwork.openNpcConfig(player, npc);
            }
            case "save_npc" -> saveNpc(player, data, payload);
            case "mob_setup_save" -> net.schwarz.rotasutils.server.MobSetupService.save(player,
                    payload.getString("id"), payload.getString("body"));
            case "mob_folder_set" -> {
                var list = payload.getList("ids", net.minecraft.nbt.Tag.TAG_STRING);
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (int i = 0; i < list.size() && i < 256; i++) ids.add(list.getString(i));
                net.schwarz.rotasutils.server.MobSetupService.setFolder(player, ids, payload.getString("folder").trim());
            }
            case "mob_category_save" -> net.schwarz.rotasutils.server.MobSetupService.saveCategory(player,
                    payload.getString("id"), payload.getString("body"));
            case "mob_setup_delete" -> net.schwarz.rotasutils.server.MobSetupService.delete(player, payload.getString("id"));
            case "give_npc_wand" -> {
                ItemStack wand = new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.NPC_WAND.get());
                if (player.getInventory().contains(wand)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.has_npc_wand"));
                    return;
                }
                RewardService.give(player, wand);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.npc_wand_added"));
            }
            case "npc_unbind" -> {
                NpcDef npc = data.npc(payload.getString("npc"));
                if (npc != null && npc.bound()) {
                    npc.setEntityUuid("");
                    npc.setEntityType("");
                    npc.setDimension("");
                    data.putNpc(npc);
                    data.audit(player.getGameProfile().getName() + " released NPC " + npc.id());
                    // Live release supersedes any staged draft for this character.
                    data.configHistory().discard(player.getUUID().toString(), "npc/" + npc.id());
                    RotasNetwork.syncContent(player);
                }
            }
            case "test_npc_dialogue" -> {
                // Previews the editor draft when one is attached, so unsaved lines can be
                // tested against the admin's own progress before the review flow.
                NpcDef npc = payload.contains("npc_draft")
                        ? NpcDef.load(payload.getCompound("npc_draft"))
                        : data.npc(payload.getString("npc"));
                if (npc == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.save_npc_first"));
                    return;
                }
                net.schwarz.rotasutils.server.NpcConversations.preview(player, data, npc);
            }
            case "npc_assign_quest" -> {
                NpcDef npc = data.npc(payload.getString("npc"));
                String questId = payload.getString("quest");
                if (npc == null || data.quest(questId) == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.npc_or_quest_gone"));
                    return;
                }
                boolean added = !npc.questIds().remove(questId) && npc.questIds().add(questId);
                data.putNpc(npc);
                data.audit(player.getGameProfile().getName()
                        + (added ? " assigned " : " unassigned ") + questId + " to NPC " + npc.id());
                // Live assignment supersedes any staged draft for this character.
                data.configHistory().discard(player.getUUID().toString(), "npc/" + npc.id());
                RotasNetwork.feedback(player, true, added
                        ? ThaiText.t("rotasutils.msg.sa.npc_assigned", npc.name())
                        : ThaiText.t("rotasutils.msg.sa.npc_unassigned", npc.name()));
                RotasNetwork.syncContent(player);
            }
            case "duplicate_npc" -> {
                NpcDef source = data.npc(payload.getString("npc"));
                if (source == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.npc_gone"));
                    return;
                }
                NpcDef copy = source.copyAs(Ids.unique(source.name() + " copy", data.npcs().keySet()));
                copy.setName(ThaiText.t("rotasutils.msg.sa.copy_name", source.name()));
                data.putNpc(copy);
                RotasNetwork.openNpcConfig(player, copy);
            }
            case "delete_npc" -> {
                String npcId = payload.getString("npc");
                data.removeNpc(npcId);
                data.audit(player.getGameProfile().getName() + " deleted NPC " + npcId);
                // Deletion supersedes any staged draft for this character.
                data.configHistory().discard(player.getUUID().toString(), "npc/" + npcId);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.npc_removed"));
                RotasNetwork.syncContent(player);
            }
            case "new_zone" -> {
                // Starts with a sphere around the admin rather than no shape, because a zone with no
                // shapes covers the whole dimension - a surprising thing for "New zone here" to do.
                net.minecraft.core.BlockPos here = player.blockPosition();
                net.schwarz.rotasutils.core.ZoneDef zone = net.schwarz.rotasutils.server.ZoneWandService.createZone(
                        data, player.serverLevel(), here, new net.schwarz.rotasutils.core.ZoneArea.Sphere(
                                here.getX(), here.getY(), here.getZ(),
                                net.schwarz.rotasutils.server.ZoneWandService.radius(player)));
                net.schwarz.rotasutils.server.ZoneWandService.setActive(player, zone.id());
                data.audit(player.getGameProfile().getName() + " created zone " + zone.id());
                syncZones(player);
                RotasNetwork.openZoneEdit(player, zone.id());
            }
            case "open_zone" -> {
                net.schwarz.rotasutils.core.ZoneDef zone = data.zone(payload.getString("zone"));
                if (zone == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.zone_gone"));
                    return;
                }
                // The wand grows whichever zone the admin is looking at in the editor.
                net.schwarz.rotasutils.server.ZoneWandService.setActive(player, zone.id());
                RotasNetwork.openZoneEdit(player, zone.id());
            }
            case "save_zone" -> {
                net.schwarz.rotasutils.core.ZoneDef zone = data.zone(payload.getString("zone"));
                if (zone == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.zone_gone"));
                    return;
                }
                try {
                    String name = payload.getString("name").trim();
                    name = name.isEmpty() ? zone.name() : name.length() > 64 ? name.substring(0, 64) : name;
                    var danger = net.schwarz.rotasutils.core.ZoneDef.Danger.parse(payload.getString("danger"), zone.danger());
                    net.schwarz.rotasutils.core.ZoneDef updated = zone.withSettings(name,
                            payload.getInt("level_min"), payload.getInt("level_max"),
                            payload.getInt("priority"), payload.getBoolean("enabled"), danger,
                            payload.contains("recommended_min") ? payload.getInt("recommended_min") : zone.recommendedMin(),
                            payload.contains("recommended_max") ? payload.getInt("recommended_max") : zone.recommendedMax(),
                            payload.contains("xp_multiplier") ? payload.getDouble("xp_multiplier") : zone.xpMultiplier(),
                            payload.contains("transition_blocks") ? payload.getInt("transition_blocks") : zone.transitionBlocks(),
                            payload.contains("safe") ? payload.getBoolean("safe") : zone.safe());
                    if (payload.contains("entry_requirements")) {
                        updated = updated.withEntryRequirements(net.schwarz.rotasutils.util.Nbt.loadList(
                                payload, "entry_requirements", net.schwarz.rotasutils.quest.requirement.Requirement::load));
                    }
                    if (payload.contains("combat_rules")) {
                        updated = updated.withCombatRules(net.schwarz.rotasutils.core.ZoneCombatRules.load(payload.getCompound("combat_rules")));
                    }
                    if (payload.contains("features")) {
                        updated = updated.withFeatures(net.schwarz.rotasutils.core.ZoneFeatures.load(payload.getCompound("features")));
                    }
                    long baseRevision = payload.contains("base_revision") ? payload.getLong("base_revision") : zone.revision();
                    var applied = net.schwarz.rotasutils.core.ZoneValidator.apply(zone, updated, baseRevision);
                    if (applied.status() != net.schwarz.rotasutils.core.ZoneApplyResult.Status.APPLIED) {
                        RotasNetwork.feedback(player, false, String.join("; ", applied.errors()));
                        syncZones(player);
                        return;
                    }
                    updated = applied.zone();
                    data.putZone(updated);
                    net.schwarz.rotasutils.server.ZoneWandService.setActive(player, updated.id());
                    data.audit(player.getGameProfile().getName() + " saved zone " + updated.id()
                            + " " + updated.levelLabel() + " " + updated.danger() + " xp=" + updated.xpMultiplier());
                    RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.zone_saved"));
                    syncZones(player);
                } catch (IllegalArgumentException failure) {
                    RotasNetwork.feedback(player, false, failure.getMessage());
                }
            }
            case "delete_zone" -> {
                String zoneId = payload.getString("zone");
                data.removeZone(zoneId);
                data.audit(player.getGameProfile().getName() + " deleted zone " + zoneId);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.zone_removed"));
                syncZones(player);
                RotasNetwork.openAdminMenu(player, "ZONES");
            }
            case "zone_point_respawn" -> {
                var kernel = data.kernel();
                boolean ready = kernel != null
                        && kernel.encounters().respawnNow(payload.getString("zone"), payload.getString("point"));
                RotasNetwork.feedback(player, ready, ThaiText.t(ready ? "rotasutils.msg.sa.point_respawn" : "rotasutils.msg.sa.point_busy"));
            }
            case "zone_add_sphere" -> {
                net.schwarz.rotasutils.core.ZoneDef zone = data.zone(payload.getString("zone"));
                if (zone == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.zone_gone"));
                    return;
                }
                RotasNetwork.feedback(player, true, addArea(player, data, zone, new net.schwarz.rotasutils.core.ZoneArea.Sphere(
                        payload.getInt("x"), payload.getInt("y"), payload.getInt("z"),
                        Math.max(1, payload.getInt("radius")))));
            }
            case "zone_add_box" -> {
                net.schwarz.rotasutils.core.ZoneDef zone = data.zone(payload.getString("zone"));
                if (zone == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.zone_gone"));
                    return;
                }
                RotasNetwork.feedback(player, true, addArea(player, data, zone, new net.schwarz.rotasutils.core.ZoneArea.Box(
                        payload.getInt("x1"), payload.getInt("y1"), payload.getInt("z1"),
                        payload.getInt("x2"), payload.getInt("y2"), payload.getInt("z2"))));
            }
            case "zone_remove_area" -> {
                net.schwarz.rotasutils.core.ZoneDef zone = data.zone(payload.getString("zone"));
                if (zone == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.zone_gone"));
                    return;
                }
                int index = payload.getInt("index");
                if (index < 0 || index >= zone.areas().size()) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.area_gone"));
                    return;
                }
                java.util.List<net.schwarz.rotasutils.core.ZoneArea> remaining =
                        new java.util.ArrayList<>(zone.areas());
                remaining.remove(index);
                data.putZone(zone.withAreas(remaining));
                data.audit(player.getGameProfile().getName() + " removed an area from zone " + zone.id());
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.area_removed"));
                syncZones(player);
            }
            case "zone_clear_areas" -> {
                net.schwarz.rotasutils.core.ZoneDef zone = data.zone(payload.getString("zone"));
                if (zone == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.zone_gone"));
                    return;
                }
                data.putZone(zone.withAreas(java.util.List.of()));
                data.audit(player.getGameProfile().getName() + " cleared zone areas for " + zone.id());
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.zone_whole", zone.name()));
                syncZones(player);
            }
            case "give_zone_wand" -> {
                ItemStack wand = new ItemStack(net.schwarz.rotasutils.registry.RotasRegistry.ZONE_WAND.get());
                if (player.getInventory().contains(wand)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.has_zone_wand"));
                    return;
                }
                RewardService.give(player, wand);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.zone_wand_added",
                        net.schwarz.rotasutils.server.ZoneWandService.radius(player)));
            }
            case "zone_wand_radius" -> {
                net.schwarz.rotasutils.server.ZoneWandService.setRadius(player, payload.getInt("radius"));
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.zone_wand_radius",
                        net.schwarz.rotasutils.server.ZoneWandService.radius(player)));
            }
            case "new_category" -> {
                String name = payload.getString("name").isEmpty() ? ThaiText.t("rotasutils.default.category.name") : payload.getString("name");
                SkillCategory category = new SkillCategory(Ids.unique(name, data.categories().keySet()), name);
                category.setOrder(data.categories().size());
                data.putCategory(category);
                RotasNetwork.openSkillEditor(player, category.id());
            }
            case "open_category" -> RotasNetwork.openSkillEditor(player, payload.getString("category"));
            case "save_category" -> {
                SkillCategory category = SkillCategory.load(payload.getCompound("category"));
                if (category.id().isEmpty()) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.category_no_id"));
                    return;
                }
                SkillCategory previous = data.category(category.id());
                // Command effects run at operator level 4; only a level-4 operator may add or rewrite them.
                if (!BoardService.isOperator(player)
                        && !net.schwarz.rotasutils.server.ConfigService.commands(category)
                                .equals(net.schwarz.rotasutils.server.ConfigService.commands(previous))) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.commands_op4"));
                    return;
                }
                if (previous != null) {
                    category.bumpVersion();
                    preserveRemovedNodes(data, previous, category);
                }
                data.putCategory(category);
                data.audit(player.getGameProfile().getName() + " saved skill category " + category.id());
                for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                    SkillService.recalculate(online, data);
                    RotasNetwork.syncProgress(online);
                }
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.skill_tree_saved"));
                RotasNetwork.syncContent(player);
            }
            case "delete_category" -> {
                String categoryId = payload.getString("category");
                SkillCategory category = data.category(categoryId);
                if (category != null && payload.getBoolean("refund")) {
                    refundCategory(player, data, category);
                }
                data.removeCategory(categoryId);
                data.audit(player.getGameProfile().getName() + " deleted skill category " + categoryId);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.category_removed"));
                RotasNetwork.syncContent(player);
            }
            case "save_level_config" -> {
                LevelConfig config = LevelConfig.load(payload.getCompound("level_config"));
                // The screen only edits the EXP curve of the season rules. Take those three numbers and keep
                // the server's own copy of everything else, so a screen opened before another admin's
                // season edit cannot quietly undo it.
                var season = data.levelConfig().season().copy();
                season.mainBaseXp = config.season().mainBaseXp;
                season.mainExponent = config.season().mainExponent;
                season.mainMaxLevel = config.season().mainMaxLevel;
                config.setSeason(season);
                data.setLevelConfig(config);
                try {
                    net.schwarz.rotasutils.server.SeasonConfigFile.write(player.server, config.season());
                } catch (java.io.IOException failure) {
                    RotasNetwork.feedback(player, false, "Saved, but season.json could not be written: " + failure.getMessage());
                }
                data.setDirty();
                for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                    ProgressService.refreshClearance(online, data, data.progress(online.getUUID()));
                    net.schwarz.rotasutils.server.CharacterStatService.grantLevelPoints(data.progress(online.getUUID()), data);
                    net.schwarz.rotasutils.server.CharacterStatService.apply(online, data);
                    RotasNetwork.syncProgress(online);
                    RotasNetwork.syncContent(online);
                }
                data.audit(player.getGameProfile().getName() + " updated the level system");
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.level_saved"));
            }
            case "save_server_settings" -> {
                CompoundTag stored = payload.getCompound("server_settings");
                net.schwarz.rotasutils.data.ServerSettings loaded =
                        net.schwarz.rotasutils.data.ServerSettings.load(stored);
                // The admin level decides who reaches every action in handleAdmin; lowering it would
                // hand item grants and level-4 reward commands to ordinary players.
                if (loaded.adminOpLevel() != data.serverSettings().adminOpLevel() && !BoardService.isOperator(player)) {
                    RotasNetwork.feedback(player, false,
                            ThaiText.t("rotasutils.msg.sa.op4"));
                    return;
                }
                copySettings(loaded, data);
                data.setDirty();
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.server_saved"));
                RotasNetwork.syncContent(player);
            }
            case "validate" -> {
                List<Validation.Issue> issues = Validation.validateAll(data);
                RotasNetwork.sendValidation(player, issues);
            }
            case "pick" -> {
                WorldPicker.Kind kind = switch (payload.getString("kind")) {
                    case "ENTITY" -> WorldPicker.Kind.ENTITY;
                    case "NPC" -> WorldPicker.Kind.NPC;
                    case "NPC_BIND" -> WorldPicker.Kind.NPC_BIND;
                    case "BOARD" -> WorldPicker.Kind.BOARD;
                    default -> WorldPicker.Kind.POSITION;
                };
                WorldPicker.begin(player, kind, payload.getString("screen"), payload.getString("field"),
                        payload.contains("npc") ? payload.getString("npc") : "");
            }

            // Jobs and character stats -------------------------------------
            case "save_job" -> {
                net.schwarz.rotasutils.job.JobDef job = net.schwarz.rotasutils.job.JobDef.load(payload.getCompound("job"));
                if (!job.id().matches("[a-z0-9_]{1,32}")) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.job_id"));
                    return;
                }
                data.putJob(job);
                data.audit(player.getGameProfile().getName() + " saved job " + job.id());
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.job_saved"));
                syncContentToEveryone(player);
            }
            case "delete_job" -> {
                String jobId = payload.getString("job");
                data.removeJob(jobId);
                data.audit(player.getGameProfile().getName() + " deleted job " + jobId);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.job_removed"));
                for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                    SkillService.recalculate(online, data);
                }
                syncContentToEveryone(player);
            }
            case "admin_set_job" -> withTarget(player, data, payload, target -> {
                String jobId = payload.getString("job");
                if (!jobId.isEmpty() && data.job(jobId) == null) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.job_gone"));
                    return;
                }
                int refunded = net.schwarz.rotasutils.server.JobService.assign(target, data,
                        data.progress(target.getUUID()), jobId);
                data.audit(player.getGameProfile().getName() + " set job of "
                        + target.getGameProfile().getName() + " to " + (jobId.isEmpty() ? "none" : jobId));
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, (jobId.isEmpty()
                        ? ThaiText.t("rotasutils.msg.sa.player_no_job", target.getGameProfile().getName())
                        : ThaiText.t("rotasutils.msg.sa.player_job", target.getGameProfile().getName(), data.job(jobId).name()))
                        + (refunded > 0 ? " " + ThaiText.t("rotasutils.msg.sa.refunded_suffix", refunded) : ""));
            });
            case "admin_reset_stats" -> withTarget(player, data, payload, target -> {
                int refunded = net.schwarz.rotasutils.server.CharacterStatService.reset(target, data);
                data.audit(player.getGameProfile().getName() + " reset core stats of "
                        + target.getGameProfile().getName() + " refunded=" + refunded);
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.refunded_stat", refunded));
            });

            // Player progress manager ---------------------------------------
            case "admin_set_level" -> withTarget(player, data, payload, target -> {
                int before = data.progress(target.getUUID()).level();
                ProgressService.setLevel(target, data, payload.getInt("level"));
                int after = data.progress(target.getUUID()).level();
                data.audit(player.getGameProfile().getName() + " set level of "
                        + target.getGameProfile().getName() + " " + before + " -> " + after);
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.now_level",
                        target.getGameProfile().getName(), after));
            });
            case "admin_add_xp" -> withTarget(player, data, payload, target -> {
                long amount = payload.getLong("amount");
                if (amount <= 0) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.stat.admin_invalid"));
                    return;
                }
                int gained = ProgressService.addExperience(target, data, amount, false);
                data.audit(player.getGameProfile().getName() + " granted " + amount + " xp to "
                        + target.getGameProfile().getName() + " levels_gained=" + gained);
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.xp_granted"));
            });
            case "admin_add_points" -> withTarget(player, data, payload, target -> {
                int amount = payload.getInt("amount");
                if (amount <= 0) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.stat.admin_invalid"));
                    return;
                }
                data.progress(target.getUUID()).addSkillPoints(amount);
                data.audit(player.getGameProfile().getName() + " granted " + amount + " skill points to "
                        + target.getGameProfile().getName());
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.points_granted"));
            });
            case "admin_grant_clearance" -> withTarget(player, data, payload, target -> {
                DangerRank rank = DangerRank.byName(payload.getString("rank"), DangerRank.F);
                data.progress(target.getUUID()).grantClearance(rank);
                data.audit(player.getGameProfile().getName() + " granted clearance " + rank.name() + " to "
                        + target.getGameProfile().getName());
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.clearance_granted", rank.display()));
            });
            case "admin_revoke_clearance" -> withTarget(player, data, payload, target -> {
                DangerRank rank = DangerRank.byName(payload.getString("rank"), DangerRank.F);
                // F is the starting clearance every player keeps.
                if (rank == DangerRank.F || !data.progress(target.getUUID()).clearance().remove(rank)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.admin_nothing_changed"));
                    return;
                }
                data.progress(target.getUUID()).markDirty();
                adminAudit(player, target, "revoked clearance " + rank.name());
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.admin_done"));
            });
            case "admin_currency" -> withTarget(player, data, payload, target -> {
                long delta = payload.getLong("amount");
                String currency = SeasonService.rules(data).currency;
                try {
                    if (delta == 0) { throw new IllegalArgumentException(); }
                    data.progress(target.getUUID()).rpg().currency(currency, delta);
                } catch (IllegalArgumentException | ArithmeticException invalid) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.admin_currency_range"));
                    return;
                }
                adminAudit(player, target, "currency " + currency + " " + (delta > 0 ? "+" : "") + delta);
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.admin_done"));
            });
            case "admin_grant_title", "admin_revoke_title" -> withTarget(player, data, payload, target -> {
                String id = payload.getString("title");
                boolean grant = action.equals("admin_grant_title");
                net.schwarz.rotasutils.title.TitleDef title = data.title(id);
                boolean changed = grant ? title != null && TitleService.award(target, data, title, true)
                        : TitleService.revoke(target, data, id);
                if (!changed) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.admin_nothing_changed"));
                    return;
                }
                adminAudit(player, target, (grant ? "granted title " : "revoked title ") + id);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.admin_done"));
            });
            case "admin_remove_quest" -> withTarget(player, data, payload, target -> {
                String quest = payload.getString("quest");
                PlayerProgress progress = data.progress(target.getUUID());
                if (!progress.activeQuests().containsKey(quest)) {
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.admin_nothing_changed"));
                    return;
                }
                progress.removeActive(quest);
                adminAudit(player, target, "removed active quest " + quest);
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.admin_done"));
            });
            case "admin_clear_cooldowns" -> withTarget(player, data, payload, target -> {
                PlayerProgress progress = data.progress(target.getUUID());
                int cleared = progress.questCooldowns().size();
                progress.questCooldowns().clear();
                progress.markDirty();
                adminAudit(player, target, "cleared " + cleared + " quest cooldowns");
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.admin_done"));
            });
            case "admin_unlock_waystones" -> withTarget(player, data, payload, target -> {
                PlayerProgress progress = data.progress(target.getUUID());
                int added = 0;
                for (String id : data.waystones().keySet()) {
                    if (progress.discoverWaystone(id)) { added++; }
                }
                adminAudit(player, target, "unlocked " + added + " waystones");
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.admin_done"));
            });
            case "admin_reset_skills" -> withTarget(player, data, payload, target -> {
                int refunded = SkillService.reset(target, data, null);
                data.audit(player.getGameProfile().getName() + " reset skills of "
                        + target.getGameProfile().getName() + " refunded=" + refunded);
                RotasNetwork.syncProgress(target);
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.refunded_points", refunded));
            });
            case "admin_grant_quest" -> withTarget(player, data, payload, target -> {
                QuestService.ActionResult result = QuestService.accept(target, data,
                        payload.getString("quest"), "");
                if (result.success()) {
                    data.audit(player.getGameProfile().getName() + " granted quest " + payload.getString("quest")
                            + " to " + target.getGameProfile().getName());
                }
                RotasNetwork.feedback(player, result.success(), result.message());
            });

            // Quest testing --------------------------------------------------
            case "test_complete_objective" -> testCompleteObjective(player, data, payload);
            case "test_fail" -> QuestService.fail(player, data, payload.getString("quest"), ThaiText.t("rotasutils.msg.sa.test_failure"));
            case "test_reset" -> {
                data.progress(player.getUUID()).removeActive(payload.getString("quest"));
                data.progress(player.getUUID()).completedQuests().remove(payload.getString("quest"));
                data.progress(player.getUUID()).setCooldown(payload.getString("quest"), 0);
                data.setDirty();
                RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.test_reset"));
                RotasNetwork.syncProgress(player);
            }
            case "test_give_item" -> {
                ResourceLocation itemId = ResourceLocation.tryParse(payload.getString("item"));
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    RewardService.give(player, new ItemStack(BuiltInRegistries.ITEM.get(itemId),
                            Math.max(1, Math.min(64 * 36, payload.getInt("amount")))));
                    RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.item_given"));
                }
            }
            case "test_spawn_mob" -> {
                ResourceLocation entityId = ResourceLocation.tryParse(payload.getString("entity"));
                // The entity registry is defaulted: get() on an unknown id would spawn a pig.
                EntityType<?> type = entityId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)
                        ? null : BuiltInRegistries.ENTITY_TYPE.get(entityId);
                if (type != null) {
                    type.spawn(player.serverLevel(), player.blockPosition().above(),
                            net.minecraft.world.entity.MobSpawnType.COMMAND);
                    RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.mob_spawned"));
                }
            }
            case "test_teleport" -> {
                var pos = RewardService.parsePos(payload.getString("pos"));
                if (pos != null) {
                    player.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.teleported"));
                }
            }
            case "test_why" -> RotasNetwork.feedback(player, true,
                    ObjectiveEngine.lastRejection(player).isEmpty()
                            ? ThaiText.t("rotasutils.msg.sa.no_rejections")
                            : ThaiText.t("rotasutils.msg.sa.last_rejection", ObjectiveEngine.lastRejection(player)));

            default -> RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.unknown_action", action));
        }
    }

    // Helpers --------------------------------------------------------------

    private static void houseCreate(ServerPlayer player, RotasData data, CompoundTag payload) {
        HouseAdminService.CreateRequest request = decodeHouseCreate(player, payload);
        if (request == null) {
            return;
        }
        reportHouseAction(player, houseAdminService(player, data).create(player.getUUID(), request));
    }

    private static void houseEdit(ServerPlayer player, RotasData data, CompoundTag payload) {
        HouseAdminService.EditRequest request = decodeHouseEdit(player, payload);
        if (request == null) {
            return;
        }
        reportHouseAction(player, houseAdminService(player, data).edit(player.getUUID(), request));
    }

    private static void houseReplaceBounds(ServerPlayer player, RotasData data, CompoundTag payload) {
        HouseAdminService.ReplaceBoundsRequest request = decodeHouseReplaceBounds(player, payload);
        if (request == null) {
            return;
        }
        reportHouseAction(player, houseAdminService(player, data).replaceBounds(player.getUUID(), request));
    }

    private static void houseRemove(ServerPlayer player, RotasData data, CompoundTag payload) {
        HouseAdminService.RemoveRequest request = decodeHouseRemove(player, payload);
        if (request == null) {
            return;
        }
        reportHouseAction(player, houseAdminService(player, data).remove(player.getUUID(), request));
    }

    private static void houseSaveConfig(ServerPlayer player, RotasData data, CompoundTag payload) {
        HouseAdminService.ConfigRequest request = decodeHouseConfig(player, payload);
        if (request == null) {
            return;
        }
        reportHouseAction(player, houseAdminService(player, data).saveConfig(player.getUUID(), request));
    }

    private static void reportHouseAction(ServerPlayer player, HouseAdminService.Action action) {
        RotasNetwork.feedback(player, action.success(), action.message());
        // A stale editor needs the current revision, but failed validation must not
        // cause a broad content refresh or discard the client's typed draft.
        if (!action.success() && action.message().equals("house.stale")) {
            RotasNetwork.syncContent(player);
        }
    }

    private static HouseAdminService.Store houseStore(ServerPlayer actor, RotasData data) {
        return new HouseAdminService.Store() {
            @Override
            public boolean isAdmin(UUID playerId) {
                return actor.getUUID().equals(playerId) && BoardService.isAdmin(actor, data);
            }

            @Override
            public java.util.Map<String, net.schwarz.rotasutils.house.HouseDefinition> houses() {
                return data.houses();
            }

            @Override
            public net.schwarz.rotasutils.house.HouseConfig config() {
                return data.houseConfig();
            }

            @Override
            public HouseAdminService.Selection selection(UUID playerId) {
                if (!actor.getUUID().equals(playerId)) {
                    return null;
                }
                ItemStack wand = heldHouseWand(actor);
                if (wand.isEmpty()) {
                    return null;
                }
                return new HouseAdminService.Selection(HouseWandItem.dimension(wand),
                        HouseWandItem.first(wand), HouseWandItem.second(wand));
            }

            @Override
            public void clearSelection(UUID playerId) {
                if (actor.getUUID().equals(playerId)) {
                    ItemStack wand = heldHouseWand(actor);
                    if (!wand.isEmpty()) {
                        HouseWandItem.clear(wand);
                    }
                }
            }

            @Override
            public void putHouse(net.schwarz.rotasutils.house.HouseDefinition house) {
                data.putHouse(house);
            }

            @Override
            public void removeHouse(String id) {
                data.removeHouse(id);
            }

            @Override
            public void setConfig(net.schwarz.rotasutils.house.HouseConfig config) {
                data.setHouseConfig(config);
            }

            @Override
            public net.schwarz.rotasutils.house.HouseTenancy tenancy(String id) {
                return data.houseTenancy(id);
            }

            @Override
            public void markDirty() {
                data.setDirty();
            }

            @Override
            public void rebuildHousing() {
                // HouseRegistry and HouseBillingService read RotasData directly; no secondary index exists.
            }

            @Override
            public void audit(String record) {
                data.audit(Instant.now() + " " + record);
            }

            @Override
            public void resync() {
                RotasNetwork.syncContent(actor.server);
            }
        };
    }

    /**
     * Creates the same actor-bound service used by packet actions for legacy
     * server commands. The service still rechecks permission on each operation.
     */
    public static HouseAdminService houseAdminService(ServerPlayer actor, RotasData data) {
        return new HouseAdminService(houseStore(actor, data));
    }

    private static ItemStack heldHouseWand(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof HouseWandItem) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof HouseWandItem ? offhand : ItemStack.EMPTY;
    }

    private static HouseAdminService.CreateRequest decodeHouseCreate(ServerPlayer player, CompoundTag payload) {
        try {
            return new HouseAdminService.CreateRequest(
                    requiredString(payload, 64, "id", "house_id", "house"),
                    requiredString(payload, 96, "name", "display_name"),
                    requiredString(payload, 64, "tier", "tier_id"), requiredRevision(payload,
                            "config_revision", "base_revision", "expected_config_revision", "revision"));
        } catch (IllegalArgumentException malformed) {
            rejectHousePayload(player, malformed);
            return null;
        }
    }

    private static HouseAdminService.EditRequest decodeHouseEdit(ServerPlayer player, CompoundTag payload) {
        try {
            return new HouseAdminService.EditRequest(
                    requiredString(payload, 64, "id", "house_id", "house"),
                    requiredString(payload, 96, "name", "display_name"),
                    requiredString(payload, 64, "tier", "tier_id"), requiredBoolean(payload, "enabled"),
                    requiredRevision(payload, "revision", "definition_revision", "expected_revision"));
        } catch (IllegalArgumentException malformed) {
            rejectHousePayload(player, malformed);
            return null;
        }
    }

    private static HouseAdminService.ReplaceBoundsRequest decodeHouseReplaceBounds(ServerPlayer player,
                                                                                    CompoundTag payload) {
        try {
            return new HouseAdminService.ReplaceBoundsRequest(requiredString(payload, 64, "id", "house_id", "house"),
                    requiredRevision(payload, "revision", "definition_revision", "expected_revision"));
        } catch (IllegalArgumentException malformed) {
            rejectHousePayload(player, malformed);
            return null;
        }
    }

    private static HouseAdminService.RemoveRequest decodeHouseRemove(ServerPlayer player, CompoundTag payload) {
        try {
            return new HouseAdminService.RemoveRequest(requiredString(payload, 64, "id", "house_id", "house"),
                    requiredRevision(payload, "revision", "definition_revision", "expected_revision"));
        } catch (IllegalArgumentException malformed) {
            rejectHousePayload(player, malformed);
            return null;
        }
    }

    private static HouseAdminService.ConfigRequest decodeHouseConfig(ServerPlayer player, CompoundTag payload) {
        try {
            return new HouseAdminService.ConfigRequest(
                    requiredString(payload, 128, "currency", "currency_id"),
                    requiredLong(payload, "payment_interval_millis", "payment_interval"),
                    requiredLong(payload, "reminder_lead_millis", "reminder_lead"),
                    requiredLong(payload, "grace_millis", "grace"),
                    requiredInt(payload, "buyout_multiplier"),
                    requiredInt(payload, "base_member_limit"),
                    requiredLong(payload, "member_slot_price"),
                    requiredInt(payload, "max_purchased_member_slots", "max_member_slots"),
                    decodeHouseTiers(payload),
                    requiredRevision(payload, "base_revision", "config_revision", "expected_config_revision", "revision"));
        } catch (IllegalArgumentException malformed) {
            rejectHousePayload(player, malformed);
            return null;
        }
    }

    private static List<net.schwarz.rotasutils.house.HouseTier> decodeHouseTiers(CompoundTag payload) {
        if (!payload.contains("tiers", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("tiers must be a list");
        }
        Tag rawList = payload.get("tiers");
        if (!(rawList instanceof ListTag rawTiers)) {
            throw new IllegalArgumentException("tiers must be a list");
        }
        if (rawTiers.size() > 64) {
            throw new IllegalArgumentException("tiers may contain at most 64 entries");
        }
        List<net.schwarz.rotasutils.house.HouseTier> tiers = new ArrayList<>(rawTiers.size());
        for (int index = 0; index < rawTiers.size(); index++) {
            Tag raw = rawTiers.get(index);
            if (!(raw instanceof CompoundTag tier)) {
                throw new IllegalArgumentException("tiers[" + index + "] must be a compound");
            }
            String id = requiredString(tier, "id", 64);
            long deposit = requiredLong(tier, "deposit");
            long maintenance = requiredLong(tier, "maintenance");
            try {
                tiers.add(new net.schwarz.rotasutils.house.HouseTier(id, deposit, maintenance));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("tiers[" + index + "]: " + invalid.getMessage(), invalid);
            }
        }
        return List.copyOf(tiers);
    }

    private static String requiredString(CompoundTag payload, String key, int maxLength) {
        return requiredString(payload, maxLength, key);
    }

    private static String requiredString(CompoundTag payload, int maxLength, String... keys) {
        String key = presentKey(payload, keys, Tag.TAG_STRING);
        String value = payload.getString(key);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(key + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    private static boolean requiredBoolean(CompoundTag payload, String key) {
        if (payload == null || !payload.contains(key, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return payload.getBoolean(key);
    }

    private static int requiredInt(CompoundTag payload, String... keys) {
        String key = presentKey(payload, keys, Tag.TAG_INT);
        return payload.getInt(key);
    }

    private static long requiredLong(CompoundTag payload, String... keys) {
        String key = presentKey(payload, keys, Tag.TAG_LONG);
        return payload.getLong(key);
    }

    private static long requiredRevision(CompoundTag payload, String... keys) {
        if (payload == null) {
            throw new IllegalArgumentException("revision is required");
        }
        Long found = null;
        for (String key : keys) {
            if (!payload.contains(key)) {
                continue;
            }
            if (!payload.contains(key, Tag.TAG_LONG)) {
                throw new IllegalArgumentException(key + " must be a long");
            }
            long value = payload.getLong(key);
            if (found != null && found.longValue() != value) {
                throw new IllegalArgumentException("conflicting revision fields");
            }
            found = value;
        }
        if (found == null) {
            throw new IllegalArgumentException("revision is required");
        }
        return found;
    }

    private static String presentKey(CompoundTag payload, String[] keys, int type) {
        if (payload == null) {
            throw new IllegalArgumentException(keys[0] + " is required");
        }
        String first = null;
        for (String key : keys) {
            if (!payload.contains(key)) {
                continue;
            }
            if (!payload.contains(key, type)) {
                throw new IllegalArgumentException(key + " has the wrong type");
            }
            if (first == null) {
                first = key;
            } else if (type == Tag.TAG_LONG && payload.getLong(first) != payload.getLong(key)) {
                throw new IllegalArgumentException("conflicting " + keys[0] + " fields");
            } else if (type == Tag.TAG_INT && payload.getInt(first) != payload.getInt(key)) {
                throw new IllegalArgumentException("conflicting " + keys[0] + " fields");
            } else if (type == Tag.TAG_STRING && !payload.getString(first).equals(payload.getString(key))) {
                throw new IllegalArgumentException("conflicting " + keys[0] + " fields");
            }
        }
        if (first == null) {
            throw new IllegalArgumentException(keys[0] + " is required");
        }
        return first;
    }

    private static void rejectHousePayload(ServerPlayer player, IllegalArgumentException malformed) {
        RotasNetwork.feedback(player, false, "house.invalid_request: " + malformed.getMessage());
    }

    /** Jobs and stats are shown to every player, so their edits refresh everyone's content. */
    private static void syncContentToEveryone(ServerPlayer actor) {
        for (ServerPlayer online : actor.server.getPlayerList().getPlayers()) {
            RotasNetwork.syncContent(online);
        }
    }

    /** Refreshes a player who was just removed from a party, if they are online. */
    private static void notifyRemoved(ServerPlayer actor, java.util.UUID memberId) {
        if (memberId.equals(actor.getUUID())) {
            return;
        }
        ServerPlayer member = actor.server.getPlayerList().getPlayer(memberId);
        if (member != null) {
            RotasNetwork.syncProgress(member);
            RotasNetwork.syncParty(member);
        }
    }

    /** Reports a party action and refreshes the roster for everyone it touched. */
    private static void party(ServerPlayer player, PartyService.Result result) {
        RotasNetwork.feedback(player, result.success(), result.message());
        RotasNetwork.syncProgress(player);
        RotasNetwork.syncPartyAll(player);
    }

    private static QuestDef saveQuest(ServerPlayer player, RotasData data,
                                      CompoundTag payload, boolean publish) {
        QuestDef quest = QuestDef.load(payload.getCompound("quest"));
        if (quest.id().isEmpty()) {
            quest.setId(Ids.unique(quest.name(), data.quests().keySet()));
        }
        QuestDef previous = data.quest(quest.id());
        if (previous != null) {
            quest.setVersion(previous.version());
        }
        // Command rewards run at operator level 4; only a level-4 operator may add or rewrite them.
        if (!BoardService.isOperator(player)
                && !net.schwarz.rotasutils.server.ConfigService.commands(quest)
                        .equals(net.schwarz.rotasutils.server.ConfigService.commands(previous))) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.commands_op4"));
            return null;
        }
        if (publish) {
            if (data.serverSettings().validateOnPublish()) {
                List<Validation.Issue> issues = Validation.validateQuest(data, quest);
                boolean blocking = issues.stream().anyMatch(i -> i.severity() == Validation.Severity.ERROR);
                if (blocking) {
                    RotasNetwork.sendValidation(player, issues);
                    RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.fix_errors"));
                    return null;
                }
            }
            quest.setPublished(true);
            quest.bumpVersion();
        }
        data.putQuest(quest);
        // Keep every board's mirror of the quest's board list in sync.
        for (BoardConfig board : data.boards().values()) {
            boolean shouldList = quest.boardIds().contains(board.id());
            if (shouldList && !board.questIds().contains(quest.id())) {
                board.questIds().add(quest.id());
            } else if (!shouldList) {
                board.questIds().remove(quest.id());
            }
        }
        data.audit(player.getGameProfile().getName() + (publish ? " published " : " saved ") + quest.id());
        RotasNetwork.feedback(player, true, publish ? ThaiText.t("rotasutils.msg.sa.quest_published") : ThaiText.t("rotasutils.msg.sa.draft_saved"));
        RotasNetwork.syncContent(player);
        return quest;
    }

    private static BoardConfig saveBoard(ServerPlayer player, RotasData data, CompoundTag payload) {
        BoardConfig board = BoardConfig.load(payload.getCompound("board"));
        if (board.id().isEmpty()) {
            board.setId(Ids.unique(board.name(), data.boards().keySet()));
        }
        // Rotation, access and permission settings need admin level (op 2-4 by default). Anyone
        // below keeps the stored values for those fields even if the client sent others.
        if (!BoardService.isAdmin(player, data)) {
            BoardConfig stored = data.board(board.id());
            if (stored != null) {
                restoreOperatorOnlyFields(board, stored);
            }
        }
        data.putBoard(board);
        data.audit(player.getGameProfile().getName() + " saved board " + board.id());
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.board_saved"));
        RotasNetwork.syncContent(player);
        return board;
    }

    private static NpcDef saveNpc(ServerPlayer player, RotasData data, CompoundTag payload) {
        NpcDef npc = NpcDef.load(payload.getCompound("npc"));
        if (npc.id().isEmpty()) {
            npc.setId(Ids.unique(npc.name(), data.npcs().keySet()));
        }
        // Two NPCs answering for one entity would make the click ambiguous, so the newer
        // binding wins and the older one is released rather than silently shadowed.
        if (npc.bound()) {
            for (NpcDef other : data.npcs().values()) {
                if (!other.id().equals(npc.id()) && npc.entityUuid().equals(other.entityUuid())) {
                    other.setEntityUuid("");
                }
            }
        }
        data.putNpc(npc);
        var entity = net.schwarz.rotasutils.server.NpcService.findEntity(player.server, npc);
        if (entity != null) {
            net.schwarz.rotasutils.server.NpcService.applyEntityOptions(npc, entity);
        }
        data.audit(player.getGameProfile().getName() + " saved NPC " + npc.id());
        // A direct save supersedes any staged draft for this character.
        data.configHistory().discard(player.getUUID().toString(), "npc/" + npc.id());
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.npc_saved"));
        // Markers and dialogue read the synced NPC, so every player gets the change, not only the editor.
        syncContentToEveryone(player);
        return npc;
    }

    /** Runs {@code consumer} with the NPC named in the payload, or reports it is gone. */
    /** The stable NPC a horse screen was opened from, or "" when it came from the whistle or a command. */
    private static String stableNpc(RotasData data, CompoundTag payload) {
        NpcDef npc = data.npc(payload.getString("npc"));
        return npc != null && npc.enabled() && npc.role() == NpcDef.Role.STABLE ? npc.id() : "";
    }

    /** Market and NPC sales happen at a stable NPC the player is standing next to. */
    private static void atStable(ServerPlayer player, RotasData data, CompoundTag payload, Runnable action) {
        if (payload.getString("npc").isBlank()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.horse.need_npc"));
            return;
        }
        withNpc(player, data, payload, npc -> {
            if (npc.role() != NpcDef.Role.STABLE) {
                RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.horse.need_npc"));
                return;
            }
            action.run();
        });
    }

    private static void horseResult(ServerPlayer player, RotasData data, CompoundTag payload,
                                     net.schwarz.rotasutils.server.horse.HorseService.Result result, String tab) {
        RotasNetwork.feedback(player, result.ok(), result.message());
        RotasNetwork.syncProgress(player);
        if (!payload.getBoolean("close")) {
            net.schwarz.rotasutils.server.horse.HorseService.open(player, stableNpc(data, payload), tab, null);
        }
    }

    private static void withNpc(ServerPlayer player, RotasData data, CompoundTag payload,
                                java.util.function.Consumer<NpcDef> consumer) {
        NpcDef npc = data.npc(payload.getString("npc"));
        if (npc == null || !npc.enabled()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.character_gone"));
            return;
        }
        String blocked = net.schwarz.rotasutils.server.NpcConversations.blockedReason(player, npc);
        if (blocked != null) { RotasNetwork.feedback(player, false, blocked); return; }
        consumer.accept(npc);
    }

    /**
     * Copies the operator-only board fields from {@code stored} onto {@code incoming}.
     *
     * <p>These are the settings that decide who may use a board and what they may do
     * with it, so a plain content admin cannot change them.
     */
    public static void restoreOperatorOnlyFields(BoardConfig incoming, BoardConfig stored) {
        incoming.setVisible(stored.visible());
        incoming.setRotation(stored.rotation());
        incoming.setRotationSlots(stored.rotationSlots());
        incoming.setMinPlayerLevel(stored.minPlayerLevel());
        incoming.setMaxPlayerLevel(stored.maxPlayerLevel());
        incoming.setRequiredClearance(stored.requiredClearance());
        incoming.setRequiredDimension(stored.requiredDimension());
        incoming.setScheduleStartHour(stored.scheduleStartHour());
        incoming.setScheduleEndHour(stored.scheduleEndHour());
        incoming.availability().clear();
        incoming.availability().addAll(stored.availability());
        incoming.setAllowAccept(stored.allowAccept());
        incoming.setAllowTurnIn(stored.allowTurnIn());
        incoming.setAllowClaim(stored.allowClaim());
        incoming.setAllowAbandon(stored.allowAbandon());
        incoming.setAllowPartyCreate(stored.allowPartyCreate());
        incoming.setAllowPartyJoin(stored.allowPartyJoin());
        incoming.setShowUnavailable(stored.showUnavailable());
        incoming.setHideLocked(stored.hideLocked());
        incoming.setAnnounceFeatured(stored.announceFeatured());
        incoming.setAnnounceEmergency(stored.announceEmergency());
        incoming.setShowQuestMarkers(stored.showQuestMarkers());
        incoming.setInteractionDistance(stored.interactionDistance());
    }

    private static BoardConfig firstAssignedBoard(RotasData data, QuestDef quest) {
        for (String boardId : quest.boardIds()) {
            BoardConfig board = data.board(boardId);
            if (board != null) {
                return board;
            }
        }
        return null;
    }

    /** Keeps deleted nodes' point costs so a later reset can still refund them. */
    public static void preserveRemovedNodes(RotasData data, SkillCategory previous, SkillCategory updated) {
        for (SkillNode oldNode : previous.nodes().values()) {
            if (updated.node(oldNode.id()) != null) {
                continue;
            }
            for (PlayerProgress progress : data.allPlayers()) {
                int rank = progress.skillRank(oldNode.id());
                if (rank <= 0) {
                    continue;
                }
                int refundable = 0;
                for (int i = 0; i < rank; i++) {
                    refundable += oldNode.costForRank(i);
                }
                progress.legacySkills().put(oldNode.id(), refundable);
                progress.markDirty();
            }
        }
    }

    /**
     * Refunds the category for every stored player, not only those online: once the category is
     * removed its nodes no longer resolve, so an offline player's spent points could not be recovered.
     */
    private static void refundCategory(ServerPlayer actor, RotasData data, SkillCategory category) {
        for (PlayerProgress progress : data.allPlayers()) {
            SkillService.refund(progress, data, category.id());
        }
        data.setDirty();
        for (ServerPlayer online : actor.server.getPlayerList().getPlayers()) {
            SkillService.recalculate(online, data);
            RotasNetwork.syncProgress(online);
        }
    }

    /**
     * Replaces the live settings with the ones the admin screen sent.
     *
     * <p>This copies through {@code save()} + {@code load()} rather than a hand-written list of
     * setters. The old list silently dropped every field nobody remembered to add to it - the
     * waystone costs and the monster-drop toggle were editable in the UI and thrown away on save.
     * The round trip carries whatever {@code ServerSettings} persists, and {@code load} applies the
     * same clamps the setters do, so a new field is configurable the moment it is saved.</p>
     */
    private static void copySettings(net.schwarz.rotasutils.data.ServerSettings source, RotasData data) {
        data.setServerSettings(net.schwarz.rotasutils.data.ServerSettings.load(source.save()));
    }

    private static void adminAudit(ServerPlayer actor, ServerPlayer target, String change) {
        RotasData.get(actor.server).audit(actor.getGameProfile().getName() + " " + change + " for "
                + target.getGameProfile().getName());
    }

    private static void withTarget(ServerPlayer actor, RotasData data, CompoundTag payload,
                                   java.util.function.Consumer<ServerPlayer> consumer) {
        String name = payload.getString("player");
        ServerPlayer target = actor.server.getPlayerList().getPlayerByName(name);
        if (target == null && payload.hasUUID("player_uuid")) {
            target = actor.server.getPlayerList().getPlayer(payload.getUUID("player_uuid"));
        }
        if (target == null) {
            RotasNetwork.feedback(actor, false, ThaiText.t("rotasutils.msg.party.offline"));
            return;
        }
        consumer.accept(target);
        data.setDirty();
    }

    private static void deliverItem(ServerPlayer player, RotasData data, CompoundTag payload) {
        QuestService.ActionResult result = ObjectiveEngine.deliver(player, data,
                payload.getString("quest"), payload.getInt("objective"));
        RotasNetwork.feedback(player, result.success(), result.message());
        if (result.success()) {
            RotasNetwork.syncProgress(player);
        }
    }

    private static void claimChoice(ServerPlayer player, RotasData data, CompoundTag payload) {
        QuestDef quest = data.quest(payload.getString("quest"));
        if (quest == null) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.quest.gone"));
            return;
        }
        int index = payload.getInt("reward");
        if (index < 0 || index >= quest.rewards().size()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.reward_gone"));
            return;
        }
        Reward reward = quest.rewards().get(index);
        if (reward.mode() != Reward.Mode.PLAYER_CHOICE) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.reward_not_choice"));
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        int completions = progress.completionCount(quest.id());
        // Choice pools are claimed per completion; a quest never completed has nothing to claim.
        if (completions <= 0) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.complete_first"));
            return;
        }
        int completionIndex = completions - 1;
        boolean granted = RewardService.grantChoice(player, data, reward,
                RewardService.Context.quest(quest, completionIndex, completionIndex == 0, 1.0, 0));
        RotasNetwork.feedback(player, granted,
                granted ? ThaiText.t("rotasutils.msg.sa.reward_claimed") : ThaiText.t("rotasutils.msg.sa.reward_already"));
        RotasNetwork.syncProgress(player);
    }

    private static void testCompleteObjective(ServerPlayer player, RotasData data, CompoundTag payload) {
        QuestDef quest = data.quest(payload.getString("quest"));
        PlayerProgress progress = data.progress(player.getUUID());
        ActiveQuest active = progress.active(payload.getString("quest"));
        if (quest == null || active == null) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.sa.accept_first"));
            return;
        }
        ObjectiveEngine.migrate(active, quest);
        int index = payload.getInt("objective");
        if (index < 0 || index >= quest.objectives().size()) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.obj.no_objective"));
            return;
        }
        active.setProgress(index, quest.objectives().get(index).requiredAmount());
        active.setComplete(index, true);
        if (QuestService.allRequiredComplete(quest, active)) {
            active.setTurnInReady(true);
        }
        progress.markDirty();
        data.setDirty();
        RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.msg.sa.objective_forced"));
        RotasNetwork.syncProgress(player);
    }

    /** Appends one area to a zone and reports the new shape count. */
    private static String addArea(ServerPlayer player, RotasData data,
                                  net.schwarz.rotasutils.core.ZoneDef zone,
                                  net.schwarz.rotasutils.core.ZoneArea area) {
        if (zone.areas().size() >= net.schwarz.rotasutils.core.ZoneDef.MAX_AREAS) {
            return ThaiText.t("rotasutils.msg.sa.zone_max_areas", net.schwarz.rotasutils.core.ZoneDef.MAX_AREAS);
        }
        net.schwarz.rotasutils.core.ZoneDef updated = zone.withArea(area);
        data.putZone(updated);
        data.audit(player.getGameProfile().getName() + " added an area to zone " + updated.id());
        syncZones(player);
        return ThaiText.t("rotasutils.msg.sa.zone_area_count", updated.name(), updated.areas().size());
    }

    /** Zones live in content; refresh every admin so open editors see the new shape at once. */
    private static void syncZones(ServerPlayer source) {
        for (ServerPlayer online : source.server.getPlayerList().getPlayers()) {
            RotasNetwork.syncContent(online);
        }
    }
}
