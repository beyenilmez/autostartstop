package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Logs a message at a specified log level.
 */
public class LogAction implements Action {
    private static final Logger logger = Log.get(LogAction.class);
    
    private final String message;
    private final String level;
    private final VariableResolver variableResolver;

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    public LogAction(String message, String level, VariableResolver variableResolver) {
        this.message = message;
        this.level = level;
        this.variableResolver = variableResolver;
    }

    public static LogAction create(ActionConfig config, ActionContext ctx) {
        return new LogAction(
                config.requireString("message"),
                config.getString("level", "INFO"),
                ctx.variableResolver()
        );
    }

    @Override
    public ActionType getType() {
        return ActionType.LOG;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String resolvedMessage = variableResolver.resolve(message, context);
        LogLevel logLevel = variableResolver.resolveEnum(level, context, LogLevel.class, LogLevel.INFO);
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        String formattedMessage = "(" + ruleName + ") " + resolvedMessage;
        
        switch (logLevel) {
            case TRACE -> logger.trace(formattedMessage);
            case DEBUG -> logger.debug(formattedMessage);
            case INFO -> logger.info(formattedMessage);
            case WARN -> logger.warn(formattedMessage);
            case ERROR -> logger.error(formattedMessage);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    public String getMessage() { return message; }
    public String getLevel() { return level; }
}
