package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.server.ServerManager;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import com.autostartstop.util.MiniMessageUtil;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Trigger that fires when a client pings the proxy server (server list/MOTD requests).
 * Emitted context:
 * - ${ping} - ProxyPingEvent object
 * - ${ping.server.version_name} - Server version name
 * - ${ping.server.protocol_version} - Server protocol version
 * - ${ping.server.player_count} - Current player count
 * - ${ping.server.max_players} - Maximum players
 * - ${ping.server.motd} - Server MOTD (in MiniMessage format)
 * - ${ping.player.remote_address} - Client remote address
 * - ${ping.player.virtual_host} - Client virtual host
 * - ${ping.player.protocol_version} - Client protocol version
 */
public class PingTrigger implements Trigger {
    private static final Logger logger = Log.get(PingTrigger.class);

    // Injected dependencies
    private final ProxyServer proxy;
    private final Object plugin;
    private final ServerManager serverManager;

    // Configuration
    private final TriggerConfig.VirtualHostListConfig virtualHostList;
    private final TriggerConfig.ServerListConfig serverList;
    private final boolean holdResponse;

    // Runtime state (set during activate)
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private boolean activated = false;
    
    // Merged virtual hosts from both virtual_host_list and server_list (computed once during activate)
    private Set<String> mergedVirtualHosts;
    private String mergedMode;

    /**
     * Creates a PingTrigger from the given configuration.
     */
    public static PingTrigger create(TriggerConfig config, TriggerContext context) {
        TriggerConfig.VirtualHostListConfig virtualHostList = config.getVirtualHostList();
        TriggerConfig.ServerListConfig serverList = config.getServerList();
        boolean holdResponse = config.isHoldResponse();

        return new PingTrigger(context.proxy(), context.plugin(), context.serverManager(),
                virtualHostList, serverList, holdResponse);
    }

    public PingTrigger(ProxyServer proxy, Object plugin, ServerManager serverManager,
            TriggerConfig.VirtualHostListConfig virtualHostList, TriggerConfig.ServerListConfig serverList, boolean holdResponse) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.serverManager = serverManager;
        this.virtualHostList = virtualHostList;
        this.serverList = serverList;
        this.holdResponse = holdResponse;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.PING;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;
        this.activated = true;

        // Merge virtual hosts from both virtual_host_list and server_list into a single Set
        this.mergedVirtualHosts = new HashSet<>();
        String virtualHostListMode = null;
        String serverListMode = null;
        
        // Add virtual hosts from virtual_host_list
        if (virtualHostList != null && virtualHostList.getVirtualHosts() != null && !virtualHostList.getVirtualHosts().isEmpty()) {
            for (String vh : virtualHostList.getVirtualHosts()) {
                if (vh != null && !vh.isBlank()) {
                    mergedVirtualHosts.add(vh.toLowerCase());
                }
            }
            virtualHostListMode = virtualHostList.getMode();
            if (virtualHostListMode == null || virtualHostListMode.isBlank()) {
                virtualHostListMode = "whitelist";
            }
        }
        
        // Add virtual hosts from server_list (mapped from server names)
        if (serverList != null && serverList.getServers() != null && !serverList.getServers().isEmpty()) {
            Set<String> serverVirtualHosts = getVirtualHostsForServers(serverList.getServers());
            mergedVirtualHosts.addAll(serverVirtualHosts);
            serverListMode = serverList.getMode();
            if (serverListMode == null || serverListMode.isBlank()) {
                serverListMode = "whitelist";
            }
        }
        
        // Determine merged mode (if both exist and differ, prefer whitelist)
        if (virtualHostListMode != null && serverListMode != null) {
            if (virtualHostListMode.equalsIgnoreCase(serverListMode)) {
                this.mergedMode = virtualHostListMode;
            } else {
                // Different modes - prefer whitelist, but log warning
                this.mergedMode = "whitelist";
                logger.warn("PingTrigger: conflicting filter modes (virtual_host_list: {}, server_list: {}), using whitelist",
                        virtualHostListMode, serverListMode);
            }
        } else if (virtualHostListMode != null) {
            this.mergedMode = virtualHostListMode;
        } else if (serverListMode != null) {
            this.mergedMode = serverListMode;
        } else {
            this.mergedMode = null;
        }
        
