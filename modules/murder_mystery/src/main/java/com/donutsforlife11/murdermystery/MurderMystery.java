package com.donutsforlife11.murdermystery;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team.OptionStatus;
import com.donutsforlife11.donutgame.api.event.*;
import com.donutsforlife11.donutgame.api.item.*;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.*;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.murdermystery.GameKits;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class MurderMystery extends GameModule {
    enum Phase { WAITING, PLAYING, MEETING, RESULT, ENDING }
    private Phase phase = Phase.WAITING;
    private final Set<UUID> participants = new LinkedHashSet<>();
    // TODO: Roles and participation are fixed at load; late arrivals never enter this alive roster.
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> monsters = new LinkedHashSet<>();
    private final Map<UUID, Integer> killClocks = new HashMap<>();
    private final Map<UUID, GameBossbar> killBars = new HashMap<>();
    private final Map<UUID, String> perks = new HashMap<>();
    private final Map<UUID, GameItem> replacedItems = new HashMap<>();
    private GameTeam monsterTeam, innocentTeam;
    private GameBossbar meetingBar;
    private MurderVoting voting;
    private org.bukkit.configuration.file.YamlConfiguration kits;
    private GameTimer clock;
    private int sharedKillClock, meetingTime, armGeneration;
    private boolean armed;

    @Override public void onLoad() {
        // TODO: Review text and string identifiers below before release.
        require(world().getPoint("spawn") != null, "spawn point(s)");
        // TODO: Review text and string identifiers below before release.
        require(world().getPoint("meeting_location") != null, "meeting_location point");
        // TODO: Review text and string identifiers below before release.
        require(world().getRegion("meeting_bound") != null, "meeting_bound region");
        // TODO: Review text and string identifiers below before release.
        world().setSpawnLocation(world().getPoint("spawn"));
        world().setHungerEnabled(false);
        world().gamerules().pvp(false);
        world().gamerules().showDeathMessages(false);
        world().gamerules().immediateRespawn(true);
        world().gamerules().spawnMobs(false);
        // TODO: Review text and string identifiers below before release.
        kits = data().config("kits.yml");
        List<GamePlayer> players = new ArrayList<>(playerManager().getNonSpectators());
        if (players.size() < 2) logWarning("Solo practice: opposing-role win checks and kill timer are disabled.");
        // TODO: Review text and string identifiers below before release.

        Collections.shuffle(players);
        // TODO: Review text and string identifiers below before release.
        monsterTeam = teamManager().newTeam().setTeamGlow(config().getBoolean("show_other_monsters", true))
            // TODO: Review text and string identifiers below before release.
            .setFriendlyFire(config().getBoolean("monster_friendly_fire", true))
            .setNametagVisibility(OptionStatus.NEVER).setSeeFriendlyInvisibles(false);
        innocentTeam = teamManager().newTeam().setTeamGlow(false).setFriendlyFire(false)
            .setNametagVisibility(OptionStatus.NEVER).setSeeFriendlyInvisibles(false);
        // TODO: Review text and string identifiers below before release.
        int count = Math.clamp(config().getInt("monster_count", 1), 0, Math.max(0,players.size() == 1 ? 1 : players.size()-1));
        // TODO: Review text and string identifiers below before release.
        List<String> enabled = config().getStringList("perks");
        // TODO: Review text and string identifiers below before release.
        if (enabled.isEmpty()) enabled = List.of("none");
        String sharedPerk = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        for (int i = 0; i < players.size(); i++) {
            GamePlayer player = players.get(i);
            participants.add(player.uuid());
            alive.add(player.uuid());
            boolean monster = i < count;
            (monster ? monsterTeam : innocentTeam).addPlayer(player);
            if (monster) {
                monsters.add(player.uuid());
                // TODO: Review text and string identifiers below before release.
                perks.put(player.uuid(), config().getBoolean("shared_monster_perk", true) ? sharedPerk : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())));
                killBars.put(player.uuid(), uiManager().newBossbar().setColor(BossBar.Color.RED));
            }
            // TODO: Review text and string identifiers below before release.
            GameLocation spawn = world().getPoints("spawn").get(i % world().getPoints("spawn").size());
            player.setSpectator(false, spawn);
            player.setGameMode(GameMode.ADVENTURE);
            player.setSpawnPoint(spawn);
            player.clearItems();
            player.clearActivePotionEffects();
            player.heal();
            player.clearExperience();
            // TODO: Review text and string identifiers below before release.
            GameKits.give(this, player, kits.getConfigurationSection("player"));
        }
        sharedKillClock = killTime();
        monsters.forEach(id -> killClocks.put(id, killTime()));
        meetingBar = uiManager().newBossbar().setColor(BossBar.Color.GREEN);
        voting = new MurderVoting(this);
        // TODO: Review text and string identifiers below before release.
        data().interactEvent("meeting", new GameItemInteraction(null, use -> {
            if (use.ticks() == 100 && phase == Phase.PLAYING && alive.contains(use.player().uuid())) {
                GameItem item = use.player().getItem(use.hand());
                if (item == null) return;
                item.setAmount(item.getAmount()-1);
                use.player().setItem(use.hand(), item.isEmpty() ? null : item);
                callMeeting();
            }
        }, null));
        // TODO: Review text and string identifiers below before release.
        data().interactEvent("vote", new GameItemInteraction(use -> voting.open(use.player(), 0), null, null));
        uiManager().newSidebar().setRefreshInterval(20)
            // TODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.fraction("Alive Players", alive::size, participants::size))
            // TODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.time("Meeting", () -> (phase == Phase.MEETING ? meetingTime : 0) * 20))
            // TODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.custom(player -> Component.text(showKillClock(player) ? "Kill | " + time(killClock(player)) : "", NamedTextColor.RED)))
            .show();
    }

    @Override public void onStart() {
        phase = Phase.PLAYING;
        world().gamerules().pvp(true);
        for (GamePlayer player : living()) {
            boolean monster = isMonster(player);
            // TODO: Review text and string identifiers below before release.
            uiManager().title(player, Component.text(monster ? "MONSTER" : "Innocent", monster ? NamedTextColor.RED : NamedTextColor.WHITE));
            // TODO: Review text and string identifiers below before release.
            uiManager().subtitle(player, Component.text(monster ? "Murder other players!" : "Survive the Monster!"));
            if (monster) uiManager().playSound(player, GameSound.of(Sound.ENTITY_ENDER_DRAGON_GROWL));
            player.addEffect(PotionEffectType.INVISIBILITY, -1, 0, true);
        }
        armMonsters();
        // TODO: Review text and string identifiers below before release.
        timeManager().newTimer(Math.max(0, config().getInt("meeting_kit_delay", 10)) * 20).onFinish(ignored -> {
            if (phase == Phase.ENDING) return;
            for (GamePlayer player : living()) {
                // TODO: Review text and string identifiers below before release.
                GameKits.give(this, player, kits.getConfigurationSection("delayed_player"));
                bindMeetingItems(player);
            }
        }).start();
        clock = timeManager().newTimer().onTick(20, timer -> { if (timer.elapsedTicks() > 0) tick(); }).start();
        checkWin();
    }

    private void armMonsters() {
        // TODO: Invalidate earlier delayed kit callbacks whenever a meeting interrupts play.
        armed = false;
        int generation = ++armGeneration;
        // TODO: Review text and string identifiers below before release.
        timeManager().newTimer(Math.max(0, config().getInt("monster_kit_delay", 5)) * 20).onFinish(ignored -> {
            if (generation != armGeneration || phase != Phase.PLAYING) return;
            armed = true;
            // TODO: Review text and string identifiers below before release.
            for (GamePlayer player : living()) if (isMonster(player)) GameKits.give(this, player, kits.getConfigurationSection("monster"));
        }).start();
    }

    private void bindMeetingItems(GamePlayer player) {
        for (int slot = 0; slot <= 40; slot++) {
            GameItem item = player.getInventoryItem(slot);
            // TODO: Review text and string identifiers below before release.
            if (!marked(item, "meeting")) continue;
            // TODO: Review text and string identifiers below before release.
            item.setData(GameItemComponents.INTERACT_EVENT, "meeting");
            player.setInventoryItem(slot, item);
        }
    }

    private void tick() {
        // TODO: Kill clocks advance only during armed play, so meetings and weapon delays pause them naturally.
        if (phase == Phase.MEETING) {
            if (--meetingTime <= 0) voting.resolve();
        } else if (phase == Phase.PLAYING && armed && killTime() > 0) {
            // TODO: Review text and string identifiers below before release.
            if (config().getBoolean("shared_kill_time", true)) {
                if (--sharedKillClock <= 0) {
                    // TODO: Review text and string identifiers below before release.
                    message("The monsters ran out of kill time!");
                    // TODO: Review text and string identifiers below before release.
                    for (GamePlayer player : List.copyOf(living())) if (isMonster(player)) eliminate(player, "ran out of kill time", false);
                }
            } else for (GamePlayer player : List.copyOf(living())) if (isMonster(player)) {
                int left = killClocks.merge(player.uuid(), -1, Integer::sum);
                // TODO: Review text and string identifiers below before release.
                if (left <= 0) eliminate(player, "ran out of kill time", true);
            }
        }
        refreshBars();
    }

    private void refreshBars() {
        // TODO: Review text and string identifiers below before release.
        if (phase == Phase.MEETING) meetingBar.setTitle(Component.text("Meeting | " + time(meetingTime)))
            // TODO: Review text and string identifiers below before release.
            .setProgress(meetingTime / (float) Math.max(1, config().getInt("meeting_time", 60))).show(living());
        else meetingBar.hide();
        for (UUID id : killBars.keySet()) {
            GamePlayer player = playerManager().getPlayer(id);
            // TODO: Review text and string identifiers below before release.
            boolean publicClock = config().getBoolean("show_kill_time_to_innocents", false);
            // TODO: Review text and string identifiers below before release.
            boolean duplicateSharedBar = publicClock && config().getBoolean("shared_kill_time", true)
                && !id.equals(monsters.stream().filter(alive::contains).findFirst().orElse(null));
            var viewers = player != null && alive.contains(id) && phase == Phase.PLAYING && armed && killTime() > 0
                && !duplicateSharedBar ? publicClock ? living() : List.of(player) : List.<GamePlayer>of();
            // TODO: Review text and string identifiers below before release.
            killBars.get(id).setTitle(Component.text("Kill | " + time(player == null ? 0 : killClock(player))))
                .setProgress((player == null ? 0 : killClock(player)) / (float) Math.max(1, killTime())).show(viewers);
        }
    }

    void callMeeting() {
        if (phase != Phase.PLAYING) return;
        phase = Phase.MEETING;
        armed = false;
        armGeneration++;
        // TODO: Review text and string identifiers below before release.
        meetingTime = Math.max(1, config().getInt("meeting_time", 60));
        world().gamerules().pvp(false);
        for (GamePlayer player : living()) {
            removeMonsterItems(player);
            // TODO: Review text and string identifiers below before release.
            player.teleport(world().getPoint("meeting_location"));
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.heal();
            // TODO: Review text and string identifiers below before release.
            uiManager().title(player, Component.text("Meeting", NamedTextColor.GREEN));
            replacedItems.put(player.uuid(), player.getHotbarItem(8));
            // TODO: Review text and string identifiers below before release.
            GameItem vote = data().item("paper[item_name={text:'Vote',color:'green'}]");
            // TODO: Review text and string identifiers below before release.
            vote.setData(GameItemComponents.INTERACT_EVENT, "vote");
            player.setHotbarItem(8, vote);
        }
        voting.begin();
        refreshBars();
    }

    void beginResult() { phase = Phase.RESULT; refreshBars(); }

    void endMeeting() {
        if (phase == Phase.ENDING) return;
        voting.close();
        phase = Phase.PLAYING;
        for (GamePlayer player : living()) {
            player.setHotbarItem(8, replacedItems.remove(player.uuid()));
            player.addEffect(PotionEffectType.INVISIBILITY, -1, 0, true);
            // TODO: Review text and string identifiers below before release.
            uiManager().subtitle(player, Component.text("Meeting over!"));
        }
        replacedItems.clear();
        world().gamerules().pvp(true);
        armMonsters();
    }

    private void removeMonsterItems(GamePlayer player) {
        // TODO: Review text and string identifiers below before release.
        for (int slot = 0; slot <= 40; slot++) if (marked(player.getInventoryItem(slot), "monster_weapon")) player.setInventoryItem(slot, null);
    }

    void eliminate(GamePlayer player, String reason, boolean announce) {
        if (!alive.remove(player.uuid())) return;
        if (announce) {
            // TODO: Review text and string identifiers below before release.
            message(player.name() + " " + reason + ".");
            // TODO: Review text and string identifiers below before release.
            if (config().getBoolean("reveal_role_on_death", true)) message(isMonster(player) ? "They were a monster." : "They were innocent.");
        }
        world().particle(Particle.POOF, player.getLocation(), 25);
        player.cancelRespawn();
        player.setSpectator(true, player.getLocation());
        voting.remove(player);
        checkWin();
    }

    private void checkWin() {
        if (participants.size() < 2) return;
        if (phase == Phase.WAITING || phase == Phase.ENDING) return;
        long remaining = alive.stream().filter(monsters::contains).count();
        if (remaining > 0 && alive.size() > remaining) return;
        boolean monstersWon = remaining > 0;
        phase = Phase.ENDING;
        armed = false;
        armGeneration++;
        if (clock != null) clock.cancel();
        voting.close();
        meetingBar.hide();
        killBars.values().forEach(GameBossbar::hide);
        world().gamerules().pvp(false);
        for (GamePlayer player : playerManager().getPlayers()) uiManager().title(player, Component.text(
            // TODO: Review text and string identifiers below before release.
            participants.contains(player.uuid()) ? isMonster(player) == monstersWon ? "VICTORY" : "DEFEAT" : "Game over!",
            isMonster(player) == monstersWon ? NamedTextColor.GOLD : NamedTextColor.DARK_RED));
        // TODO: Review text and string identifiers below before release.
        uiManager().subtitle(playerManager().getPlayers(), Component.text(monstersWon ? "Monsters win!" : "Innocents win!"));
        // TODO: Review text and string identifiers below before release.
        message("Monsters: " + participants.stream().filter(monsters::contains).map(playerManager()::getPlayer).filter(Objects::nonNull).map(GamePlayer::name).reduce((a,b) -> a + ", " + b).orElse("None"));
        timeManager().newTimer(120).onFinish(ignored -> unload()).start();
    }

    @GameEventHandler public void damage(GameEvent<EntityDamageEvent> event) {
        if (phase != Phase.PLAYING) event.setCancelled(true);
    }

    @GameEventHandler public void attack(GameEvent<EntityDamageByEntityEvent> event) {
        if (!(event.getDamager() instanceof GamePlayer attacker) || !(event.getEntity() instanceof GamePlayer victim)) { event.setCancelled(true); return; }
        if (phase != Phase.PLAYING || !armed || !alive.contains(attacker.uuid()) || !alive.contains(victim.uuid())
            // TODO: Review text and string identifiers below before release.
            || !isMonster(attacker) || !marked(attacker.getItem(EquipmentSlot.HAND), "monster_weapon")
            // TODO: Review text and string identifiers below before release.
            || isMonster(victim) && !config().getBoolean("monster_friendly_fire", true)) event.setCancelled(true);
    }

    @GameEventHandler public void death(GameEvent<PlayerDeathEvent> event) {
        event.clearDrops();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        GamePlayer killer = event.getKiller();
        if (phase == Phase.PLAYING && killer != null && alive.contains(event.getPlayer().uuid()) && isMonster(killer)) {
            // TODO: Review text and string identifiers below before release.
            if (config().getBoolean("shared_kill_time", true)) sharedKillClock = killTime();
            else killClocks.put(killer.uuid(), killTime());
            // TODO: Review text and string identifiers below before release.
            if ("killers_aura".equals(perks.get(killer.uuid()))) {
                for (var effect : List.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.HASTE)) killer.addEffect(effect, 140, 2, true);
                killer.addEffect(PotionEffectType.GLOWING, 200, 0, true);
            }
        }
        // TODO: Review text and string identifiers below before release.
        eliminate(event.getPlayer(), "was murdered", config().getBoolean("announce_deaths", true));
    }

    @GameEventHandler public void move(GameEvent<PlayerMoveEvent> event) {
        if ((phase == Phase.MEETING || phase == Phase.RESULT) && alive.contains(event.getPlayer().uuid()) && event.hasChangedPosition()
            // TODO: Review text and string identifiers below before release.
            && !world().getRegion("meeting_bound").contains(event.getTo())) event.setTo(world().getPoint("meeting_location"));
    }

    @GameEventHandler public void lateJoin(GameEvent<GamePlayerLateJoinEvent> event) {
        event.setJoinAsSpectator(true);
        event.setSpectatorLocation(world().getSpawnLocation());
    }


    @GameEventHandler public void quit(GameEvent<PlayerQuitEvent> event) {
        // TODO: Review text and string identifiers below before release.
        eliminate(event.getPlayer(), "left the game", false);
    }
    @GameEventHandler public void leave(GameEvent<GamePlayerLeaveEvent> event) {
        // TODO: Review text and string identifiers below before release.
        eliminate(event.getPlayer(), "left the game", false);
    }

    @GameEventHandler public void breakBlock(GameEvent<BlockBreakEvent> event) { event.setCancelled(true); }
    @GameEventHandler public void placeBlock(GameEvent<BlockPlaceEvent> event) { event.setCancelled(true); }
    @GameEventHandler public void explosion(GameEvent<EntityExplodeEvent> event) { event.removeBlocksIf(block -> true); }
    @GameEventHandler public void blockExplosion(GameEvent<BlockExplodeEvent> event) { event.removeBlocksIf(block -> true); }

    List<GamePlayer> living() { return playerManager().getPlayers().stream().filter(p -> alive.contains(p.uuid()) && p.isOnline()).toList(); }
    boolean meeting() { return phase == Phase.MEETING; }
    boolean isMonster(GamePlayer player) { return monsters.contains(player.uuid()); }
    // TODO: Review text and string identifiers below before release.
    private int killTime() { return participants.size() < 2 ? 0 : Math.max(0, config().getInt("kill_time", 75)); }
    // TODO: Review text and string identifiers below before release.
    private int killClock(GamePlayer player) { return config().getBoolean("shared_kill_time", true) ? sharedKillClock : killClocks.getOrDefault(player.uuid(), 0); }
    // TODO: Review text and string identifiers below before release.
    private boolean showKillClock(GamePlayer player) { return phase == Phase.PLAYING && armed && killTime() > 0 && (isMonster(player) || config().getBoolean("show_kill_time_to_innocents", false)); }
    // TODO: Review text and string identifiers below before release.
    private String time(int seconds) { return String.format("%02d:%02d", Math.max(0,seconds)/60, Math.max(0,seconds)%60); }
    void message(String text) { uiManager().gameMessage(playerManager().getPlayers(), Component.text(text)); }
    // TODO: Review text and string identifiers below before release.
    private void require(boolean valid, String object) { if (!valid) throw new IllegalStateException("Murder Mystery map needs " + object + "."); }

    static boolean marked(GameItem item, String marker) {
        // TODO: These tags belong to this game and stay in Minecraft's custom_data component.
        // TODO: Review text and string identifiers below before release.
        return item != null && Pattern.compile("[\\\"']?murder_mystery:" + marker + "[\\\"']?\\s*:\\s*1b?").matcher(item.getItemMeta().getAsString()).find();
    }
}
