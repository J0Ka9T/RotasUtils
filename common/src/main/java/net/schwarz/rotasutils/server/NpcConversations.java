package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.Affection;
import net.schwarz.rotasutils.core.NpcInteractions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.progress.PlayerProgress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-owned conversations. Clients submit only the current nonce and offered response id. */
public final class NpcConversations {
    private record Option(String type, String target, NpcInteractions.Choice choice, NpcInteractions.Gift gift) {}
    private record Session(String npc, String node, int page, UUID nonce, long expires, CompoundTag definition,
                           NpcDef preview, Map<String, Option> options) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private NpcConversations() {}

    public static void clear() { SESSIONS.clear(); }
    public static void close(UUID player) { SESSIONS.remove(player); }

    public static String blockedReason(ServerPlayer player, NpcDef npc) {
        if (!npc.enabled() || !npc.bound()) return ThaiText.t("rotasutils.msg.npc.unavailable");
        Entity entity;
        try { entity = player.serverLevel().getEntity(UUID.fromString(npc.entityUuid())); }
        catch (IllegalArgumentException error) { return ThaiText.t("rotasutils.msg.npc.invalid_binding"); }
        if (entity == null || !entity.isAlive() || entity.level() != player.level()) return ThaiText.t("rotasutils.msg.npc.not_nearby");
        double reach = npc.interactionDistance();
        if (player.distanceToSqr(entity) > reach * reach || !player.hasLineOfSight(entity)) return ThaiText.t("rotasutils.msg.npc.move_closer", npc.name());
        int level = RotasData.get(player.server).progress(player.getUUID()).level();
        return level < npc.requiredLevel() ? ThaiText.t("rotasutils.msg.npc.requires_level", npc.requiredLevel(), level) : null;
    }

    public static void open(ServerPlayer player, RotasData data, NpcDef npc) {
        show(player, data, npc, npc.interactions().start(), "", false, 0);
    }

    public static void preview(ServerPlayer player, RotasData data, NpcDef draft) {
        if (!BoardService.isAdmin(player, data)) return;
        NpcDef preview = NpcDef.load(draft.save());
        if (preview.interactions() == null) preview.setInteractionJson("{}");
        show(player, data, preview, preview.interactions().start(), ThaiText.t("rotasutils.msg.npc.preview"), true, 0);
    }

    private static void show(ServerPlayer player, RotasData data, NpcDef npc, String nodeId, String feedback, boolean preview, int requestedPage) {
        show(player, data, npc, nodeId, feedback, preview, requestedPage, "");
    }

