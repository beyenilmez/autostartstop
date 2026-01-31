# Settings

The `settings:` section contains global plugin settings that affect overall behavior.

## Available settings

```{ .yaml }
settings:
  shutdown_timeout: 30s            # Maximum time to wait for pending actions during proxy shutdown
  empty_server_check_interval: 5m  # Interval for checking if servers are empty (for empty_server trigger)
  motd_cache_interval: 15m         # Interval for caching MOTD responses (for respond_ping action)
  check_for_updates: true          # Check for plugin updates on startup
```

## Setting descriptions

### `shutdown_timeout`

Maximum time to wait for pending actions to complete during proxy shutdown.

- **Format**: Duration (e.g., `30s`, `2m`, `1h`)
- **Default**: `30s`

If actions are still running when this timeout is reached, they will be interrupted.

### `empty_server_check_interval`

Interval for checking if servers are empty (used by the `empty_server` trigger).

- **Format**: Duration (e.g., `5m`, `10m`, `1h`)
- **Default**: `5m`
- **Disable periodic checks**: Set to `0`

This interval determines how often the plugin checks for empty servers. The interval can be kept high since the plugin also detects empty servers when players disconnect. This check is mainly for servers that start without any player activity.

### `motd_cache_interval`

Interval for caching MOTD (Message of the Day) responses for virtual hosts (used by the `respond_ping` action).

- **Format**: Duration (e.g., `15m`, `30m`, `1h`)
- **Default**: `15m`
- **Disable interval-based caching**: Set to `0`

MOTD responses are cached in two scenarios:

1. **Interval-based**: Periodically at the configured interval
2. **Event-based**: When a server is stopped by the plugin

If `motd_cache_interval` is set to `0`, only event-based caching (when the plugin stops a server) is performed, and interval-based caching is disabled.

Caching MOTD allows you to respond with the same MOTD for a virtual host even if the server is offline.

### `check_for_updates`

Whether to check for plugin updates on startup.

- **Type**: Boolean
- **Default**: `true`

When enabled, the plugin checks for a newer version when the proxy starts. If an update is available, a visible message is logged with the current version and the new version. Set to `false` to disable the startup update check.
