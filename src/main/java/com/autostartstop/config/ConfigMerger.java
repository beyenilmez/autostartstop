package com.autostartstop.config;

import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Merges default configurations with specific configurations.
 * Specific values take precedence over defaults.
 */
public class ConfigMerger {
    private static final Logger logger = Log.get(ConfigMerger.class);

    /**
     * Merges a server config with default server config.
     */
    public ServerConfig mergeServerConfig(ServerConfig specific, ServerConfig defaults) {
        String serverName = specific != null ? specific.getName() : "unknown";
        logger.debug("Merging server config for '{}'", serverName);
        
        if (defaults == null) {
            return specific;
        }
        if (specific == null) {
            return cloneServerConfig(defaults);
        }

        ServerConfig merged = new ServerConfig();
        merged.setVirtualHost(coalesce(specific.getVirtualHost(), defaults.getVirtualHost()));
        merged.setPing(mergePingConfig(specific.getPing(), defaults.getPing()));
        merged.setControlApi(mergeControlApiConfig(specific.getControlApi(), defaults.getControlApi()));
        merged.setStartupTimer(mergeStartupTimerConfig(specific.getStartupTimer(), defaults.getStartupTimer()));
        
        return merged;
    }

    private PingConfig mergePingConfig(PingConfig specific, PingConfig defaults) {
        if (defaults == null) return specific;
        if (specific == null) return clonePingConfig(defaults);

        PingConfig merged = new PingConfig();
        merged.setTimeout(coalesce(specific.getTimeout(), defaults.getTimeout()));
        merged.setMethod(coalesce(specific.getMethod(), defaults.getMethod()));
        return merged;
    }

    private ControlApiConfig mergeControlApiConfig(ControlApiConfig specific, ControlApiConfig defaults) {
        if (defaults == null) return specific;
        if (specific == null) return cloneControlApiConfig(defaults);

        // Merge raw configs - specific values override defaults
        Map<String, Object> mergedRaw = new HashMap<>();
        
        if (defaults.getRawConfig() != null) {
            mergedRaw.putAll(defaults.getRawConfig());
        }
        if (specific.getRawConfig() != null) {
            mergedRaw.putAll(specific.getRawConfig());
        }
        
        // Type from specific takes precedence
        String type = coalesce(specific.getType(), defaults.getType());
        
        ControlApiConfig merged = new ControlApiConfig();
        merged.setType(type);
        merged.setRawConfig(mergedRaw);
        
        logger.debug("Control API merged (type: {})", type);
        return merged;
    }

    private StartupTimerConfig mergeStartupTimerConfig(StartupTimerConfig specific, StartupTimerConfig defaults) {
        if (defaults == null) return specific;
        if (specific == null) return cloneStartupTimerConfig(defaults);

        StartupTimerConfig merged = new StartupTimerConfig();
        merged.setExpectedStartupTime(coalesce(specific.getExpectedStartupTime(), defaults.getExpectedStartupTime()));
        merged.setAutoCalculateExpectedStartupTime(
                specific.isAutoCalculateExpectedStartupTime() || defaults.isAutoCalculateExpectedStartupTime());
        return merged;
    }

    private ServerConfig cloneServerConfig(ServerConfig source) {
        if (source == null) return null;

        ServerConfig clone = new ServerConfig();
        clone.setVirtualHost(source.getVirtualHost());
        clone.setPing(clonePingConfig(source.getPing()));
        clone.setControlApi(cloneControlApiConfig(source.getControlApi()));
        clone.setStartupTimer(cloneStartupTimerConfig(source.getStartupTimer()));
        return clone;
    }

    private PingConfig clonePingConfig(PingConfig source) {
        if (source == null) return null;

        PingConfig clone = new PingConfig();
        clone.setTimeout(source.getTimeout());
        clone.setMethod(source.getMethod());
        return clone;
    }

    private ControlApiConfig cloneControlApiConfig(ControlApiConfig source) {
        if (source == null) return null;

        ControlApiConfig clone = new ControlApiConfig();
        clone.setType(source.getType());
        if (source.getRawConfig() != null) {
            clone.setRawConfig(new HashMap<>(source.getRawConfig()));
        }
        return clone;
    }

    private StartupTimerConfig cloneStartupTimerConfig(StartupTimerConfig source) {
        if (source == null) return null;

        StartupTimerConfig clone = new StartupTimerConfig();
        clone.setExpectedStartupTime(source.getExpectedStartupTime());
        clone.setAutoCalculateExpectedStartupTime(source.isAutoCalculateExpectedStartupTime());
        return clone;
    }

    private static <T> T coalesce(T specific, T defaults) {
        return specific != null ? specific : defaults;
    }
}
