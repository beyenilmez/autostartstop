package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.SettingsConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.server.ServerManager;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import com.autostartstop.util.DurationUtil;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Trigger that fires when a server has been empty (no players) for a specified
 * duration.
 * Uses a hybrid approach:
 * - Event-driven: listens to player connect/disconnect events and schedules
 * timers
 * - Periodic check: catches servers that started after activation without any
 * player activity
 * 
 * Configuration:
 * - empty_time: Duration the server must be empty before triggering (default:
 * 15m)
 * - server_list: Optional whitelist/blacklist of servers to monitor
 * 
 * Emitted context:
 * - ${empty_server.server} - RegisteredServer object
 * - ${empty_server.server.name} - Server name
 * - ${empty_server.empty_time} - Configured empty time
 * - ${empty_server.empty_since} - ISO-8601 timestamp when server became empty
 */
public class EmptyServerTrigger implements Trigger {
    private static final Logger logger = Log.get(EmptyServerTrigger.class);

    /** Default interval for periodic check (used if settings not available) */
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(5);

    // Injected dependencies
    private final ProxyServer proxy;
    private final Object plugin;
    private final ServerManager serverManager;

    // Configuration
    private final Duration emptyTime;
    private final Duration checkInterval;
    private final TriggerConfig.ServerListConfig serverList;

    // Runtime state (set during activate)
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private boolean activated = false;

    // Per-server empty tracking
    private final Map<String, ScheduledTask> pendingEmptyTasks = new ConcurrentHashMap<>();
    private final Map<String, Instant> emptySinceTimestamps = new ConcurrentHashMap<>();
    private final Set<String> firedServers = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();

    // Periodic check task
    private ScheduledTask periodicCheckTask;

    /**
     * Creates an EmptyServerTrigger from the given configuration.
     */
    public static EmptyServerTrigger create(TriggerConfig config, TriggerContext context) {
        Duration emptyTime = config.getEmptyTime();
        TriggerConfig.ServerListConfig serverList = config.getServerList();

        // Get check interval from settings
        Duration checkInterval = DEFAULT_CHECK_INTERVAL;
        SettingsConfig settings = context.settings();
        if (settings != null && settings.getEmptyServerCheckInterval() != null) {
            try {
                checkInterval = DurationUtil.parse(settings.getEmptyServerCheckInterval());
            } catch (Exception e) {
                logger.warn("EmptyServerTrigger: invalid empty_server_check_interval '{}', using default {}",
                        settings.getEmptyServerCheckInterval(), DurationUtil.format(DEFAULT_CHECK_INTERVAL));
            }
        }

        return new EmptyServerTrigger(context.proxy(), context.plugin(), context.serverManager(),
                emptyTime, checkInterval, serverList);
    }

    public EmptyServerTrigger(ProxyServer proxy, Object plugin, ServerManager serverManager,
            Duration emptyTime, Duration checkInterval, TriggerConfig.ServerListConfig serverList) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.serverManager = serverManager;
        this.emptyTime = emptyTime;
        this.checkInterval = checkInterval;
        this.serverList = serverList;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.EMPTY_SERVER;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;
        this.activated = true;

        String serverFilterInfo = serverList != null && serverList.getServers() != null
                ? serverList.getMode() + ":" + serverList.getServers().size()
                : "none";
        logger.debug(
                "EmptyServerTrigger: activating for rule '{}' (empty_time: {}, check_interval: {}, server filter: {})",
                ruleName, DurationUtil.format(emptyTime), DurationUtil.format(checkInterval), serverFilterInfo);

        // Register for events
        proxy.getEventManager().register(plugin, this);

        // Check current server states - servers that are already empty should start
        // their timers
        checkServersForEmpty();

        // Start periodic check to catch servers that start later
        if (checkInterval.toMillis() > 0) {
            startPeriodicCheck();
        } else {
            logger.debug("EmptyServerTrigger: periodic check interval is disabled, skipping");
        }

        logger.debug("EmptyServerTrigger: registered for DisconnectEvent, ServerConnectedEvent, and periodic check");
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("EmptyServerTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        proxy.getEventManager().unregisterListener(plugin, this);

        // Cancel periodic check
        if (periodicCheckTask != null) {
            periodicCheckTask.cancel();
            periodicCheckTask = null;
        }

        // Cancel all pending empty tasks
        synchronized (lock) {
            for (Map.Entry<String, ScheduledTask> entry : pendingEmptyTasks.entrySet()) {
                entry.getValue().cancel();
                logger.debug("EmptyServerTrigger: cancelled pending empty task for server '{}'", entry.getKey());
            }
            pendingEmptyTasks.clear();
            emptySinceTimestamps.clear();
            firedServers.clear();
        }

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;

        logger.debug("EmptyServerTrigger: unregistered from event manager");
    }

