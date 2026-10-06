package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class DharmakayaSoundTrack {
    private static final int[] BEATS = {0, 60, 120, 180, 240, 300, 360};

    private final CombatSkillEffects.Cue cue;
    private final List<SoundInstance> owned = new ArrayList<>();
    private int stage;

    DharmakayaSoundTrack(CombatSkillEffects.Cue cue) {
        this.cue = cue;
    }

    void tick(long age) {
        while (stage < BEATS.length && age >= BEATS[stage]) {
            int beat = stage++;
            play(beat % 2 == 0 ? SoundEvents.BEACON_AMBIENT : SoundEvents.PORTAL_AMBIENT,
                    beat % 2 == 0 ? .5f : .35f, beat % 2 == 0 ? .6f : 1.4f, cue.source());
        }
    }

    private void play(net.minecraft.sounds.SoundEvent event, float volume, float pitch, Vec3 at) {
        SoundInstance sound = new SimpleSoundInstance(event.getLocation(), SoundSource.AMBIENT, volume, pitch,
                RandomSource.create(), true, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        owned.add(sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    void stop() {
        for (SoundInstance sound : owned) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        owned.clear();
    }
}
