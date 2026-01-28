package com.autostartstop.condition;

import com.autostartstop.config.SettingsConfig;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.server.ServerManager;

/**
 * Context object that provides all dependencies needed by condition factories.
 */
public record ConditionContext(
    VariableResolver variableResolver,
    ServerManager serverManager,
    SettingsConfig settings
) {
    /**
     * Creates a builder for constructing a ConditionContext.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VariableResolver variableResolver;
        private ServerManager serverManager;
        private SettingsConfig settings;

        public Builder variableResolver(VariableResolver variableResolver) {
            this.variableResolver = variableResolver;
            return this;
        }

        public Builder serverManager(ServerManager serverManager) {
            this.serverManager = serverManager;
            return this;
        }

        public Builder settings(SettingsConfig settings) {
            this.settings = settings;
            return this;
        }

        public ConditionContext build() {
            return new ConditionContext(variableResolver, serverManager, settings);
        }
    }
}
