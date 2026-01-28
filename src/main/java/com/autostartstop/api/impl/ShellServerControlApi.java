package com.autostartstop.api.impl;

import com.autostartstop.Log;
import com.autostartstop.api.ServerControlApi;
import com.autostartstop.config.ControlApiConfig;
import com.autostartstop.util.CommandExecutor;
import com.autostartstop.util.DurationUtil;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Server control API implementation using shell commands.
 */
public class ShellServerControlApi implements ServerControlApi {
    private static final Logger logger = Log.get(ShellServerControlApi.class);
    private static final String TYPE = "shell";

    private final String serverName;
    private final String startCommand;
    private final String stopCommand;
    private final String restartCommand;
    private final String sendCommandCommand;
    private final String workingDirectory;
    private final Duration commandTimeout;
    private final Map<String, String> environment;

    /**
     * Creates a ShellServerControlApi from the given configuration.
     */
    public static ShellServerControlApi create(ControlApiConfig config, String serverName) {
        Duration commandTimeout = CommandExecutor.DEFAULT_COMMAND_TIMEOUT;
        if (config.getCommandTimeout() != null && !config.getCommandTimeout().isBlank()) {
            try {
                commandTimeout = DurationUtil.parse(config.getCommandTimeout());
            } catch (Exception e) {
                // Use default if parsing fails
            }
        }
        
        return new ShellServerControlApi(
                serverName,
                config.getStartCommand(),
                config.getStopCommand(),
                config.getRestartCommand(),
                config.getSendCommandCommand(),
                config.getWorkingDirectory(),
                commandTimeout,
                config.getEnvironment()
        );
    }

    public ShellServerControlApi(String serverName, String startCommand, String stopCommand,
                                  String restartCommand, String sendCommandCommand, String workingDirectory,
                                  Duration commandTimeout, Map<String, String> environment) {
        this.serverName = serverName;
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.restartCommand = restartCommand;
        this.sendCommandCommand = sendCommandCommand;
        this.workingDirectory = workingDirectory;
        this.commandTimeout = commandTimeout != null ? commandTimeout : CommandExecutor.DEFAULT_COMMAND_TIMEOUT;
        this.environment = environment;
        
        logger.debug("Server '{}': ShellServerControlApi initialized", serverName);
        logger.debug("Server '{}': start_command={}", serverName, startCommand != null ? "[configured]" : "[not set]");
        logger.debug("Server '{}': stop_command={}", serverName, stopCommand != null ? "[configured]" : "[not set]");
        logger.debug("Server '{}': restart_command={}", serverName, restartCommand != null ? "[configured]" : "[not set]");
        logger.debug("Server '{}': send_command_command={}", serverName, sendCommandCommand != null ? "[configured]" : "[not set]");
        logger.debug("Server '{}': working_directory={}", serverName, workingDirectory != null ? workingDirectory : "[not set]");
        logger.debug("Server '{}': command_timeout={}s", serverName, this.commandTimeout.toSeconds());
        logger.debug("Server '{}': environment={} variable(s)", serverName, environment != null ? environment.size() : 0);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public CompletableFuture<Boolean> start() {
        logger.debug("Server '{}': start() called via shell API", serverName);
        if (startCommand == null || startCommand.isBlank()) {
            logger.warn("Server '{}': cannot start - no start_command configured", serverName);
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Server '{}': executing start command", serverName);
        return CommandExecutor.execute(startCommand, "start", "Server '" + serverName + "'", 
                workingDirectory, environment, commandTimeout);
    }

    @Override
    public CompletableFuture<Boolean> stop() {
        logger.debug("Server '{}': stop() called via shell API", serverName);
        if (stopCommand == null || stopCommand.isBlank()) {
            logger.warn("Server '{}': cannot stop - no stop_command configured", serverName);
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Server '{}': executing stop command", serverName);
        return CommandExecutor.execute(stopCommand, "stop", "Server '" + serverName + "'", 
                workingDirectory, environment, commandTimeout);
    }

    @Override
    public CompletableFuture<Boolean> restart() {
        logger.debug("Server '{}': restart() called via shell API", serverName);
        if (restartCommand == null || restartCommand.isBlank()) {
            logger.warn("Server '{}': cannot restart - no restart_command configured", serverName);
            return CompletableFuture.completedFuture(false);
        }
        logger.debug("Server '{}': executing restart command", serverName);
        return CommandExecutor.execute(restartCommand, "restart", "Server '" + serverName + "'", 
                workingDirectory, environment, commandTimeout);
    }

    @Override
    public boolean supportsCommandSending() {
        return sendCommandCommand != null && !sendCommandCommand.isBlank();
    }

    @Override
    public CompletableFuture<Boolean> sendCommand(String command) {
        logger.debug("Server '{}': sendCommand() called via shell API", serverName);
        if (sendCommandCommand == null || sendCommandCommand.isBlank()) {
            logger.warn("Server '{}': cannot send command - no send_command_command configured", serverName);
            return CompletableFuture.completedFuture(false);
        }
        if (command == null || command.isBlank()) {
            logger.warn("Server '{}': cannot send empty command", serverName);
            return CompletableFuture.completedFuture(false);
        }
        
        // Replace ${command} placeholder with the actual command
        String resolvedCommand = sendCommandCommand.replace("${command}", command);
        
        logger.debug("Server '{}': executing send command: {}", serverName, resolvedCommand);
        return CommandExecutor.execute(resolvedCommand, "send_command", "Server '" + serverName + "'", 
                workingDirectory, environment, commandTimeout);
    }

    public String getServerName() {
        return serverName;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public String getStopCommand() {
        return stopCommand;
    }

    public String getRestartCommand() {
        return restartCommand;
    }

    public String getSendCommandCommand() {
        return sendCommandCommand;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }
}
