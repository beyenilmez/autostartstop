package com.autostartstop.config;

/**
 * Interface for types that have a configuration name.
 * Provides a common pattern for enums that map to configuration values.
 */
public interface ConfigNamedType {
    
    /**
     * Gets the configuration name for this type.
     *
     * @return The configuration name
     */
    String getConfigName();

    /**
     * Utility method to find an enum constant by its configuration name.
     * This provides a reusable implementation of the fromConfigName pattern.
     *
     * @param <T> The enum type
     * @param enumClass The enum class
     * @param configName The configuration name to search for
     * @return The matching enum constant, or null if not found
     */
    static <T extends Enum<T> & ConfigNamedType> T fromConfigName(
            Class<T> enumClass, String configName) {
        if (configName == null) {
            return null;
        }
        for (T constant : enumClass.getEnumConstants()) {
            if (constant.getConfigName().equalsIgnoreCase(configName)) {
                return constant;
            }
        }
        return null;
    }

    /**
     * Gets all valid configuration names for an enum type.
     *
     * @param <T> The enum type
     * @param enumClass The enum class
     * @return Comma-separated list of valid configuration names
     */
    static <T extends Enum<T> & ConfigNamedType> String getValidNames(Class<T> enumClass) {
        StringBuilder sb = new StringBuilder();
        for (T constant : enumClass.getEnumConstants()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(constant.getConfigName());
        }
        return sb.toString();
    }
}
