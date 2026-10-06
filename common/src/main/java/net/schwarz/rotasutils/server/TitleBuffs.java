package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.title.TitleDef;

import java.util.ArrayList;
import java.util.List;

public final class TitleBuffs {
    static final double RAISE = 1.5;

    private TitleBuffs() {
    }

    private static CharacterStat.Effect percent(String attribute, double amount, String label) {
        return new CharacterStat.Effect(attribute, amount, CharacterStat.Operation.MULTIPLY_BASE, true, label, 0);
    }

    private static CharacterStat.Effect flat(String attribute, double amount, String label) {
        return new CharacterStat.Effect(attribute, amount, CharacterStat.Operation.ADD, false, label, 0);
    }

    private static CharacterStat.Effect chance(String stat, double amount, String label) {
        return new CharacterStat.Effect(stat, amount, CharacterStat.Operation.ADD, true, label, 0);
    }

    private static List<CharacterStat.Effect> pool(TitleDef.Category category) {
        return switch (category) {
            case COMBAT -> List.of(percent("minecraft:generic.attack_damage", 0.015, "พลังโจมตี"),
                    chance(CombatStats.CRIT_CHANCE, 0.01, "โอกาสคริติคอล"),
                    flat(CombatStats.DEFENSE, 1, "พลังป้องกัน"));
            case PROGRESS -> List.of(percent("minecraft:generic.max_health", 0.015, "พลังชีวิต"),
                    chance(CombatStats.REGEN, 0.0003, "ฟื้นเลือด/วินาที"),
                    flat(CombatStats.DEFENSE, 1, "พลังป้องกัน"));
            case CRAFTING -> List.of(flat(CombatStats.DEFENSE, 1.5, "พลังป้องกัน"),
                    flat("minecraft:generic.luck", 0.3, "โชค"),
                    chance(CombatStats.CRIT_DAMAGE, 0.05, "ดาเมจคริติคอล"));
            case WEALTH -> List.of(flat("minecraft:generic.luck", 0.5, "โชค"),
                    percent("minecraft:generic.max_health", 0.01, "พลังชีวิต"),
                    chance(CombatStats.CRIT_CHANCE, 0.005, "โอกาสคริติคอล"));
            case SPECIAL -> List.of(percent("minecraft:generic.max_health", 0.02, "พลังชีวิต"),
                    percent("minecraft:generic.attack_damage", 0.01, "พลังโจมตี"),
                    chance(CombatStats.EVASION, 0.01, "โอกาสหลบ"));
        };
    }

    private static boolean has(TitleDef title, String attribute) {
        return title.effects().stream().anyMatch(effect -> effect.attribute().equals(attribute));
    }

    public static TitleDef upgrade(TitleDef title) {
        title.setRarity(title.rarity());
        List<CharacterStat.Effect> raised = new ArrayList<>();
        for (CharacterStat.Effect effect : title.effects()) {
            raised.add(new CharacterStat.Effect(effect.attribute(), round(effect.perPoint() * RAISE), effect.operation(),
                    effect.percent(), effect.label(), effect.cap()));
        }
        title.effects().clear();
        raised.forEach(title::addEffect);
        List<CharacterStat.Effect> pool = pool(title.category());
        int start = Math.floorMod(title.id().hashCode(), pool.size());
        int wanted = title.rarity().ordinal() >= TitleDef.Rarity.RARE.ordinal() ? 2 : 1;
        for (int i = 0; i < pool.size() && title.effects().size() < wanted; i++) {
            CharacterStat.Effect candidate = pool.get((start + i) % pool.size());
            if (!has(title, candidate.attribute())) {
                title.addEffect(candidate);
            }
        }
        return title;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public static List<TitleDef> upgrade(List<TitleDef> titles) {
        titles.forEach(TitleBuffs::upgrade);
        return titles;
    }
}
