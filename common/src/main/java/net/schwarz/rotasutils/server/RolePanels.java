package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobMasteryCurve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

public final class RolePanels {
    private RolePanels() {
    }

    private record JobField(String name, double step, double min, double max, int format,
                            ToDoubleFunction<JobDef> get, BiConsumer<JobDef, Double> set) {
    }

    private static final List<JobField> JOB_FIELDS = List.of(
            new JobField("enabled", 1, 0, 1, SettingsPanels.FLAG, j -> j.enabled() ? 1 : 0, (j, v) -> j.setEnabled(v >= 0.5)),
            new JobField("mainAllowed", 1, 0, 1, SettingsPanels.FLAG, j -> j.mainAllowed() ? 1 : 0, (j, v) -> j.setMainAllowed(v >= 0.5)),
            new JobField("subAllowed", 1, 0, 1, SettingsPanels.FLAG, j -> j.subAllowed() ? 1 : 0, (j, v) -> j.setSubAllowed(v >= 0.5)),
            new JobField("minLevel", 1, 1, 100, SettingsPanels.INT, JobDef::minLevel, (j, v) -> j.setMinLevel((int) Math.round(v))),
            new JobField("order", 1, 0, 99, SettingsPanels.INT, JobDef::order, (j, v) -> j.setOrder((int) Math.round(v))),
            new JobField("subJobXpRate", 0.05, 0, 1, SettingsPanels.PERCENT, JobDef::subJobXpRate, (j, v) -> j.setSubJobXpRate(v)),
            new JobField("subPassiveCap", 0.05, 0, 1, SettingsPanels.PERCENT, JobDef::subPassiveCap, (j, v) -> j.setSubPassiveCap(v)),
            new JobField("skillPoints", 1, 0, 20, SettingsPanels.INT, JobDef::skillPointsPerMasteryLevel, (j, v) -> j.setSkillPointsPerMasteryLevel((int) Math.round(v))),
            new JobField("baseXp", 10, 1, 100_000, SettingsPanels.INT, j -> j.masteryCurve().baseXp(), (j, v) -> curve(j, Math.round(v), -1, -1, -1)),
            new JobField("growth", 0.05, 1, 3, SettingsPanels.DECIMAL, j -> j.masteryCurve().growth(), (j, v) -> curve(j, -1, v, -1, -1)),
            new JobField("maxLevel", 1, 1, 100, SettingsPanels.INT, j -> j.masteryCurve().maxLevel(), (j, v) -> curve(j, -1, -1, Math.round(v), -1)),
            new JobField("exponent", 0.1, 0, 4, SettingsPanels.DECIMAL, j -> j.masteryCurve().exponent(), (j, v) -> curve(j, -1, -1, -1, v)),
            new JobField("productionRate", 0.01, 0, 5, SettingsPanels.DECIMAL, JobDef::productionXpRate, (j, v) -> j.setProductionXpRate(v)));

    private static void curve(JobDef job, long baseXp, double growth, long maxLevel, double exponent) {
        JobMasteryCurve old = job.masteryCurve();
        job.setMasteryCurve(new JobMasteryCurve(baseXp >= 0 ? baseXp : old.baseXp(), growth >= 0 ? growth : old.growth(),
                maxLevel >= 0 ? (int) maxLevel : old.maxLevel(), exponent >= 0 ? exponent : old.exponent()));
    }

    private static final JobDef.ProductionEntry.Activity[] ACTIVITIES = JobDef.ProductionEntry.Activity.values();

public static List<String> scopes(RotasData data) {
        List<String> out = new ArrayList<>();
        out.add("roles");
        for (JobDef job : sorted(data)) {
            if (job.subAllowed() || !job.production().isEmpty()) {
                out.add("unlocks:" + job.id());
            }
        }
        return out;
    }

    private static List<JobDef> sorted(RotasData data) {
        return data.jobs().values().stream().sorted(Comparator.comparingInt(JobDef::order).thenComparing(JobDef::name)).toList();
    }

