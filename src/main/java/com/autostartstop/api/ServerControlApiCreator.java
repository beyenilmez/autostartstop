package com.autostartstop.api;

import com.autostartstop.config.ControlApiConfig;

/**
 * Functional interface for creating ServerControlApi instances.
 */
@FunctionalInterface
public interface ServerControlApiCreator {
    /**
     * Creates a ServerControlApi instance from the given configuration.
     *
     * @param config The control API configuration
     * @param serverName The name of the server this API will control
     * @return The created ServerControlApi instance
     */
    ServerControlApi create(ControlApiConfig config, String serverName);
}
