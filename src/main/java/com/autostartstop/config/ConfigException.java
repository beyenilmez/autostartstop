package com.autostartstop.config;

/**
 * Exception thrown when configuration parsing or validation fails.
 * Provides structured information about the component and parameter that caused the error.
 */
public class ConfigException extends RuntimeException {
    private final String component;
    private final String parameter;

    /**
     * Creates a new configuration exception.
     *
     * @param component The component name (e.g., "start", "send_message", "connection")
     * @param parameter The parameter name that caused the error
     * @param message The error message describing the problem
     */
    public ConfigException(String component, String parameter, String message) {
        super(formatMessage(component, parameter, message));
        this.component = component;
        this.parameter = parameter;
    }

    /**
     * Creates a new configuration exception with a cause.
     *
     * @param component The component name
     * @param parameter The parameter name that caused the error
     * @param message The error message
     * @param cause The underlying cause
     */
    public ConfigException(String component, String parameter, String message, Throwable cause) {
        super(formatMessage(component, parameter, message), cause);
        this.component = component;
        this.parameter = parameter;
    }

    /**
     * Creates a configuration exception for a missing required parameter.
     *
     * @param component The component name
     * @param parameter The missing parameter name
     * @return A ConfigException for the missing parameter
     */
    public static ConfigException required(String component, String parameter) {
        return new ConfigException(component, parameter, "is required");
    }

    /**
     * Creates a configuration exception for an invalid parameter value.
     *
     * @param component The component name
     * @param parameter The parameter name
     * @param value The invalid value
     * @return A ConfigException for the invalid value
     */
    public static ConfigException invalid(String component, String parameter, Object value) {
        return new ConfigException(component, parameter, "invalid value: " + value);
    }

    /**
     * Creates a configuration exception for an invalid parameter value with expected format.
     *
     * @param component The component name
     * @param parameter The parameter name
     * @param value The invalid value
     * @param expected Description of what was expected
     * @return A ConfigException for the invalid value
     */
    public static ConfigException invalid(String component, String parameter, Object value, String expected) {
        return new ConfigException(component, parameter, 
                "invalid value '" + value + "', expected: " + expected);
    }

    private static String formatMessage(String component, String parameter, String message) {
        if (parameter != null && !parameter.isEmpty()) {
            return String.format("%s action: parameter '%s' %s", component, parameter, message);
        } else {
            return String.format("%s action: %s", component, message);
        }
    }

    /**
     * Gets the component name that caused the error.
     *
     * @return The component name
     */
    public String getComponent() {
        return component;
    }

    /**
     * Gets the parameter name that caused the error.
     *
     * @return The parameter name
     */
    public String getParameter() {
        return parameter;
    }
}
