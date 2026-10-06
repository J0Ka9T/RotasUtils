package net.schwarz.rotasutils.house;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HouseConfigPresets {
    private HouseConfigPresets() {
    }

    private static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    public record Preset(String id, String label, String blurb, long paymentIntervalMillis, long reminderLeadMillis, long graceMillis,
                         int buyoutMultiplier, int baseMemberLimit, long memberSlotPrice, int maxPurchasedMemberSlots) {
        public Preset {
            if (reminderLeadMillis >= paymentIntervalMillis || paymentIntervalMillis < MINUTE) {
                throw new IllegalArgumentException("Preset " + id + " reminds after rent is due");
            }
        }

        public HouseConfig applyTo(HouseConfig config) {
            return new HouseConfig(config.currency(), paymentIntervalMillis, reminderLeadMillis, graceMillis, buyoutMultiplier,
                    baseMemberLimit, memberSlotPrice, maxPurchasedMemberSlots, config.tiers(), config.revision());
        }
    }

    public static final List<Preset> ALL = List.of(
            new Preset("relaxed", "Relaxed", "Weekly rent, three days of grace, a cheap buyout and room for a big household.",
                    7 * DAY, 2 * DAY, 3 * DAY, 8, 8, 0, 0),
            new Preset("standard", "Standard", "Rent every three days, reminded a day ahead, an hour of grace.",
                    3 * DAY, DAY, HOUR, 10, 5, 0, 0),
            new Preset("strict", "Strict", "Daily rent, six hours of grace, a small household you can grow by paying.",
                    DAY, 6 * HOUR, 6 * HOUR, 12, 3, 500, 5),
            new Preset("monthly", "Monthly", "Rent every thirty days, reminded three days ahead, two days of grace.",
                    30 * DAY, 3 * DAY, 2 * DAY, 6, 6, 0, 0));

    public static Optional<Preset> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(p -> p.id().equals(wanted)).findFirst();
    }

    public static Preset after(HouseConfig config) {
        for (int i = 0; i < ALL.size(); i++) {
            if (matches(ALL.get(i), config)) {
                return ALL.get((i + 1) % ALL.size());
            }
        }
        return ALL.get(0);
    }

    public static boolean matches(Preset p, HouseConfig c) {
        return p.paymentIntervalMillis() == c.paymentIntervalMillis() && p.reminderLeadMillis() == c.reminderLeadMillis()
                && p.graceMillis() == c.graceMillis() && p.buyoutMultiplier() == c.buyoutMultiplier()
                && p.baseMemberLimit() == c.baseMemberLimit() && p.memberSlotPrice() == c.memberSlotPrice()
                && p.maxPurchasedMemberSlots() == c.maxPurchasedMemberSlots();
    }
}
