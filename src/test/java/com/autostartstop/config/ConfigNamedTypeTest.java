package com.autostartstop.config;

import com.autostartstop.action.ActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigNamedType")
class ConfigNamedTypeTest {

    @Test
    @DisplayName("fromConfigName should be case insensitive")
    void fromConfigNameCaseInsensitive() {
        assertEquals(ActionType.START, ConfigNamedType.fromConfigName(ActionType.class, "start"));
        assertEquals(ActionType.START, ConfigNamedType.fromConfigName(ActionType.class, "START"));
        assertEquals(ActionType.START, ConfigNamedType.fromConfigName(ActionType.class, "Start"));
    }

    @Test
    @DisplayName("fromConfigName should return null for null or non-matching input")
    void fromConfigNameReturnsNull() {
        assertNull(ConfigNamedType.fromConfigName(ActionType.class, null));
        assertNull(ConfigNamedType.fromConfigName(ActionType.class, "non_existent"));
    }
}
