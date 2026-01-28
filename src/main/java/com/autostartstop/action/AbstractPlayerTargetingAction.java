package com.autostartstop.action;

import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import com.autostartstop.util.TargetResolver;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for actions that target one or more players.
 * Provides common player/server resolution logic and reduces boilerplate.
 * 
 * <p>Subclasses only need to implement {@link #executeForPlayers(Set, ExecutionContext, String)}
 * with their specific action logic.
 * 
 * <p>Parameters supported:
 * <ul>
 *   <li>player: Single player (Player object, player name, or UUID string)</li>
 *   <li>players: Multiple players (list of player names/UUIDs)</li>
 *   <li>server: Single server (target all players on that server)</li>
 *   <li>servers: Multiple servers (target all players on those servers)</li>
 * </ul>
 */
public abstract class AbstractPlayerTargetingAction implements Action {
    private static final Logger logger = Log.get(AbstractPlayerTargetingAction.class);
    
    protected final String playerParam;
    protected final List<String> playersParam;
    protected final String serverParam;
    protected final List<String> serversParam;
    protected final TargetResolver targetResolver;

    /**
     * Creates a new player-targeting action.
     *
     * @param playerParam Single player parameter (may be null)
     * @param playersParam List of player parameters (may be null)
     * @param serverParam Single server parameter (may be null)
     * @param serversParam List of server parameters (may be null)
     * @param targetResolver The target resolver for resolving players
     */
    protected AbstractPlayerTargetingAction(
            String playerParam,
            List<String> playersParam,
            String serverParam,
            List<String> serversParam,
            TargetResolver targetResolver) {
        this.playerParam = playerParam;
        this.playersParam = playersParam;
        this.serverParam = serverParam;
        this.serversParam = serversParam;
        this.targetResolver = targetResolver;
    }

    /**
     * Executes the action-specific logic for the resolved players.
     * This method is called after players have been resolved and validated.
     *
     * @param players The set of resolved players (never empty)
     * @param context The execution context
     * @param ruleName The name of the rule being executed
     */
    protected abstract void executeForPlayers(Set<Player> players, ExecutionContext context, String ruleName);

    @Override
    public final CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = getRuleName(context);
        String actionName = getActionName();
        
        // Resolve all target players
        Set<Player> targetPlayers = targetResolver.resolveTargetPlayers(
                playerParam, playersParam, serverParam, serversParam, context, actionName);
        
        if (targetPlayers.isEmpty()) {
            logger.warn("({}) {}: No valid players found", ruleName, actionName);
            return CompletableFuture.completedFuture(null);
        }
        
        // Execute the action-specific logic
        executeForPlayers(targetPlayers, context, ruleName);
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Gets the action name for logging purposes.
     * Defaults to the config name from the action type.
     *
     * @return The action name
     */
    protected String getActionName() {
        return getType().getConfigName();
    }

    /**
     * Gets the rule name from the execution context.
     *
     * @param context The execution context
     * @return The rule name, or "unknown" if not set
     */
    protected String getRuleName(ExecutionContext context) {
        return (String) context.getVariable("_rule_name", "unknown");
    }

    /**
     * Gets the target resolver used by this action.
     *
     * @return The target resolver
     */
    protected TargetResolver getTargetResolver() {
        return targetResolver;
    }

    // Getters for subclasses and potential serialization
    public String getPlayerParam() { return playerParam; }
    public List<String> getPlayersParam() { return playersParam; }
    public String getServerParam() { return serverParam; }
    public List<String> getServersParam() { return serversParam; }
}
