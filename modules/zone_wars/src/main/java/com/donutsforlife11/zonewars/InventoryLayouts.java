package com.donutsforlife11.zonewars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.zonewars.InventoryLayoutPlan.Placement;
import com.donutsforlife11.donutgame.api.item.GameItemComponent;
import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public final class InventoryLayouts {
    // RODO: Review text and string identifiers below before release.
    private static final GameItemComponent<Integer> INDEX = GameItemComponents.integer("layout_index");
    private final GameModule game;
    private final String key;
    private final List<GameItem> identities = new ArrayList<>();
    private final Map<UUID, Map<Integer, Integer>> starts = new HashMap<>();

    public InventoryLayouts(GameModule game, String key) {
        this.game = game;
        // RODO: Review text and string identifiers below before release.
        this.key = "inventory_layout." + key;
        // RODO: Review text and string identifiers below before release.
        GameItem[] saved = game.storage().get(this.key + ".items", GameItem[].class);
        if (saved != null) identities.addAll(List.of(saved));
    }

    public void prepare(GamePlayer player) {
        // RODO: Refresh the shared registry so simultaneous instances cannot assign the same index to different kits.
        // RODO: Review text and string identifiers below before release.
        GameItem[] latest = game.storage().get(key + ".items", GameItem[].class);
        if (latest != null) { identities.clear(); identities.addAll(List.of(latest)); }
        Map<Integer, Integer> amounts = new HashMap<>();
        java.util.Set<Integer> used = new java.util.HashSet<>();
        Map<Integer, GameItem> kit = new LinkedHashMap<>();
        boolean changed = false;
        for (int slot = 0; slot <= 40; slot++) {
            GameItem item = player.getInventoryItem(slot);
            if (item == null || item.isEmpty()) continue;
            item.removeData(INDEX);
            GameItem canonical = canonical(item);
            int id = -1;
            // RODO: Duplicate starting stacks get separate indices, while each player's matching kit reuses those indices.
            for (int i = 0; i < identities.size(); i++) {
                GameItem identity = identities.get(i);
                if (!used.contains(i) && identity.getAmount() == canonical.getAmount() && identity.isSimilar(canonical)) { id = i; break; }
            }
            if (id < 0) { id = identities.size(); identities.add(canonical); changed = true; }
            used.add(id);
            item.setData(INDEX, id);
            kit.put(slot, item);
            amounts.merge(id, item.getAmount(), Integer::sum);
        }
        // RODO: Review text and string identifiers below before release.
        if (changed) game.storage().set(key + ".items", identities.toArray(GameItem[]::new));
        starts.put(player.uuid(), amounts);
        Placement[] layout = game.storage().player(player).get(key, Placement[].class);
        if (layout == null) { kit.forEach(player::setInventoryItem); return; }
        // RODO: Only rearrange this round's kit. Saved items never create items or restore old kits.
        List<InventoryLayoutPlan.Start> initial = new ArrayList<>();
        Map<Integer, GameItem> sources = new HashMap<>();
        kit.forEach((slot, item) -> {
            int id = item.getData(INDEX);
            sources.put(id, item);
            initial.add(new InventoryLayoutPlan.Start(id, slot, item.getAmount(), item.getMaxStackSize()));
        });
        List<Placement> result = InventoryLayoutPlan.restore(initial, layout);
        player.clearItems();
        for (Placement entry : result) {
            GameItem item = new GameItem(sources.get(entry.id()).copyBukkitItem());
            item.setAmount(entry.count());
            player.setInventoryItem(entry.slot(), item);
        }
    }

    public void save(GamePlayer player) {
        Map<Integer, Integer> remaining = starts.remove(player.uuid());
        if (remaining == null) return;
        List<Placement> layout = new ArrayList<>();
        for (int slot = 0; slot <= 40; slot++) {
            GameItem item = player.getInventoryItem(slot);
            if (item == null) continue;
            Integer id = item.getData(INDEX);
            if (id == null) continue;
            int count = Math.min(item.getAmount(), remaining.getOrDefault(id, 0));
            if (count > 0) { layout.add(new Placement(id, slot, count)); remaining.merge(id, -count, Integer::sum); }
        }
        if (!layout.isEmpty()) game.storage().player(player).set(key, layout.toArray(Placement[]::new));
    }

    public void saveAll() { game.playerManager().getPlayers().forEach(this::save); }

    private GameItem canonical(GameItem source) {
        GameItem item = new GameItem(source.copyBukkitItem());
        if (GameItemComponents.hasTeamSync(item.copyBukkitItem())) {
            // RODO: Team colors are presentation; identical starting stacks share IDs across teams and players.
            String name = item.getType().name();
            for (org.bukkit.DyeColor color : org.bukkit.DyeColor.values()) {
                // RODO: Review text and string identifiers below before release.
                if (!name.startsWith(color.name() + "_")) continue;
                // RODO: Review text and string identifiers below before release.
                org.bukkit.Material white = org.bukkit.Material.matchMaterial("WHITE_" + name.substring(color.name().length()+1));
                if (white != null) item.setType(white);
                break;
            }
            item.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.DYED_COLOR);
        }
        return item;
    }

}
