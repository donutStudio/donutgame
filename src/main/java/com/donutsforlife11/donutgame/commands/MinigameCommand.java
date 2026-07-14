package com.donutsforlife11.donutgame.commands;

import java.util.Map;

import org.bukkit.command.CommandSender;

import com.donutsforlife11.donutgame.internal.command.PluginCommand;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MinigameCommand implements PluginCommand {
    private final ModuleService moduleService;
    private final Map<String, GameModuleDescriptor> gameModules;

    public MinigameCommand(
        FileService fileService,
        ModuleService moduleService
    ) {
        this.moduleService = moduleService;
        this.gameModules = fileService.gameModules();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("minigame")
            .then(loadCommand())
            .then(unloadCommand())
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> loadCommand() {
        return Commands.literal("load")
            .then(Commands.argument(
                "game_id",
                StringArgumentType.word()
            )
                .suggests((context, builder) -> {
                    gameModules.keySet().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String gameId = StringArgumentType.getString(
                        context,
                        "game_id"
                    );

                    try {
                        return loadModule(
                            context.getSource().getSender(),
                            gameId
                        );
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load game module!");
                    }
                })
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> unloadCommand() {
        return Commands.literal("unload")
            .then(Commands.argument(
                "index",
                IntegerArgumentType.integer(0)
            )
                .suggests((context, builder) -> {
                    for (int i : moduleService.activeGames().keySet()) {
                        builder.suggest(i);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    int index = IntegerArgumentType.getInteger(
                        context,
                        "index"
                    );

                    return unloadModule(
                        context.getSource().getSender(),
                        index
                    );
                })
            );
    }

    private int loadModule(CommandSender sender, String gameId) throws Exception {
        GameModuleDescriptor descriptor = gameModules.get(gameId);

        if (descriptor == null) {
            sendError(sender, "Unknown module ID: " + gameId);
            return Command.SINGLE_SUCCESS;
        }

        try {
            moduleService.loadModule(descriptor)
                .thenAccept(module -> sendSuccess(
                    sender,
                    "Loaded "
                        + descriptor.id()
                        + " as active game "
                        + module.index()
                        + "."
                ));
        } catch (ReflectiveOperationException exception) {
            sendError(
                sender,
                "Could not create module: " + exception.getMessage()
            );

            exception.printStackTrace();
        }

        return Command.SINGLE_SUCCESS;
    }

    private int unloadModule(CommandSender sender, int index) {
        moduleService.unloadModule(index)
            .thenAccept(unloaded -> {
                if (unloaded) {
                    sendSuccess(
                        sender,
                        "Unloaded active game " + index + "."
                    );
                } else {
                    sendError(
                        sender,
                        "No active game exists at index " + index + "."
                    );
                }
            });

        return Command.SINGLE_SUCCESS;
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(Component.text(
            message,
            NamedTextColor.GREEN
        ));
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(
            message,
            NamedTextColor.RED
        ));
    }

    @Override
    public String description() {
        return "Loads and unloads minigame modules";
    }
}