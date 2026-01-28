package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Trigger that fires when the Velocity proxy shuts down.
 */
public class ProxyShutdownTrigger implements Trigger {
    private static final Logger logger = Log.get(ProxyShutdownTrigger.class);

    private final ProxyServer proxy;
    private final Object plugin;

    // Runtime state (set during activate)
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private boolean activated = false;

    /**
     * Creates a ProxyShutdownTrigger from the given configuration.
     */
    public static ProxyShutdownTrigger create(TriggerConfig config, TriggerContext context) {
        return new ProxyShutdownTrigger(context.proxy(), context.plugin());
    }

    public ProxyShutdownTrigger(ProxyServer proxy, Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.PROXY_SHUTDOWN;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;
        this.activated = true;

        logger.debug("ProxyShutdownTrigger: activating for rule '{}'", ruleName);

        // Register for the event
        proxy.getEventManager().register(plugin, this);

        logger.debug("ProxyShutdownTrigger: registered for ProxyShutdownEvent");
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("ProxyShutdownTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        proxy.getEventManager().unregisterListener(plugin, this);

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;

        logger.debug("ProxyShutdownTrigger: unregistered from event manager");
    }

    // Use HIGH priority so this fires BEFORE the main plugin's cleanup handler, should be higher than 50
    @Subscribe(priority = 100)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (!activated || executionCallback == null) {
            logger.debug("ProxyShutdownTrigger: received event but not activated, ignoring");
            return;
        }

        logger.debug("ProxyShutdownTrigger: proxy shutdown event received for rule '{}'", ruleName);

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.PROXY_SHUTDOWN.getConfigName());

        // Invoke the execution callback (template will handle waiting if needed)
        logger.debug("ProxyShutdownTrigger: invoking execution callback for rule '{}'", ruleName);
        executionCallback.apply(context);
    }
}
