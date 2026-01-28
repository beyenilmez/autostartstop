package com.autostartstop.config;

import java.util.List;
import java.util.Map;

/**
 * Configuration for conditions.
 */
public class ConditionConfig {
    private String mode = "all";
    private List<Map<String, Object>> checks;

    public ConditionConfig() {
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<Map<String, Object>> getChecks() {
        return checks;
    }

    public void setChecks(List<Map<String, Object>> checks) {
        this.checks = checks;
    }

    /**
     * Checks if conditions are empty (no checks defined).
     *
     * @return true if no checks are defined
     */
    public boolean isEmpty() {
        return checks == null || checks.isEmpty();
    }
}
