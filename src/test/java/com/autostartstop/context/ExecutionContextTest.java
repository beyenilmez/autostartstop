package com.autostartstop.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExecutionContext")
class ExecutionContextTest {

    @Test
    @DisplayName("each context should have a unique execution ID")
    void shouldGenerateUniqueId() {
        ExecutionContext ctx1 = new ExecutionContext();
        ExecutionContext ctx2 = new ExecutionContext();
        assertNotEquals(ctx1.getExecutionId(), ctx2.getExecutionId());
    }

    @Nested
    @DisplayName("Copy constructor")
    class CopyConstructorTests {

        @Test
        @DisplayName("should copy parent variables and merge additional vars")
        void shouldCopyAndMerge() {
            ExecutionContext parent = new ExecutionContext();
            parent.setVariable("parent_key", "parent_value");

            ExecutionContext child = new ExecutionContext(parent, Map.of("child_key", "child_value"));
            assertEquals("parent_value", child.getVariable("parent_key"));
            assertEquals("child_value", child.getVariable("child_key"));
        }

        @Test
        @DisplayName("additional vars should override parent vars with same key")
        void shouldOverrideParentVars() {
            ExecutionContext parent = new ExecutionContext();
            parent.setVariable("key", "parent");
            ExecutionContext child = new ExecutionContext(parent, Map.of("key", "child"));
            assertEquals("child", child.getVariable("key"));
        }

        @Test
        @DisplayName("child modifications should not affect parent")
        void childShouldNotAffectParent() {
            ExecutionContext parent = new ExecutionContext();
            parent.setVariable("key", "original");
            ExecutionContext child = new ExecutionContext(parent, null);
            child.setVariable("key", "modified");
            assertEquals("original", parent.getVariable("key"));
        }

        @Test
        @DisplayName("child execution ID should derive from parent")
        void childIdShouldDeriveFromParent() {
            ExecutionContext parent = new ExecutionContext();
            ExecutionContext child = new ExecutionContext(parent, null);
            assertTrue(child.getExecutionId().startsWith(parent.getExecutionId() + "-"));
        }
    }

    @Nested
    @DisplayName("getVariables()")
    class GetVariablesTests {

        @Test
        @DisplayName("should return an unmodifiable snapshot")
        void shouldReturnUnmodifiableSnapshot() {
            ExecutionContext ctx = new ExecutionContext();
            ctx.setVariable("key", "value");
            Map<String, Object> vars = ctx.getVariables();
            assertThrows(UnsupportedOperationException.class, () -> vars.put("new", "val"));
        }
    }

    @Nested
    @DisplayName("Event release signal")
    class EventReleaseSignalTests {

        @Test
        @DisplayName("signal should not exist until first requested")
        void shouldNotExistInitially() {
            ExecutionContext ctx = new ExecutionContext();
            assertFalse(ctx.hasEventReleaseSignal());
        }

        @Test
        @DisplayName("getOrCreate should always return the same future instance")
        void shouldReturnSameFuture() {
            ExecutionContext ctx = new ExecutionContext();
            CompletableFuture<Void> s1 = ctx.getOrCreateEventReleaseSignal();
            CompletableFuture<Void> s2 = ctx.getOrCreateEventReleaseSignal();
            assertSame(s1, s2);
            assertTrue(ctx.hasEventReleaseSignal());
        }

        @Test
        @DisplayName("releaseEvent should complete the signal")
        void releaseEventShouldComplete() {
            ExecutionContext ctx = new ExecutionContext();
            CompletableFuture<Void> signal = ctx.getOrCreateEventReleaseSignal();
            assertFalse(signal.isDone());
            ctx.releaseEvent();
            assertTrue(signal.isDone());
        }

        @Test
        @DisplayName("releaseEvent should be safe when no signal exists")
        void releaseEventShouldBeSafeWithoutSignal() {
            ExecutionContext ctx = new ExecutionContext();
            assertDoesNotThrow(ctx::releaseEvent);
        }
    }
}
