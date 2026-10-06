package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.title.TitleCounters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class BountyService {
    private static final String P = "rpg.bounty.";

    public record Contract(int index, SeasonRules.BountyDef def) {
    }

    public record Active(String entity, int need, int count, long gold, long xp) {
        public boolean complete() {
            return count >= need;
        }
    }

    private BountyService() {
    }

    public static List<Contract> today(SeasonRules.NpcServiceRules rules, long day) {
        List<Contract> all = new ArrayList<>();
        for (int i = 0; i < rules.bounties.length; i++) all.add(new Contract(i, rules.bounties[i]));
        SplittableRandom random = new SplittableRandom(day * 0x9E3779B97F4A7C15L + 17);
        for (int i = all.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Contract swap = all.get(i);
            all.set(i, all.get(j));
            all.set(j, swap);
        }
        return all.subList(0, Math.min(rules.bountiesPerDay, all.size()));
    }

    public static Active active(PlayerProgress progress) {
        Map<String, String> v = progress.questVariables();
        String entity = v.get(P + "entity");
        if (entity == null || entity.isEmpty()) return null;
        return new Active(entity, (int) TitleCounters.read(v, P + "need"), (int) TitleCounters.read(v, P + "count"),
                TitleCounters.read(v, P + "gold"), TitleCounters.read(v, P + "xp"));
    }

    static int doneToday(PlayerProgress progress, long day) {
        Map<String, String> v = progress.questVariables();
        return TitleCounters.read(v, P + "done_day") == day ? (int) TitleCounters.read(v, P + "done") : 0;
    }

    public static String take(ServerPlayer player, RotasData data, int index) {
        SeasonRules.NpcServiceRules rules = SeasonService.rules(data).npcServices;
        PlayerProgress progress = data.progress(player.getUUID());
        if (active(progress) != null) return "คุณมีงานค่าหัวอยู่แล้ว ส่งหรือยกเลิกก่อน";
        long day = SeasonService.today();
        if (doneToday(progress, day) >= rules.bountiesCompletedPerDay) return "วันนี้ส่งงานค่าหัวครบแล้ว";
        Contract contract = today(rules, day).stream().filter(c -> c.index() == index).findFirst().orElse(null);
        if (contract == null) return "งานนี้ไม่อยู่บนกระดานวันนี้แล้ว";
        if (progress.level() < contract.def().minLevel) return "ต้องเลเวล " + contract.def().minLevel + " ขึ้นไป";
        Map<String, String> v = progress.questVariables();
        v.put(P + "entity", contract.def().entity);
        v.put(P + "need", Integer.toString(contract.def().count));
        v.put(P + "count", "0");
        v.put(P + "gold", Long.toString(contract.def().gold));
        v.put(P + "xp", Long.toString(contract.def().xp));
        data.setDirty();
        return null;
    }

    public static void abandon(RotasData data, PlayerProgress progress) {
        for (String key : List.of("entity", "need", "count", "gold", "xp")) progress.questVariables().remove(P + key);
        data.setDirty();
    }

    public static String turnIn(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.progress(player.getUUID());
        Active active = active(progress);
        if (active == null) return "ยังไม่ได้รับงานค่าหัว";
        if (!active.complete()) return "ยังล่าไม่ครบ (" + active.count() + "/" + active.need() + ")";
        long day = SeasonService.today();
        int done = doneToday(progress, day);
        abandon(data, progress);
        progress.questVariables().put(P + "done_day", Long.toString(day));
        progress.questVariables().put(P + "done", Integer.toString(done + 1));
        progress.rpg().currency(GoldCoinService.CURRENCY, active.gold());
        if (active.xp() > 0) ProgressService.addExperience(player, data, active.xp(), true);
        TitleCounters.add(progress.questVariables(), "rpg.title.bounties", 1);
        data.setDirty();
        RotasNetwork.syncProgress(player);
        return null;
    }

    public static boolean matches(String target, EntityType<?> type) {
        if (target.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(target.substring(1));
            return tag != null && type.is(TagKey.create(Registries.ENTITY_TYPE, tag));
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return target.equals(id.toString());
    }

    public static void onKill(ServerPlayer killer, RotasData data, LivingEntity victim) {
        PlayerProgress progress = data.peek(killer.getUUID());
        if (progress == null) return;
        Active active = active(progress);
        if (active == null || active.complete() || !matches(active.entity(), victim.getType())) return;
        int count = active.count() + 1;
        progress.questVariables().put(P + "count", Integer.toString(count));
        data.setDirty();
        boolean done = count >= active.need();
        killer.displayClientMessage(Component.literal((done ? "ค่าหัวครบแล้ว! กลับไปหานักล่าค่าหัว " : "ค่าหัว ")
                + count + "/" + active.need()).withStyle(done ? ChatFormatting.GOLD : ChatFormatting.YELLOW), true);
    }

    public static String targetName(String target) {
        if (target.startsWith("#")) return target;
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) return target;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription().getString()).orElse(target);
    }
}
