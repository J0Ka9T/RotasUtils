package net.schwarz.rotasutils.client.screen.admin;

public final class HouseTimeFields {
    public static final long MILLIS_PER_MINUTE = 60_000L;
    public static final long MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE;
    public static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;

    private HouseTimeFields() {}

    public record Result(boolean valid, long millis, String field, String message) {
        public Result {
            if (field == null || message == null || millis < 0L) {
                throw new IllegalArgumentException("Invalid time-field result");
            }
            if (valid && (!field.isEmpty() || !message.isEmpty())) {
                throw new IllegalArgumentException("Valid time-field results cannot contain an error");
            }
            if (!valid && (field.isBlank() || message.isBlank())) {
                throw new IllegalArgumentException("Invalid time-field results require an error");
            }
        }
    }

    public record Parts(long days, long hours, long minutes, long remainderMillis) {
        public Parts(long days, long hours, long minutes) {
            this(days, hours, minutes, 0L);
        }

        public Parts {
            if (days < 0L || hours < 0L || hours >= 24L || minutes < 0L || minutes >= 60L
                    || remainderMillis < 0L || remainderMillis >= MILLIS_PER_MINUTE) {
                throw new IllegalArgumentException("Invalid time parts");
            }
        }
    }

    public static Result toMillis(String daysText, String hoursText, String minutesText) {
        Long days = parseNonNegative(daysText);
        if (days == null) return invalid("days", "Days must be a non-negative whole number");
        Long hours = parseNonNegative(hoursText);
        if (hours == null) return invalid("hours", "Hours must be a non-negative whole number");
        Long minutes = parseNonNegative(minutesText);
        if (minutes == null) return invalid("minutes", "Minutes must be a non-negative whole number");

        long dayMillis;
        try {
            dayMillis = Math.multiplyExact(days, MILLIS_PER_DAY);
        } catch (ArithmeticException overflow) {
            return invalid("days", "Days exceed the supported time range");
        }
        long hourMillis;
        try {
            hourMillis = Math.multiplyExact(hours, MILLIS_PER_HOUR);
        } catch (ArithmeticException overflow) {
            return invalid("hours", "Hours exceed the supported time range");
        }
        long minuteMillis;
        try {
            minuteMillis = Math.multiplyExact(minutes, MILLIS_PER_MINUTE);
        } catch (ArithmeticException overflow) {
            return invalid("minutes", "Minutes exceed the supported time range");
        }
        long total;
        try {
            total = Math.addExact(dayMillis, hourMillis);
        } catch (ArithmeticException overflow) {
            return invalid("hours", "Time values exceed the supported range");
        }
        try {
            total = Math.addExact(total, minuteMillis);
        } catch (ArithmeticException overflow) {
            return invalid("minutes", "Time values exceed the supported range");
        }
        return new Result(true, total, "", "");
    }

    public static Parts fromMillis(long millis) {
        if (millis < 0L) throw new IllegalArgumentException("Millis cannot be negative");
        long days = millis / MILLIS_PER_DAY;
        long afterDays = millis % MILLIS_PER_DAY;
        long hours = afterDays / MILLIS_PER_HOUR;
        long afterHours = afterDays % MILLIS_PER_HOUR;
        long minutes = afterHours / MILLIS_PER_MINUTE;
        long remainderMillis = afterHours % MILLIS_PER_MINUTE;
        return new Parts(days, hours, minutes, remainderMillis);
    }

    private static Long parseNonNegative(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            long value = Long.parseLong(raw.trim());
            return value >= 0L ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static Result invalid(String field, String message) {
        return new Result(false, 0L, field, message);
    }
}
