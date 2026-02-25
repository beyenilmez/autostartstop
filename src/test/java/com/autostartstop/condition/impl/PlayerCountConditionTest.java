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
@DisplayName("PlayerCountCondition")
class PlayerCountConditionTest {

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
      assertThrows(ConfigException.class, () -> PlayerCountCondition.create(Map.of(), ctx));
    }

    @Test
    @DisplayName("create succeeds with server only (no constraints)")
    void createSucceedsWithServerOnly() {
      ConditionContext ctx =
          ConditionContext.builder()
              .serverManager(serverManager)
              .variableResolver(resolver)
              .build();
      PlayerCountCondition condition = PlayerCountCondition.create(Map.of("server", "lobby"), ctx);

      assertEquals("lobby", condition.getServer());
      assertNull(condition.getMin());
      assertNull(condition.getMax());
      assertNull(condition.getEquals());
    }

    @Test
    @DisplayName("create parses all constraint fields")
    void createParsesAllFields() {
      ConditionContext ctx =
          ConditionContext.builder()
              .serverManager(serverManager)
              .variableResolver(resolver)
              .build();
      PlayerCountCondition condition =
          PlayerCountCondition.create(
              Map.of("server", "lobby", "min", "5", "max", "20", "equals", "10"), ctx);

      assertEquals("lobby", condition.getServer());
      assertEquals("5", condition.getMin());
      assertEquals("20", condition.getMax());
      assertEquals("10", condition.getEquals());
    }
  }

  // ========== Type ==========

  @Test
  @DisplayName("getType returns PLAYER_COUNT")
  void getTypeReturnsPlayerCount() {
    PlayerCountCondition condition =
        new PlayerCountCondition("lobby", null, null, null, serverManager, resolver);
    assertEquals(ConditionType.PLAYER_COUNT, condition.getType());
  }

  // ========== Equals ==========

  @Nested
  @DisplayName("Equals constraint")
  class EqualsConstraint {

    @Test
    @DisplayName("true when player count matches equals")
    void trueWhenMatches() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(10);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, null, "10", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when player count does not match equals")
    void falseWhenNotMatches() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(5);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, null, "10", serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }

    @Test
    @DisplayName("equals takes priority over min/max")
    void equalsTakesPriority() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(10);
      // min=0, max=100 would pass, but equals=5 should fail
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "0", "100", "5", serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }

    @Test
    @DisplayName("equals zero checks for empty server")
    void equalsZero() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(0);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, null, "0", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }
  }

  // ========== Min constraint ==========

  @Nested
  @DisplayName("Min constraint")
  class MinConstraint {

    @Test
    @DisplayName("true when player count is above min")
    void trueWhenAboveMin() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(10);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "5", null, null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("true when player count equals min")
    void trueWhenEqualsMin() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(5);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "5", null, null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when player count is below min")
    void falseWhenBelowMin() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(3);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "5", null, null, serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }
  }

  // ========== Max constraint ==========

  @Nested
  @DisplayName("Max constraint")
  class MaxConstraint {

    @Test
    @DisplayName("true when player count is below max")
    void trueWhenBelowMax() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(10);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, "20", null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("true when player count equals max")
    void trueWhenEqualsMax() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(20);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, "20", null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when player count is above max")
    void falseWhenAboveMax() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(25);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, "20", null, serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }
  }

  // ========== Range (min + max) ==========

  @Nested
  @DisplayName("Range constraint (min + max)")
  class RangeConstraint {

    @Test
    @DisplayName("true when player count is within range")
    void trueWhenWithinRange() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(10);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "5", "20", null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when player count is below range")
    void falseWhenBelowRange() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(2);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "5", "20", null, serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }

    @Test
    @DisplayName("false when player count is above range")
    void falseWhenAboveRange() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(25);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "5", "20", null, serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }

    @Test
    @DisplayName("true at range boundaries (inclusive)")
    void trueAtBoundaries() {
      // At min boundary
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(5);
      PlayerCountCondition atMin =
          new PlayerCountCondition("lobby", "5", "20", null, serverManager, resolver);
      assertTrue(atMin.evaluate(context), "should pass at min boundary");

      // At max boundary
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(20);
      PlayerCountCondition atMax =
          new PlayerCountCondition("lobby", "5", "20", null, serverManager, resolver);
      assertTrue(atMax.evaluate(context), "should pass at max boundary");
    }
  }

  // ========== No constraints ==========

  @Nested
  @DisplayName("No constraints")
  class NoConstraints {

    @Test
    @DisplayName("true when no constraints are specified")
    void trueWithNoConstraints() {
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(42);
      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, null, null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }
  }

  // ========== Variable Resolution ==========

  @Nested
  @DisplayName("Variable resolution")
  class VariableResolution {

    @Test
    @DisplayName("resolves server name from context variable")
    void resolvesServerName() {
      context.setVariable("target_server", "survival");
      when(serverManager.getServerPlayerCount("survival")).thenReturn(10);

      PlayerCountCondition condition =
          new PlayerCountCondition("${target_server}", "5", null, null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
      verify(serverManager).getServerPlayerCount("survival");
    }

    @Test
    @DisplayName("resolves min from context variable")
    void resolvesMinFromVariable() {
      context.setVariable("min_players", "3");
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(5);

      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", "${min_players}", null, null, serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }

    @Test
    @DisplayName("resolves max from context variable")
    void resolvesMaxFromVariable() {
      context.setVariable("max_players", "10");
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(15);

      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, "${max_players}", null, serverManager, resolver);
      assertFalse(condition.evaluate(context));
    }

    @Test
    @DisplayName("resolves equals from context variable")
    void resolvesEqualsFromVariable() {
      context.setVariable("exact_count", "7");
      when(serverManager.getServerPlayerCount("lobby")).thenReturn(7);

      PlayerCountCondition condition =
          new PlayerCountCondition("lobby", null, null, "${exact_count}", serverManager, resolver);
      assertTrue(condition.evaluate(context));
    }
  }
}
