package com.autostartstop.template;

import com.autostartstop.config.ConfigNamedType;
import com.autostartstop.template.impl.RespondPingTemplate;
import com.autostartstop.template.impl.StartOnConnectionTemplate;
import com.autostartstop.template.impl.StartOnProxyStartTemplate;
import com.autostartstop.template.impl.StopOnEmptyTemplate;
import com.autostartstop.template.impl.StopOnProxyShutdownTemplate;

/**
 * Available template types with their configuration names and creators.
 */
public enum TemplateType implements ConfigNamedType {
    STOP_ON_EMPTY("stop_on_empty", StopOnEmptyTemplate::create),
    STOP_ON_PROXY_SHUTDOWN("stop_on_proxy_shutdown", StopOnProxyShutdownTemplate::create),
    START_ON_CONNECTION("start_on_connection", StartOnConnectionTemplate::create),
    START_ON_PROXY_START("start_on_proxy_start", StartOnProxyStartTemplate::create),
    RESPOND_PING("respond_ping", RespondPingTemplate::create);

    private final String configName;
    private final TemplateCreator creator;

    TemplateType(String configName, TemplateCreator creator) {
        this.configName = configName;
        this.creator = creator;
    }

    @Override
    public String getConfigName() {
        return configName;
    }

    public TemplateCreator getCreator() {
        return creator;
    }

    public boolean hasCreator() {
        return creator != null;
    }

    public static TemplateType fromConfigName(String configName) {
        return ConfigNamedType.fromConfigName(TemplateType.class, configName);
    }

    public static String getValidNames() {
        return ConfigNamedType.getValidNames(TemplateType.class);
    }
}
