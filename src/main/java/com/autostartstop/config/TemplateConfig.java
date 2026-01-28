package com.autostartstop.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a template.
 * Delegates to ConfigAccessor for type-safe parsing utilities.
 */
public class TemplateConfig {
    private String template;
    private Map<String, Object> rawConfig;
    
    // Lazy-initialized accessor
    private transient ConfigAccessor accessor;

    public TemplateConfig() {
    }

    // ========== Core Getters/Setters ==========

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
        this.accessor = null;
    }

    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }

    public void setRawConfig(Map<String, Object> rawConfig) {
        this.rawConfig = rawConfig;
        this.accessor = null;
    }

    // ========== ConfigAccessor Delegate ==========

    private ConfigAccessor accessor() {
        if (accessor == null) {
            accessor = new ConfigAccessor(rawConfig, template != null ? template : "template");
        }
        return accessor;
    }

    // ========== Convenience Getters for Common Fields ==========

    /**
     * Gets the empty time duration (for stop_on_empty template).
     * Default: 15 minutes.
     */
    public Duration getEmptyTime() {
        return getDuration("empty_time", Duration.ofMinutes(15));
    }

    /**
     * Gets the list of servers (for stop_on_empty, start_on_connection templates).
     */
    public List<String> getServers() {
        return getStringList("servers");
    }

    /**
     * Gets the list of players (for start_on_connection template).
     */
    public List<String> getPlayers() {
        return getStringList("players");
    }

    /**
     * Gets the connection mode (for start_on_connection template).
     * Default: "none".
     */
    public String getMode() {
        return getString("mode", "none");
    }

    /**
     * Gets the disconnect message (for start_on_connection template with disconnect mode).
     */
    public String getDisconnectMessage() {
        return getString("disconnect_message");
    }

    // ========== Delegated ConfigAccessor Methods ==========

    public String getString(String key) {
        return accessor().getString(key);
    }

    public String requireString(String key) {
        return accessor().requireString(key);
    }

    public String getString(String key, String defaultValue) {
        return accessor().getString(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return accessor().getInt(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return accessor().getLong(key, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return accessor().getDouble(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return accessor().getBoolean(key, defaultValue);
    }

    public List<String> getStringList(String key) {
        return accessor().getStringList(key);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return accessor().getDuration(key, defaultValue);
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        return accessor().getEnum(key, enumClass, defaultValue);
    }

    public boolean hasKey(String key) {
        return accessor().hasKey(key);
    }

    public Object get(String key) {
        return accessor().get(key);
    }
}
