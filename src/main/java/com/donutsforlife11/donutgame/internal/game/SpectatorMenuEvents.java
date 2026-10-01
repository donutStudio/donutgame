package com.donutsforlife11.donutgame.internal.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class SpectatorMenuEvents implements Listener {
    private static final int ROW_SIZE = 9;
    private static final int MAIN_MIN_ROWS = 1;
    private static final int TEAM_MIN_ROWS = 2;
    private static final int MAX_ROWS = 6;

    private final ModuleService moduleService;

    public SpectatorMenuEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler
    public void openSpectatorMenu(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isUseAction(event.getAction())) {
            return;
        }
        GamePlayer viewer = spectator(event.getPlayer());
        if (viewer == null) {
            return;
        }
        event.setCancelled(true);
        openMainMenu(viewer, 0);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void handleSpectatorMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        GamePlayer viewer = spectator(player);
        if (viewer == null) {
            player.closeInventory();
            return;
        }
        MenuAction action = holder.action(event.getSlot());
        if (action == null) {
            return;
        }
        switch (action) {
            case NextPage next -> open(holder.team() == null ? mainMenu(viewer, next.page()) : teamMenu(viewer, holder.team(), next.page()));
            case PreviousPage previous -> open(holder.team() == null ? mainMenu(viewer, previous.page()) : teamMenu(viewer, holder.team(), previous.page()));
            case BackToMain ignored -> openMainMenu(viewer, 0);
            case OpenTeam teamAction -> openTeamMenu(viewer, teamAction.team(), 0);
            case TeleportToPlayer teleport -> teleportTo(viewer, teleport.player());
        }
    }

    private void openMainMenu(GamePlayer viewer, int page) {
        open(mainMenu(viewer, page));
    }

    private void openTeamMenu(GamePlayer viewer, GameTeam team, int page) {
        open(teamMenu(viewer, team, page));
    }

    private void open(MenuHolder holder) {
        Player player = holder.viewer().bukkitPlayer();
        if (player != null) {
            player.openInventory(holder.inventory());
        }
    }

    private MenuHolder mainMenu(GamePlayer viewer, int page) {
        List<MenuAction> entries = new ArrayList<>();
        List<GameTeam> teams = spectatableTeams(viewer);
        for (GameTeam team : teams) {
            entries.add(new OpenTeam(team));
        }
        if (!teams.isEmpty() && entries.size() % ROW_SIZE != 0) {
            int skip = ROW_SIZE - (entries.size() % ROW_SIZE);
            for (int i = 0; i < skip; i++) {
                entries.add(null);
            }
        }
        for (GamePlayer player : spectatablePlayers(viewer)) {
            entries.add(new TeleportToPlayer(player));
        }
        return buildMenu(viewer, null, Component.text("Spectator Menu", NamedTextColor.AQUA), entries, MAIN_MIN_ROWS, false, page);
    }

    private MenuHolder teamMenu(GamePlayer viewer, GameTeam team, int page) {
        List<MenuAction> entries = new ArrayList<>();
        for (GamePlayer player : nonSpectatorPlayers(team.getPlayers())) {
            entries.add(new TeleportToPlayer(player));
        }
        return buildMenu(viewer, team, team.displayName(), entries, TEAM_MIN_ROWS, true, page);
    }

    private MenuHolder buildMenu(
        GamePlayer viewer,
        GameTeam team,
        Component title,
        List<MenuAction> entries,
        int minimumRows,
        boolean reservedBottomRow,
        int requestedPage
    ) {
        int contentRows = Math.max(1, (entries.size() + ROW_SIZE - 1) / ROW_SIZE);
        boolean paged = reservedBottomRow ? contentRows > MAX_ROWS - 1 : contentRows > MAX_ROWS;
        int rows;
        int contentSlots;
        if (paged) {
            rows = MAX_ROWS;
            contentSlots = (MAX_ROWS - 1) * ROW_SIZE;
        } else {
            rows = Math.max(minimumRows, contentRows + (reservedBottomRow ? 1 : 0));
            contentSlots = reservedBottomRow ? (rows - 1) * ROW_SIZE : rows * ROW_SIZE;
        }
        int pages = Math.max(1, (entries.size() + contentSlots - 1) / contentSlots);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(viewer, team, rows, title);
        int start = page * contentSlots;
        int end = Math.min(entries.size(), start + contentSlots);
        for (int index = start; index < end; index++) {
            MenuAction action = entries.get(index);
            if (action == null) {
                continue;
            }
            int slot = index - start;
            holder.setAction(slot, action, action.item(viewer));
        }
        int controlsRowStart = (rows - 1) * ROW_SIZE;
        if (reservedBottomRow) {
            holder.setAction(controlsRowStart + 1, new BackToMain(), namedItem(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW)));
        }
        if (paged) {
            if (page > 0) {
                holder.setAction(controlsRowStart, new PreviousPage(page - 1), namedItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW)));
            }
            if (page < pages - 1) {
                holder.setAction(controlsRowStart + ROW_SIZE - 1, new NextPage(page + 1), namedItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW)));
            }
        }
        return holder;
    }

    private void teleportTo(GamePlayer viewer, GamePlayer target) {
        if (target == null || target.isSpectator() || target.location() == null) {
            return;
        }
        viewer.teleport(target.location());
        Player player = viewer.bukkitPlayer();
        if (player != null) {
            player.closeInventory();
        }
    }

    private List<GameTeam> spectatableTeams(GamePlayer viewer) {
        return viewer.spectatableTeams().stream()
            .filter(team -> team != null && !team.allSpectators())
            .toList();
    }

    private List<GamePlayer> spectatablePlayers(GamePlayer viewer) {
        return nonSpectatorPlayers(viewer.spectatablePlayers());
    }

    private List<GamePlayer> nonSpectatorPlayers(Collection<GamePlayer> players) {
        return players.stream()
            .filter(player -> player != null && player.bukkitPlayer() != null && !player.isSpectator())
            .toList();
    }

    private GamePlayer spectator(Player player) {
        GameModule game = moduleService.getGameOfPlayer(player);
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(player);
        return gamePlayer != null && gamePlayer.isSpectator() ? gamePlayer : null;
    }

    private boolean isUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private static ItemStack playerHead(GamePlayer player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player.bukkitPlayer());
            meta.customName(
                Component.text(player.bukkitPlayer().getName()).decoration(TextDecoration.ITALIC, false)
            );
        });
        return item;
    }

    private static ItemStack teamItem(GameTeam team, boolean selected) {
        GameItem item = team.item() == null ? GameItem.of(Material.WHITE_WOOL) : team.item();
        item.editMeta(meta -> meta.itemName(team.displayName()));
        if (selected) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item.copyBukkitItem();
    }

    private static ItemStack namedItem(Material material, Component name) {
        GameItem item = GameItem.of(material);
        item.editMeta(meta -> meta.itemName(name));
        return item.copyBukkitItem();
    }

    public static final class MenuHolder implements InventoryHolder {
        private final GamePlayer viewer;
        private final GameTeam team;
        private final Inventory inventory;
        private final MenuAction[] actions;

        private MenuHolder(GamePlayer viewer, GameTeam team, int rows, Component title) {
            this.viewer = viewer;
            this.team = team;
            this.actions = new MenuAction[rows * ROW_SIZE];
            this.inventory = Bukkit.createInventory(this, rows * ROW_SIZE, title == null ? Component.text("Spectator Menu") : title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        GamePlayer viewer() {
            return viewer;
        }

        GameTeam team() {
            return team;
        }

        Inventory inventory() {
            return inventory;
        }

        MenuAction action(int slot) {
            return slot < 0 || slot >= actions.length ? null : actions[slot];
        }

        void setAction(int slot, MenuAction action, ItemStack item) {
            if (slot < 0 || slot >= actions.length) {
                return;
            }
            actions[slot] = action;
            inventory.setItem(slot, item);
        }
    }

    private sealed interface MenuAction permits BackToMain, NextPage, OpenTeam, PreviousPage, TeleportToPlayer {
        default ItemStack item(GamePlayer viewer) {
            return null;
        }
    }

    private record OpenTeam(GameTeam team) implements MenuAction {
        @Override
        public ItemStack item(GamePlayer viewer) {
            return teamItem(team, team == viewer.getTeam());
        }
    }

    private record TeleportToPlayer(GamePlayer player) implements MenuAction {
        @Override
        public ItemStack item(GamePlayer viewer) {
            return playerHead(player);
        }
    }

    private record NextPage(int page) implements MenuAction {
    }

    private record PreviousPage(int page) implements MenuAction {
    }

    private record BackToMain() implements MenuAction {
    }
}
