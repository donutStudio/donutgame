package com.donutsforlife11.donutgame.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

final class InlineConfigArgument implements CustomArgumentType<String, String> {
    private static final SimpleCommandExceptionType ERROR_EXPECTED_CONFIG =
        new SimpleCommandExceptionType(new LiteralMessage("Expected config object like {...}"));
    private static final SimpleCommandExceptionType ERROR_UNCLOSED_CONFIG =
        new SimpleCommandExceptionType(new LiteralMessage("Unclosed config overrides. Missing closing }."));

    private final SuggestionProvider suggestionProvider;

    InlineConfigArgument(SuggestionProvider suggestionProvider) {
        this.suggestionProvider = suggestionProvider;
    }

    static Map<String, Object> parseOverrides(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            throw new IllegalArgumentException("Config overrides cannot be blank.");
        }
        if (!rawConfig.startsWith("{") || !rawConfig.endsWith("}")) {
            throw new IllegalArgumentException("Config overrides must use the {key:value} format.");
        }
        Parser parser = new Parser(rawConfig);
        Map<String, Object> values = parser.parseObject();
        parser.ensureFullyConsumed();
        return values;
    }

    static CompletableFuture<Suggestions> suggestKeys(SuggestionsBuilder builder, List<String> keys) {
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

    interface SuggestionProvider {
        CompletableFuture<Suggestions> suggest(CommandContext<?> context, SuggestionsBuilder builder);
    }

    private record ConfigSuggestionState(Set<String> usedKeys, String prefix, int replacementStart, boolean expectingKey, boolean expectingColon) {
        private static ConfigSuggestionState parse(String input, List<String> allowedKeys) {
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
                        collectUsedKey(usedKeys, input.substring(segmentStart, i));
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

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
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
