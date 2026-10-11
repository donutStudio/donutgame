package com.donutsforlife11.doppelganger;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.doppelganger.NPCWorkstationRegistry.NPCDestination;

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
        this.waitTicks = game.random().nextInt(20, 80);
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
        destination = game.random().nextDouble() < settings.wanderChance
            ? workstations.randomWanderDestination(location)
            : workstations.randomDestination();
        if (destination == null || !navigator.moveTo(destination.standAt())) {
            waitTicks = game.random().nextInt(40, 100);
            return;
        }
        state = State.WALKING;
    }

    private void tickWalking() {
        if (!navigator.tick()) {
            return;
        }
        faceDestination();
        waitTicks = game.random().nextInt(settings.idleMinTicks, settings.idleMaxTicks + 1);
        state = State.WORKING;
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
        entity.teleportNow(new GameLocation(game.world(), location.x(), location.y(), location.z(), yaw, 0.0f));
    }

    private enum State {
        IDLE,
        WALKING,
        WORKING
    }
}
