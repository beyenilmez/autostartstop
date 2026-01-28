package com.autostartstop.api;

import com.autostartstop.server.ServerState;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for server control APIs.
 * Implementations handle starting, stopping, and restarting servers
 * through various control mechanisms (shell commands, AMP, etc.).
 */
public interface ServerControlApi {

    /**
     * Starts the server.
     *
     * @return A CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> start();

    /**
     * Stops the server.
     *
     * @return A CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> stop();

    /**
     * Restarts the server.
     *
     * @return A CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> restart();

    /**
     * Gets the type identifier for this control API.
     *
     * @return The API type name
     */
    String getType();

    /**
     * Checks if this control API supports pinging the server to check its status.
     *
     * @return true if ping() is supported, false otherwise
     */
    default boolean supportsPing() {
        return false;
    }

    /**
     * Pings the server to check if it's online using the control API.
     * Only call this if supportsPing() returns true.
     *
     * @return A CompletableFuture that completes with true if online, false if offline
     */
    default CompletableFuture<Boolean> ping() {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Checks if this control API supports getting the server state.
     *
     * @return true if getState() is supported, false otherwise
     */
    default boolean supportsState() {
        return false;
    }

    /**
     * Gets the current state of the server.
     * Returns a normalized ServerState enum value.
     *
     * @return The current server state as ServerState enum
     */
    default ServerState getState() {
        return ServerState.UNKNOWN;
    }

    /**
     * Checks if this control API supports sending commands to the server console.
     *
     * @return true if sendCommand() is supported, false otherwise
     */
    default boolean supportsCommandSending() {
        return false;
    }

    /**
     * Sends a command to the server console.
     * Only call this if supportsCommandSending() returns true.
     *
     * @param command The command to send to the server console
     * @return A CompletableFuture that completes with true if successful, false otherwise
     */
    default CompletableFuture<Boolean> sendCommand(String command) {
        return CompletableFuture.completedFuture(false);
    }
}
