package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.title.TitleCounters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BuffService {
    public enum Buff {
        RESTED("พักผ่อนเต็มที่"),
        FORTUNE_WARRIOR("ดวงนักรบ"),
        FORTUNE_ARTISAN("ดวงช่างฝีมือ"),
        FORTUNE_WANDERER("ดวงนักเดินทาง"),
        FORTUNE_SCHOLAR("ดวงนักปราชญ์"),
        BAD_OMEN("ลางร้าย");

        public final String display;

        Buff(String display) {
            this.display = display;
        }

        String key() {
            return "rpg.buff." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private BuffService() {
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public static void grant(PlayerProgress progress, Buff buff, long seconds) {
        progress.questVariables().put(buff.key(), Long.toString(now() + Math.max(0, seconds)));
    }

    public static void clear(PlayerProgress progress, Buff buff) {
        progress.questVariables().remove(buff.key());
    }

    public static long remaining(PlayerProgress progress, Buff buff) {
        return Math.max(0, TitleCounters.read(progress.questVariables(), buff.key()) - now());
    }

    public static boolean active(PlayerProgress progress, Buff buff) {
        return remaining(progress, buff) > 0;
    }

    public static void clearFortunes(PlayerProgress progress) {
        for (Buff buff : Buff.values()) {
            if (buff.name().startsWith("FORTUNE_") || buff == Buff.BAD_OMEN) clear(progress, buff);
        }
    }

    public static double xpMultiplier(ServerPlayer player, RotasData data, XpSource source) {
        PlayerProgress progress = data.peek(player.getUUID());
        if (progress == null) return 1;
        Map<String, String> variables = progress.questVariables();
        if (variables.isEmpty()) return 1;
        SeasonRules.NpcServiceRules rules = SeasonService.rules(data).npcServices;
        double bonus = 0;
        if (active(progress, Buff.RESTED)) bonus += rules.restedXp;
        Buff favoured = switch (source) {
            case MOB_KILL, BOSS_KILL, PLAYER_KILL, ASSIST, DUNGEON_COMPLETE -> Buff.FORTUNE_WARRIOR;
            case MINING, CRAFTING, SMELTING, FISHING, FARMING, TRADING -> Buff.FORTUNE_ARTISAN;
            case DISCOVERY, ADVANCEMENT, SERVER_EVENT -> Buff.FORTUNE_WANDERER;
            case QUEST_COMPLETE, OPTIONAL_OBJECTIVE, FIRST_COMPLETION -> Buff.FORTUNE_SCHOLAR;
            default -> null;
        };
        if (favoured != null && active(progress, favoured)) bonus += rules.fortuneXp;
        return 1 + bonus;
    }

    public static List<String> describe(PlayerProgress progress) {
        List<String> lines = new ArrayList<>();
        for (Buff buff : Buff.values()) {
            long left = remaining(progress, buff);
            if (left > 0) lines.add(buff.display + " · อีก " + (left / 60 + 1) + " นาที");
        }
        return lines;
    }
}
