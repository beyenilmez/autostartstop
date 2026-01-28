package com.autostartstop.server;

import com.autostartstop.api.ServerControlApi;
import com.autostartstop.config.PingConfig;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.util.DurationUtil;
import com.autostartstop.Log;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Represents a server managed by the plugin.
 * Wraps a Velocity RegisteredServer with control capabilities.
 */
public class ManagedServer {
    private static final Logger logger = Log.get(ManagedServer.class);
    
    private final String name;
    private final ServerConfig config;
    private final ServerControlApi controlApi;
    private RegisteredServer registeredServer;

    public ManagedServer(String name, ServerConfig config, ServerControlApi controlApi) {
        this.name = name;
        this.config = config;
        this.controlApi = controlApi;
        logger.debug("Created ManagedServer instance for '{}'", name);
    }

    /**
     * Gets the name of this server.
     *
     * @return The server name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the configuration for this server.
     *
     * @return The server configuration
     */
    public ServerConfig getConfig() {
        return config;
    }

    /**
     * Gets the control API for this server.
     *
     * @return The control API
     */
    public ServerControlApi getControlApi() {
        return controlApi;
    }

    /**
     * Gets the Velocity RegisteredServer.
     *
     * @return The RegisteredServer, or null if not linked
     */
    public RegisteredServer getRegisteredServer() {
        return registeredServer;
    }

    /**
     * Links this managed server to a Velocity RegisteredServer.
     *
     * @param registeredServer The RegisteredServer to link
     */
    public void setRegisteredServer(RegisteredServer registeredServer) {
        this.registeredServer = registeredServer;
    }

    /**
     * Starts this server if not already online.
     *
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> start() {
        return start(false);
    }

    /**
     * Starts this server.
     *
     * @param force If true, sends start command even if server appears online
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> start(boolean force) {
        if (controlApi == null) {
            logger.warn("Cannot start server '{}': control API not available (check debug logs for errors)", name);
            return CompletableFuture.completedFuture(false);
        }
        
        // Check if already online (skip if force is true)
        if (!force && isOnline()) {
            logger.info("Server '{}' is already online, skipping start", name);
            return CompletableFuture.completedFuture(true);
        }
        
        logger.debug("Server '{}': delegating start to control API (type: {}){}", 
                name, controlApi.getType(), force ? " [forced]" : "");
        return controlApi.start()
                .thenApply(result -> {
                    if (result) {
                        logger.debug("Server '{}': start command succeeded", name);
                    } else {
                        logger.warn("Server '{}': start command returned failure", name);
                    }
                    return result;
                })
                .exceptionally(e -> {
                    logger.error("Server '{}': start command threw exception: {}", name, e.getMessage());
                    logger.debug("Start exception details for server '{}':", name, e);
                    return false;
                });
    }

    /**
     * Stops this server.
     *
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> stop() {
        if (controlApi == null) {
            logger.warn("Cannot stop server '{}': control API not available (check debug logs for errors)", name);
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Server '{}': delegating stop to control API (type: {})", name, controlApi.getType());
        return controlApi.stop()
                .thenApply(result -> {
                    if (result) {
                        logger.debug("Server '{}': stop command succeeded", name);
                    } else {
                        logger.warn("Server '{}': stop command returned failure", name);
                    }
                    return result;
                })
                .exceptionally(e -> {
                    logger.error("Server '{}': stop command threw exception: {}", name, e.getMessage());
                    logger.debug("Stop exception details for server '{}':", name, e);
                    return false;
                });
    }

    /**
     * Restarts this server.
     *
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> restart() {
        if (controlApi == null) {
            logger.warn("Cannot restart server '{}': control API not available (check debug logs for errors)", name);
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Server '{}': delegating restart to control API (type: {})", name, controlApi.getType());
        return controlApi.restart()
                .thenApply(result -> {
                    if (result) {
                        logger.debug("Server '{}': restart command succeeded", name);
                    } else {
                        logger.warn("Server '{}': restart command returned failure", name);
                    }
                    return result;
                })
                .exceptionally(e -> {
                    logger.error("Server '{}': restart command threw exception: {}", name, e.getMessage());
                    logger.debug("Restart exception details for server '{}':", name, e);
                    return false;
                });
    }

    /**
     * Sends a command to this server's console.
     *
     * @param command The command to send
     * @return A CompletableFuture with the result
     */
    public CompletableFuture<Boolean> sendCommand(String command) {
        if (controlApi == null) {
            logger.warn("Cannot send command to server '{}': control API not available (check debug logs for errors)", name);
            return CompletableFuture.completedFuture(false);
        }
        
        if (!controlApi.supportsCommandSending()) {
            logger.warn("Cannot send command to server '{}': control API (type: {}) does not support command sending", 
                    name, controlApi.getType());
            return CompletableFuture.completedFuture(false);
        }
        
        logger.debug("Server '{}': delegating command send to control API (type: {}): {}", 
                name, controlApi.getType(), command);
        return controlApi.sendCommand(command)
                .thenApply(result -> {
                    if (result) {
                        logger.debug("Server '{}': command sent successfully", name);
                    } else {
                        logger.warn("Server '{}': command send returned failure", name);
                    }
                    return result;
                })
                .exceptionally(e -> {
                    logger.error("Server '{}': command send threw exception: {}", name, e.getMessage());
                    logger.debug("Command send exception details for server '{}':", name, e);
                    return false;
                });
    }

