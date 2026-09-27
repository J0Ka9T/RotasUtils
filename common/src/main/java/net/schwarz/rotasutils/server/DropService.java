package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.DropGrade;
import net.schwarz.rotasutils.core.MonsterRank;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops by monster rank.
 *
 * <p>The rule a player has to remember is one sentence: ordinary monsters pay coins, a miniboss
 * pays common or medium loot, and a boss pays rare or epic loot. The loot is the items themselves -
 * there is no container to collect and open first - so a kill needs no follow-up action at all.
 *
 * <p>Every number behind that sentence lives in {@code season.json} under {@code drops} and reloads
 * with {@code /rotas season reload}. A monster profile that already names its own loot table keeps
 * that table as well: this only ever adds to a hand-built drop.
 */
public final class DropService {
    /** Kill lines are only listed up to here, so a boss payout cannot flood the chat. */
    private static final int MAX_CHAT_LINES = 6;

    private DropService() {
    }

    /**
     * Called once per confirmed monster death, for the player credited with the kill. Never throws
     * into the kill path: a failed drop must not stop the rest of the reward.
     */
    public static void onMonsterKilled(ServerPlayer killer, Mob mob, MonsterState state) {
        RotasData data = RotasData.get(killer.server);
        SeasonRules.DropRules rules = SeasonService.rules(data).drops;
        if (!data.serverSettings().monsterDropsEnabled() || rules == null || !rules.enabled) {
            return;
        }
        MonsterRank rank = state.rank();
        // An entity an administrator gave its own line keeps it even when it was leveled automatically:
        // that is the whole point of the per-entity rules, which need no Mob Setup behind them.
        SeasonRules.RankDrop rule = perEntity(rules, mob);
        if (rule == null) {
            rule = rules.ranks.get(rank.name());
        }
        if (rule == null) {
            // A rank the configuration does not describe drops nothing; that is how one is turned off.
            return;
        }
        pay(killer, data, rules, rule, mob, state.level());
    }

    /**
     * Called for a mob that has no Mob Setup at all - no profile, no level band, nothing. Without this
     * such a kill paid nothing, which is why a pack's own mobs felt worthless next to configured ones.
     */
    public static void onPlainMobKilled(ServerPlayer killer, net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        RotasData data = RotasData.get(killer.server);
        SeasonRules.DropRules rules = SeasonService.rules(data).drops;
        if (!data.serverSettings().monsterDropsEnabled() || rules == null || !rules.enabled
                || rules.plain == null || !rules.plain.enabled) {
            return;
        }
        String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        for (String ignored : rules.plain.ignore) {
            if (id.equalsIgnoreCase(ignored)) {
                return;
            }
        }
        SeasonRules.RankDrop rule = perEntity(rules, mob);
        if (rule == null) {
            boolean hostile = mob instanceof net.minecraft.world.entity.monster.Enemy
                    || mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
            if (rules.plain.hostileOnly && !hostile) {
                return;
            }
            rule = rules.plain.rule;
        }
        // A mob with no level band still scales its coins by something; its own health is the honest proxy.
        int level = (int) Math.max(1, Math.min(100, Math.round(mob.getMaxHealth() / 4.0)));
        pay(killer, data, rules, rule, mob, level);
    }

    /** The rule an administrator wrote for this entity type, or null when they wrote none. */
    private static SeasonRules.RankDrop perEntity(SeasonRules.DropRules rules, Mob mob) {
        if (rules.plain == null || rules.plain.byEntity == null || rules.plain.byEntity.isEmpty()) {
            return null;
        }
        String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        return rules.plain.byEntity.get(id);
    }

