package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Interface sound effects.
 *
 * <p>All of these are UI sounds: they play on the master/UI channel at the player's
 * position, so they never leak into the world or follow the camera.
 */
@Environment(EnvType.CLIENT)
public final class Sfx {
    private Sfx() {
    }

    private static void play(SoundEvent sound, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null && minecraft.getSoundManager() == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    /** A soft tick for text typing out in a conversation, varied a little so it sounds like a voice. */
    public static void blip() {
        play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f + (float) Math.random() * 0.3f);
    }

    /** Generic control press. */
    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
    }

    /** Wooden press used by the quest board buttons and tabs. */
    public static void wood() {
        play(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.9f);
    }

    /** Moving between screens or tabs. */
    public static void page() {
        play(SoundEvents.BOOK_PAGE_TURN, 1.0f);
    }

    /** Boolean flip; the pitch tells the player which way it went. */
    public static void toggle(boolean on) {
        play(SoundEvents.LEVER_CLICK, on ? 1.1f : 0.75f);
    }

    /** Row or option selected. */
    public static void select() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f);
    }

    /** Entry added to a list. */
    public static void add() {
        play(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.1f);
    }

    /** Entry removed from a list. */
    public static void remove() {
        play(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.9f);
    }

    /** Text edit committed. */
    public static void commit() {
        play(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f);
    }

    /** Configuration saved. */
    public static void save() {
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f);
    }

    /** Wax seal stamped: accepting a contract. */
    public static void stamp() {
        play(SoundEvents.HONEYCOMB_WAX_ON, 1.0f);
    }

    /** Reward claimed / quest turned in. */
    public static void reward() {
        play(SoundEvents.PLAYER_LEVELUP, 1.4f);
    }

    /** Rejected input or a blocked action. */
    public static void error() {
        play(SoundEvents.VILLAGER_NO, 1.2f);
    }

    /**
     * Plays a sound named by id, e.g. a board's configured opening sound.
     * Falls back to the page turn when the id is blank or unknown.
     */
    public static void custom(String soundId) {
        if (soundId == null || soundId.isBlank()) {
            page();
            return;
        }
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(soundId.trim());
        SoundEvent sound = id == null
                ? null
                : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound == null) {
            page();
            return;
        }
        play(sound, 1.0f);
    }
}
