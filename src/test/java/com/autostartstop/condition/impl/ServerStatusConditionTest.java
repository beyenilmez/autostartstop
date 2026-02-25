package com.autostartstop.condition.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.autostartstop.condition.ConditionContext;
import com.autostartstop.condition.ConditionType;
import com.autostartstop.config.ConfigException;
import com.autostartstop.context.ExecutionContext;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ServerStatusCondition")
class ServerStatusConditionTest {

  @Mock ServerManager serverManager;

  private VariableResolver resolver;
  private ExecutionContext context;

  @BeforeEach
  void setUp() {
    resolver = new VariableResolver();
    context = new ExecutionContext();
  }

  // ========== Factory ==========

  @Nested
  @DisplayName("Factory")
  class Factory {

    @Test
    @DisplayName("create throws when server is missing")
    void createThrowsWhenServerMissing() {
      ConditionContext ctx =
          ConditionContext.builder()
              .serverManager(serverManager)
              .variableResolver(resolver)
              .build();
      assertThrows(
          ConfigException.class,
          () -> ServerStatusCondition.create(Map.of("status", "online"), ctx));
    }

    @Test
    @DisplayName("create throws when status is missing")
    void createThrowsWhenStatusMissing() {
      ConditionContext ctx =
          ConditionContext.builder()
              .serverManager(serverManager)
              .variableResolver(resolver)
              .build();
      assertThrows(
          ConfigException.class,
          () -> ServerStatusCondition.create(Map.of("server", "lobby"), ctx));
    }

    @Test
    @DisplayName("create succeeds with server and status")
    void createSucceeds() {
      ConditionContext ctx =
          ConditionContext.builder()
              .serverManager(serverManager)
              .variableResolver(resolver)
              .build();
      ServerStatusCondition condition =
          ServerStatusCondition.create(Map.of("server", "lobby", "status", "online"), ctx);

      assertEquals("lobby", condition.getServer());
      assertEquals("online", condition.getExpectedStatus());
    }
  }

  // ========== Type ==========

  @Test
  @DisplayName("getType returns SERVER_STATUS")
  void getTypeReturnsServerStatus() {
    ServerStatusCondition condition =
        new ServerStatusCondition("lobby", "online", serverManager, resolver);
    assertEquals(ConditionType.SERVER_STATUS, condition.getType());
  }

  // ========== Online Checks ==========

  @Nested
  @DisplayName("Online status checks")
  class OnlineChecks {

    @Test
    @DisplayName("true when expecting online and server is online")
    void trueWhenOnlineExpectedAndOnline() {
      when(serverManager.isServerOnline("lobby")).thenReturn(true);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "online", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when expecting online and server is offline")
    void falseWhenOnlineExpectedAndOffline() {
      when(serverManager.isServerOnline("lobby")).thenReturn(false);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "online", serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }
  }

  // ========== Offline Checks ==========

  @Nested
  @DisplayName("Offline status checks")
  class OfflineChecks {

    @Test
    @DisplayName("true when expecting offline and server is offline")
    void trueWhenOfflineExpectedAndOffline() {
      when(serverManager.isServerOnline("lobby")).thenReturn(false);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "offline", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when expecting offline and server is online")
    void falseWhenOfflineExpectedAndOnline() {
      when(serverManager.isServerOnline("lobby")).thenReturn(true);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "offline", serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }
  }

  // ========== Case Insensitive ==========

  @Nested
  @DisplayName("Case insensitive comparison")
  class CaseInsensitive {

    @Test
    @DisplayName("accepts ONLINE uppercase")
    void acceptsOnlineUppercase() {
      when(serverManager.isServerOnline("lobby")).thenReturn(true);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "ONLINE", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("accepts Offline mixed case")
    void acceptsOfflineMixedCase() {
      when(serverManager.isServerOnline("lobby")).thenReturn(false);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "Offline", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }
  }

  // ========== Unknown Status ==========

  @Nested
  @DisplayName("Unknown status value")
  class UnknownStatus {

    @Test
    @DisplayName("false for unrecognized status string")
    void falseForUnknownStatus() {
      when(serverManager.isServerOnline("lobby")).thenReturn(true);
      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "starting", serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }
  }

  // ========== Variable Resolution ==========

  @Nested
  @DisplayName("Variable resolution")
  class VariableResolution {

    @Test
    @DisplayName("resolves server name from context variable")
    void resolvesServerName() {
      context.setVariable("target", "survival");
      when(serverManager.isServerOnline("survival")).thenReturn(true);

      ServerStatusCondition condition =
          new ServerStatusCondition("${target}", "online", serverManager, resolver);
      assertTrue(condition.evaluate(context));
      verify(serverManager).isServerOnline("survival");
    }

    @Test
    @DisplayName("resolves expected status from context variable")
    void resolvesStatus() {
      context.setVariable("expected_status", "offline");
      when(serverManager.isServerOnline("lobby")).thenReturn(false);

      ServerStatusCondition condition =
          new ServerStatusCondition("lobby", "${expected_status}", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("resolves both server and status from variables")
    void resolvesBoth() {
      context.setVariable("srv", "creative");
      context.setVariable("sts", "online");
      when(serverManager.isServerOnline("creative")).thenReturn(true);

      ServerStatusCondition condition =
          new ServerStatusCondition("${srv}", "${sts}", serverManager, resolver);
      assertTrue(condition.evaluate(context));
      verify(serverManager).isServerOnline("creative");
    }
  }
}
