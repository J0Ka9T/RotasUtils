package net.schwarz.rotasutils.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picking the rule that answers for one event, and the arithmetic of what it pays.
 *
 * <p>A rule is a type plus a filter. The filter is empty for the plain entry every type starts with,
 * {@code namespace:*} for "anything from that mod", or an exact id. The most specific rule that matches
 * wins, so a pack can say "kills pay one rate, Cataclysm bosses pay another" without the two fighting.
 * Everything here is pure, so the precedence a server depends on is unit-tested.</p>
 */
public final class EventRules {
    private EventRules() {
    }

    /** What one rule does when its event happens. */
    public enum Announce {
        /** Nobody is told. */
        OFF,
        /** Only the player it happened to. */
        PLAYER,
        /** Everyone on the server. */
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

    /**
     * How well a filter fits a subject: 3 for an exact id, 2 for a namespace, 1 for "anything", and 0
     * when it does not fit at all.
     */
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

    /** True when a filter is written in a shape this matcher understands. */
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

    /**
     * The rule that answers for this event: the most specific match, and among equals the one written
     * first. Null when no rule covers the type at all.
     */
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

    /** Experience after a rule's multiplier and flat bonus, never negative and never overflowing. */
    public static long award(long baseXp, double multiplier, long flat) {
        double scaled = Math.max(0, baseXp) * (Double.isFinite(multiplier) ? Math.max(0, multiplier) : 1);
        double total = scaled + Math.max(0, flat);
        if (!Double.isFinite(total) || total >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(total);
    }

    /** True when this player may trigger the rule again, given when they last did. */
    public static boolean offCooldown(long nowSeconds, long lastSeconds, int cooldownSeconds) {
        return cooldownSeconds <= 0 || lastSeconds <= 0 || nowSeconds - lastSeconds >= cooldownSeconds;
    }

    /** Every namespace that appears in a list of ids, for the filter picker. */
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
