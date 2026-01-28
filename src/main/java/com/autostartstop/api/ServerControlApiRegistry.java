package com.autostartstop.api;

import com.autostartstop.Log;
import com.autostartstop.config.ControlApiConfig;
import org.slf4j.Logger;

/**
 * Registry for server control API creators.
 * Manages the creation of control APIs using static create methods defined in ServerControlApiType.
 */
public class ServerControlApiRegistry {
    
    private static final Logger logger = Log.get(ServerControlApiRegistry.class);

    public ServerControlApiRegistry() {
    }

    /**
     * Creates a control API from the given configuration.
     *
     * @param config The control API configuration
     * @param serverName The name of the server
     * @return The created control API, or null if creation fails
     */
    public ServerControlApi create(ControlApiConfig config, String serverName) {
        logger.debug("Creating control API for server '{}'", serverName);
        
        if (config == null || config.getType() == null) {
            logger.error("Server '{}': control API configuration is missing or has no type", serverName);
            return null;
        }

        String configType = config.getType().toLowerCase();
        logger.debug("Server '{}': looking up creator for API type '{}'", serverName, configType);
        
        ServerControlApiType type = ServerControlApiType.fromConfigName(configType);
        if (type == null) {
            logger.error("Server '{}': unknown API type '{}' - valid types: {}", 
                    serverName, configType, ServerControlApiType.getValidNames());
            return null;
        }

        try {
            if (!type.hasCreator()) {
                logger.error("Server '{}': API type '{}' has no creator defined", serverName, type.getConfigName());
                return null;
            }

            logger.debug("Server '{}': invoking creator to create '{}' control API", serverName, configType);
            ServerControlApi api = type.getCreator().create(config, serverName);
            if (api != null) {
                logger.debug("Server '{}': control API created successfully (type: {})", serverName, api.getType());
            } else {
                logger.error("Server '{}': creator returned null for API type '{}'", serverName, configType);
            }
            return api;
        } catch (Exception e) {
            logger.error("Server '{}': failed to create control API: {}", serverName, e.getMessage());
            logger.debug("Control API creation error for server '{}':", serverName, e);
            return null;
        }
    }

    /**
     * Checks if a creator is available for the given API type.
     *
     * @param type The API type name
     * @return true if a creator is available
     */
    public boolean hasCreator(String type) {
        ServerControlApiType apiType = ServerControlApiType.fromConfigName(type.toLowerCase());
        return apiType != null && apiType.hasCreator();
    }

    /**
     * Logs all available API types with built-in creators.
     * 
     * @return The number of available API creators
     */
    public int autoRegisterCreators() {
        int count = 0;
        for (ServerControlApiType type : ServerControlApiType.values()) {
            if (type.hasCreator()) {
                logger.debug("Available control API: {} (has creator)", type.getConfigName());
                count++;
            }
        }
        logger.info("Available control API types: {} with built-in creators", count);
        return count;
    }
}
