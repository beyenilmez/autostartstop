package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.ConfigException;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import com.autostartstop.util.DurationUtil;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Trigger that fires on a schedule defined by a cron expression.
 * Supports multiple cron formats: UNIX (default), QUARTZ, CRON4J, SPRING, SPRING53.
 * 
 * Configuration:
 * - expression: The cron expression (required).
 * - time_zone: The time zone for the schedule (optional, defaults to UTC).
 * - format: The cron format (optional, defaults to UNIX). 
 *           Valid values: UNIX, QUARTZ, CRON4J, SPRING, SPRING53.
 * 
 * Emitted context:
 * - ${cron.expression} - The cron expression
 * - ${cron.format} - The cron format used
 * - ${cron.time_zone} - The time zone used
 * - ${cron.scheduled_time} - The scheduled execution time (ISO-8601)
 * - ${cron.actual_time} - The actual execution time (ISO-8601)
 */
public class CronTrigger implements Trigger {
    private static final Logger logger = Log.get(CronTrigger.class);

    private final ProxyServer proxy;
    private final Object plugin;
    private final String expression;
    private final String timeZoneStr;
    private final String formatStr;

    // Parsed cron state
    private Cron parsedCron;
    private CronType cronType;
    private ZoneId timeZone;
    private ExecutionTime executionTime;

    // Runtime state
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private ScheduledTask scheduledTask;
    private final Object scheduleLock = new Object();
    private boolean activated = false;
    private ZonedDateTime nextScheduledTime;

    /**
     * Creates a CronTrigger from the given configuration.
     */
    public static CronTrigger create(TriggerConfig config, TriggerContext context) {
        String expression = config.getExpression();
        String timeZone = config.getTimeZone();
        String format = config.getFormat();

        if (expression == null || expression.isBlank()) {
            throw ConfigException.required("cron", "expression");
        }

        return new CronTrigger(context.proxy(), context.plugin(), expression, timeZone, format);
    }

    public CronTrigger(ProxyServer proxy, Object plugin, String expression, String timeZoneStr, String formatStr) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.expression = expression;
        this.timeZoneStr = timeZoneStr;
        this.formatStr = formatStr;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.CRON;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;

        logger.debug("CronTrigger: activating for rule '{}' with expression '{}'", ruleName, expression);

        // Parse the cron expression
        if (!parseCronExpression()) {
            logger.error("CronTrigger: failed to parse cron expression '{}' for rule '{}'", expression, ruleName);
            return;
        }

        // Parse the time zone
        this.timeZone = parseTimeZone(timeZoneStr);
        logger.debug("CronTrigger: using time zone '{}' for rule '{}'", timeZone.getId(), ruleName);

        // Create execution time calculator
        this.executionTime = ExecutionTime.forCron(parsedCron);

        // Schedule the first execution
        if (!scheduleNextExecution()) {
            logger.error("CronTrigger: could not schedule first execution for rule '{}'", ruleName);
            return;
        }

