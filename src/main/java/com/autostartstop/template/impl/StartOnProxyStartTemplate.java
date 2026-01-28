package com.autostartstop.template.impl;

import com.autostartstop.Log;
import com.autostartstop.action.impl.StartAction;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.template.Template;
import com.autostartstop.template.TemplateContext;
import com.autostartstop.template.TemplateType;
import com.autostartstop.trigger.impl.ProxyStartTrigger;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Template that starts servers when the proxy starts.
 * 
 * Configuration:
 * - servers: List of server names to start on proxy start
 * 
 * Uses ProxyStartTrigger internally to detect startup and StartAction to start servers.
 */
public class StartOnProxyStartTemplate implements Template {
    private static final Logger logger = Log.get(StartOnProxyStartTemplate.class);

    private final TemplateContext context;
    private final List<String> servers;
    
    private String ruleName;
    private ProxyStartTrigger trigger;
    private boolean activated = false;

    /**
     * Creates a StartOnProxyStartTemplate from the given configuration.
     */
    public static StartOnProxyStartTemplate create(TemplateConfig config, TemplateContext context) {
        List<String> servers = config.getServers();
        
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("start_on_proxy_start template requires at least one server");
        }

        return new StartOnProxyStartTemplate(context, servers);
    }

    private StartOnProxyStartTemplate(TemplateContext context, List<String> servers) {
        this.context = context;
        this.servers = servers;
    }

    @Override
    public TemplateType getType() {
        return TemplateType.START_ON_PROXY_START;
    }

    @Override
    public void activate(String ruleName) {
        if (activated) {
            return;
        }
        
        this.ruleName = ruleName;
        logger.debug("StartOnProxyStartTemplate: activating for rule '{}' (servers: {})",
                ruleName, servers);

        // Create the trigger directly using TriggerContext from registry
        trigger = ProxyStartTrigger.create(null, 
                context.triggerRegistry().getTriggerContext());

        // Create execution callback that starts all configured servers
        Function<ExecutionContext, CompletableFuture<Void>> executionCallback = context -> {
            // Set rule name in context for actions
            context.setVariable("_rule_name", ruleName);
            
            logger.debug("StartOnProxyStartTemplate: proxy start detected, starting {} server(s)...", 
                    servers.size());

            // Start all servers sequentially
            CompletableFuture<Void> allStarted = CompletableFuture.completedFuture(null);
            for (String serverName : servers) {
                logger.debug("StartOnProxyStartTemplate: starting server '{}'", serverName);

                StartAction startAction = new StartAction(
                        serverName,
                        this.context.serverManager(),
                        this.context.variableResolver(),
                        this.context.startupTracker());

                allStarted = allStarted.thenCompose(v -> startAction.execute(context));
            }

            return allStarted.thenAccept(v -> 
                    logger.debug("StartOnProxyStartTemplate: all {} server(s) started", servers.size()));
        };

        // Activate the trigger
        trigger.activate(ruleName, executionCallback);
        activated = true;
        
        logger.debug("StartOnProxyStartTemplate: activated for rule '{}'", ruleName);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("StartOnProxyStartTemplate: deactivating for rule '{}'", ruleName);

        if (trigger != null) {
            trigger.deactivate();
            trigger = null;
        }

        this.ruleName = null;
        activated = false;

        logger.debug("StartOnProxyStartTemplate: deactivated for rule '{}'", ruleName);
    }
}
