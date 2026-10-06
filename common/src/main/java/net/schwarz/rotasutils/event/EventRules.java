package net.schwarz.rotasutils.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EventRules {
    private EventRules() {
    }

    public enum Announce {
        OFF,
        PLAYER,
        SERVER;

        public static Announce byName(String name) {
            if (name != null) {
                for (Announce value : values()) {
                    if (value.name().equalsIgnoreCase(name.trim())) {
                        return value;
                    }
                }
            }
            return OFF;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static int specificity(String filter, String subject) {
        String pattern = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        String value = subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);
        if (pattern.isEmpty() || pattern.equals("*")) {
            return 1;
        }
        if (pattern.endsWith(":*")) {
            String namespace = pattern.substring(0, pattern.length() - 2);
            return value.startsWith(namespace + ":") ? 2 : 0;
        }
        return pattern.equals(value) ? 3 : 0;
    }

    public static boolean validFilter(String filter) {
        String pattern = filter == null ? "" : filter.trim();
        if (pattern.isEmpty() || pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith(":*")) {
            return pattern.length() > 2 && !pattern.substring(0, pattern.length() - 2).contains(":");
        }
        return pattern.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    public static <T> T best(List<T> rules, java.util.function.Function<T, EventType> typeOf,
                             java.util.function.Function<T, String> filterOf,
                             EventType type, String subject) {
        T best = null;
        int bestScore = 0;
        for (T rule : rules) {
            if (typeOf.apply(rule) != type) {
                continue;
            }
            int score = specificity(filterOf.apply(rule), subject);
            if (score > bestScore) {
                best = rule;
                bestScore = score;
            }
        }
        return best;
    }

    public static long award(long baseXp, double multiplier, long flat) {
        double scaled = Math.max(0, baseXp) * (Double.isFinite(multiplier) ? Math.max(0, multiplier) : 1);
        double total = scaled + Math.max(0, flat);
        if (!Double.isFinite(total) || total >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(total);
    }

    public static boolean offCooldown(long nowSeconds, long lastSeconds, int cooldownSeconds) {
        return cooldownSeconds <= 0 || lastSeconds <= 0 || nowSeconds - lastSeconds >= cooldownSeconds;
    }

    public static List<String> namespaces(List<String> ids) {
        List<String> found = new ArrayList<>();
        for (String id : ids) {
            int colon = id == null ? -1 : id.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String namespace = id.substring(0, colon);
            if (!found.contains(namespace)) {
                found.add(namespace);
            }
        }
        found.sort(String::compareTo);
        return found;
    }
}
