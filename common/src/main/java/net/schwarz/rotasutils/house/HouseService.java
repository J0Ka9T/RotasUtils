package net.schwarz.rotasutils.house;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HouseService {
    public interface Wallet {
        long balance(UUID player);
        boolean debit(UUID player, long amount);
    }

    public record Action(boolean success, String message, HouseTenancy tenancy) {}

    private final HouseConfig config;
    private final Wallet wallet;

    public HouseService(HouseConfig config, Wallet wallet) {
        this.config = config;
        this.wallet = wallet;
    }

    public Action rent(HouseTenancy current, UUID player, String tierId, long now, long expectedRevision) {
        return rent(current, player, config.tier(tierId), now, expectedRevision);
    }

    public Action rent(HouseTenancy current, UUID player, HouseTier tier, long now, long expectedRevision) {
        if (current.status() != HouseStatus.AVAILABLE) return fail("house.not_available", current);
        if (current.revision() != expectedRevision) return fail("house.stale", current);
        if (tier == null) return fail("house.unknown_tier", current);
        if (!wallet.debit(player, tier.deposit())) return fail("house.insufficient_funds", current);
        HouseTenancy rented = new HouseTenancy(HouseStatus.ACTIVE, player, Set.of(),
                Math.addExact(now, config.paymentIntervalMillis()), 0, 0, 0, current.revision() + 1);
        return new Action(true, "house.rented", rented);
    }

    public Action payOverdue(HouseTenancy current, UUID player, long now, long expectedRevision) {
        if (current.status() != HouseStatus.OVERDUE || !player.equals(current.owner())) return fail("house.not_owner", current);
        if (current.revision() != expectedRevision) return fail("house.stale", current);
        if (now >= current.graceEndsAt()) return fail("house.grace_expired", current);
        if (!wallet.debit(player, current.overdueCharge())) return fail("house.insufficient_funds", current);
        return new Action(true, "house.paid", new HouseTenancy(HouseStatus.ACTIVE, player, current.members(),
                Math.addExact(now, config.paymentIntervalMillis()), 0, 0, current.purchasedMemberSlots(), current.revision() + 1));
    }

    public Action buyout(HouseTenancy current, UUID player, String tierId, long expectedRevision) {
        return buyout(current, player, config.tier(tierId), expectedRevision);
    }

    public Action buyout(HouseTenancy current, UUID player, HouseTier tier, long expectedRevision) {
        if (current.owner() == null || !player.equals(current.owner())) return fail("house.not_owner", current);
        if (current.status() == HouseStatus.BOUGHT_OUT) return fail("house.already_bought_out", current);
        if (current.revision() != expectedRevision) return fail("house.stale", current);
        if (tier == null) return fail("house.unknown_tier", current);
        long cost;
        try {
            long product = Math.multiplyExact(tier.deposit(), config.buyoutMultiplier());
            cost = Math.addExact(product, current.overdueCharge());
        } catch (ArithmeticException overflow) {
            return fail("house.buyout_overflow", current);
        }
        if (!wallet.debit(player, cost)) return fail("house.insufficient_funds", current);
        return new Action(true, "house.bought_out", new HouseTenancy(HouseStatus.BOUGHT_OUT, player, current.members(),
                0, 0, 0, current.purchasedMemberSlots(), current.revision() + 1));
    }

    public static HouseTenancy repossessIfDue(HouseTenancy tenancy, long now) {
        return tenancy.status() == HouseStatus.OVERDUE && now >= tenancy.graceEndsAt()
                ? new HouseTenancy(HouseStatus.AVAILABLE, null, Set.of(), 0, 0, 0, 0, tenancy.revision() + 1)
                : tenancy;
    }

    private static Action fail(String message, HouseTenancy tenancy) { return new Action(false, message, tenancy); }

    public static final class MemoryWallet implements Wallet {
        private final Map<UUID, Long> balances = new HashMap<>();
        public MemoryWallet(Map<UUID, Long> balances) { this.balances.putAll(balances); }
        public long balance(UUID player) { return balances.getOrDefault(player, 0L); }
        public boolean debit(UUID player, long amount) {
            long balance = balance(player);
            if (amount < 0 || balance < amount) return false;
            balances.put(player, balance - amount); return true;
        }
    }
}
