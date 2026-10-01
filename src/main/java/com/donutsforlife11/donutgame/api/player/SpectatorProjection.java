package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

final class SpectatorProjection {
    private SpectatorProjection() {
    }

    static void applyNonSpectatorOverlay(Player player, PlayerSnapshot playingState) {
        GameMode playingMode = playingMode(playingState);

        player.setGameMode(playingMode);
        player.setInvulnerable(playingState.invulnerable);
        player.setInvisible(playingState.invisible);
        player.setCanPickupItems(playingState.canPickupItems);

        boolean effectiveAllowFlight = playingState.allowsFlight();

        player.setAllowFlight(effectiveAllowFlight);
        player.setFlying(effectiveAllowFlight && playingState.flying);
        player.setNoDamageTicks(0);
        player.setFallDistance(0.0f);
    }

    static void clearPhysical(Player player) {
        if (player == null) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCanPickupItems(true);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE);
        player.setFlying(false);
        player.setNoDamageTicks(0);
        player.setFallDistance(0.0f);
        player.updateInventory();
    }

    static boolean matches(Player player, PlayerSnapshot playingState) {
        if (player == null) {
            return true;
        }

        boolean effectiveAllowFlight = playingState.allowsFlight();

        return player.getGameMode() == playingMode(playingState)
            && player.isInvulnerable() == playingState.invulnerable
            && player.isInvisible() == playingState.invisible
            && player.getCanPickupItems() == playingState.canPickupItems
            && player.getAllowFlight() == effectiveAllowFlight
            && player.isFlying() == (effectiveAllowFlight && playingState.flying);
    }

    static String describeMismatch(Player player, PlayerSnapshot playingState) {
        if (player == null) {
            return "player=offline";
        }
        List<String> differences = new ArrayList<>();
        boolean expectedAllowFlight = playingState.allowsFlight();
        boolean expectedFlying = expectedAllowFlight && playingState.flying;

        addDifference(differences, "gameMode", player.getGameMode(), playingMode(playingState));
        addDifference(differences, "invulnerable", player.isInvulnerable(), playingState.invulnerable);
        addDifference(differences, "invisible", player.isInvisible(), playingState.invisible);
        addDifference(differences, "canPickupItems", player.getCanPickupItems(), playingState.canPickupItems);
        addDifference(differences, "allowFlight", player.getAllowFlight(), expectedAllowFlight);
        addDifference(differences, "flying", player.isFlying(), expectedFlying);
        return differences.isEmpty() ? "none" : String.join(", ", differences);
    }

    static String describePhysical(Player player) {
        if (player == null) {
            return "offline";
        }
        return "gameMode=" + player.getGameMode()
            + ", invulnerable=" + player.isInvulnerable()
            + ", invisible=" + player.isInvisible()
            + ", canPickupItems=" + player.getCanPickupItems()
            + ", allowFlight=" + player.getAllowFlight()
            + ", flying=" + player.isFlying()
            + ", invisibilityEffect=" + player.hasPotionEffect(PotionEffectType.INVISIBILITY)
            + ", noDamageTicks=" + player.getNoDamageTicks()
            + ", dead=" + player.isDead();
    }

    static String describeExpected(PlayerSnapshot playingState) {
        boolean expectedAllowFlight = playingState.allowsFlight();
        boolean expectedInvisibilityEffect = playingState.effects.stream()
            .anyMatch(effect -> effect.getType().equals(PotionEffectType.INVISIBILITY));
        return "gameMode=" + playingMode(playingState)
            + ", invulnerable=" + playingState.invulnerable
            + ", invisible=" + playingState.invisible
            + ", canPickupItems=" + playingState.canPickupItems
            + ", allowFlight=" + expectedAllowFlight
            + ", flying=" + (expectedAllowFlight && playingState.flying)
            + ", invisibilityEffect=" + expectedInvisibilityEffect;
    }

    private static GameMode playingMode(PlayerSnapshot playingState) {
        return playingState.gameMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : playingState.gameMode;
    }

    private static void addDifference(List<String> differences, String name, Object actual, Object expected) {
        if (!java.util.Objects.equals(actual, expected)) {
            differences.add(name + "=" + actual + " (expected " + expected + ")");
        }
    }
}
