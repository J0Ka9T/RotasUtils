package net.schwarz.rotasutils.entity;

public final class SmoothAge {
    private static final int SNAP = 4;
    private int local = -1;

    public int tick(int server) {
        if (local < 0 || Math.abs(server - local) > SNAP) {
            local = server;
        } else {
            local++;
        }
        return local;
    }

    public int value(int server) {
        return local < 0 ? server : local;
    }
}
