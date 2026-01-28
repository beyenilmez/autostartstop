package com.autostartstop.action;

import com.autostartstop.condition.ConditionEvaluator;
import com.autostartstop.config.SettingsConfig;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.MotdCacheManager;
import com.autostartstop.server.ServerManager;
import com.autostartstop.server.ServerStartupTracker;
import com.autostartstop.util.TargetResolver;

/**
 * Context object that provides all dependencies needed by action factories.
 * This allows action classes to have a static create method without needing
 * separate factory classes for each action type.
 */
public record ActionContext(
    ServerManager serverManager,
    VariableResolver variableResolver,
    ServerStartupTracker startupTracker,
    ConditionEvaluator conditionEvaluator,
    TargetResolver targetResolver,
    ActionRegistry actionRegistry,
    SettingsConfig settings,
    MotdCacheManager motdCacheManager
) {
    /**
     * Creates a builder for constructing an ActionContext.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating ActionContext instances.
     */
    public static class Builder {
        private ServerManager serverManager;
        private VariableResolver variableResolver;
        private ServerStartupTracker startupTracker;
        private ConditionEvaluator conditionEvaluator;
        private TargetResolver targetResolver;
        private ActionRegistry actionRegistry;
        private SettingsConfig settings;
        private MotdCacheManager motdCacheManager;

        public Builder serverManager(ServerManager serverManager) {
            this.serverManager = serverManager;
            return this;
        }

        public Builder variableResolver(VariableResolver variableResolver) {
            this.variableResolver = variableResolver;
            return this;
        }

        public Builder startupTracker(ServerStartupTracker startupTracker) {
            this.startupTracker = startupTracker;
            return this;
        }

        public Builder conditionEvaluator(ConditionEvaluator conditionEvaluator) {
            this.conditionEvaluator = conditionEvaluator;
            return this;
        }

        public Builder targetResolver(TargetResolver targetResolver) {
            this.targetResolver = targetResolver;
            return this;
        }

        public Builder actionRegistry(ActionRegistry actionRegistry) {
            this.actionRegistry = actionRegistry;
            return this;
        }

        public Builder settings(SettingsConfig settings) {
            this.settings = settings;
            return this;
        }

        public Builder motdCacheManager(MotdCacheManager motdCacheManager) {
            this.motdCacheManager = motdCacheManager;
            return this;
        }

        public ActionContext build() {
            return new ActionContext(
                serverManager,
                variableResolver,
                startupTracker,
                conditionEvaluator,
                targetResolver,
                actionRegistry,
                settings,
                motdCacheManager
            );
        }
    }
}
