package com.donutsforlife11.doppelganger;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.doppelganger.NPCWorkstationRegistry.NPCDestination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NPCBehavior {
    private final Doppelganger game;
    private final NPCSettings settings;
    private final NPCWorkstationRegistry workstations;
    private final GameEntity entity;
    private final NPCNavigator navigator;
    private State state = State.IDLE;
    private int waitTicks;
    private int ticks;
    private int lastDestinationDebugTick = Integer.MIN_VALUE;
    private NPCDestination destination;
    private final Map<String, Integer> failedDestinationCooldowns = new HashMap<>();
    private final Map<String, Integer> recentDestinationCooldowns = new HashMap<>();

    NPCBehavior(Doppelganger game, NPCSettings settings, NPCWorkstationRegistry workstations, GameEntity entity) {
        this.game = game;
        this.settings = settings;
        this.workstations = workstations;
        this.entity = entity;
        this.navigator = new NPCNavigator(game, settings, entity);
        this.waitTicks = game.random().nextInt(0, 40);
    }

    void tick() {
        ticks++;
        tickFailedDestinationCooldowns();
        tickRecentDestinationCooldowns();
        switch (state) {
            case IDLE -> tickIdle();
            case WALKING -> tickWalking();
            case WORKING -> tickWorking();
        }
    }

    private void tickIdle() {
        if (waitTicks-- > 0) {
            return;
        }
        GameLocation location = entity.location();
        destination = nextReachableDestination(location);
        if (destination == null) {
            debugDestinations("No reachable destination from " + describe(location) + "; retrying shortly.");
            waitTicks = game.random().nextInt(10, 30);
            return;
        }
        debugDestinations("Walking from " + describe(location) + " to " + describe(destination.standAt())
            + (destination.lookAt() == null ? " (wander)" : " (workstation " + describe(destination.lookAt()) + ")"));
        state = State.WALKING;
    }

    private NPCDestination nextReachableDestination(GameLocation location) {
        // Occasionally wander instead of visiting a workstation.
        boolean shouldWander =
            workstations.destinations().isEmpty()
            || game.random().nextDouble() < settings.wanderChance;

        if (shouldWander) {
            NPCDestination wander =
                workstations.randomWanderDestination(location);

            if (wander != null
                    && !isCoolingDown(wander)
                    && navigator.moveTo(wander.standAt())) {
                return wander;
            }
        }

        // Otherwise, search for a reachable workstation.
        List<NPCDestination> choices =
            new ArrayList<>(workstations.destinations());

        Collections.shuffle(choices, game.random());

        // Prefer destinations that haven't recently been visited.
        choices.sort((a, b) ->
            Boolean.compare(isRecent(a), isRecent(b))
        );

        int attempts = 0;

        for (NPCDestination choice : choices) {
            if (attempts >= 16) {
                break;
            }

            if (choice == null || isCoolingDown(choice)) {
                continue;
            }

            if (distanceSquared(location, choice) < 6.25) {
                continue;
            }

            attempts++;

            if (navigator.moveTo(choice.standAt())) {
                return choice;
            }

            rememberFailedDestination(choice);
        }

        // Fallback: try wandering if no workstation was reachable.
        NPCDestination fallback =
            workstations.randomWanderDestination(location);

        if (fallback != null
                && navigator.moveTo(fallback.standAt())) {
            return fallback;
        }

        return null;
    }

    private double distanceSquared(GameLocation origin, NPCDestination destination) {
        if (origin == null || destination == null || destination.standAt() == null) {
            return Double.MAX_VALUE;
        }
        double dx = origin.x() - destination.standAt().x();
        double dy = origin.y() - destination.standAt().y();
        double dz = origin.z() - destination.standAt().z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void tickWalking() {
        switch (navigator.tick()) {
            case MOVING -> {
                return;
            }
            case BLOCKED -> {
                debugMovement("Blocked while walking to " + describe(destination == null ? null : destination.standAt())
                    + ": " + navigator.lastFailure());
                if (destination != null) {
                    rememberFailedDestination(destination);
                }
                state = State.IDLE;
                waitTicks = game.random().nextInt(8, 24);
                destination = null;
                return;
            }
            case ARRIVED -> {
                faceDestination();
                if (destination != null) {
                    rememberRecentDestination(destination);
                }
                waitTicks = game.random().nextInt(settings.idleMinTicks, settings.idleMaxTicks + 1);
                state = State.WORKING;
            }
        }
    }

    private void tickWorking() {
        faceDestination();
        if (waitTicks-- > 0) {
            return;
        }
        state = State.IDLE;
        waitTicks = game.random().nextInt(15, 45);
    }

    private void tickFailedDestinationCooldowns() {
        failedDestinationCooldowns.replaceAll((ignored, ticksLeft) -> ticksLeft - 1);
        failedDestinationCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private void tickRecentDestinationCooldowns() {
        recentDestinationCooldowns.replaceAll((ignored, ticksLeft) -> ticksLeft - 1);
        recentDestinationCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private boolean isCoolingDown(NPCDestination destination) {
        return failedDestinationCooldowns.containsKey(destinationKey(destination));
    }

    private boolean isRecent(NPCDestination destination) {
        return recentDestinationCooldowns.containsKey(destinationKey(destination));
    }

    private void rememberFailedDestination(NPCDestination destination) {
        failedDestinationCooldowns.put(destinationKey(destination), game.random().nextInt(80, 180));
    }

    private void rememberRecentDestination(NPCDestination destination) {
        recentDestinationCooldowns.put(destinationKey(destination), game.random().nextInt(220, 420));
    }

    private String destinationKey(NPCDestination destination) {
        GameLocation standAt = destination == null ? null : destination.standAt();
        if (standAt == null) {
            return "null";
        }
        return standAt.blockX() + ":" + standAt.blockY() + ":" + standAt.blockZ();
    }

    private void debugDestinations(String message) {
        if (settings.debugDestinations && ticks - lastDestinationDebugTick >= settings.debugSampleTicks) {
            lastDestinationDebugTick = ticks;
            game.log("[npc:destinations] " + entity.uuid() + " " + message);
        }
    }

    private void debugMovement(String message) {
        if (settings.debugMovement) {
            game.log("[npc:movement] " + entity.uuid() + " " + message);
        }
    }

    private String describe(GameLocation location) {
        if (location == null) {
            return "null";
        }
        return location.blockX() + "," + location.blockY() + "," + location.blockZ();
    }

    private void faceDestination() {
        GameLocation location = entity.location();

        if (location == null
                || destination == null
                || destination.lookAt() == null) {
            return;
        }

        double dx = destination.lookAt().x() + 0.5
            - location.x();

        double dz = destination.lookAt().z() + 0.5
            - location.z();

        if (dx * dx + dz * dz < 0.000001) {
            return;
        }

        float targetYaw = (float) Math.toDegrees(
            Math.atan2(-dx, dz)
        );

        float currentYaw = location.yaw();

        float difference = targetYaw - currentYaw;

        // Normalize angle to [-180, 180].
        difference = (difference + 540.0f) % 360.0f - 180.0f;

        // Maximum 6 degrees per tick.
        float turn = Math.max(
            -6.0f,
            Math.min(6.0f, difference)
        );

        entity.setRotation(
            currentYaw + turn,
            0.0f
        );
    }

    private enum State {
        IDLE,
        WALKING,
        WORKING
    }
}
