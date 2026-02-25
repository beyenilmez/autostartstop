package com.autostartstop.condition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ConditionType")
class ConditionTypeTest {

  @ParameterizedTest
  @EnumSource(ConditionType.class)
  @DisplayName("every type should have a non-empty config name and a creator")
  void everyShouldHaveNameAndCreator(ConditionType type) {
    assertNotNull(type.getConfigName());
    assertFalse(type.getConfigName().isEmpty());
    assertTrue(type.hasCreator());
    assertNotNull(type.getCreator());
  }

  @ParameterizedTest
  @EnumSource(ConditionType.class)
  @DisplayName("fromConfigName should round-trip for every type")
  void fromConfigNameShouldRoundTrip(ConditionType type) {
    assertEquals(type, ConditionType.fromConfigName(type.getConfigName()));
  }

  @Test
  @DisplayName("fromConfigName should return null for unknown or null names")
  void fromConfigNameShouldReturnNullForUnknown() {
    assertNull(ConditionType.fromConfigName("nonexistent_condition"));
    assertNull(ConditionType.fromConfigName(null));
  }
}
