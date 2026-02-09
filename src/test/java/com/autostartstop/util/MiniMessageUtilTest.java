package com.autostartstop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MiniMessageUtil")
class MiniMessageUtilTest {

    @Nested
    @DisplayName("convertLegacyToMiniMessage()")
    class ConvertLegacyTests {

        @Test
        @DisplayName("should pass through null and empty unchanged")
        void shouldPassThroughNullEmpty() {
            assertNull(MiniMessageUtil.convertLegacyToMiniMessage(null));
            assertEquals("", MiniMessageUtil.convertLegacyToMiniMessage(""));
        }

        @Test
        @DisplayName("should convert section-sign color codes")
        void shouldConvertSectionSignCodes() {
            assertEquals("<green>Hello <red>World",
                MiniMessageUtil.convertLegacyToMiniMessage("§aHello §cWorld"));
        }

        @Test
        @DisplayName("should convert ampersand color codes")
        void shouldConvertAmpersandCodes() {
            assertEquals("<green>Hello <red>World",
                MiniMessageUtil.convertLegacyToMiniMessage("&aHello &cWorld"));
        }

        @Test
        @DisplayName("should convert formatting codes (bold, italic, reset)")
        void shouldConvertFormattingCodes() {
            assertEquals("<bold>Bold <italic>Italic <reset>Reset",
                MiniMessageUtil.convertLegacyToMiniMessage("§lBold §oItalic §rReset"));
        }

        @Test
        @DisplayName("should convert hex color codes")
        void shouldConvertHexColors() {
            assertEquals("<#FF5555>Hello", MiniMessageUtil.convertLegacyToMiniMessage("§#FF5555Hello"));
            assertEquals("<#00AAFF>World", MiniMessageUtil.convertLegacyToMiniMessage("&#00AAFFWorld"));
        }

        @ParameterizedTest
        @CsvSource({
            "§0, <black>",   "§1, <dark_blue>",  "§2, <dark_green>",
            "§3, <dark_aqua>", "§4, <dark_red>",  "§5, <dark_purple>",
            "§6, <gold>",    "§7, <gray>",        "§8, <dark_gray>",
            "§9, <blue>",    "§a, <green>",       "§b, <aqua>",
            "§c, <red>",     "§d, <light_purple>","§e, <yellow>",
            "§f, <white>",   "§k, <obfuscated>",  "§l, <bold>",
            "§m, <strikethrough>", "§n, <underlined>", "§o, <italic>",
            "§r, <reset>"
        })
        @DisplayName("should map every single-char legacy code correctly")
        void shouldMapAllLegacyCodes(String input, String expected) {
            assertEquals(expected, MiniMessageUtil.convertLegacyToMiniMessage(input));
        }

        @Test
        @DisplayName("should leave already-valid MiniMessage tags untouched")
        void shouldLeaveMiniMessageAlone() {
            String input = "<green>Hello <bold>World</bold></green>";
            assertEquals(input, MiniMessageUtil.convertLegacyToMiniMessage(input));
        }
    }

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @Test
        @DisplayName("should parse plain text")
        void shouldParsePlainText() {
            assertEquals("Hello World", plainOf(MiniMessageUtil.parse("Hello World")));
        }

        @Test
        @DisplayName("should parse MiniMessage and strip tags for plain text")
        void shouldParseMiniMessage() {
            assertEquals("Hello", plainOf(MiniMessageUtil.parse("<green>Hello</green>")));
        }

        @Test
        @DisplayName("should parse legacy codes and strip them for plain text")
        void shouldParseLegacy() {
            assertEquals("Hello", plainOf(MiniMessageUtil.parse("§aHello")));
        }

        @Test
        @DisplayName("should resolve single string placeholder")
        void shouldResolveSinglePlaceholder() {
            assertEquals("Hello Steve!", plainOf(MiniMessageUtil.parse("Hello <name>!", "name", "Steve")));
        }

        @Test
        @DisplayName("should resolve map of placeholders")
        void shouldResolveMapPlaceholders() {
            Component c = MiniMessageUtil.parse(
                "Server: <server> | Players: <count>",
                Map.of("server", "lobby", "count", "42")
            );
            assertEquals("Server: lobby | Players: 42", plainOf(c));
        }

        @Test
        @DisplayName("null values in map placeholders should become empty strings")
        void shouldHandleNullValuesInMap() {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("key", null);
            assertEquals("Value: ", plainOf(MiniMessageUtil.parse("Value: <key>", placeholders)));
        }

        @Test
        @DisplayName("should resolve component placeholder")
        void shouldResolveComponentPlaceholder() {
            Component inner = Component.text("World");
            assertEquals("Hello World!", plainOf(MiniMessageUtil.parse("Hello <name>!", "name", inner)));
        }
    }

    @Nested
    @DisplayName("stripTags()")
    class StripTagsTests {

        @Test
        @DisplayName("should strip MiniMessage tags to plain text")
        void shouldStripTags() {
            assertEquals("Hello World", MiniMessageUtil.stripTags("<green>Hello <bold>World</bold></green>"));
        }

        @Test
        @DisplayName("should strip converted legacy codes to plain text")
        void shouldStripLegacy() {
            assertEquals("Hello World", MiniMessageUtil.stripTags("§aHello §lWorld"));
        }
    }

    @Nested
    @DisplayName("escape() / serialize() / toLegacy()")
    class RoundTripTests {

        @Test
        @DisplayName("escape should make tags render as literal text")
        void escapeShouldMakeTagsLiteral() {
            String escaped = MiniMessageUtil.escape("<green>Hello</green>");
            Component parsed = MiniMessageUtil.parse(escaped);
            String plain = plainOf(parsed);
            assertTrue(plain.contains("green"), "Escaped tags should appear as literal text");
        }

        @Test
        @DisplayName("serialize should produce MiniMessage from a component")
        void serializeShouldProduce() {
            Component c = Component.text("Hello").color(NamedTextColor.GREEN);
            String mm = MiniMessageUtil.serialize(c);
            assertNotNull(mm);
            assertTrue(mm.contains("Hello"));
        }

        @Test
        @DisplayName("toLegacy(String) should produce section-sign codes")
        void toLegacyFromString() {
            String legacy = MiniMessageUtil.toLegacy("<green>Hi</green>");
            assertTrue(legacy.contains("§"));
            assertTrue(legacy.contains("Hi"));
        }

        @Test
        @DisplayName("toLegacy(Component) should produce section-sign codes")
        void toLegacyFromComponent() {
            Component c = Component.text("Hi").color(NamedTextColor.GREEN);
            String legacy = MiniMessageUtil.toLegacy(c);
            assertTrue(legacy.contains("§"));
            assertTrue(legacy.contains("Hi"));
        }
    }

    private static String plainOf(Component c) {
        return MiniMessageUtil.toPlainText(c);
    }
}
