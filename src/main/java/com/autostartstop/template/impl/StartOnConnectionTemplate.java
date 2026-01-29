package com.autostartstop.template.impl;

import com.autostartstop.Log;
import com.autostartstop.action.impl.StartAction;
import com.autostartstop.config.ConfigAccessor;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.template.Template;
import com.autostartstop.template.TemplateContext;
import com.autostartstop.template.TemplateType;
import com.autostartstop.trigger.impl.ConnectionTrigger;
import com.autostartstop.util.MiniMessageUtil;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Template that starts servers when players attempt to connect to them.
 * 
 * <p>Supports multiple modes:
 * <ul>
 *   <li><b>none</b>: Start server without interfering with connection</li>
 *   <li><b>disconnect</b>: Deny connection, start server, disconnect player with message</li>
 *   <li><b>hold</b>: Deny connection, start server, wait until online, then allow connection</li>
 *   <li><b>waiting_server</b>: Send player to waiting server while target starts</li>
 * </ul>
 * 
 * <p>Configuration:
 * <pre>
 * rule_name:
 *   template: start_on_connection
 *   servers:
 *     - lobby
 *     - survival
 *   players:  # optional
 *     - alice
 *     - bob
 *   mode: waiting_server
 *   waiting_server:
 *     server: limbo
 *     start_waiting_server_on_connection: true
 *     message:
 *       enabled: false
 *       message: "..."
 *     progress_bar:
 *       enabled: true
 *       message: "..."
 *       progress: "${...}%"
 *       color: WHITE
 *       overlay: PROGRESS
 *     title:
 *       enabled: true
 *       title: "..."
 *       subtitle: "..."
 *       fade_in: "1s"
 *       stay: "1h"
 *       fade_out: "1s"
 *     action_bar:
 *       enabled: false
 *       message: "..."
 *   disconnect_message: "..."  # for disconnect mode
 * </pre>
 */
public class StartOnConnectionTemplate implements Template {
    private static final Logger logger = Log.get(StartOnConnectionTemplate.class);
    
    private static final long POLL_INTERVAL_MS = 500;
    private static final long MAX_WAIT_MS = 15 * 60 * 1000; // 15 minutes max wait
    private static final long POST_ONLINE_DELAY_MS = 1000; // Wait 1 second after server is online before connecting
    
    // Active bossbars for cleanup
    private final Map<UUID, BossBar> playerBossbars = new ConcurrentHashMap<>();
    // Track if initial title has been shown (to avoid fade-in on updates)
    private final Map<UUID, Boolean> initialTitleShown = new ConcurrentHashMap<>();

    private final TemplateContext context;
    private final List<String> servers;
    private final List<String> players;
    private final ConnectionMode mode;
    private final WaitingServerConfig waitingServerConfig;
    private final String disconnectMessage;
    
    private String ruleName;
    private ConnectionTrigger trigger;
    private boolean activated = false;

    /**
     * Connection handling modes.
     */
    public enum ConnectionMode {
        NONE,
        DISCONNECT,
        HOLD,
        WAITING_SERVER
    }

    private static final String DEFAULT_DISCONNECT_MESSAGE = 
            "<gold>${connection.server.name}</gold> is currently <gray>${${connection.server.name}.state}</gray>. Try again in a few seconds.";

    /**
     * Creates a StartOnConnectionTemplate from the given configuration.
     */
    @SuppressWarnings("unchecked")
    public static StartOnConnectionTemplate create(TemplateConfig config, TemplateContext context) {
        List<String> servers = config.getServers();
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("start_on_connection template requires at least one server");
        }

        List<String> players = config.getPlayers();
        ConnectionMode mode = parseMode(config.getMode());
        
