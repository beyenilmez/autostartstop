package com.autostartstop.server;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the startup state of a server during an active startup operation.
 * Provides dynamic values for startup_timer, startup_progress_percentage, and state.
 */
public class ServerStartupContext {
    private final String serverName;
    private final long startTimeMs;
    private final Duration expectedStartupTime;
    private final AtomicReference<String> state;
    private final AtomicLong completionTimeMs;
    private volatile boolean completed = false;

    public ServerStartupContext(String serverName, Duration expectedStartupTime) {
        this.serverName = serverName;
        this.startTimeMs = System.currentTimeMillis();
        this.expectedStartupTime = expectedStartupTime != null ? expectedStartupTime : Duration.ofSeconds(30);
        this.state = new AtomicReference<>(ServerState.STARTING.getName());
        this.completionTimeMs = new AtomicLong(0);
    }

    /**
     * Gets the server name.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Gets the current state.
     */
    public String getState() {
        return state.get();
    }

    /**
     * Sets the current state.
     */
    public void setState(String newState) {
        this.state.set(newState);
    }

    /**
     * Gets the startup timer in seconds.
     * This is calculated dynamically from the start time.
     */
    public long getStartupTimerSeconds() {
        if (completed && completionTimeMs.get() > 0) {
            return (completionTimeMs.get() - startTimeMs) / 1000;
        }
        return (System.currentTimeMillis() - startTimeMs) / 1000;
    }

    /**
     * Gets the startup timer in milliseconds.
     */
    public long getStartupTimerMs() {
        if (completed && completionTimeMs.get() > 0) {
            return completionTimeMs.get() - startTimeMs;
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Gets the startup progress percentage (0-100).
     * This is calculated from the timer and expected startup time.
     */
    public int getStartupProgressPercentage() {
        String currentState = state.get();
        if (completed || ServerState.ONLINE.getName().equals(currentState)) {
            return 100;
        }
        
        if (ServerState.OFFLINE.getName().equals(currentState) || ServerState.FAILED.getName().equals(currentState)) {
            return 0;
        }
        
        long elapsedMs = getStartupTimerMs();
        long expectedMs = expectedStartupTime.toMillis();
        
        if (expectedMs <= 0) {
            return 0;
        }
        
        int percentage = (int) ((elapsedMs * 100) / expectedMs);
        // Cap at 99% until actually online (100% is reserved for online state)
        return Math.min(99, Math.max(0, percentage));
    }

    /**
     * Gets the startup progress as a value between 0.0 and 1.0.
     * This is useful for bossbars which expect progress in this range.
     */
    public double getStartupProgress() {
        String currentState = state.get();
        if (completed || ServerState.ONLINE.getName().equals(currentState)) {
            return 1.0;
        }
        
        if (ServerState.OFFLINE.getName().equals(currentState) || ServerState.FAILED.getName().equals(currentState)) {
            return 0.0;
        }
        
        long elapsedMs = getStartupTimerMs();
        long expectedMs = expectedStartupTime.toMillis();
        
        if (expectedMs <= 0) {
            return 0.0;
        }
        
        double progress = (double) elapsedMs / expectedMs;
        // Cap at 0.99 until actually online (1.0 is reserved for online state)
        return Math.min(0.99, Math.max(0.0, progress));
    }

    /**
     * Marks the startup as completed.
     * @param finalState The final state (e.g., "online", "failed")
     */
    public void markCompleted(String finalState) {
        this.completionTimeMs.set(System.currentTimeMillis());
        this.completed = true;
        this.state.set(finalState);
    }

    /**
     * Checks if the startup is completed.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Gets the start time in milliseconds.
     */
    public long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * Gets the actual startup duration if completed.
     * @return Duration of startup, or null if not completed
     */
    public Duration getActualStartupDuration() {
        if (!completed || completionTimeMs.get() == 0) {
            return null;
        }
        return Duration.ofMillis(completionTimeMs.get() - startTimeMs);
    }

    /**
     * Gets the expected startup time.
     */
    public Duration getExpectedStartupTime() {
        return expectedStartupTime;
    }
}
