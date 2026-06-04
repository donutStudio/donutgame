package com.donutsforlife11.donutgame.commands;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.game.GameModule;
import com.donutsforlife11.donutgame.game.ModuleManager;
import com.donutsforlife11.donutgame.util.PluginCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

import java.util.Map;

public final class MinigameCommand implements PluginCommand {

    private final ModuleManager moduleManager;

    private final Map<String, Class<? extends GameModule>> gameModuleClasses;
    private final Map<Integer, GameModule> activeGames;

    public MinigameCommand(Donutgame plugin, ModuleManager moduleManager) {
        this.moduleManager = moduleManager;

        gameModuleClasses = plugin.getGameModuleClasses();
        activeGames = moduleManager.getActiveGames();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("minigame")
            .then(Commands.literal("load")
                .then(Commands.argument("game_id", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String game_id : gameModuleClasses.keySet()) {
                            builder.suggest(game_id);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String gameId = StringArgumentType.getString(ctx, "game_id");
                        Class<? extends GameModule> moduleClass = gameModuleClasses.get(gameId);

                        if (moduleClass == null) {
                            ctx.getSource().getSender().sendMessage(Component.text("Unknown game id: " + gameId));
                            return Command.SINGLE_SUCCESS;
                        }

                        try {
                            GameModule module = moduleClass.getDeclaredConstructor().newInstance();
                            moduleManager.loadModule(module);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )


            .then(Commands.literal("unload")
                .then(Commands.argument("game_index", IntegerArgumentType.integer())
                    .suggests((ctx, builder) -> {
                        for (int game_index : activeGames.keySet()) {
                            builder.suggest(game_index);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        int gameIndex = IntegerArgumentType.getInteger(ctx, "game_index");

                        if (!activeGames.containsKey(gameIndex)) {
                            ctx.getSource().getSender().sendMessage(Component.text("No active game running with index " + gameIndex));
                            return Command.SINGLE_SUCCESS;
                        }

                        moduleManager.unloadModule(IntegerArgumentType.getInteger(ctx, "game_index"));
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .build();
    }

    @Override
    public String description() {
        return "Start, stop, or manage minigames in general";
    }
}