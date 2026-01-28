package com.autostartstop.action.impl;

import com.autostartstop.action.Action;
import com.autostartstop.action.ActionContext;
import com.autostartstop.action.ActionRegistry;
import com.autostartstop.action.ActionType;
import com.autostartstop.condition.ConditionEvaluator;
import com.autostartstop.config.ActionConfig;
import com.autostartstop.config.ConditionConfig;
import com.autostartstop.context.ExecutionContext;
import com.autostartstop.Log;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Action that provides conditional execution with if/else_if/else branching.
 */
public class IfAction implements Action {
    private static final Logger logger = Log.get(IfAction.class);
    private static final String ACTION_NAME = "if";
    
    private final ConditionConfig primaryCondition;
    private final List<ActionConfig> thenActions;
    private final List<ElseIfBranch> elseIfBranches;
    private final List<ActionConfig> elseActions;
    private final ConditionEvaluator conditionEvaluator;
    private final ActionRegistry actionRegistry;

    /**
     * Represents an else_if branch with its conditions and actions.
     */
    public static class ElseIfBranch {
        private final ConditionConfig condition;
        private final List<ActionConfig> thenActions;

        public ElseIfBranch(ConditionConfig condition, List<ActionConfig> thenActions) {
            this.condition = condition;
            this.thenActions = thenActions;
        }

        public ConditionConfig getCondition() { return condition; }
        public List<ActionConfig> getThenActions() { return thenActions; }
    }

    public IfAction(ConditionConfig primaryCondition, List<ActionConfig> thenActions,
                    List<ElseIfBranch> elseIfBranches, List<ActionConfig> elseActions,
                    ConditionEvaluator conditionEvaluator, ActionRegistry actionRegistry) {
        this.primaryCondition = primaryCondition;
        this.thenActions = thenActions;
        this.elseIfBranches = elseIfBranches;
        this.elseActions = elseActions;
        this.conditionEvaluator = conditionEvaluator;
        this.actionRegistry = actionRegistry;
    }

    /**
     * Creates an IfAction from configuration.
     */
    public static IfAction create(ActionConfig config, ActionContext ctx) {
        Map<String, Object> rawConfig = config.getRawConfig();
        if (rawConfig == null) {
            throw new IllegalArgumentException("IfAction requires configuration");
        }

        String mode = config.getString("mode", "all");

        ConditionConfig primaryCondition = new ConditionConfig();
        primaryCondition.setMode(mode);
        
        Object checksObj = rawConfig.get("checks");
        if (checksObj instanceof List<?> checksList) {
            List<Map<String, Object>> checks = parseChecks(checksList);
            primaryCondition.setChecks(checks);
        }

        List<ActionConfig> thenActions = new ArrayList<>();
        Object thenObj = rawConfig.get("then");
        if (thenObj instanceof List<?> thenList) {
            thenActions = parseActionList(thenList);
        }

        List<ElseIfBranch> elseIfBranches = new ArrayList<>();
        Object elseIfObj = rawConfig.get("else_if");
        if (elseIfObj instanceof List<?> elseIfList) {
            for (Object branchObj : elseIfList) {
                if (branchObj instanceof Map<?, ?> branchMap) {
                    ElseIfBranch branch = parseElseIfBranch(branchMap, mode);
                    if (branch != null) {
                        elseIfBranches.add(branch);
                    }
                }
            }
        }

        List<ActionConfig> elseActions = new ArrayList<>();
        Object elseObj = rawConfig.get("else");
        if (elseObj instanceof List<?> elseList) {
            elseActions = parseActionList(elseList);
        }

        return new IfAction(primaryCondition, thenActions, elseIfBranches, elseActions, 
                ctx.conditionEvaluator(), ctx.actionRegistry());
    }

    private static ElseIfBranch parseElseIfBranch(Map<?, ?> branchMap, String defaultMode) {
        String mode = defaultMode;
        Object modeObj = branchMap.get("mode");
        if (modeObj != null) {
            mode = modeObj.toString();
        }

        ConditionConfig condition = new ConditionConfig();
        condition.setMode(mode);
        
        Object checksObj = branchMap.get("checks");
        if (checksObj instanceof List<?> checksList) {
            List<Map<String, Object>> checks = parseChecks(checksList);
            condition.setChecks(checks);
        }

        List<ActionConfig> thenActions = new ArrayList<>();
        Object thenObj = branchMap.get("then");
        if (thenObj instanceof List<?> thenList) {
            thenActions = parseActionList(thenList);
        }

        if (condition.getChecks() == null || condition.getChecks().isEmpty()) {
            return null;
        }

        return new ElseIfBranch(condition, thenActions);
    }

