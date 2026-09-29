package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the numbers in one season section mean in play, computed with the same math the server uses:
 * "level 50 needs 22k EXP", "+7 costs 1,342 gold at 40%". Shown live while the admin edits, so a
 * change can be judged before it is saved.
 */
public final class SeasonPreview {
    private SeasonPreview() {
    }

    /** Short preview lines for a section; empty when the section has nothing worth computing. */
    public static List<String> lines(SeasonRules r, String section) {
        List<String> out = new ArrayList<>();
        switch (section) {
            case "leveling" -> {
                var curve = new net.schwarz.rotasutils.level.LevelCurve();
                curve.set(r);
                String kills = r.pacingEnabled ? " (ฆ่ามอนเลเวลเท่ากัน ~" + Math.round(curve.killsFor(10)) + " / ~" + Math.round(curve.killsFor(50))
                        + " / ~" + Math.round(curve.killsFor(Math.max(1, r.mainMaxLevel - 1))) + " ตัว)" : "";
                out.add("อาชีพหลัก EXP/เลเวล: LV10 " + big(curve.xpToNext(10)) + " · LV50 " + big(curve.xpToNext(50)) + " · LV"
                        + Math.max(1, r.mainMaxLevel - 1) + " " + big(curve.xpToNext(Math.max(1, r.mainMaxLevel - 1))) + kills);
                out.add("รวมถึงเลเวลสูงสุด " + r.mainMaxLevel + ": " + big(curve.totalXpTo(r.mainMaxLevel))
                        + " EXP · อาชีพรองถึง " + r.subMaxLevel + ": " + big(SeasonMath.powerTotal(r.subBaseXp, r.subExponent, r.subMaxLevel)));
            }
            case "monster" -> {
                double over = SeasonMath.overLevelMultiplier(40, 30, r.overLevelGrace, r.overLevelPenaltyPerLevel, r.overLevelMaxPenalty);
                double under = SeasonMath.monsterXp(1, 10, r.monsterLevelBonus);
                out.add("ผู้เล่น LV40 ฆ่ามอน LV30 ได้ EXP " + pct(over) + " · มอน LV10 ให้ EXP ×" + dec(under)
                        + " · บอส ×" + dec(r.bossMultiplier));
            }
            case "party" -> {
                StringBuilder line = new StringBuilder("EXP ต่อคนในปาร์ตี้:");
                for (int members = 1; members <= Math.min(5, Math.max(1, r.partyMaxSize)); members++) {
                    line.append("  ").append(members).append(" คน ").append(pct(SeasonMath.partyShare(members, r.partyBonusPerMember)));
                }
                out.add(line.toString());
            }
            case "stats" -> {
                var stats = r.stats;
                out.add("แต้มสเตตัสที่ LV10 / 50 / 100: " + stats.pointsAt(10) + " / " + stats.pointsAt(50) + " / "
                        + stats.pointsAt(100) + " · สเตตัสละไม่เกิน " + stats.maxPerStat + " แต้ม");
                out.add("เต็ม " + stats.maxPerStat + " แต้ม: STR " + pct(1 + stats.strAttack * stats.maxPerStat)
                        + " ตี · VIT " + pct(1 + stats.vitHealth * stats.maxPerStat) + " HP · INT "
                        + pct(1 + stats.intMagic * stats.maxPerStat) + " เวทย์ · AGI "
                        + pct(1 + stats.agiAttackSpeed * stats.maxPerStat) + " ความเร็ว, หลบ "
                        + pct(stats.agiDodge * stats.maxPerStat));
            }
            case "pvp" -> out.add("PvP: ป้องกัน 50 / 100 / 200 รับดาเมจ "
                    + pct(SeasonMath.defenseMultiplier(50, r.defenseScale)) + " / "
                    + pct(SeasonMath.defenseMultiplier(100, r.defenseScale)) + " / "
                    + pct(SeasonMath.defenseMultiplier(200, r.defenseScale))
                    + " · ตีครั้งเดียวไม่เกิน " + pct(r.pvpMaxHitShare) + " HP");
            case "refine" -> {
                StringBuilder line = new StringBuilder("ตีบวก:");
                double[] chances = r.refine.chances;
                for (int level = 1; level <= Math.min(r.refine.maxLevel, chances.length); level++) {
                    long gold = Math.round(r.refine.goldPerAttempt * Math.pow(r.refine.goldGrowth, level - 1));
                    line.append("  +").append(level).append(' ').append(pct(chances[level - 1]))
                            .append('/').append(big(gold));
                }
                out.add(line.toString());
            }
            case "farming" -> out.add("มอนที่เกิด: เวเทอรัน " + pct(r.farming.veteranChance) + " · อีลิต " + pct(r.farming.eliteChance)
                    + " · คอมโบเต็ม " + r.farming.comboCap + " ตัว = EXP +" + pct(r.farming.comboCap * r.farming.comboXpPerKill));
            case "worldEvents" -> {
                double perHour = 60.0 / Math.max(1, r.worldEvents.intervalMinutes) * r.worldEvents.startChance;
                out.add("ประมาณ " + dec(perHour) + " เหตุการณ์ต่อชั่วโมง · อยู่ " + r.worldEvents.durationMinutes
                        + " นาที · " + r.worldEvents.types.size() + " ประเภท");
            }
            case "horse" -> out.add("สุ่ม 10 ครั้งถูกกว่า " + pct(1 - r.horse.tenPullCost / (double) Math.max(1, r.horse.pullCost * 10L))
                    + " · ได้ตำนานแน่นอนภายใน " + r.horse.pityLegendary + " ครั้ง = " + big(r.horse.pityLegendary * (long) r.horse.pullCost) + " เงิน");
            default -> {
            }
        }
        return out;
    }

    private static String at(double base, double exponent, int level) {
        return "LV" + level + " " + big(SeasonMath.powerCost(base, exponent, level));
    }

    static String big(long value) {
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1e9);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        if (value >= 10_000L) return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String pct(double share) {
        return Math.round(share * 100) + "%";
    }

    private static String dec(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }
}
