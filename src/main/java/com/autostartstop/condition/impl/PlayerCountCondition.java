package com.autostartstop.condition.impl;

import com.autostartstop.Log;
import com.autostartstop.condition.Condition;
import com.autostartstop.condition.ConditionContext;
import com.autostartstop.condition.ConditionType;
import com.autostartstop.config.ConfigException;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.ServerManager;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Checks player count on a server against min, max, or equals constraints.
 */
public class PlayerCountCondition implements Condition {
    private static final Logger logger = Log.get(PlayerCountCondition.class);
    
    private final String server;
    private final String min;
    private final String max;
    private final String equals;
    private final ServerManager serverManager;
    private final VariableResolver variableResolver;

    public PlayerCountCondition(String server, String min, String max, String equals, 
                                ServerManager serverManager, VariableResolver variableResolver) {
        this.server = server;
        this.min = min;
        this.max = max;
        this.equals = equals;
        this.serverManager = serverManager;
        this.variableResolver = variableResolver;
    }

    public static PlayerCountCondition create(Map<String, Object> config, ConditionContext ctx) {
        Object serverObj = config.get("server");
        if (serverObj == null) {
            throw ConfigException.required("player_count", "server");
        }
        
        return new PlayerCountCondition(
                serverObj.toString(),
                config.get("min") != null ? config.get("min").toString() : null,
                config.get("max") != null ? config.get("max").toString() : null,
                config.get("equals") != null ? config.get("equals").toString() : null,
                ctx.serverManager(),
                ctx.variableResolver()
        );
    }

    @Override
    public ConditionType getType() {
        return ConditionType.PLAYER_COUNT;
    }

    @Override
    public boolean evaluate(ExecutionContext context) {
        String resolvedServer = variableResolver.resolve(server, context);
        int playerCount = serverManager.getServerPlayerCount(resolvedServer);
        logger.debug("Server '{}' has {} players", resolvedServer, playerCount);

        if (equals != null && !equals.isBlank()) {
            int resolvedEquals = variableResolver.resolveInt(equals, context, Integer.MIN_VALUE);
            if (resolvedEquals != Integer.MIN_VALUE) {
                boolean result = playerCount == resolvedEquals;
                logger.debug("{} == {} -> {}", playerCount, resolvedEquals, result);
                return result;
            }
        }

        if (min != null && !min.isBlank()) {
            int resolvedMin = variableResolver.resolveInt(min, context, Integer.MIN_VALUE);
            if (resolvedMin != Integer.MIN_VALUE && playerCount < resolvedMin) {
                logger.debug("{} < min({}) -> false", playerCount, resolvedMin);
                return false;
            }
        }

        if (max != null && !max.isBlank()) {
            int resolvedMax = variableResolver.resolveInt(max, context, Integer.MAX_VALUE);
            if (resolvedMax != Integer.MAX_VALUE && playerCount > resolvedMax) {
                logger.debug("{} > max({}) -> false", playerCount, resolvedMax);
                return false;
            }
        }

        logger.debug("{} within range -> true", playerCount);
        return true;
    }

    public String getServer() { return server; }
    public String getMin() { return min; }
    public String getMax() { return max; }
    public String getEquals() { return equals; }
}
