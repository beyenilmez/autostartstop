package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.server.ServerManager;
import com.autostartstop.trigger.TriggerType;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
@DisplayName("ConnectionTrigger")
class ConnectionTriggerTest {

  @Mock ProxyServer proxy;
  @Mock EventManager eventManager;
  @Mock ServerManager serverManager;
  @Mock Player player;
  @Mock RegisteredServer registeredServer;
  @Mock ServerInfo serverInfo;

  private final Object plugin = new Object();

  @BeforeEach
  void setUp() {
    lenient().when(proxy.getEventManager()).thenReturn(eventManager);
  }

  // ========== Helpers ==========

  private ConnectionTrigger createTrigger(
      TriggerConfig.ServerListConfig serverList,
      TriggerConfig.PlayerListConfig playerList,
      boolean denyConnection) {
    return new ConnectionTrigger(
        proxy, plugin, serverManager, serverList, playerList, denyConnection);
  }

  private void activateTrigger(
      ConnectionTrigger trigger, Function<ExecutionContext, CompletableFuture<Void>> callback) {
    trigger.activate("test-rule", callback);
  }

  private ServerPreConnectEvent mockConnectEvent(
      String serverName, String playerName, UUID playerUuid) {
    when(serverInfo.getName()).thenReturn(serverName);
    when(registeredServer.getServerInfo()).thenReturn(serverInfo);
    when(registeredServer.getPlayersConnected()).thenReturn(Collections.emptyList());
    when(player.getUsername()).thenReturn(playerName);
    when(player.getUniqueId()).thenReturn(playerUuid);
    when(serverManager.isServerOnline(serverName)).thenReturn(true);

    ServerPreConnectEvent event = mock(ServerPreConnectEvent.class);
    when(event.getPlayer()).thenReturn(player);
    when(event.getOriginalServer()).thenReturn(registeredServer);
    return event;
  }

  // ========== Lifecycle ==========

  @Nested
  @DisplayName("Lifecycle")
  class Lifecycle {

