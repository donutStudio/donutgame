package com.donutsforlife11.donutgame.api.player;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.object.ItemSpec;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;

public class GamePlayer implements GameEntityBase {
    private final PlayerManager playerManager;
    private final UUID uuid;
    private PlayerState state;
    private String lastWorldName;
    private Location lastLocation;

    GamePlayer(PlayerManager playerManager, UUID uuid) {
        this.playerManager = playerManager;
        this.uuid = uuid;
        this.state = PlayerState.OFFLINE;
    }

    public UUID uuid() {
        return uuid;
    }

    public Player bukkitPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public Entity bukkitEntity() {
        return bukkitPlayer();
    }

    public GameModule module() {
        return playerManager.module();
    }

    public GameWorld world() {
        return module().world();
    }

    public PlayerState state() {
        return state;
    }

    public boolean isOnline() {
        return state == PlayerState.ONLINE && bukkitPlayer() != null;
    }

    public String lastWorldName() {
        return lastWorldName;
    }

    public Location lastLocation() {
        return lastLocation == null ? null : lastLocation.clone();
    }

    public void title(Component title) {
        ui().title(this, title);
    }

    public void subtitle(Component subtitle) {
        ui().subtitle(this, subtitle);
    }

    public void actionbar(Component actionbar) {
        ui().actionbar(this, actionbar);
    }

    public void chat(Component message) {
        ui().chat(this, message);
    }

    public void gameMessage(Component message) {
        ui().gameMessage(this, message);
    }

    public void give(ItemSpec item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        Player player = bukkitPlayer();
        if (player == null) {
            throw new IllegalStateException("Player " + uuid + " is not online.");
        }
        player.getInventory().addItem(item.createItemStack());
    }

    public void playSound(Sound sound) {
        playSound(sound, SoundCategory.MASTER, null, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, GameLocation location) {
        playSound(sound, SoundCategory.MASTER, location, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track) {
        playSound(sound, track, null, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location) {
        playSound(sound, track, location, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, float volume) {
        playSound(sound, SoundCategory.MASTER, null, volume, 1f, 0f);
    }

    public void playSound(Sound sound, GameLocation location, float volume) {
        playSound(sound, SoundCategory.MASTER, location, volume, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, float volume) {
        playSound(sound, track, null, volume, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location, float volume) {
        playSound(sound, track, location, volume, 1f, 0f);
    }

    public void playSound(Sound sound, float volume, float pitch) {
        playSound(sound, SoundCategory.MASTER, null, volume, pitch, 0f);
    }

    public void playSound(Sound sound, GameLocation location, float volume, float pitch) {
        playSound(sound, SoundCategory.MASTER, location, volume, pitch, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, float volume, float pitch) {
        playSound(sound, track, null, volume, pitch, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location, float volume, float pitch) {
        playSound(sound, track, location, volume, pitch, 0f);
    }

    public void playSound(Sound sound, float volume, float pitch, float minVolume) {
        playSound(sound, SoundCategory.MASTER, null, volume, pitch, minVolume);
    }

    public void playSound(Sound sound, GameLocation location, float volume, float pitch, float minVolume) {
        playSound(sound, SoundCategory.MASTER, location, volume, pitch, minVolume);
    }

    public void playSound(Sound sound, SoundCategory track, float volume, float pitch, float minVolume) {
        playSound(sound, track, null, volume, pitch, minVolume);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location, float volume, float pitch, float minVolume) {
        ui().playSound(this, sound, track, location, volume, pitch, minVolume);
    }

    void remember(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }
        state = PlayerState.ONLINE;
        World world = player.getWorld();
        lastWorldName = world == null ? null : world.getName();
        lastLocation = player.getLocation().clone();
    }

    void markOffline() {
        state = PlayerState.OFFLINE;
    }

    private UIManager ui() {
        return playerManager.uiManager();
    }
}
