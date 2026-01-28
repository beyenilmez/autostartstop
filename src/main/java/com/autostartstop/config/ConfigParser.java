package com.autostartstop.config;

import com.autostartstop.Log;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for parsing configuration from YAML sections.
 * Provides reusable parsing methods to reduce code duplication in ConfigLoader.
 */
public final class ConfigParser {
    
    private static final Logger logger = Log.get(ConfigParser.class);

    private ConfigParser() {
        // Utility class
    }

    // ========== Action Parsing ==========

    /**
     * Parses an action configuration from a map.
     * The action type is the first key in the map.
     *
     * @param actionMap The raw action map
     * @return The parsed ActionConfig, or null if invalid
     */
    public static ActionConfig parseAction(Map<?, ?> actionMap) {
        if (actionMap == null || actionMap.isEmpty()) {
            return null;
        }

        ActionConfig config = new ActionConfig();

        for (Map.Entry<?, ?> entry : actionMap.entrySet()) {
            String type = entry.getKey().toString();
            config.setType(type);

            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> actionData = toStringKeyMap((Map<?, ?>) value);
                config.setRawConfig(actionData);

                // Parse wait_for_completion
                if (actionData.containsKey("wait_for_completion")) {
                    config.setWaitForCompletion(
                            Boolean.parseBoolean(actionData.get("wait_for_completion").toString()));
                }
            }

            break; // Only process the first entry
        }