    @Test
    @DisplayName("activate registers with event manager")
    void activateRegistersEvent() {
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(trigger, ctx -> CompletableFuture.completedFuture(null));
      verify(eventManager).register(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate unregisters from event manager")
    void deactivateUnregistersEvent() {
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(trigger, ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();
      verify(eventManager).unregisterListener(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      ConnectionTrigger trigger = createTrigger(null, null, false);
      trigger.deactivate(); // should not throw
      verifyNoInteractions(eventManager);
    }

    @Test
    @DisplayName("getType returns CONNECTION")
    void getTypeReturnsConnection() {
      ConnectionTrigger trigger = createTrigger(null, null, false);
      assertEquals(TriggerType.CONNECTION, trigger.getType());
    }
  }

  // ========== Server List Filtering ==========

  @Nested
  @DisplayName("Server list filtering")
  class ServerListFiltering {

    @Test
    @DisplayName("no filter allows all servers")
    void noFilterAllowsAll() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("anyserver", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "callback should fire for any server");
    }

    @Test
    @DisplayName("whitelist allows matching server")
    void whitelistAllowsMatching() {
      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              java.util.Map.of("mode", "whitelist", "servers", List.of("lobby", "survival")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(serverList, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "whitelisted server should fire");
    }

    @Test
    @DisplayName("whitelist blocks non-matching server")
    void whitelistBlocksNonMatching() {
      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              java.util.Map.of("mode", "whitelist", "servers", List.of("lobby")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(serverList, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("creative", "Steve", UUID.randomUUID()));
      assertNull(captured.get(), "non-whitelisted server should not fire");
    }

    @Test
    @DisplayName("blacklist blocks matching server")
    void blacklistBlocksMatching() {
      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              java.util.Map.of("mode", "blacklist", "servers", List.of("lobby")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(serverList, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Steve", UUID.randomUUID()));
      assertNull(captured.get(), "blacklisted server should not fire");
    }

    @Test
    @DisplayName("blacklist allows non-matching server")
    void blacklistAllowsNonMatching() {
      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              java.util.Map.of("mode", "blacklist", "servers", List.of("lobby")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(serverList, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("survival", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "non-blacklisted server should fire");
    }

    @Test
    @DisplayName("disabled mode allows all servers even with a list")
    void disabledModeAllowsAll() {
      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              java.util.Map.of("mode", "disabled", "servers", List.of("lobby")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(serverList, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("creative", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "disabled mode should allow all servers");
    }

    @Test
    @DisplayName("defaults to whitelist when mode is null")
    void defaultsToWhitelistWhenModeNull() {
      // No explicit mode → should default to whitelist
      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(java.util.Map.of("servers", List.of("lobby")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(serverList, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      // "creative" not in whitelist → should be blocked
      trigger.onServerPreConnect(mockConnectEvent("creative", "Steve", UUID.randomUUID()));
      assertNull(captured.get(), "default whitelist should block non-listed server");
    }
  }

  // ========== Player List Filtering ==========

  @Nested
  @DisplayName("Player list filtering")
  class PlayerListFiltering {

    @Test
    @DisplayName("whitelist allows matching player")
    void whitelistAllowsMatchingPlayer() {
      TriggerConfig.PlayerListConfig playerList =
          new TriggerConfig.PlayerListConfig(
              java.util.Map.of("mode", "whitelist", "players", List.of("Steve", "Alex")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, playerList, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "whitelisted player should fire");
    }

    @Test
    @DisplayName("whitelist blocks non-matching player")
    void whitelistBlocksNonMatchingPlayer() {
      TriggerConfig.PlayerListConfig playerList =
          new TriggerConfig.PlayerListConfig(
              java.util.Map.of("mode", "whitelist", "players", List.of("Steve")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, playerList, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Alex", UUID.randomUUID()));
      assertNull(captured.get(), "non-whitelisted player should not fire");
    }

    @Test
    @DisplayName("blacklist blocks matching player")
    void blacklistBlocksMatchingPlayer() {
      TriggerConfig.PlayerListConfig playerList =
          new TriggerConfig.PlayerListConfig(
              java.util.Map.of("mode", "blacklist", "players", List.of("Griefer")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, playerList, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Griefer", UUID.randomUUID()));
      assertNull(captured.get(), "blacklisted player should not fire");
    }

    @Test
    @DisplayName("blacklist allows non-matching player")
    void blacklistAllowsNonMatchingPlayer() {
      TriggerConfig.PlayerListConfig playerList =
          new TriggerConfig.PlayerListConfig(
              java.util.Map.of("mode", "blacklist", "players", List.of("Griefer")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, playerList, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "non-blacklisted player should fire");
    }
  }

  // ========== Combined Filters ==========

  @Nested
  @DisplayName("Combined server + player filtering")
  class CombinedFiltering {

    private TriggerConfig.ServerListConfig whitelistServer() {
      return new TriggerConfig.ServerListConfig(
          java.util.Map.of("mode", "whitelist", "servers", List.of("lobby")));
    }

    private TriggerConfig.PlayerListConfig whitelistPlayer() {
      return new TriggerConfig.PlayerListConfig(
          java.util.Map.of("mode", "whitelist", "players", List.of("Steve")));
    }

    @Test
    @DisplayName("right server + wrong player is blocked")
    void rightServerWrongPlayerBlocked() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(whitelistServer(), whitelistPlayer(), false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Alex", UUID.randomUUID()));
      assertNull(captured.get(), "wrong player should block");
    }

    @Test
    @DisplayName("right server + right player is allowed")
    void rightServerRightPlayerAllowed() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(whitelistServer(), whitelistPlayer(), false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Steve", UUID.randomUUID()));
      assertNotNull(captured.get(), "right server + right player should fire");
    }

    @Test
    @DisplayName("wrong server + right player is blocked by server filter")
    void wrongServerRightPlayerBlocked() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(whitelistServer(), whitelistPlayer(), false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("creative", "Steve", UUID.randomUUID()));
      assertNull(captured.get(), "wrong server should block even with right player");
    }

    @Test
    @DisplayName("wrong server + wrong player is blocked")
    void wrongServerWrongPlayerBlocked() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(whitelistServer(), whitelistPlayer(), false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("creative", "Alex", UUID.randomUUID()));
      assertNull(captured.get(), "wrong server + wrong player should block");
    }
  }

  // ========== Context Emission ==========

  @Nested
  @DisplayName("Context emission")
  class ContextEmission {

    @Test
    @DisplayName("emits all expected context variables")
    void emitsAllContextVariables() {
      UUID playerUuid = UUID.randomUUID();
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("survival", "Steve", playerUuid));

      ExecutionContext ctx = captured.get();
      assertNotNull(ctx);

      // Trigger type
      assertEquals("connection", ctx.getVariable("_trigger_type"));

      // Player variables
      assertEquals("Steve", ctx.getVariable("connection.player.name"));
      assertEquals(playerUuid.toString(), ctx.getVariable("connection.player.uuid"));
      assertSame(player, ctx.getVariable("connection.player"));

      // Server variables
      assertEquals("survival", ctx.getVariable("connection.server.name"));
      assertSame(registeredServer, ctx.getVariable("connection.server"));
      assertEquals("online", ctx.getVariable("connection.server.status"));
      assertEquals(0, ctx.getVariable("connection.server.player_count"));
      assertEquals(Collections.emptyList(), ctx.getVariable("connection.server.players"));
    }

    @Test
    @DisplayName("emits offline status when server is not online")
    void emitsOfflineStatus() {
      UUID playerUuid = UUID.randomUUID();
      when(serverInfo.getName()).thenReturn("survival");
      when(registeredServer.getServerInfo()).thenReturn(serverInfo);
      when(registeredServer.getPlayersConnected()).thenReturn(Collections.emptyList());
      when(player.getUsername()).thenReturn("Steve");
      when(player.getUniqueId()).thenReturn(playerUuid);
      when(serverManager.isServerOnline("survival")).thenReturn(false);

      ServerPreConnectEvent event = mock(ServerPreConnectEvent.class);
      when(event.getPlayer()).thenReturn(player);
      when(event.getOriginalServer()).thenReturn(registeredServer);

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(event);
      assertEquals("offline", captured.get().getVariable("connection.server.status"));
    }
  }

  // ========== Deny Connection ==========

  @Nested
  @DisplayName("Deny connection")
  class DenyConnection {

    @Test
    @DisplayName("sets denied result when deny_connection is true")
    void setsDeniedResult() {
      ConnectionTrigger trigger = createTrigger(null, null, true);
      activateTrigger(trigger, ctx -> CompletableFuture.completedFuture(null));

      ServerPreConnectEvent event = mockConnectEvent("lobby", "Steve", UUID.randomUUID());
      trigger.onServerPreConnect(event);

      verify(event).setResult(ServerPreConnectEvent.ServerResult.denied());
    }

    @Test
    @DisplayName("does not set denied result when deny_connection is false")
    void doesNotDenyWhenFalse() {
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(trigger, ctx -> CompletableFuture.completedFuture(null));

      ServerPreConnectEvent event = mockConnectEvent("lobby", "Steve", UUID.randomUUID());
      trigger.onServerPreConnect(event);

      verify(event, never()).setResult(any());
    }

    @Test
    @DisplayName("creates event release signal when deny_connection is true")
    void createsReleaseSignalWhenDeny() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, true);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onServerPreConnect(mockConnectEvent("lobby", "Steve", UUID.randomUUID()));

      ExecutionContext ctx = captured.get();
      assertNotNull(ctx);
      assertTrue(
          ctx.hasEventReleaseSignal(), "should create release signal when deny_connection=true");
    }
  }

  // ========== Event Guard ==========

  @Nested
  @DisplayName("Event guard")
  class EventGuard {

    @Test
    @DisplayName("ignores event with null target server")
    void ignoresNullTargetServer() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      ServerPreConnectEvent event = mock(ServerPreConnectEvent.class);
      when(event.getPlayer()).thenReturn(player);
      when(event.getOriginalServer()).thenReturn(null);

      trigger.onServerPreConnect(event);
      assertNull(captured.get(), "null target server should be ignored");
    }

    @Test
    @DisplayName("ignores event when not activated")
    void ignoresWhenNotActivated() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, false);
      // Do NOT activate

      ServerPreConnectEvent event = mock(ServerPreConnectEvent.class);
      trigger.onServerPreConnect(event);
      assertNull(captured.get());
    }

    @Test
    @DisplayName("ignores event after deactivation")
    void ignoresAfterDeactivation() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ConnectionTrigger trigger = createTrigger(null, null, false);
      activateTrigger(
          trigger,
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });
      trigger.deactivate();

      ServerPreConnectEvent event = mock(ServerPreConnectEvent.class);
      trigger.onServerPreConnect(event);
      assertNull(captured.get(), "should not fire after deactivation");
    }
  }
}
