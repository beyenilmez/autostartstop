package com.autostartstop.config;

import java.util.List;

/**
 * Configuration for a rule.
 */
public class RuleConfig {
    private String name;
    private Boolean enabled;
    private String template;
    private TemplateConfig templateConfig;
    private List<TriggerConfig> triggers;
    private ConditionConfig conditions;
    private List<ActionConfig> actions;

    public RuleConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets whether this rule is enabled.
     * Defaults to true if not explicitly set.
     *
     * @return true if the rule is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled != null ? enabled : true;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<TriggerConfig> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<TriggerConfig> triggers) {
        this.triggers = triggers;
    }

    public ConditionConfig getConditions() {
        return conditions;
    }

    public void setConditions(ConditionConfig conditions) {
        this.conditions = conditions;
    }

    public List<ActionConfig> getActions() {
        return actions;
    }

    public void setActions(List<ActionConfig> actions) {
        this.actions = actions;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public TemplateConfig getTemplateConfig() {
        return templateConfig;
    }

    public void setTemplateConfig(TemplateConfig templateConfig) {
        this.templateConfig = templateConfig;
    }

    /**
     * Checks if this rule uses a template.
     *
     * @return true if this rule uses a template
     */
    public boolean isTemplateRule() {
        return template != null && !template.isEmpty();
    }
}
