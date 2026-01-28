# start_on_proxy_start Template

Automatically starts specified servers when the proxy starts. This is useful for keeping essential servers (like lobbies) always running.

## Configuration

```{ .yaml }
rules:
  start_on_boot:
    template: start_on_proxy_start  # Template type
    servers: ['lobby', 'limbo']     # List of server names to start
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `servers` | - | List of server names to start when the proxy starts. |

## How it works

The `start_on_proxy_start` template:

1. Uses the [`proxy_start`](/triggers/proxy-start.md) trigger internally
2. Uses the [`start`](/actions/server-management/start.md) action to start servers

Servers are started in the order they are listed in the configuration.