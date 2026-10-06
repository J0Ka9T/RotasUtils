package net.schwarz.rotasutils.ability;

import net.minecraft.world.phys.Vec3;

public record Target(int entityId, Vec3 position, Vec3 eye) {
    public boolean isEntity() {
        return entityId >= 0;
    }
}