    /** {@code say}, when not blank, is what the NPC says instead of the node's lines (a flirt's answer). */
    private static void show(ServerPlayer player, RotasData data, NpcDef npc, String nodeId, String feedback, boolean preview,
                             int requestedPage, String say) {
        String blocked = preview ? null : blockedReason(player, npc);
        if (blocked != null) { RotasNetwork.feedback(player, false, blocked); return; }
        var definition = npc.interactions();
        if (definition == null) return;
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().expires() < System.nanoTime());
        if (!SESSIONS.containsKey(player.getUUID()) && SESSIONS.size() >= 1024) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.npc.too_many")); return;
        }
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean("preview", preview);
        snapshot.putString("npc", npc.id()); snapshot.putString("name", npc.name());
        snapshot.putString("title", npc.title()); snapshot.putString("feedback", feedback);
        ListTag rows = new ListTag(); Map<String, Option> options = new LinkedHashMap<>();
        var node = definition.nodes().get(nodeId);
        PlayerProgress progress = data.progress(player.getUUID());
        var romance = definition.romance();
        int affection = Affection.parse(progress.questVariables().get(Affection.key(npc.id())));
        // What the NPC says, one page per line: the client shows them one at a time.
        List<String> pages = new ArrayList<>();
        if (!say.isBlank()) pages.add(say);
        else if (definition.dialogue() && node != null) {
            if (romance.enabled() && nodeId.equals(definition.start())) {
                String greeting = romance.greetings().get(Affection.tier(affection).key());
                if (greeting != null) pages.add(greeting);
            }
            pages.addAll(node.lines());
        } else pages.add(npc.lineFor(NpcService.stateFor(player, data, npc)));
        ListTag pageTags = new ListTag();
        for (String page : pages) pageTags.add(net.minecraft.nbt.StringTag.valueOf(fill(page, player, npc)));
        snapshot.put("pages", pageTags);
        snapshot.putString("speech", fill(String.join("\n", pages), player, npc));
        snapshot.putBoolean("romance", romance.enabled());
        snapshot.putInt("affection", affection);
        snapshot.putString("tier", Affection.tier(affection).key());
        if (definition.dialogue() && node != null) {
            for (var choice : node.choices()) {
                // Quest and flag branches are mutually exclusive, so a failing one is hidden. Level
                // and affection gates stay visible, locked, so the player knows what to work toward.
                if (hiddenFailure(progress, choice.when())) continue;
                String reason = gateReason(progress, npc, choice.when());
                String key = "choice:" + choice.id();
                row(rows, key, fill(choice.text(), player, npc), reason == null ? ThaiText.t("rotasutils.msg.npc.choose") : reason, reason == null);
                options.put(key, new Option("choice", choice.id(), choice, null));
            }
        }
        if (romance.enabled()) {
            int left = romance.flirtsPerDay() - Affection.flirtsToday(progress.questVariables().get(Affection.dayKey(npc.id())), today());
            row(rows, "flirt", ThaiText.t("rotasutils.msg.npc.flirt"),
                    left > 0 ? ThaiText.t("rotasutils.msg.npc.flirts_left", left) : ThaiText.t("rotasutils.msg.npc.flirts_none"), left > 0);
            options.put("flirt", new Option("flirt", "", null, null));
        }
        if (definition.quests()) {
            for (String id : npc.questIds()) {
                var quest = data.quest(id); if (quest == null || !quest.published()) continue;
                var active = data.progress(player.getUUID()).active(id);
                if (active == null && !QuestService.canSee(player, data, quest)) continue;
                String action = active == null ? "accept" : active.turnInReady() ? "turn_in" : "journal";
                String reason = active == null ? QuestService.blockedReason(player, data, quest) : null;
                String label = ThaiText.t(action.equals("accept") ? "rotasutils.msg.npc.accept"
                        : action.equals("turn_in") ? "rotasutils.msg.npc.turn_in" : "rotasutils.msg.npc.track", quest.name());
                row(rows, "quest:" + id, label, reason == null ? quest.shortDescription() : reason, reason == null);
                options.put("quest:" + id, new Option(action, id, null, null));
            }
        }
        if (definition.acceptsGifts()) for (var gift : definition.gifts()) {
            String detail = gift.count() + " x " + String.join(" / ", gift.items()) + " | " + gift.repeat();
            boolean available = available(data.progress(player.getUUID()), npc, "gift:" + gift.id(), gift.repeat(), gift.cooldown());
            row(rows, "gift:" + gift.id(), ThaiText.t("rotasutils.msg.npc.give_gift", gift.id().replace('_', ' ')),
                    available ? detail : ThaiText.t("rotasutils.msg.npc.gift_given", gift.repeat()), available);
            options.put("gift:" + gift.id(), new Option("gift", gift.id(), null, gift));
        }
        if (definition.shop() && npc.hasShop()) {
            row(rows, "shop", ThaiText.t("rotasutils.msg.npc.browse_shop"), npc.trades().isEmpty() ? npc.merchantId()
                    : ThaiText.t("rotasutils.npc.trade_count", npc.trades().size()), true);
            options.put("shop", new Option("shop", npc.merchantId(), null, null));
        }
        if (npc.role() == NpcDef.Role.CRAFTER && npc.crafterService() != null) {
            row(rows, "crafter", ThaiText.t("rotasutils.msg.npc.commission"), ThaiText.t("rotasutils.msg.npc.commission_detail"), true);
            options.put("crafter", new Option("crafter", "", null, null));
        }
        if (!npc.boardId().isBlank()) {
            row(rows, "board", ThaiText.t("rotasutils.msg.npc.browse_board"), npc.boardId(), true);
            options.put("board", new Option("board", npc.boardId(), null, null));
        }
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (rows.size() - 1) / 60)));
        ListTag visibleRows = new ListTag();
        for (int i = page * 60; i < Math.min(rows.size(), (page + 1) * 60); i++) visibleRows.add(rows.get(i));
        if (page > 0) {
            row(visibleRows, "previous_page", ThaiText.t("rotasutils.msg.npc.previous"), "", true);
            options.put("previous_page", new Option("page", Integer.toString(page - 1), null, null));
        }
        if ((page + 1) * 60 < rows.size()) {
            row(visibleRows, "next_page", ThaiText.t("rotasutils.msg.npc.more"), "", true);
            options.put("next_page", new Option("page", Integer.toString(page + 1), null, null));
        }
        UUID nonce = UUID.randomUUID(); snapshot.putUUID("nonce", nonce); snapshot.put("rows", visibleRows);
        Set<String> offered = new HashSet<>();
        for (var value : visibleRows) offered.add(((CompoundTag) value).getString("id"));
        options.keySet().retainAll(offered);
        SESSIONS.put(player.getUUID(), new Session(npc.id(), nodeId, page, nonce, System.nanoTime() + 120_000_000_000L,
                npc.save(), preview ? npc : null, Map.copyOf(options)));
        RotasNetwork.openScreen(player, "npc_conversation", snapshot);
    }

    private static void row(ListTag rows, String id, String label, String detail, boolean enabled) {
        CompoundTag row = new CompoundTag(); row.putString("id", id); row.putString("label", label);
        row.putString("detail", detail); row.putBoolean("enabled", enabled); rows.add(row);
    }

    public static void respond(ServerPlayer player, RotasData data, CompoundTag request) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.expires() < System.nanoTime() || !request.hasUUID("nonce")
                || !session.nonce().equals(request.getUUID("nonce"))) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.npc.expired")); return;
        }
        boolean preview = session.preview() != null;
        if (preview && !BoardService.isAdmin(player, data)) {
            close(player.getUUID()); RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.npc.perm_removed")); return;
        }
        NpcDef npc = preview ? session.preview() : data.npc(session.npc());
        if (npc == null || npc.interactions() == null || !session.definition().equals(npc.save())) {
            close(player.getUUID()); RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.npc.changed")); return;
        }
        String blocked = preview ? null : blockedReason(player, npc);
        if (blocked != null) { close(player.getUUID()); RotasNetwork.feedback(player, false, blocked); return; }
        Option option = session.options().get(request.getString("response"));
        if (option == null) { RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.npc.not_offered")); return; }
        close(player.getUUID()); // Consume before any mutation; an old packet cannot repeat a reward.
        if (option.type().equals("page")) {
            show(player, data, npc, session.node(), "", preview, Integer.parseInt(option.target()));
            return;
        }
        if (preview) {
            String next = session.node();
            if (option.choice() != null && !option.choice().next().isBlank()) next = option.choice().next();
            String sample = option.type().equals("flirt") ? flirtLine(npc.interactions().romance(), player.getRandom().nextBoolean(), player) : "";
            show(player, data, npc, next, ThaiText.t("rotasutils.msg.npc.preview_done"), true, 0, fill(sample, player, npc));
            return;
        }
        String next = session.node(), message = "", say = "";
        try {
            switch (option.type()) {
                case "gift" -> message = give(player, data, npc, option.gift());
                case "accept", "turn_in" -> message = quest(player, data, npc, option.type(), option.target());
                case "journal" -> { RotasNetwork.openScreen(player, "journal", new CompoundTag()); return; }
                case "shop" -> { NpcService.openShop(player, data, npc); return; }
                case "board" -> { NpcService.openBoard(player, data, npc); return; }
                case "crafter" -> { CrafterService.open(player, data, npc); return; }
                case "flirt" -> {
                    String[] result = flirt(player, data, npc);
                    say = result[0];
                    message = result[1];
                }
                case "choice" -> {
                    var choice = option.choice();
                    if (hiddenFailure(data.progress(player.getUUID()), choice.when()))
                        throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.quest_state"));
                    String reason = gateReason(data.progress(player.getUUID()), npc, choice.when());
                    if (reason != null) throw new IllegalStateException(reason);
                    var action = choice.action();
                    switch (action.type()) {
                        case "start_quest", "turn_in" -> message = quest(player, data, npc, action.type().equals("start_quest") ? "accept" : "turn_in", action.target());
                        case "reward", "event" -> {
                            reward(player, data, npc, "choice:" + session.node() + ":" + choice.id(), action.repeat(), action.cooldown(), action.rewards(), null, 0);
                            if (action.type().equals("event")) RpgKernel.emit(player, action.target(), session.nonce().toString(), Map.of("event.npc", npc.id()));
                            message = ThaiText.t("rotasutils.msg.npc.reward_received");
                        }
                        case "shop" -> {
                            if (!npc.interactions().shop() || !npc.hasShop()) throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.shop_disabled"));
                            conversationEvent(player, data, npc, session.node(), choice.id(), true);
                            NpcService.openShop(player, data, npc); return;
                        }
                        default -> { }
                    }
                    if (!choice.next().isBlank()) next = choice.next();
                    var destination = npc.interactions().nodes().get(next);
                    boolean complete = choice.next().isBlank() || destination == null || destination.choices().isEmpty();
                    conversationEvent(player, data, npc, session.node(), choice.id(), complete);
                }
                default -> throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.unknown_response"));
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            message = error.getMessage();
        }
        RotasNetwork.syncProgress(player);
        show(player, data, npc, next, message, false, next.equals(session.node()) ? session.page() : 0, say);
    }

    /**
     * One flirt: spend today's allowance, roll the odds (better the fonder the NPC already is), move
     * affection up on a success or a little down on a miss, and answer. Returns {what the NPC says,
     * a status line} - the status announces a new stage of the relationship.
     */
    private static String[] flirt(ServerPlayer player, RotasData data, NpcDef npc) {
        var romance = npc.interactions().romance();
        if (!romance.enabled()) throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.not_offered"));
        PlayerProgress progress = data.progress(player.getUUID());
        String key = Affection.key(npc.id()), dayKey = Affection.dayKey(npc.id());
        String spent = progress.questVariables().get(dayKey);
        long day = today();
        if (Affection.flirtsToday(spent, day) >= romance.flirtsPerDay()) {
            return new String[]{fill(romance.tired().isBlank() ? ThaiText.t("rotasutils.dialogue.default.flirt_tired") : romance.tired(), player, npc), ""};
        }
        int before = Affection.parse(progress.questVariables().get(key));
        boolean success = player.getRandom().nextDouble() < Affection.flirtChance(before, romance.baseChancePercent() / 100.0);
        int after = Affection.clamp(before + (success ? romance.successGain() : -romance.failLoss()));
        try (var transaction = new PlayerRecordTransaction(data, progress, player)) {
            transaction.variable(key, Integer.toString(after));
            transaction.variable(dayKey, Affection.recordFlirt(spent, day));
            transaction.commit();
        }
        hearts(player, npc, success);
        String status = "";
        if (Affection.tier(after).ordinal() > Affection.tier(before).ordinal()) {
            status = ThaiText.t("rotasutils.msg.npc.affection_up", npc.name(),
                    ThaiText.t("rotasutils.affection.tier." + Affection.tier(after).key()));
        }
        return new String[]{fill(flirtLine(romance, success, player), player, npc), status};
    }

    private static String flirtLine(NpcInteractions.Romance romance, boolean success, ServerPlayer player) {
        List<String> pool = success ? romance.success() : romance.fail();
        if (pool.isEmpty()) return ThaiText.t(success ? "rotasutils.dialogue.default.flirt_success" : "rotasutils.dialogue.default.flirt_fail");
        return pool.get(player.getRandom().nextInt(pool.size()));
    }

    /** Hearts over the NPC for a flirt that landed; a puff of smoke for one that did not. */
    private static void hearts(ServerPlayer player, NpcDef npc, boolean success) {
        if (npc.entityUuid() == null || npc.entityUuid().isEmpty()) return;
        Entity entity;
        try { entity = player.serverLevel().getEntity(UUID.fromString(npc.entityUuid())); }
        catch (IllegalArgumentException malformed) { return; }
        if (entity == null) return;
        double y = entity.getY() + entity.getBbHeight() + 0.3;
        player.serverLevel().sendParticles(success ? net.minecraft.core.particles.ParticleTypes.HEART
                : net.minecraft.core.particles.ParticleTypes.SMOKE, entity.getX(), y, entity.getZ(), success ? 5 : 6, 0.3, 0.2, 0.3, 0.02);
        player.serverLevel().playSound(null, entity.blockPosition(), success
                ? net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME : net.minecraft.sounds.SoundEvents.VILLAGER_NO,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, success ? 1.5f : 1.0f);
    }

    private static long today() {
        return QuestService.nowSeconds() / 86400;
    }

    /** Fills {player} and {npc} in a line the admin wrote. */
    static String fill(String text, ServerPlayer player, NpcDef npc) {
        if (text == null || text.indexOf('{') < 0) return text == null ? "" : text;
        return text.replace("{player}", player.getGameProfile().getName()).replace("{npc}", npc.name());
    }

    private static String quest(ServerPlayer player, RotasData data, NpcDef npc, String action, String id) {
        if (!npc.interactions().quests() || !npc.questIds().contains(id)) throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.quest_not_offered"));
        var result = action.equals("accept") ? QuestService.accept(player, data, id, npc.boardId(), true)
                : QuestService.turnIn(player, data, id, npc.boardId());
        if (!result.success()) throw new IllegalStateException(result.message());
        return result.message();
    }

    private static void conversationEvent(ServerPlayer player, RotasData data, NpcDef npc, String node, String choice, boolean complete) {
        // An NPC previewed from the editor has no bound entity yet, and blockedReason (which would
        // have caught this) is skipped in preview mode - UUID.fromString("") would throw here.
        if (npc.entityUuid() == null || npc.entityUuid().isEmpty()) { return; }
        net.minecraft.world.entity.Entity entity;
        try {
            entity = player.serverLevel().getEntity(UUID.fromString(npc.entityUuid()));
        } catch (IllegalArgumentException malformed) {
            return;
        }
        if (entity == null) return;
        for (var kind : List.of(net.schwarz.rotasutils.quest.objective.EventKind.DIALOGUE_CHOICE,
                net.schwarz.rotasutils.quest.objective.EventKind.TALK_NPC)) {
            ObjectiveEngine.handle(player, data, new QuestEvent(kind).entityUuid(entity.getUUID())
                    .entityType(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                    .entityName(entity.getName().getString()).dimension(player.level().dimension().location().toString())
                    .dialogueId(node).choiceId(choice).dialogueComplete(complete));
        }
    }

    private static String give(ServerPlayer player, RotasData data, NpcDef npc, NpcInteractions.Gift gift) {
        if (!npc.interactions().acceptsGifts()) throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.gifts_disabled"));
        ItemStack held = player.getMainHandItem();
        boolean matches = !held.isEmpty() && gift.items().stream().anyMatch(selector -> selector.startsWith("#")
                ? held.is(TagKey.create(Registries.ITEM, new ResourceLocation(selector.substring(1))))
                : BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(selector));
        if (!matches) throw new IllegalStateException(npc.interactions().invalidGift());
        if (held.getCount() < gift.count()) throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.hold_items", gift.count()));
        reward(player, data, npc, "gift:" + gift.id(), gift.repeat(), gift.cooldown(), gift.rewards(), held, gift.count());
        return gift.success();
    }

    private static void reward(ServerPlayer player, RotasData data, NpcDef npc, String key, String repeat, int cooldown,
                               List<NpcInteractions.Grant> grants, ItemStack payment, int count) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (!available(progress, npc, key, repeat, cooldown)) throw new IllegalStateException(ThaiText.t("rotasutils.msg.npc.already_received", repeat));
        int xp = 0;
        int affectionDelta = 0;
        ItemStack original = payment == null ? ItemStack.EMPTY : payment.copy();
        try (var transaction = new PlayerRecordTransaction(data, progress, player)) {
            for (var grant : grants) switch (grant.type()) {
                case "item" -> {
                    int max = BuiltInRegistries.ITEM.get(new ResourceLocation(grant.id())).getMaxStackSize();
                    for (int remaining = grant.amount(); remaining > 0; remaining -= max)
                        transaction.item(grant.id(), Math.min(remaining, max));
                }
                case "currency" -> transaction.currency(grant.id(), grant.amount());
                case "reputation" -> transaction.reputation(grant.id(), grant.amount());
                case "quest_unlock" -> transaction.unlock(grant.id());
                case "flag" -> transaction.variable(grant.id(), Integer.toString(grant.amount()));
                case "affection" -> affectionDelta += grant.amount();
                case "xp" -> xp = Math.addExact(xp, grant.amount());
                default -> throw new IllegalArgumentException(ThaiText.t("rotasutils.msg.npc.invalid_reward"));
            }
            if (affectionDelta != 0) {
                String affectionKey = Affection.key(npc.id());
                int now = Affection.parse(progress.questVariables().get(affectionKey));
                transaction.variable(affectionKey, Integer.toString(Affection.clamp(now + affectionDelta)));
            }
            transaction.variable(receiptKey(npc.id(), key), Long.toString(QuestService.nowSeconds()));
            if (payment != null) payment.shrink(count);
            try { transaction.commit(); }
            catch (RuntimeException error) {
                if (!transaction.committed() && payment != null) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, original);
                throw error;
            }
        }
        if (xp > 0) ProgressService.addExperience(player, data, xp, true);
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }

    private static boolean available(PlayerProgress player, NpcDef npc, String key, String repeat, int cooldown) {
        return NpcInteractions.available(repeat, cooldown, player.questVariables().get(receiptKey(npc.id(), key)), QuestService.nowSeconds());
    }
    private static String receiptKey(String npc, String key) {
        return "rpg.npc." + UUID.nameUUIDFromBytes((npc + "|" + key).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
    /** A gate the player can see and work toward: level, or how fond the NPC is of them. */
    private static String gateReason(PlayerProgress player, NpcDef npc, NpcInteractions.Condition when) {
        if (player.level() < when.level()) return ThaiText.t("rotasutils.msg.npc.requires_level", when.level(), player.level());
        if (Affection.parse(player.questVariables().get(Affection.key(npc.id()))) < when.minAffection()) {
            return ThaiText.t("rotasutils.msg.npc.needs_affection",
                    ThaiText.t("rotasutils.affection.tier." + Affection.tier(when.minAffection()).key()));
        }
        return null;
    }

    /** A branch that does not apply at all (wrong quest state, flag not set): hidden, not locked. */
    private static boolean hiddenFailure(PlayerProgress player, NpcInteractions.Condition when) {
        return conditionReason(player, when) != null;
    }

    private static String conditionReason(PlayerProgress player, NpcInteractions.Condition when) {
        if (!when.flag().isEmpty() && !when.value().equals(player.questVariables().get(when.flag()))) return ThaiText.t("rotasutils.msg.npc.step_needed");
        var active = player.active(when.quest());
        boolean matches = switch (when.state()) {
            case "not_started" -> active == null && player.completionCount(when.quest()) == 0;
            case "in_progress" -> active != null && !active.turnInReady();
            case "ready" -> active != null && active.turnInReady();
            case "completed" -> player.completionCount(when.quest()) > 0;
            default -> true;
        };
        return matches ? null : ThaiText.t("rotasutils.msg.npc.quest_state");
    }
}
