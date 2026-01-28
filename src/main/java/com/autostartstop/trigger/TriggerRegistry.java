package com.autostartstop.trigger;

import com.autostartstop.Log;
import com.autostartstop.config.ConfigException;
import com.autostartstop.config.TriggerConfig;
import org.slf4j.Logger;

/**
 * Registry for trigger creators.
 * Manages the creation of triggers using static create methods defined in TriggerType.
 */
public class TriggerRegistry {
    
    private static final Logger logger = Log.get(TriggerRegistry.class);
    private TriggerContext triggerContext;

    public TriggerRegistry() {
    }

    /**
     * Sets the trigger context used for creating triggers.
     *
     * @param context The trigger context
     */
    public void setTriggerContext(TriggerContext context) {
        this.triggerContext = context;
    }

    /**
     * Gets the trigger context.
     *
     * @return The trigger context
     */
    public TriggerContext getTriggerContext() {
        return triggerContext;
    }

    /**
     * Creates a trigger from the given configuration.
     *
     * @param config The trigger configuration
     * @return The created trigger, or null if creation fails
     */
    public Trigger create(TriggerConfig config) {
        String configType = config.getType();
        logger.debug("Creating trigger of type '{}'", configType);
        
        TriggerType type = TriggerType.fromConfigName(configType);
        if (type == null) {
            logger.error("Unknown trigger type '{}' - valid types: {}", 
                    configType, TriggerType.getValidNames());
            return null;
        }

        try {
            if (!type.hasCreator()) {
                logger.error("Trigger type '{}' has no creator defined", type.getConfigName());
                return null;
            }
            
            if (triggerContext == null) {
                logger.error("TriggerContext is not set - cannot create trigger '{}'", type.getConfigName());
                return null;
            }

            Trigger trigger = type.getCreator().create(config, triggerContext);
            logger.debug("Trigger '{}' created successfully", type.getConfigName());
            return trigger;
        } catch (ConfigException e) {
            logger.error("Failed to create trigger '{}': {}", type.getConfigName(), e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to create trigger '{}': {}", type.getConfigName(), e.getMessage());
            logger.debug("Trigger creation error details:", e);
            return null;
        }
    }

    /**
     * Checks if a creator is available for the given trigger type.
     *
     * @param type The trigger type
     * @return true if a creator is available
     */
    public boolean hasCreator(TriggerType type) {
        return type.hasCreator() && triggerContext != null;
    }

    /**
     * Logs all available trigger types with built-in creators.
     * 
     * @return The number of available trigger creators
     */
    public int autoRegisterCreators() {
        int count = 0;
        for (TriggerType type : TriggerType.values()) {
            if (type.hasCreator()) {
                logger.debug("Available trigger: {} (has creator)", type.getConfigName());
                count++;
            }
        }
        logger.info("Available trigger types: {} with built-in creators", count);
        return count;
    }
}
