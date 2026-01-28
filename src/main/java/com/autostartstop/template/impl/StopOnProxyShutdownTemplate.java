package com.autostartstop.template.impl;

import com.autostartstop.Log;
import com.autostartstop.action.impl.StopAction;
import com.autostartstop.config.TemplateConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.template.Template;
import com.autostartstop.template.TemplateContext;
import com.autostartstop.template.TemplateType;
import com.autostartstop.trigger.impl.ProxyShutdownTrigger;
import com.autostartstop.util.DurationUtil;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Template that stops servers when the proxy shuts down.
 * 
 * Configuration:
 * - servers: List of server names to stop on proxy shutdown
 * 
 * Uses ProxyShutdownTrigger internally to detect shutdown and StopAction to stop servers.
 */
public class StopOnProxyShutdownTemplate implements Template {
    private static final Logger logger = Log.get(StopOnProxyShutdownTemplate.class);

    private final TemplateContext context;
    private final List<String> servers;
    
    private String ruleName;
    private ProxyShutdownTrigger trigger;
    private boolean activated = false;

    /**
     * Creates a StopOnProxyShutdownTemplate from the given configuration.
     */
    public static StopOnProxyShutdownTemplate create(TemplateConfig config, TemplateContext context) {
        List<String> servers = config.getServers();
        
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("stop_on_proxy_shutdown template requires at least one server");
        }

        return new StopOnProxyShutdownTemplate(context, servers);
    }

    private StopOnProxyShutdownTemplate(TemplateContext context, List<String> servers) {
        this.context = context;
        this.servers = servers;
    }

    @Override
    public TemplateType getType() {
        return TemplateType.STOP_ON_PROXY_SHUTDOWN;
    }

    @Override
    public void activate(String ruleName) {
        if (activated) {
            return;
        }
        
        this.ruleName = ruleName;
        logger.debug("StopOnProxyShutdownTemplate: activating for rule '{}' (servers: {})",
                ruleName, servers);

        // Create the trigger directly
        trigger = new ProxyShutdownTrigger(
                context.proxy(),
                context.plugin());

        // Create execution callback that stops all configured servers
        Function<ExecutionContext, CompletableFuture<Void>> executionCallback = ctx -> {
            // Set rule name in context for actions
            ctx.setVariable("_rule_name", ruleName);
            
            logger.debug("StopOnProxyShutdownTemplate: proxy shutdown detected, stopping {} server(s)...", 
                    servers.size());

            // Stop all servers sequentially
            CompletableFuture<Void> allStopped = CompletableFuture.completedFuture(null);
            for (String serverName : servers) {
                logger.debug("StopOnProxyShutdownTemplate: stopping server '{}'", serverName);

                StopAction stopAction = new StopAction(
                        serverName,
                        this.context.serverManager(),
                        this.context.variableResolver(),
                        this.context.motdCacheManager());

                allStopped = allStopped.thenCompose(v -> stopAction.execute(ctx));
            }

            // Wait for all actions to complete with timeout before proxy shuts down
            CompletableFuture<Void> result = allStopped.thenAccept(v -> 
                    logger.debug("StopOnProxyShutdownTemplate: all {} server(s) stopped", servers.size()));
            
            // Get shutdown timeout from settings
            Duration shutdownTimeout = Duration.ofSeconds(30);
            if (context.settings() != null) {
                try {
                    String timeoutStr = context.settings().getShutdownTimeout();
                    if (timeoutStr != null && !timeoutStr.isEmpty()) {
                        shutdownTimeout = DurationUtil.parse(timeoutStr);
                    }
                } catch (Exception e) {
                    logger.warn("StopOnProxyShutdownTemplate: invalid shutdown_timeout in config, using default 30s");
                }
            }
            
            // Wait for completion with timeout (blocking call in shutdown event handler)
            try {
                logger.debug("StopOnProxyShutdownTemplate: waiting up to {} for all servers to stop...", 
                        DurationUtil.format(shutdownTimeout));
                result.get(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
                logger.debug("StopOnProxyShutdownTemplate: all actions completed for rule '{}'", ruleName);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.warn("StopOnProxyShutdownTemplate: timeout waiting for servers to stop for rule '{}' (timeout: {})", 
                        ruleName, DurationUtil.format(shutdownTimeout));
            } catch (Exception e) {
                logger.error("StopOnProxyShutdownTemplate: error waiting for servers to stop for rule '{}': {}", 
                        ruleName, e.getMessage());
                logger.debug("StopOnProxyShutdownTemplate: execution error details:", e);
            }
            
            return result;
        };

        // Activate the trigger
        trigger.activate(ruleName, executionCallback);
        activated = true;
        
        logger.debug("StopOnProxyShutdownTemplate: activated for rule '{}'", ruleName);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("StopOnProxyShutdownTemplate: deactivating for rule '{}'", ruleName);

        if (trigger != null) {
            trigger.deactivate();
            trigger = null;
        }

        this.ruleName = null;
        activated = false;

        logger.debug("StopOnProxyShutdownTemplate: deactivated for rule '{}'", ruleName);
    }
}
