package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.title.TitleCounters;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * NPCs as people. Friendship grows by talking to an NPC each day and by using its services, and each level
 * takes a little off its prices. Rumours are the server's own recent news - rare titles, milestones, starborn
 * foals - which innkeepers, guards and fortune tellers pass on.
 *
 * <p>Cheap by design: friendship is one number per player and NPC in the player's server-only variables, touched
 * only when they interact; rumours are a short in-memory list, gone on restart like real gossip.</p>
 */
public final class NpcSocial {
    private static final String KEY = "rpg.friend.";
    private static final int RUMOR_LIMIT = 20;
    private static final Deque<String> RUMORS = new ArrayDeque<>();
    private static final String[] NAMES = {"คนแปลกหน้า", "คนรู้จัก", "เพื่อน", "เพื่อนสนิท", "สหายร่วมชะตา"};

    private NpcSocial() {
    }

    private static SeasonRules.NpcSocialRules rules(RotasData data) {
        return SeasonService.rules(data).npcSocial;
    }

    // Friendship -----------------------------------------------------------------------------------

    public static long points(PlayerProgress progress, NpcDef npc) {
        return TitleCounters.read(progress.questVariables(), KEY + npc.id());
    }

    public static int level(SeasonRules.NpcSocialRules rules, long points) {
        int level = 0;
        for (int need : rules.levels) if (points >= need) level++;
        return Math.min(level, NAMES.length - 1);
    }

    public static int level(RotasData data, PlayerProgress progress, NpcDef npc) {
        return rules(data).friendship ? level(rules(data), points(progress, npc)) : 0;
    }

    /** "♥♥♡♡ เพื่อน (120/250)" for the service screen header. */
    public static String describe(RotasData data, PlayerProgress progress, NpcDef npc) {
        SeasonRules.NpcSocialRules rules = rules(data);
        if (!rules.friendship) return "";
        long points = points(progress, npc);
        int level = level(rules, points);
        StringBuilder hearts = new StringBuilder();
        for (int i = 1; i < NAMES.length; i++) hearts.append(i <= level ? "♥" : "♡");
        String next = level < rules.levels.length ? " (" + points + "/" + rules.levels[level] + ")" : "";
        int discount = (int) Math.round(level * rules.discountPerLevel * 100);
        return hearts + " " + NAMES[level] + next + (discount > 0 ? " · ลด " + discount + "%" : "");
    }

    /** A service's price after this NPC's friendship discount. */
    public static long price(RotasData data, PlayerProgress progress, NpcDef npc, long cost) {
        if (cost <= 0) return cost;
        double discount = level(data, progress, npc) * rules(data).discountPerLevel;
        return Math.max(1, Math.round(cost * (1 - discount)));
    }

    private static void gain(ServerPlayer player, RotasData data, NpcDef npc, long amount) {
        if (amount <= 0) return;
        SeasonRules.NpcSocialRules rules = rules(data);
        PlayerProgress progress = data.progress(player.getUUID());
        int before = level(rules, points(progress, npc));
        long after = TitleCounters.add(progress.questVariables(), KEY + npc.id(), amount);
        data.setDirty();
        int level = level(rules, after);
        if (level > before) {
            player.sendSystemMessage(Component.literal("♥ " + npc.name() + " มองท่านเป็น" + NAMES[level] + "แล้ว"
                    + (rules.discountPerLevel > 0 ? " (ส่วนลด " + Math.round(level * rules.discountPerLevel * 100) + "%)" : ""))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            Fx.fountain(player, ParticleTypes.HEART, 4 + level * 2);
        }
    }

    /** The first talk of the day with an NPC. */
    public static void onTalk(ServerPlayer player, RotasData data, NpcDef npc) {
        SeasonRules.NpcSocialRules rules = rules(data);
        if (!rules.friendship || rules.chatPoints <= 0) return;
        if (data.addCounter("chat|" + player.getUUID() + "|" + npc.id(), SeasonService.today(), 1, 1)) {
            gain(player, data, npc, rules.chatPoints);
        }
    }

    /** A paid service; capped per day so buying potions in bulk is not the way to a friend. */
    public static void onService(ServerPlayer player, RotasData data, NpcDef npc) {
        SeasonRules.NpcSocialRules rules = rules(data);
        if (!rules.friendship || rules.servicePoints <= 0) return;
        if (data.addCounter("serve|" + player.getUUID() + "|" + npc.id(), SeasonService.today(),
                rules.servicePointsPerDay, rules.servicePoints)) {
            gain(player, data, npc, rules.servicePoints);
        }
    }

    // Rumours --------------------------------------------------------------------------------------

    /** Something worth talking about happened on the server. */
    public static synchronized void rumor(String text) {
        if (text == null || text.isBlank()) return;
        RUMORS.addFirst(text);
        while (RUMORS.size() > RUMOR_LIMIT) RUMORS.removeLast();
    }

    public static synchronized List<String> recentRumors(int count) {
        List<String> out = new ArrayList<>();
        for (String rumor : RUMORS) {
            if (out.size() >= count) break;
            out.add(rumor);
        }
        return out;
    }

    public static synchronized void clear() {
        RUMORS.clear();
    }

    public static boolean rumorsOn(RotasData data) {
        return rules(data).rumors && rules(data).rumorsShown > 0;
    }

    public static int rumorsShown(RotasData data) {
        return rules(data).rumorsShown;
    }
}
