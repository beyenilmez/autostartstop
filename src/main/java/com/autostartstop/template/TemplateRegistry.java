package com.autostartstop.template;

import com.autostartstop.Log;
import com.autostartstop.config.ConfigException;
import com.autostartstop.config.TemplateConfig;
import org.slf4j.Logger;

/**
 * Registry for template creators.
 * Manages the creation of templates using static create methods defined in TemplateType.
 */
public class TemplateRegistry {
    
    private static final Logger logger = Log.get(TemplateRegistry.class);
    private TemplateContext templateContext;

    public TemplateRegistry() {
    }

    /**
     * Sets the template context used for creating templates.
     *
     * @param context The template context
     */
    public void setTemplateContext(TemplateContext context) {
        this.templateContext = context;
    }

    /**
     * Gets the template context.
     *
     * @return The template context
     */
    public TemplateContext getTemplateContext() {
        return templateContext;
    }

    /**
     * Creates a template from the given configuration.
     *
     * @param config The template configuration
     * @return The created template, or null if creation fails
     */
    public Template create(TemplateConfig config) {
        String configType = config.getTemplate();
        logger.debug("Creating template of type '{}'", configType);
        
        TemplateType type = TemplateType.fromConfigName(configType);
        if (type == null) {
            logger.error("Unknown template type '{}' - valid types: {}", 
                    configType, TemplateType.getValidNames());
            return null;
        }

        try {
            if (!type.hasCreator()) {
                logger.error("Template type '{}' has no creator defined", type.getConfigName());
                return null;
            }
            
            if (templateContext == null) {
                logger.error("TemplateContext is not set - cannot create template '{}'", type.getConfigName());
                return null;
            }

            Template template = type.getCreator().create(config, templateContext);
            logger.debug("Template '{}' created successfully", type.getConfigName());
            return template;
        } catch (ConfigException e) {
            logger.error("Failed to create template '{}': {}", type.getConfigName(), e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to create template '{}': {}", type.getConfigName(), e.getMessage());
            logger.debug("Template creation error details:", e);
            return null;
        }
    }

    /**
     * Checks if a creator is available for the given template type.
     *
     * @param type The template type
     * @return true if a creator is available
     */
    public boolean hasCreator(TemplateType type) {
        return type.hasCreator() && templateContext != null;
    }

    /**
     * Logs all available template types with built-in creators.
     * 
     * @return The number of available template creators
     */
    public int autoRegisterCreators() {
        int count = 0;
        for (TemplateType type : TemplateType.values()) {
            if (type.hasCreator()) {
                logger.debug("Available template: {} (has creator)", type.getConfigName());
                count++;
            }
        }
        logger.info("Available template types: {} with built-in creators", count);
        return count;
    }
}
