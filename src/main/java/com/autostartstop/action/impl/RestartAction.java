package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.MotdCacheManager;
import com.autostartstop.server.ServerManager;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Action that restarts a server.
 * Calls beginStartup() on the tracker which handles all monitoring automatically.
 */
public class RestartAction implements Action {
    private static final Logger logger = Log.get(RestartAction.class);
    
    private final String server;
    private final ServerManager serverManager;
    private final VariableResolver variableResolver;
    private final MotdCacheManager motdCacheManager;

    public RestartAction(String server, ServerManager serverManager, VariableResolver variableResolver, MotdCacheManager motdCacheManager) {
        this.server = server;
        this.serverManager = serverManager;
        this.variableResolver = variableResolver;
        this.motdCacheManager = motdCacheManager;
    }

    /**
     * Creates a RestartAction from configuration.
     */
    public static RestartAction create(ActionConfig config, ActionContext ctx) {
        String server = config.requireString("server");
        return new RestartAction(server, ctx.serverManager(), ctx.variableResolver(), ctx.motdCacheManager());
    }

    @Override
    public ActionType getType() {
        return ActionType.RESTART;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String resolvedServer = variableResolver.resolve(server, context);
        logger.debug("Restarting server '{}'", resolvedServer);

        // Cache MOTD before restarting the server (while it's still online)
        if (motdCacheManager != null) {
            logger.debug("Caching MOTD for server '{}' before restart", resolvedServer);
            motdCacheManager.cacheMotdForServer(resolvedServer);
        }

        return serverManager.restartServer(resolvedServer)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Server '{}' restart command sent successfully", resolvedServer);
                    } else {
                        logger.warn("Server '{}' restart command failed", resolvedServer);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Server '{}' restart threw exception: {}", 
                            resolvedServer, throwable.getMessage());
                    return null;
                });
    }

    public String getServer() { return server; }
}
