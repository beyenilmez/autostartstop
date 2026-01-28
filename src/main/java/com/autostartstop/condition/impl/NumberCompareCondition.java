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
 * Compares a numeric value against min, max, or equals constraints.
 */
public class NumberCompareCondition implements Condition {
    private static final Logger logger = Log.get(NumberCompareCondition.class);
    
    private final String value;
    private final String min;
    private final String max;
    private final String equals;
    private final VariableResolver variableResolver;

    public NumberCompareCondition(String value, String min, String max, String equals, VariableResolver variableResolver) {
        this.value = value;
        this.min = min;
        this.max = max;
        this.equals = equals;
        this.variableResolver = variableResolver;
    }

    public static NumberCompareCondition create(Map<String, Object> config, ConditionContext ctx) {
        Object valueObj = config.get("value");
        if (valueObj == null) {
            throw ConfigException.required("number_compare", "value");
        }
        
        return new NumberCompareCondition(
                valueObj.toString(),
                config.get("min") != null ? config.get("min").toString() : null,
                config.get("max") != null ? config.get("max").toString() : null,
                config.get("equals") != null ? config.get("equals").toString() : null,
                ctx.variableResolver()
        );
    }

    @Override
    public ConditionType getType() {
        return ConditionType.NUMBER_COMPARE;
    }

    @Override
    public boolean evaluate(ExecutionContext context) {
        double numValue = variableResolver.resolveDouble(value, context, Double.NaN);
        if (Double.isNaN(numValue)) {
            logger.warn("Failed to parse value '{}' as number", value);
            return false;
        }
        logger.debug("Evaluating value {} (from '{}')", numValue, value);

        if (equals != null && !equals.isBlank()) {
            double resolvedEquals = variableResolver.resolveDouble(equals, context, Double.NaN);
            if (!Double.isNaN(resolvedEquals)) {
                boolean result = Double.compare(numValue, resolvedEquals) == 0;
                logger.debug("{} == {} -> {}", numValue, resolvedEquals, result);
                return result;
            }
        }

        if (min != null && !min.isBlank()) {
            double resolvedMin = variableResolver.resolveDouble(min, context, Double.NaN);
            if (!Double.isNaN(resolvedMin) && numValue < resolvedMin) {
                logger.debug("{} < min({}) -> false", numValue, resolvedMin);
                return false;
            }
        }

        if (max != null && !max.isBlank()) {
            double resolvedMax = variableResolver.resolveDouble(max, context, Double.NaN);
            if (!Double.isNaN(resolvedMax) && numValue > resolvedMax) {
                logger.debug("{} > max({}) -> false", numValue, resolvedMax);
                return false;
            }
        }

        logger.debug("{} within range -> true", numValue);
        return true;
    }

    public String getValue() { return value; }
    public String getMin() { return min; }
    public String getMax() { return max; }
    public String getEquals() { return equals; }
}
