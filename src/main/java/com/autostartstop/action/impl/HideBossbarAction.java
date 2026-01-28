package com.autostartstop.action.impl;

import com.autostartstop.action.AbstractPlayerTargetingAction;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.bossbar.BossBar;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Action that hides a bossbar from one or more players.
 */
public class HideBossbarAction extends AbstractPlayerTargetingAction {
    private static final Logger logger = Log.get(HideBossbarAction.class);
    
    private final String id;

    public HideBossbarAction(String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            String id, TargetResolver targetResolver) {
        super(playerParam, playersParam, serverParam, serversParam, targetResolver);
        this.id = id;
    }

    /**
     * Creates a HideBossbarAction from configuration.
     */
    public static HideBossbarAction create(ActionConfig config, ActionContext ctx) {
        String player = config.getString("player");
        List<String> players = config.getStringList("players");
        String server = config.getString("server");
        List<String> servers = config.getStringList("servers");
        String id = config.requireString("id");
        return new HideBossbarAction(player, players, server, servers, id, ctx.targetResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.HIDE_BOSSBAR;
    }

    @Override
    protected void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName) {
        // Resolve the bossbar ID
        String resolvedId = getTargetResolver().getVariableResolver().resolve(id, context);
        if (resolvedId == null || resolvedId.isBlank()) {
            logger.error("({}) {}: No bossbar id provided", ruleName, getActionName());
            return;
        }
        
        // Get the bossbar map for this ID
        Map<String, Map<UUID, BossBar>> activeBossbars = ShowBossbarAction.getActiveBossbars();
        Map<UUID, BossBar> playerBossbars = activeBossbars.get(resolvedId);
        
        if (playerBossbars == null || playerBossbars.isEmpty()) {
            logger.debug("({}) {}: No active bossbars for id '{}'", ruleName, getActionName(), resolvedId);
            return;
        }
        
        // Hide bossbar from all players
        int hiddenCount = 0;
        for (Player player : players) {
            try {
                UUID playerUuid = player.getUniqueId();
                BossBar bossBar = playerBossbars.remove(playerUuid);
                
                if (bossBar != null) {
                    player.hideBossBar(bossBar);
                    hiddenCount++;
                }
            } catch (Exception e) {
                logger.error("({}) {}: Failed for player '{}': {}", 
                        ruleName, getActionName(), player.getUsername(), e.getMessage());
            }
        }
        
        // Clean up empty bossbar ID map
        if (playerBossbars.isEmpty()) {
            activeBossbars.remove(resolvedId);
        }
        
        logger.debug("({}) {}: Hid '{}' from {} player(s)", ruleName, getActionName(), resolvedId, hiddenCount);
    }

    public String getId() { return id; }
}
