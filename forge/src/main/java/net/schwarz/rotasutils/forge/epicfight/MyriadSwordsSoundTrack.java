package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.SwordConvergenceTimeline;

import java.util.ArrayList;
import java.util.List;

final class MyriadSwordsSoundTrack {
    private final CombatSkillEffects.Cue cue;
    private final List<SoundInstance> owned = new ArrayList<>();
    private int stage;
    private int nextWhoosh;
    private int nextImpact;
    private boolean silenced;

    MyriadSwordsSoundTrack(CombatSkillEffects.Cue cue) {
        this.cue = cue;
    }

    void tick(long age) {
        if (age >= SwordConvergenceTimeline.FREEZE && !silenced) {
            stopOwned();
            silenced = true;
        }
        while (stage < 4 && age >= stage * 20L) {
            int beat = stage++;
            if (silenced || age >= 80) {
                continue;
            }
            play(beat == 0 ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.AMETHYST_BLOCK_CHIME,
                    .55f + beat * .12f, .6f + beat * .18f, cue.source());
        }
        if (!silenced) {
            return;
        }
        int[] impacts = SwordConvergenceTimeline.IMPACTS;
        while (nextWhoosh < impacts.length && age >= impacts[nextWhoosh] - 4) {
            int volley = nextWhoosh++;
            play(SoundEvents.TRIDENT_RIPTIDE_2, 1.15f, .6f + volley * .07f, cue.target());
        }
        while (nextImpact < impacts.length && age >= impacts[nextImpact]) {
            int volley = nextImpact++;
            boolean finale = volley == impacts.length - 1;
            play(finale ? SoundEvents.TRIDENT_RIPTIDE_3 : SoundEvents.TRIDENT_HIT, .9f + volley * .12f,
                    .7f + volley * .05f, cue.target());
            if (finale) {
                play(SoundEvents.GENERIC_EXPLODE, 1.25f, .7f, cue.target());
                play(SoundEvents.TRIDENT_THUNDER, .9f, .65f, cue.target());
            }
        }
    }

    private void play(SoundEvent event, float volume, float pitch, Vec3 at) {
        SoundInstance sound = new SimpleSoundInstance(event.getLocation(), SoundSource.PLAYERS, volume, pitch,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        owned.add(sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private void stopOwned() {
        for (SoundInstance sound : owned) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    void stop() {
        stopOwned();
        owned.clear();
    }
}