    /**
     * Gets the ping timeout for this server.
     *
     * @return The ping timeout duration
     */
    public Duration getPingTimeout() {
        PingConfig pingConfig = config.getPing();
        if (pingConfig != null) {
            String timeout = pingConfig.getTimeout();
            if (timeout != null && !timeout.isBlank()) {
                try {
                    Duration parsed = DurationUtil.parse(timeout);
                    logger.debug("Server '{}': using configured ping timeout: {}", name, timeout);
                    return parsed;
                } catch (IllegalArgumentException e) {
                    logger.warn("Server '{}': invalid ping.timeout '{}', using default 30s. Error: {}", 
                            name, timeout, e.getMessage());
                }
            }
        }
        return Duration.ofSeconds(30);
    }

    /**
     * Gets the configured ping method for this server.
     *
     * @return The ping method ("velocity" or "control_api"), defaults to "velocity"
     */
    public String getPingMethod() {
        PingConfig pingConfig = config.getPing();
        if (pingConfig != null) {
            String method = pingConfig.getMethod();
            if (method != null && !method.isBlank()) {
                return method.toLowerCase();
            }
        }
        return "velocity";
    }

    /**
     * Checks if this server is online by pinging it.
     * Uses the configured ping_method setting, falling back to velocity ping
     * if control_api ping is not supported.
     *
     * @return true if online, false if offline/unreachable
     */
    public boolean isOnline() {
        String pingMethod = getPingMethod();
        
        // Try control_api ping if configured
        if ("control_api".equals(pingMethod)) {
            if (controlApi != null && controlApi.supportsPing()) {
                logger.debug("Pinging server '{}' using control_api (type: {})", name, controlApi.getType());
                try {
                    Duration timeout = getPingTimeout();
                    return controlApi.ping()
                            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                            .handle((online, throwable) -> {
                                if (throwable != null) {
                                    logger.debug("Server '{}' control_api ping failed: {}", name, throwable.getMessage());
                                    return false;
                                }
                                logger.debug("Server '{}' control_api ping result: {}", name, online ? "ONLINE" : "OFFLINE");
                                return online;
                            })
                            .join();
                } catch (Exception e) {
                    logger.debug("Server '{}' control_api ping threw exception: {}", name, e.getMessage());
                    return false;
                }
            } else {
                // Fallback to velocity ping if control API doesn't support ping
                logger.debug("Server '{}': control_api ping requested but not supported (type: {}), falling back to velocity ping",
                        name, controlApi != null ? controlApi.getType() : "null");
            }
        }

        // Use velocity ping (default or fallback)
        return pingViaVelocity();
    }

    /**
     * Pings the server using Velocity's built-in ping mechanism.
     *
     * @return true if online, false if offline/unreachable
     */
    private boolean pingViaVelocity() {
        if (registeredServer == null) {
            logger.debug("Server '{}' has no RegisteredServer, returning offline", name);
            return false;
        }

        Duration timeout = getPingTimeout();
        logger.debug("Pinging server '{}' via velocity (timeout: {}ms)", name, timeout.toMillis());

        return registeredServer.ping()
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((ping, throwable) -> {
                    if (throwable != null) {
                        logger.debug("Server '{}' velocity ping failed: {}", name, throwable.getMessage());
                        return false;
                    }
                    logger.debug("Server '{}' is online (velocity ping)", name);
                    return true;
                })
                .join();
    }

    /**
     * Gets the current state of this server.
     * Returns normalized ServerState enum value.
     * For AMP: Returns normalized ApplicationState from control API.
     * For Shell: Returns normalized state based on ping (starting state is managed by ServerStartupTracker)
     *
     * @return The normalized server state as ServerState enum
     */
    public ServerState getState() {
        if (controlApi != null && controlApi.supportsState()) {
            ServerState state = controlApi.getState();
            logger.debug("Server '{}': got state from control API: {}", name, state);
            return state;
        }
        
        // For shell or unsupported APIs, normalize based on ping result
        boolean online = isOnline();
        ServerState normalizedState = online ? ServerState.ONLINE : ServerState.OFFLINE;
        logger.debug("Server '{}': determined state from ping: {}", name, normalizedState);
        return normalizedState;
    }

    /**
     * Checks if the control API supports state queries.
     *
     * @return true if state queries are supported
     */
    public boolean supportsState() {
        return controlApi != null && controlApi.supportsState();
    }

}
