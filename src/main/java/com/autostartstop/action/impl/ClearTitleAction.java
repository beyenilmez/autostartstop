package com.autostartstop.action.impl;

import com.autostartstop.action.AbstractPlayerTargetingAction;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/**
 * Action that clears the title for one or more players.
 */
public class ClearTitleAction extends AbstractPlayerTargetingAction {
    private static final Logger logger = Log.get(ClearTitleAction.class);

    public ClearTitleAction(String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            TargetResolver targetResolver) {
        super(playerParam, playersParam, serverParam, serversParam, targetResolver);
    }

    /**
     * Creates a ClearTitleAction from configuration.
     */
    public static ClearTitleAction create(ActionConfig config, ActionContext ctx) {
        String player = config.getString("player");
        List<String> players = config.getStringList("players");
        String server = config.getString("server");
        List<String> servers = config.getStringList("servers");
        return new ClearTitleAction(player, players, server, servers, ctx.targetResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.CLEAR_TITLE;
    }

    @Override
    protected void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName) {
        int clearedCount = 0;
        for (Player player : players) {
            try {
                player.clearTitle();
                clearedCount++;
            } catch (Exception e) {
                logger.error("({}) {}: Failed for player '{}': {}", 
                        ruleName, getActionName(), player.getUsername(), e.getMessage());
            }
        }
        logger.debug("({}) {}: Cleared for {} player(s)", ruleName, getActionName(), clearedCount);
    }
}

