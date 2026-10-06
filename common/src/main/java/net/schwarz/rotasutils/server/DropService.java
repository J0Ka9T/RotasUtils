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

public final class DropService {
    private static final int MAX_CHAT_LINES = 6;

    private DropService() {
    }

    public static void onMonsterKilled(ServerPlayer killer, Mob mob, MonsterState state) {
        RotasData data = RotasData.get(killer.server);
        SeasonRules.DropRules rules = SeasonService.rules(data).drops;
        if (!data.serverSettings().monsterDropsEnabled() || rules == null || !rules.enabled) {
            return;
        }
        MonsterRank rank = state.rank();
        SeasonRules.RankDrop rule = perEntity(rules, mob);
        if (rule == null) {
            rule = rules.ranks.get(rank.name());
        }
        if (rule == null) {
            return;
        }
        pay(killer, data, rules, rule, mob, state.level());
    }

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
        int level = (int) Math.max(1, Math.min(100, Math.round(mob.getMaxHealth() / 4.0)));
        pay(killer, data, rules, rule, mob, level);
    }

    private static SeasonRules.RankDrop perEntity(SeasonRules.DropRules rules, Mob mob) {
        if (rules.plain == null || rules.plain.byEntity == null || rules.plain.byEntity.isEmpty()) {
            return null;
        }
        String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        return rules.plain.byEntity.get(id);
    }

    private static void pay(ServerPlayer killer, RotasData data, SeasonRules.DropRules rules,
                            SeasonRules.RankDrop rule, Mob mob, int level) {
        RandomSource random = killer.getRandom();
        List<ItemStack> drop = new ArrayList<>();
        var event = WorldEventService.at(mob);
        long coins = 0;
        double bonusDrop = CombatStats.get(killer.getUUID()).dropRate();
        double dropScale = 1.0 + Math.max(0, bonusDrop);
        if (random.nextDouble() < rule.coinChance) {
            coins += Math.round(coins(level, rule, random) * (event == null ? 1.0 : event.coinMultiplier) * dropScale);
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
                (event == null ? 1.0 : event.lootMultiplier) * dropScale);
        DropGrade grade = rollGrade(rule, lootChance, random);
        if (grade != null) {
            drop.addAll(DropLoot.roll(data, grade, mob.getUUID() + "|" + grade.key()));
        }
        drop.removeIf(stack -> DropFilterService.blocked(data, mob, stack));
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

    private static DropGrade rollGrade(SeasonRules.RankDrop rule, double lootChance, RandomSource random) {
        if (rule.grades == null || rule.grades.length == 0 || random.nextDouble() >= lootChance) {
            return null;
        }
        return DropGrade.byKey(rule.grades[random.nextInt(rule.grades.length)]);
    }

    static long coins(int level, SeasonRules.RankDrop rule, RandomSource random) {
        int min = Math.max(0, rule.coinMin);
        int max = Math.max(min, rule.coinMax);
        double base = min + (max == min ? 0 : random.nextInt(max - min + 1)) + Math.max(0, rule.coinPerLevel) * Math.max(0, level);
        return Math.max(1, Math.round(base * Math.max(0, rule.coinMultiplier)));
    }

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
