package com.autostartstop.condition.impl;

import com.autostartstop.condition.ConditionType;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StringEqualsCondition")
class StringEqualsConditionTest {

    private VariableResolver resolver;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver();
        context = new ExecutionContext();
    }

    @Nested
    @DisplayName("Basic comparison")
    class BasicComparisonTests {

        @Test
        @DisplayName("should return true for equal strings")
        void shouldReturnTrueForEqual() {
            StringEqualsCondition condition = new StringEqualsCondition("hello", "hello", false, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false for different strings")
        void shouldReturnFalseForDifferent() {
            StringEqualsCondition condition = new StringEqualsCondition("hello", "world", false, resolver);
            assertFalse(condition.evaluate(context));
        }

        @Test
        @DisplayName("should be case sensitive by default")
        void shouldBeCaseSensitive() {
            StringEqualsCondition condition = new StringEqualsCondition("Hello", "hello", false, resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Case insensitive comparison")
    class CaseInsensitiveTests {

        @Test
        @DisplayName("should return true for equal strings with different case")
        void shouldReturnTrueIgnoringCase() {
            StringEqualsCondition condition = new StringEqualsCondition("Hello", "hello", true, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return true for exact match with ignore case")
        void shouldReturnTrueForExactMatch() {
            StringEqualsCondition condition = new StringEqualsCondition("hello", "hello", true, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should return false for different strings even with ignore case")
        void shouldReturnFalseForDifferent() {
            StringEqualsCondition condition = new StringEqualsCondition("hello", "world", true, resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Variable resolution")
    class VariableResolutionTests {

        @Test
        @DisplayName("should resolve variables in value")
        void shouldResolveValue() {
            context.setVariable("status", "online");
            StringEqualsCondition condition = new StringEqualsCondition("${status}", "online", false, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should resolve variables in equals")
        void shouldResolveEquals() {
            context.setVariable("expected", "online");
            StringEqualsCondition condition = new StringEqualsCondition("online", "${expected}", false, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should resolve variables in both value and equals")
        void shouldResolveBoth() {
            context.setVariable("actual", "online");
            context.setVariable("expected", "online");
            StringEqualsCondition condition = new StringEqualsCondition("${actual}", "${expected}", false, resolver);
            assertTrue(condition.evaluate(context));
        }

        @Test
        @DisplayName("should handle unresolvable variables")
        void shouldHandleUnresolvable() {
            StringEqualsCondition condition = new StringEqualsCondition("${unknown}", "value", false, resolver);
            // ${unknown} won't resolve, so it stays as literal "${unknown}"
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Empty string handling")
    class EmptyStringHandlingTests {

        @Test
        @DisplayName("should return false when one resolves differently")
        void shouldReturnFalseForMismatch() {
            StringEqualsCondition condition = new StringEqualsCondition("", "value", false, resolver);
            assertFalse(condition.evaluate(context));
        }
    }

    @Nested
    @DisplayName("Properties")
    class PropertyTests {

        @Test
        @DisplayName("should return correct type")
        void shouldReturnType() {
            StringEqualsCondition condition = new StringEqualsCondition("a", "b", false, resolver);
            assertEquals(ConditionType.STRING_EQUALS, condition.getType());
        }

        @Test
        @DisplayName("should return configured value")
        void shouldReturnValue() {
            StringEqualsCondition condition = new StringEqualsCondition("value", "equals", true, resolver);
            assertEquals("value", condition.getValue());
        }

        @Test
        @DisplayName("should return configured equals")
        void shouldReturnEquals() {
            StringEqualsCondition condition = new StringEqualsCondition("value", "equals", true, resolver);
            assertEquals("equals", condition.getEquals());
        }

        @Test
        @DisplayName("should return configured ignoreCase")
        void shouldReturnIgnoreCase() {
            StringEqualsCondition condition = new StringEqualsCondition("a", "b", true, resolver);
            assertTrue(condition.isIgnoreCase());

            StringEqualsCondition condition2 = new StringEqualsCondition("a", "b", false, resolver);
            assertFalse(condition2.isIgnoreCase());
        }
    }
}
