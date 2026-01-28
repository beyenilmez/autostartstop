package com.autostartstop.action;

import com.autostartstop.action.impl.*;
import com.autostartstop.config.ConfigNamedType;

/**
 * Available action types with their configuration names and creators.
 */
public enum ActionType implements ConfigNamedType {
    START("start", StartAction::create),
    STOP("stop", StopAction::create),
    RESTART("restart", RestartAction::create),
    SLEEP("sleep", SleepAction::create),
    LOG("log", LogAction::create),
    EXEC("exec", ExecAction::create),
    ALLOW_CONNECTION("allow_connection", AllowConnectionAction::create),
    DENY_PING("deny_ping", DenyPingAction::create),
    ALLOW_PING("allow_ping", AllowPingAction::create),
    RESPOND_PING("respond_ping", RespondPingAction::create),
    DISCONNECT("disconnect", DisconnectAction::create),
    CONNECT("connect", ConnectAction::create),
    SEND_MESSAGE("send_message", SendMessageAction::create),
    SEND_TITLE("send_title", SendTitleAction::create),
    SEND_ACTION_BAR("send_action_bar", SendActionBarAction::create),
    SHOW_BOSSBAR("show_bossbar", ShowBossbarAction::create),
    CLEAR_TITLE("clear_title", ClearTitleAction::create),
    HIDE_BOSSBAR("hide_bossbar", HideBossbarAction::create),
    WHILE("while", WhileAction::create),
    IF("if", IfAction::create),
    SEND_COMMAND("send_command", SendCommandAction::create);

    private final String configName;
    private final ActionCreator creator;

    ActionType(String configName, ActionCreator creator) {
        this.configName = configName;
        this.creator = creator;
    }

    @Override
    public String getConfigName() {
        return configName;
    }

    public ActionCreator getCreator() {
        return creator;
    }

    public boolean hasCreator() {
        return creator != null;
    }

    public static ActionType fromConfigName(String configName) {
        return ConfigNamedType.fromConfigName(ActionType.class, configName);
    }

    public static String getValidNames() {
        return ConfigNamedType.getValidNames(ActionType.class);
    }
}