        if (!mergedVirtualHosts.isEmpty()) {
            logger.debug("PingTrigger: merged {} virtual hosts (mode: {})", mergedVirtualHosts.size(), mergedMode);
        }
        
        String virtualHostFilterInfo = virtualHostList != null && virtualHostList.getVirtualHosts() != null 
                ? virtualHostList.getMode() + ":" + virtualHostList.getVirtualHosts().size() 
                : "none";
        String serverFilterInfo = serverList != null && serverList.getServers() != null 
                ? serverList.getMode() + ":" + serverList.getServers().size() 
                : "none";
        logger.debug("PingTrigger: activating for rule '{}' (virtual_host filter: {}, server filter: {}, hold_response: {})",
                ruleName, virtualHostFilterInfo, serverFilterInfo, holdResponse);

        // Register for the event
        proxy.getEventManager().register(plugin, this);

        logger.debug("PingTrigger: registered for ProxyPingEvent");
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("PingTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        proxy.getEventManager().unregisterListener(plugin, this);

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;
        this.mergedVirtualHosts = null;
        this.mergedMode = null;

        logger.debug("PingTrigger: unregistered from event manager");
    }

    @Subscribe(priority = 50)
    public void onProxyPing(ProxyPingEvent event) {
        if (!activated || executionCallback == null) {
            return;
        }

        InboundConnection connection = event.getConnection();
        ServerPing ping = event.getPing();

        // Get the virtual host from connection
        Optional<InetSocketAddress> virtualHostOpt = connection.getVirtualHost();
        String virtualHostStr = null;
        if (virtualHostOpt.isPresent()) {
            InetSocketAddress vh = virtualHostOpt.get();
            virtualHostStr = vh.getHostString();
        }

        // Check merged virtual host filter (single check for both virtual_host_list and server_list)
        if (mergedVirtualHosts != null && !mergedVirtualHosts.isEmpty() && mergedMode != null) {
            if (virtualHostStr != null) {
                boolean matches = mergedVirtualHosts.contains(virtualHostStr.toLowerCase());
                
                if ("blacklist".equalsIgnoreCase(mergedMode)) {
                    // Block virtual hosts in the blacklist
                    if (matches) {
                        // Virtual host is blacklisted - skip silently
                        return;
                    }
                } else if (!"disabled".equalsIgnoreCase(mergedMode)) {
                    // Whitelist mode (default)
                    if (!matches) {
                        // Virtual host not in whitelist - skip silently
                        return;
                    }
                }
            } else {
                // No virtual host present
                // In whitelist mode, if no virtual host, skip (unless disabled)
                if (!"disabled".equalsIgnoreCase(mergedMode) && !"blacklist".equalsIgnoreCase(mergedMode)) {
                    // Whitelist mode and no virtual host - skip silently
                    return;
                }
            }
        }

        logger.debug("PingTrigger: ping request matched rule '{}'", ruleName);

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.PING.getConfigName());

        // Emit context variables
        emitContext(context, connection, ping, event);

        // If hold_response is true, set up the release signal so allow_ping/deny_ping can
        // signal early release
        CompletableFuture<Void> releaseSignal = null;
        if (holdResponse) {
            releaseSignal = context.getOrCreateEventReleaseSignal();
        }

        // Invoke the execution callback
        logger.debug("PingTrigger: invoking execution callback for rule '{}'", ruleName);
        CompletableFuture<Void> executionFuture = executionCallback.apply(context);

