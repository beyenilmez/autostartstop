package com.autostartstop.condition;

import com.autostartstop.context.ExecutionContext;

/**
 * Interface for all condition types.
 * Conditions filter when rules should execute.
 */
public interface Condition {

    /**
     * Gets the type of this condition.
     *
     * @return The condition type
     */
    ConditionType getType();

    /**
     * Evaluates this condition against the given context.
     *
     * @param context The execution context containing variables and state
     * @return true if the condition is satisfied
     */
    boolean evaluate(ExecutionContext context);
}
