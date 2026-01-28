package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.server.ServerManager;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Trigger that fires when a player attempts to connect to a specific server.
 * Self-contained: registers for ServerPreConnectEvent and invokes callback when
 * triggered.
 * Emitted context:
 * - ${connection.player} - Player object
 * - ${connection.player.name} - Username
 * - ${connection.player.uuid} - UUID
 * - ${connection.server} - RegisteredServer object
 * - ${connection.server.name} - Server name
 * - ${connection.server.status} - Server status (online/offline)
 * - ${connection.server.player_count} - Number of players on server
 * - ${connection.server.players} - Collection of players on server
 * - ${connection} - Connection event
 */
public class ConnectionTrigger implements Trigger {
    private static final Logger logger = Log.get(ConnectionTrigger.class);

    // Injected dependencies
    private final ProxyServer proxy;
    private final Object plugin;
    private final ServerManager serverManager;

    // Configuration
    private final TriggerConfig.ServerListConfig serverList;
    private final TriggerConfig.PlayerListConfig playerList;
    private final boolean denyConnection;

    // Runtime state (set during activate)
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private boolean activated = false;

    /**
     * Creates a ConnectionTrigger from the given configuration.
     */
    public static ConnectionTrigger create(TriggerConfig config, TriggerContext context) {
        TriggerConfig.ServerListConfig serverList = config.getServerList();
        TriggerConfig.PlayerListConfig playerList = config.getPlayerList();
        boolean denyConnection = config.isDenyConnection();

        return new ConnectionTrigger(context.proxy(), context.plugin(), context.serverManager(), 
                serverList, playerList, denyConnection);
    }

    public ConnectionTrigger(ProxyServer proxy, Object plugin, ServerManager serverManager,
            TriggerConfig.ServerListConfig serverList, TriggerConfig.PlayerListConfig playerList, boolean denyConnection) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.serverManager = serverManager;
        this.serverList = serverList;
        this.playerList = playerList;
        this.denyConnection = denyConnection;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.CONNECTION;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;
        this.activated = true;

        String serverFilterInfo = serverList != null && serverList.getServers() != null 
                ? serverList.getMode() + ":" + serverList.getServers().size() 
                : "none";
        logger.debug("ConnectionTrigger: activating for rule '{}' (server filter: {})",
                ruleName, serverFilterInfo);

        // Register for the event
        proxy.getEventManager().register(plugin, this);

        logger.debug("ConnectionTrigger: registered for ServerPreConnectEvent");
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("ConnectionTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        proxy.getEventManager().unregisterListener(plugin, this);

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;

        logger.debug("ConnectionTrigger: unregistered from event manager");
    }

    @Subscribe(priority = 50)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (!activated || executionCallback == null) {
            return;
        }

        Player player = event.getPlayer();
        RegisteredServer originalTarget = event.getOriginalServer();

        if (originalTarget == null) {
            logger.debug("ConnectionTrigger: ignoring connection event with null target server");
            return;
        }

        String serverName = originalTarget.getServerInfo().getName();
        String playerName = player.getUsername();

        // Check server list filter
        if (serverList != null && serverList.getServers() != null && !serverList.getServers().isEmpty()) {
            List<String> servers = serverList.getServers();
            String mode = serverList.getMode();
            
            // Default to whitelist if mode is not specified
            if (mode == null || mode.isBlank()) {
                mode = "whitelist";
            }

            if ("blacklist".equalsIgnoreCase(mode)) {
                // Block servers in the blacklist
                if (servers.contains(serverName)) {
                    // Server is blacklisted - skip silently (this is expected behavior)
                    return;
                }
            } else if (!"disabled".equalsIgnoreCase(mode)) {
                // Whitelist mode (default)
                if (!servers.contains(serverName)) {
                    // Server not in whitelist - skip silently (this is expected behavior)
                    return;
                }
            }
        }

        // Check player list filter
        if (playerList != null && playerList.getPlayers() != null && !playerList.getPlayers().isEmpty()) {
            List<String> players = playerList.getPlayers();
            String mode = playerList.getMode();
            
            // Default to whitelist if mode is not specified
            if (mode == null || mode.isBlank()) {
                mode = "whitelist";
            }

            if ("blacklist".equalsIgnoreCase(mode)) {
                // Block players in the blacklist
                if (players.contains(playerName)) {
                    // Player is blacklisted - skip silently (this is expected behavior)
                    return;
                }
            } else if (!"disabled".equalsIgnoreCase(mode)) {
                // Whitelist mode (default)
                if (!players.contains(playerName)) {
                    // Player not in whitelist - skip silently (this is expected behavior)
                    return;
                }
            }
        }

        logger.debug("ConnectionTrigger: player '{}' connecting to '{}' matched rule '{}'",
                playerName, serverName, ruleName);

        // Deny connection if configured
        if (denyConnection) {
            logger.debug("ConnectionTrigger: denying connection for player '{}' to '{}' (deny_connection=true)", 
                    playerName, serverName);
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.CONNECTION.getConfigName());

        // Emit context variables
        emitContext(context, player, originalTarget, event);

        // If deny_connection is true, set up the release signal so allow_connection can
        // signal early release
        CompletableFuture<Void> releaseSignal = null;
        if (denyConnection) {
            releaseSignal = context.getOrCreateEventReleaseSignal();
        }

        // Invoke the execution callback
        logger.debug("ConnectionTrigger: invoking execution callback for rule '{}'", ruleName);
        CompletableFuture<Void> executionFuture = executionCallback.apply(context);

        // If deny_connection is true, we must wait for either:
        // 1. The rule execution to complete, OR
        // 2. An action (like allow_connection) to signal early release
        // This allows the player to be connected immediately when allow_connection runs,
        // while remaining actions continue executing in the background.
        if (denyConnection && executionFuture != null) {
            logger.debug("ConnectionTrigger: waiting for rule execution or release signal (deny_connection=true)");
            try {
                // Wait for either the execution to complete OR the release signal
                CompletableFuture.anyOf(executionFuture, releaseSignal).join();
                
                if (releaseSignal != null && releaseSignal.isDone() && !executionFuture.isDone()) {
                    logger.debug("ConnectionTrigger: connection released early by action for rule '{}'", ruleName);
                } else {
                    logger.debug("ConnectionTrigger: rule execution completed for '{}'", ruleName);
                }
            } catch (Exception e) {
                logger.error("ConnectionTrigger: error waiting for rule execution for '{}': {}", 
                        ruleName, e.getMessage());
                logger.debug("ConnectionTrigger: execution error details:", e);
            }
        }
    }

    /**
     * Emits the connection context variables.
     */
    private void emitContext(ExecutionContext context, Player player,
            RegisteredServer registeredServer, ServerPreConnectEvent event) {
        String serverName = registeredServer.getServerInfo().getName();

        // Player variables
        context.setVariable("connection.player", player);
        context.setVariable("connection.player.name", player.getUsername());
        context.setVariable("connection.player.uuid", player.getUniqueId().toString());

        // Server variables
        context.setVariable("connection.server", registeredServer);
        context.setVariable("connection.server.name", serverName);

        // Server status (requires ping)
        boolean online = serverManager.isServerOnline(serverName);
        context.setVariable("connection.server.status", online ? "online" : "offline");

        // Server players
        Collection<Player> players = registeredServer.getPlayersConnected();
        context.setVariable("connection.server.players", players);
        context.setVariable("connection.server.player_count", players.size());

        // Event
        context.setVariable("connection", event);
    }
}
