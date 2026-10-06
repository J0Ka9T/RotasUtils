package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.mine.MinePresets;
import net.schwarz.rotasutils.mine.MiningSite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

public final class MinePanels {
    private MinePanels() {
    }

    private record SiteField(String name, double step, double min, double max, int format,
                             ToDoubleFunction<MiningSite> get, BiConsumer<MiningSite, Integer> set) {
    }

    private static final List<SiteField> FIELDS = List.of(
            new SiteField("respawn", 30, 5, 604_800, SettingsPanels.INT, MiningSite::respawnSeconds, MiningSite::setRespawnSeconds),
            new SiteField("goldMin", 1, 0, 1_000_000, SettingsPanels.INT, MiningSite::goldMin, (s, v) -> s.setGold(v, s.goldMax())),
            new SiteField("goldMax", 1, 0, 1_000_000, SettingsPanels.INT, MiningSite::goldMax, (s, v) -> s.setGold(s.goldMin(), v)),
            new SiteField("xp", 5, 0, 1_000_000, SettingsPanels.INT, MiningSite::xp, (s, v) -> s.setXp(v)),
            new SiteField("dailyLimit", 1, 0, 1000, SettingsPanels.INT, MiningSite::dailyLimit, MiningSite::setDailyLimit),
            new SiteField("minTier", 1, 0, 4, SettingsPanels.INT, MiningSite::minTier, MiningSite::setMinTier),
            new SiteField("richChance", 5, 0, 100, SettingsPanels.INT, MiningSite::richChance, MiningSite::setRichChance),
            new SiteField("richMultiplier", 1, 1, 10, SettingsPanels.INT, MiningSite::richMultiplier, MiningSite::setRichMultiplier),
            new SiteField("minerBonus", 10, 0, 300, SettingsPanels.INT, MiningSite::minerBonus, MiningSite::setMinerBonus),
            new SiteField("sparkle", 1, 0, 1, SettingsPanels.FLAG, s -> s.sparkle() ? 1 : 0, (s, v) -> s.setSparkle(v >= 1)));

    public static boolean owns(String scope) {
        return scope.equals("mines") || scope.startsWith("mines:");
    }

    public static List<String> scopes(RotasData data) {
        if (data.miningSites().isEmpty()) {
            return List.of("mines");
        }
        List<String> out = new ArrayList<>();
        data.miningSites().keySet().forEach(id -> out.add("mines:" + id));
        return out;
    }

    public static MiningSite site(RotasData data, String scope) {
        return scope.startsWith("mines:") ? data.miningSites().get(scope.substring("mines:".length())) : null;
    }

    public static List<SettingsPanels.Field> fields(RotasData data, String scope) {
        List<SettingsPanels.Field> out = new ArrayList<>();
        MiningSite site = site(data, scope);
        if (site == null) {
            return out;
        }
        MiningSite starter = new MiningSite("starter", site.dimension());
        MinePresets.apply(starter, "starter");
        String group = "mine:" + site.id() + ":" + site.nodes().size();
        for (SiteField f : FIELDS) {
            out.add(new SettingsPanels.Field("mine." + site.id() + "." + f.name(), group, f.name(), f.get().applyAsDouble(site),
                    f.get().applyAsDouble(starter), f.step(), f.min(), f.max(), f.format()));
        }
        return out;
    }

    public static String set(RotasData data, String key, double value) {
        String[] part = key.split("\\.");
        if (part.length != 3 || !part[0].equals("mine")) {
            return "unknown setting";
        }
        MiningSite site = data.miningSites().get(part[1]);
        SiteField field = FIELDS.stream().filter(f -> f.name().equals(part[2])).findFirst().orElse(null);
        if (site == null || field == null) {
            return "unknown setting";
        }
        field.set().accept(site, (int) Math.round(Math.max(field.min(), Math.min(field.max(), value))));
        data.setDirty();
        return null;
    }

    public static void reset(RotasData data, String scope) {
        MiningSite site = site(data, scope);
        if (site != null) {
            MinePresets.apply(site, "starter");
            data.setDirty();
        }
    }
}
