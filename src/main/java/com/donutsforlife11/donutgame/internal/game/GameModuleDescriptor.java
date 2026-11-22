package com.donutsforlife11.donutgame.internal.game;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

public record GameModuleDescriptor(
    String id,
    String mainClass,
    String name,
    Class<? extends GameModule> moduleClass,
    String configText,
    File file,
    File directory
) {
    public YamlConfiguration createConfig() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new File(directory, "config.yml"));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load config for module " + id + ".", exception);
        }
        return config;
    }

    public GameModule createModule() throws ReflectiveOperationException {
        return moduleClass.getDeclaredConstructor().newInstance();
    }
}
