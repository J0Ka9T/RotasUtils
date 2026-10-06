package net.schwarz.rotasutils.mine;

import java.util.List;

public final class MinePresets {
    private MinePresets() {
    }

    public record Preset(String id, int respawnSeconds, long goldMin, long goldMax, long xp, int dailyLimit, int minTier,
                         int richChance, int richMultiplier, int minerBonus) {
    }

    public static final List<Preset> ALL = List.of(
            new Preset("starter", 300, 0, 3, 10, 0, 0, 5, 2, 25),
            new Preset("rich", 600, 5, 20, 30, 40, 2, 15, 3, 25),
            new Preset("deep", 1200, 20, 60, 80, 20, 3, 20, 4, 40));

    public static Preset get(String id) {
        for (Preset preset : ALL) {
            if (preset.id().equals(id)) return preset;
        }
        return ALL.get(0);
    }

    public static void apply(MiningSite site, String id) {
        Preset p = get(id);
        site.setRespawnSeconds(p.respawnSeconds());
        site.setGold(p.goldMin(), p.goldMax());
        site.setXp(p.xp());
        site.setDailyLimit(p.dailyLimit());
        site.setMinTier(p.minTier());
        site.setRichChance(p.richChance());
        site.setRichMultiplier(p.richMultiplier());
        site.setMinerBonus(p.minerBonus());
    }
}
