package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionRegistry;
import com.autostartstop.action.ActionType;
import com.autostartstop.condition.ConditionEvaluator;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.config.ConditionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes nested actions repeatedly while conditions are met.
 * Supports timeout and configurable update interval.
 */
public class WhileAction implements Action {
    private static final Logger logger = Log.get(WhileAction.class);
    private static final String ACTION_NAME = "while";
    private static final Duration DEFAULT_UPDATE_INTERVAL = Duration.ofSeconds(1);
    
    private final String timeoutRaw;
    private final String updateIntervalRaw;
    private final ConditionConfig conditionConfig;
    private final List<ActionConfig> doActions;
    private final ConditionEvaluator conditionEvaluator;
    private final ActionRegistry actionRegistry;
    private final VariableResolver variableResolver;

    public WhileAction(String timeout, String updateInterval, ConditionConfig conditionConfig,
                       List<ActionConfig> doActions, ConditionEvaluator conditionEvaluator,
                       ActionRegistry actionRegistry, VariableResolver variableResolver) {
        this.timeoutRaw = timeout;
        this.updateIntervalRaw = updateInterval;
        this.conditionConfig = conditionConfig;
        this.doActions = doActions;
        this.conditionEvaluator = conditionEvaluator;
        this.actionRegistry = actionRegistry;
        this.variableResolver = variableResolver;
    }

    public static WhileAction create(ActionConfig config, ActionContext ctx) {
        Map<String, Object> rawConfig = config.getRawConfig();
        if (rawConfig == null) {
            throw new IllegalArgumentException("WhileAction requires configuration");
        }

        String timeout = config.getString("timeout");
        String updateInterval = config.getString("update_interval", "1s");
        String mode = config.getString("mode", "all");

        ConditionConfig conditionConfig = new ConditionConfig();
        conditionConfig.setMode(mode);
        
        Object checksObj = rawConfig.get("checks");
        if (checksObj instanceof List<?> checksList) {
            List<Map<String, Object>> checks = new ArrayList<>();
            for (Object check : checksList) {
                if (check instanceof Map<?, ?> checkMap) {
                    Map<String, Object> typedCheck = new HashMap<>();
                    for (Map.Entry<?, ?> entry : checkMap.entrySet()) {
                        typedCheck.put(entry.getKey().toString(), entry.getValue());
                    }
                    checks.add(typedCheck);
                }
            }
            conditionConfig.setChecks(checks);
        }

        List<ActionConfig> doActions = new ArrayList<>();
        Object doObj = rawConfig.get("do");
        if (doObj instanceof List<?> doList) {
            for (Object actionObj : doList) {
                if (actionObj instanceof Map<?, ?> actionMap) {
                    ActionConfig actionConfig = parseActionConfig(actionMap);
                    if (actionConfig != null) {
                        doActions.add(actionConfig);
                    }
                }
            }
        }

        return new WhileAction(timeout, updateInterval, conditionConfig, doActions, 
                ctx.conditionEvaluator(), ctx.actionRegistry(), ctx.variableResolver());
    }

