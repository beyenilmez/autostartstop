package com.autostartstop.config;

/**
 * Configuration for a managed server.
 */
public class ServerConfig {
    private String name;
    private String virtualHost;
    private PingConfig ping;
    private ControlApiConfig controlApi;
    private StartupTimerConfig startupTimer;

    public ServerConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PingConfig getPing() {
        return ping;
    }

    public void setPing(PingConfig ping) {
        this.ping = ping;
    }

    public ControlApiConfig getControlApi() {
        return controlApi;
    }

    public void setControlApi(ControlApiConfig controlApi) {
        this.controlApi = controlApi;
    }

    public StartupTimerConfig getStartupTimer() {
        return startupTimer;
    }

    public void setStartupTimer(StartupTimerConfig startupTimer) {
        this.startupTimer = startupTimer;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }
}
