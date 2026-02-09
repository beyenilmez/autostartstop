package com.autostartstop.condition;

import com.autostartstop.context.VariableResolver;
import com.autostartstop.config.SettingsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConditionRegistry")
@ExtendWith(MockitoExtension.class)
class ConditionRegistryTest {

    private ConditionRegistry registry;

    @Mock
    private VariableResolver variableResolver;

    @Mock
    private com.autostartstop.server.ServerManager serverManager;

    @BeforeEach
    void setUp() {
        registry = new ConditionRegistry();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should return null for null config map")
        void shouldReturnNullForNull() {
            assertNull(registry.create(null));
        }

        @Test
        @DisplayName("should return null for empty config map")
        void shouldReturnNullForEmpty() {
            assertNull(registry.create(Map.of()));
        }

        @Test
        @DisplayName("should return null when context is not set")
        void shouldReturnNullWithoutContext() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("string_equals", Map.of("value", "test", "equals", "test"));
            assertNull(registry.create(configMap));
        }

        @Test
        @DisplayName("should create string_equals condition")
        void shouldCreateStringEquals() {
            setupContext();
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("string_equals", Map.of("value", "hello", "equals", "hello"));
            
            Condition condition = registry.create(configMap);
            assertNotNull(condition);
            assertEquals(ConditionType.STRING_EQUALS, condition.getType());
        }

        @Test
        @DisplayName("should create number_compare condition")
        void shouldCreateNumberCompare() {
            setupContext();
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("number_compare", Map.of("value", "42", "min", "0", "max", "100"));
            
            Condition condition = registry.create(configMap);
            assertNotNull(condition);
            assertEquals(ConditionType.NUMBER_COMPARE, condition.getType());
        }

        @Test
        @DisplayName("should return null for unknown condition type")
        void shouldReturnNullForUnknown() {
            setupContext();
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("unknown_condition", Map.of("value", "test"));
            assertNull(registry.create(configMap));
        }

        @Test
        @DisplayName("should return null when condition creation throws ConfigException")
        void shouldReturnNullOnConfigError() {
            setupContext();
            Map<String, Object> configMap = new HashMap<>();
            // string_equals requires "value" and "equals" parameters
            configMap.put("string_equals", Map.of()); // Missing required params
            assertNull(registry.create(configMap));
        }

        @Test
        @DisplayName("should handle non-map params gracefully")
        void shouldHandleNonMapParams() {
            setupContext();
            Map<String, Object> configMap = new HashMap<>();
            // Pass null as params, which results in empty Map being used
            configMap.put("string_equals", null);
            // string_equals requires "value" param, so this should fail gracefully
            assertNull(registry.create(configMap));
        }
    }

    @Nested
    @DisplayName("Context management")
    class ContextTests {

        @Test
        @DisplayName("should set and get condition context")
        void shouldSetAndGetContext() {
            assertNull(registry.getConditionContext());
            
            ConditionContext ctx = ConditionContext.builder()
                .variableResolver(variableResolver)
                .build();
            registry.setConditionContext(ctx);
            
            assertNotNull(registry.getConditionContext());
            assertSame(ctx, registry.getConditionContext());
        }
    }

    @Nested
    @DisplayName("hasCreator()")
    class HasCreatorTests {

        @Test
        @DisplayName("should return false when context is not set")
        void shouldReturnFalseWithoutContext() {
            assertFalse(registry.hasCreator(ConditionType.STRING_EQUALS));
        }

        @Test
        @DisplayName("should return true when context is set and type has creator")
        void shouldReturnTrueWithContext() {
            setupContext();
            assertTrue(registry.hasCreator(ConditionType.STRING_EQUALS));
            assertTrue(registry.hasCreator(ConditionType.NUMBER_COMPARE));
        }
    }

    @Nested
    @DisplayName("autoRegisterCreators()")
    class AutoRegisterTests {

        @Test
        @DisplayName("should return count of available condition creators")
        void shouldReturnCreatorCount() {
            int count = registry.autoRegisterCreators();
            assertEquals(ConditionType.values().length, count);
        }
    }

    private void setupContext() {
        ConditionContext ctx = ConditionContext.builder()
            .variableResolver(variableResolver)
            .serverManager(serverManager)
            .settings(new SettingsConfig())
            .build();
        registry.setConditionContext(ctx);
    }
}
