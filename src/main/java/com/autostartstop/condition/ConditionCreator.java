package com.autostartstop.condition;

import java.util.Map;

/**
 * Functional interface for creating condition instances.
 */
@FunctionalInterface
public interface ConditionCreator {
    /**
     * Creates a condition instance from the given configuration.
     *
     * @param config The condition configuration map
     * @param context The condition context containing dependencies
     * @return The created condition instance
     */
    Condition create(Map<String, Object> config, ConditionContext context);
}
