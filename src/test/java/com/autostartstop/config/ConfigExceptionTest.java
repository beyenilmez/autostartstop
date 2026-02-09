package com.autostartstop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigException")
class ConfigExceptionTest {

    @Test
    @DisplayName("should format message with component and parameter")
    void shouldFormatMessage() {
        ConfigException ex = new ConfigException("start", "server", "is required");
        assertEquals("start action: parameter 'server' is required", ex.getMessage());
        assertEquals("start", ex.getComponent());
        assertEquals("server", ex.getParameter());
    }

    @Test
    @DisplayName("should omit parameter section when parameter is null or empty")
    void shouldOmitParameterWhenAbsent() {
        assertEquals("start action: general error",
            new ConfigException("start", null, "general error").getMessage());
        assertEquals("start action: general error",
            new ConfigException("start", "", "general error").getMessage());
    }

    @Test
    @DisplayName("required() factory should mention 'is required'")
    void requiredFactory() {
        ConfigException ex = ConfigException.required("send_message", "message");
        assertTrue(ex.getMessage().contains("is required"));
        assertEquals("send_message", ex.getComponent());
        assertEquals("message", ex.getParameter());
    }

    @Test
    @DisplayName("invalid() factory should mention the bad value and expected format")
    void invalidFactory() {
        ConfigException ex = ConfigException.invalid("start", "delay", "abc", "a valid duration");
        assertTrue(ex.getMessage().contains("abc"));
        assertTrue(ex.getMessage().contains("a valid duration"));
    }

    @Test
    @DisplayName("should preserve cause when provided")
    void shouldPreserveCause() {
        RuntimeException cause = new RuntimeException("root");
        ConfigException ex = new ConfigException("test", "key", "broken", cause);
        assertSame(cause, ex.getCause());
    }
}
