package com.autostartstop.config;

/**
 * Plugin settings configuration.
 */
public class SettingsConfig {
    private String shutdownTimeout = "30s";
    private String emptyServerCheckInterval = "5m";
    private String motdCacheInterval = "15m";
    private boolean checkForUpdates = true;

    public SettingsConfig() {
    }

    public String getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(String shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public String getEmptyServerCheckInterval() {
        return emptyServerCheckInterval;
    }

    public void setEmptyServerCheckInterval(String emptyServerCheckInterval) {
        this.emptyServerCheckInterval = emptyServerCheckInterval;
    }

    public String getMotdCacheInterval() {
        return motdCacheInterval;
    }

    public void setMotdCacheInterval(String motdCacheInterval) {
        this.motdCacheInterval = motdCacheInterval;
    }

    public boolean isCheckForUpdates() {
        return checkForUpdates;
    }

    public void setCheckForUpdates(boolean checkForUpdates) {
        this.checkForUpdates = checkForUpdates;
    }
}
