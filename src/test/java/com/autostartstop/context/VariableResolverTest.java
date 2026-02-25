package com.autostartstop.context;

import static org.junit.jupiter.api.Assertions.*;

import com.autostartstop.server.ServerState;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("VariableResolver")
class VariableResolverTest {

  private VariableResolver resolver;
  private ExecutionContext context;

  @BeforeEach
  void setUp() {
    resolver = new VariableResolver();
    context = new ExecutionContext();
  }

  @Nested
  @DisplayName("extractVariableName()")
  class ExtractVariableNameTests {

    @Test
    @DisplayName("should unwrap ${...} syntax")
    void shouldUnwrap() {
      assertEquals("server.name", VariableResolver.extractVariableName("${server.name}"));
    }

    @Test
    @DisplayName("should return input unchanged when not wrapped")
    void shouldReturnUnchanged() {
      assertEquals("plain", VariableResolver.extractVariableName("plain"));
      assertNull(VariableResolver.extractVariableName(null));
    }

    @Test
    @DisplayName("should not unwrap partial syntax")
    void shouldNotUnwrapPartial() {
      assertEquals("${incomplete", VariableResolver.extractVariableName("${incomplete"));
    }
  }

  @Nested
  @DisplayName("resolve() - string variable substitution")
  class ResolveTests {

    @Test
    @DisplayName("should substitute a single variable")
    void shouldSubstituteSingle() {
      context.setVariable("name", "Steve");
      assertEquals("Hello Steve!", resolver.resolve("Hello ${name}!", context));
    }

    @Test
    @DisplayName("should substitute multiple variables")
    void shouldSubstituteMultiple() {
      context.setVariable("first", "Hello");
      context.setVariable("second", "World");
      assertEquals("Hello World", resolver.resolve("${first} ${second}", context));
    }

    @Test
    @DisplayName("should keep unresolved placeholders as-is")
    void shouldKeepUnresolved() {
      assertEquals("${unknown}", resolver.resolve("${unknown}", context));
    }

    @Test
    @DisplayName("should resolve nested variables like ${${key}}")
    void shouldResolveNested() {
      context.setVariable("server_name", "lobby");
      context.setVariable("lobby", "Lobby Server");
      assertEquals("Lobby Server", resolver.resolve("${${server_name}}", context));
    }

    @Test
    @DisplayName("should not infinite-loop on self-referencing variables")
    void shouldNotInfiniteLoop() {
      context.setVariable("loop", "${loop}");
      assertDoesNotThrow(() -> resolver.resolve("${loop}", context));
    }

    @Test
    @DisplayName("should return null for null input and pass through plain text")
    void shouldHandleEdgeCases() {
      assertNull(resolver.resolve(null, context));
      assertEquals("plain", resolver.resolve("plain", context));
    }
  }

  @Nested
  @DisplayName("resolveDuration()")
  class ResolveDurationTests {

    @Test
    @DisplayName("should parse duration string")
    void shouldParseDuration() {
      assertEquals(Duration.ofSeconds(5), resolver.resolveDuration("5s", context, Duration.ZERO));
    }

    @Test
    @DisplayName("should resolve variable then parse duration")
    void shouldResolveVariable() {
      context.setVariable("delay", "10s");
      assertEquals(
          Duration.ofSeconds(10), resolver.resolveDuration("${delay}", context, Duration.ZERO));
    }

    @Test
    @DisplayName("should return default for invalid or blank input")
    void shouldReturnDefault() {
      assertEquals(
          Duration.ofMinutes(1),
          resolver.resolveDuration("invalid", context, Duration.ofMinutes(1)));
      assertEquals(
          Duration.ofMinutes(1), resolver.resolveDuration("", context, Duration.ofMinutes(1)));
    }
  }

  @Nested
  @DisplayName("resolveInt()")
  class ResolveIntTests {

    @Test
    @DisplayName("should parse int from string and resolve variables")
    void shouldParseAndResolve() {
      assertEquals(42, resolver.resolveIntFromString("42", context, 0));
      context.setVariable("n", "10");
      assertEquals(10, resolver.resolveIntFromString("${n}", context, 0));
    }

