# allow_connection Action

Allows a previously denied connection to proceed. Use this action when a connection trigger has `deny_connection: true` and you want to allow the connection to proceed. See [connection](/triggers/connection.md) for more information.

## Configuration

```{ .yaml }
action:
  - allow_connection:
      connection: ${connection}     # Connection event (default: ${connection})
      server: ${connection.server}  # Target server (default: ${connection.server})
```

## Configuration fields

| Field | Default | Description |
|-------|---------|-------------|
| `connection` | `${connection}` | The connection event object |
| `server` | `${connection.server}` | The target server to allow connection to |

## Example

```{ .yaml }
rules:
  auto_start_on_connection:
    triggers:
      - connection:
          deny_connection: true
          server_list:
            mode: whitelist
            servers:
              - survival  # Only trigger for connections to survival server
    action:
      - start:
          server: ${connection.server.name}  # Start the server that player is trying to connect to
      - while:  # Wait for server to start
        timeout: 2m
        checks:
          - server_status:
              server: ${connection.server.name}
              status: offline
      - allow_connection:  # Allow the connection after server is ready
          connection: ${connection}     # Connection event
          server: ${connection.server}  # Target server
```
This rule will automatically start the server when a player tries to connect to it, wait for it to start, and then allow the connection.