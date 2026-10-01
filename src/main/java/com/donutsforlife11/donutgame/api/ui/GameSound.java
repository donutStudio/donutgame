package com.donutsforlife11.donutgame.api.ui;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

import com.donutsforlife11.donutgame.api.map.GameLocation;

public record GameSound(Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) {
    public GameSound {
        category = category == null ? SoundCategory.MASTER : category;
        volume = Math.max(0f, volume);
        minVolume = Math.max(0f, minVolume);
    }

    public static GameSound of(Sound sound) {
        return new GameSound(sound, SoundCategory.MASTER, null, 1f, 1f, 0f);
    }

    public GameSound category(SoundCategory category) {
        return new GameSound(sound, category, location, volume, pitch, minVolume);
    }

    public GameSound location(GameLocation location) {
        return new GameSound(sound, category, location, volume, pitch, minVolume);
    }

    public GameSound volume(float volume) {
        return new GameSound(sound, category, location, volume, pitch, minVolume);
    }

    public GameSound pitch(float pitch) {
        return new GameSound(sound, category, location, volume, pitch, minVolume);
    }

    public GameSound minVolume(float minVolume) {
        return new GameSound(sound, category, location, volume, pitch, minVolume);
    }
}
