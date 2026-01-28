# stop_on_proxy_shutdown Template

Automatically stops specified servers when the proxy shuts down. This ensures servers are properly stopped before the proxy closes.

## Configuration

```{ .yaml }
rules:
  stop_on_shutdown:
    template: stop_on_proxy_shutdown  # Template type
    servers: ['survival', 'lobby']    # List of server names to stop
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `servers` | - | List of server names to stop when the proxy shuts down. |

## How it works

The `stop_on_proxy_shutdown` template:

1. Uses the [`proxy_shutdown`](/triggers/proxy-shutdown.md) trigger internally
2. Uses the [`stop`](/actions/server-management/stop.md) action to stop servers

!!! warning "Shutdown timeout"
    The proxy will wait for servers to stop, but only up to the configured [`shutdown_timeout`](/configuration/settings.md#shutdown_timeout). Make sure your servers can stop within this time, or increase the timeout in your configuration.