    /** Rolls one rule and hands the result to the player. Shared by every kill path. */
    private static void pay(ServerPlayer killer, RotasData data, SeasonRules.DropRules rules,
                            SeasonRules.RankDrop rule, Mob mob, int level) {
        RandomSource random = killer.getRandom();
        List<ItemStack> drop = new ArrayList<>();
        var event = WorldEventService.at(mob);
        long coins = 0;
        if (random.nextDouble() < rule.coinChance) {
            coins += Math.round(coins(level, rule, random) * (event == null ? 1.0 : event.coinMultiplier));
        }
        var lines = new java.util.Random(mob.getUUID().getLeastSignificantBits() ^ System.nanoTime());
        for (String line : rule.items) {
            if (drop.size() >= 16) {
                break;
            }
            ItemStack rolled = DropLoot.parse(line, lines);
            if (!rolled.isEmpty()) {
                drop.add(rolled);
            }
        }
        String entityId = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        double lootChance = net.schwarz.rotasutils.core.FarmingMath.boosted(rule.lootChance,
                FarmingService.lootLuck(killer, data), FarmingService.comboLoot(killer, data),
                BestiaryService.loot(data, data.progress(killer.getUUID()), entityId),
                event == null ? 1.0 : event.lootMultiplier);
        DropGrade grade = rollGrade(rule, lootChance, random);
        if (grade != null) {
            drop.addAll(DropLoot.roll(data, grade, mob.getUUID() + "|" + grade.key()));
        }
        // An item an administrator switched off must not come back through this mod's own tables.
        drop.removeIf(stack -> DropFilterService.blocked(data, mob, stack));
        // Every coin from this kill - the rule's own and any a grade paid - lands as one pile on the ground.
        for (ItemStack stack : drop) {
            coins += net.schwarz.rotasutils.item.GoldCoins.amount(stack);
        }
        drop.removeIf(net.schwarz.rotasutils.item.GoldCoins::is);
        if (coins > 0) {
            GoldCoinService.dropLoot(killer, (net.minecraft.server.level.ServerLevel) mob.level(),
                    mob.getX(), mob.getY(), mob.getZ(), coins);
        }
        if (drop.isEmpty()) {
            return;
        }

        EventService.fire(killer, data, net.schwarz.rotasutils.event.EventType.MONSTER_DROP,
                String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())));
        int mailed = LootService.deliver(killer, drop);
        announce(killer, drop);
        if (mailed > 0) {
            RotasNetwork.feedback(killer, true, ThaiText.t("rotasutils.msg.drop.mailed", mailed));
        } else if (grade != null) {
            RotasNetwork.feedback(killer, true, ThaiText.t("rotasutils.msg.drop.grade",
                    ThaiText.t(grade.nameKey())));
        }
        RotasNetwork.syncProgress(killer);
    }

    /** The grade this kill pays, or null when it pays coins only. */
    private static DropGrade rollGrade(SeasonRules.RankDrop rule, double lootChance, RandomSource random) {
        if (rule.grades == null || rule.grades.length == 0 || random.nextDouble() >= lootChance) {
            return null;
        }
        return DropGrade.byKey(rule.grades[random.nextInt(rule.grades.length)]);
    }

    /**
     * Coins: {@code coinMin..coinMax} plus {@code coinPerLevel} per monster level, times the rank's multiplier.
     * No stack cap: coins are one pile of any size.
     */
    static long coins(int level, SeasonRules.RankDrop rule, RandomSource random) {
        int min = Math.max(0, rule.coinMin);
        int max = Math.max(min, rule.coinMax);
        double base = min + (max == min ? 0 : random.nextInt(max - min + 1)) + Math.max(0, rule.coinPerLevel) * Math.max(0, level);
        return Math.max(1, Math.round(base * Math.max(0, rule.coinMultiplier)));
    }

    /** One chat line per stack, so the player sees what a kill paid without opening their bag. */
    private static void announce(ServerPlayer player, List<ItemStack> drop) {
        for (int i = 0; i < drop.size() && i < MAX_CHAT_LINES; i++) {
            ItemStack stack = drop.get(i);
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.drop.line",
                    stack.getCount(), stack.getHoverName().getString()));
        }
        if (drop.size() > MAX_CHAT_LINES) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.drop.more", drop.size() - MAX_CHAT_LINES));
        }
    }
}
