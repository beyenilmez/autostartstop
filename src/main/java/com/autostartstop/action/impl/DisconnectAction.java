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
 * Action that disconnects one or more players from the proxy.
 */
public class DisconnectAction extends AbstractPlayerTargetingAction {
    private static final Logger logger = Log.get(DisconnectAction.class);
    
    private final String reason;

    public DisconnectAction(String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            String reason, TargetResolver targetResolver) {
        super(playerParam, playersParam, serverParam, serversParam, targetResolver);
        this.reason = reason;
    }

    /**
     * Creates a DisconnectAction from configuration.
     */
    public static DisconnectAction create(ActionConfig config, ActionContext ctx) {
        String player = config.getString("player");
        List<String> players = config.getStringList("players");
        String server = config.getString("server");
        List<String> servers = config.getStringList("servers");
        String reason = config.getString("reason");
        return new DisconnectAction(player, players, server, servers, reason, ctx.targetResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.DISCONNECT;
    }

    @Override
    protected void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName) {
        // Resolve the disconnect reason
        Component reasonComponent = null;
        if (reason != null && !reason.isBlank()) {
            String resolvedReason = getTargetResolver().getVariableResolver().resolve(reason, context);
            reasonComponent = MiniMessageUtil.parse(resolvedReason);
        }
        
        // Disconnect all players
        int disconnectedCount = 0;
        for (Player player : players) {
            try {
                if (reasonComponent != null) {
                    player.disconnect(reasonComponent);
                } else {
                    player.disconnect(Component.empty());
                }
                disconnectedCount++;
            } catch (Exception e) {
                logger.error("({}) {}: Failed for player '{}': {}", 
                        ruleName, getActionName(), player.getUsername(), e.getMessage());
            }
        }
        
        logger.debug("({}) {}: Disconnected {} player(s)", ruleName, getActionName(), disconnectedCount);
    }

    public String getReason() { return reason; }
}
