package com.autostartstop.config;

/**
 * Default configurations that are merged with specific configurations.
 */
public class DefaultsConfig {
    private ServerConfig server;

    public DefaultsConfig() {
    }

    public ServerConfig getServer() {
        return server;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }
}
