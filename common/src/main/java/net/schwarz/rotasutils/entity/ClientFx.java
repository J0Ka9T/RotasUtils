package net.schwarz.rotasutils.entity;

/**
 * Client-side effects that common entity code may ask for without referencing client classes.
 * Servers keep the no-op; the client installs the real one at start-up.
 */
public final class ClientFx {
    /** A camera quake of {@code degrees} at a world position, fading out over {@code falloff} blocks. */
    public interface Quake {
        void at(double x, double y, double z, float degrees, double falloff);
    }

    /**
     * One tick of a channelled beam's state, for client-only readouts (the crosshair charge/heat
     * gauge and the lens punch when it erupts). Sent every client tick for every beam, so the
     * implementation filters by {@code ownerId}; it also has to fade the readout out on its own,
     * because a dying beam simply stops reporting.
     *
     * <p>{@code heat} is the gun's heat while firing, except for the lance - which never heats -
     * where it is the fraction of its own discharge already spent, so one gauge serves both.</p>
     */
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
