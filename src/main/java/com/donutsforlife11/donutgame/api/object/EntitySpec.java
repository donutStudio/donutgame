package com.donutsforlife11.donutgame.api.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

public final class EntitySpec<E extends Entity> {
    private final EntityType type;
    private final Class<E> entityClass;
    private final List<Consumer<? super E>> configurations;

    private EntitySpec(EntityType type, Class<E> entityClass, List<Consumer<? super E>> configurations) {
        this.type = Objects.requireNonNull(type, "type");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        if (type.getEntityClass() != null && !entityClass.isAssignableFrom(type.getEntityClass())) {
            throw new IllegalArgumentException("Entity type " + type + " does not spawn " + entityClass.getName() + ".");
        }
        this.configurations = List.copyOf(configurations);
    }

    public static <E extends Entity> EntitySpec<E> of(EntityType type, Class<E> entityClass) {
        return new EntitySpec<>(type, entityClass, List.of());
    }

    public EntityType type() {
        return type;
    }

    public Class<E> entityClass() {
        return entityClass;
    }

    public EntitySpec<E> configure(Consumer<? super E> configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<Consumer<? super E>> nextConfigurations = new ArrayList<>(configurations);
        nextConfigurations.add(configuration);
        return new EntitySpec<>(type, entityClass, nextConfigurations);
    }

    public E spawn(World world, Location location) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(location, "location");
        if (location.getWorld() != null && !world.equals(location.getWorld())) {
            throw new IllegalArgumentException("Spawn location belongs to " + location.getWorld().getName() + ", not " + world.getName() + ".");
        }
        E entity = entityClass.cast(world.spawnEntity(location, type));
        configurations.forEach(configuration -> configuration.accept(entity));
        return entity;
    }
}
