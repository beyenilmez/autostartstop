package com.autostartstop.server;

import com.autostartstop.api.ServerControlApi;
import com.autostartstop.api.ServerControlApiRegistry;
import com.autostartstop.config.PluginConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.Log;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all servers controlled by the plugin.
 */
public class ServerManager {
    private static final Logger logger = Log.get(ServerManager.class);
    
    private final Map<String, ManagedServer> servers = new ConcurrentHashMap<>();
    private final ProxyServer proxy;
    private final ServerControlApiRegistry apiRegistry;
    private volatile PluginConfig pluginConfig;

    public ServerManager(ProxyServer proxy, ServerControlApiRegistry apiRegistry) {
        this.proxy = proxy;
        this.apiRegistry = apiRegistry;
    }
    
    /**
     * Gets the plugin configuration.
     * 
     * @return The plugin configuration, or null if not set
     */
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }
    
    /**
     * Sets the plugin configuration.
     * 
     * @param pluginConfig The plugin configuration
     */
    public void setPluginConfig(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    /**
     * Loads servers from the plugin configuration.
     *
     * @param config The plugin configuration
     */
    public void loadServers(PluginConfig config) {
        logger.debug("Loading servers from configuration...");
        this.pluginConfig = config;
        servers.clear();

        Map<String, ServerConfig> serverConfigs = config.getServers();
        if (serverConfigs == null || serverConfigs.isEmpty()) {
            logger.warn("No servers configured in config.yml - server control features will be unavailable");
            return;
        }

        logger.debug("Processing {} server configurations", serverConfigs.size());
        int successCount = 0;
        int warningCount = 0;

        for (Map.Entry<String, ServerConfig> entry : serverConfigs.entrySet()) {
            String name = entry.getKey();
            ServerConfig serverConfig = entry.getValue();

            logger.debug("Loading server '{}' configuration...", name);

            // Create control API
            ServerControlApi controlApi = null;
            if (serverConfig.getControlApi() != null) {
                logger.debug("Server '{}': creating control API of type '{}'", 
                        name, serverConfig.getControlApi().getType());
                try {
                    controlApi = apiRegistry.create(serverConfig.getControlApi(), name);
                    if (controlApi != null) {
                        logger.debug("Server '{}': control API created successfully (type: {})", name, controlApi.getType());
                    } else {
                        logger.warn("Server '{}': failed to create control API - server control will not be available", name);
                        warningCount++;
                    }
                } catch (Exception e) {
                    logger.error("Server '{}': control API initialization failed - {}", name, e.getMessage());
                    logger.debug("Server '{}': control API creation error details:", name, e);
                    warningCount++;
                }
            } else {
                logger.debug("Server '{}': no control_api configured", name);
            }

            // Create managed server
            ManagedServer managedServer = new ManagedServer(name, serverConfig, controlApi);

            // Link to Velocity's RegisteredServer if it exists
            Optional<RegisteredServer> registeredServer = proxy.getServer(name);
            registeredServer.ifPresent(rs -> {
                managedServer.setRegisteredServer(rs);
                logger.debug("Server '{}': linked to Velocity RegisteredServer ({}:{})", 
                        name, rs.getServerInfo().getAddress().getHostString(), 
                        rs.getServerInfo().getAddress().getPort());
            });

            if (registeredServer.isEmpty()) {
                logger.warn("Server '{}' is configured in AutoStartStop but not registered in Velocity's velocity.toml - pinging will not work", name);
                warningCount++;
            }

            servers.put(name, managedServer);
            successCount++;
            logger.debug("Server '{}' loaded successfully", name);
        }

        logger.debug("Loaded {} managed servers ({} warnings)", successCount, warningCount);
        if (logger.isDebugEnabled()) {
            logger.debug("Managed servers: {}", servers.keySet());
        }
    }

    /**
     * Gets a managed server by name.
     *
     * @param name The server name
     * @return The managed server, or null if not found
     */
    public ManagedServer getServer(String name) {
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.debug("Server '{}' not found in managed servers", name);
        }
        return server;
    }

    /**
     * Gets all managed servers.
     *
     * @return Collection of all managed servers
     */
    public Collection<ManagedServer> getAllServers() {
        return servers.values();
    }

    /**
     * Checks if a server is managed by this plugin.
     *
     * @param name The server name
     * @return true if the server is managed
     */
    public boolean hasServer(String name) {
        return servers.containsKey(name);
    }

    /**
     * Starts a server by name if not already online.
     *
     * @param name The server name
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> startServer(String name) {
        return startServer(name, false);
    }

    /**
     * Starts a server by name.
     *
     * @param name The server name
     * @param force If true, sends start command even if server appears online
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> startServer(String name, boolean force) {
        logger.debug("Request to start server '{}'{}", name, force ? " [forced]" : "");
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.error("Cannot start server '{}': not found in managed servers. Available servers: {}", 
                    name, servers.keySet());
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Initiating start for server '{}'", name);
        return server.start(force);
    }

    /**
     * Stops a server by name.
     *
     * @param name The server name
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> stopServer(String name) {
        logger.debug("Request to stop server '{}'", name);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.error("Cannot stop server '{}': not found in managed servers. Available servers: {}", 
                    name, servers.keySet());
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Initiating stop for server '{}'", name);
        return server.stop();
    }

    /**
     * Restarts a server by name.
     *
     * @param name The server name
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> restartServer(String name) {
        logger.debug("Request to restart server '{}'", name);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.error("Cannot restart server '{}': not found in managed servers. Available servers: {}", 
                    name, servers.keySet());
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Initiating restart for server '{}'", name);
        return server.restart();
    }

    /**
     * Sends a command to a server's console.
     *
     * @param name The server name
     * @param command The command to send
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> sendCommand(String name, String command) {
        logger.debug("Request to send command to server '{}': {}", name, command);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.error("Cannot send command to server '{}': not found in managed servers. Available servers: {}", 
                    name, servers.keySet());
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Initiating command send for server '{}'", name);
        return server.sendCommand(command);
    }

    /**
     * Checks if a server is online.
     * Always pings the server to get the current status.
     *
     * @param name The server name
     * @return true if the server is online
     */
    public boolean isServerOnline(String name) {
        logger.debug("Checking online status for server '{}'", name);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.debug("Server '{}' not found, returning offline status", name);
            return false;
        }

        boolean online = server.isOnline();
        logger.debug("Server '{}': ping result = {}", name, online ? "ONLINE" : "OFFLINE");
        return online;
    }

    /**
     * Gets the player count for a server.
     * Returns the number of players connected to this server through the proxy.
     *
     * @param name The server name
     * @return The player count, or 0 if server not found
     */
    public int getServerPlayerCount(String name) {
        logger.debug("Getting player count for server '{}'", name);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.debug("Server '{}' not found, returning 0 players", name);
            return 0;
        }

        RegisteredServer registeredServer = server.getRegisteredServer();
        if (registeredServer == null) {
            logger.debug("Server '{}' has no Velocity RegisteredServer, returning 0 players", name);
            return 0;
        }

        int playerCount = registeredServer.getPlayersConnected().size();
        logger.debug("Server '{}': player count = {}", name, playerCount);
        return playerCount;
    }

    /**
     * Gets the players connected to a server through the proxy.
     *
     * @param name The server name
     * @return Collection of players, or empty collection if server not found
     */
    public Collection<Player> getServerPlayers(String name) {
        logger.debug("Getting players for server '{}'", name);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.debug("Server '{}' not found, returning empty collection", name);
            return Collections.emptyList();
        }

        RegisteredServer registeredServer = server.getRegisteredServer();
        if (registeredServer == null) {
            logger.debug("Server '{}' has no Velocity RegisteredServer, returning empty collection", name);
            return Collections.emptyList();
        }

        Collection<Player> players = registeredServer.getPlayersConnected();
        logger.debug("Server '{}': {} players connected", name, players.size());
        return players;
    }

    /**
     * Gets the RegisteredServer for a managed server.
     *
     * @param name The server name
     * @return The RegisteredServer, or null if not found
     */
    public RegisteredServer getRegisteredServer(String name) {
        logger.debug("Getting RegisteredServer for '{}'", name);
        ManagedServer server = servers.get(name);
        if (server == null) {
            logger.debug("Server '{}' not found", name);
            return null;
        }
        return server.getRegisteredServer();
    }

    /**
     * Clears all managed servers.
     */
    public void clear() {
        int count = servers.size();
        servers.clear();
        logger.debug("Cleared {} managed servers", count);
    }
}
