package net.schwarz.rotasutils.client.screen.admin;

final class MobStatFormat {
    private MobStatFormat() {
    }

    static String fmt(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e9) return trimFixed(value / 1e9, 2) + "B";
        if (abs >= 1e6) return trimFixed(value / 1e6, 2) + "M";
        if (abs >= 1e4) return trimFixed(value / 1e3, 1) + "K";
        if (abs >= 100) return String.valueOf(Math.round(value));
        return trimFixed(value, 2);
    }

    private static String trimFixed(double value, int places) {
        String text = String.format(java.util.Locale.ROOT, "%." + places + "f", value);
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }
}