        WaitingServerConfig waitingServerConfig = null;
        if (mode == ConnectionMode.WAITING_SERVER) {
            Object wsObj = config.get("waiting_server");
            Map<String, Object> wsMap = wsObj instanceof Map 
                    ? (Map<String, Object>) wsObj 
                    : Map.of();
            waitingServerConfig = new WaitingServerConfig(wsMap);
            
            if (waitingServerConfig.getServer() == null || waitingServerConfig.getServer().isBlank()) {
                throw new IllegalArgumentException(
                        "start_on_connection template with waiting_server mode requires waiting_server.server");
            }
        }
        
        String disconnectMessage = config.getDisconnectMessage();
        if (disconnectMessage == null || disconnectMessage.isBlank()) {
            disconnectMessage = DEFAULT_DISCONNECT_MESSAGE;
        }

        return new StartOnConnectionTemplate(context, servers, players, mode, waitingServerConfig, disconnectMessage);
    }

    private static ConnectionMode parseMode(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) {
            return ConnectionMode.NONE;
        }
        try {
            return ConnectionMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown connection mode '{}', defaulting to NONE", modeStr);
            return ConnectionMode.NONE;
        }
    }

    private StartOnConnectionTemplate(TemplateContext context, List<String> servers, List<String> players,
            ConnectionMode mode, WaitingServerConfig waitingServerConfig, String disconnectMessage) {
        this.context = context;
        this.servers = servers;
        this.players = players;
        this.mode = mode;
        this.waitingServerConfig = waitingServerConfig;
        this.disconnectMessage = disconnectMessage;
    }

    @Override
    public TemplateType getType() {
        return TemplateType.START_ON_CONNECTION;
    }

    @Override
    public void activate(String ruleName) {
        if (activated) {
            return;
        }
        
        this.ruleName = ruleName;
        logger.debug("StartOnConnectionTemplate: activating for rule '{}' (mode: {}, servers: {})",
                ruleName, mode, servers);

        // Create server list config for the trigger
        Map<String, Object> serverListMap = new HashMap<>();
        serverListMap.put("mode", "whitelist");
        serverListMap.put("servers", servers);
        TriggerConfig.ServerListConfig serverList = new TriggerConfig.ServerListConfig(serverListMap);

        // Create player list config if players are specified
        TriggerConfig.PlayerListConfig playerList = null;
        if (players != null && !players.isEmpty()) {
            Map<String, Object> playerListMap = new HashMap<>();
            playerListMap.put("mode", "whitelist");
            playerListMap.put("players", players);
            playerList = new TriggerConfig.PlayerListConfig(playerListMap);
        }

        // Determine if we need to deny connection based on mode
        boolean denyConnection = mode != ConnectionMode.NONE;

        // Create the connection trigger
        trigger = new ConnectionTrigger(
                context.proxy(),
                context.plugin(),
                context.serverManager(),
                serverList,
                playerList,
                denyConnection);

        // Create execution callback based on mode
        Function<ExecutionContext, CompletableFuture<Void>> executionCallback = ctx -> 
                handleConnection(ctx);

        // Activate the trigger
        trigger.activate(ruleName, executionCallback);
        activated = true;
        
        logger.debug("StartOnConnectionTemplate: activated for rule '{}'", ruleName);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("StartOnConnectionTemplate: deactivating for rule '{}'", ruleName);

        if (trigger != null) {
            trigger.deactivate();
            trigger = null;
        }

        // Clean up any active bossbars and titles
        for (Map.Entry<UUID, BossBar> entry : playerBossbars.entrySet()) {
            try {
                context.proxy().getPlayer(entry.getKey())
                        .ifPresent(player -> {
                            player.hideBossBar(entry.getValue());
                            player.clearTitle();
                        });
            } catch (Exception e) {
                logger.debug("Error cleaning up bossbar: {}", e.getMessage());
            }
        }
        playerBossbars.clear();
        initialTitleShown.clear();

        this.ruleName = null;
        activated = false;

        logger.debug("StartOnConnectionTemplate: deactivated");
    }

    /**
     * Handles a connection attempt based on the configured mode.
     */
    private CompletableFuture<Void> handleConnection(ExecutionContext ctx) {
        // Set rule name in context for actions
        ctx.setVariable("_rule_name", ruleName);
        
        String targetServerName = (String) ctx.getVariable("connection.server.name");
        Player player = (Player) ctx.getVariable("connection.player");
        
        if (targetServerName == null || player == null) {
            logger.warn("StartOnConnectionTemplate: missing connection context variables");
            return CompletableFuture.completedFuture(null);
        }

        // Check if target server is already online - if so, allow connection
        if (context.serverManager().isServerOnline(targetServerName)) {
            logger.debug("StartOnConnectionTemplate: server '{}' is already online, allowing connection", 
                    targetServerName);
            // We need to allow the connection since it was denied (if not NONE mode)
            if (mode != ConnectionMode.NONE) {
                allowConnection(player, targetServerName, ctx);
            }
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("StartOnConnectionTemplate: handling connection for player '{}' to offline server '{}'",
                player.getUsername(), targetServerName);

        return switch (mode) {
            case NONE -> handleModeNone(player, targetServerName, ctx);
            case DISCONNECT -> handleModeDisconnect(player, targetServerName, ctx);
            case HOLD -> handleModeHold(player, targetServerName, ctx);
            case WAITING_SERVER -> handleModeWaitingServer(player, targetServerName, ctx);
        };
    }

    /**
     * Mode NONE: Just start the server, don't interfere with connection.
     */
    private CompletableFuture<Void> handleModeNone(Player player, String targetServerName, ExecutionContext ctx) {
        logger.debug("StartOnConnectionTemplate: mode NONE - starting server '{}' without connection handling",
                targetServerName);
        return startServer(targetServerName, ctx);
    }

    /**
     * Mode DISCONNECT: Start server and disconnect player with message.
     */
    private CompletableFuture<Void> handleModeDisconnect(Player player, String targetServerName, ExecutionContext ctx) {
        logger.debug("StartOnConnectionTemplate: mode DISCONNECT - starting '{}' and disconnecting player '{}'",
                targetServerName, player.getUsername());

        // Start the server
        startServer(targetServerName, ctx);

        // Disconnect player with message
        String resolvedMessage = context.variableResolver().resolve(disconnectMessage, ctx);
        Component messageComponent = MiniMessageUtil.parse(resolvedMessage);
        player.disconnect(messageComponent);

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Mode HOLD: Start server, wait for it to come online, then allow connection.
     * Warning: This should only be used for servers that start in under 30 seconds.
     */
    private CompletableFuture<Void> handleModeHold(Player player, String targetServerName, ExecutionContext ctx) {
        logger.debug("StartOnConnectionTemplate: mode HOLD - starting '{}' and waiting for online",
                targetServerName);

        return startServer(targetServerName, ctx)
                .thenCompose(v -> waitForServerOnline(targetServerName, Duration.ofSeconds(30)))
                .thenAccept(online -> {
                    if (online) {
                        logger.debug("StartOnConnectionTemplate: server '{}' is now online, allowing connection",
                                targetServerName);
                        allowConnection(player, targetServerName, ctx);
                    } else {
                        logger.warn("StartOnConnectionTemplate: timeout waiting for server '{}' in HOLD mode",
                                targetServerName);
                        // Disconnect with a message since we couldn't connect in time
                        String message = context.variableResolver().resolve(disconnectMessage, ctx);
                        player.disconnect(MiniMessageUtil.parse(message));
                    }
                });
    }

    /**
     * Mode WAITING_SERVER: Complex flow with waiting server support.
     */
    private CompletableFuture<Void> handleModeWaitingServer(Player player, String targetServerName, ExecutionContext ctx) {
        String waitingServerName = waitingServerConfig.getServer();
        logger.debug("StartOnConnectionTemplate: mode WAITING_SERVER - using '{}' while starting '{}'",
                waitingServerName, targetServerName);

        // Step 1: Check if waiting server is online
        return ensureWaitingServerOnline(waitingServerName, ctx)
                .thenCompose(waitingServerOnline -> {
                    if (!waitingServerOnline) {
                        // Waiting server failed to start, disconnect player
                        logger.warn("StartOnConnectionTemplate: waiting server '{}' failed to start",
                                waitingServerName);
                        String message = context.variableResolver().resolve(disconnectMessage, ctx);
                        player.disconnect(MiniMessageUtil.parse(message));
                        return CompletableFuture.completedFuture(null);
                    }

                    // Step 2: Connect player to waiting server
                    return connectPlayerToServer(player, waitingServerName, ctx, waitingServerConfig.getConnectErrorMessage())
                            .thenCompose(connected -> {
                                if (!connected) {
                                    logger.warn("StartOnConnectionTemplate: failed to connect player to waiting server");
                                    return CompletableFuture.completedFuture(null);
                                }

                                // Step 3: Start the target server
                                startServer(targetServerName, ctx);

                                // Step 4: Send initial message if configured
                                sendInitialMessage(player, targetServerName, ctx);

                                // Step 5: Show initial title if configured
                                updateTitle(player, targetServerName, ctx);

                                // Step 6: Wait for target server while updating UI
                                return waitForServerWithUI(player, targetServerName, ctx)
                                        .thenCompose(online -> {
                                            if (online) {
                                                // Step 7: Wait a moment for server to fully initialize
                                                logger.debug("StartOnConnectionTemplate: server online, waiting {}ms for full initialization", 
                                                        POST_ONLINE_DELAY_MS);
                                                return CompletableFuture.runAsync(() -> {
                                                    try {
                                                        TimeUnit.MILLISECONDS.sleep(POST_ONLINE_DELAY_MS);
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }).thenCompose(v -> {
                                                    // Step 8: Cleanup UI
                                                    cleanupUI(player);
                                                    
                                                    // Step 9: Connect to target server
                                                    logger.debug("StartOnConnectionTemplate: connecting player to target server");
                                                    return connectPlayerToServer(player, targetServerName, ctx, waitingServerConfig.getConnectErrorMessage())
                                                            .thenApply(success -> null);
                                                });
                                            } else {
                                                // Cleanup UI on timeout too
                                                cleanupUI(player);
                                                logger.warn("StartOnConnectionTemplate: timeout waiting for target server");
                                                player.sendMessage(MiniMessageUtil.parse(
                                                        "<red>Server startup timed out. Please try again.</red>"));
                                                return CompletableFuture.completedFuture(null);
                                            }
                                        });
                            });
                });
    }

    /**
     * Ensures the waiting server is online, starting it if necessary.
     */
    private CompletableFuture<Boolean> ensureWaitingServerOnline(String waitingServerName, ExecutionContext ctx) {
        if (context.serverManager().isServerOnline(waitingServerName)) {
            return CompletableFuture.completedFuture(true);
        }

        if (!waitingServerConfig.isStartWaitingServerOnConnection()) {
            logger.debug("StartOnConnectionTemplate: waiting server '{}' is offline and auto-start disabled",
                    waitingServerName);
            return CompletableFuture.completedFuture(false);
        }

        logger.debug("StartOnConnectionTemplate: starting waiting server '{}'", waitingServerName);
        return startServer(waitingServerName, ctx)
                .thenCompose(v -> waitForServerOnline(waitingServerName, Duration.ofSeconds(30)));
    }

    /**
     * Starts a server using StartAction to properly track startup with expected startup time.
     */
    private CompletableFuture<Void> startServer(String serverName, ExecutionContext ctx) {
        logger.debug("StartOnConnectionTemplate: starting server '{}'", serverName);
        
        StartAction startAction = new StartAction(
                serverName,
                context.serverManager(),
                context.variableResolver(),
                context.startupTracker());
        
        return startAction.execute(ctx);
    }

    /**
     * Waits for a server to come online with a timeout.
     */
    private CompletableFuture<Boolean> waitForServerOnline(String serverName, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = timeout.toMillis();
            long lastIterationTime = startTime;

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (context.serverManager().isServerOnline(serverName)) {
                    return true;
                }
                
                // Calculate how long the iteration took
                long currentTime = System.currentTimeMillis();
                long iterationDuration = currentTime - lastIterationTime;
                lastIterationTime = currentTime;
                
                // Only sleep if iteration was faster than poll interval
                long sleepTime = POLL_INTERVAL_MS - iterationDuration;
                if (sleepTime > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepTime);
                        lastIterationTime = System.currentTimeMillis();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return false;
        });
    }

    /**
     * Waits for the target server while updating UI elements.
     */
    private CompletableFuture<Boolean> waitForServerWithUI(Player player, String targetServerName, ExecutionContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            long lastIterationTime = startTime;

            while (System.currentTimeMillis() - startTime < MAX_WAIT_MS) {
                // Check if player is still online
                if (!player.isActive()) {
                    logger.debug("StartOnConnectionTemplate: player disconnected, stopping wait loop");
                    return false;
                }

                // Check if server is online
                if (context.serverManager().isServerOnline(targetServerName)) {
                    return true;
                }

                // Update UI elements
                updateTitle(player, targetServerName, ctx);
                updateBossbar(player, targetServerName, ctx);
                updateActionBar(player, targetServerName, ctx);

                // Calculate how long the iteration took
                long currentTime = System.currentTimeMillis();
                long iterationDuration = currentTime - lastIterationTime;
                lastIterationTime = currentTime;
                
                // Only sleep if iteration was faster than poll interval
                long sleepTime = POLL_INTERVAL_MS - iterationDuration;
                if (sleepTime > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepTime);
                        lastIterationTime = System.currentTimeMillis();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return false;
        });
    }

    /**
     * Sends the initial message when player enters waiting server.
     */
    private void sendInitialMessage(Player player, String targetServerName, ExecutionContext ctx) {
        if (waitingServerConfig == null || !waitingServerConfig.isMessageEnabled()) {
            return;
        }

        String message = waitingServerConfig.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }

        String resolved = context.variableResolver().resolve(message, ctx);
        player.sendMessage(MiniMessageUtil.parse(resolved));
        logger.debug("StartOnConnectionTemplate: sent initial message to player");
    }

    /**
     * Updates the title with current variable values.
     * Uses configured fade-in for initial display, zero fade-in for updates.
     */
    private void updateTitle(Player player, String targetServerName, ExecutionContext ctx) {
        if (waitingServerConfig == null || !waitingServerConfig.isTitleEnabled()) {
            return;
        }

        VariableResolver resolver = context.variableResolver();
        UUID playerUuid = player.getUniqueId();
        
        String titleText = waitingServerConfig.getTitleText();
        String subtitleText = waitingServerConfig.getTitleSubtitle();
        
        Component title = Component.empty();
        if (titleText != null && !titleText.isBlank()) {
            title = MiniMessageUtil.parse(resolver.resolve(titleText, ctx));
        }
        
        Component subtitle = Component.empty();
        if (subtitleText != null && !subtitleText.isBlank()) {
            subtitle = MiniMessageUtil.parse(resolver.resolve(subtitleText, ctx));
        }
        
        // Use configured fade-in for initial display, zero for updates
        boolean isInitial = !initialTitleShown.containsKey(playerUuid);
        Duration fadeIn = isInitial ? waitingServerConfig.getTitleFadeIn() : Duration.ZERO;
        Duration stay = waitingServerConfig.getTitleStay();
        Duration fadeOut = waitingServerConfig.getTitleFadeOut();
        
        Title.Times times = Title.Times.times(fadeIn, stay, fadeOut);
        player.showTitle(Title.title(title, subtitle, times));
        
        if (isInitial) {
            initialTitleShown.put(playerUuid, true);
        }
    }

    /**
     * Updates the bossbar progress.
     */
    private void updateBossbar(Player player, String targetServerName, ExecutionContext ctx) {
        if (waitingServerConfig == null || !waitingServerConfig.isProgressBarEnabled()) {
            return;
        }

        VariableResolver resolver = context.variableResolver();
        UUID playerUuid = player.getUniqueId();

        String message = waitingServerConfig.getProgressBarMessage();
        String resolvedMessage = resolver.resolve(message, ctx);
        Component messageComponent = MiniMessageUtil.parse(resolvedMessage);

        String progressRaw = waitingServerConfig.getProgressBarProgress();
        float progress = resolver.resolveFloatClamped(progressRaw, ctx, 0.0f, 0.0f, 1.0f);

        BossBar.Color color = waitingServerConfig.getProgressBarColor();
        BossBar.Overlay overlay = waitingServerConfig.getProgressBarOverlay();

        BossBar existingBar = playerBossbars.get(playerUuid);
        if (existingBar != null) {
            // Update existing bossbar
            existingBar.name(messageComponent);
            existingBar.progress(progress);
            existingBar.color(color);
            existingBar.overlay(overlay);
        } else {
            // Create new bossbar
            BossBar bossBar = BossBar.bossBar(messageComponent, progress, color, overlay);
            player.showBossBar(bossBar);
            playerBossbars.put(playerUuid, bossBar);
        }
    }

    /**
     * Updates the action bar message.
     */
    private void updateActionBar(Player player, String targetServerName, ExecutionContext ctx) {
        if (waitingServerConfig == null || !waitingServerConfig.isActionBarEnabled()) {
            return;
        }

        String message = waitingServerConfig.getActionBarMessage();
        if (message == null || message.isBlank()) {
            return;
        }

        String resolved = context.variableResolver().resolve(message, ctx);
        player.sendActionBar(MiniMessageUtil.parse(resolved));
    }

    /**
     * Cleans up UI elements for a player.
     */
    private void cleanupUI(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        // Clear title
        player.clearTitle();
        
        // Hide bossbar
        BossBar bossBar = playerBossbars.remove(playerUuid);
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        
        // Clear title tracking
        initialTitleShown.remove(playerUuid);
        
        logger.debug("StartOnConnectionTemplate: cleaned up UI for player '{}'", player.getUsername());
    }

    /**
     * Connects a player to a server.
     * @param errorMessageTemplate Optional MiniMessage template to send on failure (supports ${connect_server}, ${connect_error_reason}). Null/blank = do not message.
     */
    private CompletableFuture<Boolean> connectPlayerToServer(Player player, String serverName,
            ExecutionContext ctx, String errorMessageTemplate) {
        // Check if player is still connected
        if (!player.isActive()) {
            logger.warn("StartOnConnectionTemplate: player '{}' is no longer active, cannot connect to '{}'",
                    player.getUsername(), serverName);
            return CompletableFuture.completedFuture(false);
        }
        
        RegisteredServer server = context.serverManager().getRegisteredServer(serverName);
        if (server == null) {
            logger.error("StartOnConnectionTemplate: server '{}' not found for connection", serverName);
            return CompletableFuture.completedFuture(false);
        }

        // Log current player server for debugging
        player.getCurrentServer().ifPresent(currentServer -> 
            logger.debug("StartOnConnectionTemplate: player '{}' is currently on '{}', connecting to '{}'",
                    player.getUsername(), currentServer.getServerInfo().getName(), serverName));

        return player.createConnectionRequest(server)
                .connect()
                .thenApply(result -> {
                    if (result.isSuccessful()) {
                        logger.debug("StartOnConnectionTemplate: connected player '{}' to '{}'",
                                player.getUsername(), serverName);
                        return true;
                    } else {
                        String reasonText = result.getReasonComponent()
                                .map(MiniMessageUtil::toPlainText)
                                .orElse("");
                        logger.warn("StartOnConnectionTemplate: failed to connect player '{}' to '{}' - status: {}, reason: {}",
                                player.getUsername(), serverName, result.getStatus(), reasonText);
                        sendConnectErrorMessage(player, serverName, reasonText, ctx, errorMessageTemplate);
                        return false;
                    }
                })
                .exceptionally(e -> {
                    logger.error("StartOnConnectionTemplate: error connecting player '{}' to '{}': {}", 
                            player.getUsername(), serverName, e.getMessage());
                    sendConnectErrorMessage(player, serverName, e.getMessage(), ctx, errorMessageTemplate);
                    return false;
                });
    }

    private void sendConnectErrorMessage(Player player, String serverName, String reasonText,
            ExecutionContext ctx, String errorMessageTemplate) {
        if (errorMessageTemplate == null || errorMessageTemplate.isBlank()) {
            return;
        }
        try {
            Map<String, Object> errVars = new HashMap<>();
            errVars.put("connect_server", serverName);
            errVars.put("connect_error_reason", reasonText != null ? reasonText : "");
            ExecutionContext errContext = new ExecutionContext(ctx, errVars);
            String resolved = context.variableResolver().resolve(errorMessageTemplate, errContext);
            Component messageComponent = MiniMessageUtil.parse(resolved);
            if (messageComponent != null && !Component.empty().equals(messageComponent)) {
                player.sendMessage(messageComponent);
            }
        } catch (Exception e) {
            logger.debug("StartOnConnectionTemplate: failed to send connect error message to '{}': {}",
                    player.getUsername(), e.getMessage());
        }
    }

    /**
     * Allows a connection that was previously denied.
     * This modifies the original ServerPreConnectEvent to allow the connection.
     */
    private void allowConnection(Player player, String serverName, ExecutionContext ctx) {
        RegisteredServer server = context.serverManager().getRegisteredServer(serverName);
        if (server == null) {
            logger.error("StartOnConnectionTemplate: server '{}' not found for allow_connection", serverName);
            return;
        }

        // Get the original connection event and modify it to allow
        Object eventObj = ctx.getVariable("connection");
        if (eventObj instanceof ServerPreConnectEvent event) {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(server));
            logger.debug("StartOnConnectionTemplate: set event result to allowed for '{}'", serverName);
        } else {
            logger.warn("StartOnConnectionTemplate: connection event not found in context, cannot allow");
        }

        // Signal the connection release
        if (ctx.hasEventReleaseSignal()) {
            ctx.releaseEvent();
            logger.debug("StartOnConnectionTemplate: signaled connection release for '{}'", serverName);
        }
    }

    /**
     * Configuration for the waiting_server mode.
     */
    public static class WaitingServerConfig {
        private final ConfigAccessor accessor;
        private final MessageConfig messageConfig;
        private final ProgressBarConfig progressBarConfig;
        private final TitleConfig titleConfig;
        private final ActionBarConfig actionBarConfig;

        public WaitingServerConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "waiting_server");
            this.messageConfig = new MessageConfig(accessor.getMap("message"));
            this.progressBarConfig = new ProgressBarConfig(accessor.getMap("progress_bar"));
            this.titleConfig = new TitleConfig(accessor.getMap("title"));
            this.actionBarConfig = new ActionBarConfig(accessor.getMap("action_bar"));
        }

        public String getServer() {
            return accessor.getString("server");
        }

        public boolean isStartWaitingServerOnConnection() {
            return accessor.getBoolean("start_waiting_server_on_connection", true);
        }

        /** Message sent to the player when connect to target (or waiting) server fails. Supports ${connect_server} and ${connect_error_reason}. */
        public String getConnectErrorMessage() {
            return accessor.getString("connect_error_message",
                    "<red>Could not connect to ${connect_server}: ${connect_error_reason}</red>");
        }

        // Message config
        public boolean isMessageEnabled() {
            return messageConfig.isEnabled();
        }

        public String getMessage() {
            return messageConfig.getMessage();
        }

        // Progress bar config
        public boolean isProgressBarEnabled() {
            return progressBarConfig.isEnabled();
        }

        public String getProgressBarMessage() {
            return progressBarConfig.getMessage();
        }

        public String getProgressBarProgress() {
            return progressBarConfig.getProgress();
        }

        public BossBar.Color getProgressBarColor() {
            return progressBarConfig.getColor();
        }

        public BossBar.Overlay getProgressBarOverlay() {
            return progressBarConfig.getOverlay();
        }

        // Title config
        public boolean isTitleEnabled() {
            return titleConfig.isEnabled();
        }

        public String getTitleText() {
            return titleConfig.getTitle();
        }

        public String getTitleSubtitle() {
            return titleConfig.getSubtitle();
        }

        public Duration getTitleFadeIn() {
            return titleConfig.getFadeIn();
        }

        public Duration getTitleStay() {
            return titleConfig.getStay();
        }

        public Duration getTitleFadeOut() {
            return titleConfig.getFadeOut();
        }

        // Action bar config
        public boolean isActionBarEnabled() {
            return actionBarConfig.isEnabled();
        }

        public String getActionBarMessage() {
            return actionBarConfig.getMessage();
        }
    }

    /**
     * Configuration for the message setting.
     */
    private static class MessageConfig {
        private final ConfigAccessor accessor;

        public MessageConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "message");
        }

        public boolean isEnabled() {
            return accessor.getBoolean("enabled", false);
        }

        public String getMessage() {
            return accessor.getString("message", 
                    "<gold>${connection.server.name}</gold> <gray>is starting. You will be connected shortly.</gray>");
        }
    }

    /**
     * Configuration for the progress bar (bossbar).
     */
    private static class ProgressBarConfig {
        private final ConfigAccessor accessor;

        public ProgressBarConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "progress_bar");
        }

        public boolean isEnabled() {
            return accessor.getBoolean("enabled", true);
        }

        public String getMessage() {
            return accessor.getString("message", "${${connection.server.name}.startup_progress_percentage}%");
        }

        public String getProgress() {
            return accessor.getString("progress", "${${connection.server.name}.startup_progress}");
        }

        public BossBar.Color getColor() {
            String colorStr = accessor.getString("color", "WHITE");
            try {
                return BossBar.Color.valueOf(colorStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return BossBar.Color.WHITE;
            }
        }

        public BossBar.Overlay getOverlay() {
            String overlayStr = accessor.getString("overlay", "PROGRESS");
            try {
                return BossBar.Overlay.valueOf(overlayStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return BossBar.Overlay.PROGRESS;
            }
        }
    }

    /**
     * Configuration for the title.
     */
    private static class TitleConfig {
        private final ConfigAccessor accessor;

        public TitleConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "title");
        }

        public boolean isEnabled() {
            return accessor.getBoolean("enabled", true);
        }

        public String getTitle() {
            return accessor.getString("title", "Please Wait...");
        }

        public String getSubtitle() {
            return accessor.getString("subtitle", 
                    "<gold>${connection.server.name}</gold> <gray>is ${${connection.server.name}.state}. You will be connected shortly.</gray>");
        }

        public Duration getFadeIn() {
            return accessor.getDuration("fade_in", Duration.ofSeconds(1));
        }

        public Duration getStay() {
            return accessor.getDuration("stay", Duration.ofHours(1));
        }

        public Duration getFadeOut() {
            return accessor.getDuration("fade_out", Duration.ofSeconds(1));
        }
    }

    /**
     * Configuration for the action bar.
     */
    private static class ActionBarConfig {
        private final ConfigAccessor accessor;

        public ActionBarConfig(Map<String, Object> config) {
            this.accessor = new ConfigAccessor(config, "action_bar");
        }

        public boolean isEnabled() {
            return accessor.getBoolean("enabled", false);
        }

        public String getMessage() {
            return accessor.getString("message", "Server is ${${connection.server.name}.state}");
        }
    }
}
