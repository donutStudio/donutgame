package com.donutsforlife11.donutgame.commands;

import org.bukkit.command.CommandSender;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.internal.command.PluginCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class DonutgameCommand implements PluginCommand {
    private final Donutgame plugin;

    public DonutgameCommand(Donutgame plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("donutgame")
            .requires(source -> source.getSender().hasPermission(PluginCommand.PERMISSION))
            .then(reloadCommand())
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload")
            .executes(context -> {
                reload(context.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            });
    }

    private void reload(CommandSender sender) {
        plugin.moduleService().unloadAll();
        plugin.reloadConfig();
        plugin.fileService().closeModuleLoaders();
        plugin.fileService().reload();
        plugin.getLogger().info("Reloaded Donutgame configuration, modules, and maps.");
        sender.sendMessage(Component.text("Reloaded Donutgame.", NamedTextColor.GREEN));
    }

    @Override
    public String description() {
        return "Reloads Donutgame files and configuration";
    }
}
