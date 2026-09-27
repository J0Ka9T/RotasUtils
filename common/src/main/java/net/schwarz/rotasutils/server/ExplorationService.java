package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.ProgressionRewards;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.title.TitleCounters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EXP for seeing the world rather than grinding one spot: the first step into each zone, each waystone, each new
 * kind of monster, vanilla advancements, and distance travelled. Also keeps the variety bonus, which rewards a
 * player for mixing activities: fighting, gathering, crafting, exploring, questing and trading.
 */
public final class ExplorationService {
    private static final String VARIETY = "rpg.variety.";
    private static final String TRAVEL = "rpg.travel";

    private record Spot(String dimension, Vec3 pos) {
    }

    /** Where each online player stood a second ago; rebuilt from the online list so it never outlives a logout. */
    private static final Map<UUID, Spot> LAST = new HashMap<>();

    private ExplorationService() {
    }

    private static SeasonRules.ExplorationRules rules(RotasData data) {
        return SeasonService.rules(data).exploration;
    }

    private static void award(ServerPlayer player, RotasData data, XpSource source, String key, long base, String what) {
        SeasonRules.ExplorationRules rules = rules(data);
        if (!rules.enabled || base <= 0) return;
        long amount = ProgressionRewards.scaled(rules, base, data.progress(player.getUUID()).level());
        ProgressService.awardFromSource(player, data, source, key, amount);
        player.displayClientMessage(Component.literal("✦ " + what + "  +" + amount + " EXP").withStyle(ChatFormatting.AQUA), true);
    }

    /** First time a player sets foot in a zone. */
    public static void onZoneEntered(ServerPlayer player, RotasData data, ZoneDef zone) {
        PlayerProgress progress = data.progress(player.getUUID());
        String key = "rpg.discover.zone." + zone.id();
        if (progress.questVariables().containsKey(key)) return;
        progress.questVariables().put(key, "1");
        data.setDirty();
        SeasonRules.ExplorationRules rules = rules(data);
        award(player, data, XpSource.DISCOVERY, "zone:" + zone.id(),
                rules.zoneBase + (long) zone.recommendedMin() * rules.zonePerLevel, "ค้นพบ " + zone.name());
    }

    public static void onWaystoneDiscovered(ServerPlayer player, RotasData data, String name) {
        award(player, data, XpSource.DISCOVERY, "waystone:" + name, rules(data).waystoneXp, "หินวาร์ปใหม่ " + name);
        TitleService.onProgress(player, data);
    }

    public static void onNewMonster(ServerPlayer player, RotasData data, String entityId, String name) {
        award(player, data, XpSource.DISCOVERY, "bestiary:" + entityId, rules(data).newMonsterXp, "บันทึกมอนใหม่: " + name);
    }

    public static void onAdvancement(ServerPlayer player, Advancement advancement) {
        DisplayInfo display = advancement.getDisplay();
        // Recipe unlocks and hidden bookkeeping advancements have no display and pay nothing.
        if (display == null || !display.shouldAnnounceChat() && !display.shouldShowToast()) return;
        RotasData data = RotasData.get(player.server);
        long base = rules(data).advancementXp[Math.min(2, display.getFrame().ordinal())];
        award(player, data, XpSource.ADVANCEMENT, advancement.getId().toString(), base, display.getTitle().getString());
    }

    /** Once a second: distance travelled, in any way but teleporting. */
    public static void tick(MinecraftServer server, RotasData data) {
        SeasonRules.ExplorationRules rules = rules(data);
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            Spot now = new Spot(player.level().dimension().location().toString(), player.position());
            Spot before = LAST.put(player.getUUID(), now);
            if (!rules.enabled || before == null || !before.dimension().equals(now.dimension()) || player.isSpectator()) continue;
            double dx = now.pos().x - before.pos().x;
            double dz = now.pos().z - before.pos().z;
            double moved = Math.sqrt(dx * dx + dz * dz);
            // Faster than an elytra dive is a teleport, a warp or a respawn: not travel.
            if (moved < 0.5 || moved > 60) continue;
            PlayerProgress progress = data.progress(player.getUUID());
            long total = TitleCounters.add(progress.questVariables(), TRAVEL, Math.round(moved));
            long previous = total - Math.round(moved);
            if (total / rules.travelBlocks > previous / rules.travelBlocks) {
                award(player, data, XpSource.DISCOVERY, "travel:" + total / rules.travelBlocks, rules.travelXp,
                        "เดินทางครบ " + (total / 1000) + " กม.");
            }
        }
        LAST.keySet().retainAll(online);
    }

    // Variety --------------------------------------------------------------------------------------

    enum Kind { FIGHT, GATHER, CRAFT, EXPLORE, QUEST, TRADE }

    static Kind kind(XpSource source) {
        return switch (source) {
            case MOB_KILL, BOSS_KILL, PLAYER_KILL, ASSIST, DUNGEON_COMPLETE -> Kind.FIGHT;
            case MINING, FARMING, FISHING -> Kind.GATHER;
            case CRAFTING, SMELTING -> Kind.CRAFT;
            case DISCOVERY, ADVANCEMENT, SERVER_EVENT -> Kind.EXPLORE;
            case QUEST_COMPLETE, OPTIONAL_OBJECTIVE, FIRST_COMPLETION -> Kind.QUEST;
            case TRADING, HEALING -> Kind.TRADE;
            default -> null;
        };
    }

    /** Records this activity and returns the variety multiplier for it. */
    public static double variety(ServerPlayer player, RotasData data, XpSource source) {
        SeasonRules.ExplorationRules rules = rules(data);
        Kind kind = kind(source);
        if (!rules.enabled || kind == null) return 1;
        PlayerProgress progress = data.progress(player.getUUID());
        long now = System.currentTimeMillis() / 1000L;
        progress.questVariables().put(VARIETY + kind.name().toLowerCase(java.util.Locale.ROOT), Long.toString(now));
        return ProgressionRewards.variety(rules, recentKinds(progress, rules, now));
    }

    static int recentKinds(PlayerProgress progress, SeasonRules.ExplorationRules rules, long now) {
        long window = rules.varietyWindowMinutes * 60L;
        int kinds = 0;
        for (Kind kind : Kind.values()) {
            long last = TitleCounters.read(progress.questVariables(), VARIETY + kind.name().toLowerCase(java.util.Locale.ROOT));
            if (last > 0 && now - last <= window) kinds++;
        }
        return kinds;
    }

    /** "Variety +15% (4 activities)" for the service screens and the character sheet, or null. */
    public static String describe(RotasData data, PlayerProgress progress) {
        SeasonRules.ExplorationRules rules = rules(data);
        int kinds = recentKinds(progress, rules, System.currentTimeMillis() / 1000L);
        double multiplier = ProgressionRewards.variety(rules, kinds);
        if (multiplier <= 1) return null;
        return "หลากหลาย EXP +" + Math.round((multiplier - 1) * 100) + "% (" + kinds + " กิจกรรม)";
    }
}
