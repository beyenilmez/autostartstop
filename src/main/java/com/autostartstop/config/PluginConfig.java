package com.autostartstop.config;

import java.util.Map;

/**
 * Main plugin configuration.
 */
public class PluginConfig {
    private int version = 1;
    private SettingsConfig settings;
    private DefaultsConfig defaults;
    private Map<String, ServerConfig> servers;
    private Map<String, RuleConfig> rules;

    public PluginConfig() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public SettingsConfig getSettings() {
        return settings;
    }

    public void setSettings(SettingsConfig settings) {
        this.settings = settings;
    }

    public DefaultsConfig getDefaults() {
        return defaults;
    }

    public void setDefaults(DefaultsConfig defaults) {
        this.defaults = defaults;
    }

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    public Map<String, RuleConfig> getRules() {
        return rules;
    }

    public void setRules(Map<String, RuleConfig> rules) {
        this.rules = rules;
    }
}
