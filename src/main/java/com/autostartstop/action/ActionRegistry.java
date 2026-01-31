package com.autostartstop.action;

import com.autostartstop.Log;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.config.ConfigException;
import org.slf4j.Logger;

/**
 * Registry for action creators.
 * Manages the creation of actions using static create methods defined in ActionType.
 */
public class ActionRegistry {
    
    private static final Logger logger = Log.get(ActionRegistry.class);
    private ActionContext actionContext;

    public ActionRegistry() {
    }

    /**
     * Sets the action context used for creating actions.
     *
     * @param context The action context
     */
    public void setActionContext(ActionContext context) {
        this.actionContext = context;
    }

    /**
     * Gets the action context.
     *
     * @return The action context
     */
    public ActionContext getActionContext() {
        return actionContext;
    }

    /**
     * Creates an action from the given configuration.
     *
     * @param config The action configuration
     * @return The created action, or null if creation fails
     */
    public Action create(ActionConfig config) {
        String configType = config.getType();
        logger.debug("Creating action of type '{}'", configType);
        
        ActionType type = ActionType.fromConfigName(configType);
        if (type == null) {
            logger.error("Unknown action type '{}' - valid types: {}", 
                    configType, ActionType.getValidNames());
            return null;
        }

        try {
            if (!type.hasCreator()) {
                logger.error("Action type '{}' has no creator defined", type.getConfigName());
                return null;
            }
            
            if (actionContext == null) {
                logger.error("ActionContext is not set - cannot create action '{}'", type.getConfigName());
                return null;
            }

            Action action = type.getCreator().create(config, actionContext);
            logger.debug("Action '{}' created successfully", type.getConfigName());
            return action;
        } catch (ConfigException e) {
            logger.error("Failed to create action '{}': {}", type.getConfigName(), e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to create action '{}': {}", type.getConfigName(), e.getMessage());
            logger.debug("Action creation error details:", e);
            return null;
        }
    }

    /**
     * Checks if a creator is available for the given action type.
     *
     * @param type The action type
     * @return true if a creator is available
     */
    public boolean hasCreator(ActionType type) {
        return type.hasCreator() && actionContext != null;
    }

    /**
     * Logs all available action types with built-in creators.
     * 
     * @return The number of available action creators
     */
    public int autoRegisterCreators() {
        int count = 0;
        for (ActionType type : ActionType.values()) {
            if (type.hasCreator()) {
                logger.debug("Available action: {} (has creator)", type.getConfigName());
                count++;
            }
        }
        logger.debug("Available action types: {} with built-in creators", count);
        return count;
    }
}
