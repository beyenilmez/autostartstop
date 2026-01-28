package com.autostartstop.server;

import com.autostartstop.Log;
import com.autostartstop.config.ServerConfig;
import com.autostartstop.config.StartupTimerConfig;
import com.autostartstop.util.DurationUtil;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks server startup operations across all managed servers.
 * Provides access to startup state, timer, and progress for variable resolution.
 * 
 * When beginStartup() is called, a background monitor automatically polls the server
 * and completes tracking when the server comes online (or times out).
 */
public class ServerStartupTracker {
    private static final Logger logger = Log.get(ServerStartupTracker.class);
    private static final Duration DEFAULT_EXPECTED_TIME = Duration.ofSeconds(30);
    private static final long MONITOR_POLL_INTERVAL_MS = 1000; // Poll every second
    private static final long MONITOR_TIMEOUT_MS = 10 * 60 * 1000; // 10 minute timeout
    
    // Map of server name -> active startup context
    private final Map<String, ServerStartupContext> activeStartups = new ConcurrentHashMap<>();
    
    private final ServerManager serverManager;
    private final StartupTimeTracker startupTimeTracker;

    public ServerStartupTracker(ServerManager serverManager, StartupTimeTracker startupTimeTracker) {
        this.serverManager = serverManager;
        this.startupTimeTracker = startupTimeTracker;
    }

    /**
     * Begins tracking a server startup and starts a background monitor.
     * The monitor will automatically complete tracking when the server comes online.
     * 
     * @param serverName The server name
     * @return The startup context for this operation
     */
    public ServerStartupContext beginStartup(String serverName) {
        Duration expectedTime = getExpectedStartupTime(serverName);
        ServerStartupContext context = new ServerStartupContext(serverName, expectedTime);
        activeStartups.put(serverName, context);
        logger.debug("Began tracking startup for '{}' (expected: {}ms)", serverName, expectedTime.toMillis());
        
        // Start background monitor to detect when server comes online
        startBackgroundMonitor(serverName);
        
        return context;
    }

