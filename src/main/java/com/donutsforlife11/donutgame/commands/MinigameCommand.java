package com.donutsforlife11.donutgame.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.internal.command.PluginCommand;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MinigameCommand implements PluginCommand {
    public static final String LOAD_PERMISSION = "donutgame.command.minigame.load";
    public static final String JOIN_PERMISSION = "donutgame.command.minigame.join";
    public static final String LEAVE_PERMISSION = "donutgame.command.minigame.leave";
    public static final String UNLOAD_PERMISSION = "donutgame.command.minigame.unload";
    public static final String LOCK_PERMISSION = "donutgame.command.minigame.lock";
    public static final String UNLOCK_PERMISSION = "donutgame.command.minigame.unlock";

    private static final String GAME_ID_ARGUMENT = "game_id";
    private static final String CONFIG_ARGUMENT = "config";
    private static final String TARGETS_ARGUMENT = "targets";
    private static final Set<String> RESERVED_CONFIG_KEYS = Set.of("id", "name", "main_class");

    private final FileService fileService;
    private final ModuleService moduleService;

    public MinigameCommand(FileService fileService, ModuleService moduleService) {
        this.fileService = fileService;
        this.moduleService = moduleService;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("minigame")
            .then(loadCommand())
            .then(joinCommand())
            .then(leaveCommand())
            .then(unloadCommand())
            .then(lockCommand())
            .then(unlockCommand())
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> loadCommand() {
        return Commands.literal("load")
            .requires(source -> source.getSender().hasPermission(LOAD_PERMISSION))
            .then(Commands.argument(GAME_ID_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> {
                    fileService.gameModules().keySet().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> loadModule(context, selectedPlayersOrSelf(context, false), null, false))
                .then(Commands.literal("lock")
                    .executes(context -> loadModule(context, selectedPlayersOrSelf(context, false), null, true))
                )
                .then(Commands.argument(TARGETS_ARGUMENT, playerSelector())
                    .executes(context -> loadModule(context, selectedPlayersOrSelf(context, true), null, false))
                    .then(Commands.literal("lock")
                        .executes(context -> loadModule(context, selectedPlayersOrSelf(context, true), null, true))
                    )
                    .then(Commands.argument(CONFIG_ARGUMENT, new InlineConfigArgument(this::suggestInlineConfig))
                        .executes(context -> loadModule(context, selectedPlayersOrSelf(context, true), configOverride(context), false))
                        .then(Commands.literal("lock")
                            .executes(context -> loadModule(context, selectedPlayersOrSelf(context, true), configOverride(context), true))
                        )
                    )
                )
                .then(Commands.argument(CONFIG_ARGUMENT, new InlineConfigArgument(this::suggestInlineConfig))
                    .executes(context -> loadModule(context, selectedPlayersOrSelf(context, false), configOverride(context), false))
                    .then(Commands.literal("lock")
                        .executes(context -> loadModule(context, selectedPlayersOrSelf(context, false), configOverride(context), true))
                    )
                )
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> joinCommand() {
        return Commands.literal("join")
            .requires(source -> source.getSender().hasPermission(JOIN_PERMISSION))
            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                .suggests((context, builder) -> {
                    for (int index : moduleService.activeGames().keySet()) {
                        builder.suggest(index);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> joinGame(context, selectedPlayersOrSelf(context, false)))
                .then(Commands.argument(TARGETS_ARGUMENT, playerSelector())
                    .executes(context -> joinGame(context, selectedPlayersOrSelf(context, true)))
                )
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> leaveCommand() {
        return Commands.literal("leave")
            .requires(source -> source.getSender().hasPermission(LEAVE_PERMISSION))
            .executes(context -> leaveGames(context.getSource().getSender(), selectedPlayersOrSelf(context, false)))
            .then(Commands.argument(TARGETS_ARGUMENT, playerSelector())
                .executes(context -> leaveGames(context.getSource().getSender(), selectedPlayersOrSelf(context, true)))
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> unloadCommand() {
        return Commands.literal("unload")
            .requires(source -> source.getSender().hasPermission(UNLOAD_PERMISSION))
            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                .suggests((context, builder) -> {
                    for (int index : moduleService.activeGames().keySet()) {
                        builder.suggest(index);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> unloadModule(context.getSource().getSender(), IntegerArgumentType.getInteger(context, "index")))
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> lockCommand() {
        return Commands.literal("lock")
            .requires(source -> source.getSender().hasPermission(LOCK_PERMISSION))
            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                .suggests((context, builder) -> {
                    for (int index : moduleService.activeGames().keySet()) {
                        builder.suggest(index);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> lockGame(context.getSource().getSender(), IntegerArgumentType.getInteger(context, "index")))
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> unlockCommand() {
        return Commands.literal("unlock")
            .requires(source -> source.getSender().hasPermission(UNLOCK_PERMISSION))
            .executes(context -> unlockGame(context.getSource().getSender()));
    }

    private int loadModule(CommandContext<CommandSourceStack> context, List<Player> players, String configOverrideText, boolean lockAfterLoad) {
        CommandSender sender = context.getSource().getSender();
        String gameId = StringArgumentType.getString(context, GAME_ID_ARGUMENT);
        GameModuleDescriptor descriptor = fileService.gameModules().get(gameId);
        if (descriptor == null) {
            sendError(sender, "Unknown module ID: " + gameId);
            return Command.SINGLE_SUCCESS;
        }

        try {
            moduleService.loadModule(descriptor, mergedConfig(descriptor, configOverrideText), players)
                .thenCompose(module -> {
                    if (!lockAfterLoad) {
                        return CompletableFuture.completedFuture(new LoadResult(module, null));
                    }
                    return moduleService.lockGame(module.index()).thenApply(joined -> new LoadResult(module, joined));
                })
                .thenAccept(result -> {
                    GameModule module = result.module();
                    Integer lockedPlayers = result.lockedPlayers();
                    String suffix = players.isEmpty() ? "." : " with " + module.playerManager().getOnlinePlayers().size() + " player(s).";
                    if (lockedPlayers != null) {
                        suffix = " and locked the server to it with " + lockedPlayers + " online player(s).";
                    }
                    sendSuccess(sender, "Loaded " + descriptor.id() + " as active game " + module.index() + suffix);
                })
                .exceptionally(error -> {
                    sendError(sender, "Failed to load game module. See console for details.");
                    error.printStackTrace();
                    return null;
                });
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sendError(sender, exception.getMessage());
        } catch (Exception exception) {
            sendError(sender, "Failed to load game module.");
            exception.printStackTrace();
        }

        return Command.SINGLE_SUCCESS;
    }

    private int joinGame(CommandContext<CommandSourceStack> context, List<Player> players) {
        CommandSender sender = context.getSource().getSender();
        GameModule game = moduleService.activeGame(IntegerArgumentType.getInteger(context, "index"));
        if (game == null) {
            sendError(sender, "No active game exists at that index.");
            return Command.SINGLE_SUCCESS;
        }
        game.playerManager().join(players)
            .thenAccept(joined -> sendSuccess(sender, "Joined " + joined + " player(s) to game " + game.index() + "."))
            .exceptionally(error -> {
                sendError(sender, "Failed to join player(s) to game " + game.index() + ". See console for details.");
                error.printStackTrace();
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }

    private int leaveGames(CommandSender sender, List<Player> players) {
        moduleService.leaveGames(players)
            .thenAccept(removed -> sendSuccess(sender, "Removed " + removed + " player(s) from active games."))
            .exceptionally(error -> {
                sendError(sender, "Failed to remove player(s) from active games. See console for details.");
                error.printStackTrace();
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }

    private int unloadModule(CommandSender sender, int index) {
        moduleService.unloadModule(index)
            .thenAccept(unloaded -> {
                if (!unloaded.unloaded()) {
                    sendError(sender, "No active game exists at index " + index + ".");
                } else if (unloaded.hadErrors()) {
                    sendError(sender, "Unloaded active game " + index + " with errors. Check console for details.");
                } else {
                    sendSuccess(sender, "Unloaded active game " + index + ".");
                }
            })
            .exceptionally(error -> {
                sendError(sender, "Failed to unload active game " + index + ". See console for details.");
                error.printStackTrace();
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }

    private int lockGame(CommandSender sender, int index) {
        moduleService.lockGame(index)
            .thenAccept(joined -> sendSuccess(sender, "Locked the server to active game " + index + " and joined " + joined + " online player(s)."))
            .exceptionally(error -> {
                sendError(sender, error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }

    private int unlockGame(CommandSender sender) {
        Integer lockedIndex = moduleService.lockedGameIndex();
        if (lockedIndex == null) {
            sendError(sender, "The server is not locked to a game.");
            return Command.SINGLE_SUCCESS;
        }
        moduleService.unlockGame();
        sendSuccess(sender, "Unlocked the server from active game " + lockedIndex + ".");
        return Command.SINGLE_SUCCESS;
    }

    private YamlConfiguration mergedConfig(GameModuleDescriptor descriptor, String configOverrideText) {
        YamlConfiguration config = descriptor.createConfig();
        if (configOverrideText == null || configOverrideText.isBlank()) {
            return config;
        }
        ConfigurationSection overrides = parseConfigOverrides(configOverrideText);
        mergeInto(config, overrides.getValues(false));
        return config;
    }

    private ConfigurationSection parseConfigOverrides(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            throw new IllegalArgumentException("Config overrides cannot be blank.");
        }
        if (!rawConfig.startsWith("{") || !rawConfig.endsWith("}")) {
            throw new IllegalArgumentException("Config overrides must use the {key:value} format.");
        }

        Map<String, Object> values = InlineConfigArgument.parseOverrides(rawConfig);

        YamlConfiguration parsed = new YamlConfiguration();
        ConfigurationSection section = parsed.createSection("root");
        populateSection(section, values);
        return section;
    }

    private void populateSection(ConfigurationSection section, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                ConfigurationSection nested = section.createSection(entry.getKey());
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) nestedMap;
                populateSection(nested, castMap);
                continue;
            }
            if (value instanceof List<?> list) {
                section.set(entry.getKey(), copyList(list));
                continue;
            }
            section.set(entry.getKey(), value);
        }
    }

    private List<Object> copyList(List<?> values) {
        List<Object> copy = new ArrayList<>(values.size());
        for (Object value : values) {
            copy.add(copyValue(value));
        }
        return copy;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            return copyList(list);
        }
        return value;
    }

    private void mergeInto(ConfigurationSection target, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = normalizeYamlValue(entry.getValue());
            if (value instanceof Map<?, ?> nestedMap) {
                ConfigurationSection child = target.getConfigurationSection(key);
                if (child == null) {
                    child = target.createSection(key);
                }
                mergeInto(child, nestedMap);
                continue;
            }
            target.set(key, value);
        }
    }

    private Object normalizeYamlValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeYamlValue).toList();
        }
        return value;
    }

    private String configOverride(CommandContext<CommandSourceStack> context) {
        return context.getArgument(CONFIG_ARGUMENT, String.class);
    }

    private List<Player> selectedPlayersOrSelf(CommandContext<CommandSourceStack> context, boolean explicitSelector) {
        if (explicitSelector) {
            try {
                PlayerSelectorArgumentResolver resolver = context.getArgument(TARGETS_ARGUMENT, PlayerSelectorArgumentResolver.class);
                return resolver.resolve(context.getSource());
            } catch (CommandSyntaxException exception) {
                throw new IllegalStateException("Invalid target selector.", exception);
            }
        }
        if (context.getSource().getExecutor() instanceof Player player) {
            return List.of(player);
        }
        return List.of();
    }

    private ArgumentType<PlayerSelectorArgumentResolver> playerSelector() {
        return ArgumentTypes.players();
    }

    private CompletableFuture<Suggestions> suggestInlineConfig(CommandContext<?> context, SuggestionsBuilder builder) {
        List<String> keys = suggestableConfigKeys(context);
        return InlineConfigArgument.suggestKeys(builder, keys);
    }

    private List<String> suggestableConfigKeys(CommandContext<?> context) {
        String gameId;
        try {
            gameId = context.getArgument(GAME_ID_ARGUMENT, String.class);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
        GameModuleDescriptor descriptor = fileService.gameModules().get(gameId);
        if (descriptor == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(descriptor.createConfig().getKeys(false).stream()
            .filter(key -> !RESERVED_CONFIG_KEYS.contains(key))
            .sorted()
            .toList());
        if (!keys.contains("map")) {
            keys.add("map");
        }
        return keys.stream().sorted().toList();
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    @Override
    public String description() {
        return "Loads and unloads minigame modules";
    }

    private record LoadResult(GameModule module, Integer lockedPlayers) {
    }

}