    public static boolean owns(String scope) {
        return scope.equals("roles") || scope.startsWith("unlocks:");
    }

public static List<SettingsPanels.Field> fields(RotasData data, String scope) {
        List<SettingsPanels.Field> out = new ArrayList<>();
        if (scope.equals("roles")) {
            for (JobDef job : sorted(data)) {
                JobDef shipped = JobArchetypes.create(job.id());
                for (JobField f : JOB_FIELDS) {
                    double value = f.get().applyAsDouble(job);
                    double def = shipped == null ? value : f.get().applyAsDouble(shipped);
                    out.add(new SettingsPanels.Field("job." + job.id() + "." + f.name(), "job:" + job.id(), f.name(), value,
                            clamp(def, f), f.step(), f.min(), f.max(), f.format()));
                }
            }
        } else if (scope.startsWith("unlocks:")) {
            JobDef job = data.job(scope.substring("unlocks:".length()));
            if (job != null) {
                JobDef shipped = JobArchetypes.create(job.id());
                for (int i = 0; i < job.production().size(); i++) {
                    JobDef.ProductionEntry row = job.production().get(i);
                    String key = "unlock." + job.id() + "." + i + ".", group = "unlock:" + job.id() + ":" + row.activity().name() + ":" + row.selector();
                    JobDef.ProductionEntry original = shipped == null ? null : shipped.production().stream()
                            .filter(e -> e.activity() == row.activity() && e.selector().equals(row.selector())).findFirst().orElse(null);
                    out.add(new SettingsPanels.Field(key + "level", group, "level", row.unlockLevel(),
                            original == null ? row.unlockLevel() : original.unlockLevel(), 1, 1, 100, SettingsPanels.INT));
                    out.add(new SettingsPanels.Field(key + "activity", group, "activity", row.activity().ordinal(),
                            row.activity().ordinal(), 1, 0, ACTIVITIES.length - 1, SettingsPanels.INT));
                    out.add(new SettingsPanels.Field(key + "keep", group, "keep", 1, 1, 1, 0, 1, SettingsPanels.FLAG));
                }
            }
        }
        return out;
    }

    private static double clamp(double v, JobField f) {
        return Math.max(f.min(), Math.min(f.max(), v));
    }

public static String set(RotasData data, String key, double value) {
        try {
            String[] part = key.split("\\.");
            if (part[0].equals("job") && part.length == 3) {
                JobDef job = data.job(part[1]);
                JobField field = JOB_FIELDS.stream().filter(f -> f.name().equals(part[2])).findFirst().orElse(null);
                if (job == null || field == null) {
                    return "unknown setting";
                }
                field.set().accept(job, Math.max(field.min(), Math.min(field.max(), value)));
                data.putJob(job);
                return null;
            }
            if (part[0].equals("unlock") && part.length == 4) {
                JobDef job = data.job(part[1]);
                int index = Integer.parseInt(part[2]);
                if (job == null || index < 0 || index >= job.production().size()) {
                    return "unknown unlock";
                }
                JobDef.ProductionEntry row = job.production().get(index);
                switch (part[3]) {
                    case "level" -> job.production().set(index, new JobDef.ProductionEntry(row.activity(), row.selector(),
                            (int) Math.max(1, Math.min(100, Math.round(value)))));
                    case "activity" -> job.production().set(index, new JobDef.ProductionEntry(
                            ACTIVITIES[(int) Math.max(0, Math.min(ACTIVITIES.length - 1, Math.round(value)))], row.selector(), row.unlockLevel()));
                    case "keep" -> {
                        if (value < 0.5) job.production().remove(index);
                    }
                    default -> {
                        return "unknown setting";
                    }
                }
                data.putJob(job);
                return null;
            }
        } catch (RuntimeException refused) {
            return refused.getMessage() == null ? refused.toString() : refused.getMessage();
        }
        return "unknown setting";
    }

    public static String addUnlock(RotasData data, String jobId, String item) {
        JobDef job = data.job(jobId);
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (job == null || id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return "not an item";
        }
        if (job.production().size() >= JobDef.MAX_PRODUCTION) {
            return "too many unlocks";
        }
        boolean exists = job.production().stream().anyMatch(e -> e.selector().equals(item) && e.activity() == JobDef.ProductionEntry.Activity.CRAFT);
        if (exists) {
            return "already listed";
        }
        job.production().add(new JobDef.ProductionEntry(JobDef.ProductionEntry.Activity.CRAFT, item, 1));
        data.putJob(job);
        return null;
    }

    public static void reset(RotasData data, String scope) {
        if (scope.equals("roles")) {
            for (JobDef job : data.jobs().values()) {
                JobDef shipped = JobArchetypes.create(job.id());
                if (shipped == null) {
                    continue;
                }
                for (JobField f : JOB_FIELDS) {
                    f.set().accept(job, f.get().applyAsDouble(shipped));
                }
                data.putJob(job);
            }
        } else if (scope.startsWith("unlocks:")) {
            JobDef job = data.job(scope.substring("unlocks:".length()));
            JobDef shipped = job == null ? null : JobArchetypes.create(job.id());
            if (job != null && shipped != null) {
                job.production().clear();
                job.production().addAll(shipped.production());
                data.putJob(job);
            }
        }
    }
}
