package com.autostartstop.condition.impl;

import com.autostartstop.Log;
import com.autostartstop.condition.Condition;
import com.autostartstop.condition.ConditionContext;
import com.autostartstop.condition.ConditionType;
import com.autostartstop.config.ConfigException;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.context.VariableResolver;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Condition that compares two string values.
 * Supports variable resolution and case-insensitive comparison.
 */
public class StringEqualsCondition implements Condition {
    private static final Logger logger = Log.get(StringEqualsCondition.class);
    
    private final String value;
    private final String equals;
    private final boolean ignoreCase;
    private final VariableResolver variableResolver;

    public StringEqualsCondition(String value, String equals, boolean ignoreCase, VariableResolver variableResolver) {
        this.value = value;
        this.equals = equals;
        this.ignoreCase = ignoreCase;
        this.variableResolver = variableResolver;
    }

    /**
     * Creates a StringEqualsCondition from configuration.
     */
    public static StringEqualsCondition create(Map<String, Object> config, ConditionContext ctx) {
        Object valueObj = config.get("value");
        Object equalsObj = config.get("equals");
        Object ignoreCaseObj = config.get("ignore_case");

        if (valueObj == null) {
            throw ConfigException.required("string_equals", "value");
        }
        if (equalsObj == null) {
            throw ConfigException.required("string_equals", "equals");
        }

        String value = valueObj.toString();
        String equals = equalsObj.toString();
        boolean ignoreCase = ignoreCaseObj != null && Boolean.parseBoolean(ignoreCaseObj.toString());

        return new StringEqualsCondition(value, equals, ignoreCase, ctx.variableResolver());
    }

    @Override
    public ConditionType getType() {
        return ConditionType.STRING_EQUALS;
    }

    @Override
    public boolean evaluate(ExecutionContext context) {
        String resolvedValue = variableResolver.resolve(value, context);
        String resolvedEquals = variableResolver.resolve(equals, context);

        logger.debug("StringEqualsCondition: comparing '{}' (resolved from '{}') with '{}' (resolved from '{}'), ignoreCase={}", 
                resolvedValue, value, resolvedEquals, equals, ignoreCase);

        if (resolvedValue == null && resolvedEquals == null) {
            logger.debug("StringEqualsCondition: both values are null, returning true");
            return true;
        }
        if (resolvedValue == null || resolvedEquals == null) {
            logger.debug("StringEqualsCondition: one value is null (value={}, equals={}), returning false", 
                    resolvedValue, resolvedEquals);
            return false;
        }

        boolean result;
        if (ignoreCase) {
            result = resolvedValue.equalsIgnoreCase(resolvedEquals);
        } else {
            result = resolvedValue.equals(resolvedEquals);
        }
        
        logger.debug("StringEqualsCondition: result = {}", result);
        return result;
    }

    public String getValue() { return value; }
    public String getEquals() { return equals; }
    public boolean isIgnoreCase() { return ignoreCase; }
}
