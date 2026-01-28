package com.autostartstop.rule;

import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.RuleConfig;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.template.Template;
import com.autostartstop.template.TemplateRegistry;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerRegistry;
import com.autostartstop.trigger.TriggerType;
import com.autostartstop.trigger.impl.ManualTrigger;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Manages all rules in the plugin.
 * Handles rule lifecycle including trigger activation/deactivation and template activation.
 */
public class RuleManager {
    private static final Logger logger = Log.get(RuleManager.class);
    
    private final Map<String, Rule> rules = new ConcurrentHashMap<>();
    private final Map<String, List<ManualTrigger>> manualTriggersById = new ConcurrentHashMap<>();
    private final TriggerRegistry triggerRegistry;
    private final TemplateRegistry templateRegistry;

    public RuleManager(TriggerRegistry triggerRegistry, TemplateRegistry templateRegistry) {
        this.triggerRegistry = triggerRegistry;
        this.templateRegistry = templateRegistry;
    }

    /**
     * Loads rules from the plugin configuration and activates their triggers.
     *
     * @param config The plugin configuration
     * @param ruleExecutor The rule executor for creating execution callbacks
     */
    public void loadRules(PluginConfig config, RuleExecutor ruleExecutor) {
        logger.debug("Loading rules from configuration...");
        clear();

        Map<String, RuleConfig> ruleConfigs = config.getRules();
        if (ruleConfigs == null || ruleConfigs.isEmpty()) {
            logger.warn("No rules configured - automation will not be active");
            return;
        }

        logger.debug("Processing {} rule configurations", ruleConfigs.size());
        int successCount = 0;
        int disabledCount = 0;
        int failCount = 0;

        for (Map.Entry<String, RuleConfig> entry : ruleConfigs.entrySet()) {
            String name = entry.getKey();
            RuleConfig ruleConfig = entry.getValue();

            try {
                logger.debug("Loading rule '{}'...", name);
                Rule rule = createRule(name, ruleConfig);
                rules.put(name, rule);

                // Check if rule is enabled
                if (!ruleConfig.isEnabled()) {
                    logger.debug("Rule '{}' is disabled, skipping activation", name);
                    disabledCount++;
                    continue;
                }

                // Index manual triggers by ID for TriggerCommand lookup (only for normal rules)
                if (!rule.isTemplateRule() && rule.getTriggers() != null) {
                    for (Trigger trigger : rule.getTriggers()) {
                        if (trigger.getType() == TriggerType.MANUAL && trigger instanceof ManualTrigger manualTrigger) {
                            String triggerId = manualTrigger.getId();
                            manualTriggersById.computeIfAbsent(triggerId, k -> new ArrayList<>()).add(manualTrigger);
                            logger.debug("Rule '{}': indexed manual trigger with ID '{}'", name, triggerId);
                        }
                    }
                }

                // Create execution callback for this rule (only used for normal rules with triggers)
                // Returns a CompletableFuture so triggers that need synchronous execution can wait
                Function<ExecutionContext, CompletableFuture<Void>> executionCallback = context -> {
                    logger.debug("Trigger fired for rule '{}', executing...", rule.getName());
                    return ruleExecutor.execute(rule, context);
                };

                // Activate the rule (starts all triggers or template)
                rule.activate(executionCallback);
                
                if (rule.isTemplateRule()) {
                    logger.debug("Rule '{}' activated with template '{}'", name, ruleConfig.getTemplate());
                } else {
                    int triggerCount = rule.getTriggers() != null ? rule.getTriggers().size() : 0;
                    logger.debug("Rule '{}' activated with {} triggers", name, triggerCount);
                }

                successCount++;
                if (rule.isTemplateRule()) {
                    logger.debug("Rule '{}' loaded successfully (template: {})", name, ruleConfig.getTemplate());
                } else {
                    int triggerCount = rule.getTriggers() != null ? rule.getTriggers().size() : 0;
                    int actionCount = rule.getActions() != null ? rule.getActions().size() : 0;
                    logger.debug("Rule '{}' loaded successfully ({} triggers, {} actions)", 
                            name, triggerCount, actionCount);
                }
            } catch (Exception e) {
                failCount++;
                logger.error("Failed to load rule '{}': {}", name, e.getMessage());
                logger.debug("Rule '{}' load error details:", name, e);
            }
        }

        logger.debug("Loaded and activated {} rules ({} disabled, {} failed)", successCount, disabledCount, failCount);
    }

