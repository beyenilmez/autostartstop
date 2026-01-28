package com.autostartstop.rule;

import com.autostartstop.config.ActionConfig;
import com.autostartstop.config.ConditionConfig;
import com.autostartstop.config.RuleConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.template.Template;
import com.autostartstop.trigger.Trigger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Represents a rule that connects triggers to actions with optional conditions,
 * or uses a template for built-in behavior.
 */
public class Rule {
    private final String name;
    private final RuleConfig config;
    private final List<Trigger> triggers;
    private final Template template;
    private boolean activated = false;

    /**
     * Creates a rule with triggers (normal rule).
     */
    public Rule(String name, RuleConfig config, List<Trigger> triggers) {
        this.name = name;
        this.config = config;
        this.triggers = triggers;
        this.template = null;
    }

    /**
     * Creates a rule with a template (template-based rule).
     */
    public Rule(String name, RuleConfig config, Template template) {
        this.name = name;
        this.config = config;
        this.triggers = null;
        this.template = template;
    }

    /**
     * Activates all triggers in this rule, or the template if this is a template-based rule.
     * Each trigger will start listening for its respective events.
     *
     * @param executionCallback Callback to invoke when any trigger fires.
     *                          Returns a CompletableFuture that completes when execution is done.
     *                          Not used for template rules (templates handle their own execution).
     */
    public void activate(Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        if (activated) {
            return;
        }
        if (template != null) {
            // Template-based rule
            template.activate(this.name);
        } else if (triggers != null) {
            // Normal rule with triggers
            for (Trigger trigger : triggers) {
                trigger.activate(this.name, executionCallback);
            }
        }
        activated = true;
    }

    /**
     * Deactivates all triggers in this rule, or the template if this is a template-based rule.
     * Each trigger will stop listening for events.
     */
    public void deactivate() {
        if (!activated) {
            return;
        }
        if (template != null) {
            // Template-based rule
            template.deactivate();
        } else if (triggers != null) {
            // Normal rule with triggers
            for (Trigger trigger : triggers) {
                trigger.deactivate();
            }
        }
        activated = false;
    }

    /**
     * Checks if this rule is currently activated.
     *
     * @return true if the rule's triggers are active
     */
    public boolean isActivated() {
        return activated;
    }

    /**
     * Gets the name of this rule.
     *
     * @return The rule name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the configuration for this rule.
     *
     * @return The rule configuration
     */
    public RuleConfig getConfig() {
        return config;
    }

    /**
     * Gets the triggers for this rule.
     *
     * @return List of triggers, or null if this is a template-based rule
     */
    public List<Trigger> getTriggers() {
        return triggers;
    }

    /**
     * Gets the template for this rule.
     *
     * @return The template, or null if this is a normal rule
     */
    public Template getTemplate() {
        return template;
    }

    /**
     * Checks if this rule uses a template.
     *
     * @return true if this rule uses a template
     */
    public boolean isTemplateRule() {
        return template != null;
    }

    /**
     * Gets the conditions for this rule.
     *
     * @return The condition configuration, or null if no conditions
     */
    public ConditionConfig getConditions() {
        return config.getConditions();
    }

    /**
     * Gets the actions for this rule.
     *
     * @return List of action configurations
     */
    public List<ActionConfig> getActions() {
        return config.getActions();
    }

    /**
     * Checks if this rule has any conditions.
     *
     * @return true if conditions are defined
     */
    public boolean hasConditions() {
        return config.getConditions() != null && !config.getConditions().isEmpty();
    }
}
