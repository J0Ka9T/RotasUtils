package net.schwarz.rotasutils.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RotasUtils always speaks Thai, whatever language the game itself is set to.
 *
 * <p>The Thai table is read straight from the {@code th_th.json} bundled in the mod jar, so it works
 * the same on a client, an integrated server and a dedicated server - a server can build Thai chat
 * and feedback text without knowing anything about the receiving client. Vanilla Minecraft and other
 * mods are untouched: only keys that exist in RotasUtils' own Thai file are answered here.</p>
 *
 * <p>Formatting follows Minecraft's translation rules ({@code %s}, {@code %1$s}, {@code %%}), and
 * also accepts {@code %d}/{@code %f} the way the parity test allows them.</p>
 */
public final class ThaiText {
    private static final String PATH = "/assets/rotasutils/lang/th_th.json";
    private static final String PHRASES = "/assets/rotasutils/thai/phrases.json";
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([sdf%])");
    private static volatile Map<String, String> table;

    private ThaiText() {
    }

    /** True when RotasUtils ships a Thai text for this key. */
    public static boolean has(String key) {
        return key != null && table().containsKey(key);
    }

    /** The raw Thai text for a key, or null when the key is not RotasUtils'. */
    public static String get(String key) {
        return key == null ? null : table().get(key);
    }

    /** Formatted Thai text; the key itself when it is unknown, so a missing entry is visible. */
    public static String t(String key, Object... args) {
        String pattern = get(key);
        return pattern == null ? key : format(pattern, args);
    }

    /**
     * Thai name for an enum constant shown in the UI, from {@code rotasutils.enum.<group>.<constant>}.
     * The English label stays as the fallback, so a constant without a Thai entry still reads.
     * Only for display: never compare or store the result.
     */
    public static String label(String group, Enum<?> value, String english) {
        String thai = get("rotasutils.enum." + group + "." + value.name().toLowerCase(java.util.Locale.ROOT));
        return thai == null ? english : thai;
    }

    /** A literal component holding the formatted Thai text, for chat, buttons and tooltips. */
    public static net.minecraft.network.chat.MutableComponent c(String key, Object... args) {
        return Component.literal(t(key, args));
    }

