package com.autostartstop.api;

import com.autostartstop.config.ControlApiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServerControlApiRegistry")
class ServerControlApiRegistryTest {

    private ServerControlApiRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServerControlApiRegistry();
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("returns null for null config")
        void returnsNullForNullConfig() {
            assertNull(registry.create(null, "myserver"));
        }

        @Test
        @DisplayName("returns null when config has no type")
        void returnsNullWhenNoType() {
            ControlApiConfig config = new ControlApiConfig();
            // type is null by default
            assertNull(registry.create(config, "myserver"));
        }

        @Test
        @DisplayName("returns null for unknown API type")
        void returnsNullForUnknownType() {
            ControlApiConfig config = new ControlApiConfig();
            config.setType("nonexistent_api");
            config.setRawConfig(Map.of());

            assertNull(registry.create(config, "myserver"));
        }

        @Test
        @DisplayName("creates shell API successfully")
        void createsShellApi() {
            ControlApiConfig config = new ControlApiConfig();
            config.setType("shell");
            config.setRawConfig(Map.of(
                    "start_command", "start.sh",
                    "stop_command", "stop.sh"
            ));

            ServerControlApi api = registry.create(config, "myserver");
            assertNotNull(api);
            assertEquals("shell", api.getType());
        }

        @Test
        @DisplayName("type lookup is case-insensitive")
        void typeLookupCaseInsensitive() {
            ControlApiConfig config = new ControlApiConfig();
            config.setType("SHELL");
            config.setRawConfig(Map.of(
                    "start_command", "start.sh",
                    "stop_command", "stop.sh"
            ));

            ServerControlApi api = registry.create(config, "myserver");
            assertNotNull(api);
        }
    }

    // ========== hasCreator() ==========

    @Nested
    @DisplayName("hasCreator()")
    class HasCreator {

        @Test
        @DisplayName("returns true for known types")
        void returnsTrueForKnown() {
            assertTrue(registry.hasCreator("shell"));
            assertTrue(registry.hasCreator("amp"));
            assertTrue(registry.hasCreator("pterodactyl"));
        }

        @Test
        @DisplayName("returns false for unknown type")
        void returnsFalseForUnknown() {
            assertFalse(registry.hasCreator("nonexistent"));
        }

        @Test
        @DisplayName("is case-insensitive")
        void caseInsensitive() {
            assertTrue(registry.hasCreator("SHELL"));
            assertTrue(registry.hasCreator("Shell"));
        }
    }

    // ========== autoRegisterCreators() ==========

    @Nested
    @DisplayName("autoRegisterCreators()")
    class AutoRegister {

        @Test
        @DisplayName("returns count matching all ServerControlApiType values with creators")
        void returnsCorrectCount() {
            int count = registry.autoRegisterCreators();
            long expected = java.util.Arrays.stream(ServerControlApiType.values())
                    .filter(ServerControlApiType::hasCreator).count();
            assertEquals(expected, count);
        }
    }
}
