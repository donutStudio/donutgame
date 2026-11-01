package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.profile.PlayerTextures;

import com.donutsforlife11.donutgame.api.entity.npc.GameNpc;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import io.papermc.paper.registry.keys.tags.BlockTypeTagKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public class Doppelganger extends GameModule {
    private final List<GameNpc> testNpcs = new ArrayList<>();
    private GameTimer diagnostics;

    @Override
    public void onStart() {
        if (diagnostics != null) {
            diagnostics.cancel();
        }
        for (GameNpc npc : testNpcs) {
            npc.remove();
        }
        testNpcs.clear();
        Consumer<String> debug = message -> {
            uiManager().chat(playerManager().getOnlinePlayers(), Component.text("[npc-test] " + message, NamedTextColor.YELLOW));
            log("[npc-test] " + message);
        };
        GamePlayer model = playerManager().getOnlinePlayers().stream().findFirst().orElse(null);
        GameLocation origin = model == null ? world().worldSpawn() : model.location();
        List<GameLocation> points = world().getPoints("npc_test");
        GameLocation destination = points.isEmpty()
            ? new GameLocation(world(), origin.x() + 6, origin.y(), origin.z()) : points.getFirst();
        GameNpc walker = world().newNpc(origin);
        GameNpc follower = model == null
            ? world().newNpc(new GameLocation(world(), origin.x(), origin.y(), origin.z() + 2))
            : world().newNpc(new GameLocation(world(), origin.x(), origin.y(), origin.z() + 2), model);
        GameNpc skinned = world().newNpc(new GameLocation(world(), origin.x(), origin.y(), origin.z() - 2), follower.profile());
        testNpcs.addAll(List.of(walker, follower, skinned));
        String[] names = {"Default walker", "Player follower", "Skin / pose test"};
        for (int i = 0; i < testNpcs.size(); i++) {
            GameNpc npc = testNpcs.get(i);
            String label = names[i];
            npc.setCustomName(Component.text(label));
            npc.setCustomNameVisible(true);
            npc.setDescription(Component.text("Right click to test; punch for jump + hand swings"));
            npc.setDescriptionVisible(true);
            npc.setInteractionCooldown(10);
            npc.setAttackCooldown(10);
            npc.controller().onNavigationSuccess(done -> debug.accept(label + " SUCCESS at "
                + String.format(Locale.ROOT, "%.2f, %.2f, %.2f", done.location().x(), done.location().y(), done.location().z())));
            npc.controller().onNavigationFail(done -> debug.accept(label + " FAIL (blocked, no path, out of range, immovable or timeout="
                + done.controller().navigationTimeout() + ")"));
            npc.onAttack(player -> {
                npc.jump();
                npc.swingMainHand();
                npc.swingOffhand();
                debug.accept(label + " attacked by " + player.name() + "; invulnerable=" + npc.isInvulnerable()
                    + ", health=" + npc.health() + ", immovable=" + npc.isImmovable() + "; jump + swings requested");
            });
            debug.accept(label + " spawned: profile=" + npc.profile().getName() + ", textures=" + npc.profile().hasTextures()
                + ", safe crossing=" + npc.controller().pathfindSettings().crossableBlocks().size()
                + ", safe supports=" + npc.controller().pathfindSettings().walkableBlocks().size());
        }
        // A tag can be removed and restored without enumerating every carpet material.
        walker.controller().pathfindSettings().removeWalkableTags(BlockTypeTagKeys.WOOL_CARPETS);
        walker.controller().pathfindSettings().addWalkableTags(BlockTypeTagKeys.WOOL_CARPETS);
        walker.setItem(EquipmentSlot.HAND, GameItem.of(Material.COMPASS));
        int[] phase = {0};
        walker.onInteract(player -> {
            var control = walker.controller();
            switch (phase[0]++ % 9) {
                case 0 -> {
                    control.moveTo(destination);
                    debug.accept("Walker: direct moveTo destination (obstacles should block it).");
                }
                case 1 -> {
                    List<GameLocation> path = control.findPath(destination);
                    debug.accept("Walker: findPath returned " + path.size() + " waypoints; moveAlong the result.");
                    control.cancelNavigation();
                    if (!path.isEmpty()) {
                        control.moveAlong(path);
                    }
                }
                case 2 -> {
                    control.pause();
                    debug.accept("Walker: PAUSED; position and timeout should stay unchanged. Click to resume.");
                }
                case 3 -> {
                    control.resume();
                    debug.accept("Walker: RESUMED.");
                }
                case 4 -> {
                    walker.setSprinting(true);
                    control.pathfindTo(origin);
                    debug.accept("Walker: sprinting pathfindTo origin; should route around obstacles.");
                }
                case 5 -> {
                    walker.setSprinting(false);
                    walker.setSneaking(true);
                    control.pathfindTo(destination);
                    debug.accept("Walker: sneaking pathfindTo destination; watch the pose, hitbox and speed.");
                }
                case 6 -> {
                    walker.setSneaking(false);
                    walker.setImmovable(true);
                    control.moveTo(destination);
                    debug.accept("Walker: immovable navigation must FAIL immediately; interactions still work.");
                }
                case 7 -> {
                    walker.setImmovable(false);
                    control.setNavigationTimeout(40);
                    control.moveTo(new GameLocation(world(), origin.x(), origin.y() + 5, origin.z()));
                    debug.accept("Walker: unreachable vertical target must time out after 40 active ticks.");
                }
                default -> {
                    control.cancelNavigation();
                    control.setNavigationTimeout(200);
                    walker.setRotationTowards(player.location(), 20);
                    debug.accept("Walker: cancelled without failure; smoothly turning toward you over 20 ticks. Cycle resets.");
                }
            }
        });
        boolean[] pathFollow = {true};
        follower.controller().setArrivalDistance(2);
        follower.onInteract(player -> {
            pathFollow[0] = !pathFollow[0];
            if (pathFollow[0]) {
                follower.controller().pathfindFollowing(player);
            } else {
                follower.controller().follow(player);
            }
            debug.accept("Follower: " + (pathFollow[0] ? "A*" : "direct") + " following " + player.name()
                + "; stop within 2 blocks, restart when you move, wait beyond 32 blocks.");
        });
        if (model != null) {
            follower.controller().pathfindFollowing(model);
        }
        boolean[] slim = {false};
        skinned.setImmovable(true);
        skinned.onInteract(player -> {
            slim[0] = !slim[0];
            var profile = player.playerProfile();
            var textures = profile.getTextures();
            if (textures.getSkin() != null) {
                textures.setSkin(textures.getSkin(), slim[0] ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC);
                profile.setTextures(textures);
            }
            skinned.setProfile(profile);
            skinned.setSneaking(slim[0]);
            skinned.setDescriptionVisible(!slim[0]);
            skinned.setRotationTowards(player.location(), 10);
            debug.accept("Skin test: copied " + player.name() + " using " + textures.getSkinModel()
                + ", skin URL=" + textures.getSkin() + ", sneaking=" + skinned.isSneaking()
                + ", description=" + !slim[0] + "; signed profiles also tested by follower.");
        });
        debug.accept("Right click each NPC to cycle tests. Optional map points 'npc_test' select the walking destination.");
        diagnostics = timeManager().newTimer().onTick(100, timer -> {
            for (GameNpc npc : testNpcs) {
                if (npc.isDead()) {
                    debug.accept("NPC " + npc.uuid() + " was removed or died.");
                    continue;
                }
                var control = npc.controller();
                var location = npc.location();
                var target = control.navigationTarget();
                debug.accept(String.format(Locale.ROOT, "%s pos=(%.2f, %.2f, %.2f) speed=%.3f navigating=%s paused=%s target=%s",
                    npc.profile().getName(), location.x(), location.y(), location.z(), npc.velocity().length(),
                    control.isNavigating(), control.isPaused(), target == null ? "none"
                        : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", target.x(), target.y(), target.z())));
            }
        }).start();
    }

    @Override
    public void onUnload() {
        if (diagnostics != null) {
            diagnostics.cancel();
        }
        testNpcs.forEach(GameNpc::remove);
        testNpcs.clear();
    }
}
