# server_status Condition

Checks if a server is online or offline.

## Configuration

```{ .yaml }
conditions:
  mode: all
  checks:
    - server_status:
        server: survival  # Server name (required)
        status: online    # Expected status: 'online' or 'offline' (required)
        invert: false     # Optional: Invert the result (default: false)
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `server` | - | The server name to check (required) |
| `status` | - | Expected status: `online` or `offline` (required) |
| `invert` | `false` | If `true`, negates the condition result |

## Example

```{ .yaml }
rules:
  auto_start_on_connect:
    triggers:
      - connection:
          server_list:
            mode: whitelist
            servers: [survival]
          deny_connection: true  # Deny connection
    conditions:
      mode: all
      checks:
        - server_status:
            server: ${connection.server.name}
            status: offline  # Only start if server is offline
    action:
      - start:
          server: ${connection.server.name}  # Start the server
      - sleep:
          duration: 20s  # Wait for server to start
      - allow_connection:  # Allow the player to connect after server is ready
```