    @Test
    @DisplayName("should handle Number and String objects")
    void shouldHandleObjects() {
      assertEquals(42, resolver.resolveInt((Object) 42, context, 0));
      assertEquals(42, resolver.resolveInt((Object) 42.9, context, 0));
      assertEquals(42, resolver.resolveInt((Object) "42", context, 0));
      assertEquals(99, resolver.resolveInt((Object) null, context, 99));
    }
  }

  @Nested
  @DisplayName("resolveDouble()")
  class ResolveDoubleTests {

    @Test
    @DisplayName("should parse double from string")
    void shouldParse() {
      assertEquals(3.14, resolver.resolveDoubleFromString("3.14", context, 0.0), 0.001);
    }

    @Test
    @DisplayName("should handle Number objects directly")
    void shouldHandleNumber() {
      assertEquals(3.14, resolver.resolveDouble((Object) 3.14, context, 0.0), 0.001);
    }
  }

  @Nested
  @DisplayName("resolveFloatClamped()")
  class ResolveFloatClampedTests {

    @Test
    @DisplayName("should clamp to [min, max]")
    void shouldClamp() {
      assertEquals(
          0.0f, resolver.resolveFloatClampedFromString("-1.0", context, 0.5f, 0.0f, 1.0f), 0.001);
      assertEquals(
          1.0f, resolver.resolveFloatClampedFromString("5.0", context, 0.5f, 0.0f, 1.0f), 0.001);
      assertEquals(
          0.5f, resolver.resolveFloatClampedFromString("0.5", context, 0.0f, 0.0f, 1.0f), 0.001);
    }
  }

  @Nested
  @DisplayName("resolveBoolean()")
  class ResolveBooleanTests {

    @ParameterizedTest
    @ValueSource(strings = {"true", "yes", "1", "on", "TRUE", "Yes", "ON"})
    @DisplayName("should resolve truthy values")
    void shouldResolveTruthy(String input) {
      assertTrue(resolver.resolveBooleanFromString(input, context, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "no", "0", "off", "FALSE", "No", "OFF"})
    @DisplayName("should resolve falsy values")
    void shouldResolveFalsy(String input) {
      assertFalse(resolver.resolveBooleanFromString(input, context, true));
    }

    @Test
    @DisplayName("should return default for unrecognized or null input")
    void shouldReturnDefault() {
      assertTrue(resolver.resolveBooleanFromString("maybe", context, true));
      assertTrue(resolver.resolveBoolean((Object) null, context, true));
    }

    @Test
    @DisplayName("should handle Boolean objects directly")
    void shouldHandleBooleanObject() {
      assertTrue(resolver.resolveBoolean((Object) Boolean.TRUE, context, false));
      assertFalse(resolver.resolveBoolean((Object) Boolean.FALSE, context, true));
    }

    @Test
    @DisplayName("should resolve variable then parse boolean")
    void shouldResolveVariable() {
      context.setVariable("flag", "yes");
      assertTrue(resolver.resolveBooleanFromString("${flag}", context, false));
    }
  }

  @Nested
  @DisplayName("resolveEnum()")
  class ResolveEnumTests {

    @Test
    @DisplayName("should resolve uppercase enum literal")
    void shouldResolveUppercase() {
      assertEquals(
          ServerState.ONLINE,
          resolver.resolveEnum("ONLINE", context, ServerState.class, ServerState.UNKNOWN));
    }

    @Test
    @DisplayName("should return default for invalid or blank values")
    void shouldReturnDefault() {
      assertEquals(
          ServerState.UNKNOWN,
          resolver.resolveEnum("xyz", context, ServerState.class, ServerState.UNKNOWN));
      assertEquals(
          ServerState.UNKNOWN,
          resolver.resolveEnum("", context, ServerState.class, ServerState.UNKNOWN));
    }
  }

  @Nested
  @DisplayName("resolveList()")
  class ResolveListTests {

    @Test
    @DisplayName("should resolve variables inside each list element")
    void shouldResolveInList() {
      context.setVariable("name", "Steve");
      List<String> result = resolver.resolveList(List.of("Hello ${name}", "plain"), context);
      assertEquals("Hello Steve", result.get(0));
      assertEquals("plain", result.get(1));
    }

    @Test
    @DisplayName("should return empty list for null or empty input")
    void shouldReturnEmpty() {
      assertTrue(resolver.resolveList(null, context).isEmpty());
      assertTrue(resolver.resolveList(List.of(), context).isEmpty());
    }
  }
}
