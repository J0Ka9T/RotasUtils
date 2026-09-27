package net.schwarz.rotasutils.core;

import java.util.Map;

public interface KernelContext {
    double number(String name);

    String text(String name);

    boolean requirement(String type, Map<String, String> parameters);

    Transaction begin();

    interface Transaction extends AutoCloseable {
        String variable(String key);

        void variable(String key, String value);

        void unlock(String id);

        default void currency(String id, long amount) { throw new UnsupportedOperationException("Wallet not available"); }
        default void reputation(String id, long amount) { throw new UnsupportedOperationException("Reputation not available"); }
        default void statPoints(int amount) { throw new UnsupportedOperationException("Stat points not available"); }
        default void heal(String target, double amount) { throw new UnsupportedOperationException("Entity healing not available"); }
        default void damage(String target, double amount) { throw new UnsupportedOperationException("Entity damage not available"); }
        default void effect(String target, String id, int duration, int amplifier) { throw new UnsupportedOperationException("Entity effects not available"); }
        default void item(String id, int count) { throw new UnsupportedOperationException("Item grants not available"); }
        default void profileItem(String profile, int level, int count) { throw new UnsupportedOperationException("Item grants not available"); }
        default void loot(String table, int level, double multiplier) { throw new UnsupportedOperationException("Loot grants not available"); }

        /**
         * Deterministic occurrence for anything this transaction rolls. The reward engine seeds every
         * transaction with its receipt, so a retried grant reproduces exactly the same loot.
         */
        default void seed(String occurrence) { }

        boolean claimed(String receipt);

        void claim(String receipt);

        void commit();

        @Override
        void close();
    }
}
