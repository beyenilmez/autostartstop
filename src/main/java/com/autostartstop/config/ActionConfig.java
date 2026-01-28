package com.autostartstop.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for an action.
 * Delegates to ConfigAccessor for type-safe parsing utilities.
 */
public class ActionConfig {
    private String type;
    private boolean waitForCompletion = true;
    private Map<String, Object> rawConfig;
    
    // Lazy-initialized accessor
    private transient ConfigAccessor accessor;

    public ActionConfig() {
    }

    // ========== Core Getters/Setters ==========

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        this.accessor = null; // Reset accessor when type changes
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public void setWaitForCompletion(boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
    }

    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }

    public void setRawConfig(Map<String, Object> rawConfig) {
        this.rawConfig = rawConfig;
        this.accessor = null; // Reset accessor when config changes
    }

    // ========== ConfigAccessor Delegate ==========

    /**
     * Gets the ConfigAccessor for this action's raw config.
     * Lazily initialized on first access.
     */
    private ConfigAccessor accessor() {
        if (accessor == null) {
            accessor = new ConfigAccessor(rawConfig, type != null ? type : "action");
        }
        return accessor;
    }

    // ========== Delegated Methods ==========

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

    public List<Map<String, Object>> getMapList(String key) {
        return accessor().getMapList(key);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return accessor().getDuration(key, defaultValue);
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        return accessor().getEnum(key, enumClass, defaultValue);
    }

    public float getFloatClamped(String key, float defaultValue, float min, float max) {
        return accessor().getFloatClamped(key, defaultValue, min, max);
    }

    public boolean hasKey(String key) {
        return accessor().hasKey(key);
    }

    public Object get(String key) {
        return accessor().get(key);
    }
}
