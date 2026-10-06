package net.schwarz.rotasutils.house;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HouseNaming {
    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)\\s*$");
    private static final int MAX_ID = 64;

    private HouseNaming() {
    }

    public static String slug(String name) {
        if (name == null) {
            return "house";
        }
        StringBuilder out = new StringBuilder();
        boolean gap = false;
        for (char c : name.strip().toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-') {
                if (gap && out.length() > 0) {
                    out.append('_');
                }
                gap = false;
                out.append(c);
            } else {
                gap = true;
            }
        }
        String id = out.length() > MAX_ID ? out.substring(0, MAX_ID) : out.toString();
        return id.isEmpty() ? "house" : id;
    }

    public static String uniqueId(String name, Collection<String> taken) {
        Set<String> used = Set.copyOf(taken);
        String base = slug(name);
        if (!used.contains(base)) {
            return base;
        }
        for (int i = 2; i < 10_000; i++) {
            String suffix = "_" + i;
            String candidate = (base.length() + suffix.length() > MAX_ID ? base.substring(0, MAX_ID - suffix.length()) : base) + suffix;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    public static String next(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        Matcher m = TRAILING_NUMBER.matcher(name.strip());
        if (m.matches() && !m.group(1).isBlank()) {
            String digits = m.group(2);
            if (digits.length() > 9) {
                return name.strip() + " 2";
            }
            long value = Long.parseLong(digits) + 1;
            String padded = String.format(Locale.ROOT, "%0" + digits.length() + "d", value);
            return m.group(1) + padded;
        }
        return name.strip() + " 2";
    }
}
