# empty_server Trigger

Fires when a server has been empty (no players) for a specified duration.

## Configuration

```{ .yaml }
triggers:
  - empty_server:
      empty_time: 15m    # Duration the server must be empty (default: 15m)
      server_list:       # Optional: Filter which servers to monitor
        mode: whitelist  # 'whitelist' or 'blacklist'
        servers:         # List of server names (as defined in velocity.toml)
          - survival
          - creative
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `empty_time` | `15m` | Duration the server must be empty before triggering (e.g., `15m`, `30m`, `1h`) |
| `server_list` | - | Filter which servers to monitor. See [Server list filtering](#server-list-filtering) for details. |

!!! note "How empty servers are detected"
    Empty servers are primarily detected when players disconnect. Additionally, the plugin performs periodic checks based on the [`empty_server_check_interval`](/configuration/settings.md#empty_server_check_interval) setting (default: `5m`) to catch servers that start without players or become empty through other means.

### Server list filtering

Filter which servers to monitor for empty state.

```{ .yaml }
server_list:
  mode: whitelist
  servers:
    - survival
    - creative
```

- **`mode`**: `whitelist` (only listed servers) or `blacklist` (all except listed servers)
- **`servers`**: List of server names (as defined in velocity.toml)

## Context variables

| Variable | Description |
|----------|-------------|
| `empty_server.server` | RegisteredServer object |
| `empty_server.server.name` | Server name |
| `empty_server.empty_time` | Configured empty time duration |
| `empty_server.empty_since` | ISO-8601 timestamp when server became empty |

## Example

```{ .yaml }
rules:
  stop_empty_servers:
    triggers:
      - empty_server:
          empty_time: 15m  # Trigger after server is empty for 15 minutes
          server_list:
            mode: whitelist
            servers:  # Only monitor these servers
              - survival
              - creative
    action:
      - log:
          message: "Stopping ${empty_server.server.name} after ${empty_server.empty_time} of inactivity"  # Log the action
      - stop:
          server: ${empty_server.server.name}  # Stop the empty server
```
