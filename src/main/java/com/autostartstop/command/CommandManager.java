package com.autostartstop.command;

import com.autostartstop.util.MiniMessageUtil;
import com.autostartstop.Log;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages the main plugin command and its subcommands.
 */
public class CommandManager implements SimpleCommand {
    private static final Logger logger = Log.get(CommandManager.class);
    private static final String MAIN_COMMAND = "autostartstop";
    private static final String ALIAS = "ass";
    private static final String BASE_PERMISSION = "autostartstop.command";

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final Object plugin;
    private final ProxyServer proxy;

    public CommandManager(Object plugin, ProxyServer proxy) {
        this.plugin = plugin;
        this.proxy = proxy;
    }

    /**
     * Registers a subcommand.
     *
     * @param subCommand The subcommand to register
     */
    public void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
        logger.debug("Registered subcommand: {}", subCommand.getName());
    }

    /**
     * Registers the main command with Velocity.
     */
    public void register() {
        com.velocitypowered.api.command.CommandManager commandManager = proxy.getCommandManager();
        commandManager.register(
                commandManager.metaBuilder(MAIN_COMMAND)
                        .aliases(ALIAS)
                        .plugin(plugin)
                        .build(),
                this
        );
        logger.debug("Registered command /{} (alias: /{})", MAIN_COMMAND, ALIAS);
    }

    /**
     * Unregisters the main command.
     */
    public void unregister() {
        proxy.getCommandManager().unregister(MAIN_COMMAND);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        logger.debug("Command executed by {} with args: {}", source, Arrays.toString(args));

        if (!source.hasPermission(BASE_PERMISSION)) {
            logger.debug("Command denied to {} - missing permission '{}'", source, BASE_PERMISSION);
            source.sendMessage(MiniMessageUtil.parse("<red>You don't have permission to use this command.</red>"));
            return;
        }

        if (args.length == 0) {
            logger.debug("No subcommand provided, showing help to {}", source);
            showHelp(source);
            return;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            logger.debug("Unknown subcommand '{}' requested by {}", subCommandName, source);
            source.sendMessage(MiniMessageUtil.parse("<red>Unknown subcommand: " + subCommandName + "</red>"));
            showHelp(source);
            return;
        }

        // Pass remaining arguments to subcommand
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        logger.debug("Executing subcommand '{}' for {} with args: {}", subCommandName, source, Arrays.toString(subArgs));
        subCommand.execute(source, subArgs);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission(BASE_PERMISSION)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (args.length <= 1) {
            // Suggest subcommand names
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> suggestions = subCommands.keySet().stream()
                    .filter(name -> name.startsWith(prefix))
                    .filter(name -> {
                        SubCommand subCommand = subCommands.get(name);
                        return source.hasPermission(subCommand.getPermission());
                    })
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(suggestions);
        }

        // Delegate to subcommand
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand != null && source.hasPermission(subCommand.getPermission())) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return CompletableFuture.completedFuture(subCommand.suggest(source, subArgs));
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(BASE_PERMISSION);
    }

    /**
     * Shows help message to the command source.
     */
    private void showHelp(CommandSource source) {
        source.sendMessage(MiniMessageUtil.parse("<gold>AutoStartStop Commands:</gold>"));

        for (SubCommand subCommand : subCommands.values()) {
            if (source.hasPermission(subCommand.getPermission())) {
                source.sendMessage(MiniMessageUtil.parse(
                        "<yellow>" + subCommand.getUsage() + "</yellow> <gray>- " + subCommand.getDescription() + "</gray>"
                ));
            }
        }
    }
}
