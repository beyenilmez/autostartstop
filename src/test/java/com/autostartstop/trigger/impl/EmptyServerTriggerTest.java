package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.server.ServerManager;
import com.autostartstop.trigger.TriggerType;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmptyServerTrigger")
class EmptyServerTriggerTest {

  @Mock ProxyServer proxy;
  @Mock EventManager eventManager;
  @Mock ServerManager serverManager;
  @Mock Scheduler scheduler;
  @Mock Scheduler.TaskBuilder taskBuilder;
  @Mock ScheduledTask scheduledTask;

  private final Object plugin = new Object();

  @BeforeEach
  void setUp() {
    lenient().when(proxy.getEventManager()).thenReturn(eventManager);
    lenient().when(proxy.getScheduler()).thenReturn(scheduler);
    lenient().when(scheduler.buildTask(eq(plugin), any(Runnable.class))).thenReturn(taskBuilder);
    lenient().when(taskBuilder.delay(any(Duration.class))).thenReturn(taskBuilder);
    lenient().when(taskBuilder.repeat(any(Duration.class))).thenReturn(taskBuilder);
    lenient().when(taskBuilder.schedule()).thenReturn(scheduledTask);
    // No servers initially registered
    lenient().when(proxy.getAllServers()).thenReturn(Collections.emptyList());
  }

  // ========== Type and Config ==========