    @SuppressWarnings("unchecked")
    private static ActionConfig parseActionConfig(Map<?, ?> actionMap) {
        if (actionMap == null || actionMap.isEmpty()) {
            return null;
        }

        ActionConfig config = new ActionConfig();
        for (Map.Entry<?, ?> entry : actionMap.entrySet()) {
            String type = entry.getKey().toString();
            config.setType(type);

            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> actionData = (Map<String, Object>) value;
                config.setRawConfig(actionData);

                if (actionData.containsKey("wait_for_completion")) {
                    Object wfc = actionData.get("wait_for_completion");
                    config.setWaitForCompletion(Boolean.parseBoolean(wfc.toString()));
                }
            }
            break;
        }
        return config;
    }

    @Override
    public ActionType getType() {
        return ActionType.WHILE;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        
        Duration timeout = variableResolver.resolveDuration(timeoutRaw, context, null);
        Duration updateInterval = variableResolver.resolveDuration(updateIntervalRaw, context, DEFAULT_UPDATE_INTERVAL);
        
        logger.debug("({}) {}: starting loop (timeout: {}, interval: {})", 
                ruleName, ACTION_NAME, 
                timeout != null ? timeout.toMillis() + "ms" : "none",
                updateInterval.toMillis() + "ms");
        
        final Duration finalTimeout = timeout;
        final Duration finalUpdateInterval = updateInterval;
        
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = finalTimeout != null && finalTimeout.toMillis() > 0 ? finalTimeout.toMillis() : Long.MAX_VALUE;
            int iterations = 0;
            boolean timedOut = false;
            
            try {
                while (true) {
                    long iterationStartTime = System.currentTimeMillis();
                    long elapsed = iterationStartTime - startTime;
                    
                    if (elapsed >= timeoutMs) {
                        logger.debug("({}) {}: timeout reached after {} iterations ({}ms)", 
                                ruleName, ACTION_NAME, iterations, elapsed);
                        timedOut = true;
                        break;
                    }
                    
                    if (!conditionEvaluator.evaluate(conditionConfig, context)) {
                        logger.debug("({}) {}: conditions no longer met after {} iterations ({}ms)", 
                                ruleName, ACTION_NAME, iterations, elapsed);
                        break;
                    }
                    
                    iterations++;
                    logger.debug("({}) {}: iteration {} ({}ms elapsed)", ruleName, ACTION_NAME, iterations, elapsed);
                    
                    if (doActions != null && !doActions.isEmpty()) {
                        for (ActionConfig actionConfig : doActions) {
                            // Calculate remaining time before timeout
                            long remainingMs = timeoutMs - (System.currentTimeMillis() - startTime);
                            if (remainingMs <= 0) {
                                logger.debug("({}) {}: timeout reached during action execution after {} iterations", 
                                        ruleName, ACTION_NAME, iterations);
                                timedOut = true;
                                break;
                            }
                            
                            Action action = actionRegistry.create(actionConfig);
                            if (action != null) {
                                try {
                                    if (actionConfig.isWaitForCompletion()) {
                                        // Use get with remaining timeout to enforce the while timeout on actions
                                        action.execute(context).get(remainingMs, TimeUnit.MILLISECONDS);
                                    } else {
                                        action.execute(context);
                                    }
                                } catch (TimeoutException e) {
                                    logger.debug("({}) {}: timeout reached while waiting for action '{}' after {} iterations", 
                                            ruleName, ACTION_NAME, actionConfig.getType(), iterations);
                                    timedOut = true;
                                    break;
                                } catch (Exception e) {
                                    logger.error("({}) {}: error in nested action '{}': {}", 
                                            ruleName, ACTION_NAME, actionConfig.getType(), e.getMessage());
                                }
                            }
                        }
                        
                        // Break outer loop if timeout was reached during action execution
                        if (timedOut) {
                            break;
                        }
                    }
                    
                    // Calculate how long this iteration took and wait only the remaining time to meet update interval
                    long iterationDuration = System.currentTimeMillis() - iterationStartTime;
                    long remainingIntervalTime = finalUpdateInterval.toMillis() - iterationDuration;
                    
                    if (remainingIntervalTime > 0) {
                        // Check timeout before waiting
                        long remainingTimeout = timeoutMs - (System.currentTimeMillis() - startTime);
                        if (remainingTimeout <= 0) {
                            logger.debug("({}) {}: timeout reached before wait after {} iterations", 
                                    ruleName, ACTION_NAME, iterations);
                            break;
                        }
                        
                        // Wait for the minimum of remaining interval time and remaining timeout
                        long waitTime = Math.min(remainingIntervalTime, remainingTimeout);
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("({}) {}: error during loop: {}", ruleName, ACTION_NAME, e.getMessage());
            }
            
            logger.debug("({}) {}: completed after {} iterations ({}ms)", 
                    ruleName, ACTION_NAME, iterations, System.currentTimeMillis() - startTime);
        });
    }

    public String getTimeoutRaw() { return timeoutRaw; }
    public String getUpdateIntervalRaw() { return updateIntervalRaw; }
    public ConditionConfig getConditionConfig() { return conditionConfig; }
    public List<ActionConfig> getDoActions() { return doActions; }
}