    /**
     * Starts the periodic check task to catch newly started servers.
     */
    private void startPeriodicCheck() {
        periodicCheckTask = proxy.getScheduler()
                .buildTask(plugin, this::checkServersForEmpty)
                .delay(checkInterval)
                .repeat(checkInterval)
                .schedule();

        logger.debug("EmptyServerTrigger: started periodic check every {}", DurationUtil.format(checkInterval));
    }

    /**
     * Checks all servers and schedules timers for any empty servers not already
     * tracked.
     * This catches:
     * - Servers that were empty on activation
     * - Servers that started after activation without any player activity
     */
    private void checkServersForEmpty() {
        if (!activated) {
            return;
        }

        for (RegisteredServer server : proxy.getAllServers()) {
            String serverName = server.getServerInfo().getName();

            if (!isServerMonitored(serverName)) {
                continue;
            }

            // Skip if already tracking this server or already fired
            synchronized (lock) {
                if (pendingEmptyTasks.containsKey(serverName) || firedServers.contains(serverName)) {
                    continue;
                }
            }

            // Check if server is online and empty
            int playerCount = server.getPlayersConnected().size();
            if (playerCount == 0 && isServerOnline(serverName)) {
                logger.debug("EmptyServerTrigger: server '{}' detected as empty and online, scheduling timer",
                        serverName);
                scheduleEmptyTimer(serverName, server);
            }
        }
    }

    /**
     * Checks if a server is online (can be pinged).
     */
    private boolean isServerOnline(String serverName) {
        return serverManager.isServerOnline(serverName);
    }

    /**
     * Called when a player disconnects from the proxy entirely.
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!activated || executionCallback == null) {
            return;
        }

        Player player = event.getPlayer();

        // Get the server the player was on
        Optional<RegisteredServer> currentServerOpt = player.getCurrentServer()
                .map(conn -> conn.getServer());

        if (currentServerOpt.isEmpty()) {
            return;
        }

        RegisteredServer server = currentServerOpt.get();
        String serverName = server.getServerInfo().getName();

        if (!isServerMonitored(serverName)) {
            return;
        }

        // Check player count AFTER this player leaves (current count - 1)
        // Note: At the time of DisconnectEvent, the player is still counted
        int remainingPlayers = server.getPlayersConnected().size() - 1;

        logger.debug("EmptyServerTrigger: player '{}' disconnecting from '{}' ({} players will remain)",
                player.getUsername(), serverName, remainingPlayers);

        if (remainingPlayers <= 0) {
            // Clear fired state so this server can fire again
            synchronized (lock) {
                firedServers.remove(serverName);
            }
            scheduleEmptyTimer(serverName, server);
        }
    }

    /**
     * Called when a player connects to a server (initial connect or server switch).
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (!activated || executionCallback == null) {
            return;
        }

        Player player = event.getPlayer();
        RegisteredServer server = event.getServer();
        String serverName = server.getServerInfo().getName();

        // Cancel any pending empty timer for this server since a player joined
        cancelEmptyTimer(serverName);

        // If the player switched FROM another server, check if that server is now empty
        Optional<RegisteredServer> previousServerOpt = event.getPreviousServer();
        if (previousServerOpt.isPresent()) {
            RegisteredServer previousServer = previousServerOpt.get();
            String previousServerName = previousServer.getServerInfo().getName();

            if (isServerMonitored(previousServerName)) {
                // Check player count AFTER this player left (current count since they already
                // left)
                int remainingPlayers = previousServer.getPlayersConnected().size();

                logger.debug("EmptyServerTrigger: player '{}' left '{}' for '{}' ({} players remain on previous)",
                        player.getUsername(), previousServerName, serverName, remainingPlayers);

                if (remainingPlayers == 0) {
                    // Clear fired state so this server can fire again
                    synchronized (lock) {
                        firedServers.remove(previousServerName);
                    }
                    scheduleEmptyTimer(previousServerName, previousServer);
                }
            }
        }
    }

    /**
     * Schedules an empty timer for the given server.
     */
    private void scheduleEmptyTimer(String serverName, RegisteredServer server) {
        synchronized (lock) {
            // Don't schedule if already fired (prevents re-firing for same empty period)
            if (firedServers.contains(serverName)) {
                return;
            }

            // Cancel any existing timer first
            ScheduledTask existingTask = pendingEmptyTasks.remove(serverName);
            if (existingTask != null) {
                existingTask.cancel();
            }

            Instant emptySince = Instant.now();
            emptySinceTimestamps.put(serverName, emptySince);

            logger.debug("EmptyServerTrigger: scheduling empty timer for server '{}' (will fire in {})",
                    serverName, DurationUtil.format(emptyTime));

            ScheduledTask task = proxy.getScheduler()
                    .buildTask(plugin, () -> fireEmptyTrigger(serverName, server, emptySince))
                    .delay(emptyTime)
                    .schedule();

            pendingEmptyTasks.put(serverName, task);
        }
    }

