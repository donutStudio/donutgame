package com.donutsforlife11.doppelganger;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.doppelganger.NPCWorkstationRegistry.NPCDestination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NPCBehavior {
    private final Doppelganger game;
    private final NPCSettings settings;
    private final NPCWorkstationRegistry workstations;
    private final GameEntity entity;
    private final NPCNavigator navigator;
    private State state = State.IDLE;
    private int waitTicks;
    private NPCDestination destination;

    NPCBehavior(Doppelganger game, NPCSettings settings, NPCWorkstationRegistry workstations, GameEntity entity) {
        this.game = game;
        this.settings = settings;
        this.workstations = workstations;
        this.entity = entity;
        this.navigator = new NPCNavigator(game, settings, entity);
        this.waitTicks = game.random().nextInt(0, 40);
    }

    void tick() {
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
            waitTicks = game.random().nextInt(10, 30);
            return;
        }
        state = State.WALKING;
    }

    private NPCDestination nextReachableDestination(GameLocation location) {
        List<NPCDestination> choices = new ArrayList<>();
        if (game.random().nextDouble() < settings.wanderChance) {
            NPCDestination wander = workstations.randomWanderDestination(location);
            if (wander != null) {
                choices.add(wander);
            }
        }
        choices.addAll(workstations.destinations());
        Collections.shuffle(choices, game.random());
        choices.sort((left, right) -> Double.compare(distanceSquared(location, left), distanceSquared(location, right)));
        int attempts = Math.min(choices.size(), 12);
        for (int index = 0; index < attempts; index++) {
            NPCDestination choice = choices.get(index);
            if (choice != null && navigator.moveTo(choice.standAt())) {
                return choice;
            }
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
                state = State.IDLE;
                waitTicks = 0;
                destination = null;
                return;
            }
            case ARRIVED -> {
                faceDestination();
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

    private void faceDestination() {
        GameLocation location = entity.location();
        if (location == null || destination == null || destination.lookAt() == null) {
            return;
        }
        double dx = destination.lookAt().x() + 0.5 - location.x();
        double dz = destination.lookAt().z() + 0.5 - location.z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        entity.setRotation(yaw, 0.0f);
    }

    private enum State {
        IDLE,
        WALKING,
        WORKING
    }
}
