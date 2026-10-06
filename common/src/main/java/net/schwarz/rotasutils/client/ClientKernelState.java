package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.util.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class ClientKernelState {
    public record MonsterEntry(String id, String name, int levelMin, int levelMax, boolean manualOnly,
                               int tiers, int affixes, String boss, String loot, String body) { }

    public record BossEntry(String id, String label, int phases, int arenaRadius, int enrageSeconds,
                            double minimumShare) { }

    public record ItemEntry(String id, String item, int levelMin, int levelMax, int modifiers,
                            int minPlayerLevel, String slot, String set) { }

    public record LootEntry(String id, int minRolls, int maxRolls, int entries) { }

    public record Objective(String key, String label, int count) { }

    public record Stage(String label, List<Objective> objectives) { }

    public record QuestEntry(String id, String label, int stageCount, String reset, int bountyLimit,
                             List<Stage> stages) { }

    public record TradeEntry(String key, String label, String result, int resultCount, int itemLevel,
                             int stock, int perPlayerLimit, String cost, Map<String, Long> costs) { }

    public record MerchantEntry(String id, String label, List<TradeEntry> trades) { }

    public record QuestState(String id, int stage, boolean available, boolean claimable, long completed,
                             Map<String, Integer> objectives) { }

    public record TradeState(int remaining, int purchased, boolean available) { }

    private static boolean ready;
    private static String contentHash = "";
    private static long revision;
    private static boolean truncated;
    private static boolean canEdit;
    private static int level = 1;
    private static long totalXp;
    private static int statPoints;
    private static int mailbox;
    private static Map<String, Long> wallet = Map.of();
    private static List<MonsterEntry> monsters = List.of();
    private static List<BossEntry> bosses = List.of();
    private static List<ItemEntry> items = List.of();
    private static List<LootEntry> loot = List.of();
    private static List<QuestEntry> quests = List.of();
    private static List<MerchantEntry> merchants = List.of();
    private static Map<String, QuestState> questState = Map.of();
    private static Map<String, Map<String, TradeState>> tradeState = Map.of();
    private static String previewTable = "";
    private static List<String> previewLines = List.of();

    private ClientKernelState() {
    }

    public static void apply(CompoundTag tag) {
        ready = tag.getBoolean("ready");
        contentHash = tag.getString("hash");
        revision = tag.getLong("revision");
        if (!ready) {
            monsters = List.of();
            bosses = List.of();
            items = List.of();
            loot = List.of();
            quests = List.of();
            merchants = List.of();
            questState = Map.of();
            tradeState = Map.of();
            return;
        }
        CompoundTag catalog = tag.getCompound("catalog");
        truncated = catalog.getBoolean("truncated");

        List<MonsterEntry> monsterList = new ArrayList<>();
        each(catalog, "monsters", entry -> monsterList.add(new MonsterEntry(entry.getString("id"),
                entry.getString("name"), entry.getInt("level_min"), entry.getInt("level_max"),
                entry.getBoolean("manual_only"), entry.getInt("tiers"), entry.getInt("affixes"),
                entry.getString("boss"), entry.getString("loot"), entry.getString("body"))));
        monsters = List.copyOf(monsterList);

        List<BossEntry> bossList = new ArrayList<>();
        each(catalog, "bosses", entry -> bossList.add(new BossEntry(entry.getString("id"),
                entry.getString("label"), entry.getInt("phases"), entry.getInt("arena"),
                entry.getInt("enrage"), entry.getDouble("minimum_share"))));
        bosses = List.copyOf(bossList);

        List<ItemEntry> itemList = new ArrayList<>();
        each(catalog, "items", entry -> itemList.add(new ItemEntry(entry.getString("id"),
                entry.getString("item"), entry.getInt("level_min"), entry.getInt("level_max"),
                entry.getInt("modifiers"), entry.getInt("min_player_level"), entry.getString("slot"),
                entry.getString("set"))));
        items = List.copyOf(itemList);

        List<LootEntry> lootList = new ArrayList<>();
        each(catalog, "loot", entry -> lootList.add(new LootEntry(entry.getString("id"),
                entry.getInt("rolls_min"), entry.getInt("rolls_max"), entry.getInt("entries"))));
        loot = List.copyOf(lootList);

        List<QuestEntry> questList = new ArrayList<>();
        each(catalog, "quests", entry -> {
            List<Stage> stages = new ArrayList<>();
            ListTag stageTags = entry.getList("stage_list", Tag.TAG_COMPOUND);
            for (int i = 0; i < stageTags.size(); i++) {
                CompoundTag stageTag = stageTags.getCompound(i);
                List<Objective> objectives = new ArrayList<>();
                ListTag objectiveTags = stageTag.getList("objectives", Tag.TAG_COMPOUND);
                for (int j = 0; j < objectiveTags.size(); j++) {
                    CompoundTag objective = objectiveTags.getCompound(j);
                    objectives.add(new Objective(objective.getString("key"), objective.getString("label"),
                            objective.getInt("count")));
                }
                stages.add(new Stage(stageTag.getString("label"), List.copyOf(objectives)));
            }
            questList.add(new QuestEntry(entry.getString("id"), entry.getString("label"),
                    entry.getInt("stages"), entry.getString("reset"), entry.getInt("bounty_limit"),
                    List.copyOf(stages)));
        });
        quests = List.copyOf(questList);

        List<MerchantEntry> merchantList = new ArrayList<>();
        each(catalog, "merchants", entry -> {
            List<TradeEntry> trades = new ArrayList<>();
            ListTag tradeTags = entry.getList("trades", Tag.TAG_COMPOUND);
            for (int i = 0; i < tradeTags.size(); i++) {
                CompoundTag trade = tradeTags.getCompound(i);
                Map<String, Long> costs = new LinkedHashMap<>();
                CompoundTag costTag = trade.getCompound("costs");
                costTag.getAllKeys().forEach(key -> costs.put(key, costTag.getLong(key)));
                trades.add(new TradeEntry(trade.getString("key"), trade.getString("label"),
                        trade.getString("result"), trade.getInt("result_count"), trade.getInt("item_level"),
                        trade.getInt("stock"), trade.getInt("per_player_limit"), trade.getString("cost"), Map.copyOf(costs)));
            }
            merchantList.add(new MerchantEntry(entry.getString("id"), entry.getString("label"), List.copyOf(trades)));
        });
        merchants = List.copyOf(merchantList);

        CompoundTag state = tag.getCompound("state");
        level = Math.max(1, state.getInt("level"));
        totalXp = state.getLong("total_xp");
        statPoints = state.getInt("stat_points");
        mailbox = state.getInt("mailbox");
        canEdit = state.getBoolean("can_edit");
        Map<String, Long> balances = new LinkedHashMap<>();
        CompoundTag walletTag = state.getCompound("wallet");
        walletTag.getAllKeys().forEach(id -> balances.put(id, walletTag.getLong(id)));
        wallet = Map.copyOf(balances);

        Map<String, QuestState> questStates = new LinkedHashMap<>();
        each(state, "quests", entry -> {
            Map<String, Integer> counters = new LinkedHashMap<>();
            CompoundTag objectives = entry.getCompound("objectives");
            objectives.getAllKeys().forEach(key -> counters.put(key, objectives.getInt(key)));
            questStates.put(entry.getString("id"), new QuestState(entry.getString("id"), entry.getInt("stage"),
                    entry.getBoolean("available"), entry.getBoolean("claimable"), entry.getLong("completed"),
                    Map.copyOf(counters)));
        });
        questState = Map.copyOf(questStates);

        Map<String, Map<String, TradeState>> trades = new LinkedHashMap<>();
        each(state, "merchants", entry -> {
            Map<String, TradeState> perTrade = new LinkedHashMap<>();
            CompoundTag remaining = entry.getCompound("remaining");
            CompoundTag purchased = entry.getCompound("purchased");
            remaining.getAllKeys().forEach(key -> perTrade.put(key,
                    new TradeState(remaining.getInt(key), purchased.getInt(key), entry.getCompound("available").getBoolean(key))));
            trades.put(entry.getString("id"), Map.copyOf(perTrade));
        });
        tradeState = Map.copyOf(trades);
    }

    public static void applyPreview(CompoundTag tag) {
        previewTable = tag.getString("table");
        previewLines = List.copyOf(Nbt.loadStrings(tag, "lines"));
    }

    private static void each(CompoundTag parent, String key, java.util.function.Consumer<CompoundTag> action) {
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            action.accept(list.getCompound(i));
        }
    }

    public static boolean ready() { return ready; }
    public static boolean truncated() { return truncated; }
    public static boolean canEdit() { return canEdit; }
    public static String contentHash() { return contentHash; }
    public static long revision() { return revision; }
    public static int level() { return level; }
    public static long totalXp() { return totalXp; }
    public static int statPoints() { return statPoints; }
    public static int mailbox() { return mailbox; }
    public static Map<String, Long> wallet() { return wallet; }
    public static List<MonsterEntry> monsters() { return monsters; }
    public static List<BossEntry> bosses() { return bosses; }
    public static List<ItemEntry> items() { return items; }
    public static List<LootEntry> loot() { return loot; }
    public static List<QuestEntry> quests() { return quests; }
    public static List<MerchantEntry> merchants() { return merchants; }
    public static QuestState questState(String id) { return questState.get(id); }
    public static TradeState tradeState(String merchant, String trade) {
        return tradeState.getOrDefault(merchant, Map.of()).get(trade);
    }
    public static String previewTable() { return previewTable; }
    public static List<String> previewLines() { return previewLines; }

    public static void clearPreview() {
        previewTable = "";
        previewLines = List.of();
    }

    public static void reset() {
        ready = false;
        contentHash = "";
        revision = 0;
        truncated = false;
        canEdit = false;
        level = 1;
        totalXp = 0;
        statPoints = 0;
        mailbox = 0;
        wallet = Map.of();
        monsters = List.of();
        bosses = List.of();
        items = List.of();
        loot = List.of();
        quests = List.of();
        merchants = List.of();
        questState = Map.of();
        tradeState = Map.of();
        clearPreview();
    }
}
