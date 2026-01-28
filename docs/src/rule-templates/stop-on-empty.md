# stop_on_empty Template

Automatically stops servers after they've been empty (no players) for a specified duration.

## Configuration

```{ .yaml }
rules:
  auto_stop_empty:
    template: stop_on_empty  # Template type
    empty_time: 15m  # Duration server must be empty before stopping (default: 15m)
    servers: ['survival', 'creative']   # List of server names to monitor
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `empty_time` | `15m` | Duration the server must be empty before stopping. Supports duration formats like `15m`, `1h`, `30s`, etc. |
| `servers` | - | List of server names to monitor. At least one server must be specified. |

## How it works

The `stop_on_empty` template:

1. Uses the [`empty_server`](/triggers/empty-server.md) trigger internally to detect empty servers
2. Uses the [`stop`](/actions/server-management/stop.md) action to stop servers

Check [`empty_server`](/triggers/empty-server.md) trigger for more information.