package com.autostartstop.util;

import com.autostartstop.Log;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for resolving target players and servers from action parameters.
 * Centralizes the common resolution logic used across player/server targeting actions.
 * 
 * <p>Supports resolving:
 * <ul>
 *   <li>Single player from 'player' parameter</li>
 *   <li>Multiple players from 'players' parameter (list or variable reference)</li>
 *   <li>Single server from 'server' parameter</li>
 *   <li>Multiple servers from 'servers' parameter (list or variable reference)</li>
 *   <li>Players connected to resolved servers</li>
 * </ul>
 * 
 * <p>Player resolution supports:
 * <ul>
 *   <li>Player objects directly</li>
 *   <li>Player names (String)</li>
 *   <li>Player UUIDs (String format)</li>
 *   <li>Collections of players or names</li>
 *   <li>Variable references (${...})</li>
 * </ul>
 */
public class TargetResolver {
    private static final Logger logger = Log.get(TargetResolver.class);
    
    private final VariableResolver variableResolver;
    private final ProxyServer proxy;
    
    public TargetResolver(VariableResolver variableResolver, ProxyServer proxy) {
        this.variableResolver = variableResolver;
        this.proxy = proxy;
    }
    
    /**
     * Resolves all target players from player/players and server/servers parameters.
     * 
     * @param playerParam Single player parameter (may be null)
     * @param playersParam List of player parameters (may be null)
     * @param serverParam Single server parameter (may be null)
     * @param serversParam List of server parameters (may be null)
     * @param context The execution context
     * @param actionName The action name for logging
     * @return Set of resolved players (never null, may be empty)
     */
    public Set<Player> resolveTargetPlayers(
            String playerParam, List<String> playersParam,
            String serverParam, List<String> serversParam,
            ExecutionContext context, String actionName) {
        
        Set<Player> targetPlayers = new HashSet<>();
        String ruleName = getRuleName(context);
        
        // Resolve from players parameter
        if (playersParam != null && !playersParam.isEmpty()) {
            for (String playerEntry : playersParam) {
                resolveAndAddPlayers(playerEntry, context, targetPlayers, actionName, ruleName);
            }
        }
        
        // Resolve from player parameter
        if (playerParam != null && !playerParam.isBlank()) {
            resolveAndAddPlayers(playerParam, context, targetPlayers, actionName, ruleName);
        }
        
        // Resolve from servers parameter
        if (serversParam != null && !serversParam.isEmpty()) {
            for (String serverEntry : serversParam) {
                resolveAndAddServerPlayers(serverEntry, context, targetPlayers, actionName, ruleName);
            }
        }
        
        // Resolve from server parameter
        if (serverParam != null && !serverParam.isBlank()) {
            resolveAndAddServerPlayers(serverParam, context, targetPlayers, actionName, ruleName);
        }
        
        return targetPlayers;
    }
    
    /**
     * Resolves target players from player/players parameters only (no server support).
     * 
     * @param playerParam Single player parameter (may be null)
     * @param playersParam List of player parameters (may be null)
     * @param context The execution context
     * @param actionName The action name for logging
     * @return Set of resolved players (never null, may be empty)
     */
    public Set<Player> resolvePlayersOnly(
            String playerParam, List<String> playersParam,
            ExecutionContext context, String actionName) {
        
        return resolveTargetPlayers(playerParam, playersParam, null, null, context, actionName);
    }
    
    /**
     * Resolves a single server from a parameter.
     * 
     * @param serverParam The server parameter
     * @param context The execution context
     * @param actionName The action name for logging
     * @return The resolved RegisteredServer, or null if not found
     */
    public RegisteredServer resolveServer(String serverParam, ExecutionContext context, String actionName) {
        if (serverParam == null || serverParam.isBlank()) {
            return null;
        }
        
        String ruleName = getRuleName(context);
        
        // Try to resolve as variable reference
        if (serverParam.startsWith("${") && serverParam.endsWith("}")) {
            String variableName = VariableResolver.extractVariableName(serverParam);
            Object serverObj = variableResolver.resolveVariable(variableName, context);
            
            if (serverObj != null) {
                if (serverObj instanceof RegisteredServer) {
                    return (RegisteredServer) serverObj;
                }
                if (serverObj instanceof String) {
                    return resolveServerByName((String) serverObj, actionName, ruleName);
                }
                logger.warn("({}) {}: Variable '{}' is not a server: {}", 
                        ruleName, actionName, variableName, serverObj.getClass().getSimpleName());
            }
            return null;
        }
        
        // Resolve as literal server name (with embedded ${...} variable substitution if present)
        String resolvedServerName = variableResolver.resolve(serverParam, context);
        return resolveServerByName(resolvedServerName, actionName, ruleName);
    }
    
