package com.autostartstop.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DurationUtil")
class DurationUtilTest {

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @ParameterizedTest
        @CsvSource({
            "10t,  500",    // 10 ticks = 500ms
            "1t,   50",     // 1 tick = 50ms
            "500ms,500",
            "5s,   5000",
            "2m,   120000",
            "1h,   3600000",
            "500,  500",    // bare number defaults to ms
            "0s,   0",
            "0,    0"
        })
        @DisplayName("should parse valid duration strings")
        void shouldParse(String input, long expectedMs) {
            assertEquals(expectedMs, DurationUtil.parse(input).toMillis());
        }

        @Test
        @DisplayName("should be case insensitive for units")
        void shouldBeCaseInsensitive() {
            assertEquals(DurationUtil.parse("5s"), DurationUtil.parse("5S"));
            assertEquals(DurationUtil.parse("2m"), DurationUtil.parse("2M"));
            assertEquals(DurationUtil.parse("1h"), DurationUtil.parse("1H"));
            assertEquals(DurationUtil.parse("10t"), DurationUtil.parse("10T"));
            assertEquals(DurationUtil.parse("100ms"), DurationUtil.parse("100MS"));
        }

        @Test
        @DisplayName("should trim surrounding whitespace")
        void shouldTrimWhitespace() {
            assertEquals(5000, DurationUtil.parse("  5s  ").toMillis());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "abc", "5x", "-5s", "5.5s", "s", "5ss"})
        @DisplayName("should throw on null, blank, or invalid input")
        void shouldThrowOnInvalid(String input) {
            assertThrows(IllegalArgumentException.class, () -> DurationUtil.parse(input));
        }
    }

    @Nested
    @DisplayName("parseToTicks()")
    class ParseToTicksTests {

        @ParameterizedTest
        @CsvSource({
            "10t,  10",
            "1s,   20",
            "500ms,10",
            "1m,   1200",
            "30ms, 0"      // truncated
        })
        @DisplayName("should convert to ticks (1 tick = 50ms)")
        void shouldConvert(String input, long expectedTicks) {
            assertEquals(expectedTicks, DurationUtil.parseToTicks(input));
        }
    }

    @Nested
    @DisplayName("parseOrDefault()")
    class ParseOrDefaultTests {

        @Test
        @DisplayName("should parse valid input")
        void shouldParse() {
            assertEquals(5000, DurationUtil.parseOrDefault("5s", Duration.ofSeconds(10)).toMillis());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "invalid"})
        @DisplayName("should return default for null, blank, or invalid input")
        void shouldReturnDefault(String input) {
            Duration def = Duration.ofSeconds(10);
            assertEquals(def, DurationUtil.parseOrDefault(input, def));
        }
    }

    @Nested
    @DisplayName("format()")
    class FormatTests {

        @ParameterizedTest
        @CsvSource({
            "0,    0s",
            "1,    1s",
            "59,   59s",
            "60,   1m 0s",
            "61,   1m 1s",
            "3599, 59m 59s",
            "3600, 1h 0m",
            "3661, 1h 1m",
            "7200, 2h 0m"
        })
        @DisplayName("should format seconds / minutes / hours correctly")
        void shouldFormat(long seconds, String expected) {
            assertEquals(expected, DurationUtil.format(Duration.ofSeconds(seconds)));
        }
    }
}
