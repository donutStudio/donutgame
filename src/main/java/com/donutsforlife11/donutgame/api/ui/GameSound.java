package com.donutsforlife11.donutgame.api.ui;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

import com.donutsforlife11.donutgame.api.map.GameLocation;

public record GameSound(Sound sound, SoundCategory category, GameLocation location, float volume, float pitch) {
    public GameSound {
        category = category == null ? SoundCategory.MASTER : category;
        volume = Math.max(0f, volume);
    }

    public static GameSound of(Sound sound) {
        return new GameSound(sound, SoundCategory.MASTER, null, 1f, 1f);
    }

    public GameSound category(SoundCategory category) {
        return new GameSound(sound, category, location, volume, pitch);
    }

    public GameSound location(GameLocation location) {
        return new GameSound(sound, category, location, volume, pitch);
    }

    public GameSound volume(float volume) {
        return new GameSound(sound, category, location, volume, pitch);
    }

    public GameSound pitch(float pitch) {
        return new GameSound(sound, category, location, volume, pitch);
    }
}
