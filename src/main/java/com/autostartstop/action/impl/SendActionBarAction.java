package com.autostartstop.action.impl;

import com.autostartstop.action.AbstractPlayerTargetingAction;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import com.autostartstop.util.MiniMessageUtil;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/**
 * Action that sends an action bar message to one or more players.
 */
public class SendActionBarAction extends AbstractPlayerTargetingAction {
    private static final Logger logger = Log.get(SendActionBarAction.class);
    
    private final String message;

    public SendActionBarAction(String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            String message, TargetResolver targetResolver) {
        super(playerParam, playersParam, serverParam, serversParam, targetResolver);
        this.message = message;
    }

    /**
     * Creates a SendActionBarAction from configuration.
     */
    public static SendActionBarAction create(ActionConfig config, ActionContext ctx) {
        String player = config.getString("player");
        List<String> players = config.getStringList("players");
        String server = config.getString("server");
        List<String> servers = config.getStringList("servers");
        String message = config.requireString("message");
        return new SendActionBarAction(player, players, server, servers, message, ctx.targetResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.SEND_ACTION_BAR;
    }

    @Override
    protected void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName) {
        // Resolve and parse the message
        if (message == null || message.isBlank()) {
            logger.error("({}) {}: No message provided", ruleName, getActionName());
            return;
        }
        
        String resolvedMessage = getTargetResolver().getVariableResolver().resolve(message, context);
        Component messageComponent = MiniMessageUtil.parse(resolvedMessage);
        
        // Send action bar to all players
        int sentCount = 0;
        for (Player player : players) {
            try {
                player.sendActionBar(messageComponent);
                sentCount++;
            } catch (Exception e) {
                logger.error("({}) {}: Failed for player '{}': {}", 
                        ruleName, getActionName(), player.getUsername(), e.getMessage());
            }
        }
        
        logger.debug("({}) {}: Sent to {} player(s)", ruleName, getActionName(), sentCount);
    }

    public String getMessage() { return message; }
}
