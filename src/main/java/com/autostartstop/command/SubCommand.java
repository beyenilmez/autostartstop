package com.autostartstop.command;

import com.velocitypowered.api.command.CommandSource;

import java.util.List;

/**
 * Interface for subcommands of the main plugin command.
 */
public interface SubCommand {

    /**
     * Gets the name of this subcommand.
     *
     * @return The subcommand name
     */
    String getName();

    /**
     * Gets the permission required to use this subcommand.
     *
     * @return The permission string
     */
    String getPermission();

    /**
     * Gets the usage string for this subcommand.
     *
     * @return The usage string
     */
    String getUsage();

    /**
     * Gets the description of this subcommand.
     *
     * @return The description
     */
    String getDescription();

    /**
     * Executes this subcommand.
     *
     * @param source The command source
     * @param args The command arguments (excluding the subcommand name)
     */
    void execute(CommandSource source, String[] args);

    /**
     * Provides tab completion suggestions.
     *
     * @param source The command source
     * @param args The current arguments
     * @return List of suggestions
     */
    List<String> suggest(CommandSource source, String[] args);
}
