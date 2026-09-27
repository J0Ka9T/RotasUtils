package net.schwarz.rotasutils.title;

import java.util.Locale;
import java.util.Map;

/**
 * The tallies titles are earned from.
 *
 * <p>They live in the player's existing variable map, next to the quest variables, so they persist and
 * migrate with the record and need no new store. Only the entity types some title actually asks about
 * are ever counted: a server with no "kill 500 zombies" title never writes a zombie counter, so the
 * map cannot grow with the entity list of a large modpack.</p>
 */
public final class TitleCounters {
    /** Prefix of a per-entity kill tally, followed by the entity id. */
    public static final String KILL_PREFIX = "rpg.title.kill.";
    /** Monsters killed, of any kind. */
    public static final String KILL_ANY = "rpg.title.kill_any";
    /** Bosses killed. */
    public static final String KILL_BOSS = "rpg.title.kill_boss";
    /** Highest refine level this player has ever reached. */
    public static final String REFINE_BEST = "rpg.title.refine_best";
    /** Nemeses this player has slain. Always counted: there are few of them, so the tally stays small. */
    public static final String NEMESIS_SLAIN = "rpg.title.nemesis_slain";

    private TitleCounters() {
    }

    /** Reads a tally; anything unreadable counts as zero rather than failing a kill. */
    public static long read(Map<String, String> variables, String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        String stored = variables.get(key);
        if (stored == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(stored));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    /** Adds to a tally and returns the new total. */
    public static long add(Map<String, String> variables, String key, long delta) {
        long value = read(variables, key) + Math.max(0, delta);
        variables.put(key, Long.toString(value));
        return value;
    }

    /** Raises a "best ever" tally; a lower value never lowers what is stored. */
    public static long raise(Map<String, String> variables, String key, long value) {
        long current = read(variables, key);
        if (value <= current) {
            return current;
        }
        variables.put(key, Long.toString(Math.max(0, value)));
        return value;
    }

    /** The tally key for one entity type. */
    public static String killKey(String entityId) {
        return KILL_PREFIX + (entityId == null ? "" : entityId.toLowerCase(Locale.ROOT));
    }
}
