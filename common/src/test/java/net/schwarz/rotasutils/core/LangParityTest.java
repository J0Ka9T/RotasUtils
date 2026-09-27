package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped interface languages.
 *
 * <p>The mod reads {@code assets/rotasutils/lang/*.json} at runtime, so a key that exists in one
 * language but not another silently renders its raw key (or the English default) for players on
 * the other language. These checks keep the two files in lockstep and stop a translation from
 * dropping a {@code %s} placeholder that the code fills in.</p>
 */
class LangParityTest {
    private static final String[] LANGUAGES = {"en_us", "th_th"};
    /** Minecraft translation placeholders: {@code %s}, {@code %d}, {@code %f} and indexed forms. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sdf]");

    private static JsonObject load(String language) {
        String path = "/assets/rotasutils/lang/" + language + ".json";
        try (InputStream in = LangParityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing language resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception failure) {
            throw new AssertionError("could not read " + path, failure);
        }
    }

    private static List<String> placeholders(String value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    @Test void everyLanguageCarriesTheSameKeySet() {
        TreeSet<String> expected = new TreeSet<>(load(LANGUAGES[0]).keySet());
        assertFalse(expected.isEmpty(), "the shipped language must not be empty");
        for (int i = 1; i < LANGUAGES.length; i++) {
            assertEquals(expected, new TreeSet<>(load(LANGUAGES[i]).keySet()),
                    LANGUAGES[i] + " must carry exactly the same keys as " + LANGUAGES[0]);
        }
    }

    @Test void noLanguageHasBlankOrNonStringValues() {
        for (String language : LANGUAGES) {
            JsonObject json = load(language);
            json.entrySet().forEach(entry -> {
                assertEquals(com.google.gson.JsonPrimitive.class, entry.getValue().getClass(),
                        language + " value must be a string: " + entry.getKey());
                assertFalse(entry.getValue().getAsString().isBlank(),
                        language + " has a blank value for " + entry.getKey());
            });
        }
    }

    @Test void placeholdersMatchAcrossEveryLanguage() {
        JsonObject english = load("en_us");
        for (int i = 1; i < LANGUAGES.length; i++) {
            JsonObject other = load(LANGUAGES[i]);
            for (String key : english.keySet()) {
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(other.get(key).getAsString()),
                        "placeholder mismatch for " + key + " in " + LANGUAGES[i]);
            }
        }
    }

    /** {@code true} when the text uses Thai script, so an untranslated English value is caught. */
    private static boolean isThai(String text) {
        return text.codePoints().anyMatch(point -> point >= 0x0E00 && point <= 0x0E7F);
    }

    @Test void everyDialogueFieldAndTemplateIsNamedInEveryLanguage() {
        for (String language : LANGUAGES) {
            JsonObject json = load(language);
            for (String field : NpcDialogueGuide.fields()) {
                String key = NpcDialogueGuide.labelKey(field);
                assertTrue(json.has(key), language + " is missing the dialogue field label " + key);
            }
            for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
                String base = "rotasutils.dialogue.template."
                        + template.name().toLowerCase(java.util.Locale.ROOT);
                assertTrue(json.has(base), language + " is missing " + base);
                assertTrue(json.has(base + ".hint"), language + " is missing " + base + ".hint");
            }
        }
    }

    @Test void thaiNamesTheDialogueInThaiRatherThanEnglish() {
        JsonObject thai = load("th_th");
        for (String field : NpcDialogueGuide.fields()) {
            String key = NpcDialogueGuide.labelKey(field);
            assertTrue(isThai(thai.get(key).getAsString()),
                    "the Thai dialogue field label for " + field + " is still English");
        }
        for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
            String base = "rotasutils.dialogue.template."
                    + template.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(isThai(thai.get(base).getAsString()),
                    "the Thai name for " + template + " is still English");
        }
        for (String key : thai.keySet()) {
            if (key.startsWith("rotasutils.dialogue.problem.") || key.startsWith("rotasutils.dialogue.btn.")
                    || key.startsWith("rotasutils.dialogue.msg.")) {
                assertTrue(isThai(thai.get(key).getAsString()),
                        "the Thai dialogue text for " + key + " is still English");
            }
        }
    }
}
