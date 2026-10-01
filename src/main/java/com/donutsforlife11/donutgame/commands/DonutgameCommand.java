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
    public static final String RELOAD_PERMISSION = "donutgame.command.reload";

    private final Donutgame plugin;

    public DonutgameCommand(Donutgame plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("donutgame")
            .then(reloadCommand())
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload")
            .requires(source -> source.getSender().hasPermission(RELOAD_PERMISSION))
            .executes(context -> {
                reload(context.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            });
    }

    private void reload(CommandSender sender) {
        plugin.moduleService().unloadAll();
        plugin.reloadConfig();
        plugin.fileService().reload();
        plugin.getServer().getDatapackManager().refreshPacks();
        plugin.getServer().reloadData();
        plugin.getServer().updateResources();
        plugin.getLogger().info(
            "Reloaded Donutgame configuration and server data with "
                + plugin.fileService().gameModules().size()
                + " discovered module(s) and "
                + plugin.fileService().gameMaps().size()
                + " discovered map(s)."
        );
        sender.sendMessage(Component.text("Reloaded Donutgame.", NamedTextColor.GREEN));
    }

    @Override
    public String description() {
        return "Reloads Donutgame configuration, modules, and maps";
    }
}
