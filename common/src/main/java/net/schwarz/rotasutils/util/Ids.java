package net.schwarz.rotasutils.util;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class Ids {
    private Ids() {
    }

    public static String slug(String input) {
        String base = input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        base = base.replaceAll("^_+", "").replaceAll("_+$", "");
        if (base.isEmpty()) {
            base = "entry";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        return base;
    }

    public static String unique(String name, Set<String> taken) {
        String base = slug(name);
        if (!taken.contains(base)) {
            return base;
        }
        for (int i = 2; i < 10000; i++) {
            String candidate = base + "_" + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }
}
