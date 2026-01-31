package com.autostartstop.condition;

import com.autostartstop.Log;
import com.autostartstop.config.ConfigException;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for condition creators.
 * Manages the creation of conditions using static create methods defined in ConditionType.
 */
public class ConditionRegistry {
    
    private static final Logger logger = Log.get(ConditionRegistry.class);
    private ConditionContext conditionContext;

    public ConditionRegistry() {
    }

    /**
     * Sets the condition context used for creating conditions.
     */
    public void setConditionContext(ConditionContext context) {
        this.conditionContext = context;
    }

    /**
     * Gets the condition context.
     */
    public ConditionContext getConditionContext() {
        return conditionContext;
    }

    /**
     * Creates a condition from the given configuration map.
     */
    @SuppressWarnings("unchecked")
    public Condition create(Map<String, Object> configMap) {
        if (configMap == null || configMap.isEmpty()) {
            logger.error("Empty condition configuration provided");
            return null;
        }

        logger.debug("Creating condition from config: {}", configMap.keySet());

        // Find the condition type from the map keys
        for (Map.Entry<String, Object> entry : configMap.entrySet()) {
            String typeName = entry.getKey();
            ConditionType type = ConditionType.fromConfigName(typeName);

            if (type != null) {
                logger.debug("Identified condition type: '{}'", type.getConfigName());
                
                Object value = entry.getValue();
                Map<String, Object> params = new HashMap<>();
                if (value instanceof Map) {
                    params = (Map<String, Object>) value;
                    logger.debug("Condition '{}' parameters: {}", type.getConfigName(), params.keySet());
                }

                try {
                    if (!type.hasCreator()) {
                        logger.error("Condition type '{}' has no creator defined", type.getConfigName());
                        return null;
                    }
                    
                    if (conditionContext == null) {
                        logger.error("ConditionContext is not set - cannot create condition '{}'", type.getConfigName());
                        return null;
                    }

                    Condition condition = type.getCreator().create(params, conditionContext);
                    logger.debug("Condition '{}' created successfully", type.getConfigName());
                    return condition;
                } catch (ConfigException e) {
                    logger.error("Failed to create condition '{}': {}", type.getConfigName(), e.getMessage());
                    return null;
                } catch (Exception e) {
                    logger.error("Failed to create condition '{}': {}", type.getConfigName(), e.getMessage());
                    logger.debug("Condition creation error details:", e);
                    return null;
                }
            }
        }

        logger.error("Unknown condition type '{}' - valid types: {}", 
                configMap.keySet(), ConditionType.getValidNames());
        return null;
    }

    /**
     * Checks if a creator is available for the given condition type.
     */
    public boolean hasCreator(ConditionType type) {
        return type.hasCreator() && conditionContext != null;
    }

    /**
     * Logs all available condition types with built-in creators.
     * 
     * @return The number of available condition creators
     */
    public int autoRegisterCreators() {
        int count = 0;
        for (ConditionType type : ConditionType.values()) {
            if (type.hasCreator()) {
                logger.debug("Available condition: {} (has creator)", type.getConfigName());
                count++;
            }
        }
        logger.debug("Available condition types: {} with built-in creators", count);
        return count;
    }
}
