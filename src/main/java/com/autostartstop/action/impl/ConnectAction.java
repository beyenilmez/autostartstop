package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import com.autostartstop.util.MiniMessageUtil;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Action that connects one or more players to a specified server.
 */
public class ConnectAction implements Action {
    private static final Logger logger = Log.get(ConnectAction.class);
    private static final String ACTION_NAME = "connect";
    private static final String DEFAULT_ERROR_MESSAGE = "<red>Could not connect to ${connect_server}: ${connect_error_reason}</red>";

    private final String playerParam;
    private final List<String> playersParam;
    private final String serverParam;
    private final String errorMessage;
    private final TargetResolver targetResolver;

    public ConnectAction(String playerParam, List<String> playersParam, String serverParam,
            String errorMessage, TargetResolver targetResolver) {
        this.playerParam = playerParam;
        this.playersParam = playersParam;
        this.serverParam = serverParam;
        this.errorMessage = errorMessage;
        this.targetResolver = targetResolver;
    }

    /**
     * Creates a ConnectAction from configuration.
     */
    public static ConnectAction create(ActionConfig config, ActionContext ctx) {
        String player = config.getString("player");
        List<String> players = config.getStringList("players");
        String server = config.requireString("server");
        String errorMessage = config.getString("error_message", DEFAULT_ERROR_MESSAGE);
        return new ConnectAction(player, players, server, errorMessage, ctx.targetResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.CONNECT;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        
        // Resolve the target server first
        RegisteredServer targetServer = targetResolver.resolveServer(serverParam, context, ACTION_NAME);
        if (targetServer == null) {
            logger.error("({}) {}: Could not resolve server '{}'", ruleName, ACTION_NAME, serverParam);
            return CompletableFuture.completedFuture(null);
        }
        
        String serverName = targetServer.getServerInfo().getName();
        
        // Resolve all target players (only from player/players, not from servers)
        Set<Player> playersToConnect = targetResolver.resolvePlayersOnly(
                playerParam, playersParam, context, ACTION_NAME);
        
        if (playersToConnect.isEmpty()) {
            logger.warn("({}) {}: No valid players found", ruleName, ACTION_NAME);
            return CompletableFuture.completedFuture(null);
        }
        
        // Connect all players
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Player player : playersToConnect) {
            String playerName = player.getUsername();
            CompletableFuture<Boolean> future = player.createConnectionRequest(targetServer)
                    .connect()
                    .thenApply(result -> {
                        if (result.isSuccessful()) {
                            logger.debug("({}) {}: Connected '{}' to '{}'", 
                                    ruleName, ACTION_NAME, playerName, serverName);
                            return true;
                        } else {
                            String reasonText = result.getReasonComponent()
                                    .map(MiniMessageUtil::toPlainText)
                                    .orElse("");
                            logger.warn("({}) {}: Failed to connect '{}' to '{}': {}", 
                                    ruleName, ACTION_NAME, playerName, serverName, reasonText);
                            sendConnectionErrorMessage(player, serverName, reasonText, context);
                            return false;
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("({}) {}: Error connecting '{}': {}", 
                                ruleName, ACTION_NAME, playerName, e.getMessage());
                        sendConnectionErrorMessage(player, serverName, e.getMessage(), context);
                        return false;
                    });
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    long successCount = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Boolean::booleanValue)
                            .count();
                    logger.debug("({}) {}: Connected {}/{} player(s) to '{}'", 
                            ruleName, ACTION_NAME, successCount, playersToConnect.size(), serverName);
                });
    }

    private void sendConnectionErrorMessage(Player player, String serverName, String reasonText,
            ExecutionContext context) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return;
        }
        try {
            Map<String, Object> errVars = new HashMap<>();
            errVars.put("connect_server", serverName);
            errVars.put("connect_error_reason", reasonText != null ? reasonText : "");
            ExecutionContext errContext = new ExecutionContext(context, errVars);
            String resolved = targetResolver.getVariableResolver().resolve(errorMessage, errContext);
            Component messageComponent = MiniMessageUtil.parse(resolved);
            if (messageComponent != null && !Component.empty().equals(messageComponent)) {
                player.sendMessage(messageComponent);
            }
        } catch (Exception e) {
            logger.debug("({}) {}: Failed to send error message to '{}': {}", 
                    context.getVariable("_rule_name", "unknown"), ACTION_NAME, player.getUsername(), e.getMessage());
        }
    }

    public String getPlayerParam() { return playerParam; }
    public List<String> getPlayersParam() { return playersParam; }
    public String getServerParam() { return serverParam; }
}
