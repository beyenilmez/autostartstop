package com.autostartstop;

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * A wrapper for SLF4J Logger that prefixes all messages with [AutoStartStop].
 */
public class PluginLogger implements Logger {
    private static final String PREFIX = "[AutoStartStop] ";
    private final Logger delegate;

    private PluginLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public static Logger wrap(Logger logger) {
        return new PluginLogger(logger);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    // TRACE level
    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        delegate.trace(PREFIX + msg);
    }

    @Override
    public void trace(String format, Object arg) {
        delegate.trace(PREFIX + format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        delegate.trace(PREFIX + format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        delegate.trace(PREFIX + format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        delegate.trace(PREFIX + msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
        delegate.trace(marker, PREFIX + msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        delegate.trace(marker, PREFIX + format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        delegate.trace(marker, PREFIX + format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        delegate.trace(marker, PREFIX + format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        delegate.trace(marker, PREFIX + msg, t);
    }

    // DEBUG level
    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        delegate.debug(PREFIX + msg);
    }

    @Override
    public void debug(String format, Object arg) {
        delegate.debug(PREFIX + format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        delegate.debug(PREFIX + format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        delegate.debug(PREFIX + format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        delegate.debug(PREFIX + msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
        delegate.debug(marker, PREFIX + msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        delegate.debug(marker, PREFIX + format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        delegate.debug(marker, PREFIX + format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... argArray) {
        delegate.debug(marker, PREFIX + format, argArray);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        delegate.debug(marker, PREFIX + msg, t);
    }

    // INFO level
    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        delegate.info(PREFIX + msg);
    }

    @Override
    public void info(String format, Object arg) {
        delegate.info(PREFIX + format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        delegate.info(PREFIX + format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        delegate.info(PREFIX + format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        delegate.info(PREFIX + msg, t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
        delegate.info(marker, PREFIX + msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        delegate.info(marker, PREFIX + format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        delegate.info(marker, PREFIX + format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... argArray) {
        delegate.info(marker, PREFIX + format, argArray);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        delegate.info(marker, PREFIX + msg, t);
    }

    // WARN level
    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        delegate.warn(PREFIX + msg);
    }

    @Override
    public void warn(String format, Object arg) {
        delegate.warn(PREFIX + format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        delegate.warn(PREFIX + format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        delegate.warn(PREFIX + format, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
        delegate.warn(PREFIX + msg, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
        delegate.warn(marker, PREFIX + msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        delegate.warn(marker, PREFIX + format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        delegate.warn(marker, PREFIX + format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... argArray) {
        delegate.warn(marker, PREFIX + format, argArray);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        delegate.warn(marker, PREFIX + msg, t);
    }

    // ERROR level
    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        delegate.error(PREFIX + msg);
    }

    @Override
    public void error(String format, Object arg) {
        delegate.error(PREFIX + format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        delegate.error(PREFIX + format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        delegate.error(PREFIX + format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        delegate.error(PREFIX + msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
        delegate.error(marker, PREFIX + msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        delegate.error(marker, PREFIX + format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        delegate.error(marker, PREFIX + format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... argArray) {
        delegate.error(marker, PREFIX + format, argArray);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        delegate.error(marker, PREFIX + msg, t);
    }
}
