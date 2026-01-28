package com.autostartstop.rule;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionRegistry;
import com.autostartstop.condition.ConditionEvaluator;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.*;

/**
 * Executes rules with their actions.
 * Supports parallel execution of the same rule for different contexts.
 */
public class RuleExecutor {
    private static final Logger logger = Log.get(RuleExecutor.class);
    
    private final ExecutorService executorService;
    private final ActionRegistry actionRegistry;
    private final ConditionEvaluator conditionEvaluator;

    public RuleExecutor(ActionRegistry actionRegistry, ConditionEvaluator conditionEvaluator) {
        this.actionRegistry = actionRegistry;
        this.conditionEvaluator = conditionEvaluator;

        // Use a cached thread pool for parallel execution
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "AutoStartStop-Executor-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        logger.debug("RuleExecutor initialized with cached thread pool");
    }

    /**
     * Executes a rule with the given context.
     * Each execution is independent and runs in its own thread.
     *
     * @param rule The rule to execute
     * @param context The execution context
     * @return A CompletableFuture that completes when execution is done
     */
    public CompletableFuture<Void> execute(Rule rule, ExecutionContext context) {
        String ruleName = rule.getName();
        String contextId = context.getExecutionId();
        logger.debug("Executing rule '{}' (context: {})", ruleName, contextId);
        
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // Set the rule name in context for use by actions (e.g., LogAction)
                context.setVariable("_rule_name", ruleName);
                
                logger.debug("Rule '{}': beginning execution in thread {}", ruleName, Thread.currentThread().getName());

                // Check conditions
                if (rule.hasConditions()) {
                    logger.debug("Rule '{}': evaluating {} conditions...", ruleName, 
                            rule.getConditions().getChecks() != null ? rule.getConditions().getChecks().size() : 0);
                    boolean conditionsMet = conditionEvaluator.evaluate(rule.getConditions(), context);
                    if (!conditionsMet) {
                        logger.debug("Rule '{}': conditions not met, skipping", ruleName);
                        return;
                    }
                    logger.debug("Rule '{}': all conditions met, proceeding with actions", ruleName);
                } else {
                    logger.debug("Rule '{}': no conditions defined, proceeding with actions", ruleName);
                }

                // Execute actions sequentially within this context
                List<ActionConfig> actionConfigs = rule.getActions();
                if (actionConfigs != null && !actionConfigs.isEmpty()) {
                    logger.debug("Rule '{}': executing {} actions sequentially", ruleName, actionConfigs.size());
                    int actionIndex = 0;
                    for (ActionConfig actionConfig : actionConfigs) {
                        actionIndex++;
                        logger.debug("Rule '{}': executing action {}/{} (type: {})", 
                                ruleName, actionIndex, actionConfigs.size(), actionConfig.getType());
                        executeAction(actionConfig, context);
                    }
                } else {
                    logger.warn("Rule '{}': no actions defined", ruleName);
                }

                long duration = System.currentTimeMillis() - startTime;
                logger.debug("Rule '{}' completed ({}ms)", ruleName, duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Rule '{}' execution failed after {}ms: {}", ruleName, duration, e.getMessage());
                logger.debug("Rule '{}' execution error details:", ruleName, e);
            }
        }, executorService);
    }

    /**
     * Executes a single action.
     *
     * @param actionConfig The action configuration
     * @param context The execution context
     */
    private void executeAction(ActionConfig actionConfig, ExecutionContext context) {
        String actionType = actionConfig.getType();
        logger.debug("Creating action of type '{}'", actionType);
        
        Action action = actionRegistry.create(actionConfig);
        if (action == null) {
            logger.error("Failed to create action of type '{}' - check configuration", actionType);
            return;
        }

        try {
            long actionStart = System.currentTimeMillis();
            if (actionConfig.isWaitForCompletion()) {
                logger.debug("Action '{}': executing synchronously (wait_for_completion=true)", actionType);
                action.execute(context).join();
                long actionDuration = System.currentTimeMillis() - actionStart;
                logger.debug("Action '{}': completed synchronously in {}ms", actionType, actionDuration);
            } else {
                logger.debug("Action '{}': executing asynchronously (fire-and-forget)", actionType);
                action.execute(context);
                logger.debug("Action '{}': async execution initiated", actionType);
            }
        } catch (Exception e) {
            logger.error("Action '{}' execution failed: {}", actionType, e.getMessage());
            logger.debug("Action '{}' execution error details:", actionType, e);
        }
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        logger.debug("Initiating graceful shutdown of rule executor...");
        executorService.shutdown();
        logger.debug("Executor shutdown initiated - no longer accepting new tasks");
    }

    /**
     * Waits for pending executions to complete.
     *
     * @param timeout The maximum time to wait
     * @return true if all executions completed, false if timeout was reached
     */
    public boolean awaitTermination(java.time.Duration timeout) {
        logger.debug("Awaiting executor termination (timeout: {}ms)", timeout.toMillis());
        try {
            boolean completed = executorService.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (completed) {
                logger.debug("Executor terminated successfully - all tasks completed");
            } else {
                logger.debug("Executor termination timed out - some tasks may still be running");
            }
            return completed;
        } catch (InterruptedException e) {
            logger.warn("Executor termination interrupted");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Forces immediate shutdown.
     */
    public void shutdownNow() {
        logger.warn("Forcing immediate executor shutdown...");
        executorService.shutdownNow();
        logger.warn("Force shutdown complete");
    }
}
