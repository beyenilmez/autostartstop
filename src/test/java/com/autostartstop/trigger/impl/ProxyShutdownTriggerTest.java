package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.TriggerType;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProxyShutdownTrigger")
class ProxyShutdownTriggerTest {

  @Mock ProxyServer proxy;
  @Mock EventManager eventManager;

  private final Object plugin = new Object();

  @BeforeEach
  void setUp() {
    when(proxy.getEventManager()).thenReturn(eventManager);
  }

  @Test
  @DisplayName("getType returns PROXY_SHUTDOWN")
  void getTypeReturnsProxyShutdown() {
    assertEquals(TriggerType.PROXY_SHUTDOWN, new ProxyShutdownTrigger(proxy, plugin).getType());
  }

  // ========== Lifecycle ==========

  @Nested
  @DisplayName("Lifecycle")
  class Lifecycle {

    @Test
    @DisplayName("activate registers with event manager")
    void activateRegistersEvent() {
      ProxyShutdownTrigger trigger = new ProxyShutdownTrigger(proxy, plugin);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      verify(eventManager).register(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate unregisters from event manager")
    void deactivateUnregistersEvent() {
      ProxyShutdownTrigger trigger = new ProxyShutdownTrigger(proxy, plugin);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();
      verify(eventManager).unregisterListener(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      ProxyShutdownTrigger trigger = new ProxyShutdownTrigger(proxy, plugin);
      trigger.deactivate();
      verifyNoInteractions(eventManager);
    }
  }

  // ========== Event Handling ==========

  @Nested
  @DisplayName("Event handling")
  class EventHandling {

    @Test
    @DisplayName("fires callback on shutdown event")
    void firesCallbackOnShutdownEvent() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ProxyShutdownTrigger trigger = new ProxyShutdownTrigger(proxy, plugin);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyShutdown(mock(ProxyShutdownEvent.class));

      assertNotNull(captured.get());
      assertEquals("proxy_shutdown", captured.get().getVariable("_trigger_type"));
    }

    @Test
    @DisplayName("ignores event when not activated")
    void ignoresWhenNotActivated() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ProxyShutdownTrigger trigger = new ProxyShutdownTrigger(proxy, plugin);
      // NOT activated

      trigger.onProxyShutdown(mock(ProxyShutdownEvent.class));
      assertNull(captured.get());
    }

    @Test
    @DisplayName("ignores event after deactivation")
    void ignoresAfterDeactivation() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ProxyShutdownTrigger trigger = new ProxyShutdownTrigger(proxy, plugin);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });
      trigger.deactivate();

      trigger.onProxyShutdown(mock(ProxyShutdownEvent.class));
      assertNull(captured.get(), "should not fire after deactivation");
    }
  }
}
