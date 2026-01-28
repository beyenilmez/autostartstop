package com.autostartstop.trigger.impl;

import com.autostartstop.Log;
import com.autostartstop.config.ConfigException;
import com.autostartstop.config.TriggerConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.trigger.Trigger;
import com.autostartstop.trigger.TriggerContext;
import com.autostartstop.trigger.TriggerType;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Trigger that fires via console command.
 * Emitted context:
 * - ${manual.id} - The trigger ID
 * - ${manual.args.0}, ${manual.args.1}, ... - Command arguments
 * - ${manual.args.length} - Argument count
 */
public class ManualTrigger implements Trigger {
    private static final Logger logger = Log.get(ManualTrigger.class);

    private final String id;

    /**
     * Creates a ManualTrigger from the given configuration.
     */
    public static ManualTrigger create(TriggerConfig config, TriggerContext context) {
        String id = config.getId();
        if (id == null || id.isBlank()) {
            throw ConfigException.required("manual", "id");
        }
        return new ManualTrigger(id);
    }

    // Runtime state (set during activate)
    private String ruleName;
    private Function<ExecutionContext, CompletableFuture<Void>> executionCallback;
    private boolean activated = false;

    public ManualTrigger(String id) {
        this.id = id;
    }

    @Override
    public TriggerType getType() {
        return TriggerType.MANUAL;
    }

    @Override
    public void activate(String ruleName, Function<ExecutionContext, CompletableFuture<Void>> executionCallback) {
        this.ruleName = ruleName;
        this.executionCallback = executionCallback;
        this.activated = true;

        logger.debug("ManualTrigger: activating for rule '{}' with id '{}'", ruleName, id);
    }

    @Override
    public void deactivate() {
        if (!activated) {
            return;
        }

        logger.debug("ManualTrigger: deactivating for rule '{}'", ruleName != null ? ruleName : "unknown");

        this.ruleName = null;
        this.executionCallback = null;
        this.activated = false;
    }

    /**
     * Fires this manual trigger with the given arguments.
     * Called by TriggerCommand when a manual trigger is invoked.
     *
     * @param args The command arguments
     */
    public void fire(String[] args) {
        if (!activated || executionCallback == null) {
            logger.warn("ManualTrigger: attempted to fire trigger '{}' but it is not activated", id);
            return;
        }

        logger.debug("ManualTrigger: firing trigger '{}' for rule '{}' with {} args",
                id, ruleName, args.length);

        // Create execution context
        ExecutionContext context = new ExecutionContext();
        context.setVariable("_trigger_type", TriggerType.MANUAL.getConfigName());
        context.setVariable("manual.id", id);

        // Emit arguments into context
        emitArgs(context, args);

        // Invoke the execution callback (fire-and-forget for manual triggers)
        logger.debug("ManualTrigger: invoking execution callback for rule '{}'", ruleName);
        executionCallback.apply(context);
    }

    /**
     * Emits the manual trigger arguments into the context.
     *
     * @param context The execution context
     * @param args The command arguments
     */
    private void emitArgs(ExecutionContext context, String[] args) {
        context.setVariable("manual.args.length", args.length);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("manual.args." + i, args[i]);
        }
    }

    /**
     * Gets the ID of this manual trigger.
     *
     * @return The trigger ID
     */
    public String getId() {
        return id;
    }

    /**
     * Checks if this trigger is currently activated.
     *
     * @return true if activated
     */
    public boolean isActivated() {
        return activated;
    }
}
