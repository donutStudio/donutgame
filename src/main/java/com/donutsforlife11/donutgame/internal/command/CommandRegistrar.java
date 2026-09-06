package com.donutsforlife11.donutgame.internal.command;

import java.util.List;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public class CommandRegistrar {
    private final Plugin plugin;
    private final List<PluginCommand> commands;

    public CommandRegistrar(Plugin plugin, List<PluginCommand> commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (PluginCommand command : commands) {
                registrar.register(command.node(), command.description(), command.aliases());
            }
        });
    }
}