    /**
     * Starts a background task that monitors the server and completes tracking
     * when the server comes online or times out.
     */
    private void startBackgroundMonitor(String serverName) {
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            logger.debug("ServerStartupTracker: background monitor started for '{}'", serverName);
            
            while (true) {
                // Check if tracking was already completed
                ServerStartupContext context = activeStartups.get(serverName);
                if (context == null || context.isCompleted()) {
                    logger.debug("ServerStartupTracker: monitor exiting for '{}' - tracking completed", serverName);
                    return;
                }
                
                // Check timeout
                if (System.currentTimeMillis() - startTime > MONITOR_TIMEOUT_MS) {
                    logger.warn("ServerStartupTracker: monitor timeout for '{}' after {}ms", 
                            serverName, MONITOR_TIMEOUT_MS);
                    completeStartup(serverName, false);
                    return;
                }
                
                // Check if server is now online
                ManagedServer server = serverManager.getServer(serverName);
                if (server != null && server.isOnline()) {
                    logger.debug("ServerStartupTracker: monitor detected '{}' is online", serverName);
                    completeStartup(serverName, true);
                    return;
                }
                
                // Wait before next poll
                try {
                    TimeUnit.MILLISECONDS.sleep(MONITOR_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("ServerStartupTracker: monitor interrupted for '{}'", serverName);
                    return;
                }
            }
        });
    }

    /**
     * Completes a server startup and optionally records the time for auto-calculation.
     * 
     * @param serverName The server name
     * @param success Whether the startup was successful
     */
    public void completeStartup(String serverName, boolean success) {
        ServerStartupContext context = activeStartups.get(serverName);
        if (context == null || context.isCompleted()) {
            logger.debug("No active startup context for '{}' to complete", serverName);
            return;
        }
        
        ServerState finalState = success ? ServerState.ONLINE : ServerState.FAILED;
        context.markCompleted(finalState.getName());
        
        // Record startup time for auto-calculation if successful
        if (success && shouldAutoCalculate(serverName)) {
            Duration actualDuration = context.getActualStartupDuration();
            if (actualDuration != null) {
                startupTimeTracker.recordStartupTime(serverName, actualDuration.toMillis());
                logger.debug("Recorded startup time for '{}': {}ms", serverName, actualDuration.toMillis());
            }
        }
        
        logger.debug("Completed startup tracking for '{}' (success: {}, duration: {}ms)", 
                serverName, success, context.getStartupTimerMs());
    }

    /**
     * Gets the active startup context for a server, if any.
     */
    public ServerStartupContext getActiveStartup(String serverName) {
        return activeStartups.get(serverName);
    }

    /**
     * Gets the current state for a server.
     * Returns normalized ServerState enum value.
     * For AMP: Returns normalized ApplicationState from control API.
     * For Shell: Returns STARTING if active tracking, otherwise ONLINE/OFFLINE based on ping.
     * 
     * @param serverName The name of the server
     * @return The normalized server state as ServerState enum
     */
    public ServerState getServerState(String serverName) {
        ManagedServer server = serverManager.getServer(serverName);
        if (server == null) {
            return ServerState.UNKNOWN;
        }
        
        // For supported control APIs, always use the control API
        if (server.supportsState()) {
            return server.getState();
        }
        
        // For unsupported control APIs: Check if there's an active startup
        ServerStartupContext context = activeStartups.get(serverName);
        if (context != null && !context.isCompleted()) {
            return ServerState.STARTING;
        }
        
        // Server.getState() already normalizes the state
        return server.getState();
    }

    /**
     * Gets the startup timer in seconds for a server.
     * Returns 0 if no active startup.
     */
    public long getStartupTimerSeconds(String serverName) {
        ServerStartupContext context = activeStartups.get(serverName);
        if (context != null && !context.isCompleted()) {
            return context.getStartupTimerSeconds();
        }
        return 0;
    }

    /**
     * Gets the startup progress percentage (0-100) for a server.
     */
    public int getStartupProgressPercentage(String serverName) {
        ServerStartupContext context = activeStartups.get(serverName);
        if (context != null && !context.isCompleted()) {
            return context.getStartupProgressPercentage();
        }
        
        // No active startup - return 100 if online, 0 if offline
        ManagedServer server = serverManager.getServer(serverName);
        return (server != null && server.isOnline()) ? 100 : 0;
    }

    /**
     * Gets the startup progress (0.0-1.0) for a server.
     * Useful for bossbars.
     */
    public double getStartupProgress(String serverName) {
        ServerStartupContext context = activeStartups.get(serverName);
        if (context != null && !context.isCompleted()) {
            return context.getStartupProgress();
        }
        
        // No active startup - return 1.0 if online, 0.0 if offline
        ManagedServer server = serverManager.getServer(serverName);
        return (server != null && server.isOnline()) ? 1.0 : 0.0;
    }

    /**
     * Clears a completed startup context.
     */
    public void clearStartup(String serverName) {
        activeStartups.remove(serverName);
        logger.debug("Cleared startup tracking for '{}'", serverName);
    }

    /**
     * Gets the expected startup time for a server.
     * Priority: auto-calculated (if enabled and has data) > configured > default
     */
    private Duration getExpectedStartupTime(String serverName) {
        ManagedServer server = serverManager.getServer(serverName);
        if (server == null) {
            return DEFAULT_EXPECTED_TIME;
        }
        
        ServerConfig config = server.getConfig();
        if (config == null) {
            return DEFAULT_EXPECTED_TIME;
        }
        
        StartupTimerConfig timerConfig = config.getStartupTimer();
        if (timerConfig == null) {
            // No startup_timer config, check for saved data anyway
            if (startupTimeTracker.hasData(serverName)) {
                Duration autoCalc = startupTimeTracker.getExpectedStartupTime(serverName);
                logger.debug("Using auto-calculated startup time for '{}': {}ms", serverName, autoCalc.toMillis());
                return autoCalc;
            }
            return DEFAULT_EXPECTED_TIME;
        }
        
        // Auto-calculate takes priority when enabled and has data
        if (timerConfig.isAutoCalculateExpectedStartupTime() && startupTimeTracker.hasData(serverName)) {
            Duration autoCalc = startupTimeTracker.getExpectedStartupTime(serverName);
            logger.debug("Using auto-calculated startup time for '{}': {}ms", serverName, autoCalc.toMillis());
            return autoCalc;
        }
        
        // Fall back to configured expected time
        String expectedTimeStr = timerConfig.getExpectedStartupTime();
        if (expectedTimeStr != null && !expectedTimeStr.isBlank()) {
            try {
                return DurationUtil.parse(expectedTimeStr);
            } catch (Exception e) {
                logger.warn("Invalid expected_startup_time '{}' for server '{}', using default", 
                        expectedTimeStr, serverName);
            }
        }
        
        return DEFAULT_EXPECTED_TIME;
    }

    /**
     * Checks if auto-calculation is enabled for a server.
     */
    private boolean shouldAutoCalculate(String serverName) {
        ManagedServer server = serverManager.getServer(serverName);
        if (server == null) {
            return false;
        }
        
        ServerConfig config = server.getConfig();
        if (config == null) {
            return false;
        }
        
        StartupTimerConfig timerConfig = config.getStartupTimer();
        return timerConfig != null && timerConfig.isAutoCalculateExpectedStartupTime();
    }
}
