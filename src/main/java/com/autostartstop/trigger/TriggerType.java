package com.autostartstop.trigger;

import com.autostartstop.config.ConfigNamedType;
import com.autostartstop.trigger.impl.*;

/**
 * Available trigger types with their configuration names and creators.
 */
public enum TriggerType implements ConfigNamedType {
    PROXY_START("proxy_start", ProxyStartTrigger::create),
    PROXY_SHUTDOWN("proxy_shutdown", ProxyShutdownTrigger::create),
    CONNECTION("connection", ConnectionTrigger::create),
    PING("ping", PingTrigger::create),
    MANUAL("manual", ManualTrigger::create),
    CRON("cron", CronTrigger::create),
    EMPTY_SERVER("empty_server", EmptyServerTrigger::create);

    private final String configName;
    private final TriggerCreator creator;

    TriggerType(String configName, TriggerCreator creator) {
        this.configName = configName;
        this.creator = creator;
    }

    @Override
    public String getConfigName() {
        return configName;
    }

    public TriggerCreator getCreator() {
        return creator;
    }

    public boolean hasCreator() {
        return creator != null;
    }

    public static TriggerType fromConfigName(String configName) {
        return ConfigNamedType.fromConfigName(TriggerType.class, configName);
    }

    public static String getValidNames() {
        return ConfigNamedType.getValidNames(TriggerType.class);
    }
}
