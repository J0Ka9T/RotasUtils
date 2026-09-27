package net.schwarz.rotasutils.entity;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

public final class CeroDamageBatch<T> {
    @FunctionalInterface
    public interface EntryConsumer<T> {
        void accept(T target, float damage);
    }

    private final Object2FloatOpenHashMap<T> totals = new Object2FloatOpenHashMap<>();

    public void add(T target, float damage) {
        if (target != null && damage > 0f) {
            totals.addTo(target, damage);
        }
    }

    public float damageFor(T target) {
        return totals.getFloat(target);
    }

    public int size() {
        return totals.size();
    }

    public void forEach(EntryConsumer<T> consumer) {
        for (Object2FloatMap.Entry<T> entry : totals.object2FloatEntrySet()) {
            consumer.accept(entry.getKey(), entry.getFloatValue());
        }
    }

    public void clear() {
        totals.clear();
    }
}
