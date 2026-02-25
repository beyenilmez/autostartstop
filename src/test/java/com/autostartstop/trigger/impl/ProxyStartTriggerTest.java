package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProxyStartTrigger")
class ProxyStartTriggerTest {

  private ProxyStartTrigger createTrigger(boolean isReload) {
    TriggerContext triggerContext = TriggerContext.builder().isReload(isReload).build();
    TriggerConfig config = new TriggerConfig();
    config.setType("proxy_start");
    return ProxyStartTrigger.create(config, triggerContext);
  }

  @Test
  @DisplayName("getType returns PROXY_START")
  void getTypeReturnsProxyStart() {
    assertEquals(TriggerType.PROXY_START, createTrigger(false).getType());
  }

  // ========== Fire on Activation ==========

  @Nested
  @DisplayName("Fire on activation")
  class FireOnActivation {

    @Test
    @DisplayName("fires immediately on initial startup")
    void firesImmediatelyOnStartup() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ProxyStartTrigger trigger = createTrigger(false);

      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      assertNotNull(captured.get(), "should fire immediately on initial startup");
      assertEquals("proxy_start", captured.get().getVariable("_trigger_type"));
    }

    @Test
    @DisplayName("does NOT fire on reload")
    void doesNotFireOnReload() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ProxyStartTrigger trigger = createTrigger(true);

      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      assertNull(captured.get(), "should NOT fire on reload");
    }

    @Test
    @DisplayName("fires exactly once on activation")
    void firesExactlyOnce() {
      AtomicInteger fireCount = new AtomicInteger(0);
      ProxyStartTrigger trigger = createTrigger(false);

      trigger.activate(
          "rule1",
          ctx -> {
            fireCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
          });

      assertEquals(1, fireCount.get(), "should fire exactly once");
    }
  }

  // ========== Deactivation ==========

  @Nested
  @DisplayName("Deactivation")
  class Deactivation {

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      ProxyStartTrigger trigger = createTrigger(false);
      trigger.deactivate(); // should not throw
    }
  }
}
