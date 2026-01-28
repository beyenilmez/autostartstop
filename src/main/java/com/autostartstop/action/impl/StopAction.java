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
 * Action that stops a server.
 */
public class StopAction implements Action {
    private static final Logger logger = Log.get(StopAction.class);
    
    private final String server;
    private final ServerManager serverManager;
    private final VariableResolver variableResolver;
    private final MotdCacheManager motdCacheManager;

    public StopAction(String server, ServerManager serverManager, VariableResolver variableResolver, MotdCacheManager motdCacheManager) {
        this.server = server;
        this.serverManager = serverManager;
        this.variableResolver = variableResolver;
        this.motdCacheManager = motdCacheManager;
    }

    /**
     * Creates a StopAction from configuration.
     *
     * @param config The action configuration
     * @param ctx The action context containing dependencies
     * @return A new StopAction instance
     */
    public static StopAction create(ActionConfig config, ActionContext ctx) {
        String server = config.requireString("server");
        return new StopAction(server, ctx.serverManager(), ctx.variableResolver(), ctx.motdCacheManager());
    }

    @Override
    public ActionType getType() {
        return ActionType.STOP;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String resolvedServer = variableResolver.resolve(server, context);
        logger.debug("StopAction: resolving server variable '{}' -> '{}'", server, resolvedServer);
        logger.debug("Stopping server '{}'", resolvedServer);

        // Cache MOTD before stopping the server (while it's still online)
        if (motdCacheManager != null) {
            logger.debug("Caching MOTD for server '{}' before stop", resolvedServer);
            motdCacheManager.cacheMotdForServer(resolvedServer);
        }

        long startTime = System.currentTimeMillis();
        return serverManager.stopServer(resolvedServer)
                .thenAccept(success -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (success) {
                        logger.info("Server '{}' stopped", resolvedServer);
                    } else {
                        logger.warn("StopAction: server '{}' stop returned failure (duration: {}ms)", 
                                resolvedServer, duration);
                    }
                })
                .exceptionally(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.error("StopAction: server '{}' stop threw exception after {}ms: {}", 
                            resolvedServer, duration, throwable.getMessage(), throwable);
                    return null;
                });
    }

    public String getServer() {
        return server;
    }
}
