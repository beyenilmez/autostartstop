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
 * Action that denies a ping request.
 * This action cancels the ProxyPingEvent to prevent the ping response from being sent.
 */
public class DenyPingAction implements Action {
    private static final Logger logger = Log.get(DenyPingAction.class);
    private static final String ACTION_NAME = "deny_ping";
    
    private final String pingEventParam;
    private final VariableResolver variableResolver;

    public DenyPingAction(String pingEventParam, VariableResolver variableResolver) {
        this.pingEventParam = pingEventParam;
        this.variableResolver = variableResolver;
    }

    /**
     * Creates a DenyPingAction from configuration.
     */
    public static DenyPingAction create(ActionConfig config, ActionContext ctx) {
        String pingEvent = config.getString("ping", "${ping}");
        return new DenyPingAction(pingEvent, ctx.variableResolver());
    }

    @Override
    public ActionType getType() {
        return ActionType.DENY_PING;
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
        
        // Deny the ping by canceling the event
        // This prevents the ping response from being sent
        event.setResult(ResultedEvent.GenericResult.denied());
        
        logger.debug("({}) {}: Denied ping request", ruleName, ACTION_NAME);
        
        // Signal the ping trigger to release the event immediately
        context.releaseEvent();
        
        return CompletableFuture.completedFuture(null);
    }

    public String getPingEventParam() { 
        return pingEventParam; 
    }
}
