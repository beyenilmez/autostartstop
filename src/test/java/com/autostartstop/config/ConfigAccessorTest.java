package com.autostartstop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigAccessor")
class ConfigAccessorTest {

    @Nested
    @DisplayName("String methods")
    class StringTests {

        @Test
        @DisplayName("should coerce non-string values via toString")
        void shouldCoerceNonString() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("key", 42), "test");
            assertEquals("42", acc.getString("key"));
        }

        @Test
        @DisplayName("requireString should throw ConfigException for blank value")
        void requireStringShouldThrowForBlank() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("key", "   "), "test");
            assertThrows(ConfigException.class, () -> acc.requireString("key"));
        }

        @Test
        @DisplayName("getString with default should return default for blank value")
        void getStringDefaultForBlank() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("key", "   "), "test");
            assertEquals("fallback", acc.getString("key", "fallback"));
        }
    }

    @Nested
    @DisplayName("Numeric coercion")
    class NumericTests {

        @Test
        @DisplayName("getInt should parse string to int")
        void getIntFromString() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "42"), "t");
            assertEquals(42, acc.getInt("k", 0));
        }

        @Test
        @DisplayName("getInt should truncate Number subtypes")
        void getIntFromDouble() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", 42.9), "t");
            assertEquals(42, acc.getInt("k", 0));
        }

        @Test
        @DisplayName("getInt should return default for non-numeric string")
        void getIntDefaultForNonNumeric() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "abc"), "t");
            assertEquals(99, acc.getInt("k", 99));
        }

        @Test
        @DisplayName("getLong should parse string to long")
        void getLongFromString() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "999999999999"), "t");
            assertEquals(999999999999L, acc.getLong("k", 0L));
        }

        @Test
        @DisplayName("getDouble should parse string to double")
        void getDoubleFromString() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "3.14"), "t");
            assertEquals(3.14, acc.getDouble("k", 0.0), 0.001);
        }

        @Test
        @DisplayName("getFloatClamped should clamp to [min, max]")
        void floatClampedBounds() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("lo", -5.0, "hi", 5.0, "mid", 0.5), "t");
            assertEquals(0.0f, acc.getFloatClamped("lo", 0.5f, 0.0f, 1.0f), 0.001);
            assertEquals(1.0f, acc.getFloatClamped("hi", 0.5f, 0.0f, 1.0f), 0.001);
            assertEquals(0.5f, acc.getFloatClamped("mid", 0.0f, 0.0f, 1.0f), 0.001);
        }
    }

    @Nested
    @DisplayName("Boolean coercion")
    class BooleanTests {

        @Test
        @DisplayName("should accept 'yes' / '1' as true and 'no' / '0' as false")
        void shouldParseAlternativeBooleans() {
            ConfigAccessor acc = new ConfigAccessor(Map.of(
                "y", "yes", "one", "1", "n", "no", "zero", "0"
            ), "test");
            assertTrue(acc.getBoolean("y", false));
            assertTrue(acc.getBoolean("one", false));
            assertFalse(acc.getBoolean("n", true));
            assertFalse(acc.getBoolean("zero", true));
        }
        
        @Test
        @DisplayName("should return true for true")
        void shouldReturnTrueForTrue() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", true), "test");
            assertTrue(acc.getBoolean("k", false));
        }
        @Test
        @DisplayName("should return false for false")
        void shouldReturnFalseForFalse() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", false), "test");
            assertFalse(acc.getBoolean("k", true));
        }

        @Test
        @DisplayName("should return default for unrecognized string")
        void shouldReturnDefaultForUnrecognized() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "maybe"), "test");
            assertTrue(acc.getBoolean("k", true));
        }
    }

    @Nested
    @DisplayName("List handling")
    class ListTests {

        @Test
        @DisplayName("getStringList should wrap a single string in a list")
        void shouldWrapSingleString() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "single"), "t");
            assertEquals(List.of("single"), acc.getStringList("k"));
        }

        @Test
        @DisplayName("getStringList should skip null items")
        void shouldSkipNulls() {
            java.util.List<Object> withNull = new java.util.ArrayList<>();
            withNull.add("a");
            withNull.add(null);
            withNull.add("b");
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", withNull), "t");
            assertEquals(List.of("a", "b"), acc.getStringList("k"));
        }

        @Test
        @DisplayName("getMapList should convert nested maps to string-keyed maps")
        void shouldConvertMapList() {
            List<Map<String, Object>> maps = List.of(
                Map.of("type", "start"),
                Map.of("type", "stop")
            );
            ConfigAccessor acc = new ConfigAccessor(Map.of("actions", maps), "t");
            List<Map<String, Object>> result = acc.getMapList("actions");
            assertEquals(2, result.size());
            assertEquals("start", result.get(0).get("type"));
        }
    }

    @Nested
    @DisplayName("Duration parsing")
    class DurationTests {

        @Test
        @DisplayName("getDuration should parse valid duration strings")
        void shouldParseDuration() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "5s"), "t");
            assertEquals(Duration.ofSeconds(5), acc.getDuration("k", Duration.ZERO));

            acc = new ConfigAccessor(Map.of("k", "500ms"), "t");
            assertEquals(Duration.ofMillis(500), acc.getDuration("k", Duration.ZERO));

            acc = new ConfigAccessor(Map.of("k", "2m"), "t");
            assertEquals(Duration.ofMinutes(2), acc.getDuration("k", Duration.ZERO));

            acc = new ConfigAccessor(Map.of("k", "20t"), "t");
            assertEquals(Duration.ofSeconds(1), acc.getDuration("k", Duration.ZERO));
        }

        @Test
        @DisplayName("getDuration should return default for invalid strings")
        void shouldReturnDefaultForInvalid() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "nope"), "t");
            assertEquals(Duration.ofMinutes(1), acc.getDuration("k", Duration.ofMinutes(1)));
        }

        @Test
        @DisplayName("requireDuration should throw for missing or invalid values")
        void requireDurationShouldThrow() {
            ConfigAccessor missing = new ConfigAccessor(Map.of(), "t");
            assertThrows(ConfigException.class, () -> missing.requireDuration("k"));

            ConfigAccessor invalid = new ConfigAccessor(Map.of("k", "nope"), "t");
            assertThrows(ConfigException.class, () -> invalid.requireDuration("k"));
        }
    }

    @Nested
    @DisplayName("Enum parsing")
    class EnumTests {

        enum Color { RED, DARK_BLUE }

        @Test
        @DisplayName("should parse case-insensitively and convert hyphens to underscores")
        void shouldParseLeniently() {
            ConfigAccessor acc = new ConfigAccessor(Map.of(
                "lower", "red", "upper", "RED", "hyphen", "dark-blue"
            ), "t");
            assertEquals(Color.RED, acc.getEnum("lower", Color.class, null));
            assertEquals(Color.RED, acc.getEnum("upper", Color.class, null));
            assertEquals(Color.DARK_BLUE, acc.getEnum("hyphen", Color.class, null));
        }

        @Test
        @DisplayName("should return default for unknown enum value")
        void shouldReturnDefaultForUnknown() {
            ConfigAccessor acc = new ConfigAccessor(Map.of("k", "pink"), "t");
            assertEquals(Color.RED, acc.getEnum("k", Color.class, Color.RED));
        }
    }

    @Nested
    @DisplayName("Nested sections")
    class SectionTests {

        @Test
        @DisplayName("getSection should return an accessor with combined component name")
        void shouldReturnNestedAccessor() {
            Map<String, Object> nested = Map.of("inner", "value");
            ConfigAccessor acc = new ConfigAccessor(Map.of("child", nested), "parent");
            ConfigAccessor section = acc.getSection("child");
            assertEquals("value", section.getString("inner"));
            assertEquals("parent.child", section.getComponentName());
        }
    }

    @Test
    @DisplayName("null config should behave as empty map")
    void nullConfigShouldBeEmpty() {
        ConfigAccessor acc = new ConfigAccessor(null, "test");
        assertTrue(acc.isEmpty());
        assertNull(acc.getString("anything"));
    }
}