    /**
     * Resolves a player parameter and adds the resulting player(s) to the collection.
     * Handles Player objects, player name strings, and Collections.
     * Only resolves as object variable if wrapped in ${...}.
     */
    private void resolveAndAddPlayers(String param, ExecutionContext context, 
            Collection<Player> players, String actionName, String ruleName) {
        
        // Try to resolve as variable reference
        if (param.startsWith("${") && param.endsWith("}")) {
            String variableName = VariableResolver.extractVariableName(param);
            Object resolved = variableResolver.resolveVariable(variableName, context);
            
            if (resolved != null) {
                if (resolved instanceof Player) {
                    players.add((Player) resolved);
                    logger.debug("({}) {}: Resolved variable '{}' to Player", ruleName, actionName, variableName);
                    return;
                } else if (resolved instanceof Collection) {
                    Collection<?> collection = (Collection<?>) resolved;
                    int added = 0;
                    for (Object item : collection) {
                        if (item instanceof Player) {
                            players.add((Player) item);
                            added++;
                        } else if (item instanceof String) {
                            if (resolvePlayerByName((String) item, players, actionName, ruleName)) {
                                added++;
                            }
                        }
                    }
                    logger.debug("({}) {}: Resolved variable '{}' to {} players", 
                            ruleName, actionName, variableName, added);
                    return;
                } else if (resolved instanceof String) {
                    resolvePlayerByName((String) resolved, players, actionName, ruleName);
                    return;
                } else {
                    logger.warn("({}) {}: Variable '{}' is unsupported type: {}", 
                            ruleName, actionName, variableName, resolved.getClass().getSimpleName());
                    return;
                }
            }
        }
        
        // Resolve as literal player name (with embedded ${...} variable substitution if present)
        String resolvedName = variableResolver.resolve(param, context);
        resolvePlayerByName(resolvedName, players, actionName, ruleName);
    }
    
    /**
     * Resolves a player by name or UUID and adds them to the collection if found.
     * 
     * @return true if player was found and added
     */
    private boolean resolvePlayerByName(String playerIdentifier, Collection<Player> players, 
            String actionName, String ruleName) {
        
        if (playerIdentifier == null || playerIdentifier.isBlank()) {
            return false;
        }
        
        // Try by name first
        Optional<Player> player = proxy.getPlayer(playerIdentifier);
        if (player.isPresent()) {
            players.add(player.get());
            return true;
        }
        
        // Try by UUID
        try {
            UUID uuid = UUID.fromString(playerIdentifier);
            Optional<Player> playerByUuid = proxy.getPlayer(uuid);
            if (playerByUuid.isPresent()) {
                players.add(playerByUuid.get());
                return true;
            }
        } catch (IllegalArgumentException e) {
            // Not a valid UUID
        }
        
        logger.warn("({}) {}: Player '{}' not found or not online", ruleName, actionName, playerIdentifier);
        return false;
    }
    
    /**
     * Resolves a server parameter and adds all players on that server to the collection.
     */
    private void resolveAndAddServerPlayers(String param, ExecutionContext context,
            Collection<Player> players, String actionName, String ruleName) {
        
        RegisteredServer server = resolveServer(param, context, actionName);
        if (server != null) {
            Collection<Player> serverPlayers = server.getPlayersConnected();
            players.addAll(serverPlayers);
            logger.debug("({}) {}: Added {} players from server '{}'", 
                    ruleName, actionName, serverPlayers.size(), server.getServerInfo().getName());
        }
    }
    
    /**
     * Resolves a server by name from the proxy.
     */
    private RegisteredServer resolveServerByName(String serverName, String actionName, String ruleName) {
        if (serverName == null || serverName.isBlank()) {
            return null;
        }
        
        Optional<RegisteredServer> server = proxy.getServer(serverName);
        if (server.isEmpty()) {
            logger.warn("({}) {}: Server '{}' not found", ruleName, actionName, serverName);
            return null;
        }
        
        return server.get();
    }
    
    /**
     * Gets the rule name from the context for logging.
     */
    private String getRuleName(ExecutionContext context) {
        return (String) context.getVariable("_rule_name", "unknown");
    }
    
    /**
     * Gets the VariableResolver used by this resolver.
     */
    public VariableResolver getVariableResolver() {
        return variableResolver;
    }
    
    /**
     * Gets the ProxyServer used by this resolver.
     */
    public ProxyServer getProxy() {
        return proxy;
    }
}
