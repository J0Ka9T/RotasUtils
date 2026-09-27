package net.schwarz.rotasutils.network;

import net.minecraft.nbt.CompoundTag;

public interface AdminResponseReceiver {
    void receive(long requestId, CompoundTag response);
    void failed(String message);
}