        this.activated = true;
        logger.info("CronTrigger: activated for rule '{}' - {} cron: '{}', next execution: {}",
                ruleName, cronType.name(), expression, nextScheduledTime);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("CronTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        synchronized (scheduleLock) {
            if (scheduledTask != null) {
                scheduledTask.cancel();
                scheduledTask = null;
            }
        }

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;
        this.nextScheduledTime = null;
    }

    /**
     * Parses the cron expression using the specified format.
     *
     * @return true if parsing succeeded
     */
    private boolean parseCronExpression() {
        this.cronType = parseCronType(formatStr);
        
        try {
            var cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(cronType);
            var cronParser = new CronParser(cronDefinition);
            this.parsedCron = cronParser.parse(expression);
            this.parsedCron.validate();
            logger.debug("CronTrigger: parsed expression '{}' as {} format", expression, cronType.name());
            return true;
        } catch (Exception e) {
            logger.error("CronTrigger: expression '{}' is not a valid {} cron expression: {}", 
                    expression, cronType.name(), e.getMessage());
            return false;
        }
    }

    /**
     * Parses the format string into a CronType.
     *
     * @param formatStr The format string (e.g., "unix", "quartz", "cron4j", "spring", "spring53")
     * @return The CronType, defaults to UNIX if not specified or invalid
     */
    private CronType parseCronType(String formatStr) {
        if (formatStr == null || formatStr.isBlank()) {
            return CronType.UNIX;
        }

        return switch (formatStr.toUpperCase(Locale.ROOT)) {
            case "QUARTZ" -> CronType.QUARTZ;
            case "CRON4J" -> CronType.CRON4J;
            case "SPRING" -> CronType.SPRING;
            case "SPRING53" -> CronType.SPRING53;
            case "UNIX" -> CronType.UNIX;
            default -> {
                logger.warn("CronTrigger: unknown format '{}', using UNIX", formatStr);
                yield CronType.UNIX;
            }
        };
    }

    /**
     * Parses the time zone string into a ZoneId.
     *
     * @param timeZoneStr The time zone string (e.g., "UTC+3", "Europe/Istanbul")
     * @return The parsed ZoneId, or UTC if parsing fails
     */
    private ZoneId parseTimeZone(String timeZoneStr) {
        if (timeZoneStr == null || timeZoneStr.isEmpty()) {
            return ZoneOffset.UTC;
        }

        try {
            // Try to parse as ZoneId first (e.g., "Europe/Istanbul")
            return ZoneId.of(timeZoneStr);
        } catch (Exception e) {
            try {
                // Try to parse as UTC offset (e.g., "UTC+3", "UTC-5")
                if (timeZoneStr.toUpperCase().startsWith("UTC")) {
                    String offsetStr = timeZoneStr.substring(3);
                    if (offsetStr.isEmpty()) {
                        return ZoneOffset.UTC;
                    }
                    return ZoneOffset.of(offsetStr);
                }
                // Try to parse as plain offset (e.g., "+03:00")
                return ZoneOffset.of(timeZoneStr);
            } catch (Exception e2) {
                logger.warn("CronTrigger: invalid time zone '{}', using UTC", timeZoneStr);
                return ZoneOffset.UTC;
            }
        }
    }

    /**
     * Schedules the next execution based on the cron expression.
     *
     * @return true if scheduling succeeded
     */
    private boolean scheduleNextExecution() {
        ZonedDateTime now = ZonedDateTime.now(timeZone);
        Optional<ZonedDateTime> nextExecutionOpt = executionTime.nextExecution(now);

        if (nextExecutionOpt.isEmpty()) {
            logger.warn("CronTrigger: could not determine next execution time for rule '{}' with cron '{}'",
                    ruleName, expression);
            return false;
        }

        ZonedDateTime nextExecution = nextExecutionOpt.get();
        this.nextScheduledTime = nextExecution;

        // Calculate delay until next execution
        ZonedDateTime nowInSameZone = ZonedDateTime.now(nextExecution.getZone());
        Duration delay = Duration.between(nowInSameZone, nextExecution);

        if (delay.isNegative() || delay.isZero()) {
            // If delay is negative or zero, fire immediately and reschedule
            logger.debug("CronTrigger: delay is non-positive, firing immediately for rule '{}'", ruleName);
            fireAndReschedule();
            return true;
        }

        logger.debug("CronTrigger: scheduling rule '{}' to execute at {} (in {})",
                ruleName, nextExecution, DurationUtil.format(delay));

        synchronized (scheduleLock) {
            scheduledTask = proxy.getScheduler()
                    .buildTask(plugin, this::fireAndReschedule)
                    .delay(delay)
                    .schedule();
        }

        return true;
    }

    /**
     * Fires the trigger and reschedules for the next execution.
     */
    private void fireAndReschedule() {
        if (!activated || executionCallback == null) {
            return;
        }

        ZonedDateTime scheduledTime = nextScheduledTime;
        ZonedDateTime actualTime = ZonedDateTime.now(timeZone);

        logger.debug("CronTrigger: firing for rule '{}' (scheduled: {}, actual: {})",
                ruleName, scheduledTime, actualTime);

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.CRON.getConfigName());
        context.setVariable("cron.expression", expression);
        context.setVariable("cron.format", cronType.name());
        context.setVariable("cron.time_zone", timeZone.getId());
        if (scheduledTime != null) {
            context.setVariable("cron.scheduled_time", scheduledTime.toString());
        }
        context.setVariable("cron.actual_time", actualTime.toString());

        // Invoke the execution callback (fire-and-forget for cron triggers)
        try {
            logger.debug("CronTrigger: invoking execution callback for rule '{}'", ruleName);
            executionCallback.apply(context);
        } catch (Exception e) {
            logger.error("CronTrigger: error during execution callback for rule '{}': {}", ruleName, e.getMessage());
            logger.debug("CronTrigger: execution error details:", e);
        }

        // Reschedule for next execution
        if (activated) {
            ZonedDateTime now = ZonedDateTime.now(timeZone);
            Optional<ZonedDateTime> nextExecutionOpt = executionTime.nextExecution(now);

            if (nextExecutionOpt.isEmpty()) {
                logger.warn("CronTrigger: could not determine next execution time for rule '{}'", ruleName);
                return;
            }

            ZonedDateTime nextExecution = nextExecutionOpt.get();
            this.nextScheduledTime = nextExecution;

            ZonedDateTime nowInSameZone = ZonedDateTime.now(nextExecution.getZone());
            Duration delay = Duration.between(nowInSameZone, nextExecution);

            if (delay.isNegative() || delay.isZero()) {
                // Minimum delay to prevent infinite loop
                delay = Duration.ofSeconds(1);
            }

            logger.debug("CronTrigger: rescheduling rule '{}' to execute at {} (in {})",
                    ruleName, nextExecution, DurationUtil.format(delay));

            synchronized (scheduleLock) {
                if (scheduledTask != null) {
                    scheduledTask.cancel();
                }
                scheduledTask = proxy.getScheduler()
                        .buildTask(plugin, this::fireAndReschedule)
                        .delay(delay)
                        .schedule();
            }
        }
    }

    /**
     * Gets the cron expression.
     *
     * @return The cron expression
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Gets the time zone string.
     *
     * @return The time zone string
     */
    public String getTimeZoneStr() {
        return timeZoneStr;
    }

    /**
     * Gets the format string.
     *
     * @return The format string
     */
    public String getFormatStr() {
        return formatStr;
    }

    /**
     * Gets the parsed CronType.
     *
     * @return The CronType
     */
    public CronType getCronType() {
        return cronType;
    }

    /**
     * Gets the next scheduled execution time.
     *
     * @return The next scheduled time, or null if not scheduled
     */
    public ZonedDateTime getNextScheduledTime() {
        return nextScheduledTime;
    }

    /**
     * Checks if this trigger is currently activated.
     *
     * @return true if activated
     */
    public boolean isActivated() {
        return activated;
    }
}
