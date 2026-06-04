package com.donutsforlife11.donutgame.util;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public final class CommandRegistrar {
    private final JavaPlugin plugin;
    private final List<PluginCommand> commands;

    public CommandRegistrar(JavaPlugin plugin, List<PluginCommand> commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();

            for (PluginCommand command : commands) {
                registrar.register(
                    command.node(),
                    command.description(),
                    command.aliases()
                );
            }
        });
    }
}
