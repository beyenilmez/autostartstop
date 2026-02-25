package com.autostartstop.trigger.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.autostartstop.config.ConfigException;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import com.cronutils.model.CronType;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CronTrigger")
class CronTriggerTest {

  @Mock ProxyServer proxy;
  @Mock Scheduler scheduler;
  @Mock Scheduler.TaskBuilder taskBuilder;
  @Mock ScheduledTask scheduledTask;

  private final Object plugin = new Object();

  @BeforeEach
  void setUp() {
    lenient().when(proxy.getScheduler()).thenReturn(scheduler);
    lenient().when(scheduler.buildTask(eq(plugin), any(Runnable.class))).thenReturn(taskBuilder);
    lenient().when(taskBuilder.delay(any())).thenReturn(taskBuilder);
    lenient().when(taskBuilder.schedule()).thenReturn(scheduledTask);
  }

  // ========== Factory ==========

  @Nested
  @DisplayName("Factory")
  class Factory {

    @Test
    @DisplayName("create throws when expression is missing")
    void createThrowsWhenExpressionMissing() {
      TriggerConfig config = new TriggerConfig();
      config.setType("cron");
      config.setRawConfig(Map.of());

      TriggerContext ctx = TriggerContext.builder().proxy(proxy).plugin(plugin).build();
      assertThrows(ConfigException.class, () -> CronTrigger.create(config, ctx));
    }

    @Test
    @DisplayName("create throws when expression is blank")
    void createThrowsWhenExpressionBlank() {
      TriggerConfig config = new TriggerConfig();
      config.setType("cron");
      config.setRawConfig(Map.of("expression", "   "));

      TriggerContext ctx = TriggerContext.builder().proxy(proxy).plugin(plugin).build();
      assertThrows(ConfigException.class, () -> CronTrigger.create(config, ctx));
    }

    @Test
    @DisplayName("create succeeds with valid expression")
    void createSucceedsWithValidExpression() {
      TriggerConfig config = new TriggerConfig();
      config.setType("cron");
      config.setRawConfig(Map.of("expression", "0 * * * *"));

      TriggerContext ctx = TriggerContext.builder().proxy(proxy).plugin(plugin).build();
      CronTrigger trigger = CronTrigger.create(config, ctx);

      assertEquals("0 * * * *", trigger.getExpression());
    }

    @Test
    @DisplayName("create preserves optional time_zone and format")
    void createPreservesOptionalFields() {
      TriggerConfig config = new TriggerConfig();
      config.setType("cron");
      config.setRawConfig(
          Map.of("expression", "0 * * * *", "time_zone", "Europe/Istanbul", "format", "QUARTZ"));

      TriggerContext ctx = TriggerContext.builder().proxy(proxy).plugin(plugin).build();
      CronTrigger trigger = CronTrigger.create(config, ctx);

      assertEquals("Europe/Istanbul", trigger.getTimeZoneStr());
      assertEquals("QUARTZ", trigger.getFormatStr());
    }
  }

  // ========== Activation ==========

  @Nested
  @DisplayName("Activation")
  class Activation {

    @Test
    @DisplayName("activate with valid UNIX cron expression schedules task")
    void activateWithValidUnixExpression() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "*/5 * * * *", null, null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      assertTrue(trigger.isActivated());
      assertNotNull(trigger.getNextScheduledTime());
      assertEquals(CronType.UNIX, trigger.getCronType());
    }

