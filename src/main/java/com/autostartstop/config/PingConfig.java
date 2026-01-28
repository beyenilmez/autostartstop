package com.autostartstop.config;

/**
 * Configuration for server ping settings.
 */
public class PingConfig {
    private String timeout;
    private String method;

    public PingConfig() {
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
