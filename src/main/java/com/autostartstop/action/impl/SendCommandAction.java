package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.ServerManager;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Action that sends a command to a server's console.
 */
public class SendCommandAction implements Action {
    private static final Logger logger = Log.get(SendCommandAction.class);
    private static final String ACTION_NAME = "send_command";

    private final String server;
    private final String command;
    private final ServerManager serverManager;
    private final VariableResolver variableResolver;

    public SendCommandAction(String server, String command, ServerManager serverManager,
            VariableResolver variableResolver) {
        this.server = server;
        this.command = command;
        this.serverManager = serverManager;
        this.variableResolver = variableResolver;
    }

    /**
     * Creates a SendCommandAction from configuration.
     *
     * @param config The action configuration
     * @param ctx    The action context containing dependencies
     * @return A new SendCommandAction instance
     */
    public static SendCommandAction create(ActionConfig config, ActionContext ctx) {
        String server = config.requireString("server");
        String command = config.requireString("command");
        return new SendCommandAction(server, command, ctx.serverManager(), ctx.variableResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.SEND_COMMAND;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        String resolvedServer = variableResolver.resolve(server, context);
        String resolvedCommand = variableResolver.resolve(command, context);

        if (resolvedCommand.startsWith("/")) {
            resolvedCommand = resolvedCommand.substring(1);
        }

        logger.debug("({}) {}: Sending command to server '{}': {}", ruleName, ACTION_NAME, resolvedServer,
                resolvedCommand);

        serverManager.sendCommand(resolvedServer, resolvedCommand).thenAccept(success -> {
            if (success) {
                logger.info("({}) {}: Command sent to server '{}' successfully", ruleName, ACTION_NAME, resolvedServer);
            } else {
                logger.warn("({}) {}: Failed to send command to server '{}'", ruleName, ACTION_NAME, resolvedServer);
            }
        }).exceptionally(throwable -> {
            logger.error("({}) {}: Exception while sending command to server '{}': {}", ruleName, ACTION_NAME,
                    resolvedServer, throwable.getMessage(), throwable);
            return null;
        });

        return CompletableFuture.completedFuture(null);
    }

    public String getServer() {
        return server;
    }

    public String getCommand() {
        return command;
    }
}
