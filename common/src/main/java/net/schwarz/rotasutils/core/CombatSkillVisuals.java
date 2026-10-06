package net.schwarz.rotasutils.core;

import java.util.Locale;

public final class CombatSkillVisuals {
    public enum Skill {
        MOMENTUM(0xFFAC42), LAST_BASTION(0xFFE28A), PREDATORS_MARK(0xD056FF),
        AFTERIMAGE(0x65E4FF), ARCANE_EDGE(0xAC79FF), HUNTERS_PATIENCE(0x91FFD0),
        IRON_REBOUND(0xBCD8EF), STORM_STEP(0x62B9FF), EXECUTIONERS_OATH(0xFF4863),
        BLOOD_TITHE(0xEF345B), FROSTBIND(0x99EEFF), GRAVITY_WELL(0x9C68FF),
        SUNFIRE_BRAND(0xFFC65B), ECHO_STRIKE(0x81FFE8), MYRIAD_SWORDS_RETURN(0xA6F5FF),
        DHARMAKAYA(0x9FE9FF), WANJIAN(0xE0B25C);
        public final int colour;
        Skill(int colour) { this.colour = colour; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    private CombatSkillVisuals() { }
    public static Skill skill(String id) {
        if (id == null) return null;
        for (Skill skill : Skill.values()) if (skill.id().equals(id)) return skill;
        return null;
    }
    public static float scale(float value) {
        return Float.isFinite(value) ? Math.max(.25f, Math.min(4, value)) : 1;
    }
    public static int duration(int ticks) { return Math.max(8, Math.min(400, ticks)); }
    public static int life(Skill skill, int stage) {
        if (skill == Skill.MYRIAD_SWORDS_RETURN) return SwordConvergenceTimeline.LIFE;
        if (skill == Skill.DHARMAKAYA) return DharmakayaRules.DURATION_TICKS;
        if (skill == Skill.WANJIAN) return WanJianTimeline.LIFE;
        if (stage == 0) return switch (skill) {
            case PREDATORS_MARK -> 100;
            case AFTERIMAGE, IRON_REBOUND, STORM_STEP -> 60;
            case HUNTERS_PATIENCE -> 80;
            default -> 24;
        };
        return switch (skill) {
            case LAST_BASTION -> 36;
            case FROSTBIND, GRAVITY_WELL, SUNFIRE_BRAND -> 28;
            default -> 18;
        };
    }
    public static float alpha(float age, float life) {
        if (!Float.isFinite(age) || life <= 0 || age <= 0 || age >= life) return 0;
        return Math.min(1, age / 2) * Math.min(1, (life - age) / Math.min(10, life * .5f));
    }
}