    /**
     * Cancels any pending empty timer for the given server.
     */
    private void cancelEmptyTimer(String serverName) {
        synchronized (lock) {
            ScheduledTask task = pendingEmptyTasks.remove(serverName);
            if (task != null) {
                task.cancel();
                emptySinceTimestamps.remove(serverName);
                firedServers.remove(serverName);
                logger.debug("EmptyServerTrigger: cancelled empty timer for server '{}' (player joined)",
                        serverName);
            }
        }
    }

    /**
     * Fires the trigger when the empty timer expires.
     */
    private void fireEmptyTrigger(String serverName, RegisteredServer server, Instant emptySince) {
        if (!activated || executionCallback == null) {
            return;
        }

        // Double-check the server is still empty (race condition protection)
        int currentPlayers = server.getPlayersConnected().size();
        if (currentPlayers > 0) {
            logger.debug("EmptyServerTrigger: empty timer fired for '{}' but server now has {} players, ignoring",
                    serverName, currentPlayers);
            synchronized (lock) {
                pendingEmptyTasks.remove(serverName);
                emptySinceTimestamps.remove(serverName);
            }
            return;
        }

        logger.debug("EmptyServerTrigger: server '{}' has been empty for {}, firing trigger for rule '{}'",
                serverName, DurationUtil.format(emptyTime), ruleName);

        // Clean up tracking state and mark as fired
        synchronized (lock) {
            pendingEmptyTasks.remove(serverName);
            emptySinceTimestamps.remove(serverName);
            firedServers.add(serverName); // Prevent re-firing until a player joins and leaves
        }

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.EMPTY_SERVER.getConfigName());

        // Emit context variables
        context.setVariable("empty_server.server", server);
        context.setVariable("empty_server.server.name", serverName);
        context.setVariable("empty_server.empty_time", DurationUtil.format(emptyTime));
        context.setVariable("empty_server.empty_since", emptySince.toString());

        // Invoke the execution callback (fire-and-forget)
        try {
            logger.debug("EmptyServerTrigger: invoking execution callback for rule '{}'", ruleName);
            executionCallback.apply(context);
        } catch (Exception e) {
            logger.error("EmptyServerTrigger: error during execution callback for rule '{}': {}",
                    ruleName, e.getMessage());
            logger.debug("EmptyServerTrigger: execution error details:", e);
        }
    }

    /**
     * Checks if a server should be monitored based on the server list filter.
     */
    private boolean isServerMonitored(String serverName) {
        if (serverList == null || serverList.getServers() == null || serverList.getServers().isEmpty()) {
            // No filter - monitor all servers
            return true;
        }

        List<String> servers = serverList.getServers();
        String mode = serverList.getMode();

        // Default to whitelist if mode is not specified
        if (mode == null || mode.isBlank()) {
            mode = "whitelist";
        }

        if ("blacklist".equalsIgnoreCase(mode)) {
            // Blacklist mode: monitor servers NOT in the list
            return !servers.contains(serverName);
        } else if ("disabled".equalsIgnoreCase(mode)) {
            // Disabled mode: monitor all servers
            return true;
        } else {
            // Whitelist mode (default): only monitor servers in the list
            return servers.contains(serverName);
        }
    }

    // ========== Getters for testing ==========

    public Duration getEmptyTime() {
        return emptyTime;
    }

    public boolean isActivated() {
        return activated;
    }
}