  @Test
  @DisplayName("getType returns EMPTY_SERVER")
  void getTypeReturnsEmptyServer() {
    EmptyServerTrigger trigger =
        new EmptyServerTrigger(
            proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
    assertEquals(TriggerType.EMPTY_SERVER, trigger.getType());
  }

  @Test
  @DisplayName("emptyTime is configurable")
  void emptyTimeConfigurable() {
    Duration emptyTime = Duration.ofMinutes(30);
    EmptyServerTrigger trigger =
        new EmptyServerTrigger(
            proxy, plugin, serverManager, emptyTime, Duration.ofMinutes(5), null);
    assertEquals(emptyTime, trigger.getEmptyTime());
  }

  // ========== Lifecycle ==========

  @Nested
  @DisplayName("Lifecycle")
  class Lifecycle {

    @Test
    @DisplayName("activate registers with event manager and starts periodic check")
    void activateRegistersAndStartsPeriodicCheck() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      assertTrue(trigger.isActivated());
      verify(eventManager).register(plugin, trigger);
      // Periodic check uses buildTask with delay + repeat
      verify(scheduler, atLeastOnce()).buildTask(eq(plugin), any(Runnable.class));
    }

    @Test
    @DisplayName("deactivate unregisters from event manager")
    void deactivateUnregisters() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();

      assertFalse(trigger.isActivated());
      verify(eventManager).unregisterListener(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.deactivate();
      verifyNoInteractions(eventManager);
    }

    @Test
    @DisplayName("deactivate cancels periodic check task")
    void deactivateCancelsPeriodicTask() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();

      verify(scheduledTask, atLeastOnce()).cancel();
    }
  }

  // ========== Server List Filtering ==========

  @Nested
  @DisplayName("Server list filtering")
  class ServerListFiltering {

    private RegisteredServer mockServer(String name, int playerCount, boolean online) {
      RegisteredServer server = mock(RegisteredServer.class);
      ServerInfo info = mock(ServerInfo.class);
      when(info.getName()).thenReturn(name);
      when(server.getServerInfo()).thenReturn(info);
      when(server.getPlayersConnected())
          .thenReturn(
              playerCount == 0
                  ? Collections.emptyList()
                  : Collections.nCopies(playerCount, mock(Player.class)));
      lenient().when(serverManager.isServerOnline(name)).thenReturn(online);
      return server;
    }

    @Test
    @DisplayName("no filter monitors all empty online servers")
    void noFilterMonitorsAll() {
      RegisteredServer server1 = mockServer("lobby", 0, true);
      RegisteredServer server2 = mockServer("survival", 0, true);
      when(proxy.getAllServers()).thenReturn(List.of(server1, server2));

      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      // Both servers are empty and online → timers scheduled
      // buildTask called for periodic check + 2 empty timers
      verify(scheduler, times(3)).buildTask(eq(plugin), any(Runnable.class));
    }

    @Test
    @DisplayName("whitelist only monitors listed servers")
    void whitelistMonitorsListedOnly() {
      RegisteredServer lobby = mockServer("lobby", 0, true);
      RegisteredServer creative = mockServer("creative", 0, true);
      when(proxy.getAllServers()).thenReturn(List.of(lobby, creative));

      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              Map.of("mode", "whitelist", "servers", List.of("lobby")));

      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy,
              plugin,
              serverManager,
              Duration.ofMinutes(15),
              Duration.ofMinutes(5),
              serverList);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      // periodic check + lobby timer = 2 tasks
      // creative should NOT get a timer
      verify(scheduler, times(2)).buildTask(eq(plugin), any(Runnable.class));
    }

    @Test
    @DisplayName("does not schedule timer for non-empty servers")
    void doesNotScheduleTimerForNonEmptyServers() {
      RegisteredServer server = mockServer("lobby", 5, true);
      when(proxy.getAllServers()).thenReturn(List.of(server));

      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      // Only the periodic check task should be scheduled, not an empty timer
      // periodic check = 1 call to buildTask
      verify(scheduler, times(1)).buildTask(eq(plugin), any(Runnable.class));
    }

    @Test
    @DisplayName("does not schedule timer for offline empty servers")
    void doesNotScheduleTimerForOfflineServers() {
      RegisteredServer server = mockServer("lobby", 0, false);
      when(proxy.getAllServers()).thenReturn(List.of(server));

      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      // Only the periodic check task
      verify(scheduler, times(1)).buildTask(eq(plugin), any(Runnable.class));
    }
  }

  // ========== Player Connect Cancels Timer ==========

  @Nested
  @DisplayName("Player connect cancels timer")
  class PlayerConnectCancelsTimer {

    @Test
    @DisplayName("onServerConnected cancels empty timer for that server")
    void onServerConnectedCancelsTimer() {
      // First activate with an empty server to create a pending timer
      RegisteredServer server = mock(RegisteredServer.class);
      ServerInfo info = mock(ServerInfo.class);
      when(info.getName()).thenReturn("lobby");
      when(server.getServerInfo()).thenReturn(info);
      when(server.getPlayersConnected()).thenReturn(Collections.emptyList());
      when(serverManager.isServerOnline("lobby")).thenReturn(true);
      when(proxy.getAllServers()).thenReturn(List.of(server));

      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      // Now simulate a player joining "lobby"
      Player player = mock(Player.class);
      ServerConnectedEvent connectEvent = mock(ServerConnectedEvent.class);
      when(connectEvent.getPlayer()).thenReturn(player);
      when(connectEvent.getServer()).thenReturn(server);
      when(connectEvent.getPreviousServer()).thenReturn(Optional.empty());

      trigger.onServerConnected(connectEvent);

      // The scheduled task should be cancelled (for the empty timer)
      verify(scheduledTask, atLeastOnce()).cancel();
    }
  }

  // ========== Context Emission ==========

  @Nested
  @DisplayName("Context emission")
  class ContextEmission {

    /**
     * Sets up an empty online server, activates the trigger, captures the empty-timer Runnable from
     * the scheduler, and invokes it to simulate the timer expiring. Returns the emitted
     * ExecutionContext.
     */
    private ExecutionContext fireAndCapture(Duration emptyTime, String serverName) {
      RegisteredServer server = mock(RegisteredServer.class);
      ServerInfo info = mock(ServerInfo.class);
      when(info.getName()).thenReturn(serverName);
      when(server.getServerInfo()).thenReturn(info);
      // Must stay empty for the double-check inside fireEmptyTrigger
      when(server.getPlayersConnected()).thenReturn(Collections.emptyList());
      when(serverManager.isServerOnline(serverName)).thenReturn(true);
      when(proxy.getAllServers()).thenReturn(List.of(server));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, emptyTime, Duration.ofMinutes(5), null);
      trigger.activate(
          "test-rule",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      // Capture all Runnables passed to the scheduler.
      // Order during activation: 1) empty timer, 2) periodic check
      ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
      verify(scheduler, atLeast(2)).buildTask(eq(plugin), runnableCaptor.capture());

      // The first captured Runnable is the empty timer (from scheduleEmptyTimer)
      Runnable emptyTimerRunnable = runnableCaptor.getAllValues().get(0);
      emptyTimerRunnable.run();

      assertNotNull(captured.get(), "callback should have been invoked");
      return captured.get();
    }

    @Test
    @DisplayName("emits trigger type as 'empty_server'")
    void emitsTriggerType() {
      ExecutionContext ctx = fireAndCapture(Duration.ofMinutes(15), "lobby");
      assertEquals("empty_server", ctx.getVariable("_trigger_type"));
    }

    @Test
    @DisplayName("emits the server name")
    void emitsServerName() {
      ExecutionContext ctx = fireAndCapture(Duration.ofMinutes(15), "survival");
      assertEquals("survival", ctx.getVariable("empty_server.server.name"));
    }

    @Test
    @DisplayName("emits the RegisteredServer object")
    void emitsServerObject() {
      ExecutionContext ctx = fireAndCapture(Duration.ofMinutes(15), "lobby");
      assertNotNull(ctx.getVariable("empty_server.server"));
      assertInstanceOf(RegisteredServer.class, ctx.getVariable("empty_server.server"));
    }

    @Test
    @DisplayName("emits the formatted empty_time duration")
    void emitsEmptyTime() {
      ExecutionContext ctx = fireAndCapture(Duration.ofMinutes(15), "lobby");
      assertEquals("15m 0s", ctx.getVariable("empty_server.empty_time"));
    }

    @Test
    @DisplayName("emits custom empty_time value")
    void emitsCustomEmptyTime() {
      ExecutionContext ctx = fireAndCapture(Duration.ofHours(1), "lobby");
      assertEquals("1h 0m", ctx.getVariable("empty_server.empty_time"));
    }

    @Test
    @DisplayName("emits empty_since as ISO-8601 instant string")
    void emitsEmptySince() {
      ExecutionContext ctx = fireAndCapture(Duration.ofMinutes(15), "lobby");
      Object emptySince = ctx.getVariable("empty_server.empty_since");
      assertNotNull(emptySince, "empty_since should be set");
      assertInstanceOf(String.class, emptySince);
      // ISO-8601 Instant format contains 'T' separator
      assertTrue(((String) emptySince).contains("T"), "empty_since should be ISO-8601 format");
    }

    @Test
    @DisplayName("emits all context variables together")
    void emitsAllVariables() {
      ExecutionContext ctx = fireAndCapture(Duration.ofMinutes(30), "creative");

      assertAll(
          () -> assertEquals("empty_server", ctx.getVariable("_trigger_type")),
          () -> assertEquals("creative", ctx.getVariable("empty_server.server.name")),
          () -> assertInstanceOf(RegisteredServer.class, ctx.getVariable("empty_server.server")),
          () -> assertEquals("30m 0s", ctx.getVariable("empty_server.empty_time")),
          () -> assertNotNull(ctx.getVariable("empty_server.empty_since")));
    }

    @Test
    @DisplayName("does not fire if server has players when timer expires")
    void doesNotFireIfServerHasPlayersAtExpiry() {
      RegisteredServer server = mock(RegisteredServer.class);
      ServerInfo info = mock(ServerInfo.class);
      when(info.getName()).thenReturn("lobby");
      when(server.getServerInfo()).thenReturn(info);
      // Empty at first to schedule the timer
      when(server.getPlayersConnected()).thenReturn(Collections.emptyList());
      when(serverManager.isServerOnline("lobby")).thenReturn(true);
      when(proxy.getAllServers()).thenReturn(List.of(server));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      // Now make the server non-empty before the timer fires
      when(server.getPlayersConnected()).thenReturn(List.of(mock(Player.class)));

      ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
      verify(scheduler, atLeast(2)).buildTask(eq(plugin), runnableCaptor.capture());
      Runnable emptyTimerRunnable = runnableCaptor.getAllValues().get(0);
      emptyTimerRunnable.run();

      assertNull(captured.get(), "should NOT fire when server has players at expiry");
    }
  }

  // ========== Event Guard ==========

  @Nested
  @DisplayName("Event guard")
  class EventGuard {

    @Test
    @DisplayName("ignores disconnect event when not activated")
    void ignoresDisconnectWhenNotActivated() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);

      DisconnectEvent event = mock(DisconnectEvent.class);
      trigger.onDisconnect(event); // should not throw
      verifyNoInteractions(event);
    }

    @Test
    @DisplayName("ignores connect event when not activated")
    void ignoresConnectWhenNotActivated() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(5), null);

      ServerConnectedEvent event = mock(ServerConnectedEvent.class);
      trigger.onServerConnected(event); // should not throw
      verifyNoInteractions(event);
    }

    @Test
    @DisplayName("disabled periodic checks if check interval is 0")
    void disabledPeriodicChecksIfCheckIntervalIs0() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ZERO, null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      verify(scheduler, never()).buildTask(eq(plugin), any(Runnable.class));
    }

    @Test
    @DisplayName("disabled periodic checks if check interval is negative")
    void disabledPeriodicChecksIfCheckIntervalIsNegative() {
      EmptyServerTrigger trigger =
          new EmptyServerTrigger(
              proxy, plugin, serverManager, Duration.ofMinutes(15), Duration.ofMinutes(-5), null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      verify(scheduler, never()).buildTask(eq(plugin), any(Runnable.class));
    }
  }
}
