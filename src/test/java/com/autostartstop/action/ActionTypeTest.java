package com.autostartstop.action;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ActionType")
class ActionTypeTest {

  @ParameterizedTest
  @EnumSource(ActionType.class)
  @DisplayName("every type should have a non-empty config name and a creator")
  void everyShouldHaveNameAndCreator(ActionType type) {
    assertNotNull(type.getConfigName());
    assertFalse(type.getConfigName().isEmpty());
    assertTrue(type.hasCreator());
    assertNotNull(type.getCreator());
  }

  @ParameterizedTest
  @EnumSource(ActionType.class)
  @DisplayName("fromConfigName should round-trip for every type")
  void fromConfigNameShouldRoundTrip(ActionType type) {
    assertEquals(type, ActionType.fromConfigName(type.getConfigName()));
  }

  @Test
  @DisplayName("fromConfigName should be case insensitive")
  void fromConfigNameShouldBeCaseInsensitive() {
    ActionType type = ActionType.values()[0];
    assertEquals(type, ActionType.fromConfigName(type.getConfigName().toUpperCase()));
  }

  @Test
  @DisplayName("fromConfigName should return null for unknown or null names")
  void fromConfigNameShouldReturnNullForUnknown() {
    assertNull(ActionType.fromConfigName("nonexistent_action"));
    assertNull(ActionType.fromConfigName(null));
  }
}
