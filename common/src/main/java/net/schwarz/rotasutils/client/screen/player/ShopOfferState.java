package net.schwarz.rotasutils.client.screen.player;

import java.util.List;

public final class ShopOfferState {
    public enum Blocked { READY, LOCKED, OUT_OF_STOCK, LIMIT_REACHED, MISSING_COST }
    public record Cost(long each, long have) {}

    private ShopOfferState() {
    }

    public static Blocked blocked(boolean open, int stock, int limit, int bought,
                                  List<Cost> costs, int quantity) {
        int count = Math.max(1, quantity);
        if (!open) return Blocked.LOCKED;
        if (stock >= 0 && stock < count) return Blocked.OUT_OF_STOCK;
        if (limit > 0 && bought + count > limit) return Blocked.LIMIT_REACHED;
        for (Cost cost : costs) {
            if (cost.each() > 0 && cost.have() / cost.each() < count) return Blocked.MISSING_COST;
        }
        return Blocked.READY;
    }
}
