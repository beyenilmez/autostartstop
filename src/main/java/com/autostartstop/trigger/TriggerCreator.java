package com.autostartstop.trigger;

import com.autostartstop.config.TriggerConfig;

/**
 * Functional interface for creating trigger instances.
 */
@FunctionalInterface
public interface TriggerCreator {
    /**
     * Creates a trigger instance from the given configuration.
     *
     * @param config The trigger configuration
     * @param context The trigger context containing dependencies
     * @return The created trigger instance
     */
    Trigger create(TriggerConfig config, TriggerContext context);
}
