package com.autostartstop.template.impl;

import com.autostartstop.Log;
import com.autostartstop.action.Action;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.config.ConfigAccessor;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.template.Template;
import com.autostartstop.template.TemplateContext;
import com.autostartstop.template.TemplateType;
import com.autostartstop.trigger.impl.PingTrigger;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Template that customizes ping responses based on server status.
 * 
 * <p>Configuration:
 * <pre>
 * rule_name:
 *   template: 'respond_ping'
 *   virtual_hosts:
 *     - play.example.com
 *   offline:
 *     use_cached_motd: true
 *     motd: ''
 *     version_name: '<blue>◉ Sleeping'
 *     protocol_version: -1
 *     icon: '/path/to/offline-icon.png'  # Optional: file path or base64 string
 *   online:
 *     use_cached_motd: false
 *     motd: ''
 *     version_name: ''
 *     protocol_version: -1
 *     icon: '/path/to/online-icon.png'  # Optional: file path or base64 string
 * </pre>
 * 
 * <p>At least one of offline or online sections is required.
 */
public class RespondPingTemplate implements Template {
    private static final Logger logger = Log.get(RespondPingTemplate.class);
    
    private final TemplateContext context;
    private final List<String> virtualHosts;
    private final List<String> servers;
    private final OfflineConfig offlineConfig;
    private final OnlineConfig onlineConfig;
    
    private String ruleName;
    private PingTrigger trigger;
    private boolean activated = false;
    
    // Cached lookup map: virtual_host (lowercase) -> server name (computed once during activate)
    private Map<String, String> virtualHostToServerMap;

    /**
     * Creates a RespondPingTemplate from the given configuration.
     */
    @SuppressWarnings("unchecked")
    public static RespondPingTemplate create(TemplateConfig config, TemplateContext context) {
        List<String> virtualHosts = config.getStringList("virtual_hosts");
        List<String> servers = config.getStringList("servers");
        
        // At least one of virtual_hosts or servers must be specified
        if ((virtualHosts == null || virtualHosts.isEmpty()) && 
            (servers == null || servers.isEmpty())) {
            throw new IllegalArgumentException("respond_ping template requires at least one of 'virtual_hosts' or 'servers'");
        }

        // Parse offline config
        Object offlineObj = config.get("offline");
        OfflineConfig offlineConfig = null;
        if (offlineObj instanceof Map) {
            offlineConfig = new OfflineConfig((Map<String, Object>) offlineObj);
        }

        // Parse online config
        Object onlineObj = config.get("online");
        OnlineConfig onlineConfig = null;
        if (onlineObj instanceof Map) {
            onlineConfig = new OnlineConfig((Map<String, Object>) onlineObj);
        }

        // At least one config is required
        if (offlineConfig == null && onlineConfig == null) {
            throw new IllegalArgumentException("respond_ping template requires at least one of 'offline' or 'online' configuration");
        }

        return new RespondPingTemplate(context, virtualHosts, servers, offlineConfig, onlineConfig);
    }

    private RespondPingTemplate(TemplateContext context, List<String> virtualHosts, List<String> servers,
            OfflineConfig offlineConfig, OnlineConfig onlineConfig) {
        this.context = context;
        this.virtualHosts = virtualHosts;
        this.servers = servers;
        this.offlineConfig = offlineConfig;
        this.onlineConfig = onlineConfig;
    }

    @Override
    public TemplateType getType() {
        return TemplateType.RESPOND_PING;
    }

