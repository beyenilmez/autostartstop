package com.autostartstop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginLogger {
  private PluginLogger() {}

  public static Logger get(Class<?> clazz) {
    return LoggerFactory.getLogger(Constants.PLUGIN_ID + "." + clazz.getSimpleName());
  }
}
