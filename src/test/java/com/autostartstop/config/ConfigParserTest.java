package com.autostartstop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigParser")
class ConfigParserTest {

    @Nested
    @DisplayName("parseAction()")
    class ParseActionTests {

        @Test
        @DisplayName("should return null for null map")
        void shouldReturnNullForNull() {
            assertNull(ConfigParser.parseAction(null));
        }

        @Test
        @DisplayName("should return null for empty map")
        void shouldReturnNullForEmpty() {
            assertNull(ConfigParser.parseAction(Map.of()));
        }

        @Test
        @DisplayName("should parse action type from first key")
        void shouldParseActionType() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("start", Map.of("server", "lobby"));
            ActionConfig config = ConfigParser.parseAction(map);
            assertNotNull(config);
            assertEquals("start", config.getType());
        }

        @Test
        @DisplayName("should parse raw config from map value")
        void shouldParseRawConfig() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("start", Map.of("server", "lobby", "timeout", "30s"));
            ActionConfig config = ConfigParser.parseAction(map);
            assertNotNull(config);
            assertNotNull(config.getRawConfig());
            assertEquals("lobby", config.getRawConfig().get("server"));
            assertEquals("30s", config.getRawConfig().get("timeout"));
        }

        @Test
        @DisplayName("should parse wait_for_completion")
        void shouldParseWaitForCompletion() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("start", Map.of("server", "lobby", "wait_for_completion", "false"));
            ActionConfig config = ConfigParser.parseAction(map);
            assertNotNull(config);
            assertFalse(config.isWaitForCompletion());
        }

        @Test
        @DisplayName("should default wait_for_completion to true")
        void shouldDefaultWaitForCompletion() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("start", Map.of("server", "lobby"));
            ActionConfig config = ConfigParser.parseAction(map);
            assertNotNull(config);
            assertTrue(config.isWaitForCompletion());
        }

        @Test
        @DisplayName("should only process first entry")
        void shouldOnlyProcessFirstEntry() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("start", Map.of("server", "lobby"));
            map.put("stop", Map.of("server", "survival"));
            ActionConfig config = ConfigParser.parseAction(map);
            assertNotNull(config);
            assertEquals("start", config.getType());
        }

        @Test
        @DisplayName("should handle non-map value")
        void shouldHandleNonMapValue() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("log", "Hello World");
            ActionConfig config = ConfigParser.parseAction(map);
            assertNotNull(config);
            assertEquals("log", config.getType());
            assertNull(config.getRawConfig()); // Non-map value doesn't set rawConfig
        }
    }

    @Nested
    @DisplayName("parseTrigger()")
    class ParseTriggerTests {

        @Test
        @DisplayName("should return null for null map")
        void shouldReturnNullForNull() {
            assertNull(ConfigParser.parseTrigger(null));
        }

        @Test
        @DisplayName("should return null for empty map")
        void shouldReturnNullForEmpty() {
            assertNull(ConfigParser.parseTrigger(Map.of()));
        }

        @Test
        @DisplayName("should parse trigger type from first key")
        void shouldParseTriggerType() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("connection", Map.of("server", "lobby"));
            TriggerConfig config = ConfigParser.parseTrigger(map);
            assertNotNull(config);
            assertEquals("connection", config.getType());
        }

        @Test
        @DisplayName("should parse raw config")
        void shouldParseRawConfig() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("connection", Map.of("server", "lobby", "deny_connection", true));
            TriggerConfig config = ConfigParser.parseTrigger(map);
            assertNotNull(config);
            assertNotNull(config.getRawConfig());
            assertEquals("lobby", config.getRawConfig().get("server"));
        }

        @Test
        @DisplayName("should handle non-map value")
        void shouldHandleNonMapValue() {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put("proxy_start", null);
            TriggerConfig config = ConfigParser.parseTrigger(map);
            assertNotNull(config);
            assertEquals("proxy_start", config.getType());
        }
    }

    @Nested
    @DisplayName("parseConditions()")
    class ParseConditionsTests {

        @Test
        @DisplayName("should return null for null section")
        void shouldReturnNullForNull() {
            assertNull(ConfigParser.parseConditions(null));
        }
    }

    @Nested
    @DisplayName("toStringKeyMap()")
    class ToStringKeyMapTests {

        @Test
        @DisplayName("should convert integer keys to strings")
        void shouldConvertIntKeys() {
            Map<Object, Object> map = new HashMap<>();
            map.put(1, "one");
            map.put(2, "two");
            Map<String, Object> result = ConfigParser.toStringKeyMap(map);
            assertEquals("one", result.get("1"));
            assertEquals("two", result.get("2"));
        }

        @Test
        @DisplayName("should keep string keys as-is")
        void shouldKeepStringKeys() {
            Map<Object, Object> map = new HashMap<>();
            map.put("key", "value");
            Map<String, Object> result = ConfigParser.toStringKeyMap(map);
            assertEquals("value", result.get("key"));
        }

        @Test
        @DisplayName("should preserve values")
        void shouldPreserveValues() {
            Map<Object, Object> map = new HashMap<>();
            map.put("num", 42);
            map.put("bool", true);
            map.put("list", java.util.List.of("a", "b"));
            Map<String, Object> result = ConfigParser.toStringKeyMap(map);
            assertEquals(42, result.get("num"));
            assertEquals(true, result.get("bool"));
            assertEquals(java.util.List.of("a", "b"), result.get("list"));
        }
    }
}
