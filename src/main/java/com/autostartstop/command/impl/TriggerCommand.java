package com.autostartstop.command.impl;

import com.autostartstop.command.SubCommand;
import com.autostartstop.rule.RuleManager;
import com.autostartstop.trigger.impl.ManualTrigger;
import com.autostartstop.util.MiniMessageUtil;
import com.autostartstop.Log;
import com.velocitypowered.api.command.CommandSource;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for manually triggering rules.
 */
public class TriggerCommand implements SubCommand {
    private static final Logger logger = Log.get(TriggerCommand.class);
    private static final String PERMISSION = "autostartstop.command.trigger";

    private final RuleManager ruleManager;

    public TriggerCommand(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    @Override
    public String getName() {
        return "trigger";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public String getUsage() {
        return "/autostartstop trigger <id> [args...]";
    }

    @Override
    public String getDescription() {
        return "Manually fires a trigger by ID";
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        logger.debug("TriggerCommand: executed by {} with args: {}", source, Arrays.toString(args));
        
        if (!source.hasPermission(PERMISSION)) {
            logger.debug("TriggerCommand: denied to {} - missing permission '{}'", source, PERMISSION);
            source.sendMessage(MiniMessageUtil.parse("<red>You don't have permission to use this command.</red>"));
            return;
        }

        if (args.length < 1) {
            logger.debug("TriggerCommand: no trigger ID provided by {}", source);
            source.sendMessage(MiniMessageUtil.parse("<red>Usage: " + getUsage() + "</red>"));
            return;
        }

        String triggerId = args[0];
        String[] triggerArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        logger.debug("TriggerCommand: looking for manual triggers with ID '{}'", triggerId);

        // Find manual triggers with this ID
        List<ManualTrigger> matchingTriggers = ruleManager.getManualTriggers(triggerId);

        if (matchingTriggers.isEmpty()) {
            logger.warn("TriggerCommand: no manual triggers found for ID '{}' (requested by {})", triggerId, source);
            source.sendMessage(MiniMessageUtil.parse("<red>No manual triggers found with ID: " + triggerId + "</red>"));
            return;
        }

        // Count only activated triggers
        long activatedCount = matchingTriggers.stream().filter(ManualTrigger::isActivated).count();
        
        if (activatedCount == 0) {
            logger.warn("TriggerCommand: found {} manual triggers for ID '{}' but none are activated", matchingTriggers.size(), triggerId);
            source.sendMessage(MiniMessageUtil.parse("<red>Manual triggers with ID '" + triggerId + "' exist but are not activated.</red>"));
            return;
        }

        logger.debug("Manual trigger '{}' fired by {} with args: {} ({} triggers)", 
                triggerId, source, Arrays.toString(triggerArgs), activatedCount);

        // Fire all matching manual triggers
        int fired = 0;
        for (ManualTrigger trigger : matchingTriggers) {
            if (!trigger.isActivated()) {
                logger.debug("TriggerCommand: skipping inactive trigger with ID '{}'", triggerId);
                continue;
            }

            logger.debug("TriggerCommand: firing manual trigger '{}'", triggerId);
            trigger.fire(triggerArgs);
            fired++;
        }

        source.sendMessage(MiniMessageUtil.parse("<green>Fired " + fired + " manual trigger(s) with ID '" + triggerId + "'.</green>"));
        logger.debug("TriggerCommand: fired {} manual trigger(s) for ID '{}'", fired, triggerId);
    }

    @Override
    public List<String> suggest(CommandSource source, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return ruleManager.getManualTriggerIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(prefix))
                    .toList();
        }

        return Collections.emptyList();
    }
}
