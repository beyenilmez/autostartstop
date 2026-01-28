package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.Log;
import com.autostartstop.util.CommandExecutor;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes a shell command with optional working directory, timeout, and environment variables.
 */
public class ExecAction implements Action {
    private static final Logger logger = Log.get(ExecAction.class);
    
    private final String command;
    private final String workingDirectory;
    private final String timeout;
    private final Map<String, String> environment;
    private final VariableResolver variableResolver;

    public ExecAction(String command, String workingDirectory, String timeout,
                      Map<String, String> environment, VariableResolver variableResolver) {
        this.command = command;
        this.workingDirectory = workingDirectory;
        this.timeout = timeout;
        this.environment = environment;
        this.variableResolver = variableResolver;
    }

    public static ExecAction create(ActionConfig config, ActionContext ctx) {
        Map<String, String> environment = null;
        Object envObj = config.get("environment");
        if (envObj instanceof Map) {
            environment = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) envObj).entrySet()) {
                environment.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        
        return new ExecAction(
                config.requireString("command"),
                config.getString("working_directory"),
                config.getString("timeout"),
                environment,
                ctx.variableResolver()
        );
    }

    @Override
    public ActionType getType() {
        return ActionType.EXEC;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        
        String resolvedCommand = variableResolver.resolve(command, context);
        
        String resolvedWorkingDir = null;
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            resolvedWorkingDir = variableResolver.resolve(workingDirectory, context);
        }
        
        Map<String, String> resolvedEnvironment = null;
        if (environment != null && !environment.isEmpty()) {
            resolvedEnvironment = new HashMap<>();
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                resolvedEnvironment.put(entry.getKey(), variableResolver.resolve(entry.getValue(), context));
            }
        }
        
        Duration resolvedTimeout = variableResolver.resolveDuration(timeout, context, 
                CommandExecutor.DEFAULT_COMMAND_TIMEOUT);
        
        String contextName = "Rule '" + ruleName + "'";
        logger.debug("{}: executing command: {}", contextName, resolvedCommand);
        
        return CommandExecutor.execute(
                resolvedCommand, 
                "exec", 
                contextName, 
                resolvedWorkingDir, 
                resolvedEnvironment, 
                resolvedTimeout
        ).thenAccept(success -> {
            if (success) {
                logger.debug("{}: exec completed successfully", contextName);
            } else {
                logger.warn("{}: exec failed", contextName);
            }
        });
    }

    public String getCommand() { return command; }
    public String getWorkingDirectory() { return workingDirectory; }
    public String getTimeout() { return timeout; }
    public Map<String, String> getEnvironment() { return environment; }
}
