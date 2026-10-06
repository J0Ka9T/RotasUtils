package net.schwarz.rotasutils.server;

public record CheckResult(boolean pass, boolean blocking, String label, String detail) {
    public static CheckResult pass(String label) {
        return new CheckResult(true, true, label, "");
    }

    public static CheckResult fail(String label, String detail) {
        return new CheckResult(false, true, label, detail);
    }

    public static CheckResult advisory(boolean met, String label, String detail) {
        return new CheckResult(met, false, label, detail);
    }
}
