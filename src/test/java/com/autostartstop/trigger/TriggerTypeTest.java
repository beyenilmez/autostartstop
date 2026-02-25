package com.autostartstop.trigger;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("TriggerType")
class TriggerTypeTest {

  @ParameterizedTest
  @EnumSource(TriggerType.class)
  @DisplayName("every type should have a non-empty config name and a creator")
  void everyShouldHaveNameAndCreator(TriggerType type) {
    assertNotNull(type.getConfigName());
    assertFalse(type.getConfigName().isEmpty());
    assertTrue(type.hasCreator());
    assertNotNull(type.getCreator());
  }

  @ParameterizedTest
  @EnumSource(TriggerType.class)
  @DisplayName("fromConfigName should round-trip for every type")
  void fromConfigNameShouldRoundTrip(TriggerType type) {
    assertEquals(type, TriggerType.fromConfigName(type.getConfigName()));
  }

  @Test
  @DisplayName("fromConfigName should be case insensitive")
  void fromConfigNameShouldBeCaseInsensitive() {
    TriggerType type = TriggerType.values()[0];
    assertEquals(type, TriggerType.fromConfigName(type.getConfigName().toUpperCase()));
  }

  @Test
  @DisplayName("fromConfigName should return null for unknown or null names")
  void fromConfigNameShouldReturnNullForUnknown() {
    assertNull(TriggerType.fromConfigName("nonexistent_trigger"));
    assertNull(TriggerType.fromConfigName(null));
  }
}
