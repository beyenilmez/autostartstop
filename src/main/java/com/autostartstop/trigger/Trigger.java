package com.autostartstop.trigger;

import com.autostartstop.context.ExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interface for all trigger types.
 * Triggers are self-contained - they manage their own event subscriptions
 * and invoke the execution callback when they fire.
 */
public interface Trigger {

    /**
     * Gets the type of this trigger.
     *
     * @return The trigger type
     */
    TriggerType getType();

    /**
     * Activates this trigger, starting event listening or scheduling.
     * Called when the rule containing this trigger is loaded.
     *
     * @param ruleName The name of the rule this trigger belongs to
     * @param executionCallback Callback to invoke when the trigger fires.
     *                          Returns a CompletableFuture that completes when execution is done.
     *                          Triggers that need to wait for execution (e.g., ConnectionTrigger with deny_connection)
     *                          can join() on this future.
     */
    void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback);

    /**
     * Deactivates this trigger, stopping event listening or scheduling.
     * Called when the rule is unloaded or the plugin is disabled.
     */
    void deactivate();
}
