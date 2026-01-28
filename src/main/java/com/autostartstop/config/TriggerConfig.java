package com.autostartstop.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a trigger.
 * Delegates to ConfigAccessor for type-safe parsing utilities.
 */
public class TriggerConfig {
    private String type;
    private Map<String, Object> rawConfig;
    
    // Lazy-initialized accessor
    private transient ConfigAccessor accessor;

    public TriggerConfig() {
    }

    // ========== Core Getters/Setters ==========

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

    // ========== ConfigAccessor Delegate ==========

    private ConfigAccessor accessor() {
        if (accessor == null) {
            accessor = new ConfigAccessor(rawConfig, type != null ? type : "trigger");
        }
        return accessor;
    }

    // ========== Convenience Getters for Common Fields ==========
    // These are frequently used fields that can be accessed directly

    /**
     * Gets the server name (for connection triggers).
     */
    public String getServer() {
        return getString("server");
    }

    /**
     * Gets the trigger ID (for manual triggers).
     */
    public String getId() {
        return getString("id");
    }

    /**
     * Gets the cron expression (for cron triggers).
     */
    public String getExpression() {
        return getString("expression");
    }

    /**
     * Gets the time zone (for cron triggers).
     */
    public String getTimeZone() {
        return getString("time_zone");
    }

    /**
     * Gets the cron format (for cron triggers).
     */
    public String getFormat() {
        return getString("format");
    }

    /**
     * Gets whether to deny the connection (for connection triggers).
     */
    public boolean isDenyConnection() {
        return getBoolean("deny_connection", false);
    }

    /**
     * Gets whether to hold the ping response (for ping triggers).
     */
    public boolean isHoldResponse() {
        return getBoolean("hold_response", false);
    }

    /**
     * Gets the empty time duration (for empty_server triggers).
     * Default: 15 minutes.
     */
    public Duration getEmptyTime() {
        return getDuration("empty_time", Duration.ofMinutes(15));
    }

    /**
     * Gets the player list configuration (for connection triggers).
     */
    public PlayerListConfig getPlayerList() {
        Map<String, Object> map = accessor().getMap("player_list");
        if (map.isEmpty()) {
            return null;
        }
        return new PlayerListConfig(map);
    }

    /**
     * Gets the server list configuration (for connection triggers).
     */
    public ServerListConfig getServerList() {
        Map<String, Object> map = accessor().getMap("server_list");
        if (map.isEmpty()) {
            return null;
        }
        return new ServerListConfig(map);
    }

    /**
     * Gets the virtual host list configuration (for ping triggers).
     */
    public VirtualHostListConfig getVirtualHostList() {
        Map<String, Object> map = accessor().getMap("virtual_host_list");
        if (map.isEmpty()) {
            return null;
        }
        return new VirtualHostListConfig(map);
    }

    // ========== Delegated ConfigAccessor Methods ==========

    public String getString(String key) {
        return accessor().getString(key);
    }

    public String requireString(String key) {
        return accessor().requireString(key);
    }

    public String getString(String key, String defaultValue) {
        return accessor().getString(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return accessor().getInt(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return accessor().getLong(key, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return accessor().getDouble(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return accessor().getBoolean(key, defaultValue);
    }

    public List<String> getStringList(String key) {
        return accessor().getStringList(key);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return accessor().getDuration(key, defaultValue);
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        return accessor().getEnum(key, enumClass, defaultValue);
    }

    public boolean hasKey(String key) {
        return accessor().hasKey(key);
    }

    public Object get(String key) {
        return accessor().get(key);
    }

    // ========== Nested Config Classes ==========

    /**
     * Configuration for player whitelist/blacklist filtering.
     */
    public static class PlayerListConfig {
        private final ConfigAccessor accessor;

        public PlayerListConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "player_list");
        }

        public String getMode() {
            return accessor.getString("mode", "whitelist");
        }

        public List<String> getPlayers() {
            return accessor.getStringList("players");
        }
    }

    /**
     * Configuration for server whitelist/blacklist filtering.
     */
    public static class ServerListConfig {
        private final ConfigAccessor accessor;

        public ServerListConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "server_list");
        }

        public String getMode() {
            return accessor.getString("mode", "whitelist");
        }

        public List<String> getServers() {
            return accessor.getStringList("servers");
        }
    }

    /**
     * Configuration for virtual host whitelist/blacklist filtering.
     */
    public static class VirtualHostListConfig {
        private final ConfigAccessor accessor;

        public VirtualHostListConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "virtual_host_list");
        }

        public String getMode() {
            return accessor.getString("mode", "whitelist");
        }

        public List<String> getVirtualHosts() {
            return accessor.getStringList("virtual_hosts");
        }
    }
}
