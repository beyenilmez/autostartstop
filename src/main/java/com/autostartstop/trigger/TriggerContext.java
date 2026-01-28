package com.autostartstop.trigger;

import com.autostartstop.config.SettingsConfig;
import com.autostartstop.server.MotdCacheManager;
import com.autostartstop.server.ServerManager;
import com.velocitypowered.api.proxy.ProxyServer;

/**
 * Context object that provides all dependencies needed by trigger factories.
 */
public record TriggerContext(
    ProxyServer proxy,
    Object plugin,
    ServerManager serverManager,
    MotdCacheManager motdCacheManager,
    SettingsConfig settings,
    boolean isReload
) {
    /**
     * Creates a builder for constructing a TriggerContext.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProxyServer proxy;
        private Object plugin;
        private ServerManager serverManager;
        private MotdCacheManager motdCacheManager;
        private SettingsConfig settings;
        private boolean isReload = false;

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

        public Builder motdCacheManager(MotdCacheManager motdCacheManager) {
            this.motdCacheManager = motdCacheManager;
            return this;
        }

        public Builder settings(SettingsConfig settings) {
            this.settings = settings;
            return this;
        }

        public Builder isReload(boolean isReload) {
            this.isReload = isReload;
            return this;
        }

        public TriggerContext build() {
            return new TriggerContext(proxy, plugin, serverManager, motdCacheManager, settings, isReload);
        }
    }
}
