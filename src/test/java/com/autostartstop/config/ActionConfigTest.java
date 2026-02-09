package com.autostartstop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActionConfig")
class ActionConfigTest {

    @Nested
    @DisplayName("ConfigAccessor delegation")
    class DelegationTests {

        @Test
        @DisplayName("should delegate typed getters to underlying raw config")
        void shouldDelegateTypedGetters() {
            ActionConfig config = new ActionConfig();
            config.setType("start");
            config.setRawConfig(Map.of(
                "server", "lobby",
                "count", 42,
                "progress", 0.5,
                "enabled", true,
                "timeout", "5s",
                "targets", List.of("lobby", "survival")
            ));

            assertEquals("lobby", config.getString("server"));
            assertEquals("default", config.getString("missing", "default"));
            assertEquals(42, config.getInt("count", 0));
            assertEquals(0.5, config.getDouble("progress", 0.0), 0.001);
            assertTrue(config.getBoolean("enabled", false));
            assertEquals(Duration.ofSeconds(5), config.getDuration("timeout", Duration.ZERO));
            assertEquals(List.of("lobby", "survival"), config.getStringList("targets"));
            assertTrue(config.hasKey("server"));
            assertFalse(config.hasKey("missing"));
        }

        @Test
        @DisplayName("requireString should throw ConfigException for missing key")
        void requireStringShouldThrowForMissing() {
            ActionConfig config = new ActionConfig();
            config.setType("start");
            config.setRawConfig(Map.of());
            assertThrows(ConfigException.class, () -> config.requireString("server"));
        }

        @Test
        @DisplayName("should handle null rawConfig gracefully")
        void shouldHandleNullRawConfig() {
            ActionConfig config = new ActionConfig();
            config.setType("start");
            // rawConfig is null
            assertNull(config.getString("server"));
            assertEquals(0, config.getInt("count", 0));
            assertFalse(config.hasKey("anything"));
        }
    }

    @Nested
    @DisplayName("Accessor cache invalidation")
    class AccessorResetTests {

        @Test
        @DisplayName("should reflect new rawConfig after it changes")
        void shouldReflectNewRawConfig() {
            ActionConfig config = new ActionConfig();
            config.setType("start");
            config.setRawConfig(Map.of("server", "lobby"));
            assertEquals("lobby", config.getString("server"));

            config.setRawConfig(Map.of("server", "survival"));
            assertEquals("survival", config.getString("server"));
        }
    }

    @Test
    @DisplayName("waitForCompletion should default to true")
    void waitForCompletionShouldDefaultTrue() {
        ActionConfig config = new ActionConfig();
        assertTrue(config.isWaitForCompletion());
    }
}
