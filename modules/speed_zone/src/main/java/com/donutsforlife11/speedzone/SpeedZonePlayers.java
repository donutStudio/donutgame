package com.donutsforlife11.speedzone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.event.GamePlayerLateJoinEvent;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.item.GameItemComponents.PlayerVisibilityMode;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.ui.GameSound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class SpeedZonePlayers {
    private static final int SPEED_AMPLIFIER = 1; // Speed II
    private static final int PLAYER_VISIBILITY_SLOT = 8;

    private final SpeedZone game;
    private final SpeedZoneModifiers modifiers;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Set<UUID> lateSpectators = new LinkedHashSet<>();
    private final Set<UUID> gateReachedPlayers = new LinkedHashSet<>();
    private List<List<String>> checkpointModifiers = List.of();
    private ProgressGate progressGate;

    SpeedZonePlayers(SpeedZone game, SpeedZoneModifiers modifiers) {
        this.game = game;
        this.modifiers = modifiers;
    }

    void assignTeams() {
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getPlayers());
        Collections.shuffle(players);
        int teamCount = Math.max(1, (int) Math.ceil(players.size() / (double) game.teamSize));
        for (int i = 0; i < teamCount; i++) {
            game.teamManager().newColoredTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teams.size()).addPlayer(players.get(i));
        }
    }

    void loadCheckpointModifiers() {
        List<List<String>> parsed = new ArrayList<>();
        for (int i = 0; i < game.checkpoints().size(); i++) {
            parsed.add(new ArrayList<>());
        }

        Object raw = game.mapManager().map().metadata("checkpoint_modifiers");
        if (raw instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                Integer index = parseIndex(key);
                if (index != null && index >= 0 && index < parsed.size()) {
                    parsed.set(index, parseModifierList(section.get(key)));
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Integer index = parseIndex(String.valueOf(entry.getKey()));
                if (index != null && index >= 0 && index < parsed.size()) {
                    parsed.set(index, parseModifierList(entry.getValue()));
                }
            }
        } else if (raw instanceof List<?> list) {
            for (int i = 0; i < Math.min(list.size(), parsed.size()); i++) {
                parsed.set(i, parseModifierList(list.get(i)));
            }
        } else if (raw != null) {
            game.log("checkpoint_modifiers metadata must be a list or section; ignoring it.");
        }

        checkpointModifiers = parsed.stream().map(List::copyOf).toList();
        for (int i = 0; i < checkpointModifiers.size(); i++) {
            for (String key : checkpointModifiers.get(i)) {
                if (!modifiers.exists(key)) {
                    game.log("Unknown Speed Zone modifier '" + key + "' at checkpoint " + i + "; it will be ignored.");
                }
            }
        }
    }

    void setupAllPlayers() {
        game.playerManager().getPlayers().forEach(player -> setupPlayer(player, false));
    }

    private void setupPlayer(GamePlayer player, boolean lateJoin) {
        GameLocation start = game.checkpoints().get(0);
        data.put(player.uuid(), new PlayerData(0, 0, 0.0));
        player.setSpawnPoint(start);
        player.setSpectatablePlayers(game.teamSize <= 1 ? () -> game.playerManager().getPlayers() : List::of);
        player.setSpectatableTeams(game.teamSize <= 1 ? List::of : () -> game.teamManager().getTeams());

        if (lateJoin || lateSpectators.contains(player.uuid())) {
            player.setSpectator(true, start);
            return;
        }

        player.setSpectator(false, start);
        player.setGameMode(GameMode.SURVIVAL);
        resetPlayerState(player);
        applyCheckpointLoadout(player, 0, false);
    }

    private void resetPlayerState(GamePlayer player) {
        player.heal();
        player.setHunger(20);
        player.setSaturation(20);
        player.setArrowsInBody(0);
        player.clearEffects();
        player.clearExperience();
        player.clearItems();
        applySpeed(player);
    }

    void tickCheckpoints() {
        for (GamePlayer player : game.playerManager().getNonSpectators()) {
            GameLocation location = player.location();
            if (location == null) {
                continue;
            }
            PlayerData state = data.computeIfAbsent(player.uuid(), ignored -> new PlayerData(0, 0, 0.0));
            if (holdAtGateIfNeeded(player, state, location)) {
                continue;
            }
            int crossed = 0;
            boolean heldAtGate = false;
            while (crossed < game.checkpoints().size() && game.passedNextCheckpoint(state.segmentIndex(), location)) {
                int nextSegment = (state.segmentIndex() + 1) % game.checkpoints().size();
                int completedLaps = state.completedLaps() + (nextSegment == 0 ? 1 : 0);
                state = new PlayerData(completedLaps, nextSegment, state.progress());
                data.put(player.uuid(), state);
                applyCheckpointLoadout(player, nextSegment, true);
                player.setSpawnPoint(game.checkpoints().get(nextSegment));
                if (shouldStopAt(nextSegment)) {
                    progressGate = new ProgressGate(completedLaps, nextSegment, gateProgress(completedLaps, nextSegment));
                    gateReachedPlayers.clear();
                    gateReachedPlayers.add(player.uuid());
                    holdAtGate(player, progressGate);
                    heldAtGate = true;
                    break;
                }
                crossed++;
            }
            if (heldAtGate) {
                continue;
            }
            double progress = game.progress(state.completedLaps(), state.segmentIndex(), location);
            data.put(player.uuid(), new PlayerData(state.completedLaps(), state.segmentIndex(), progress));
        }
        releaseGateIfReady();
    }

    double leadingProgress() {
        double leading = 0.0;
        for (GamePlayer player : game.playerManager().getNonSpectators()) {
            PlayerData state = data.get(player.uuid());
            if (state != null) {
                leading = Math.max(leading, state.progress());
            }
        }
        return leading;
    }

    Optional<GameLocation> leadingLocation() {
        GamePlayer leadingPlayer = null;
        double leading = -1.0;
        for (GamePlayer player : game.playerManager().getNonSpectators()) {
            PlayerData state = data.get(player.uuid());
            GameLocation location = player.location();
            if (state != null && location != null && state.progress() > leading) {
                leading = state.progress();
                leadingPlayer = player;
            }
        }
        return leadingPlayer == null ? Optional.empty() : Optional.ofNullable(leadingPlayer.location());
    }

    void checkGameOver() {
        Collection<GameTeam> aliveTeams = game.teamManager().getNonSpectatorTeams();
        if (aliveTeams.size() > 1) {
            return;
        }
        Collection<GamePlayer> winners = aliveTeams.isEmpty()
            ? List.of()
            : aliveTeams.iterator().next().getPlayers();
        game.finish(winners);
    }

    int lap(GamePlayer player) {
        return data.getOrDefault(player.uuid(), new PlayerData(0, 0, 0.0)).completedLaps() + 1;
    }

    int checkpoint(GamePlayer player) {
        return data.getOrDefault(player.uuid(), new PlayerData(0, 0, 0.0)).segmentIndex() + 1;
    }

    private void applyCheckpointLoadout(GamePlayer player, int checkpointIndex, boolean announce) {
        List<String> keys = checkpointIndex < checkpointModifiers.size() ? checkpointModifiers.get(checkpointIndex) : List.of();
        modifiers.clear(player);
        applySpeed(player);
        modifiers.apply(player, keys);

        if (announce) {
            game.uiManager().subtitle(player, modifiers.checkpointSubtitle(keys));
            game.uiManager().playSound(player, GameSound.of(Sound.BLOCK_NOTE_BLOCK_PLING).volume(0.8f).pitch(1.7f));
        }
    }

    private void applySpeed(GamePlayer player) {
        player.addEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, SPEED_AMPLIFIER, false, false, false));
        applyPlayerVisibilityItem(player);
    }

    private void applyPlayerVisibilityItem(GamePlayer player) {
        GameItem item = GameItem.of(Material.ENDER_EYE);
        item.setData(GameItemComponents.PLAYER_VISIBILITY_TOGGLE, (byte) 1);
        item.setData(GameItemComponents.UNDROPPABLE, (byte) 1);
        item.setData(GameItemComponents.PLAYER_VISIBILITY_MODE, PlayerVisibilityMode.EVERYONE.key());
        player.setHotbarItem(PLAYER_VISIBILITY_SLOT, item);
    }

    private List<String> parseModifierList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item != null).map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if (value instanceof String string && !string.isBlank()) {
            return List.of(string.trim());
        }
        return List.of();
    }

    private Integer parseIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @GameEventHandler
    public void onDeath(GameEvent<PlayerDeathEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player == null) {
            return;
        }
        event.bukkitEvent().setKeepInventory(true);
        event.bukkitEvent().setKeepLevel(true);
        event.bukkitEvent().setDroppedExp(0);
        event.bukkitEvent().getDrops().clear();

        GameLocation location = player.location();
        if (game.border() != null && location != null && !game.border().containsLocation(location)) {
            player.setSpectator(true, player.location());
            game.uiManager().title(player, Component.text("Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
            game.uiManager().playSound(player, GameSound.of(Sound.ENTITY_WITHER_DEATH).volume(0.65f).pitch(1.7f));
            GameTeam team = player.getTeam();
            if (game.teamSize > 1 && team != null && team.allSpectators()) {
                game.uiManager().title(team.getPlayers(), Component.text("Team Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
            }
            releaseGateIfReady();
            checkGameOver();
            return;
        }

        PlayerData state = data.getOrDefault(player.uuid(), new PlayerData(0, 0, 0.0));
        GameLocation respawn = game.checkpoints().get(state.segmentIndex());
        player.respawn(respawn);
    }

    @GameEventHandler
    public void onRespawn(GameEvent<PlayerRespawnEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player == null || player.isSpectator()) {
            return;
        }
        PlayerData state = data.getOrDefault(player.uuid(), new PlayerData(0, 0, 0.0));
        resetPlayerState(player);
        applyCheckpointLoadout(player, state.segmentIndex(), false);
    }

    @GameEventHandler
    public void onJoin(GameEvent<PlayerJoinEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player == null || game.teamManager().playerHasTeam(player)) {
            return;
        }
        lateSpectators.add(player.uuid());
        setupPlayer(player, true);
    }

    @GameEventHandler
    public void onLateJoin(GameEvent<GamePlayerLateJoinEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player == null || game.teamManager().playerHasTeam(player)) {
            return;
        }
        lateSpectators.add(player.uuid());
        data.put(player.uuid(), new PlayerData(0, 0, 0.0));
        player.setSpawnPoint(game.checkpoints().get(0));
        event.bukkitEvent().setSpectatorLocation(game.checkpoints().get(0));
    }

    @GameEventHandler
    public void onQuit(GameEvent<PlayerQuitEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player != null) {
            player.setSpectator(true);
            releaseGateIfReady();
            checkGameOver();
        }
    }

    private boolean holdAtGateIfNeeded(GamePlayer player, PlayerData state, GameLocation location) {
        ProgressGate gate = progressGate;
        if (gate == null) {
            return false;
        }
        double currentProgress = game.progress(state.completedLaps(), state.segmentIndex(), location);
        if (currentProgress + 0.5 < gate.progress()) {
            return false;
        }
        gateReachedPlayers.add(player.uuid());
        holdAtGate(player, gate);
        releaseGateIfReady();
        return true;
    }

    private void holdAtGate(GamePlayer player, ProgressGate gate) {
        GameLocation checkpoint = game.checkpoints().get(gate.segmentIndex());
        player.teleport(checkpoint);
        data.put(player.uuid(), new PlayerData(gate.completedLaps(), gate.segmentIndex(), gate.progress()));
    }

    private void releaseGateIfReady() {
        ProgressGate gate = progressGate;
        if (gate == null) {
            return;
        }
        for (GamePlayer player : game.playerManager().getNonSpectators()) {
            PlayerData state = data.get(player.uuid());
            if (!gateReachedPlayers.contains(player.uuid()) && (state == null || state.progress() + 0.5 < gate.progress())) {
                return;
            }
        }
        progressGate = null;
        gateReachedPlayers.clear();
    }

    private boolean shouldStopAt(int nextSegment) {
        return nextSegment == 0 ? game.stopOnLaps : game.stopOnCheckpoints;
    }

    private double gateProgress(int completedLaps, int segmentIndex) {
        return game.progress(completedLaps, segmentIndex, game.checkpoints().get(segmentIndex));
    }

    record PlayerData(int completedLaps, int segmentIndex, double progress) {
    }

    private record ProgressGate(int completedLaps, int segmentIndex, double progress) {
    }
}
