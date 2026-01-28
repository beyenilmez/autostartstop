package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.util.DurationUtil;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Action that sleeps for a specified duration.
 */
public class SleepAction implements Action {
    private static final Logger logger = Log.get(SleepAction.class);
    
    private final String duration;
    private final VariableResolver variableResolver;

    public SleepAction(String duration, VariableResolver variableResolver) {
        this.duration = duration;
        this.variableResolver = variableResolver;
    }

    /**
     * Creates a SleepAction from configuration.
     */
    public static SleepAction create(ActionConfig config, ActionContext ctx) {
        String duration = config.requireString("duration");
        return new SleepAction(duration, ctx.variableResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.SLEEP;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String resolvedDuration = variableResolver.resolve(duration, context);
        logger.debug("SleepAction: resolving duration variable '{}' -> '{}'", duration, resolvedDuration);
        
        Duration sleepDuration;
        try {
            sleepDuration = DurationUtil.parse(resolvedDuration);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("Sleeping for {} ({}ms)", resolvedDuration, sleepDuration.toMillis());

        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
                long actualDuration = System.currentTimeMillis() - startTime;
                logger.debug("SleepAction: sleep completed (actual duration: {}ms)", actualDuration);
            } catch (InterruptedException e) {
                long actualDuration = System.currentTimeMillis() - startTime;
                Thread.currentThread().interrupt();
                logger.warn("SleepAction: sleep interrupted after {}ms of {}ms planned", actualDuration, sleepDuration.toMillis());
            }
        });
    }

    public String getDuration() { return duration; }
}
