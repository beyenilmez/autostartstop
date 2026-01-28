package com.autostartstop.command.impl;

import com.autostartstop.command.SubCommand;
import com.autostartstop.util.MiniMessageUtil;
import com.autostartstop.Log;
import com.velocitypowered.api.command.CommandSource;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Subcommand for reloading the plugin configuration.
 */
public class ReloadCommand implements SubCommand {
    private static final Logger logger = Log.get(ReloadCommand.class);
    private static final String PERMISSION = "autostartstop.command.reload";

    private final Runnable reloadAction;

    public ReloadCommand(Runnable reloadAction) {
        this.reloadAction = reloadAction;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public String getUsage() {
        return "/autostartstop reload";
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin configuration";
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        logger.debug("ReloadCommand: executed by {}", source);
        
        if (!source.hasPermission(PERMISSION)) {
            logger.debug("ReloadCommand: denied to {} - missing permission '{}'", source, PERMISSION);
            source.sendMessage(MiniMessageUtil.parse("<red>You don't have permission to use this command.</red>"));
            return;
        }

        source.sendMessage(MiniMessageUtil.parse("<yellow>Reloading configuration...</yellow>"));
        logger.debug("Configuration reload initiated by {}", source);

        try {
            long startTime = System.currentTimeMillis();
            reloadAction.run();
            long duration = System.currentTimeMillis() - startTime;
            
            source.sendMessage(MiniMessageUtil.parse("<green>Configuration reloaded successfully!</green>"));
            logger.debug("Configuration reloaded by {} (took {}ms)", source, duration);
        } catch (Exception e) {
            source.sendMessage(MiniMessageUtil.parse("<red>Failed to reload configuration: " + e.getMessage() + "</red>"));
            logger.error("ReloadCommand: configuration reload failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> suggest(CommandSource source, String[] args) {
        return Collections.emptyList();
    }
}
