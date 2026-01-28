package com.autostartstop.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for server control API (shell or AMP).
 */
public class ControlApiConfig {
    private String type;
    private Map<String, Object> rawConfig;
    private transient ConfigAccessor accessor;

    public ControlApiConfig() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        this.accessor = null;
    }

    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }

    public void setRawConfig(Map<String, Object> rawConfig) {
        this.rawConfig = rawConfig;
        this.accessor = null;
    }

    private ConfigAccessor accessor() {
        if (accessor == null) {
            accessor = new ConfigAccessor(rawConfig, type != null ? type : "control_api");
        }
        return accessor;
    }

    // ========== Shell API ==========

    public String getStartCommand() {
        return getString("start_command");
    }

    public String getStopCommand() {
        return getString("stop_command");
    }

    public String getRestartCommand() {
        return getString("restart_command");
    }

    public String getSendCommandCommand() {
        return getString("send_command_command");
    }

    public String getWorkingDirectory() {
        return getString("working_directory");
    }

    public String getCommandTimeout() {
        return getString("command_timeout");
    }

    public Map<String, String> getEnvironment() {
        Map<String, Object> envMap = accessor().getMap("environment");
        if (envMap.isEmpty()) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : envMap.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }

    // ========== AMP API ==========

    public String getAdsUrl() {
        return getString("ads_url");
    }

    public String getUsername() {
        return getString("username");
    }

    public String getPassword() {
        return getString("password");
    }

    public String getToken() {
        return getString("token", "");
    }

    public boolean isRememberMe() {
        return getBoolean("remember_me", false);
    }

    public String getInstanceId() {
        return getString("instance_id");
    }

    public String getStart() {
        return getString("start");
    }

    public String getStop() {
        return getString("stop");
    }

    public String getInstanceStartTimeout() {
        return getString("instance_start_timeout");
    }

    // ========== Pterodactyl API ==========

    public String getPanelUrl() {
        return getString("panel_url");
    }

    public String getApiKey() {
        return getString("api_key");
    }

    public String getServerId() {
        return getString("server_id");
    }

    // ========== Accessor Delegates ==========

    public String getString(String key) {
        return accessor().getString(key);
    }

    public String getString(String key, String defaultValue) {
        return accessor().getString(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return accessor().getInt(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return accessor().getBoolean(key, defaultValue);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return accessor().getDuration(key, defaultValue);
    }

    public boolean hasKey(String key) {
        return accessor().hasKey(key);
    }
}
