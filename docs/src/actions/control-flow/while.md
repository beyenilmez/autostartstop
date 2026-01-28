# while Action

Executes actions repeatedly while conditions are met. Supports timeout and configurable update interval.

## Configuration

```{ .yaml }
action:
  - while:
      mode: all  # Condition evaluation mode (default: all)
      checks:    # Condition checks
        - server_status:
            server: survival
            status: offline
      do:  # Actions to execute in loop
        - log:
            message: "Waiting for server to start..."
      timeout: 60s         # Maximum time to loop
      update_interval: 1s  # Time between iterations (default: 1s)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `mode` | `all` | Condition evaluation mode: `all` (AND) or `any` (OR) |
| `checks` | - | List of condition checks |
| `do` | - | Actions to execute in each loop iteration (optional) |
| `timeout` | - | Maximum time to loop (e.g., `60s`, `5m`) |
| `update_interval` | `1s` | Minimum time between loop iterations |

!!! note "Condition checks"
    The `checks` and `mode` fields work exactly the same as in [rule conditions](/conditions/index.md). You can use any available condition type (e.g., `server_status`, `player_count`, `string_equals`, `number_compare`) and the same evaluation modes (`all` or `any`).

## Example

```{ .yaml }
rules:
  auto_start_on_connection:
    triggers:
      - connection:
          server_list:
            mode: whitelist
            servers:
              - survival  # Only trigger for connections to survival server
    action:
      - start:
          server: ${connection.server.name}  # Start the server that player is trying to connect to
      - while:  # Wait for server to start
        timeout: 2m  # Maximum wait time
        update_interval: 2s  # Check every 2 seconds
        checks:
          - server_status:
              server: ${connection.server.name}
              status: offline
      - allow_connection:  # Allow the connection after server is ready
```
This rule will automatically start the server when a player tries to connect to it, wait for it to start, and then allow the connection.