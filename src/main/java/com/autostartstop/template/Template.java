package com.autostartstop.template;

/**
 * Interface for all template types.
 * Templates are self-contained - they manage their own triggers/actions
 * and handle their own lifecycle.
 */
public interface Template {

    /**
     * Gets the type of this template.
     *
     * @return The template type
     */
    TemplateType getType();

    /**
     * Activates this template, starting its internal triggers/actions.
     * Called when the rule containing this template is loaded.
     *
     * @param ruleName The name of the rule this template belongs to
     */
    void activate(String ruleName);

    /**
     * Deactivates this template, stopping its internal triggers/actions.
     * Called when the rule is unloaded or the plugin is disabled.
     */
    void deactivate();
}
