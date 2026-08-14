package com.donutsforlife11.donutgame.internal.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface PluginCommand {
    String PERMISSION = "donutgame.command";

    LiteralCommandNode<CommandSourceStack> node();
    
    default String description() {
        return "";
    }
    default Collection<String> aliases() {
        return List.of();
    }
}