    /**
     * Creates a Rule from its configuration.
     */
    private Rule createRule(String name, RuleConfig config) {
        logger.debug("Creating rule '{}' from configuration", name);
        
        // Check if this is a template-based rule
        if (config.isTemplateRule()) {
            logger.debug("Rule '{}': creating template-based rule with template '{}'", name, config.getTemplate());
            TemplateConfig templateConfig = config.getTemplateConfig();
            if (templateConfig == null) {
                logger.error("Rule '{}': template '{}' specified but template config is null", name, config.getTemplate());
                throw new IllegalStateException("Template config is null for template rule: " + name);
            }
            
            Template template = templateRegistry.create(templateConfig);
            if (template == null) {
                logger.error("Rule '{}': failed to create template '{}'", name, config.getTemplate());
                throw new IllegalStateException("Failed to create template: " + config.getTemplate());
            }
            
            logger.debug("Rule '{}': template '{}' created successfully", name, config.getTemplate());
            return new Rule(name, config, template);
        }
        
        // Normal rule with triggers
        List<Trigger> triggers = new ArrayList<>();

        if (config.getTriggers() != null) {
            logger.debug("Rule '{}': processing {} trigger configs", name, config.getTriggers().size());
            for (TriggerConfig triggerConfig : config.getTriggers()) {
                logger.debug("Rule '{}': creating trigger of type '{}'", name, triggerConfig.getType());
                Trigger trigger = triggerRegistry.create(triggerConfig);
                if (trigger != null) {
                    triggers.add(trigger);
                    logger.debug("Rule '{}': trigger '{}' created successfully", name, trigger.getType().getConfigName());
                } else {
                    logger.warn("Rule '{}': failed to create trigger from config (type: {})", name, triggerConfig.getType());
                }
            }
        } else {
            logger.warn("Rule '{}': no triggers configured", name);
        }

        if (triggers.isEmpty()) {
            logger.warn("Rule '{}' has no valid triggers - this rule will never fire", name);
        }

        return new Rule(name, config, triggers);
    }

    /**
     * Gets a rule by name.
     *
     * @param name The rule name
     * @return The rule, or null if not found
     */
    public Rule getRule(String name) {
        return rules.get(name);
    }

    /**
     * Gets all rules.
     *
     * @return Collection of all rules
     */
    public Collection<Rule> getAllRules() {
        return rules.values();
    }

    /**
     * Gets all registered manual trigger IDs.
     * Used by TriggerCommand for tab completion.
     *
     * @return Set of all manual trigger IDs
     */
    public Set<String> getManualTriggerIds() {
        return manualTriggersById.keySet();
    }

    /**
     * Gets all manual triggers with the given ID.
     * Used by TriggerCommand to fire manual triggers.
     *
     * @param triggerId The manual trigger ID
     * @return List of matching ManualTrigger instances, or empty list if none found
     */
    public List<ManualTrigger> getManualTriggers(String triggerId) {
        logger.debug("Looking up manual triggers for ID '{}'", triggerId);
        List<ManualTrigger> triggers = manualTriggersById.get(triggerId);
        if (triggers == null || triggers.isEmpty()) {
            logger.debug("No manual triggers found for ID '{}'", triggerId);
            return Collections.emptyList();
        }
        logger.debug("Found {} manual trigger(s) for ID '{}'", triggers.size(), triggerId);
        return triggers;
    }

    /**
     * Clears all rules and deactivates their triggers.
     */
    public void clear() {
        int ruleCount = rules.size();
        
        // Deactivate all rules before clearing
        for (Rule rule : rules.values()) {
            try {
                rule.deactivate();
                logger.debug("Deactivated rule '{}'", rule.getName());
            } catch (Exception e) {
                logger.error("Error deactivating rule '{}': {}", rule.getName(), e.getMessage());
            }
        }
        
        rules.clear();
        manualTriggersById.clear();
        logger.debug("Cleared {} rules and all indexes", ruleCount);
    }
}
