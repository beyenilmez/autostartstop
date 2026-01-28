package com.autostartstop.template;

import com.autostartstop.action.ActionRegistry;
import com.autostartstop.config.SettingsConfig;
import com.autostartstop.context.VariableResolver;
import com.autostartstop.rule.RuleExecutor;
import com.autostartstop.server.MotdCacheManager;
import com.autostartstop.server.ServerManager;
import com.autostartstop.server.ServerStartupTracker;
import com.autostartstop.trigger.TriggerRegistry;
import com.velocitypowered.api.proxy.ProxyServer;

/**
 * Context object that provides all dependencies needed by template factories.
 */
public record TemplateContext(
    ProxyServer proxy,
    Object plugin,
    ServerManager serverManager,
    ServerStartupTracker startupTracker,
    TriggerRegistry triggerRegistry,
    ActionRegistry actionRegistry,
    RuleExecutor ruleExecutor,
    VariableResolver variableResolver,
    SettingsConfig settings,
    MotdCacheManager motdCacheManager
) {
    /**
     * Creates a builder for constructing a TemplateContext.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProxyServer proxy;
        private Object plugin;
        private ServerManager serverManager;
        private ServerStartupTracker startupTracker;
        private TriggerRegistry triggerRegistry;
        private ActionRegistry actionRegistry;
        private RuleExecutor ruleExecutor;
        private VariableResolver variableResolver;
        private SettingsConfig settings;
        private MotdCacheManager motdCacheManager;

        public Builder proxy(ProxyServer proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder plugin(Object plugin) {
            this.plugin = plugin;
            return this;
        }

        public Builder serverManager(ServerManager serverManager) {
            this.serverManager = serverManager;
            return this;
        }

        public Builder startupTracker(ServerStartupTracker startupTracker) {
            this.startupTracker = startupTracker;
            return this;
        }

        public Builder triggerRegistry(TriggerRegistry triggerRegistry) {
            this.triggerRegistry = triggerRegistry;
            return this;
        }

        public Builder actionRegistry(ActionRegistry actionRegistry) {
            this.actionRegistry = actionRegistry;
            return this;
        }

        public Builder ruleExecutor(RuleExecutor ruleExecutor) {
            this.ruleExecutor = ruleExecutor;
            return this;
        }

        public Builder variableResolver(VariableResolver variableResolver) {
            this.variableResolver = variableResolver;
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

        public TemplateContext build() {
            return new TemplateContext(
                proxy,
                plugin,
                serverManager,
                startupTracker,
                triggerRegistry,
                actionRegistry,
                ruleExecutor,
                variableResolver,
                settings,
                motdCacheManager
            );
        }
    }
}
