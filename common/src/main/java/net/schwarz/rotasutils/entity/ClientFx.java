package net.schwarz.rotasutils.entity;

public final class ClientFx {
    public interface Quake {
        void at(double x, double y, double z, float degrees, double falloff);
    }

    public interface BeamState {
        void tick(int ownerId, int mode, float charge, float heat, boolean firing);
    }

    private static volatile Quake quake = (x, y, z, degrees, falloff) -> { };
    private static volatile BeamState beamState = (ownerId, mode, charge, heat, firing) -> { };

    private ClientFx() {
    }

    public static void install(Quake implementation) {
        quake = implementation;
    }

    public static void installBeamState(BeamState implementation) {
        beamState = implementation;
    }

    public static void quake(double x, double y, double z, float degrees, double falloff) {
        quake.at(x, y, z, degrees, falloff);
    }

    public static void beamState(int ownerId, int mode, float charge, float heat, boolean firing) {
        beamState.tick(ownerId, mode, charge, heat, firing);
    }
}