    /** Minecraft-style placeholder substitution; missing arguments are left as the placeholder. */
    public static String format(String pattern, Object... args) {
        if (pattern.indexOf('%') < 0) {
            return pattern;
        }
        Matcher matcher = PLACEHOLDER.matcher(pattern);
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int next = 0;
        while (matcher.find()) {
            String replacement;
            if ("%".equals(matcher.group(2))) {
                replacement = "%";
            } else {
                int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
                replacement = args != null && index >= 0 && index < args.length
                        ? text(args[index]) : matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String text(Object value) {
        if (value instanceof Component component) {
            return component.getString();
        }
        return String.valueOf(value);
    }

    /**
     * Thai for an English interface phrase, used where screens draw text that was written in English
     * and is also matched by that English text elsewhere (row labels, field names). Translating at draw
     * time keeps that matching intact. Entries in {@code thai/phrases.json} are either exact phrases or
     * patterns with {@code {}} holes, whose captured parts are translated again. Text with no entry is
     * returned unchanged, so Thai text and names pass straight through.
     */
    public static String phrase(String english) {
        if (english == null || english.isEmpty()) {
            return english;
        }
        Phrases loaded = phrases();
        String cached = loaded.cache.get(english);
        if (cached != null) {
            return cached;
        }
        String result = translatePhrase(loaded, english, 0);
        if (loaded.cache.size() > 8192) {
            loaded.cache.clear();
        }
        loaded.cache.put(english, result);
        return result;
    }

    private static String translatePhrase(Phrases loaded, String english, int depth) {
        int start = 0;
        int end = english.length();
        while (start < end && Character.isWhitespace(english.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(english.charAt(end - 1))) {
            end--;
        }
        if (start == end || !hasLatinLetter(english, start, end)) {
            return english;
        }
        String core = english.substring(start, end);
        String thai = loaded.exact.get(core);
        if (thai == null && Character.isLowerCase(core.charAt(0))) {
            // Screens sometimes lower-case a label inside a sentence ("Search quests").
            thai = loaded.exact.get(Character.toUpperCase(core.charAt(0)) + core.substring(1));
        }
        if (thai == null && depth < 3) {
            for (PhraseRule rule : loaded.rules) {
                Matcher matcher = rule.pattern().matcher(core);
                if (!matcher.matches()) {
                    continue;
                }
                StringBuilder out = new StringBuilder(rule.thai().length() + 16);
                int group = 1;
                int cursor = 0;
                int hole;
                while ((hole = rule.thai().indexOf("{}", cursor)) >= 0) {
                    out.append(rule.thai(), cursor, hole);
                    if (group <= matcher.groupCount()) {
                        out.append(translatePhrase(loaded, matcher.group(group++), depth + 1));
                    }
                    cursor = hole + 2;
                }
                out.append(rule.thai().substring(cursor));
                thai = out.toString();
                break;
            }
        }
        return thai == null ? english : english.substring(0, start) + thai + english.substring(end);
    }

    private static boolean hasLatinLetter(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    private record PhraseRule(Pattern pattern, String thai, int weight) {
    }

    private static final class Phrases {
        final Map<String, String> exact;
        final java.util.List<PhraseRule> rules;
        final Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();

        Phrases(Map<String, String> exact, java.util.List<PhraseRule> rules) {
            this.exact = exact;
            this.rules = rules;
        }
    }

    private static volatile Phrases phrases;

    private static Phrases phrases() {
        Phrases loaded = phrases;
        if (loaded == null) {
            synchronized (ThaiText.class) {
                loaded = phrases;
                if (loaded == null) {
                    loaded = loadPhrases();
                    phrases = loaded;
                }
            }
        }
        return loaded;
    }

    private static Phrases loadPhrases() {
        Map<String, String> exact = new HashMap<>();
        java.util.List<PhraseRule> rules = new java.util.ArrayList<>();
        try (InputStream in = ThaiText.class.getResourceAsStream(PHRASES)) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    String key = entry.getKey();
                    String value = entry.getValue().getAsString();
                    if (!key.contains("{}")) {
                        exact.put(key, value);
                        continue;
                    }
                    StringBuilder regex = new StringBuilder("^");
                    int cursor = 0;
                    int hole;
                    while ((hole = key.indexOf("{}", cursor)) >= 0) {
                        regex.append(Pattern.quote(key.substring(cursor, hole))).append("(.+?)");
                        cursor = hole + 2;
                    }
                    regex.append(Pattern.quote(key.substring(cursor))).append('$');
                    rules.add(new PhraseRule(Pattern.compile(regex.toString(), Pattern.DOTALL), value,
                            key.replace("{}", "").length()));
                }
            }
        } catch (Exception ignored) {
            // A broken phrase table leaves text in English rather than breaking every screen.
        }
        // The most specific pattern wins: "Rewards at level {}" before "{} level {}".
        rules.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return new Phrases(Collections.unmodifiableMap(exact), java.util.List.copyOf(rules));
    }

    private static Map<String, String> table() {
        Map<String, String> loaded = table;
        if (loaded == null) {
            synchronized (ThaiText.class) {
                loaded = table;
                if (loaded == null) {
                    loaded = load();
                    table = loaded;
                }
            }
        }
        return loaded;
    }

    private static Map<String, String> load() {
        try (InputStream in = ThaiText.class.getResourceAsStream(PATH)) {
            if (in == null) {
                return Collections.emptyMap();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> values = new HashMap<>(json.size() * 2);
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    values.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return Collections.unmodifiableMap(values);
        } catch (Exception failure) {
            return Collections.emptyMap();
        }
    }
}
