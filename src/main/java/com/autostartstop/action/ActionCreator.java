package com.autostartstop.action;

import com.autostartstop.config.ActionConfig;

/**
 * Functional interface for creating action instances.
 */
@FunctionalInterface
public interface ActionCreator {
    /**
     * Creates an action instance from the given configuration.
     *
     * @param config The action configuration
     * @param context The action context containing dependencies
     * @return The created action instance
     */
    Action create(ActionConfig config, ActionContext context);
}
