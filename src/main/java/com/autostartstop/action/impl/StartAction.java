package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.ServerManager;
import com.autostartstop.server.ServerStartupTracker;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Action that starts a server.
 * Calls beginStartup() on the tracker which handles all monitoring automatically.
 */
public class StartAction implements Action {
    private static final Logger logger = Log.get(StartAction.class);
    
    private final String server;
    private final ServerManager serverManager;
    private final VariableResolver variableResolver;
    private final ServerStartupTracker startupTracker;

    public StartAction(String server, ServerManager serverManager, VariableResolver variableResolver,
                       ServerStartupTracker startupTracker) {
        this.server = server;
        this.serverManager = serverManager;
        this.variableResolver = variableResolver;
        this.startupTracker = startupTracker;
    }

    /**
     * Creates a StartAction from configuration.
     *
     * @param config The action configuration
     * @param ctx The action context containing dependencies
     * @return A new StartAction instance
     */
    public static StartAction create(ActionConfig config, ActionContext ctx) {
        String server = config.requireString("server");
        return new StartAction(server, ctx.serverManager(), ctx.variableResolver(), ctx.startupTracker());
    }

    @Override
    public ActionType getType() {
        return ActionType.START;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String resolvedServer = variableResolver.resolve(server, context);
        logger.debug("Starting server '{}'", resolvedServer);

        // Send the start command first, then begin tracking.
        // This avoids a race condition where the background monitor's isOnline() poll
        // competes with the isOnline() check inside ManagedServer.start(), potentially
        // causing the start command to not be sent properly.
        return serverManager.startServer(resolvedServer)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Server '{}' start command sent successfully", resolvedServer);
                        // Begin startup tracking AFTER the start command is sent successfully.
                        // The tracker starts a background monitor that polls isOnline().
                        if (startupTracker != null) {
                            startupTracker.beginStartup(resolvedServer);
                        }
                    } else {
                        logger.warn("Server '{}' start command failed", resolvedServer);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Server '{}' start threw exception: {}", 
                            resolvedServer, throwable.getMessage());
                    return null;
                });
    }

    public String getServer() {
        return server;
    }
}
