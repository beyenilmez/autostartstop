package com.autostartstop.condition.impl;

import com.autostartstop.condition.ConditionType;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NumberCompareCondition")
class NumberCompareConditionTest {

    private VariableResolver resolver;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver();
        context = new ExecutionContext();
    }

    @Nested
    @DisplayName("Equals comparison")
    class EqualsTests {

        @Test
        @DisplayName("should return true when value equals target")
        void shouldReturnTrueWhenEqual() {
            NumberCompareCondition condition = new NumberCompareCondition("42", null, null, "42", resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false when value does not equal target")
        void shouldReturnFalseWhenNotEqual() {
            NumberCompareCondition condition = new NumberCompareCondition("42", null, null, "43", resolver);
            assertFalse(condition.evaluate(context));
        }

        @Test
        @DisplayName("should handle decimal equals")
        void shouldHandleDecimalEquals() {
            NumberCompareCondition condition = new NumberCompareCondition("3.14", null, null, "3.14", resolver);
            assertTrue(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Min comparison")
    class MinTests {

        @Test
        @DisplayName("should return true when value above min")
        void shouldReturnTrueAboveMin() {
            NumberCompareCondition condition = new NumberCompareCondition("10", "5", null, null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return true when value equals min")
        void shouldReturnTrueAtMin() {
            NumberCompareCondition condition = new NumberCompareCondition("5", "5", null, null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false when value below min")
        void shouldReturnFalseBelowMin() {
            NumberCompareCondition condition = new NumberCompareCondition("3", "5", null, null, resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Max comparison")
    class MaxTests {

        @Test
        @DisplayName("should return true when value below max")
        void shouldReturnTrueBelowMax() {
            NumberCompareCondition condition = new NumberCompareCondition("5", null, "10", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return true when value equals max")
        void shouldReturnTrueAtMax() {
            NumberCompareCondition condition = new NumberCompareCondition("10", null, "10", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false when value above max")
        void shouldReturnFalseAboveMax() {
            NumberCompareCondition condition = new NumberCompareCondition("15", null, "10", null, resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Range comparison (min and max)")
    class RangeTests {

        @Test
        @DisplayName("should return true when value is within range")
        void shouldReturnTrueInRange() {
            NumberCompareCondition condition = new NumberCompareCondition("7", "5", "10", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return true when value equals min boundary")
        void shouldReturnTrueAtMinBoundary() {
            NumberCompareCondition condition = new NumberCompareCondition("5", "5", "10", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return true when value equals max boundary")
        void shouldReturnTrueAtMaxBoundary() {
            NumberCompareCondition condition = new NumberCompareCondition("10", "5", "10", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false when value below min of range")
        void shouldReturnFalseBelowRange() {
            NumberCompareCondition condition = new NumberCompareCondition("3", "5", "10", null, resolver);
            assertFalse(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false when value above max of range")
        void shouldReturnFalseAboveRange() {
            NumberCompareCondition condition = new NumberCompareCondition("15", "5", "10", null, resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Variable resolution")
    class VariableResolutionTests {

        @Test
        @DisplayName("should resolve value variable")
        void shouldResolveValue() {
            context.setVariable("count", "10");
            NumberCompareCondition condition = new NumberCompareCondition("${count}", "5", "15", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should resolve min variable")
        void shouldResolveMin() {
            context.setVariable("min_val", "5");
            NumberCompareCondition condition = new NumberCompareCondition("10", "${min_val}", null, null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should resolve max variable")
        void shouldResolveMax() {
            context.setVariable("max_val", "20");
            NumberCompareCondition condition = new NumberCompareCondition("10", null, "${max_val}", null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should resolve equals variable")
        void shouldResolveEquals() {
            context.setVariable("expected", "42");
            NumberCompareCondition condition = new NumberCompareCondition("42", null, null, "${expected}", resolver);
            assertTrue(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Equals priority")
    class EqualsPriorityTests {

        @Test
        @DisplayName("equals should take priority over min/max")
        void equalsShouldTakePriority() {
            // Value is 42, equals is 42, but min is 100 (would fail if checked)
            // Equals check happens first and returns true
            NumberCompareCondition condition = new NumberCompareCondition("42", "100", "50", "42", resolver);
            assertTrue(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("No constraints")
    class NoConstraintsTests {

        @Test
        @DisplayName("should return true when only value is provided")
        void shouldReturnTrueNoConstraints() {
            NumberCompareCondition condition = new NumberCompareCondition("42", null, null, null, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return true with blank constraints")
        void shouldReturnTrueBlankConstraints() {
            NumberCompareCondition condition = new NumberCompareCondition("42", "  ", "  ", "  ", resolver);
            assertTrue(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Invalid values")
    class InvalidValueTests {

        @Test
        @DisplayName("should return false for non-numeric value")
        void shouldReturnFalseForNonNumeric() {
            NumberCompareCondition condition = new NumberCompareCondition("abc", "0", "100", null, resolver);
            assertFalse(condition.evaluate(context));
        }

        @Test
        @DisplayName("should handle unresolvable value variable")
        void shouldHandleUnresolvableValue() {
            NumberCompareCondition condition = new NumberCompareCondition("${unknown}", null, null, "42", resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Properties")
    class PropertyTests {

        @Test
        @DisplayName("should return correct type")
        void shouldReturnType() {
            NumberCompareCondition condition = new NumberCompareCondition("1", null, null, null, resolver);
            assertEquals(ConditionType.NUMBER_COMPARE, condition.getType());
        }

        @Test
        @DisplayName("should return configured properties")
        void shouldReturnProperties() {
            NumberCompareCondition condition = new NumberCompareCondition("10", "5", "20", "15", resolver);
            assertEquals("10", condition.getValue());
            assertEquals("5", condition.getMin());
            assertEquals("20", condition.getMax());
            assertEquals("15", condition.getEquals());
        }
    }
}
