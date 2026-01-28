package com.autostartstop.template;

import com.autostartstop.config.TemplateConfig;

/**
 * Functional interface for creating template instances.
 */
@FunctionalInterface
public interface TemplateCreator {
    /**
     * Creates a template instance from the given configuration.
     *
     * @param config The template configuration
     * @param context The template context containing dependencies
     * @return The created template instance
     */
    Template create(TemplateConfig config, TemplateContext context);
}
