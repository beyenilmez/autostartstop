package com.autostartstop.action;

import com.autostartstop.context.ExecutionContext;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for all action types.
 * Actions perform operations using context data.
 */
public interface Action {

    /**
     * Gets the type of this action.
     *
     * @return The action type
     */
    ActionType getType();

    /**
     * Executes this action with the given context.
     *
     * @param context The execution context containing variables and state
     * @return A CompletableFuture that completes when the action finishes
     */
    CompletableFuture<Void> execute(ExecutionContext context);
}
