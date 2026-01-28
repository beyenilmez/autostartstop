package com.autostartstop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified logging utility that reduces boilerplate for obtaining loggers.
 * 
 * <p>Instead of:
 * <pre>
 * private static final Logger logger = PluginLogger.wrap(LoggerFactory.getLogger(MyClass.class));
 * </pre>
 * 
 * <p>Use:
 * <pre>
 * private static final Logger logger = Log.get(MyClass.class);
 * </pre>
 */
public final class Log {
    
    private Log() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets a wrapped logger for the given class.
     * The logger will automatically prefix all messages with [AutoStartStop].
     *
     * @param clazz The class to create a logger for
     * @return A wrapped Logger instance
     */
    public static Logger get(Class<?> clazz) {
        return PluginLogger.wrap(LoggerFactory.getLogger(clazz));
    }

    /**
     * Gets a wrapped logger with the given name.
     * The logger will automatically prefix all messages with [AutoStartStop].
     *
     * @param name The logger name
     * @return A wrapped Logger instance
     */
    public static Logger get(String name) {
        return PluginLogger.wrap(LoggerFactory.getLogger(name));
    }
}
