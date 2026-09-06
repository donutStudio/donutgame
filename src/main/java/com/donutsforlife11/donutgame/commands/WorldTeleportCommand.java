package com.donutsforlife11.donutgame.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.internal.command.PluginCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class WorldTeleportCommand implements PluginCommand {
    public static final String PERMISSION = "donutgame.command.worldtp";

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("worldtp")
            .requires(source -> source.getSender().hasPermission(PERMISSION))
            .executes(context -> {
                context.getSource().getSender().sendMessage(Component.text("Usage: /worldtp <world_name>", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("world_name", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (World world : Bukkit.getWorlds()) {
                        builder.suggest(world.getName());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    if (!(context.getSource().getExecutor() instanceof Player player)) {
                        context.getSource().getSender().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    World world = Bukkit.getWorld(StringArgumentType.getString(context, "world_name"));
                    if (world == null) {
                        player.sendMessage(Component.text("World not found.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    player.teleportAsync(world.getSpawnLocation());
                    player.sendMessage(Component.text("Teleporting to " + world.getName() + ".", NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();
    }

    @Override
    public String description() {
        return "Teleports to a loaded world";
    }
}
