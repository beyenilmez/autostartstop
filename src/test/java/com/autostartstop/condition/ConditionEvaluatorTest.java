package com.autostartstop.condition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autostartstop.config.ConditionConfig;
import com.autostartstop.context.ExecutionContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ConditionEvaluator")
@ExtendWith(MockitoExtension.class)
class ConditionEvaluatorTest {

  @Mock private ConditionRegistry conditionRegistry;

  private ConditionEvaluator evaluator;
  private ExecutionContext context;

  @BeforeEach
  void setUp() {
    evaluator = new ConditionEvaluator(conditionRegistry);
    context = new ExecutionContext();
  }

  @Nested
  @DisplayName("Null/empty conditions")
  class NullEmptyTests {

    @Test
    @DisplayName("should return true for null config")
    void shouldReturnTrueForNull() {
      assertTrue(evaluator.evaluate(null, context));
    }

    @Test
    @DisplayName("should return true for empty config")
    void shouldReturnTrueForEmpty() {
      ConditionConfig config = new ConditionConfig();
      // checks is null, isEmpty() returns true
      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should return true for config with null checks")
    void shouldReturnTrueForNullChecks() {
      ConditionConfig config = new ConditionConfig();
      config.setChecks(null);
      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should return true for config with empty checks list")
    void shouldReturnTrueForEmptyChecks() {
      ConditionConfig config = new ConditionConfig();
      config.setChecks(List.of());
      assertTrue(evaluator.evaluate(config, context));
    }
  }

  @Nested
  @DisplayName("ALL mode (default)")
  class AllModeTests {

    @Test
    @DisplayName("should return true when all conditions pass")
    void shouldReturnTrueWhenAllPass() {
      Condition trueCondition = mockCondition(true);
      when(conditionRegistry.create(any())).thenReturn(trueCondition);

      ConditionConfig config = configWithChecks("all", 3);
      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should return false when one condition fails")
    void shouldReturnFalseWhenOneFails() {
      Condition trueCondition = mockCondition(true);
      Condition falseCondition = mockCondition(false);

      when(conditionRegistry.create(any())).thenReturn(trueCondition).thenReturn(falseCondition);

      ConditionConfig config = configWithChecks("all", 2);
      assertFalse(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should short-circuit on first failure in ALL mode")
    void shouldShortCircuitOnFailure() {
      Condition falseCondition = mockCondition(false);
      Condition trueCondition = mock(Condition.class); // Don't stub evaluate

      when(conditionRegistry.create(any())).thenReturn(falseCondition).thenReturn(trueCondition);

      ConditionConfig config = configWithChecks("all", 2);
      assertFalse(evaluator.evaluate(config, context));
      // Second condition's evaluate should not be called
      verify(trueCondition, never()).evaluate(any());
    }

    @Test
    @DisplayName("should default to ALL mode when mode not specified")
    void shouldDefaultToAllMode() {
      Condition trueCondition = mockCondition(true);
      Condition falseCondition = mockCondition(false);

      when(conditionRegistry.create(any())).thenReturn(trueCondition).thenReturn(falseCondition);

      ConditionConfig config = configWithChecks(null, 2);
      assertFalse(evaluator.evaluate(config, context));
    }
  }

  @Nested
  @DisplayName("ANY mode")
  class AnyModeTests {

    @Test
    @DisplayName("should return true when any condition passes")
    void shouldReturnTrueWhenAnyPasses() {
      Condition falseCondition = mockCondition(false);
      Condition trueCondition = mockCondition(true);

      when(conditionRegistry.create(any())).thenReturn(falseCondition).thenReturn(trueCondition);

      ConditionConfig config = configWithChecks("any", 2);
      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should return false when no conditions pass")
    void shouldReturnFalseWhenNonePass() {
      Condition falseCondition = mockCondition(false);
      when(conditionRegistry.create(any())).thenReturn(falseCondition);

      ConditionConfig config = configWithChecks("any", 3);
      assertFalse(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should short-circuit on first success in ANY mode")
    void shouldShortCircuitOnSuccess() {
      Condition trueCondition = mockCondition(true);
      Condition falseCondition = mock(Condition.class); // Don't stub evaluate

      when(conditionRegistry.create(any())).thenReturn(trueCondition).thenReturn(falseCondition);

      ConditionConfig config = configWithChecks("any", 2);
      assertTrue(evaluator.evaluate(config, context));
      // Second condition's evaluate should not be called
      verify(falseCondition, never()).evaluate(any());
    }
  }

  @Nested
  @DisplayName("Condition creation failure")
  class CreationFailureTests {

    @Test
    @DisplayName("should skip conditions that fail to create")
    void shouldSkipFailedCreations() {
      Condition trueCondition = mockCondition(true);

      when(conditionRegistry.create(any()))
          .thenReturn(null) // First creation fails
          .thenReturn(trueCondition); // Second succeeds

      ConditionConfig config = configWithChecks("all", 2);
      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should return true in ALL mode when all conditions fail to create")
    void shouldReturnTrueWhenAllFailToCreate() {
      when(conditionRegistry.create(any())).thenReturn(null);

      ConditionConfig config = configWithChecks("all", 2);
      // All skipped, returns isAllMode (true for "all")
      assertTrue(evaluator.evaluate(config, context));
    }
  }

  @Nested
  @DisplayName("Invert flag")
  class InvertTests {

    @Test
    @DisplayName("should invert condition result when invert is true")
    void shouldInvertResult() {
      Condition trueCondition = mockCondition(true);
      when(conditionRegistry.create(any())).thenReturn(trueCondition);

      // Create config with invert flag
      Map<String, Object> check = new HashMap<>();
      Map<String, Object> params = new HashMap<>();
      params.put("invert", "true");
      check.put("string_equals", params);

      ConditionConfig config = new ConditionConfig();
      config.setMode("all");
      config.setChecks(List.of(check));

      assertFalse(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should not invert when invert is false")
    void shouldNotInvertWhenFalse() {
      Condition trueCondition = mockCondition(true);
      when(conditionRegistry.create(any())).thenReturn(trueCondition);

      Map<String, Object> check = new HashMap<>();
      Map<String, Object> params = new HashMap<>();
      params.put("invert", "false");
      check.put("string_equals", params);

      ConditionConfig config = new ConditionConfig();
      config.setMode("all");
      config.setChecks(List.of(check));

      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should not invert when no invert flag")
    void shouldNotInvertWhenAbsent() {
      Condition trueCondition = mockCondition(true);
      when(conditionRegistry.create(any())).thenReturn(trueCondition);

      Map<String, Object> check = new HashMap<>();
      Map<String, Object> params = new HashMap<>();
      params.put("value", "test");
      check.put("string_equals", params);

      ConditionConfig config = new ConditionConfig();
      config.setMode("all");
      config.setChecks(List.of(check));

      assertTrue(evaluator.evaluate(config, context));
    }

    @Test
    @DisplayName("should invert false condition to true")
    void shouldInvertFalseToTrue() {
      Condition falseCondition = mockCondition(false);
      when(conditionRegistry.create(any())).thenReturn(falseCondition);

      Map<String, Object> check = new HashMap<>();
      Map<String, Object> params = new HashMap<>();
      params.put("invert", "true");
      check.put("number_compare", params);

      ConditionConfig config = new ConditionConfig();
      config.setMode("all");
      config.setChecks(List.of(check));

      assertTrue(evaluator.evaluate(config, context));
    }
  }

  // ========== Helper Methods ==========

  private Condition mockCondition(boolean result) {
    Condition condition = mock(Condition.class);
    when(condition.evaluate(any())).thenReturn(result);
    ConditionType type = ConditionType.STRING_EQUALS;
    when(condition.getType()).thenReturn(type);
    return condition;
  }

  private ConditionConfig configWithChecks(String mode, int numChecks) {
    ConditionConfig config = new ConditionConfig();
    if (mode != null) {
      config.setMode(mode);
    }
    List<Map<String, Object>> checks = new ArrayList<>();
    for (int i = 0; i < numChecks; i++) {
      Map<String, Object> check = new HashMap<>();
      check.put("string_equals_" + i, Map.of("value", "test"));
      checks.add(check);
    }
    config.setChecks(checks);
    return config;
  }
}
