package com.donutsforlife11.donutgame.commands;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.game.ModuleManager;
import com.donutsforlife11.donutgame.util.PluginCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;

public final class MinigameCommand implements PluginCommand {
    private final ModuleManager moduleManager;
    private final Map<String, GameModuleDescriptor> gameModules;

    public MinigameCommand(Donutgame plugin, ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.gameModules = plugin.getGameModules();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("minigame")
            .then(createLoadNode())
            .then(createJoinNode())
            .then(createUnloadNode())
            .build();
    }

    @Override
    public String description() {
        return "Start, stop, or manage minigames in general";
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createLoadNode() {
        return Commands.literal("load")
            .then(Commands.argument("game_id", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String gameId : gameModules.keySet()) {
                        builder.suggest(gameId);
                    }
                    return builder.buildFuture();
                })

                // /minigame load <game_id>
                .executes(ctx -> loadGame(ctx, null, List.of()))

                // /minigame load <game_id> <players>
                .then(Commands.argument("players", ArgumentTypes.players())
                    .executes(ctx -> loadGame(
                        ctx,
                        null,
                        resolvePlayers(ctx, "players")
                    ))
                )

                // /minigame load <game_id> {config}
                .then(Commands.argument("config", new InlineConfigArgument())
                    .executes(ctx -> loadGameWithRawConfig(
                        ctx,
                        ctx.getArgument("config", String.class),
                        List.of()
                    ))

                    // /minigame load <game_id> {config} <players>
                    .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> loadGameWithRawConfig(
                            ctx,
                            ctx.getArgument("config", String.class),
                            resolvePlayers(ctx, "players")
                        ))
                    )
                )
            );
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createJoinNode() {
        return Commands.literal("join")
            .then(Commands.argument("game_index", IntegerArgumentType.integer(0))
                .suggests((ctx, builder) -> {
                    for (int gameIndex : moduleManager.getActiveGames().keySet()) {
                        builder.suggest(gameIndex);
                    }
                    return builder.buildFuture();
                })

                // /minigame join <game_index>
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Console must specify players when joining a game.")
                        );
                        return Command.SINGLE_SUCCESS;
                    }

                    return joinGame(ctx, List.of(player));
                })

                // /minigame join <game_index> <players>
                .then(Commands.argument("players", ArgumentTypes.players())
                    .executes(ctx -> joinGame(ctx, resolvePlayers(ctx, "players")))
                )
            );
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createUnloadNode() {
        return Commands.literal("unload")
            .then(Commands.argument("game_index", IntegerArgumentType.integer(0))
                .suggests((ctx, builder) -> {
                    for (int gameIndex : moduleManager.getActiveGames().keySet()) {
                        builder.suggest(gameIndex);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    int gameIndex = IntegerArgumentType.getInteger(ctx, "game_index");
                    CommandSender sender = ctx.getSource().getSender();

                    if (!moduleManager.hasActiveGame(gameIndex)) {
                        sender.sendMessage(Component.text("No active game running with index " + gameIndex));
                        return Command.SINGLE_SUCCESS;
                    }

                    moduleManager.unloadModule(gameIndex);
                    sender.sendMessage(Component.text("Unloaded game " + gameIndex + "."));
                    return Command.SINGLE_SUCCESS;
                })
            );
    }

    private int loadGameWithRawConfig(
        CommandContext<CommandSourceStack> ctx,
        String rawConfig,
        List<Player> initialPlayers
    ) {
        CommandSender sender = ctx.getSource().getSender();

        try {
            return loadGame(ctx, parseConfigOverrides(rawConfig), initialPlayers);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage()));
            return Command.SINGLE_SUCCESS;
        }
    }

    private int loadGame(
        CommandContext<CommandSourceStack> ctx,
        ConfigurationSection configOverrides,
        List<Player> initialPlayers
    ) {
        CommandSender sender = ctx.getSource().getSender();
        String gameId = StringArgumentType.getString(ctx, "game_id");
        GameModuleDescriptor descriptor = gameModules.get(gameId);

        if (descriptor == null) {
            sender.sendMessage(Component.text("Unknown game id: " + gameId));
            return Command.SINGLE_SUCCESS;
        }

        try {
            ModuleManager.LoadResult result = moduleManager.loadModule(descriptor, configOverrides, initialPlayers);
            sender.sendMessage(Component.text("Loaded " + descriptor.id() + " as game " + result.gameIndex() + "."));
            sendRegistrationSummary(sender, result.gameIndex(), result.registrationSummary());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage()));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to load game " + gameId + ". See console for details."));
            e.printStackTrace();
        }

        return Command.SINGLE_SUCCESS;
    }

    private int joinGame(CommandContext<CommandSourceStack> ctx, List<Player> players) {
        CommandSender sender = ctx.getSource().getSender();
        int gameIndex = IntegerArgumentType.getInteger(ctx, "game_index");

        if (!moduleManager.hasActiveGame(gameIndex)) {
            sender.sendMessage(Component.text("No active game running with index " + gameIndex));
            return Command.SINGLE_SUCCESS;
        }

        ModuleManager.RegistrationSummary summary = moduleManager.registerPlayers(gameIndex, players);
        sendRegistrationSummary(sender, gameIndex, summary);
        return Command.SINGLE_SUCCESS;
    }

    private ConfigurationSection parseConfigOverrides(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            throw new IllegalArgumentException("Config overrides cannot be blank.");
        }

        if (!rawConfig.startsWith("{") || !rawConfig.endsWith("}")) {
            throw new IllegalArgumentException("Config overrides must use the {key:value} format.");
        }

        YamlConfiguration parsed = YamlConfiguration.loadConfiguration(
            new java.io.StringReader("root: " + rawConfig)
        );

        ConfigurationSection configSection = parsed.getConfigurationSection("root");

        if (configSection == null) {
            if ("{}".equals(rawConfig)) {
                return parsed.createSection("root");
            }

            throw new IllegalArgumentException("Invalid config overrides: " + rawConfig);
        }

        if (configSection.contains("id") || configSection.contains("main_class")) {
            throw new IllegalArgumentException("The id and main_class config values cannot be overridden.");
        }

        return configSection;
    }

    private List<Player> resolvePlayers(
        CommandContext<CommandSourceStack> ctx,
        String argumentName
    ) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(argumentName, PlayerSelectorArgumentResolver.class);
        return resolver.resolve(ctx.getSource());
    }

    private void sendRegistrationSummary(
        CommandSender sender,
        int gameIndex,
        ModuleManager.RegistrationSummary summary
    ) {
        if (summary.attempted() == 0) {
            return;
        }

        sender.sendMessage(Component.text(
            "Game " + gameIndex + ": added " + summary.added()
                + ", already in game " + summary.alreadyInGame()
                + ", already in another game " + summary.inOtherGames() + "."
        ));
    }

    /**
     * Parses an unquoted, balanced inline YAML-style config object:
     *
     *   {team_size:2,pvp_enablement_time:20}
     *   {}
     *   {message:"hello world",nested:{enabled:true}}
     *
     * This intentionally does NOT use StringArgumentType.word(), because word()
     * rejects braces before the command reaches our executor.
     */
    private static final class InlineConfigArgument implements CustomArgumentType<String, String> {
        private static final SimpleCommandExceptionType ERROR_EXPECTED_CONFIG =
            new SimpleCommandExceptionType(new LiteralMessage("Expected config object like {...}"));

        private static final SimpleCommandExceptionType ERROR_UNCLOSED_CONFIG =
            new SimpleCommandExceptionType(new LiteralMessage("Unclosed config overrides. Missing closing }."));

        @Override
        public String parse(com.mojang.brigadier.StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();

            if (!reader.canRead() || reader.peek() != '{') {
                throw ERROR_EXPECTED_CONFIG.createWithContext(reader);
            }

            int depth = 0;
            boolean inSingleQuotedString = false;
            boolean inDoubleQuotedString = false;
            boolean escaping = false;

            while (reader.canRead()) {
                char c = reader.read();

                if (escaping) {
                    escaping = false;
                    continue;
                }

                if ((inSingleQuotedString || inDoubleQuotedString) && c == '\\') {
                    escaping = true;
                    continue;
                }

                if (!inDoubleQuotedString && c == '\'') {
                    inSingleQuotedString = !inSingleQuotedString;
                    continue;
                }

                if (!inSingleQuotedString && c == '"') {
                    inDoubleQuotedString = !inDoubleQuotedString;
                    continue;
                }

                if (inSingleQuotedString || inDoubleQuotedString) {
                    continue;
                }

                if (c == '{') {
                    depth++;
                    continue;
                }

                if (c == '}') {
                    depth--;

                    if (depth == 0) {
                        return reader.getString().substring(start, reader.getCursor());
                    }

                    if (depth < 0) {
                        throw ERROR_EXPECTED_CONFIG.createWithContext(reader);
                    }
                }
            }

            throw ERROR_UNCLOSED_CONFIG.createWithContext(reader);
        }

        @Override
        public ArgumentType<String> getNativeType() {
            /*
             * Paper custom arguments need an underlying native argument type for
             * client-side validation. greedyString accepts braces client-side;
             * our parse(...) method above stops at the matching closing brace so
             * child arguments such as <players> can still parse after it.
             */
            return StringArgumentType.greedyString();
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder
        ) {
            return builder.buildFuture();
        }
    }
}