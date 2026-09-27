package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.job.JobSlot;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.npc.NpcServiceDef;

/** Choosing and assigning jobs. */
public final class JobService {
    /**
     * Once per world: creates the starter combat jobs that are missing, and gives template jobs an
     * administrator already made their signature buffs. Later edits and deletions are respected.
     */
    public static void seedDefaults(RotasData data) {
        if (!data.titleBatchSeeded("jobs:starter_v1")) {
            int order = data.jobs().size();
            for (String id : net.schwarz.rotasutils.job.JobArchetypes.STARTER_JOBS) {
                if (data.job(id) == null) {
                    JobDef job = net.schwarz.rotasutils.job.JobArchetypes.create(id);
                    job.setOrder(order++);
                    data.putJob(job);
                }
            }
            data.markTitleBatchSeeded("jobs:starter_v1");
        }
        if (!data.titleBatchSeeded("jobs:signature_v1")) {
            for (String id : net.schwarz.rotasutils.job.JobArchetypes.STARTER_JOBS) {
                JobDef job = data.job(id);
                if (job == null) continue;
                for (var buff : net.schwarz.rotasutils.job.JobArchetypes.signature(id)) {
                    boolean present = job.attributeModifiers().stream()
                            .anyMatch(existing -> existing.attribute().equals(buff.attribute()) && existing.label().equals(buff.label()));
                    if (!present) job.attributeModifiers().add(buff);
                }
                data.putJob(job);
            }
            data.markTitleBatchSeeded("jobs:signature_v1");
        }
        data.setDirty();
    }

    public record MasteryGrant(long appliedXp, int levelsGained, long pointsGranted) {}
    public record Result(boolean success, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result no(String message) {
            return new Result(false, message);
        }
    }

    private JobService() {
    }

    public static MasteryGrant grantMastery(PlayerProgress progress, JobDef job, JobSlot slot, long rawXp) {
        if(rawXp<0) throw new IllegalArgumentException("Mastery XP cannot be negative");
        String equipped=slot==JobSlot.MAIN?progress.mainJob():progress.subJob();
        if(!job.id().equals(equipped)) throw new IllegalArgumentException("Job is not equipped in that slot");
        long applied=slot==JobSlot.SUB?(long)Math.floor(rawXp*job.subJobXpRate()):rawXp;
        long beforeXp=progress.rpg().masteryXp(job.id()); int before=job.masteryCurve().levelAt(beforeXp);
        progress.rpg().masteryXp(job.id(),applied); int after=job.masteryCurve().levelAt(progress.rpg().masteryXp(job.id()));
        int gained=Math.max(0,after-before); long points=Math.multiplyExact((long)gained,job.skillPointsPerMasteryLevel());
        progress.rpg().addJobSkillPoints(job.id(),points);
        return new MasteryGrant(applied,gained,points);
    }

    /** Production EXP for the equipped sub job, paid in full (the tier and profession rate are already applied). */
    public static MasteryGrant grantProduction(PlayerProgress progress, JobDef job, long xp) {
        if (xp < 0) throw new IllegalArgumentException("Production XP cannot be negative");
        if (!job.id().equals(progress.subJob())) throw new IllegalArgumentException("Job is not the equipped sub job");
        int before = job.masteryCurve().levelAt(progress.rpg().masteryXp(job.id()));
        progress.rpg().masteryXp(job.id(), xp);
        int after = job.masteryCurve().levelAt(progress.rpg().masteryXp(job.id()));
        int gained = Math.max(0, after - before);
        long points = Math.multiplyExact((long) gained, job.skillPointsPerMasteryLevel());
        progress.rpg().addJobSkillPoints(job.id(), points);
        return new MasteryGrant(xp, gained, points);
    }

    /** A player's own choice: checks level and the change cooldown. The first job is free. */
    public static Result choose(ServerPlayer player, RotasData data, String jobId) {
        return choose(player,data,jobId,JobSlot.MAIN);
    }

    public static Result choose(ServerPlayer player, RotasData data, String jobId, JobSlot slot) {
        JobDef job = data.job(jobId);
        if (job == null || !job.enabled()) {
            return Result.no(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.job.unavailable"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if ((slot==JobSlot.MAIN && !job.mainAllowed()) || (slot==JobSlot.SUB && !job.subAllowed())) return Result.no(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.job.slot_unavailable"));
        if (job.id().equals(slot==JobSlot.MAIN?progress.mainJob():progress.subJob())) {
            return Result.no(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.job.already_slot"));
        }
        if(job.id().equals(slot==JobSlot.MAIN?progress.subJob():progress.mainJob())) return Result.no(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.job.both_slots"));
        if (progress.level() < job.minLevel()) {
            return Result.no(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.job.requires_level", job.name(), job.minLevel()));
        }
        long now = QuestService.nowSeconds();
        long cooldown = data.serverSettings().jobChangeCooldownSeconds();
        long since = now - progress.jobChangedAt();
        if (!progress.job().isEmpty() && cooldown > 0 && since < cooldown) {
            return Result.no(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.job.cooldown", QuestService.formatDuration(cooldown - since)));
        }
        assignSlot(progress,job,slot);
        SkillService.recalculate(player,data);
        CharacterStatService.apply(player, data);
        data.setDirty();
        progress.setJobChangedAt(now);
        data.audit(player.getGameProfile().getName() + " became " + job.id());
        return Result.ok(net.schwarz.rotasutils.util.ThaiText.t(slot==JobSlot.MAIN ? "rotasutils.msg.job.now_main" : "rotasutils.msg.job.now_sub", job.name()));
    }

    /**
     * Sets the job without any gate, used by admins and by {@link #choose}. Points spent in
     * trees the new job cannot use are refunded, so switching never strands skill points.
     *
     * @return points refunded
     */
    public static int assign(ServerPlayer player, RotasData data, PlayerProgress progress, String jobId) {
        JobDef job=jobId==null||jobId.isEmpty()?null:data.job(jobId);
        if(job==null) progress.setMainJob(""); else assignSlot(progress,job,JobSlot.MAIN);
        SkillService.recalculate(player, data);
        CharacterStatService.apply(player, data);
        data.setDirty();
        return 0;
    }

    public static void assignSlot(PlayerProgress progress,JobDef job,JobSlot slot) {
        if(slot==JobSlot.MAIN&&!job.mainAllowed()) throw new IllegalArgumentException("Job cannot be a main job");
        if(slot==JobSlot.SUB&&!job.subAllowed()) throw new IllegalArgumentException("Job cannot be a sub-job");
        if(slot==JobSlot.MAIN) progress.setMainJob(job.id()); else progress.setSubJob(job.id());
    }

    public static String offeredJob(NpcDef npc, JobSlot slot) {
        if (npc == null) return "";
        return npc.services().stream()
                .filter(service -> service.type() == NpcServiceDef.Type.JOB_MASTER && service.slot() == slot)
                .map(NpcServiceDef::jobId).filter(id -> !id.isBlank()).findFirst().orElse("");
    }

    /** Talking can fill an empty slot, but never silently replaces an existing build. */
    public static Result acceptNpcOffer(ServerPlayer player, RotasData data, NpcDef npc, JobSlot slot) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (!(slot == JobSlot.MAIN ? progress.mainJob() : progress.subJob()).isBlank()) {
            return Result.no("Your " + (slot == JobSlot.MAIN ? "main job" : "sub-job") + " slot is already occupied.");
        }
        String jobId = offeredJob(npc, slot);
        return jobId.isBlank() ? Result.no("This NPC does not offer that job slot.") : choose(player, data, jobId, slot);
    }
}
