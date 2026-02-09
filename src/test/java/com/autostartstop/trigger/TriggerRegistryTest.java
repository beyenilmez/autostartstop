package com.autostartstop.trigger;

import com.autostartstop.config.TriggerConfig;
import com.autostartstop.server.ServerManager;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TriggerRegistry")
@ExtendWith(MockitoExtension.class)
class TriggerRegistryTest {

    private TriggerRegistry registry;

    @Mock ProxyServer proxy;
    @Mock ServerManager serverManager;

    @BeforeEach
    void setUp() {
        registry = new TriggerRegistry();
    }

    // ========== Context management ==========

    @Nested
    @DisplayName("Context management")
    class ContextManagement {

        @Test
        @DisplayName("context is null by default")
        void contextNullByDefault() {
            assertNull(registry.getTriggerContext());
        }

        @Test
        @DisplayName("set and get context")
        void setAndGetContext() {
            TriggerContext ctx = TriggerContext.builder().build();
            registry.setTriggerContext(ctx);
            assertSame(ctx, registry.getTriggerContext());
        }
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("returns null when context is not set")
        void returnsNullWithoutContext() {
            TriggerConfig config = new TriggerConfig();
            config.setType("manual");
            config.setRawConfig(Map.of("id", "test"));

            assertNull(registry.create(config));
        }

        @Test
        @DisplayName("returns null for unknown trigger type")
        void returnsNullForUnknownType() {
            setupContext();
            TriggerConfig config = new TriggerConfig();
            config.setType("nonexistent_trigger");
            config.setRawConfig(Map.of());

            assertNull(registry.create(config));
        }

        @Test
        @DisplayName("creates manual trigger successfully")
        void createsManualTrigger() {
            setupContext();
            TriggerConfig config = new TriggerConfig();
            config.setType("manual");
            config.setRawConfig(Map.of("id", "my-trigger"));

            Trigger trigger = registry.create(config);
            assertNotNull(trigger);
            assertEquals(TriggerType.MANUAL, trigger.getType());
        }

        @Test
        @DisplayName("creates proxy_start trigger successfully")
        void createsProxyStartTrigger() {
            setupContext();
            TriggerConfig config = new TriggerConfig();
            config.setType("proxy_start");
            config.setRawConfig(Map.of());

            Trigger trigger = registry.create(config);
            assertNotNull(trigger);
            assertEquals(TriggerType.PROXY_START, trigger.getType());
        }

        @Test
        @DisplayName("returns null when trigger creation throws ConfigException")
        void returnsNullOnConfigError() {
            setupContext();
            TriggerConfig config = new TriggerConfig();
            config.setType("manual");
            config.setRawConfig(Map.of()); // missing required "id"

            assertNull(registry.create(config));
        }
    }

    // ========== hasCreator() ==========

    @Nested
    @DisplayName("hasCreator()")
    class HasCreator {

        @Test
        @DisplayName("returns false when context is not set")
        void returnsFalseWithoutContext() {
            assertFalse(registry.hasCreator(TriggerType.MANUAL));
        }

        @Test
        @DisplayName("returns true when context is set and type has creator")
        void returnsTrueWithContext() {
            setupContext();
            for (TriggerType type : TriggerType.values()) {
                assertTrue(registry.hasCreator(type),
                        "hasCreator should be true for " + type.getConfigName());
            }
        }
    }

    // ========== autoRegisterCreators() ==========

    @Nested
    @DisplayName("autoRegisterCreators()")
    class AutoRegister {

        @Test
        @DisplayName("returns count matching all TriggerType values with creators")
        void returnsCorrectCount() {
            int count = registry.autoRegisterCreators();
            long expected = java.util.Arrays.stream(TriggerType.values())
                    .filter(TriggerType::hasCreator).count();
            assertEquals(expected, count);
        }
    }

    private void setupContext() {
        TriggerContext ctx = TriggerContext.builder()
                .proxy(proxy)
                .plugin(new Object())
                .serverManager(serverManager)
                .build();
        registry.setTriggerContext(ctx);
    }
}