        // If hold_response is true, we must wait for either:
        // 1. The rule execution to complete, OR
        // 2. An action (like allow_ping or deny_ping) to signal early release
        // This allows the ping to be responded to immediately when allow_ping/deny_ping runs,
        // while remaining actions continue executing in the background.
        if (holdResponse && executionFuture != null) {
            logger.debug("PingTrigger: waiting for rule execution or release signal (hold_response=true)");
            try {
                // Wait for either the execution to complete OR the release signal
                CompletableFuture.anyOf(executionFuture, releaseSignal).join();
                
                if (releaseSignal != null && releaseSignal.isDone() && !executionFuture.isDone()) {
                    logger.debug("PingTrigger: ping released early by action for rule '{}'", ruleName);
                } else {
                    logger.debug("PingTrigger: rule execution completed for '{}'", ruleName);
                }
            } catch (Exception e) {
                logger.error("PingTrigger: error waiting for rule execution for '{}': {}", 
                        ruleName, e.getMessage());
                logger.debug("PingTrigger: execution error details:", e);
            }
        }
    }

    /**
     * Emits the ping context variables.
     */
    private void emitContext(ExecutionContext context, InboundConnection connection,
            ServerPing ping, ProxyPingEvent event) {
        // Event
        context.setVariable("ping", event);

        // Server variables from ping
        ServerPing.Version version = ping.getVersion();
        if (version != null) {
            context.setVariable("ping.server.version_name", version.getName());
            context.setVariable("ping.server.protocol_version", version.getProtocol());
        } else {
            context.setVariable("ping.server.version_name", null);
            context.setVariable("ping.server.protocol_version", null);
        }

        Optional<ServerPing.Players> playersOpt = ping.getPlayers();
        if (playersOpt.isPresent()) {
            ServerPing.Players players = playersOpt.get();
            context.setVariable("ping.server.player_count", players.getOnline());
            context.setVariable("ping.server.max_players", players.getMax());
        } else {
            context.setVariable("ping.server.player_count", 0);
            context.setVariable("ping.server.max_players", 0);
        }

        // MOTD
        Component description = ping.getDescriptionComponent();
        String motd = "";
        if (description != null) {
            motd = MiniMessageUtil.serialize(description);
        }
        context.setVariable("ping.server.motd", motd);

        // Player/connection variables
        InetSocketAddress remoteAddress = connection.getRemoteAddress();
        if (remoteAddress != null) {
            String ipAddress = remoteAddress.getAddress().getHostAddress();
            context.setVariable("ping.player.remote_address", ipAddress);
        } else {
            context.setVariable("ping.player.remote_address", null);
        }

        Optional<InetSocketAddress> virtualHost = connection.getVirtualHost();
        String domainUsed = virtualHost.map(InetSocketAddress::getHostName).orElse(null);
        context.setVariable("ping.player.virtual_host", domainUsed);

        com.velocitypowered.api.network.ProtocolVersion protocolVersion = connection.getProtocolVersion();
        context.setVariable("ping.player.protocol_version", protocolVersion.getProtocol());
    }

    /**
     * Gets virtual hosts for the given server names from the plugin configuration.
     * Returns a set of lowercase virtual host strings for matching.
     */
    private Set<String> getVirtualHostsForServers(List<String> serverNames) {
        Set<String> virtualHosts = new HashSet<>();
        
        if (serverManager == null) {
            logger.debug("PingTrigger: serverManager is null, cannot resolve server virtual hosts");
            return virtualHosts;
        }
        
        PluginConfig pluginConfig = serverManager.getPluginConfig();
        if (pluginConfig == null || pluginConfig.getServers() == null) {
            logger.debug("PingTrigger: plugin config or servers not available");
            return virtualHosts;
        }
        
        for (String serverName : serverNames) {
            ServerConfig serverConfig = pluginConfig.getServers().get(serverName);
            if (serverConfig != null) {
                String virtualHost = serverConfig.getVirtualHost();
                if (virtualHost != null && !virtualHost.isBlank()) {
                    virtualHosts.add(virtualHost.toLowerCase());
                    logger.debug("PingTrigger: mapped server '{}' to virtual_host '{}'", serverName, virtualHost);
                } else {
                    logger.debug("PingTrigger: server '{}' has no virtual_host configured", serverName);
                }
            } else {
                logger.debug("PingTrigger: server '{}' not found in config", serverName);
            }
        }
        
        return virtualHosts;
    }
}