    @Override
    public void activate(String ruleName) {
        if (activated) {
            return;
        }
        
        this.ruleName = ruleName;
        logger.debug("RespondPingTemplate: activating for rule '{}' (virtual_hosts: {}, servers: {})",
                ruleName, virtualHosts, servers);

        // Create virtual host list config for the trigger (if virtual_hosts specified)
        TriggerConfig.VirtualHostListConfig virtualHostList = null;
        if (virtualHosts != null && !virtualHosts.isEmpty()) {
            Map<String, Object> virtualHostListMap = new HashMap<>();
            virtualHostListMap.put("mode", "whitelist");
            virtualHostListMap.put("virtual_hosts", virtualHosts);
            virtualHostList = new TriggerConfig.VirtualHostListConfig(virtualHostListMap);
        }

        // Create server list config for the trigger (if servers specified)
        TriggerConfig.ServerListConfig serverList = null;
        if (servers != null && !servers.isEmpty()) {
            Map<String, Object> serverListMap = new HashMap<>();
            serverListMap.put("mode", "whitelist");
            serverListMap.put("servers", servers);
            serverList = new TriggerConfig.ServerListConfig(serverListMap);
        }

        // Build lookup map for fast server name resolution
        this.virtualHostToServerMap = buildVirtualHostToServerMap();
        if (!virtualHostToServerMap.isEmpty()) {
            logger.debug("RespondPingTemplate: built lookup map with {} virtual_host -> server mappings", 
                    virtualHostToServerMap.size());
        }

        // Create the ping trigger (it will handle all filtering)
        trigger = new PingTrigger(
                context.proxy(),
                context.plugin(),
                context.serverManager(),
                virtualHostList,
                serverList,
                true); // hold_response = true to allow action to modify ping

        // Create execution callback
        Function<ExecutionContext, CompletableFuture<Void>> executionCallback = ctx -> 
                handlePing(ctx);

        // Activate the trigger
        trigger.activate(ruleName, executionCallback);
        activated = true;
        
        logger.debug("RespondPingTemplate: activated for rule '{}'", ruleName);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("RespondPingTemplate: deactivating for rule '{}'", ruleName);

        if (trigger != null) {
            trigger.deactivate();
            trigger = null;
        }

        this.ruleName = null;
        this.virtualHostToServerMap = null;
        activated = false;

        logger.debug("RespondPingTemplate: deactivated");
    }

    /**
     * Handles a ping event.
     */
    private CompletableFuture<Void> handlePing(ExecutionContext ctx) {
        // Set rule name in context for actions
        ctx.setVariable("_rule_name", ruleName);
        
        // Get virtual_host from ping event context
        String virtualHost = (String) ctx.getVariable("ping.player.virtual_host", null);
        if (virtualHost == null || virtualHost.isEmpty()) {
            logger.debug("RespondPingTemplate: no virtual_host in ping event, skipping");
            return CompletableFuture.completedFuture(null);
        }
        
        // Find server by virtual_host using lookup map
        String serverName = virtualHostToServerMap.get(virtualHost.toLowerCase());
        if (serverName == null) {
            logger.debug("RespondPingTemplate: no server found with virtual_host '{}', skipping", virtualHost);
            return CompletableFuture.completedFuture(null);
        }

        // Check server status
        boolean isOnline = context.serverManager().isServerOnline(serverName);
        logger.debug("RespondPingTemplate: server '{}' (virtual_host: '{}') is {}", 
                serverName, virtualHost, isOnline ? "ONLINE" : "OFFLINE");

        // Select appropriate config
        PingConfig selectedConfig;
        if (isOnline) {
            if (onlineConfig != null) {
                selectedConfig = onlineConfig;
            } else {
                logger.debug("RespondPingTemplate: server is online but no online config, skipping");
                return CompletableFuture.completedFuture(null);
            }
        } else {
            if (offlineConfig != null) {
                selectedConfig = offlineConfig;
            } else {
                logger.debug("RespondPingTemplate: server is offline but no offline config, skipping");
                return CompletableFuture.completedFuture(null);
            }
        }

        // Create and execute RespondPingAction with selected config
        return executeRespondPingAction(selectedConfig, ctx);
    }

    /**
     * Builds a lookup map of virtual_host (lowercase) -> server name.
     * If servers list is specified, only includes those servers.
     * Otherwise, includes all servers from config.
     */
    private Map<String, String> buildVirtualHostToServerMap() {
        Map<String, String> mapping = new HashMap<>();
        
        PluginConfig pluginConfig = context.serverManager().getPluginConfig();
        if (pluginConfig == null || pluginConfig.getServers() == null) {
            logger.debug("RespondPingTemplate: plugin config or servers not available");
            return mapping;
        }

        // Determine which servers to include in the map
        List<String> serversToCheck;
        serversToCheck = new ArrayList<>(pluginConfig.getServers().keySet());
        
        for (String serverName : serversToCheck) {
            ServerConfig serverConfig = pluginConfig.getServers().get(serverName);
            if (serverConfig != null) {
                String serverVirtualHost = serverConfig.getVirtualHost();
                if (serverVirtualHost != null && !serverVirtualHost.isBlank()) {
                    mapping.put(serverVirtualHost.toLowerCase(), serverName);
                    logger.debug("RespondPingTemplate: mapped virtual_host '{}' -> server '{}'", 
                            serverVirtualHost, serverName);
                }
            }
        }

        return mapping;
    }

