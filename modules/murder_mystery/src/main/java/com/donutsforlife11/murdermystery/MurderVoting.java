package com.donutsforlife11.murdermystery;

import java.util.*;
import org.bukkit.Material;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.ui.GameInventoryGui;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class MurderVoting {
    private static final UUID SKIP = new UUID(0, 0);
    private final MurderMystery game;
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Map<UUID, GameInventoryGui> menus = new HashMap<>();
    private com.donutsforlife11.donutgame.api.time.GameTimer result;

    MurderVoting(MurderMystery game) { this.game = game; }

    void begin() {
        votes.clear();

    }

    void open(GamePlayer player, int page) {
        if (!game.meeting() || !game.living().contains(player)) return;
        var previous = menus.remove(player.uuid());
        if (previous != null) previous.close();
        // TODO: Review text and string identifiers below before release.
        var menu = game.uiManager().newInventoryGui(6, Component.text("Vote", NamedTextColor.GREEN));
        menus.put(player.uuid(), menu);
        var candidates = game.living();
        int start = Math.max(0, page) * 45;
        for (int i = start; i < Math.min(start+45, candidates.size()); i++) {
            GamePlayer candidate = candidates.get(i);
            GameItem head = GameItem.of(Material.PLAYER_HEAD);
            head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(candidate.playerProfile()));
            head.setData(DataComponentTypes.ITEM_NAME, Component.text(candidate.name(), NamedTextColor.WHITE));
            if (candidate.uuid().equals(votes.get(player.uuid()))) head.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            menu.setItem(i-start, head, (voter, click) -> vote(voter, candidate.uuid()));
        }
        // TODO: Review text and string identifiers below before release.
        menu.setItem(49, named(Material.BARRIER, "Skip"), (voter, click) -> vote(voter, SKIP));
        // TODO: Review text and string identifiers below before release.
        if (page > 0) menu.setItem(45, named(Material.ARROW, "Previous"), (voter, click) -> open(voter, page-1));
        // TODO: Review text and string identifiers below before release.
        if (start+45 < candidates.size()) menu.setItem(53, named(Material.ARROW, "Next"), (voter, click) -> open(voter, page+1));
        menu.open(player);
    }

    private GameItem named(Material material, String name) {
        GameItem item = GameItem.of(material);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        return item;
    }

    private void vote(GamePlayer voter, UUID target) {
        if (!game.meeting() || !game.living().contains(voter)
            // TODO: Review text and string identifiers below before release.
            || votes.containsKey(voter.uuid()) && !game.config().getBoolean("allow_vote_changing", true)
            || !target.equals(SKIP) && game.living().stream().noneMatch(p -> p.uuid().equals(target))) return;
        votes.put(voter.uuid(), target);
        // TODO: Review text and string identifiers below before release.
        game.uiManager().gameMessage(voter, Component.text("Vote recorded."));
        // TODO: Review text and string identifiers below before release.
        if (game.config().getBoolean("announce_votes", false)) {
            // TODO: Review text and string identifiers below before release.
            String who = game.config().getBoolean("anonymous_votes", true) ? "A player" : voter.name();
            // TODO: Review text and string identifiers below before release.
            game.message(who + " voted" + (game.config().getBoolean("anonymous_votes", true) ? "." : target.equals(SKIP) ? " to skip." : " for " + game.playerManager().getPlayer(target).name() + "."));
        }
        if (game.living().stream().allMatch(p -> votes.containsKey(p.uuid()))) resolve();
        else open(voter, 0);
    }

    void resolve() {
        if (!game.meeting()) return;
        game.beginResult();
        close();
        Map<UUID, Integer> totals = new HashMap<>();
        votes.values().forEach(target -> totals.merge(target, 1, Integer::sum));
        int maximum = totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var winners = totals.keySet().stream().filter(id -> totals.get(id) == maximum).toList();
        result = game.timeManager().newTimer(100).onFinish(ignored -> {
            // TODO: Review text and string identifiers below before release.
            if (winners.size() > 1) game.message("Vote tied.");
            // TODO: Review text and string identifiers below before release.
            else if (winners.isEmpty() || winners.getFirst().equals(SKIP)) game.message("Vote skipped.");
            else {
                GamePlayer ejected = game.playerManager().getPlayer(winners.getFirst());
                // TODO: Review text and string identifiers below before release.
                if (ejected != null) game.eliminate(ejected, "was voted out", true);
            }
            game.endMeeting();
        }).start();
    }

    void remove(GamePlayer player) {
        votes.remove(player.uuid());
        votes.entrySet().removeIf(entry -> entry.getValue().equals(player.uuid()));
        var menu = menus.remove(player.uuid());
        if (menu != null) menu.close();
        if (game.meeting() && game.living().stream().allMatch(p -> votes.containsKey(p.uuid()))) resolve();
    }

    void close() {
        if (result != null) { result.cancel(); result = null; }
        menus.values().forEach(GameInventoryGui::close); menus.clear();
    }
}
