package com.autostartstop.context;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an isolated execution context for a rule execution.
 * Each trigger event creates an independent context that holds variables
 * and state for that specific execution.
 */
public class ExecutionContext {
    private final String executionId;
    private final Map<String, Object> variables;
    private final long startTime;
    
    /**
     * Signal that can be completed by actions (like allow_connection, allow_ping) to indicate
     * that the event should be released immediately, without waiting for all actions
     * to complete. This is lazily initialized only when needed.
     * Used for both connection and ping events.
     */
    private volatile CompletableFuture<Void> eventReleaseSignal;

    /**
     * Creates a new execution context with a unique ID.
     */
    public ExecutionContext() {
        this.executionId = UUID.randomUUID().toString();
        this.variables = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Copy constructor for creating child contexts (e.g., for preset calls).
     *
     * @param parent The parent context to copy from
     * @param additionalVars Additional variables to add to the new context
     */
    public ExecutionContext(ExecutionContext parent, Map<String, Object> additionalVars) {
        this.executionId = parent.executionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.variables = new ConcurrentHashMap<>(parent.variables);
        if (additionalVars != null) {
            this.variables.putAll(additionalVars);
        }
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Gets the unique execution ID.
     *
     * @return The execution ID
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Gets the start time of this execution.
     *
     * @return The start time in milliseconds since epoch
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Sets a variable in the context.
     *
     * @param key The variable key (e.g., "connection.player.name")
     * @param value The variable value
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * Gets a variable from the context.
     *
     * @param key The variable key
     * @return The variable value, or null if not found
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Gets a variable from the context with a default value.
     *
     * @param key The variable key
     * @param defaultValue The default value if not found
     * @return The variable value, or the default value if not found
     */
    public Object getVariable(String key, Object defaultValue) {
        return variables.getOrDefault(key, defaultValue);
    }

    /**
     * Checks if a variable exists in the context.
     *
     * @param key The variable key
     * @return true if the variable exists, false otherwise
     */
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    /**
     * Gets all variables in the context.
     *
     * @return An unmodifiable view of the variables map
     */
    public Map<String, Object> getVariables() {
        return Map.copyOf(variables);
    }

    /**
     * Gets the elapsed time since the execution started.
     *
     * @return The elapsed time in milliseconds
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Gets or creates the release signal.
     * This signal can be completed by actions (like allow_connection, allow_ping) to indicate
     * that the event should be released immediately. Used for both connection and ping events.
     *
     * @return The release signal future
     */
    public synchronized CompletableFuture<Void> getOrCreateEventReleaseSignal() {
        if (eventReleaseSignal == null) {
            eventReleaseSignal = new CompletableFuture<>();
        }
        return eventReleaseSignal;
    }

    /**
     * Checks if a release signal exists (has been created).
     *
     * @return true if the signal exists
     */
    public boolean hasEventReleaseSignal() {
        return eventReleaseSignal != null;
    }

    /**
     * Completes the release signal, indicating that the event
     * should be released immediately. Safe to call even if no signal exists.
     * Used for both connection and ping events.
     */
    public void releaseEvent() {
        if (eventReleaseSignal != null) {
            eventReleaseSignal.complete(null);
        }
    }
}
