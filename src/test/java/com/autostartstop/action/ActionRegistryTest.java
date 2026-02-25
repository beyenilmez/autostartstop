package com.autostartstop.action;

import static org.junit.jupiter.api.Assertions.*;

import com.autostartstop.config.ActionConfig;
import com.autostartstop.config.SettingsConfig;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.ServerManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ActionRegistry")
@ExtendWith(MockitoExtension.class)
class ActionRegistryTest {

  private ActionRegistry registry;

  @Mock ServerManager serverManager;
  @Mock VariableResolver variableResolver;

  @BeforeEach
  void setUp() {
    registry = new ActionRegistry();
  }

  // ========== Context management ==========

  @Nested
  @DisplayName("Context management")
  class ContextManagement {

    @Test
    @DisplayName("context is null by default")
    void contextNullByDefault() {
      assertNull(registry.getActionContext());
    }

    @Test
    @DisplayName("set and get context")
    void setAndGetContext() {
      ActionContext ctx = ActionContext.builder().build();
      registry.setActionContext(ctx);
      assertSame(ctx, registry.getActionContext());
    }
  }

  // ========== create() ==========

  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("returns null when context is not set")
    void returnsNullWithoutContext() {
      ActionConfig config = new ActionConfig();
      config.setType("log");
      config.setRawConfig(Map.of("message", "hello"));

      assertNull(registry.create(config));
    }

    @Test
    @DisplayName("returns null for unknown action type")
    void returnsNullForUnknownType() {
      setupContext();
      ActionConfig config = new ActionConfig();
      config.setType("nonexistent_action");
      config.setRawConfig(Map.of());

      assertNull(registry.create(config));
    }

    @Test
    @DisplayName("creates log action successfully")
    void createsLogAction() {
      setupContext();
      ActionConfig config = new ActionConfig();
      config.setType("log");
      config.setRawConfig(Map.of("message", "test message"));

      Action action = registry.create(config);
      assertNotNull(action);
      assertEquals(ActionType.LOG, action.getType());
    }

    @Test
    @DisplayName("creates sleep action successfully")
    void createsSleepAction() {
      setupContext();
      ActionConfig config = new ActionConfig();
      config.setType("sleep");
      config.setRawConfig(Map.of("duration", "5s"));

      Action action = registry.create(config);
      assertNotNull(action);
      assertEquals(ActionType.SLEEP, action.getType());
    }

    @Test
    @DisplayName("returns null when action creation throws ConfigException")
    void returnsNullOnConfigError() {
      setupContext();
      ActionConfig config = new ActionConfig();
      config.setType("log");
      config.setRawConfig(Map.of()); // missing required "message"

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
      assertFalse(registry.hasCreator(ActionType.LOG));
    }

    @Test
    @DisplayName("returns true when context is set and type has creator")
    void returnsTrueWithContext() {
      setupContext();
      for (ActionType type : ActionType.values()) {
        assertTrue(
            registry.hasCreator(type), "hasCreator should be true for " + type.getConfigName());
      }
    }
  }

  // ========== autoRegisterCreators() ==========

  @Nested
  @DisplayName("autoRegisterCreators()")
  class AutoRegister {

    @Test
    @DisplayName("returns count matching all ActionType values with creators")
    void returnsCorrectCount() {
      int count = registry.autoRegisterCreators();
      long expected =
          java.util.Arrays.stream(ActionType.values()).filter(ActionType::hasCreator).count();
      assertEquals(expected, count);
    }
  }

  private void setupContext() {
    ActionContext ctx =
        ActionContext.builder()
            .serverManager(serverManager)
            .variableResolver(variableResolver)
            .settings(new SettingsConfig())
            .build();
    registry.setActionContext(ctx);
  }
}
