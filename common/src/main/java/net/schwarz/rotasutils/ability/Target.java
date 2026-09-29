package net.schwarz.rotasutils.ability;

import net.minecraft.world.phys.Vec3;

/**
 * What an ability was aimed at when it began: the entity it locked onto (or -1 for a point), where
 * that was, and where the caster's eyes were. The cutscene frames its shots from these, so the
 * sequence looks right whichever way the caster happened to be facing when they used the item.
 */
public record Target(int entityId, Vec3 position, Vec3 eye) {
    public boolean isEntity() {
        return entityId >= 0;
    }
}
