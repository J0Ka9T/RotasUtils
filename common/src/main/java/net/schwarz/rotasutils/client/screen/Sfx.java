package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

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

    public static void blip() {
        play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f + (float) Math.random() * 0.3f);
    }

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
    }

    public static void wood() {
        play(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.9f);
    }

    public static void page() {
        play(SoundEvents.BOOK_PAGE_TURN, 1.0f);
    }

    public static void toggle(boolean on) {
        play(SoundEvents.LEVER_CLICK, on ? 1.1f : 0.75f);
    }

    public static void select() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f);
    }

    public static void add() {
        play(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.1f);
    }

    public static void remove() {
        play(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.9f);
    }

    public static void commit() {
        play(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f);
    }

    public static void save() {
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f);
    }

    public static void stamp() {
        play(SoundEvents.HONEYCOMB_WAX_ON, 1.0f);
    }

    public static void reward() {
        play(SoundEvents.PLAYER_LEVELUP, 1.4f);
    }

    public static void error() {
        play(SoundEvents.VILLAGER_NO, 1.2f);
    }

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
