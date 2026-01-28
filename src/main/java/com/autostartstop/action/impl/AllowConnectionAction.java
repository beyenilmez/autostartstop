package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.Log;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Action that allows a previously denied connection.
 * This action modifies the ServerPreConnectEvent to allow the connection to proceed.
 */
public class AllowConnectionAction implements Action {
    private static final Logger logger = Log.get(AllowConnectionAction.class);
    private static final String ACTION_NAME = "allow_connection";
    
    private final String connectionParam;
    private final String serverParam;
    private final TargetResolver targetResolver;

    public AllowConnectionAction(String connectionParam, String serverParam, TargetResolver targetResolver) {
        this.connectionParam = connectionParam;
        this.serverParam = serverParam;
        this.targetResolver = targetResolver;
    }

    /**
     * Creates an AllowConnectionAction from configuration.
     */
    public static AllowConnectionAction create(ActionConfig config, ActionContext ctx) {
        String connection = config.getString("connection", "${connection}");
        String server = config.getString("server", "${connection.server}");
        return new AllowConnectionAction(connection, server, ctx.targetResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.ALLOW_CONNECTION;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        VariableResolver variableResolver = targetResolver.getVariableResolver();
        
        // Get the connection event object from context - must be wrapped in ${}
        Object connectionObj = null;
        if (connectionParam.startsWith("${") && connectionParam.endsWith("}")) {
            String variableName = VariableResolver.extractVariableName(connectionParam);
            connectionObj = variableResolver.resolveVariable(variableName, context);
        }
        
        if (connectionObj == null) {
            logger.error("({}) {}: No connection event found for '{}'", ruleName, ACTION_NAME, connectionParam);
            return CompletableFuture.completedFuture(null);
        }
        
        if (!(connectionObj instanceof ServerPreConnectEvent)) {
            logger.error("({}) {}: '{}' is not a ServerPreConnectEvent: {}", 
                    ruleName, ACTION_NAME, connectionParam, connectionObj.getClass().getName());
            return CompletableFuture.completedFuture(null);
        }
        
        ServerPreConnectEvent event = (ServerPreConnectEvent) connectionObj;
        String playerName = event.getPlayer().getUsername();
        
        // Resolve the target server using TargetResolver
        RegisteredServer targetServer = targetResolver.resolveServer(serverParam, context, ACTION_NAME);
        
        if (targetServer == null) {
            logger.error("({}) {}: Could not resolve target server", ruleName, ACTION_NAME);
            return CompletableFuture.completedFuture(null);
        }
        
        String serverName = targetServer.getServerInfo().getName();
        
        // Allow the connection by setting the result to the target server
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer));
        
        logger.debug("({}) {}: Allowed '{}' to server '{}'", ruleName, ACTION_NAME, playerName, serverName);
        
        // Signal the connection trigger to release the event immediately
        context.releaseEvent();
        
        return CompletableFuture.completedFuture(null);
    }

    public String getConnectionParam() { return connectionParam; }
    public String getServerParam() { return serverParam; }
}
