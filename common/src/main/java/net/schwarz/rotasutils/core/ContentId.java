package net.schwarz.rotasutils.core;

import java.util.Objects;
import java.util.regex.Pattern;

public record ContentId(String value) implements Comparable<ContentId> {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ContentId {
        Objects.requireNonNull(value, "content ID");
        if (value.length() > 160 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected namespaced content ID: " + value);
        }
        for (String segment : value.substring(value.indexOf(':') + 1).split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid content path: " + value);
            }
        }
    }

    @Override
    public int compareTo(ContentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
