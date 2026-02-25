package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.server.ServerManager;
import com.autostartstop.trigger.TriggerType;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
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
@DisplayName("PingTrigger")
class PingTriggerTest {

  @Mock ProxyServer proxy;
  @Mock EventManager eventManager;
  @Mock ServerManager serverManager;

  private final Object plugin = new Object();

  @BeforeEach
  void setUp() {
    when(proxy.getEventManager()).thenReturn(eventManager);
  }

  // ========== Helpers ==========

  /** Creates a default PluginConfig that maps virtual hosts used in tests to server names. */
  private PluginConfig defaultPluginConfig() {
    ServerConfig playServer = new ServerConfig();
    playServer.setVirtualHost("play.myserver.com");

    ServerConfig survivalServer = new ServerConfig();
    survivalServer.setVirtualHost("survival.myserver.com");

    ServerConfig allowedServer = new ServerConfig();
    allowedServer.setVirtualHost("allowed.server.com");

    PluginConfig config = new PluginConfig();
    config.setServers(
        Map.of(
            "play", playServer,
            "survival", survivalServer,
            "allowed", allowedServer));
    return config;
  }

  private void setupDefaultPluginConfig() {
    when(serverManager.getPluginConfig()).thenReturn(defaultPluginConfig());
  }

  private PingTrigger createTrigger(
      TriggerConfig.VirtualHostListConfig virtualHostList,
      TriggerConfig.ServerListConfig serverList,
      boolean holdResponse) {
    return new PingTrigger(proxy, plugin, serverManager, virtualHostList, serverList, holdResponse);
  }

