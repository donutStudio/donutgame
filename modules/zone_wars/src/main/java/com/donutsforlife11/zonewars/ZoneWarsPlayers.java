package com.donutsforlife11.zonewars;

import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.map.*;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.item.GameKits;

final class ZoneWarsPlayers {
    private final ZoneWars game;
    private final Map<GameTeam, GameLocation> spawns = new LinkedHashMap<>();
    private final List<GameRegion> spawnRegions = new ArrayList<>();
    private final Map<GameLocation, org.bukkit.block.data.BlockData> cages = new HashMap<>();
    private final Map<UUID, Long> protection = new HashMap<>();
    private final Set<UUID> respawning = new HashSet<>();
    private final YamlConfiguration kit;
    private long protectionId;

    ZoneWarsPlayers(ZoneWars game) {
        this.game = game;
        // RODO: Review text and string identifiers below before release.
        kit = game.data().config("kit.yml");
    }

    void loadPlayers() {
        var players = new ArrayList<>(game.playerManager().getPlayers());
        Collections.shuffle(players);
        if (game.teamManager().getTeams().isEmpty()) for (int i = 0; i < game.teamCount(); i++) game.teamManager().newColoredTeam();
        var teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) if (players.get(i).getTeam() == null) teams.get(i % teams.size()).addPlayer(players.get(i));
        for (int i = 0; i < teams.size(); i++) {
            GameLocation spawn = ZoneWars.center(game.spawns().get(i % game.spawns().size()));
            spawns.put(teams.get(i), spawn);
            GameRegion region = new GameRegion(new GameLocation(spawn.x()-2, spawn.y()-1, spawn.z()-2),
                new GameLocation(spawn.x()+2, spawn.y()+3, spawn.z()+2));
            // RODO: Review text and string identifiers below before release.
            if (i < game.world().getRegions("spawn").size()) region = game.world().getRegions("spawn").get(i);
            spawnRegions.add(region);
        }
        players.forEach(this::setup);
        for (GameLocation spawn : new HashSet<>(spawns.values())) {
            var assigned = spawns.entrySet().stream().filter(e -> e.getValue().equals(spawn)).map(Map.Entry::getKey).toList();
            var color = assigned.size() == 1 ? com.donutsforlife11.donutgame.api.item.GameItemComponents.teamDyeColor(assigned.getFirst()) : org.bukkit.DyeColor.WHITE;
            var material = org.bukkit.Material.valueOf(color.name() + "_STAINED_GLASS");
            var center = game.borderRegion().center();
            int rx = Math.abs(center.x()-spawn.x()) > Math.abs(center.z()-spawn.z()) ? 1 : 3;
            int rz = rx == 1 ? 3 : 1;
            for (int x = -rx; x <= rx; x++) for (int z = -rz; z <= rz; z++) for (int y = 0; y <= 2; y++) {
                if (Math.abs(x) != rx && Math.abs(z) != rz && y != 2) continue;
                GameLocation at = new GameLocation(spawn.blockX()+x, spawn.blockY()+y, spawn.blockZ()+z);
                var block = game.world().blockAt(at);
                if (cages.containsKey(at)) { block.setType(org.bukkit.Material.WHITE_STAINED_GLASS); continue; }
                if (!block.getType().isAir()) continue;
                cages.put(at, block.getBlockData());
                block.setType(material);
            }
        }
    }

    void startPlayers() {
        // RODO: Restore only the air cells changed into countdown cages, preserving the map's original terrain.
        cages.forEach((at, block) -> game.world().setBlock(at, block));
        cages.clear();
        game.playerManager().getNonSpectators().forEach(this::protect);
    }

    void join(GamePlayer player) {
        if (player.getTeam() == null) {
            var team = spawns.keySet().stream().min(Comparator.comparingLong(t -> t.getPlayers().stream().filter(GamePlayer::isOnline).count())).orElseThrow();
            team.addPlayer(player);
        }
        setup(player);
        if (game.running()) protect(player);
    }

    private void setup(GamePlayer player) {
        GameLocation spawn = spawns.get(player.getTeam());
        if (spawn == null) { player.setSpectator(true, game.world().getSpawnLocation()); return; }
        player.cancelRespawn();
        player.setSpawnPoint(spawn);
        player.setSpectator(false, spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.heal();
        player.clearActivePotionEffects();
        player.setFoodLevel(20);
        player.clearItems();
        // RODO: Review text and string identifiers below before release.
        GameKits.give(game, player, kit.getConfigurationSection("kit"));
        game.prepareLayout(player);
        player.setSpectatableTeams(() -> game.teamManager().getTeams());
    }

    boolean inSpawn(GameLocation location) { return spawnRegions.stream().anyMatch(region -> region.contains(location)); }

    void giveRespawnKit(GamePlayer player) {
        respawning.remove(player.uuid());
        player.clearItems();
        // RODO: Review text and string identifiers below before release.
        GameKits.give(game, player, kit.getConfigurationSection("kit"));
        game.prepareLayout(player);
        player.heal();
        // TODO: Review text and string identifiers below before release.
        game.uiManager().title(player, net.kyori.adventure.text.Component.text("Respawned!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    boolean protectedSpawn(GamePlayer player) {
        return protection.containsKey(player.uuid()) && inSpawn(player.getLocation());
    }

    void removeProtection(GamePlayer player) { protection.remove(player.uuid()); }

    void protect(GamePlayer player) {
        long token = ++protectionId;
        protection.put(player.uuid(), token);
        // RODO: Review text and string identifiers below before release.
        game.timeManager().newTimer(Math.max(0, game.config().getInt("spawn_protection", 4)) * 20)
            .onFinish(ignored -> protection.remove(player.uuid(), token)).start();
    }

    void beginRespawn(GamePlayer player) { respawning.add(player.uuid()); }
    boolean respawning(GamePlayer player) { return respawning.contains(player.uuid()); }
    void cancelRespawn(GamePlayer player) { respawning.remove(player.uuid()); player.cancelRespawn(); }
    void cancelRespawns() { respawning.clear(); game.playerManager().getPlayers().forEach(GamePlayer::cancelRespawn); }
}
