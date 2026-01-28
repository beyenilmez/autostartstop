package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Trigger that fires when the Velocity proxy initializes.
 * Since rules are loaded after ProxyInitializeEvent has already fired,
 * this trigger fires immediately upon activation.
 */
public class ProxyStartTrigger implements Trigger {
    private static final Logger logger = Log.get(ProxyStartTrigger.class);

    /**
     * Creates a ProxyStartTrigger from the given configuration.
     */
    public static ProxyStartTrigger create(TriggerConfig config, TriggerContext context) {
        return new ProxyStartTrigger(context);
    }

    // Context from creation (used to check if this is a reload)
    private final TriggerContext triggerContext;

    // Runtime state (set during activate)
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private boolean activated = false;

    private ProxyStartTrigger(TriggerContext context) {
        this.triggerContext = context;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.PROXY_START;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;
        this.activated = true;

        logger.debug("ProxyStartTrigger: activating for rule '{}'", ruleName);

        // Only fire on initial proxy startup, not on plugin reload
        if (triggerContext.isReload()) {
            logger.debug("ProxyStartTrigger: skipping fire for rule '{}' (this is a reload, not initial startup)", ruleName);
            return;
        }

        // Fire immediately since ProxyInitializeEvent has already happened
        // (rules are loaded during onProxyInitialization, after the event fired)
        logger.debug("ProxyStartTrigger: firing immediately for rule '{}' (proxy already running)", ruleName);
        fire();
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("ProxyStartTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;
    }

    /**
     * Fires this trigger, creating context and invoking the callback.
     */
    private void fire() {
        if (!activated || executionCallback == null) {
            logger.debug("ProxyStartTrigger: attempted to fire but not activated, ignoring");
            return;
        }

        logger.debug("ProxyStartTrigger: firing for rule '{}'", ruleName);

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.PROXY_START.getConfigName());

        // Invoke the execution callback (fire-and-forget for proxy start)
        logger.debug("ProxyStartTrigger: invoking execution callback for rule '{}'", ruleName);
        executionCallback.apply(context);
    }
}
