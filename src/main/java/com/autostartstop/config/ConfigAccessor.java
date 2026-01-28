package com.autostartstop.config;

import com.autostartstop.util.DurationUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for accessing configuration values from a raw config map.
 * Provides type-safe parsing methods with default values.
 * 
 * Can be used as a delegate or inherited by config classes that need to parse
 * values from a raw configuration map.
 */
public class ConfigAccessor {
    
    private final Map<String, Object> config;
    private final String componentName;

    /**
     * Creates a new ConfigAccessor.
     *
     * @param config The raw configuration map (can be null)
     * @param componentName The component name for error messages
     */
    public ConfigAccessor(Map<String, Object> config, String componentName) {
        this.config = config != null ? config : Map.of();
        this.componentName = componentName != null ? componentName : "unknown";
    }

    /**
     * Creates a new ConfigAccessor with a default component name.
     *
     * @param config The raw configuration map
     */
    public ConfigAccessor(Map<String, Object> config) {
        this(config, "config");
    }

    /**
     * Gets the raw configuration map.
     */
    public Map<String, Object> getRawConfig() {
        return config;
    }

    /**
     * Gets the component name.
     */
    public String getComponentName() {
        return componentName;
    }

    // ========== String Methods ==========

    /**
     * Gets a string value.
     *
     * @param key The key to look up
     * @return The string value, or null if not found
     */
    public String getString(String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a required string value, throwing ConfigException if missing or blank.
     *
     * @param key The key to look up
     * @return The string value (never null or blank)
     * @throws ConfigException if the value is missing or blank
     */
    public String requireString(String key) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            throw ConfigException.required(componentName, key);
        }
        return value;
    }

    /**
     * Gets a string value with a default.
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found or blank
     * @return The string value or default
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    // ========== Numeric Methods ==========

    /**
     * Gets an integer value.
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found or not a number
     * @return The integer value, or defaultValue if not found
     */
    public int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a long value.
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found or not a number
     * @return The long value, or defaultValue if not found
     */
    public long getLong(String key, long defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a double value.
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found or not a number
     * @return The double value, or defaultValue if not found
     */
    public double getDouble(String key, double defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a float value clamped to a range.
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @return The float value clamped to [min, max]
     */
    public float getFloatClamped(String key, float defaultValue, float min, float max) {
        float result = (float) getDouble(key, defaultValue);
        return Math.max(min, Math.min(max, result));
    }

    // ========== Boolean Methods ==========

    /**
     * Gets a boolean value.
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found
     * @return The boolean value, or defaultValue if not found
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        String strValue = value.toString().toLowerCase();
        if ("true".equals(strValue) || "yes".equals(strValue) || "1".equals(strValue)) {
            return true;
        }
        if ("false".equals(strValue) || "no".equals(strValue) || "0".equals(strValue)) {
            return false;
        }
        
        return defaultValue;
    }

    // ========== List Methods ==========

    /**
     * Gets a list of strings.
     * Handles both List and single String values.
     *
     * @param key The key to look up
     * @return The list of strings (never null, may be empty)
     */
    public List<String> getStringList(String key) {
        Object obj = config.get(key);
        if (obj == null) {
            return List.of();
        }
        
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        } else if (obj instanceof String s) {
            return List.of(s);
        } else {
            return List.of(obj.toString());
        }
    }

    /**
     * Gets a list of maps (for nested config structures).
     *
     * @param key The key to look up
     * @return The list of maps (never null, may be empty)
     */
    public List<Map<String, Object>> getMapList(String key) {
        Object obj = config.get(key);
        if (obj == null) {
            return List.of();
        }
        
        if (obj instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> converted = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        converted.put(entry.getKey().toString(), entry.getValue());
                    }
                    result.add(converted);
                }
            }
            return result;
        }
        
        return List.of();
    }

    // ========== Map Methods ==========

    /**
     * Gets a nested map.
     *
     * @param key The key to look up
     * @return The nested map, or empty map if not found
     */
    public Map<String, Object> getMap(String key) {
        Object obj = config.get(key);
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    /**
     * Gets a ConfigAccessor for a nested section.
     *
     * @param key The key to look up
     * @return A ConfigAccessor for the nested section
     */
    public ConfigAccessor getSection(String key) {
        return new ConfigAccessor(getMap(key), componentName + "." + key);
    }

    // ========== Duration Methods ==========

    /**
     * Gets a duration value.
     * Supports duration strings like "1s", "500ms", "2m", "10t" (ticks).
     *
     * @param key The key to look up
     * @param defaultValue The default value if not found or invalid
     * @return The duration, or defaultValue if not found or invalid
     */
    public Duration getDuration(String key, Duration defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        
        try {
            return DurationUtil.parse(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a required duration value.
     *
     * @param key The key to look up
     * @return The duration (never null)
     * @throws ConfigException if the value is missing or invalid
     */
    public Duration requireDuration(String key) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            throw ConfigException.required(componentName, key);
        }
        
        try {
            return DurationUtil.parse(value);
        } catch (Exception e) {
            throw ConfigException.invalid(componentName, key, value, "a valid duration (e.g., '1s', '500ms', '2m')");
        }
    }

    // ========== Enum Methods ==========

    /**
     * Gets an enum value.
     *
     * @param key The key to look up
     * @param enumClass The enum class
     * @param defaultValue The default value if not found or invalid
     * @param <T> The enum type
     * @return The enum value, or defaultValue if not found or invalid
     */
    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try lowercase with underscores replaced
            try {
                String normalized = value.toUpperCase().replace('-', '_');
                return Enum.valueOf(enumClass, normalized);
            } catch (IllegalArgumentException e2) {
                return defaultValue;
            }
        }
    }

    // ========== Utility Methods ==========

    /**
     * Checks if a key exists in the config.
     *
     * @param key The key to check
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        return config.containsKey(key);
    }

    /**
     * Gets a raw object value.
     *
     * @param key The key to look up
     * @return The raw object value, or null if not found
     */
    public Object get(String key) {
        return config.get(key);
    }

    /**
     * Checks if the config is empty.
     *
     * @return true if the config is empty
     */
    public boolean isEmpty() {
        return config.isEmpty();
    }

    /**
     * Gets all keys in the config.
     *
     * @return Set of keys
     */
    public java.util.Set<String> getKeys() {
        return config.keySet();
    }
}
