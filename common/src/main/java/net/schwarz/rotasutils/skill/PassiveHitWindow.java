package net.schwarz.rotasutils.skill;

import java.util.UUID;

public final class PassiveHitWindow {
    private UUID target;
    private int count;
    private long lastHit = Long.MIN_VALUE;
    private long readyAt;

    public int count() { return count; }

    public boolean finisherReady(UUID victim, long now, int required, int window) {
        return now >= readyAt && count >= required && victim.equals(target)
                && now >= lastHit && now - lastHit <= window;
    }

    public boolean hit(UUID victim, long now, int required, int window, int cooldown, boolean sameTarget) {
        if (now < readyAt) return false;
        if (lastHit == Long.MIN_VALUE || now < lastHit || now - lastHit > window
                || sameTarget && !victim.equals(target)) count = 0;
        target = victim;
        lastHit = now;
        if (++count < required) return false;
        count = 0;
        readyAt = now + cooldown;
        return true;
    }
}
