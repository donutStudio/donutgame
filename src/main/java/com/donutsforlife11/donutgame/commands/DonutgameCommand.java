package com.donutsforlife11.donutgame.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.command.PluginCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
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
            .then(gamesCommand())
            .then(worldsCommand())
            .then(unloadWorldCommand())
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload")
            .executes(context -> {
                reload(context.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            });
    }

    private LiteralArgumentBuilder<CommandSourceStack> gamesCommand() {
        return Commands.literal("games")
            .executes(context -> {
                listGames(context.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            });
    }

    private LiteralArgumentBuilder<CommandSourceStack> worldsCommand() {
        return Commands.literal("worlds")
            .executes(context -> {
                listWorlds(context.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            });
    }

    private LiteralArgumentBuilder<CommandSourceStack> unloadWorldCommand() {
        return Commands.literal("unloadworld")
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (String worldName : plugin.worldService().loadedWorldNames()) {
                        builder.suggest(worldName);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    unloadWorld(context.getSource().getSender(), StringArgumentType.getString(context, "world"));
                    return Command.SINGLE_SUCCESS;
                })
            );
    }

    private void reload(CommandSender sender) {
        plugin.moduleService().unloadAll();
        plugin.reloadConfig();
        plugin.fileService().closeModuleLoaders();
        plugin.fileService().reload();
        plugin.getServer().getDatapackManager().refreshPacks();
        plugin.getServer().reloadData();
        plugin.getLogger().info("Reloaded Donutgame configuration, modules, maps, and server data.");
        sender.sendMessage(Component.text("Reloaded Donutgame.", NamedTextColor.GREEN));
    }

    private void listGames(CommandSender sender) {
        if (plugin.moduleService().activeGames().isEmpty()) {
            sender.sendMessage(Component.text("No active games.", NamedTextColor.YELLOW));
            return;
        }
        for (GameModule game : plugin.moduleService().activeGames().values()) {
            GameWorld world = game.world();
            String worldName = world == null ? "no world" : world.bukkitWorld().getName();
            sender.sendMessage(Component.text(
                "Game " + game.index() + ": " + game.id() + " players=" + game.playerManager().getPlayers().size()
                    + " world=" + worldName + " started=" + game.hasStarted() + " transitioning=" + game.isTransitioning(),
                NamedTextColor.GRAY
            ));
        }
    }

    private void listWorlds(CommandSender sender) {
        for (World world : Bukkit.getWorlds()) {
            sender.sendMessage(Component.text(
                world.getName() + " players=" + world.getPlayers().size() + " entities=" + world.getEntities().size(),
                NamedTextColor.GRAY
            ));
        }
    }

    private void unloadWorld(CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(Component.text("World not found: " + worldName, NamedTextColor.RED));
            return;
        }
        if (Bukkit.getWorlds().size() <= 1) {
            sender.sendMessage(Component.text("Cannot unload the only loaded world.", NamedTextColor.RED));
            return;
        }
        for (GameModule game : plugin.moduleService().activeGames().values()) {
            if (game.world() != null && game.world().bukkitWorld().equals(world)) {
                sender.sendMessage(Component.text("World " + worldName + " is owned by active game " + game.index() + ". Unload the game instead.", NamedTextColor.RED));
                return;
            }
        }
        plugin.worldService().unloadWorld(worldName, true).thenRun(() ->
            sender.sendMessage(Component.text("Unloaded world " + worldName + ".", NamedTextColor.GREEN))
        ).exceptionally(throwable -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to unload world " + worldName + ".", throwable);
            sender.sendMessage(Component.text("Failed to unload world " + worldName + ". See console.", NamedTextColor.RED));
            return null;
        });
    }

    @Override
    public String description() {
        return "Reloads Donutgame files and configuration";
    }
}
