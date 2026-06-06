package com.donutsforlife11.donutgame.game;

import java.io.StringReader;

import org.bukkit.configuration.file.YamlConfiguration;

public record GameModuleDescriptor(
    String id,
    String mainClass,
    String name,
    Class<? extends GameModule> moduleClass,
    String configText
) {
    public YamlConfiguration createConfig() {
        return YamlConfiguration.loadConfiguration(new StringReader(configText));
    }
}
