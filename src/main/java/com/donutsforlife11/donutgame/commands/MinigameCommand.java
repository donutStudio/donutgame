package com.donutsforlife11.donutgame.commands;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.internal.module.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.module.ModuleManager;
import com.donutsforlife11.donutgame.internal.command.PluginCommand;
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
        this.gameModules = plugin.gameModules();
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
                    for (int gameIndex : moduleManager.activeGames().keySet()) {
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
                    for (int gameIndex : moduleManager.activeGames().keySet()) {
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

                    moduleManager.unloadModule(gameIndex).thenRun(() ->
                        sender.sendMessage(Component.text("Unloaded game " + gameIndex + "."))
                    ).exceptionally(error -> {
                        sender.sendMessage(Component.text("Failed to unload game " + gameIndex + ". See console for details."));
                        error.printStackTrace();
                        return null;
                    });
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
            moduleManager.loadModule(descriptor, configOverrides, initialPlayers).thenAccept(result -> {
                sender.sendMessage(Component.text("Loaded " + descriptor.id() + " as game " + result.gameIndex() + "."));
                sendRegistrationSummary(sender, result.gameIndex(), result.registrationSummary());
            }).exceptionally(error -> {
                sender.sendMessage(Component.text("Failed to load game " + gameId + ". See console for details."));
                error.printStackTrace();
                return null;
            });
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

        InlineConfigParser parser = new InlineConfigParser(rawConfig);
        Map<String, Object> values = parser.parseObject();
        parser.ensureFullyConsumed();

        if (values.containsKey("id") || values.containsKey("main_class")) {
            throw new IllegalArgumentException("The id and main_class config values cannot be overridden.");
        }

        YamlConfiguration parsed = new YamlConfiguration();
        ConfigurationSection configSection = parsed.createSection("root");
        populateSection(configSection, values);
        return configSection;
    }

    private void populateSection(ConfigurationSection section, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nestedMap) {
                ConfigurationSection nestedSection = section.createSection(entry.getKey());
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) nestedMap;
                populateSection(nestedSection, castMap);
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
            if (value instanceof Map<?, ?> nestedMap) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
                }
                copy.add(converted);
                continue;
            }

            copy.add(copyValue(value));
        }

        return copy;
    }

    private Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return copyList(list);
        }
        return value;
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

    private static final class InlineConfigParser {
        private final String input;
        private int index;

        private InlineConfigParser(String input) {
            this.input = input;
        }

        private Map<String, Object> parseObject() {
            skipWhitespace();
            expect('{');

            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();

            if (peek('}')) {
                index++;
                return values;
            }

            while (true) {
                String key = parseKey();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                values.put(key, value);

                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return values;
                }

                expect(',');
            }
        }

        private List<Object> parseList() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();

            if (peek(']')) {
                index++;
                return values;
            }

            while (true) {
                values.add(parseValue());
                skipWhitespace();

                if (peek(']')) {
                    index++;
                    return values;
                }

                expect(',');
            }
        }

        private Object parseValue() {
            skipWhitespace();

            if (peek('{')) {
                return parseObject();
            }
            if (peek('[')) {
                return parseList();
            }
            if (peek('"') || peek('\'')) {
                return parseQuotedString();
            }

            String token = parseBareToken();
            if (token.isEmpty()) {
                throw error("Expected a value.");
            }

            return coerceScalar(token);
        }

        private String parseKey() {
            skipWhitespace();
            if (peek('"') || peek('\'')) {
                return parseQuotedString();
            }

            String key = parseBareToken();
            if (key.isEmpty()) {
                throw error("Expected a config key.");
            }
            return key;
        }

        private String parseQuotedString() {
            char quote = input.charAt(index++);
            StringBuilder builder = new StringBuilder();
            boolean escaping = false;

            while (index < input.length()) {
                char c = input.charAt(index++);

                if (escaping) {
                    builder.append(switch (c) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> c;
                    });
                    escaping = false;
                    continue;
                }

                if (c == '\\') {
                    escaping = true;
                    continue;
                }

                if (c == quote) {
                    return builder.toString();
                }

                builder.append(c);
            }

            throw error("Unclosed quoted string.");
        }

        private String parseBareToken() {
            int start = index;

            while (index < input.length()) {
                char c = input.charAt(index);
                if (Character.isWhitespace(c) || c == ',' || c == ':' || c == '}' || c == ']') {
                    break;
                }
                index++;
            }

            return input.substring(start, index);
        }

        private Object coerceScalar(String token) {
            if ("true".equalsIgnoreCase(token)) {
                return true;
            }
            if ("false".equalsIgnoreCase(token)) {
                return false;
            }
            if ("null".equalsIgnoreCase(token)) {
                return null;
            }

            try {
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return Double.parseDouble(token);
                }
                return Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
                return token;
            }
        }

        private void ensureFullyConsumed() {
            skipWhitespace();
            if (index != input.length()) {
                throw error("Unexpected trailing config content.");
            }
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != expected) {
                throw error("Expected '" + expected + "'.");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < input.length() && input.charAt(index) == expected;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Near: " + input.substring(Math.min(index, input.length())));
        }
    }
}
