package com.autostartstop.condition;

import com.autostartstop.condition.impl.*;
import com.autostartstop.config.ConfigNamedType;

/**
 * Available condition types with their configuration names and creators.
 */
public enum ConditionType implements ConfigNamedType {
    STRING_EQUALS("string_equals", StringEqualsCondition::create),
    NUMBER_COMPARE("number_compare", NumberCompareCondition::create),
    SERVER_STATUS("server_status", ServerStatusCondition::create),
    PLAYER_COUNT("player_count", PlayerCountCondition::create);

    private final String configName;
    private final ConditionCreator creator;

    ConditionType(String configName, ConditionCreator creator) {
        this.configName = configName;
        this.creator = creator;
    }

    @Override
    public String getConfigName() {
        return configName;
    }

    public ConditionCreator getCreator() {
        return creator;
    }

    public boolean hasCreator() {
        return creator != null;
    }

    public static ConditionType fromConfigName(String configName) {
        return ConfigNamedType.fromConfigName(ConditionType.class, configName);
    }

    public static String getValidNames() {
        return ConfigNamedType.getValidNames(ConditionType.class);
    }
}
