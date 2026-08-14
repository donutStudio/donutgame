package com.donutsforlife11.donutgame.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.kyori.adventure.text.format.NamedTextColor;

public class MinigameCommand implements PluginCommand {
    private static final String GAME_ID_ARGUMENT = "game_id";
    private static final String CONFIG_ARGUMENT = "config";
    private static final String TARGETS_ARGUMENT = "targets";
    private static final Set<String> RESERVED_CONFIG_KEYS = Set.of("id", "name", "main_class");

    private final ModuleService moduleService;
    private final FileService fileService;

    public MinigameCommand(FileService fileService, ModuleService moduleService) {
        this.fileService = fileService;
        this.moduleService = moduleService;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("minigame")
            .requires(source -> source.getSender().hasPermission(PluginCommand.PERMISSION))
            .then(loadCommand())
            .then(joinCommand())
            .then(leaveCommand())
            .then(unloadCommand())
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> loadCommand() {
        return Commands.literal("load")
            .then(Commands.argument(GAME_ID_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> {
                    fileService.gameModules().keySet().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> loadModule(context, selectedPlayersOrSelf(context, false), null))
                .then(Commands.argument(TARGETS_ARGUMENT, playerSelector())
                    .executes(context -> loadModule(context, selectedPlayersOrSelf(context, true), null))
                    .then(Commands.argument(CONFIG_ARGUMENT, new InlineConfigArgument(this::suggestInlineConfig))
                        .executes(context -> loadModule(context, selectedPlayersOrSelf(context, true), configOverride(context)))
                    )
                )
                .then(Commands.argument(CONFIG_ARGUMENT, new InlineConfigArgument(this::suggestInlineConfig))
                    .executes(context -> loadModule(context, selectedPlayersOrSelf(context, false), configOverride(context)))
                )
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> joinCommand() {
        return Commands.literal("join")
            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                .suggests((context, builder) -> {
                    for (int i : moduleService.activeGames().keySet()) {
                        builder.suggest(i);
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
            .executes(context -> leaveGames(context.getSource().getSender(), selectedPlayersOrSelf(context, false)))
            .then(Commands.argument(TARGETS_ARGUMENT, playerSelector())
                .executes(context -> leaveGames(context.getSource().getSender(), selectedPlayersOrSelf(context, true)))
            );
    }

    private LiteralArgumentBuilder<CommandSourceStack> unloadCommand() {
        return Commands.literal("unload")
            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                .suggests((context, builder) -> {
                    for (int i : moduleService.activeGames().keySet()) {
                        builder.suggest(i);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> unloadModule(context.getSource().getSender(), IntegerArgumentType.getInteger(context, "index")))
            );
    }

    private int loadModule(CommandContext<CommandSourceStack> context, List<Player> players, String configOverrideText) {
        CommandSender sender = context.getSource().getSender();
        String gameId = StringArgumentType.getString(context, GAME_ID_ARGUMENT);
        GameModuleDescriptor descriptor = fileService.gameModules().get(gameId);

        if (descriptor == null) {
            sendError(sender, "Unknown module ID: " + gameId);
            return Command.SINGLE_SUCCESS;
        }

        try {
            moduleService.loadModule(descriptor, mergedConfig(descriptor, configOverrideText))
                .thenAccept(module -> {
                    int registered = module.playerManager().register(players);
                    sendSuccess(
                        sender,
                        "Loaded " + descriptor.id() + " as active game " + module.index()
                            + (players.isEmpty() ? "." : " and queued " + registered + " player(s).")
                    );
                })
                .exceptionally(error -> {
                    sendError(sender, "Failed to load game module. See console for details.");
                    error.printStackTrace();
                    return null;
                });
        } catch (IllegalArgumentException exception) {
            sendError(sender, exception.getMessage());
        } catch (Exception exception) {
            sendError(sender, "Failed to load game module.");
            exception.printStackTrace();
        }

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

        InlineConfigParser parser = new InlineConfigParser(rawConfig);
        Map<String, Object> values = parser.parseObject();
        parser.ensureFullyConsumed();

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

    private int joinGame(CommandContext<CommandSourceStack> context, List<Player> players) {
        CommandSender sender = context.getSource().getSender();
        GameModule game = moduleService.activeGame(IntegerArgumentType.getInteger(context, "index"));
        if (game == null) {
            sendError(sender, "No active game exists at that index.");
            return Command.SINGLE_SUCCESS;
        }
        int registered = game.playerManager().register(players);
        sendSuccess(sender, "Registered " + registered + " player(s) into game " + game.index() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int leaveGames(CommandSender sender, List<Player> players) {
        int removed = moduleService.leaveGames(players);
        sendSuccess(sender, "Unregistered " + removed + " player(s).");
        return Command.SINGLE_SUCCESS;
    }

    private int unloadModule(CommandSender sender, int index) {
        moduleService.unloadModule(index).thenAccept(unloaded -> {
            if (unloaded) {
                sendSuccess(sender, "Unloaded active game " + index + ".");
                return;
            }
            sendError(sender, "No active game exists at index " + index + ".");
        });
        return Command.SINGLE_SUCCESS;
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private List<Player> selectedPlayersOrSelf(CommandContext<CommandSourceStack> context, boolean explicitSelector) {
        if (explicitSelector) {
            try {
                PlayerSelectorArgumentResolver resolver = context.getArgument(TARGETS_ARGUMENT, PlayerSelectorArgumentResolver.class);
                return resolver.resolve(context.getSource());
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("Invalid target selector.", e);
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

    private String configOverride(CommandContext<CommandSourceStack> context) {
        return context.getArgument(CONFIG_ARGUMENT, String.class);
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

    private CompletableFuture<Suggestions> suggestInlineConfig(CommandContext<?> context, SuggestionsBuilder builder) {
        List<String> keys = suggestableConfigKeys(context);
        String remaining = builder.getRemaining();

        if (remaining.isEmpty()) {
            builder.suggest("{");
            return builder.buildFuture();
        }
        if (!remaining.startsWith("{")) {
            if ("{".startsWith(remaining)) {
                builder.suggest("{");
            }
            return builder.buildFuture();
        }

        ConfigSuggestionState state = ConfigSuggestionState.parse(remaining, keys);
        if (state == null) {
            return builder.buildFuture();
        }
        if (state.expectingColon()) {
            builder.createOffset(builder.getStart() + remaining.length()).suggest(":");
            return builder.buildFuture();
        }
        if (!state.expectingKey()) {
            return builder.buildFuture();
        }

        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + state.replacementStart());
        for (String key : keys) {
            if (state.usedKeys().contains(key) || !key.startsWith(state.prefix())) {
                continue;
            }
            offsetBuilder.suggest(key);
        }
        return offsetBuilder.buildFuture();
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
        return descriptor.createConfig().getKeys(false).stream()
            .filter(key -> !RESERVED_CONFIG_KEYS.contains(key))
            .sorted()
            .toList();
    }

    @Override
    public String description() {
        return "Loads and unloads minigame modules";
    }

    private static final class InlineConfigArgument implements CustomArgumentType<String, String> {
        private static final SimpleCommandExceptionType ERROR_EXPECTED_CONFIG =
            new SimpleCommandExceptionType(new LiteralMessage("Expected config object like {...}"));
        private static final SimpleCommandExceptionType ERROR_UNCLOSED_CONFIG =
            new SimpleCommandExceptionType(new LiteralMessage("Unclosed config overrides. Missing closing }."));

        private final SuggestionProvider suggestionProvider;

        private InlineConfigArgument(SuggestionProvider suggestionProvider) {
            this.suggestionProvider = suggestionProvider;
        }

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
                char current = reader.read();

                if (escaping) {
                    escaping = false;
                    continue;
                }
                if ((inSingleQuotedString || inDoubleQuotedString) && current == '\\') {
                    escaping = true;
                    continue;
                }
                if (!inDoubleQuotedString && current == '\'') {
                    inSingleQuotedString = !inSingleQuotedString;
                    continue;
                }
                if (!inSingleQuotedString && current == '"') {
                    inDoubleQuotedString = !inDoubleQuotedString;
                    continue;
                }
                if (inSingleQuotedString || inDoubleQuotedString) {
                    continue;
                }
                if (current == '{') {
                    depth++;
                    continue;
                }
                if (current == '}') {
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
            return StringArgumentType.greedyString();
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            return suggestionProvider.suggest(context, builder);
        }
    }

    private interface SuggestionProvider {
        CompletableFuture<Suggestions> suggest(CommandContext<?> context, SuggestionsBuilder builder);
    }

    private record ConfigSuggestionState(Set<String> usedKeys, String prefix, int replacementStart, boolean expectingKey, boolean expectingColon) {
        private static ConfigSuggestionState parse(String input, List<String> allowedKeys) {
            if (input.isEmpty() || input.charAt(0) != '{') {
                return null;
            }
            Set<String> usedKeys = new LinkedHashSet<>();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            int braceDepth = 0;
            int bracketDepth = 0;
            int segmentStart = 1;

            for (int i = 1; i < input.length(); i++) {
                char current = input.charAt(i);
                if (current == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                    continue;
                }
                if (current == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                    continue;
                }
                if (inSingleQuote || inDoubleQuote) {
                    continue;
                }
                if (current == '{') {
                    braceDepth++;
                    continue;
                }
                if (current == '}') {
                    if (braceDepth > 0) {
                        braceDepth--;
                        continue;
                    }
                    if (bracketDepth == 0) {
                        String segment = input.substring(segmentStart, i);
                        collectUsedKey(usedKeys, segment);
                        return null;
                    }
                }
                if (current == '[') {
                    bracketDepth++;
                    continue;
                }
                if (current == ']') {
                    if (bracketDepth > 0) {
                        bracketDepth--;
                    }
                    continue;
                }
                if (current == ',' && braceDepth == 0 && bracketDepth == 0) {
                    collectUsedKey(usedKeys, input.substring(segmentStart, i));
                    segmentStart = i + 1;
                }
            }

            String segment = input.substring(segmentStart);
            int leadingWhitespace = 0;
            while (leadingWhitespace < segment.length() && Character.isWhitespace(segment.charAt(leadingWhitespace))) {
                leadingWhitespace++;
            }
            String trimmedSegment = segment.substring(leadingWhitespace);
            int replacementStart = segmentStart + leadingWhitespace;

            if (trimmedSegment.isEmpty()) {
                return new ConfigSuggestionState(usedKeys, "", replacementStart, true, false);
            }

            int colonIndex = trimmedSegment.indexOf(':');
            if (colonIndex >= 0) {
                return new ConfigSuggestionState(usedKeys, "", replacementStart, false, false);
            }

            String prefix = trimmedSegment.trim();
            if (allowedKeys.contains(prefix) && !usedKeys.contains(prefix)) {
                return new ConfigSuggestionState(usedKeys, prefix, replacementStart, false, true);
            }
            return new ConfigSuggestionState(usedKeys, prefix, replacementStart, true, false);
        }

        private static void collectUsedKey(Set<String> usedKeys, String segment) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex <= 0) {
                return;
            }
            usedKeys.add(trimmed.substring(0, colonIndex).trim());
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
                values.put(key, parseValue());

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
                char current = input.charAt(index++);
                if (escaping) {
                    builder.append(switch (current) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> current;
                    });
                    escaping = false;
                    continue;
                }
                if (current == '\\') {
                    escaping = true;
                    continue;
                }
                if (current == quote) {
                    return builder.toString();
                }
                builder.append(current);
            }

            throw error("Unclosed quoted string.");
        }

        private String parseBareToken() {
            int start = index;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isWhitespace(current) || current == ',' || current == ':' || current == '}' || current == ']') {
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
