package net.schwarz.rotasutils.sky;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.schwarz.rotasutils.Rotasutils;

/** Per-{@link ServerLevel} persistence of the eldritch sky snapshot. */
public final class EldritchSkySavedData extends SavedData {
    public static final String FILE_ID = Rotasutils.MOD_ID + "_eldritch_sky";

    private EldritchSkyTransition.Snapshot snapshot = EldritchSkyTransition.Snapshot.off();

    public static EldritchSkySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(EldritchSkySavedData::load, EldritchSkySavedData::new, FILE_ID);
    }

    public EldritchSkyTransition.Snapshot snapshot() {
        return snapshot;
    }

    public void setSnapshot(EldritchSkyTransition.Snapshot next) {
        this.snapshot = next == null ? EldritchSkyTransition.Snapshot.off() : next;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("sky", snapshot.save());
        return tag;
    }

    public static EldritchSkySavedData load(CompoundTag tag) {
        EldritchSkySavedData data = new EldritchSkySavedData();
        data.snapshot = EldritchSkyTransition.Snapshot.load(tag.getCompound("sky"));
        return data;
    }
}
