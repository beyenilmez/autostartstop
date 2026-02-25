package com.autostartstop.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ServerState")
class ServerStateTest {

  @ParameterizedTest
  @EnumSource(ServerState.class)
  @DisplayName("getName() should return lowercase version of name()")
  void getNameShouldReturnLowercase(ServerState state) {
    assertEquals(state.name().toLowerCase(), state.getName());
  }

  @Test
  @DisplayName("should be able to look up by name via valueOf")
  void shouldLookUpByValueOf() {
    for (ServerState state : ServerState.values()) {
      assertEquals(state, ServerState.valueOf(state.name()));
    }
  }
}
