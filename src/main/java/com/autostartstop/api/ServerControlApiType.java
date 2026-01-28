package com.autostartstop.api;

import com.autostartstop.api.impl.AmpServerControlApi;
import com.autostartstop.api.impl.PterodactylServerControlApi;
import com.autostartstop.api.impl.ShellServerControlApi;
import com.autostartstop.config.ConfigNamedType;

/**
 * Enumeration of available server control API types.
 */
public enum ServerControlApiType implements ConfigNamedType {
    SHELL("shell", ShellServerControlApi::create),
    AMP("amp", AmpServerControlApi::create),
    PTERODACTYL("pterodactyl", PterodactylServerControlApi::create);

    private final String configName;
    private final ServerControlApiCreator creator;

    ServerControlApiType(String configName, ServerControlApiCreator creator) {
        this.configName = configName;
        this.creator = creator;
    }

    /**
     * Gets the configuration name for this API type.
     *
     * @return The configuration name
     */
    @Override
    public String getConfigName() {
        return configName;
    }

    /**
     * Gets the creator for this API type.
     *
     * @return The API creator
     */
    public ServerControlApiCreator getCreator() {
        return creator;
    }

    /**
     * Checks if this API type has a creator.
     *
     * @return true if a creator is available
     */
    public boolean hasCreator() {
        return creator != null;
    }

    /**
     * Gets a ServerControlApiType from its configuration name.
     *
     * @param configName The configuration name
     * @return The corresponding ServerControlApiType, or null if not found
     */
    public static ServerControlApiType fromConfigName(String configName) {
        return ConfigNamedType.fromConfigName(ServerControlApiType.class, configName);
    }

    /**
     * Gets all valid API type names.
     *
     * @return Comma-separated list of valid API type names
     */
    public static String getValidNames() {
        return ConfigNamedType.getValidNames(ServerControlApiType.class);
    }
}