  /**
   * Creates a mock ProxyPingEvent with the given virtual host. The virtual host MUST have a mapping
   * in the default PluginConfig (or be null for no-virtual-host tests that don't reach
   * emitContext).
   */
  private ProxyPingEvent mockPingEvent(String virtualHost) {
    InboundConnection connection = mock(InboundConnection.class);

    if (virtualHost != null) {
      InetSocketAddress vh = new InetSocketAddress(virtualHost, 25565);
      when(connection.getVirtualHost()).thenReturn(Optional.of(vh));
    } else {
      when(connection.getVirtualHost()).thenReturn(Optional.empty());
    }

    InetSocketAddress remoteAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345);
    when(connection.getRemoteAddress()).thenReturn(remoteAddr);
    when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21);

    ServerPing.Version version = mock(ServerPing.Version.class);
    when(version.getName()).thenReturn("1.21");
    when(version.getProtocol()).thenReturn(767);

    ServerPing.Players players = mock(ServerPing.Players.class);
    when(players.getOnline()).thenReturn(10);
    when(players.getMax()).thenReturn(100);

    ServerPing ping = mock(ServerPing.class);
    when(ping.getVersion()).thenReturn(version);
    when(ping.getPlayers()).thenReturn(Optional.of(players));
    when(ping.getDescriptionComponent()).thenReturn(Component.text("Hello World"));

    ProxyPingEvent event = mock(ProxyPingEvent.class);
    when(event.getConnection()).thenReturn(connection);
    when(event.getPing()).thenReturn(ping);

    return event;
  }

  // ========== Lifecycle ==========

  @Nested
  @DisplayName("Lifecycle")
  class Lifecycle {

    @Test
    @DisplayName("getType returns PING")
    void getTypeReturnsPing() {
      assertEquals(TriggerType.PING, createTrigger(null, null, false).getType());
    }

    @Test
    @DisplayName("activate registers with event manager")
    void activateRegistersEvent() {
      when(serverManager.getPluginConfig()).thenReturn(null);

      PingTrigger trigger = createTrigger(null, null, false);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      verify(eventManager).register(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate unregisters from event manager")
    void deactivateUnregistersEvent() {
      when(serverManager.getPluginConfig()).thenReturn(null);

      PingTrigger trigger = createTrigger(null, null, false);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();
      verify(eventManager).unregisterListener(plugin, trigger);
    }

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      PingTrigger trigger = createTrigger(null, null, false);
      trigger.deactivate();
      verifyNoInteractions(eventManager);
    }
  }

  // ========== Virtual Host Filtering ==========

  @Nested
  @DisplayName("Virtual host filtering")
  class VirtualHostFiltering {

    @Test
    @DisplayName("no filter allows all pings")
    void noFilterAllowsAll() {
      setupDefaultPluginConfig();

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));
      assertNotNull(captured.get(), "callback should fire for any ping when no filter");
    }

    @Test
    @DisplayName("whitelist allows matching virtual host")
    void whitelistAllowsMatching() {
      setupDefaultPluginConfig();

      TriggerConfig.VirtualHostListConfig vhList =
          new TriggerConfig.VirtualHostListConfig(
              Map.of("mode", "whitelist", "virtual_hosts", List.of("play.myserver.com")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(vhList, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));
      assertNotNull(captured.get(), "whitelisted virtual host should fire");
    }

    @Test
    @DisplayName("whitelist blocks non-matching virtual host")
    void whitelistBlocksNonMatching() {
      setupDefaultPluginConfig();

      TriggerConfig.VirtualHostListConfig vhList =
          new TriggerConfig.VirtualHostListConfig(
              Map.of("mode", "whitelist", "virtual_hosts", List.of("play.myserver.com")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(vhList, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("other.server.com"));
      assertNull(captured.get(), "non-whitelisted virtual host should not fire");
    }

    @Test
    @DisplayName("blacklist blocks matching virtual host")
    void blacklistBlocksMatching() {
      setupDefaultPluginConfig();

      TriggerConfig.VirtualHostListConfig vhList =
          new TriggerConfig.VirtualHostListConfig(
              Map.of("mode", "blacklist", "virtual_hosts", List.of("play.myserver.com")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(vhList, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));
      assertNull(captured.get(), "blacklisted virtual host should not fire");
    }

    @Test
    @DisplayName("blacklist allows non-matching virtual host")
    void blacklistAllowsNonMatching() {
      setupDefaultPluginConfig();

      TriggerConfig.VirtualHostListConfig vhList =
          new TriggerConfig.VirtualHostListConfig(
              Map.of("mode", "blacklist", "virtual_hosts", List.of("play.myserver.com")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(vhList, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      // "allowed.server.com" has a mapping in defaultPluginConfig
      trigger.onProxyPing(mockPingEvent("allowed.server.com"));
      assertNotNull(captured.get(), "non-blacklisted virtual host should fire");
    }

    @Test
    @DisplayName("whitelist with no virtual host in request does not fire")
    void whitelistWithNoVirtualHostDoesNotFire() {
      setupDefaultPluginConfig();

      TriggerConfig.VirtualHostListConfig vhList =
          new TriggerConfig.VirtualHostListConfig(
              Map.of("mode", "whitelist", "virtual_hosts", List.of("play.myserver.com")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(vhList, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent(null));
      assertNull(captured.get(), "whitelist should not fire when no virtual host in request");
    }

    @Test
    @DisplayName("virtual host matching is case-insensitive")
    void caseInsensitiveMatching() {
      setupDefaultPluginConfig();

      TriggerConfig.VirtualHostListConfig vhList =
          new TriggerConfig.VirtualHostListConfig(
              Map.of("mode", "whitelist", "virtual_hosts", List.of("Play.MyServer.com")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(vhList, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));
      assertNotNull(captured.get(), "virtual host matching should be case-insensitive");
    }
  }

  // ========== Server List → Virtual Host Mapping ==========

  @Nested
  @DisplayName("Server list to virtual host mapping")
  class ServerListMapping {

    @Test
    @DisplayName("server_list maps server names to their virtual hosts for filtering")
    void serverListMapsToVirtualHosts() {
      setupDefaultPluginConfig();

      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              Map.of("mode", "whitelist", "servers", List.of("survival")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, serverList, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("survival.myserver.com"));
      assertNotNull(captured.get(), "ping to server's virtual host should fire");
    }

    @Test
    @DisplayName("server_list blocks pings to unmapped virtual hosts")
    void serverListBlocksUnmapped() {
      setupDefaultPluginConfig();

      TriggerConfig.ServerListConfig serverList =
          new TriggerConfig.ServerListConfig(
              Map.of("mode", "whitelist", "servers", List.of("survival")));

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, serverList, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("other.server.com"));
      assertNull(captured.get(), "ping to non-mapped virtual host should not fire");
    }
  }

  // ========== Context Emission ==========

  @Nested
  @DisplayName("Context emission")
  class ContextEmission {

    @Test
    @DisplayName("emits all expected context variables")
    void emitsAllContextVariables() {
      setupDefaultPluginConfig();

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));

      ExecutionContext ctx = captured.get();
      assertNotNull(ctx);

      // Trigger type
      assertEquals("ping", ctx.getVariable("_trigger_type"));

      // Server version
      assertEquals("1.21", ctx.getVariable("ping.server.version_name"));
      assertEquals(767, ctx.getVariable("ping.server.protocol_version"));

      // Players
      assertEquals(10, ctx.getVariable("ping.server.player_count"));
      assertEquals(100, ctx.getVariable("ping.server.max_players"));

      // MOTD (serialized from Component)
      assertNotNull(ctx.getVariable("ping.server.motd"));

      // Connection
      assertNotNull(ctx.getVariable("ping.player.remote_address"));
      assertEquals("play.myserver.com", ctx.getVariable("ping.player.virtual_host"));
    }

    @Test
    @DisplayName("resolves ping.server from virtual host map")
    void resolvesPingServerFromVirtualHostMap() {
      setupDefaultPluginConfig();

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("survival.myserver.com"));

      ExecutionContext ctx = captured.get();
      assertEquals(
          "survival", ctx.getVariable("ping.server"), "should resolve virtual host to server name");
    }
  }

  // ========== Hold Response ==========

  @Nested
  @DisplayName("Hold response")
  class HoldResponse {

    @Test
    @DisplayName("creates event release signal when hold_response is true")
    void createsReleaseSignalWhenHold() {
      setupDefaultPluginConfig();

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, null, true);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));

      ExecutionContext ctx = captured.get();
      assertNotNull(ctx);
      assertTrue(
          ctx.hasEventReleaseSignal(), "should create release signal when hold_response=true");
    }
  }

  // ========== Event Guard ==========

  @Nested
  @DisplayName("Event guard")
  class EventGuard {

    @Test
    @DisplayName("ignores event when not activated")
    void ignoresWhenNotActivated() {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, null, false);
      // NOT activated

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));
      assertNull(captured.get());
    }

    @Test
    @DisplayName("ignores event after deactivation")
    void ignoresAfterDeactivation() {
      setupDefaultPluginConfig();

      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      PingTrigger trigger = createTrigger(null, null, false);
      trigger.activate(
          "rule1",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });
      trigger.deactivate();

      trigger.onProxyPing(mockPingEvent("play.myserver.com"));
      assertNull(captured.get(), "should not fire after deactivation");
    }
  }
}
