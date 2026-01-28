package com.autostartstop.metrics;

import com.autostartstop.Log;
import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.RuleConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.trigger.TriggerType;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Manages bStats metrics for the plugin.
 * Collects anonymous usage statistics to help improve the plugin.
 */
public class MetricsManager {
    private static final Logger logger = Log.get(MetricsManager.class);
    private static final int BSTATS_PLUGIN_ID = 29120;

    private final Metrics metrics;
    private final Supplier<PluginConfig> configSupplier;

    /**
     * Creates a new MetricsManager.
     *
     * @param plugin The main plugin instance
     * @param metricsFactory The bStats metrics factory
     * @param configSupplier Supplier for the current plugin configuration
     */
    public MetricsManager(Object plugin, Metrics.Factory metricsFactory, Supplier<PluginConfig> configSupplier) {
        this.configSupplier = configSupplier;
        this.metrics = metricsFactory.make(plugin, BSTATS_PLUGIN_ID);
        
        registerCustomCharts();
        logger.info("bStats metrics enabled - anonymous usage statistics will be collected.");
    }

    /**
     * Registers all custom charts with bStats.
     */
    private void registerCustomCharts() {
        // Server count
        metrics.addCustomChart(new SingleLineChart("autostartstop_servers", () -> {
            PluginConfig config = configSupplier.get();
            if (config == null || config.getServers() == null) {
                return 0;
            }
            return config.getServers().size();
        }));

        // Enabled rule count
        metrics.addCustomChart(new SingleLineChart("enabled_rules", () -> {
            PluginConfig config = configSupplier.get();
            if (config == null || config.getRules() == null) {
                return 0;
            }
            return (int) config.getRules().values().stream()
                    .filter(RuleConfig::isEnabled)
                    .count();
        }));

        // Control API types distribution
        metrics.addCustomChart(new AdvancedPie("control_api_types", () -> {
            Map<String, Integer> distribution = new HashMap<>();
            PluginConfig config = configSupplier.get();
            
            if (config == null || config.getServers() == null) {
                return distribution;
            }

            for (ServerConfig server : config.getServers().values()) {
                String apiType = "none";
                if (server.getControlApi() != null && server.getControlApi().getType() != null) {
                    apiType = server.getControlApi().getType();
                }
                distribution.merge(apiType, 1, (a, b) -> a + b);
            }
            
            return distribution;
        }));

        // Trigger types in use
        metrics.addCustomChart(new AdvancedPie("trigger_types", () -> {
            Map<String, Integer> distribution = new HashMap<>();
            PluginConfig config = configSupplier.get();
            
            if (config == null || config.getRules() == null) {
                return distribution;
            }

            for (RuleConfig rule : config.getRules().values()) {
                if (!rule.isEnabled()) {
                    continue;
                }
                
                // Count triggers in normal rules
                if (rule.getTriggers() != null) {
                    for (TriggerConfig trigger : rule.getTriggers()) {
                        if (trigger.getType() != null) {
                            distribution.merge(trigger.getType(), 1, (a, b) -> a + b);
                        }
                    }
                }
                
                // Count implied triggers from templates
                if (rule.isTemplateRule() && rule.getTemplate() != null) {
                    String templateName = rule.getTemplate();
                    String impliedTrigger = getImpliedTriggerForTemplate(templateName);
                    if (impliedTrigger != null) {
                        distribution.merge(impliedTrigger, 1, (a, b) -> a + b);
                    }
                }
            }
            
            return distribution;
        }));

        // Template usage
        metrics.addCustomChart(new AdvancedPie("templates", () -> {
            Map<String, Integer> distribution = new HashMap<>();
            PluginConfig config = configSupplier.get();
            
            if (config == null || config.getRules() == null) {
                return distribution;
            }

            for (RuleConfig rule : config.getRules().values()) {
                if (!rule.isEnabled() || !rule.isTemplateRule()) {
                    continue;
                }
                
                String templateName = rule.getTemplate();
                if (templateName != null) {
                    distribution.merge(templateName, 1, (a, b) -> a + b);
                }
            }
            
            return distribution;
        }));

        // Connection mode (from start_on_connection template)
        metrics.addCustomChart(new SimplePie("start_on_connection_mode", () -> {
            PluginConfig config = configSupplier.get();
            
            if (config == null || config.getRules() == null) {
                return "none";
            }

            // Find first enabled start_on_connection template
            for (RuleConfig rule : config.getRules().values()) {
                if (!rule.isEnabled() || !rule.isTemplateRule()) {
                    continue;
                }
                
                if ("start_on_connection".equals(rule.getTemplate())) {
                    if (rule.getTemplateConfig() != null && rule.getTemplateConfig().getRawConfig() != null) {
                        Object mode = rule.getTemplateConfig().getRawConfig().get("mode");
                        if (mode != null) {
                            return mode.toString();
                        }
                    }
                    return "none"; // Default mode
                }
            }
            
            return "not_used";
        }));

        // Uses virtual hosts feature
        metrics.addCustomChart(new SimplePie("uses_virtual_hosts", () -> {
            PluginConfig config = configSupplier.get();
            
            if (config == null || config.getServers() == null) {
                return "false";
            }

            for (ServerConfig server : config.getServers().values()) {
                if (server.getVirtualHost() != null && !server.getVirtualHost().isEmpty()) {
                    return "true";
                }
            }
            
            return "false";
        }));

        // Ping method used
        metrics.addCustomChart(new AdvancedPie("ping_methods", () -> {
            Map<String, Integer> distribution = new HashMap<>();
            PluginConfig config = configSupplier.get();
            
            if (config == null || config.getServers() == null) {
                return distribution;
            }

            for (ServerConfig server : config.getServers().values()) {
                String method = "velocity"; // Default
                if (server.getPing() != null && server.getPing().getMethod() != null) {
                    method = server.getPing().getMethod();
                }
                distribution.merge(method, 1, (a, b) -> a + b);
            }
            
            return distribution;
        }));

        logger.debug("Registered {} custom bStats charts", 8);
    }

    /**
     * Gets the implied trigger type for a given template.
     *
     * @param templateName The template name
     * @return The implied trigger type name, or null if unknown
     */
    private String getImpliedTriggerForTemplate(String templateName) {
        return switch (templateName) {
            case "start_on_proxy_start" -> TriggerType.PROXY_START.getConfigName();
            case "stop_on_proxy_shutdown" -> TriggerType.PROXY_SHUTDOWN.getConfigName();
            case "start_on_connection" -> TriggerType.CONNECTION.getConfigName();
            case "stop_on_empty" -> TriggerType.EMPTY_SERVER.getConfigName();
            case "respond_ping" -> TriggerType.PING.getConfigName();
            default -> null;
        };
    }
}