    @Test
    @DisplayName("activate with invalid expression does not activate")
    void activateWithInvalidExpressionDoesNotActivate() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "not a cron", null, null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));

      assertFalse(trigger.isActivated());
    }

    @Test
    @DisplayName("deactivate cancels scheduled task")
    void deactivateCancelsTask() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "*/5 * * * *", null, null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      trigger.deactivate();

      assertFalse(trigger.isActivated());
      assertNull(trigger.getNextScheduledTime());
      verify(scheduledTask).cancel();
    }

    @Test
    @DisplayName("deactivate is idempotent when not activated")
    void deactivateIdempotent() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "*/5 * * * *", null, null);
      trigger.deactivate(); // should not throw
    }
  }

  // ========== Format Parsing ==========

  @Nested
  @DisplayName("Format parsing")
  class FormatParsing {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("defaults to UNIX when format is null/empty/blank")
    void defaultsToUnix(String format) {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", null, format);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertEquals(CronType.UNIX, trigger.getCronType());
    }

    @ParameterizedTest
    @CsvSource({
      "UNIX, UNIX",
      "unix, UNIX",
      "QUARTZ, QUARTZ",
      "quartz, QUARTZ",
      "CRON4J, CRON4J",
      "cron4j, CRON4J",
      "SPRING, SPRING",
      "spring, SPRING",
      "SPRING53, SPRING53",
      "spring53, SPRING53"
    })
    @DisplayName("parses known format strings case-insensitively")
    void parsesKnownFormats(String input, String expectedName) {
      // Use matching valid expression for each format
      String expression =
          switch (expectedName) {
            case "QUARTZ" -> "0 0 * * * ?";
            case "CRON4J" -> "0 * * * *";
            case "SPRING" -> "0 0 * * * *";
            case "SPRING53" -> "0 0 * * * *";
            default -> "0 * * * *"; // UNIX
          };
      CronTrigger trigger = new CronTrigger(proxy, plugin, expression, null, input);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertEquals(CronType.valueOf(expectedName), trigger.getCronType());
    }

    @Test
    @DisplayName("unknown format defaults to UNIX")
    void unknownFormatDefaultsToUnix() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", null, "INVALID_FORMAT");
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertEquals(CronType.UNIX, trigger.getCronType());
    }
  }

  // ========== Time Zone Parsing ==========

  @Nested
  @DisplayName("Time zone parsing")
  class TimeZoneParsing {

    // We test timezone indirectly via the next scheduled time zone
    @Test
    @DisplayName("valid named timezone is used")
    void validNamedTimezone() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", "Europe/Istanbul", null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertTrue(trigger.isActivated());
      assertEquals("Europe/Istanbul", trigger.getNextScheduledTime().getZone().getId());
    }

    @Test
    @DisplayName("UTC offset timezone is used")
    void utcOffsetTimezone() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", "UTC+3", null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertTrue(trigger.isActivated());
      assertEquals("UTC+03:00", trigger.getNextScheduledTime().getZone().getId());
    }

    @Test
    @DisplayName("null timezone defaults to UTC")
    void nullTimezoneDefaultsToUtc() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", null, null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertTrue(trigger.isActivated());
      assertEquals("Z", trigger.getNextScheduledTime().getZone().getId());
    }

    @Test
    @DisplayName("invalid timezone falls back to UTC")
    void invalidTimezoneFallsBackToUtc() {
      CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", "Not/A/Zone", null);
      trigger.activate("rule1", ctx -> CompletableFuture.completedFuture(null));
      assertTrue(trigger.isActivated());
      assertEquals("Z", trigger.getNextScheduledTime().getZone().getId());
    }
  }

  // ========== Context Emission ==========

  @Nested
  @DisplayName("Context emission")
  class ContextEmission {

    /**
     * Activates a CronTrigger and captures the Runnable scheduled by the Velocity scheduler, then
     * invokes it to simulate a cron fire and capture the emitted ExecutionContext.
     */
    private ExecutionContext fireAndCapture(String expression, String timeZone, String format) {
      AtomicReference<ExecutionContext> captured = new AtomicReference<>();
      ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

      CronTrigger trigger = new CronTrigger(proxy, plugin, expression, timeZone, format);
      trigger.activate(
          "test-rule",
          ctx -> {
            captured.set(ctx);
            return CompletableFuture.completedFuture(null);
          });

      assertTrue(trigger.isActivated(), "trigger should activate with expression: " + expression);

      // Capture the Runnable passed to scheduler.buildTask during activation
      verify(scheduler, atLeastOnce()).buildTask(eq(plugin), runnableCaptor.capture());

      // Invoke the captured fireAndReschedule runnable
      runnableCaptor.getValue().run();

      assertNotNull(captured.get(), "callback should have been invoked");
      return captured.get();
    }

    @Test
    @DisplayName("emits trigger type as 'cron'")
    void emitsTriggerType() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", null, null);
      assertEquals("cron", ctx.getVariable("_trigger_type"));
    }

    @Test
    @DisplayName("emits the cron expression")
    void emitsCronExpression() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", null, null);
      assertEquals("*/5 * * * *", ctx.getVariable("cron.expression"));
    }

    @Test
    @DisplayName("emits the cron format name")
    void emitsCronFormat() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", null, null);
      assertEquals("UNIX", ctx.getVariable("cron.format"));
    }

    @Test
    @DisplayName("emits QUARTZ format when configured")
    void emitsQuartzFormat() {
      ExecutionContext ctx = fireAndCapture("0 0/5 * * * ?", null, "QUARTZ");
      assertEquals("QUARTZ", ctx.getVariable("cron.format"));
    }

    @Test
    @DisplayName("emits the time zone ID")
    void emitsTimeZone() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", "Europe/Istanbul", null);
      assertEquals("Europe/Istanbul", ctx.getVariable("cron.time_zone"));
    }

    @Test
    @DisplayName("emits UTC time zone when none specified")
    void emitsUtcTimeZoneByDefault() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", null, null);
      assertEquals("Z", ctx.getVariable("cron.time_zone"));
    }

    @Test
    @DisplayName("emits scheduled_time as ISO-8601 string")
    void emitsScheduledTime() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", null, null);
      Object scheduledTime = ctx.getVariable("cron.scheduled_time");
      assertNotNull(scheduledTime, "scheduled_time should be set");
      assertInstanceOf(String.class, scheduledTime);
      // ISO-8601 format contains 'T' separator
      assertTrue(
          ((String) scheduledTime).contains("T"), "scheduled_time should be ISO-8601 format");
    }

    @Test
    @DisplayName("emits actual_time as ISO-8601 string")
    void emitsActualTime() {
      ExecutionContext ctx = fireAndCapture("*/5 * * * *", null, null);
      Object actualTime = ctx.getVariable("cron.actual_time");
      assertNotNull(actualTime, "actual_time should be set");
      assertInstanceOf(String.class, actualTime);
      assertTrue(((String) actualTime).contains("T"), "actual_time should be ISO-8601 format");
    }

    @Test
    @DisplayName("emits all context variables together")
    void emitsAllVariables() {
      ExecutionContext ctx = fireAndCapture("0 * * * *", "Europe/Istanbul", "UNIX");

      assertAll(
          () -> assertEquals("cron", ctx.getVariable("_trigger_type")),
          () -> assertEquals("0 * * * *", ctx.getVariable("cron.expression")),
          () -> assertEquals("UNIX", ctx.getVariable("cron.format")),
          () -> assertEquals("Europe/Istanbul", ctx.getVariable("cron.time_zone")),
          () -> assertNotNull(ctx.getVariable("cron.scheduled_time")),
          () -> assertNotNull(ctx.getVariable("cron.actual_time")));
    }
  }

  // ========== Type ==========

  @Test
  @DisplayName("getType returns CRON")
  void getTypeReturnsCron() {
    CronTrigger trigger = new CronTrigger(proxy, plugin, "0 * * * *", null, null);
    assertEquals(TriggerType.CRON, trigger.getType());
  }
}
