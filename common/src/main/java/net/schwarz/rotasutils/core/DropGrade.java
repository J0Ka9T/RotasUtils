package net.schwarz.rotasutils.core;

import java.util.Locale;

public enum DropGrade {
    COMMON(0xFFB6A17C),
    MEDIUM(0xFF86C05C),
    RARE(0xFF5AA9E6),
    EPIC(0xFFB07CE8);

    private final int color;

    DropGrade(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String tableId() {
        return "rotas:drop_" + key();
    }

    public String nameKey() {
        return "rotasutils.drop.grade." + key();
    }

    public static DropGrade byKey(String key) {
        if (key != null) {
            for (DropGrade grade : values()) {
                if (grade.key().equalsIgnoreCase(key)) {
                    return grade;
                }
            }
        }
        return COMMON;
    }
}
