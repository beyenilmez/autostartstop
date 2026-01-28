package com.autostartstop.condition.impl;

import com.autostartstop.Log;
import com.autostartstop.condition.Condition;
import com.autostartstop.condition.ConditionContext;
import com.autostartstop.condition.ConditionType;
import com.autostartstop.config.ConfigException;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.ServerManager;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Condition that checks a server's online/offline status.
 */
public class ServerStatusCondition implements Condition {
    private static final Logger logger = Log.get(ServerStatusCondition.class);
    
    private final String server;
    private final String expectedStatus;
    private final ServerManager serverManager;
    private final VariableResolver variableResolver;

    public ServerStatusCondition(String server, String expectedStatus, 
            ServerManager serverManager, VariableResolver variableResolver) {
        this.server = server;
        this.expectedStatus = expectedStatus;
        this.serverManager = serverManager;
        this.variableResolver = variableResolver;
    }

    /**
     * Creates a ServerStatusCondition from configuration.
     */
    public static ServerStatusCondition create(Map<String, Object> config, ConditionContext ctx) {
        Object serverObj = config.get("server");
        Object statusObj = config.get("status");

        if (serverObj == null) {
            throw ConfigException.required("server_status", "server");
        }
        if (statusObj == null) {
            throw ConfigException.required("server_status", "status");
        }

        String server = serverObj.toString();
        String status = statusObj.toString();

        return new ServerStatusCondition(server, status, ctx.serverManager(), ctx.variableResolver());
    }

    @Override
    public ConditionType getType() {
        return ConditionType.SERVER_STATUS;
    }

    @Override
    public boolean evaluate(ExecutionContext context) {
        String resolvedServer = variableResolver.resolve(server, context);
        String resolvedStatus = variableResolver.resolve(expectedStatus, context);
        
        logger.debug("ServerStatusCondition: checking server '{}' (from '{}') for status '{}' (from '{}')",
                resolvedServer, server, resolvedStatus, expectedStatus);

        boolean isOnline = serverManager.isServerOnline(resolvedServer);
        String actualStatus = isOnline ? "online" : "offline";
        
        boolean result = actualStatus.equalsIgnoreCase(resolvedStatus);
        logger.debug("ServerStatusCondition: server '{}' is {}, expected {}, result = {}", 
                resolvedServer, actualStatus, resolvedStatus, result);
        
        return result;
    }

    public String getServer() { return server; }
    public String getExpectedStatus() { return expectedStatus; }
}