        return config;
    }

    /**
     * Parses a list of actions from a section.
     *
     * @param section The YAML section containing actions
     * @param key The key for the action list (e.g., "action")
     * @param ruleName The rule name for logging
     * @return The list of parsed actions
     */
    public static List<ActionConfig> parseActions(Section section, String key, String ruleName) {
        List<ActionConfig> actions = new ArrayList<>();
        List<Map<?, ?>> actionsList = section.getMapList(key);
        
        if (actionsList != null) {
            for (Map<?, ?> actionMap : actionsList) {
                ActionConfig actionConfig = parseAction(actionMap);
                if (actionConfig != null) {
                    actions.add(actionConfig);
                    logger.debug("Rule '{}': parsed action type '{}'", ruleName, actionConfig.getType());
                }
            }
        }
        
        return actions;
    }

    // ========== Trigger Parsing ==========

    /**
     * Parses a trigger configuration from a map.
     * The trigger type is the first key in the map.
     *
     * @param triggerMap The raw trigger map
     * @return The parsed TriggerConfig, or null if invalid
     */
    public static TriggerConfig parseTrigger(Map<?, ?> triggerMap) {
        if (triggerMap == null || triggerMap.isEmpty()) {
            return null;
        }

        TriggerConfig config = new TriggerConfig();

        for (Map.Entry<?, ?> entry : triggerMap.entrySet()) {
            String type = entry.getKey().toString();
            config.setType(type);

            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> triggerData = toStringKeyMap((Map<?, ?>) value);
                config.setRawConfig(triggerData);
            }

            break; // Only process the first entry
        }

        return config;
    }

    /**
     * Parses a list of triggers from a section.
     *
     * @param section The YAML section containing triggers
     * @param ruleName The rule name for logging
     * @return The list of parsed triggers
     */
    public static List<TriggerConfig> parseTriggers(Section section, String ruleName) {
        List<TriggerConfig> triggers = new ArrayList<>();
        List<Map<?, ?>> triggersList = section.getMapList("triggers");
        
        if (triggersList != null) {
            for (Map<?, ?> triggerMap : triggersList) {
                TriggerConfig triggerConfig = parseTrigger(triggerMap);
                if (triggerConfig != null) {
                    triggers.add(triggerConfig);
                    logger.debug("Rule '{}': parsed trigger type '{}'", ruleName, triggerConfig.getType());
                }
            }
        }
        
        return triggers;
    }

    // ========== Condition Parsing ==========

    /**
     * Parses conditions from a section.
     *
     * @param section The conditions section
     * @return The parsed ConditionConfig
     */
    public static ConditionConfig parseConditions(Section section) {
        if (section == null) {
            return null;
        }

        ConditionConfig config = new ConditionConfig();
        config.setMode(section.getString("mode", "all"));

        List<Map<?, ?>> checksList = section.getMapList("checks");
        if (checksList != null && !checksList.isEmpty()) {
            List<Map<String, Object>> checks = new ArrayList<>();
            for (Map<?, ?> checkMap : checksList) {
                checks.add(toStringKeyMap(checkMap));
            }
            config.setChecks(checks);
        }

        return config;
    }

    // ========== Control API Parsing ==========

    /**
     * Parses a control API configuration from a section.
     *
     * @param section The control_api section
     * @return The parsed ControlApiConfig
     */
    public static ControlApiConfig parseControlApi(Section section) {
        if (section == null) {
            return null;
        }

        ControlApiConfig config = new ControlApiConfig();
        
        String type = section.getString("type");
        config.setType(type);
        
        // Convert entire section to raw config map
        Map<String, Object> rawConfig = sectionToMap(section);
        config.setRawConfig(rawConfig);
        
        return config;
    }

    // ========== Server Config Parsing ==========

    /**
     * Parses a server configuration from a section.
     *
     * @param section The server section
     * @param name The server name
     * @return The parsed ServerConfig
     */
    public static ServerConfig parseServer(Section section, String name) {
        ServerConfig config = new ServerConfig();
        config.setName(name);
        
        config.setVirtualHost(section.getString("virtual_host"));
        
        Section pingSection = section.getSection("ping");
        if (pingSection != null) {
            config.setPing(parsePing(pingSection));
        }

        Section controlApiSection = section.getSection("control_api");
        if (controlApiSection != null) {
            config.setControlApi(parseControlApi(controlApiSection));
        }

        Section startupTimerSection = section.getSection("startup_timer");
        if (startupTimerSection != null) {
            config.setStartupTimer(parseStartupTimer(startupTimerSection));
        }

        return config;
    }

    /**
     * Parses a ping configuration from a section.
     */
    public static PingConfig parsePing(Section section) {
        PingConfig config = new PingConfig();
        config.setTimeout(section.getString("timeout"));
        config.setMethod(section.getString("method"));
        return config;
    }

    /**
     * Parses a startup timer configuration from a section.
     */
    public static StartupTimerConfig parseStartupTimer(Section section) {
        StartupTimerConfig config = new StartupTimerConfig();
        config.setExpectedStartupTime(section.getString("expected_startup_time"));
        config.setAutoCalculateExpectedStartupTime(
                section.getBoolean("auto_calculate_expected_startup_time", false));
        return config;
    }

    // ========== Template Parsing ==========

    /**
     * Parses a template configuration from a section.
     *
     * @param section The rule section containing template configuration
     * @param ruleName The rule name for logging
     * @return The parsed TemplateConfig, or null if invalid
     */
    public static TemplateConfig parseTemplate(Section section, String ruleName) {
        String templateName = section.getString("template");
        if (templateName == null || templateName.isEmpty()) {
            return null;
        }

        TemplateConfig config = new TemplateConfig();
        config.setTemplate(templateName);

        // Convert entire section to raw config map (excluding the template field itself)
        Map<String, Object> rawConfig = sectionToMap(section);
        rawConfig.remove("template"); // Remove the template field itself
        rawConfig.remove("enabled"); // Remove enabled field (handled by RuleConfig)
        config.setRawConfig(rawConfig);

        logger.debug("Rule '{}': parsed template '{}'", ruleName, templateName);
        return config;
    }

    // ========== Rule Parsing ==========

    /**
     * Parses a rule configuration from a section.
     *
     * @param section The rule section
     * @param name The rule name
     * @return The parsed RuleConfig
     */
    public static RuleConfig parseRule(Section section, String name) {
        RuleConfig config = new RuleConfig();
        config.setName(name);

        // Parse enabled field (defaults to true if not present)
        config.setEnabled(section.getBoolean("enabled", true));

        // Check if this is a template rule
        String templateName = section.getString("template");
        if (templateName != null && !templateName.isEmpty()) {
            // Parse template configuration
            TemplateConfig templateConfig = parseTemplate(section, name);
            config.setTemplate(templateName);
            config.setTemplateConfig(templateConfig);

            // Validate mutual exclusivity: template rules cannot have triggers/actions/conditions
            boolean hasTriggers = section.getMapList("triggers") != null && !section.getMapList("triggers").isEmpty();
            boolean hasActions = section.getMapList("action") != null && !section.getMapList("action").isEmpty();
            boolean hasConditions = section.getSection("conditions") != null;

            if (hasTriggers || hasActions || hasConditions) {
                logger.warn("Rule '{}': template rules cannot have triggers, actions, or conditions. " +
                        "Ignoring triggers/actions/conditions.", name);
            }

            return config;
        }

        // Parse normal rule (triggers, conditions, actions)
        // Parse triggers
        List<TriggerConfig> triggers = parseTriggers(section, name);
        config.setTriggers(triggers);

        // Parse conditions
        Section conditionsSection = section.getSection("conditions");
        if (conditionsSection != null) {
            config.setConditions(parseConditions(conditionsSection));
        }

        // Parse actions
        List<ActionConfig> actions = parseActions(section, "action", name);
        config.setActions(actions);

        return config;
    }

    // ========== Settings Parsing ==========

    /**
     * Parses settings configuration from a section.
     */
    public static SettingsConfig parseSettings(Section section) {
        SettingsConfig settings = new SettingsConfig();
        if (section != null) {
            settings.setShutdownTimeout(section.getString("shutdown_timeout", "30s"));
            settings.setEmptyServerCheckInterval(section.getString("empty_server_check_interval", "5m"));
            settings.setMotdCacheInterval(section.getString("motd_cache_interval", "15m"));
        }
        return settings;
    }

    // ========== Utility Methods ==========

    /**
     * Converts a map with any key type to a map with String keys.
     */
    public static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    /**
     * Converts a YAML section to a Map.
     */
    public static Map<String, Object> sectionToMap(Section section) {
        Map<String, Object> result = new HashMap<>();
        Set<?> keys = section.getKeys();
        
        for (Object key : keys) {
            String keyStr = key.toString();
            Object value = section.get(keyStr);
            
            if (value instanceof Section nestedSection) {
                result.put(keyStr, sectionToMap(nestedSection));
            } else {
                result.put(keyStr, value);
            }
        }
        
        return result;
    }
}
