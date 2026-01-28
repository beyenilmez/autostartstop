package com.autostartstop.config;

/**
 * Configuration for server startup timing and progress tracking.
 */
public class StartupTimerConfig {
    private String expectedStartupTime;
    private boolean autoCalculateExpectedStartupTime = false;

    public StartupTimerConfig() {
    }

    /**
     * Gets the expected startup time duration string.
     * @return The expected startup time (e.g., "30s"), or null if not set
     */
    public String getExpectedStartupTime() {
        return expectedStartupTime;
    }

    public void setExpectedStartupTime(String expectedStartupTime) {
        this.expectedStartupTime = expectedStartupTime;
    }

    /**
     * Whether to automatically calculate expected startup time based on historical data.
     * @return true if auto-calculation is enabled
     */
    public boolean isAutoCalculateExpectedStartupTime() {
        return autoCalculateExpectedStartupTime;
    }

    public void setAutoCalculateExpectedStartupTime(boolean autoCalculateExpectedStartupTime) {
        this.autoCalculateExpectedStartupTime = autoCalculateExpectedStartupTime;
    }
}
