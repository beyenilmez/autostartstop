package com.autostartstop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

/**
 * MiniMessage utility class following Adventure API standards.
 * 
 * <p>MiniMessage is a string-based format for representing Minecraft chat components.
 * This utility provides convenient methods for parsing and working with MiniMessage content.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Simple parsing
 * Component message = MiniMessageUtil.parse("<green>Hello <bold>world</bold>!</green>");
 * 
 * // With placeholders
 * Component greeting = MiniMessageUtil.parse(
 *     "<yellow>Welcome, <player>!</yellow>",
 *     Placeholder.unparsed("player", playerName)
 * );
 * 
 * // With multiple placeholders using a map
 * Component info = MiniMessageUtil.parse(
 *     "<gray>Server: <server> | Players: <count></gray>",
 *     Map.of("server", serverName, "count", String.valueOf(playerCount))
 * );
 * }</pre>
 * 
 * @see <a href="https://docs.papermc.io/adventure/minimessage/">MiniMessage Documentation</a>
 */
public final class MiniMessageUtil {
    
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    
    private MiniMessageUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Parses a MiniMessage formatted string into a Component.
     *
     * @param message the MiniMessage formatted string
     * @return the parsed Component, or empty component if message is null/empty
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message);
    }
    
    /**
     * Parses a MiniMessage formatted string with tag resolvers for dynamic content.
     * 
     * <p>Example:
     * <pre>{@code
     * Component msg = MiniMessageUtil.parse(
     *     "<green>Hello, <name>!</green>",
     *     Placeholder.unparsed("name", "Steve")
     * );
     * }</pre>
     *
     * @param message the MiniMessage formatted string
     * @param resolvers the tag resolvers for placeholder replacement
     * @return the parsed Component
     */
    public static Component parse(String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message, resolvers);
    }
    
    /**
     * Parses a MiniMessage formatted string with string placeholders.
     * 
     * <p>Placeholders are automatically converted to unparsed (literal) values,
     * meaning any MiniMessage tags in placeholder values will not be processed.
     * 
     * <p>Example:
     * <pre>{@code
     * Component msg = MiniMessageUtil.parse(
     *     "<red>Error: <error></red>",
     *     Map.of("error", errorMessage)
     * );
     * }</pre>
     *
     * @param message the MiniMessage formatted string
     * @param placeholders map of placeholder names to their values
     * @return the parsed Component
     */
    public static Component parse(String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return MINI_MESSAGE.deserialize(message);
        }
        
        TagResolver.Builder builder = TagResolver.builder();
        placeholders.forEach((key, value) -> 
            builder.resolver(Placeholder.unparsed(key, value != null ? value : ""))
        );
        
        return MINI_MESSAGE.deserialize(message, builder.build());
    }
    
    /**
     * Parses a MiniMessage formatted string with a single placeholder.
     * 
     * <p>This is a convenience method for single placeholder scenarios.
     *
     * @param message the MiniMessage formatted string
     * @param key the placeholder key (without angle brackets)
     * @param value the replacement value
     * @return the parsed Component
     */
    public static Component parse(String message, String key, String value) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message, Placeholder.unparsed(key, value != null ? value : ""));
    }
    
    /**
     * Parses a MiniMessage formatted string with a Component placeholder.
     * 
     * <p>Use this when you want to insert a pre-built Component into your message.
     *
     * @param message the MiniMessage formatted string
     * @param key the placeholder key (without angle brackets)
     * @param component the Component to insert
     * @return the parsed Component
     */
    public static Component parse(String message, String key, Component component) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message, Placeholder.component(key, component));
    }
    
    /**
     * Strips all MiniMessage formatting and returns plain text.
     *
     * @param message the MiniMessage formatted string
     * @return the plain text without any formatting
     */
    public static String stripTags(String message) {
        if (message == null) {
            return null;
        }
        return MINI_MESSAGE.stripTags(message);
    }
    
    /**
     * Converts a Component to plain text, removing all formatting.
     *
     * @param component the Component to convert
     * @return the plain text content
     */
    public static String toPlainText(Component component) {
        if (component == null) {
            return null;
        }
        return PLAIN_TEXT.serialize(component);
    }
    
    /**
     * Escapes MiniMessage tags in a string so they are displayed literally.
     * 
     * <p>Use this when you want to display user input that might contain
     * MiniMessage tags without processing them.
     *
     * @param message the string to escape
     * @return the escaped string
     */
    public static String escape(String message) {
        if (message == null) {
            return null;
        }
        return MINI_MESSAGE.escapeTags(message);
    }
    
    /**
     * Serializes a Component back to MiniMessage format.
     *
     * @param component the Component to serialize
     * @return the MiniMessage formatted string
     */
    public static String serialize(Component component) {
        if (component == null) {
            return null;
        }
        return MINI_MESSAGE.serialize(component);
    }
    
    /**
     * Creates a simple text Component without any formatting.
     *
     * @param text the plain text
     * @return a Component containing the text
     */
    public static Component text(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return Component.text(text);
    }
    
    /**
     * Returns the underlying MiniMessage instance for advanced usage.
     *
     * @return the MiniMessage instance
     */
    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }
    
    /**
     * Converts a MiniMessage formatted string to legacy format string.
     * This is useful for version names and other places that require legacy format.
     * 
     * <p>Example:
     * <pre>{@code
     * String legacy = MiniMessageUtil.toLegacy("<green>Sleeping</green>");
     * // Result: "§aSleeping"
     * }</pre>
     *
     * @param minimessage the MiniMessage formatted string
     * @return the legacy format string (with § color codes)
     */
    public static String toLegacy(String minimessage) {
        if (minimessage == null || minimessage.isEmpty()) {
            return "";
        }
        Component component = parse(minimessage);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }
    
    /**
     * Converts a Component to legacy format string.
     * 
     * @param component the Component to convert
     * @return the legacy format string (with § color codes)
     */
    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }
}