    private static List<Map<String, Object>> parseChecks(List<?> checksList) {
        List<Map<String, Object>> checks = new ArrayList<>();
        for (Object check : checksList) {
            if (check instanceof Map<?, ?> checkMap) {
                Map<String, Object> typedCheck = new HashMap<>();
                for (Map.Entry<?, ?> entry : checkMap.entrySet()) {
                    typedCheck.put(entry.getKey().toString(), entry.getValue());
                }
                checks.add(typedCheck);
            }
        }
        return checks;
    }

    private static List<ActionConfig> parseActionList(List<?> actionList) {
        List<ActionConfig> actions = new ArrayList<>();
        for (Object actionObj : actionList) {
            if (actionObj instanceof Map<?, ?> actionMap) {
                ActionConfig actionConfig = parseActionConfig(actionMap);
                if (actionConfig != null) {
                    actions.add(actionConfig);
                }
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private static ActionConfig parseActionConfig(Map<?, ?> actionMap) {
        if (actionMap == null || actionMap.isEmpty()) {
            return null;
        }

        ActionConfig config = new ActionConfig();
        for (Map.Entry<?, ?> entry : actionMap.entrySet()) {
            String type = entry.getKey().toString();
            config.setType(type);

            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> actionData = (Map<String, Object>) value;
                config.setRawConfig(actionData);

                if (actionData.containsKey("wait_for_completion")) {
                    Object wfc = actionData.get("wait_for_completion");
                    config.setWaitForCompletion(Boolean.parseBoolean(wfc.toString()));
                }
            }
            break;
        }
        return config;
    }

    @Override
    public ActionType getType() {
        return ActionType.IF;
    }

    @Override
    public CompletableFuture<Void> execute(ExecutionContext context) {
        String ruleName = (String) context.getVariable("_rule_name", "unknown");
        logger.debug("({}) {}: evaluating primary condition", ruleName, ACTION_NAME);
        
        return CompletableFuture.runAsync(() -> {
            try {
                if (conditionEvaluator.evaluate(primaryCondition, context)) {
                    logger.debug("({}) {}: primary condition TRUE, executing 'then' actions", ruleName, ACTION_NAME);
                    executeActions(thenActions, context, ruleName);
                    return;
                }
                
                logger.debug("({}) {}: primary condition FALSE, checking else_if branches", ruleName, ACTION_NAME);
                
                if (elseIfBranches != null && !elseIfBranches.isEmpty()) {
                    for (int i = 0; i < elseIfBranches.size(); i++) {
                        ElseIfBranch branch = elseIfBranches.get(i);
                        logger.debug("({}) {}: evaluating else_if branch {}", ruleName, ACTION_NAME, i + 1);
                        
                        if (conditionEvaluator.evaluate(branch.getCondition(), context)) {
                            logger.debug("({}) {}: else_if branch {} TRUE, executing 'then' actions", 
                                    ruleName, ACTION_NAME, i + 1);
                            executeActions(branch.getThenActions(), context, ruleName);
                            return;
                        }
                    }
                }
                
                if (elseActions != null && !elseActions.isEmpty()) {
                    logger.debug("({}) {}: no conditions matched, executing 'else' actions", ruleName, ACTION_NAME);
                    executeActions(elseActions, context, ruleName);
                } else {
                    logger.debug("({}) {}: no conditions matched and no 'else' actions defined", ruleName, ACTION_NAME);
                }
                
            } catch (Exception e) {
                logger.error("({}) {}: error during conditional execution: {}", ruleName, ACTION_NAME, e.getMessage());
            }
        });
    }

    private void executeActions(List<ActionConfig> actions, ExecutionContext context, String ruleName) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        
        for (ActionConfig actionConfig : actions) {
            Action action = actionRegistry.create(actionConfig);
            if (action != null) {
                try {
                    logger.debug("({}) {}: executing nested action '{}'", ruleName, ACTION_NAME, actionConfig.getType());
                    if (actionConfig.isWaitForCompletion()) {
                        action.execute(context).join();
                    } else {
                        action.execute(context);
                    }
                } catch (Exception e) {
                    logger.error("({}) {}: error executing nested action '{}': {}", 
                            ruleName, ACTION_NAME, actionConfig.getType(), e.getMessage());
                }
            }
        }
    }

    public ConditionConfig getPrimaryCondition() { return primaryCondition; }
    public List<ActionConfig> getThenActions() { return thenActions; }
    public List<ElseIfBranch> getElseIfBranches() { return elseIfBranches; }
    public List<ActionConfig> getElseActions() { return elseActions; }
}
