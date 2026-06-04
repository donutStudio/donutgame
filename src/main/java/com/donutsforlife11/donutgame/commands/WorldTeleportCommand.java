package com.donutsforlife11.donutgame.commands;

import com.donutsforlife11.donutgame.util.PluginCommand;
import com.donutsforlife11.donutgame.util.WorldManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldTeleportCommand implements PluginCommand {

    private WorldManager worldManager;

    public WorldTeleportCommand(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("worldtp")
            .executes(ctx -> {
                ctx.getSource().getSender().sendMessage(
                    Component.text("Usage: /worldtp <world_name>")
                );
                return Command.SINGLE_SUCCESS;
            })

            .then(Commands.argument("world_name", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (World world : Bukkit.getWorlds()) {
                        builder.suggest(world.getName());
                    }
                    return builder.buildFuture();
                })

                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        World world = Bukkit.getWorld(StringArgumentType.getString(ctx, "world_name"));
                        if (world == null) {
                            player.sendMessage(Component.text("World not found."));
                            return Command.SINGLE_SUCCESS;
                        }
                        worldManager.sendPlayerToWorld(player, world);
                    }
                    return Command.SINGLE_SUCCESS;
                })
            )

            .build();
    }

    @Override
    public String description() {
        return "Teleports you to another world";
    }
}