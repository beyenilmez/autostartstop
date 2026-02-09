package com.autostartstop.template;

import com.autostartstop.action.ActionRegistry;
import com.autostartstop.config.SettingsConfig;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.rule.RuleExecutor;
import com.autostartstop.server.ServerManager;
import com.autostartstop.server.ServerStartupTracker;
import com.autostartstop.trigger.TriggerRegistry;
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

@DisplayName("TemplateRegistry")
@ExtendWith(MockitoExtension.class)
class TemplateRegistryTest {

    private TemplateRegistry registry;

    @Mock ProxyServer proxy;
    @Mock ServerManager serverManager;
    @Mock ServerStartupTracker startupTracker;
    @Mock TriggerRegistry triggerRegistry;
    @Mock ActionRegistry actionRegistry;
    @Mock RuleExecutor ruleExecutor;

    @BeforeEach
    void setUp() {
        registry = new TemplateRegistry();
    }

    // ========== Context management ==========

    @Nested
    @DisplayName("Context management")
    class ContextManagement {

        @Test
        @DisplayName("context is null by default")
        void contextNullByDefault() {
            assertNull(registry.getTemplateContext());
        }

        @Test
        @DisplayName("set and get context")
        void setAndGetContext() {
            TemplateContext ctx = TemplateContext.builder().build();
            registry.setTemplateContext(ctx);
            assertSame(ctx, registry.getTemplateContext());
        }
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("returns null when context is not set")
        void returnsNullWithoutContext() {
            TemplateConfig config = new TemplateConfig();
            config.setTemplate("stop_on_empty");
            config.setRawConfig(Map.of());

            assertNull(registry.create(config));
        }

        @Test
        @DisplayName("returns null for unknown template type")
        void returnsNullForUnknownType() {
            setupContext();
            TemplateConfig config = new TemplateConfig();
            config.setTemplate("nonexistent_template");
            config.setRawConfig(Map.of());

            assertNull(registry.create(config));
        }

        @Test
        @DisplayName("creates stop_on_proxy_shutdown template successfully")
        void createsStopOnProxyShutdownTemplate() {
            setupContext();
            TemplateConfig config = new TemplateConfig();
            config.setTemplate("stop_on_proxy_shutdown");
            config.setRawConfig(Map.of("servers", java.util.List.of("lobby")));

            Template template = registry.create(config);
            assertNotNull(template);
            assertEquals(TemplateType.STOP_ON_PROXY_SHUTDOWN, template.getType());
        }

        @Test
        @DisplayName("creates start_on_proxy_start template successfully")
        void createsStartOnProxyStartTemplate() {
            setupContext();
            TemplateConfig config = new TemplateConfig();
            config.setTemplate("start_on_proxy_start");
            config.setRawConfig(Map.of("servers", java.util.List.of("lobby")));

            Template template = registry.create(config);
            assertNotNull(template);
            assertEquals(TemplateType.START_ON_PROXY_START, template.getType());
        }

        @Test
        @DisplayName("returns null when template creation throws due to missing config")
        void returnsNullOnCreationError() {
            setupContext();
            TemplateConfig config = new TemplateConfig();
            config.setTemplate("stop_on_proxy_shutdown");
            config.setRawConfig(Map.of()); // missing required "servers"

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
            assertFalse(registry.hasCreator(TemplateType.STOP_ON_EMPTY));
        }

        @Test
        @DisplayName("returns true when context is set and type has creator")
        void returnsTrueWithContext() {
            setupContext();
            for (TemplateType type : TemplateType.values()) {
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
        @DisplayName("returns count matching all TemplateType values with creators")
        void returnsCorrectCount() {
            int count = registry.autoRegisterCreators();
            long expected = java.util.Arrays.stream(TemplateType.values())
                    .filter(TemplateType::hasCreator).count();
            assertEquals(expected, count);
        }
    }

    private void setupContext() {
        TemplateContext ctx = TemplateContext.builder()
                .proxy(proxy)
                .plugin(new Object())
                .serverManager(serverManager)
                .startupTracker(startupTracker)
                .triggerRegistry(triggerRegistry)
                .actionRegistry(actionRegistry)
                .ruleExecutor(ruleExecutor)
                .variableResolver(new VariableResolver())
                .settings(new SettingsConfig())
                .build();
        registry.setTemplateContext(ctx);
    }
}
