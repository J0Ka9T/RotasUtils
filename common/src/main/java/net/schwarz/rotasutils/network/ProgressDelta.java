package net.schwarz.rotasutils.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import java.util.UUID;

public final class ProgressDelta {
    public static final int PROTOCOL = 1;
    private ProgressDelta() { }

    public static final class Sender {
        private final UUID epoch = UUID.randomUUID();
        private CompoundTag previous;
        private long revision;

        public CompoundTag next(CompoundTag snapshot, boolean force) {
            if (!force && previous != null && previous.equals(snapshot)) { return null; }
            boolean full = previous == null || force;
            CompoundTag set = new CompoundTag();
            ListTag removed = new ListTag();
            for (String key : snapshot.getAllKeys()) {
                if (full || !snapshot.get(key).equals(previous.get(key))) { set.put(key, snapshot.get(key).copy()); }
            }
            if (!full) {
                for (String key : previous.getAllKeys()) {
                    if (!snapshot.contains(key)) { removed.add(StringTag.valueOf(key)); }
                }
            }
            CompoundTag frame = new CompoundTag();
            frame.putInt("protocol", PROTOCOL); frame.putUUID("epoch", epoch);
            frame.putLong("base", full ? -1 : revision); frame.putLong("revision", ++revision);
            frame.putBoolean("full", full); frame.put("set", set); frame.put("removed", removed);
            previous = snapshot.copy();
            return frame;
        }

        public void reset() { previous = null; }
    }

    public static final class Receiver {
        private UUID epoch;
        private long revision;
        private CompoundTag snapshot;

        public CompoundTag apply(CompoundTag frame) {
            if (frame.getInt("protocol") != PROTOCOL || !frame.hasUUID("epoch")
                    || !frame.contains("revision", 4) || !frame.contains("base", 4)
                    || !frame.contains("full", 1) || !frame.contains("set", 10) || !frame.contains("removed", 9)) {
                throw new IllegalArgumentException("Malformed progress frame");
            }
            UUID incoming = frame.getUUID("epoch"); long next = frame.getLong("revision");
            if (next <= 0) { throw new IllegalArgumentException("Invalid progress revision"); }
            if (incoming.equals(epoch) && next <= revision) { return null; }
            boolean full = frame.getBoolean("full");
            if (!full && (snapshot == null || !incoming.equals(epoch) || frame.getLong("base") != revision)) {
                throw new IllegalArgumentException("Progress baseline mismatch; full resync required");
            }
            if (full && frame.getLong("base") != -1) { throw new IllegalArgumentException("Invalid full baseline"); }
            CompoundTag changed = frame.getCompound("set");
            ListTag removed = (ListTag) frame.get("removed");
            if (!removed.isEmpty() && removed.getElementType() != 8) {
                throw new IllegalArgumentException("Invalid removed progress keys");
            }
            if (changed.size() > 128 || removed.size() > 128) { throw new IllegalArgumentException("Progress key budget exceeded"); }
            CompoundTag merged = full ? new CompoundTag() : snapshot.copy();
            for (int i = 0; i < removed.size(); i++) { merged.remove(removed.getString(i)); }
            changed.getAllKeys().forEach(key -> merged.put(key, changed.get(key).copy()));
            if (!merged.hasUUID("player")) { throw new IllegalArgumentException("Missing player identity"); }
            snapshot = merged; epoch = incoming; revision = next;
            return snapshot.copy();
        }

        public void clear() { epoch = null; revision = 0; snapshot = null; }
    }
}
