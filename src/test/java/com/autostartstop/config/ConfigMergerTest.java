package com.autostartstop.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigMerger")
class ConfigMergerTest {

    private ConfigMerger merger;

    @BeforeEach
    void setUp() {
        merger = new ConfigMerger();
    }

    @Nested
    @DisplayName("mergeServerConfig()")
    class MergeServerConfigTests {

        @Test
        @DisplayName("should return specific when defaults is null")
        void shouldReturnSpecificWhenDefaultsNull() {
            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            specific.setVirtualHost("lobby.example.com");

            ServerConfig result = merger.mergeServerConfig(specific, null);
            assertEquals("lobby.example.com", result.getVirtualHost());
        }

        @Test
        @DisplayName("should return cloned defaults when specific is null")
        void shouldReturnDefaultsWhenSpecificNull() {
            ServerConfig defaults = new ServerConfig();
            defaults.setVirtualHost("default.example.com");

            ServerConfig result = merger.mergeServerConfig(null, defaults);
            assertEquals("default.example.com", result.getVirtualHost());
            assertNotSame(defaults, result); // Should be a clone
        }

        @Test
        @DisplayName("should merge with specific taking precedence")
        void shouldMergeWithSpecificPrecedence() {
            ServerConfig defaults = new ServerConfig();
            defaults.setVirtualHost("default.example.com");
            PingConfig defaultPing = new PingConfig();
            defaultPing.setTimeout("5s");
            defaultPing.setMethod("socket");
            defaults.setPing(defaultPing);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            specific.setVirtualHost("lobby.example.com");

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertEquals("lobby.example.com", result.getVirtualHost()); // Specific wins
            assertNotNull(result.getPing());
            assertEquals("5s", result.getPing().getTimeout()); // From defaults
            assertEquals("socket", result.getPing().getMethod()); // From defaults
        }

        @Test
        @DisplayName("should use defaults when specific fields are null")
        void shouldUseDefaultsForNullFields() {
            ServerConfig defaults = new ServerConfig();
            defaults.setVirtualHost("default.example.com");

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            // virtualHost is null in specific

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertEquals("default.example.com", result.getVirtualHost()); // Falls back to default
        }
    }

    @Nested
    @DisplayName("Ping config merging")
    class PingConfigMergeTests {

        @Test
        @DisplayName("should merge ping configs")
        void shouldMergePingConfigs() {
            ServerConfig defaults = new ServerConfig();
            PingConfig defaultPing = new PingConfig();
            defaultPing.setTimeout("5s");
            defaultPing.setMethod("socket");
            defaults.setPing(defaultPing);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            PingConfig specificPing = new PingConfig();
            specificPing.setTimeout("10s");
            // method is null in specific
            specific.setPing(specificPing);

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertEquals("10s", result.getPing().getTimeout()); // Specific wins
            assertEquals("socket", result.getPing().getMethod()); // Default fallback
        }

        @Test
        @DisplayName("should use default ping when specific has none")
        void shouldUseDefaultPing() {
            ServerConfig defaults = new ServerConfig();
            PingConfig defaultPing = new PingConfig();
            defaultPing.setTimeout("5s");
            defaults.setPing(defaultPing);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertNotNull(result.getPing());
            assertEquals("5s", result.getPing().getTimeout());
        }
    }

    @Nested
    @DisplayName("Control API config merging")
    class ControlApiMergeTests {

        @Test
        @DisplayName("should merge control API configs")
        void shouldMergeControlApiConfigs() {
            ServerConfig defaults = new ServerConfig();
            ControlApiConfig defaultApi = new ControlApiConfig();
            defaultApi.setType("shell");
            Map<String, Object> defaultRaw = new HashMap<>();
            defaultRaw.put("start_command", "start.sh");
            defaultRaw.put("stop_command", "stop.sh");
            defaultApi.setRawConfig(defaultRaw);
            defaults.setControlApi(defaultApi);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            ControlApiConfig specificApi = new ControlApiConfig();
            specificApi.setType("pterodactyl");
            Map<String, Object> specificRaw = new HashMap<>();
            specificRaw.put("api_key", "secret-key");
            specificApi.setRawConfig(specificRaw);
            specific.setControlApi(specificApi);

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertNotNull(result.getControlApi());
            assertEquals("pterodactyl", result.getControlApi().getType()); // Specific wins
            // Raw configs should be merged
            assertEquals("secret-key", result.getControlApi().getRawConfig().get("api_key"));
            assertEquals("start.sh", result.getControlApi().getRawConfig().get("start_command"));
        }

        @Test
        @DisplayName("should use default control API type when specific is null")
        void shouldUseDefaultApiType() {
            ServerConfig defaults = new ServerConfig();
            ControlApiConfig defaultApi = new ControlApiConfig();
            defaultApi.setType("shell");
            defaults.setControlApi(defaultApi);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            ControlApiConfig specificApi = new ControlApiConfig();
            // type is null in specific
            specific.setControlApi(specificApi);

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertEquals("shell", result.getControlApi().getType());
        }
    }

    @Nested
    @DisplayName("Startup timer config merging")
    class StartupTimerMergeTests {

        @Test
        @DisplayName("should merge startup timer configs")
        void shouldMergeStartupTimerConfigs() {
            ServerConfig defaults = new ServerConfig();
            StartupTimerConfig defaultTimer = new StartupTimerConfig();
            defaultTimer.setExpectedStartupTime("30s");
            defaultTimer.setAutoCalculateExpectedStartupTime(true);
            defaults.setStartupTimer(defaultTimer);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");
            StartupTimerConfig specificTimer = new StartupTimerConfig();
            specificTimer.setExpectedStartupTime("60s");
            specificTimer.setAutoCalculateExpectedStartupTime(false);
            specific.setStartupTimer(specificTimer);

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertEquals("60s", result.getStartupTimer().getExpectedStartupTime());
            assertFalse(result.getStartupTimer().isAutoCalculateExpectedStartupTime());
        }

        @Test
        @DisplayName("should use default startup timer when specific has none")
        void shouldUseDefaultStartupTimer() {
            ServerConfig defaults = new ServerConfig();
            StartupTimerConfig defaultTimer = new StartupTimerConfig();
            defaultTimer.setExpectedStartupTime("30s");
            defaults.setStartupTimer(defaultTimer);

            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertNotNull(result.getStartupTimer());
            assertEquals("30s", result.getStartupTimer().getExpectedStartupTime());
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle both null configs")
        void shouldHandleBothNull() {
            // specific is null, defaults is null
            ServerConfig result = merger.mergeServerConfig(null, null);
            assertNull(result);
        }

        @Test
        @DisplayName("should handle empty configs")
        void shouldHandleEmptyConfigs() {
            ServerConfig defaults = new ServerConfig();
            ServerConfig specific = new ServerConfig();
            specific.setName("lobby");

            ServerConfig result = merger.mergeServerConfig(specific, defaults);
            assertNotNull(result);
            assertNull(result.getVirtualHost());
            assertNull(result.getPing());
            assertNull(result.getControlApi());
            assertNull(result.getStartupTimer());
        }
    }
}
