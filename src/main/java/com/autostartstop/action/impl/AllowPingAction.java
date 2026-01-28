package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionType;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.Log;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Action that allows a ping request to proceed normally.
 * This action allows the ping response to be sent if it was previously held.
 */
public class AllowPingAction implements Action {
    private static final Logger logger = Log.get(AllowPingAction.class);
    private static final String ACTION_NAME = "allow_ping";
    
    private final String pingEventParam;
    private final VariableResolver variableResolver;

    public AllowPingAction(String pingEventParam, VariableResolver variableResolver) {
        this.pingEventParam = pingEventParam;
        this.variableResolver = variableResolver;
    }

    /**
     * Creates an AllowPingAction from configuration.
     */
    public static AllowPingAction create(ActionConfig config, ActionContext ctx) {
        String pingEvent = config.getString("ping", "${ping}");
        return new AllowPingAction(pingEvent, ctx.variableResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.ALLOW_PING;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        
        // Get the ping event object from context - must be wrapped in ${}
        Object pingEventObj = null;
        if (pingEventParam.startsWith("${") && pingEventParam.endsWith("}")) {
            String variableName = VariableResolver.extractVariableName(pingEventParam);
            pingEventObj = variableResolver.resolveVariable(variableName, context);
        }
        
        if (pingEventObj == null) {
            logger.error("({}) {}: No ping event found for '{}'", ruleName, ACTION_NAME, pingEventParam);
            return CompletableFuture.completedFuture(null);
        }
        
        if (!(pingEventObj instanceof ProxyPingEvent)) {
            logger.error("({}) {}: '{}' is not a ProxyPingEvent: {}", 
                    ruleName, ACTION_NAME, pingEventParam, pingEventObj.getClass().getName());
            return CompletableFuture.completedFuture(null);
        }
        
        ProxyPingEvent event = (ProxyPingEvent) pingEventObj;
        
        // Allow the ping by setting the result to allowed
        // This allows the ping response to be sent normally
        event.setResult(ResultedEvent.GenericResult.allowed());
        
        logger.debug("({}) {}: Allowed ping request", ruleName, ACTION_NAME);
        
        // Signal the ping trigger to release the event immediately
        context.releaseEvent();
        
        return CompletableFuture.completedFuture(null);
    }

    public String getPingEventParam() { 
        return pingEventParam; 
    }
}
