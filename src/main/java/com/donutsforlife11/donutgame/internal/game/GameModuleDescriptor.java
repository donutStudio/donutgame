package com.donutsforlife11.donutgame.internal.game;

import java.io.File;
import java.io.StringReader;

import org.bukkit.configuration.file.YamlConfiguration;

public record GameModuleDescriptor(
    String id,
    String mainClass,
    String name,
    Class<? extends GameModule> moduleClass,
    String configText,
    File file
) {
    public YamlConfiguration createConfig() {
        return YamlConfiguration.loadConfiguration(new StringReader(configText));
    }

    public GameModule createModule() throws ReflectiveOperationException {
        return moduleClass.getDeclaredConstructor().newInstance();
    }
}
