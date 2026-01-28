package com.autostartstop.condition;

import com.autostartstop.config.ConditionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Evaluates conditions against an execution context.
 * Supports AND (all) and OR (any) modes for combining multiple conditions.
 */
public class ConditionEvaluator {
    private static final Logger logger = Log.get(ConditionEvaluator.class);
    
    private final ConditionRegistry conditionRegistry;

    public ConditionEvaluator(ConditionRegistry conditionRegistry) {
        this.conditionRegistry = conditionRegistry;
    }

    /**
     * Evaluates a condition configuration against the given context.
     *
     * @param config The condition configuration
     * @param context The execution context
     * @return true if the conditions are satisfied
     */
    public boolean evaluate(ConditionConfig config, ExecutionContext context) {
        if (config == null || config.isEmpty()) {
            logger.debug("ConditionEvaluator: no conditions defined, returning true");
            return true;
        }

        List<Map<String, Object>> checks = config.getChecks();
        if (checks == null || checks.isEmpty()) {
            logger.debug("ConditionEvaluator: no condition checks defined, returning true");
            return true;
        }

        String mode = config.getMode();
        boolean isAllMode = !"any".equalsIgnoreCase(mode);

        logger.debug("ConditionEvaluator: evaluating {} conditions in '{}' mode", checks.size(), mode);

        int passCount = 0;
        int failCount = 0;
        int skipCount = 0;

        for (Map<String, Object> checkMap : checks) {
            Condition condition = conditionRegistry.create(checkMap);
            if (condition == null) {
                logger.warn("ConditionEvaluator: failed to create condition from config: {}", checkMap);
                skipCount++;
                continue;
            }

            boolean result = condition.evaluate(context);
            
            // Check for invert flag in the condition parameters
            boolean invert = shouldInvert(checkMap);
            if (invert) {
                result = !result;
                logger.debug("ConditionEvaluator: condition '{}' evaluated to {} (inverted)", 
                        condition.getType().getConfigName(), result ? "TRUE" : "FALSE");
            } else {
                logger.debug("ConditionEvaluator: condition '{}' evaluated to {}", 
                        condition.getType().getConfigName(), result ? "TRUE" : "FALSE");
            }

            if (result) {
                passCount++;
            } else {
                failCount++;
            }

            if (isAllMode && !result) {
                logger.debug("ConditionEvaluator: mode='all' and condition failed, returning false early");
                return false;
            } else if (!isAllMode && result) {
                logger.debug("ConditionEvaluator: mode='any' and condition passed, returning true early");
                return true;
            }
        }

        boolean finalResult = isAllMode;
        logger.debug("ConditionEvaluator: evaluation complete - passed: {}, failed: {}, skipped: {}, result: {}", 
                passCount, failCount, skipCount, finalResult);
        return finalResult;
    }

    /**
     * Checks if a condition should have its result inverted.
     * Looks for an "invert" key in the condition's parameters.
     *
     * @param checkMap The condition configuration map
     * @return true if the result should be inverted
     */
    @SuppressWarnings("unchecked")
    private boolean shouldInvert(Map<String, Object> checkMap) {
        // The checkMap has format: {"condition_type": {params...}}
        // The invert flag can be in the params
        for (Map.Entry<String, Object> entry : checkMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> params = (Map<String, Object>) value;
                Object invertObj = params.get("invert");
                if (invertObj != null) {
                    return Boolean.parseBoolean(invertObj.toString());
                }
            }
        }
        return false;
    }
}