    /**
     * Creates and executes a RespondPingAction with the given config.
     */
    private CompletableFuture<Void> executeRespondPingAction(PingConfig config, ExecutionContext ctx) {
        // Create ActionConfig dynamically
        ActionConfig actionConfig = new ActionConfig();
        actionConfig.setType("respond_ping");
        
        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("ping", "${ping}");
        
        if (config.getUseCachedMotd() != null) {
            rawConfig.put("use_cached_motd", config.getUseCachedMotd());
        }
        // Only add motd if it's not null and not empty
        String motd = config.getMotd();
        if (motd != null && !motd.isEmpty()) {
            rawConfig.put("motd", motd);
        }
        String versionName = config.getVersionName();
        if (versionName != null && !versionName.isEmpty()) {
            rawConfig.put("version_name", config.getVersionName());
        }
        String protocolVersion = config.getProtocolVersion();
        if (protocolVersion != null && !protocolVersion.isEmpty()) {
            rawConfig.put("protocol_version", config.getProtocolVersion());
        }
        String icon = config.getIcon();
        if (icon != null && !icon.isEmpty()) {
            rawConfig.put("icon", config.getIcon());
        }
        
        actionConfig.setRawConfig(rawConfig);

        // Create action using registry
        Action action = context.actionRegistry().create(actionConfig);
        if (action == null) {
            logger.error("RespondPingTemplate: failed to create respond_ping action");
            return CompletableFuture.completedFuture(null);
        }

        // Execute action
        logger.debug("RespondPingTemplate: executing respond_ping action");
        return action.execute(ctx);
    }

    /**
     * Base interface for ping configuration (offline/online).
     */
    private interface PingConfig {
        Boolean getUseCachedMotd();
        String getMotd();
        String getVersionName();
        String getProtocolVersion();
        String getIcon();
    }

    /**
     * Configuration for offline server ping response.
     */
    private static class OfflineConfig implements PingConfig {
        private final ConfigAccessor accessor;
        final Boolean useCachedMotd;
        final String motd;
        final String versionName;
        final String protocolVersion;
        final String icon;

        OfflineConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "offline");
            this.useCachedMotd = accessor.hasKey("use_cached_motd") ? accessor.getBoolean("use_cached_motd", false) : null;
            this.motd = accessor.getString("motd");
            this.versionName = accessor.getString("version_name");
            this.protocolVersion = accessor.getString("protocol_version");
            this.icon = accessor.getString("icon");
        }

        @Override
        public Boolean getUseCachedMotd() {
            return useCachedMotd;
        }

        @Override
        public String getMotd() {
            return motd;
        }

        @Override
        public String getVersionName() {
            return versionName;
        }

        @Override
        public String getProtocolVersion() {
            return protocolVersion;
        }

        @Override
        public String getIcon() {
            return icon;
        }
    }

    /**
     * Configuration for online server ping response.
     */
    private static class OnlineConfig implements PingConfig {
        private final ConfigAccessor accessor;
        final Boolean useCachedMotd;
        final String motd;
        final String versionName;
        final String protocolVersion;
        final String icon;

        OnlineConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "online");
            this.useCachedMotd = accessor.hasKey("use_cached_motd") ? accessor.getBoolean("use_cached_motd", false) : null;
            this.motd = accessor.getString("motd");
            this.versionName = accessor.getString("version_name");
            this.protocolVersion = accessor.getString("protocol_version");
            this.icon = accessor.getString("icon");
        }

        @Override
        public Boolean getUseCachedMotd() {
            return useCachedMotd;
        }

        @Override
        public String getMotd() {
            return motd;
        }

        @Override
        public String getVersionName() {
            return versionName;
        }

        @Override
        public String getProtocolVersion() {
            return protocolVersion;
        }

        @Override
        public String getIcon() {
            return icon;
        }
    }
}
