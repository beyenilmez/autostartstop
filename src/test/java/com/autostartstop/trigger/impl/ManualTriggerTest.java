package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.autostartstop.config.ConfigException;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ManualTrigger")
class ManualTriggerTest {

  // ========== Factory ==========

  @Nested
  @DisplayName("Factory")
  class Factory {

    @Test
    @DisplayName("create succeeds with valid id")
    void createSucceedsWithValidId() {
      TriggerConfig config = new TriggerConfig();
      config.setType("manual");
      config.setRawConfig(Map.of("id", "my-trigger"));

      TriggerContext ctx = TriggerContext.builder().build();
      ManualTrigger trigger = ManualTrigger.create(config, ctx);

      assertEquals("my-trigger", trigger.getId());
    }

    @Test
    @DisplayName("create throws when id is missing")
    void createThrowsWhenIdMissing() {
      TriggerConfig config = new TriggerConfig();
      config.setType("manual");
      config.setRawConfig(Map.of());

      TriggerContext ctx = TriggerContext.builder().build();
      assertThrows(ConfigException.class, () -> ManualTrigger.create(config, ctx));
    }

    @Test
    @DisplayName("create throws when id is blank")
    void createThrowsWhenIdBlank() {
      TriggerConfig config = new TriggerConfig();
      config.setType("manual");
      config.setRawConfig(Map.of("id", "   "));

      TriggerContext ctx = TriggerContext.builder().build();
      assertThrows(ConfigException.class, () -> ManualTrigger.create(config, ctx));
    }
  }

  // ========== Lifecycle ==========

  @Nested
  @DisplayName("Lifecycle")
  class Lifecycle {

    @Test
    @DisplayName("getType returns MANUAL")
    void getTypeReturnsManual() {
      ManualTrigger trigger = new ManualTrigger("test");
      assertEquals(TriggerType.MANUAL, trigger.getType());
    }

    @Test
    @DisplayName("activate sets trigger to activated")
    void activateSetsActivated() {
      ManualTrigger trigger = new ManualTrigger("test");
      assertFalse(trigger.isActivated());
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertTrue(trigger.isActivated());
    }

    @Test
    @DisplayName("deactivate sets trigger to not activated")
    void deactivateSetsNotActivated() {
      ManualTrigger trigger = new ManualTrigger("test");
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();
      assertFalse(trigger.isActivated());
    }

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      ManualTrigger trigger = new ManualTrigger("test");
      trigger.deactivate(); // should not throw
      assertFalse(trigger.isActivated());
    }
  }

  // ========== Fire ==========

  @Nested
  @DisplayName("Fire")
  class Fire {

    @Test
    @DisplayName("fire invokes callback with context")
    void fireInvokesCallback() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ManualTrigger trigger = new ManualTrigger("my-trigger");
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.fire(new String[] {"arg0", "arg1"});
      assertNotNull(captured.get());
    }

    @Test
    @DisplayName("fire does not invoke callback when not activated")
    void fireDoesNothingWhenNotActivated() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ManualTrigger trigger = new ManualTrigger("my-trigger");
      // NOT activated

      trigger.fire(new String[] {"arg0"});
      assertNull(captured.get());
    }

    @Test
    @DisplayName("fire does not invoke callback after deactivation")
    void fireDoesNothingAfterDeactivation() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ManualTrigger trigger = new ManualTrigger("my-trigger");
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });
      trigger.deactivate();

      trigger.fire(new String[] {"arg0"});
      assertNull(captured.get());
    }
  }

  // ========== Context Emission ==========

  @Nested
  @DisplayName("Context emission")
  class ContextEmission {

    @Test
    @DisplayName("emits trigger type, id, and arguments")
    void emitsAllContextVariables() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ManualTrigger trigger = new ManualTrigger("deploy");
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.fire(new String[] {"server1", "fast"});

      ExecutionContext ctx = captured.get();
      assertNotNull(ctx);
      assertEquals("manual", ctx.getVariable("_trigger_type"));
      assertEquals("deploy", ctx.getVariable("manual.id"));
      assertEquals(2, ctx.getVariable("manual.args.length"));
      assertEquals("server1", ctx.getVariable("manual.args.0"));
      assertEquals("fast", ctx.getVariable("manual.args.1"));
    }

    @Test
    @DisplayName("emits zero args when fired with empty array")
    void emitsZeroArgs() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ManualTrigger trigger = new ManualTrigger("test");
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.fire(new String[] {});

      ExecutionContext ctx = captured.get();
      assertEquals(0, ctx.getVariable("manual.args.length"));
      assertNull(ctx.getVariable("manual.args.0"));
    }
  }
}
